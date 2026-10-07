package dev.voidmc.lod.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The tile cache and the workers that fill it. Each frame the render thread calls {@link #update}: the
 * selector decides what to draw and what is missing, missing tiles queue for the workers, built meshes
 * upload within a budget, and tiles nobody has looked at for a while are evicted.
 *
 * <p>Performance rules this follows:
 * <ul>
 *   <li>Workers take jobs in priority order (coarse coverage first, then smallest screen error), and the
 *       queue is re-sorted a few times a second so a turning, moving camera gets what it is looking at
 *       now, not what it wanted when the job was queued.</li>
 *   <li>A job the last {@link #STALE_FRAMES} selections did not ask for is dropped unbuilt: flying fast,
 *       or the first frames of a world before any height is known, never leave the workers grinding
 *       through terrain nobody will see.</li>
 *   <li>Uploads are bounded per frame, by bytes and by the renderer's staging space; what does not fit
 *       waits, so a burst of finished tiles costs a few frames a little each instead of one frame a lot.</li>
 *   <li>Everything but job staleness is timed in seconds, not frames, so the engine behaves the same at
 *       30 frames a second and at 2000: a tile nobody draws, wants or falls back on stays 30 s.</li>
 *   <li>Workers run at minimum priority and leave cores for the game: the server thread, the chunk
 *       builders and the render thread come first.</li>
 *   <li>When the land under built tiles changes ({@link #invalidate}: chunks ingested, blocks placed, a saved
 *       region read back), the tiles keep drawing what they have while a new build is made behind them, and
 *       swap only once it is on the GPU. Rebuilds wait for the changes to settle, happen only for tiles on
 *       screen, at most one per worker at a time and behind every missing tile, and a change too small to see at a
 *       tile's level (one chunk under 512-block cells) waits for more. A change inside the area vanilla's chunks
 *       draw (where nearly all of them happen: grass spreading, water flowing, the player building) waits until
 *       the camera has moved far enough for the LOD to show it.</li>
 * </ul>
 */
public final class LodEngine implements LodSelector.Store, AutoCloseable {
	/** Where meshes go: the renderer. */
	public interface Gpu {
		/** Stages a built mesh for upload; false if there is no room this frame. Calls {@link #uploaded} when it lands. */
		boolean upload(Tile tile, ByteBuffer vertices);

		void free(Tile tile);
	}

	/**
	 * A job survives only while the latest selections still ask for it. Measured in frames, not time: the
	 * selection is the truth, and a tile it stopped asking for is never worth building, however slowly
	 * frames are coming (at world load they can come at a few a minute, while the first selections, made
	 * before any tile's height is known, ask for far more detail than the settled view needs).
	 */
	static final int STALE_FRAMES = 3;
	/**
	 * The rest is measured in time, so it means the same at 30 frames a second and at 2000: how often the queue
	 * is re-sorted and the cache swept, and how long a tile nobody has drawn or wanted stays on the GPU.
	 */
	private static final long REPRIORITIZE_NANOS = 250_000_000L;
	private static final long EVICT_NANOS = 500_000_000L;
	private static final long KEEP_NANOS = 30_000_000_000L;
	/**
	 * Mesh bytes handed to the GPU per frame at most (one tile always goes, however big). 4 MB a frame is
	 * 240 MB a second at 60 fps, far more than the workers produce, yet small enough that a burst of finished
	 * tiles never costs one frame milliseconds of copying.
	 */
	private static final long UPLOAD_BYTES_PER_FRAME = 4L << 20;
	/** Changes to the land wait this long for more before their tiles rebuild: a burst of block updates is one rebuild. */
	private static final long QUIET_NANOS = 1_000_000_000L;
	/** A tile is rebuilt at most this often, and coarser ones (where any one change matters less) less often: twice as long every two levels. */
	private static final long REBUILD_NANOS = 4_000_000_000L;
	/** Changes adding up to less than one of a tile's cells wait this long, to be gathered into one rebuild. */
	private static final long SMALL_CHANGE_NANOS = 30_000_000_000L;
	/** Rebuilds queue behind every missing tile: a hole matters more than a stale tile. */
	private static final float REBUILD_PRIORITY = 1e4F;
	/** The occluders of a tile with nothing in it: it hides nothing. */
	private static final short[] HIDES_NOTHING = filled(TileBuilder.OCCLUDERS * TileBuilder.OCCLUDERS, TileBuilder.NO_OCCLUDER);
	/** What a tile over land the source knows nothing about is: nothing to draw. */
	private static final ByteBuffer NOTHING = ByteBuffer.allocateDirect(0);

	private record Job(Tile tile, float priority, long order) implements Comparable<Job> {
		@Override
		public int compareTo(Job o) {
			int c = Float.compare(this.priority, o.priority);
			return c != 0 ? c : Long.compare(this.order, o.order);
		}
	}

	private record Built(Tile tile, ByteBuffer vertices, TileBuilder.Mesh mesh, long version, TileTerrain terrain) {
		Built(Tile tile, ByteBuffer vertices, TileBuilder.Mesh mesh, long version) {
			this(tile, vertices, mesh, version, null);
		}
	}

	/** A worker thread, and the flag that tells it to stop once the tile in hand is built. */
	private static final class Worker {
		Thread thread;
		volatile boolean stop;
	}

	private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
	private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
	private final ConcurrentLinkedQueue<Built> built = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<Built> waiting = new ArrayDeque<>();
	private final LodSelector selector = new LodSelector();
	/**
	 * Building ahead: on the move, the horizon a few seconds ahead is selected too, now and then, its missing tiles
	 * queued after everything the view needs now, so a fast flight finds its land built when it gets there.
	 */
	private final LodSelector ahead = new LodSelector();
	private static final double AHEAD_SECONDS = 3;
	/** Slower than this (blocks a second), the horizon barely changes and nothing is built ahead. */
	private static final double AHEAD_SPEED = 8;
	private static final int AHEAD_FRAMES = 8;
	private static final float AHEAD_PRIORITY = 5e3F;
	private double lastX = Double.NaN;
	private double lastZ;
	private long lastNanos;
	private double velocityX;
	private double velocityZ;
	/** Tiles the look ahead asked for (statistics). */
	public long aheadWanted;
	private final LodSelector.Store aheadStore = new LodSelector.Store() {
		@Override
		public boolean ready(long key) {
			return LodEngine.this.ready(key);
		}

		@Override
		public boolean heightRange(long key, int[] out) {
			return LodEngine.this.heightRange(key, out);
		}

		@Override
		public void want(long key, float priority) {
			Tile tile = LodEngine.this.tiles.get(key);
			if (tile != null && tile.wantedFrame == LodEngine.this.frame) {
				// the view wants it now: its own priority stands
				return;
			}
			LodEngine.this.aheadWanted++;
			LodEngine.this.want(key, priority + AHEAD_PRIORITY);
		}

		@Override
		public void touch(long key) {
			LodEngine.this.touch(key);
		}
	};
	/** The edges of recently built tiles, which their neighbours copy instead of sampling again. */
	private final SampleCache samples = new SampleCache();
	private final List<Worker> workers = new ArrayList<>();
	private int workerNames;
	/** Areas whose land changed, from any thread, as {minX, minZ, maxX, maxZ, columns changed}: marked on the next frame. */
	private final ConcurrentLinkedQueue<int[]> invalidated = new ConcurrentLinkedQueue<>();
	/** Tiles whose land changed since they were built. */
	private final LongOpenHashSet dirty = new LongOpenHashSet();
	/** Rebuilds now on the GPU, to take their tiles' places. */
	private final List<Tile> swaps = new ArrayList<>();
	/** Changes inside the area vanilla draws, per chunk, waiting for the camera to move away from them. */
	private final Long2ObjectOpenHashMap<int[]> deferred = new Long2ObjectOpenHashMap<>();
	private int rebuilding;
	private int dirtyDrawn;
	/** The camera and the radius vanilla's chunks cover, as of the latest frame. */
	private double viewX;
	private double viewZ;
	private double clip;
	private final AtomicLong order = new AtomicLong();
	private volatile TerrainSource source;
	/** The source's heights, at every level, for the tiles' lighting: one per source. */
	private volatile Heights heights;
	private volatile long version;
	private volatile long frame;
	/** The time of the current frame, and when the queue was last re-sorted and the cache last swept. */
	private long frameNanos;
	private long reprioritizedNanos;
	private long evictedNanos;
	private volatile boolean running = true;
	/**
	 * The tile the selection asked about last. It asks about each tile several times running (its height range,
	 * whether it is ready, then wants or keeps it), so one map lookup serves them all. Only while it runs: in
	 * between, tiles are evicted and made.
	 */
	private boolean selecting;
	private long askedKey;
	private Tile asked;
	private long gpuBytes;
	private long budgetBytes;

	/** Tiles whose build threw, for the log's sake. */
	private static final AtomicLong FAILURES = new AtomicLong();

	// statistics, for the debug screen and the benchmarks
	public final AtomicLong tilesBuilt = new AtomicLong();
	public final AtomicLong buildNanos = new AtomicLong();
	public final AtomicLong quadsBuilt = new AtomicLong();
	public final AtomicLong jobsDropped = new AtomicLong();
	/** Built meshes thrown away (their tile was dropped or reset meanwhile), and ready tiles evicted. */
	public long resultsDiscarded;
	public long evictedReady;
	/** Tiles rebuilt because the land under them changed, and tiles not built at all because the source knew nothing there. */
	public long rebuilt;
	public final AtomicLong tilesEmpty = new AtomicLong();

	public LodEngine(int threads, long budgetBytes) {
		this.budgetBytes = budgetBytes;
		this.setThreads(threads);
	}

	/**
	 * Starts or stops workers until there are {@code threads} of them, live. A worker told to stop finishes the
	 * tile in hand first, so no tile is left half built, and the queue simply has fewer takers.
	 */
	public void setThreads(int threads) {
		while (this.workers.size() < threads) {
			Worker worker = new Worker();
			worker.thread = new Thread(() -> this.work(worker), "VOID LOD worker " + this.workerNames++);
			worker.thread.setDaemon(true);
			worker.thread.setPriority(Thread.MIN_PRIORITY);
			worker.thread.start();
			this.workers.add(worker);
		}
		while (this.workers.size() > Math.max(0, threads)) {
			this.workers.removeLast().stop = true;
		}
	}

	public int threads() {
		return this.workers.size();
	}

	public void setBudget(long bytes) {
		this.budgetBytes = bytes;
	}

	/** Starts over with a new source (a new world, or the same world reloaded). */
	public void reset(TerrainSource source, Gpu gpu) {
		this.version++;
		this.queue.clear();
		this.built.clear();
		this.waiting.clear();
		for (Tile tile : this.tiles.values()) {
			if (tile.gpu != null) {
				gpu.free(tile);
			}
			tile.state = Tile.State.DEAD;
			Tile next = tile.rebuild;
			if (next != null) {
				if (next.gpu != null) {
					gpu.free(next);
				}
				next.state = Tile.State.DEAD;
			}
		}
		for (Tile next : this.swaps) {
			if (next.gpu != null) {
				gpu.free(next);
			}
			next.state = Tile.State.DEAD;
		}
		this.swaps.clear();
		this.dirty.clear();
		this.deferred.clear();
		this.invalidated.clear();
		this.rebuilding = 0;
		this.dirtyDrawn = 0;
		this.tiles.clear();
		this.gpuBytes = 0;
		this.samples.clear();
		this.heights = source == null ? null : new Heights();
		this.source = source;
	}

	/**
	 * Forgets the samples kept from built tiles over a block area whose terrain has changed under the same source
	 * (a chunk arrived, a player built), so the tiles rebuilt there ask the source again rather than copying from
	 * their neighbours' old edges.
	 */
	public void forgetSamples(int minX, int minZ, int maxX, int maxZ) {
		this.samples.forget(minX, minZ, maxX, maxZ);
	}

	/**
	 * Marks the tiles over a block area (max exclusive) out of date at every level, from any thread: the land there
	 * changed. They keep drawing what they have and are rebuilt in the background once the changes settle, while
	 * they are on screen.
	 */
	public void invalidate(int minX, int minZ, int maxX, int maxZ) {
		this.invalidate(minX, minZ, maxX, maxZ, (maxX - minX) * (maxZ - minZ));
	}

	/** The same, saying how many columns in the area changed: a tile rebuilds sooner the more of it did. */
	public void invalidate(int minX, int minZ, int maxX, int maxZ, int columns) {
		// the tiles rebuilt there must ask the source again, not copy their neighbours' old edges
		this.samples.forget(minX, minZ, maxX, maxZ);
		this.invalidated.add(new int[]{minX, minZ, maxX, maxZ, columns});
	}

	/**
	 * Rebuilds the tiles over an area at the levels up to {@code maxLevel}, whose source has more to show there now (the
	 * world's trees, placed in the background), keeping their samples: the land itself did not change.
	 */
	public void refresh(int minX, int minZ, int maxX, int maxZ, int maxLevel) {
		this.invalidated.add(new int[]{minX, minZ, maxX, maxZ, (maxX - minX) * (maxZ - minZ), maxLevel});
	}

	public TerrainSource source() {
		return this.source;
	}

	/**
	 * One frame: select, queue, upload, evict. Returns the tiles to draw, valid until the next call.
	 */
	public LongList update(Gpu gpu, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell) {
		return this.update(gpu, cx, cy, cz, radius, clip, scale, pixelsPerCell, pixelsPerCell);
	}

	/** One frame, with a separate detail budget for the smooth far field ({@link LodSelector}). */
	public LongList update(Gpu gpu, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell,
		double farPixelsPerCell) {
		return this.update(gpu, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell, null, 1);
	}

	/** One frame, aware of the view: see {@link LodSelector#select(LodSelector.Store, double, double, double, double, double, double, double, double, LodSelector.Visibility, double)}. */
	public LongList update(Gpu gpu, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell,
		double farPixelsPerCell, LodSelector.Visibility visibility, double outOfView) {
		++this.frame;
		long nanos = this.frameNanos = System.nanoTime();
		this.viewX = cx;
		this.viewZ = cz;
		this.clip = clip;
		this.selecting = true;
		this.asked = null;
		LongList draw = this.source == null
			? new LongList()
			: this.selector.select(this, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell, visibility, outOfView);
		this.selecting = false;
		this.asked = null;
		this.lookAhead(nanos, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell);
		if (TileBuilder.captureTerrain) {
			this.collectTerrain(draw);
		}
		if (!this.invalidated.isEmpty()) {
			this.markDirty();
		}
		if (nanos - this.reprioritizedNanos > REPRIORITIZE_NANOS) {
			this.reprioritizedNanos = nanos;
			this.reprioritize();
			if (!this.deferred.isEmpty()) {
				this.undefer();
			}
			this.rebuildDirty(draw);
		}
		this.drainBuilt(gpu);
		if (!this.swaps.isEmpty()) {
			this.swap(gpu);
		}
		if (nanos - this.evictedNanos > EVICT_NANOS) {
			this.evictedNanos = nanos;
			this.evict(gpu);
		}
		return draw;
	}

	/** What newly drawn tiles show from above, for {@link #drainTerrain}. Render thread. */
	private final java.util.ArrayList<TileTerrain> terrainOut = new java.util.ArrayList<>();
	private boolean resendTerrain;

	/** Takes the tiles drawn this frame whose terrain has not been handed over yet (all of them after {@link #resendTerrain}). */
	private void collectTerrain(LongList draw) {
		boolean all = this.resendTerrain;
		this.resendTerrain = false;
		for (int i = 0; i < draw.size(); i++) {
			Tile tile = this.tiles.get(draw.get(i));
			if (tile != null && tile.terrain != null && (all || !tile.terrainSent)) {
				tile.terrainSent = true;
				this.terrainOut.add(tile.terrain);
			}
		}
	}

	/** Hands over every drawn tile's terrain again next frame: someone new is listening. */
	public void resendTerrain() {
		this.resendTerrain = true;
	}

	/** The terrain of the tiles drawn since last asked. Render thread. */
	public java.util.List<TileTerrain> drainTerrain() {
		if (this.terrainOut.isEmpty()) {
			return java.util.List.of();
		}
		java.util.List<TileTerrain> out = new java.util.ArrayList<>(this.terrainOut);
		this.terrainOut.clear();
		return out;
	}

	/** Follows the camera's ground speed, and on the move selects the horizon {@value #AHEAD_SECONDS} s ahead. */
	private void lookAhead(long nanos, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell,
		double farPixelsPerCell) {
		if (!Double.isNaN(this.lastX) && nanos > this.lastNanos) {
			double dt = (nanos - this.lastNanos) / 1e9;
			double vx = (cx - this.lastX) / dt;
			double vz = (cz - this.lastZ) / dt;
			// a teleport is not a flight
			if (vx * vx + vz * vz > 1e6) {
				vx = vz = 0;
			}
			double k = Math.min(1, dt * 2);
			this.velocityX += (vx - this.velocityX) * k;
			this.velocityZ += (vz - this.velocityZ) * k;
		}
		this.lastX = cx;
		this.lastZ = cz;
		this.lastNanos = nanos;
		double speed = Math.hypot(this.velocityX, this.velocityZ);
		if (this.source == null || speed < AHEAD_SPEED || this.frame % AHEAD_FRAMES != 0) {
			return;
		}
		// every direction there, at the detail a normal field of view gives it: where the camera will look is unknown
		this.ahead.select(this.aheadStore, cx + this.velocityX * AHEAD_SECONDS, cy, cz + this.velocityZ * AHEAD_SECONDS, radius, clip, scale,
			pixelsPerCell, farPixelsPerCell, null, 1);
	}

	/** The tile under {@code key}, or null. Render thread only. */
	public Tile tile(long key) {
		return this.tiles.get(key);
	}

	// ---- LodSelector.Store ------------------------------------------------------------------------------

	private Tile asked(long key) {
		if (!this.selecting) {
			return this.tiles.get(key);
		}
		if (this.asked == null || key != this.askedKey) {
			this.askedKey = key;
			this.asked = this.tiles.get(key);
		}
		return this.asked;
	}

	@Override
	public boolean ready(long key) {
		Tile tile = this.asked(key);
		return tile != null && tile.state == Tile.State.READY;
	}

	@Override
	public boolean heightRange(long key, int[] out) {
		Tile tile = this.asked(key);
		if (tile == null || tile.state == Tile.State.QUEUED || tile.state == Tile.State.BUILDING) {
			// unbuilt: borrow the parent's range, which contains this tile's
			for (int up = 0; up < 3 && TileKey.level(key) < TileKey.MAX_LEVEL; up++) {
				key = TileKey.parent(key);
				tile = this.tiles.get(key);
				if (tile != null && tile.state == Tile.State.READY) {
					out[0] = tile.minY;
					out[1] = tile.maxY;
					return true;
				}
			}
			return false;
		}
		out[0] = tile.minY;
		out[1] = tile.maxY;
		return true;
	}

	@Override
	public void want(long key, float priority) {
		Tile tile = this.asked(key);
		if (tile == null) {
			tile = new Tile(key);
			this.tiles.put(key, tile);
			if (this.selecting) {
				this.askedKey = key;
				this.asked = tile;
			}
		}
		tile.wantedFrame = this.frame;
		tile.usedNanos = this.frameNanos;
		tile.priority = priority;
		if (tile.state == Tile.State.QUEUED && !tile.inQueue) {
			tile.inQueue = true;
			this.queue.add(new Job(tile, priority, this.order.incrementAndGet()));
		}
	}

	@Override
	public void touch(long key) {
		Tile tile = this.asked(key);
		if (tile != null) {
			tile.usedFrame = this.frame;
			tile.usedNanos = this.frameNanos;
		}
	}

	// ---- workers ----------------------------------------------------------------------------------------

	private void work(Worker worker) {
		TileBuilder builder = new TileBuilder(true, this.samples);
		while (this.running && !worker.stop) {
			Job job;
			try {
				job = this.queue.poll(250, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				return;
			}
			if (job == null) {
				continue;
			}
			Tile tile = job.tile();
			tile.inQueue = false;
			if (tile.state != Tile.State.QUEUED) {
				continue;
			}
			// a rebuild lives as long as the tile it replaces; anything else only while the selection asks for it
			Tile old = tile.replaces;
			if (old != null ? old.state == Tile.State.DEAD : this.frame - tile.wantedFrame > STALE_FRAMES) {
				this.jobsDropped.incrementAndGet();
				continue;
			}
			TerrainSource src = this.source;
			long ver = this.version;
			if (src == null) {
				continue;
			}
			tile.state = Tile.State.BUILDING;
			int level = TileKey.level(tile.key);
			int span = TileKey.span(level);
			int x0 = TileKey.minX(tile.key);
			int z0 = TileKey.minZ(tile.key);
			if (!src.covers(x0, z0, x0 + span, z0 + span)) {
				// nothing known there (a server's world, away from where the player has been): nothing to draw
				this.built.add(new Built(tile, NOTHING, new TileBuilder.Mesh(NOTHING, 0, 0, level >= TileBuilder.smoothFrom, 0, 0, 0, HIDES_NOTHING, false, false, 0), ver));
				this.tilesEmpty.incrementAndGet();
				continue;
			}
			long t0 = System.nanoTime();
			try {
				TileBuilder.Mesh mesh = builder.build(src, tile.key, this.heights);
				ByteBuffer copy = ByteBuffer.allocateDirect(Math.max(1, mesh.bytes())).order(ByteOrder.nativeOrder());
				copy.put(mesh.vertices()).flip();
				// the record keeps the counts; its buffer is the builder's own and is about to be reused
				this.built.add(new Built(tile, copy, mesh.withVertices(copy), ver, builder.takeTerrain()));
				this.quadsBuilt.addAndGet(mesh.quads());
			} catch (Throwable t) {
				// a source that throws for one column should not take the worker with it
				if (old != null) {
					// the render thread gives the rebuild up, and the tile it was for keeps what it has
					this.built.add(new Built(tile, null, null, ver));
				} else {
					tile.state = Tile.State.DEAD;
				}
				// the first few in full; after that a count now and then, so a failure on every tile cannot fill a disk
				long failed = FAILURES.incrementAndGet();
				if (failed <= 5) {
					org.slf4j.LoggerFactory.getLogger("VOID LOD").warn("Failed to build {}", TileKey.toString(tile.key), t);
				} else if (Long.bitCount(failed) == 1) {
					org.slf4j.LoggerFactory.getLogger("VOID LOD").warn("Failed to build {} tiles so far, the last {}: {}", failed,
						TileKey.toString(tile.key), t.toString());
				}
			}
			this.buildNanos.addAndGet(System.nanoTime() - t0);
			this.tilesBuilt.incrementAndGet();
		}
	}

	/** Re-sorts the queue by the priorities the latest selection gave, and drops what nobody wants. */
	private void reprioritize() {
		List<Job> jobs = new ArrayList<>(this.queue.size());
		this.queue.drainTo(jobs);
		long now = this.frame;
		for (Job job : jobs) {
			Tile tile = job.tile();
			boolean wanted = tile.replaces != null ? tile.replaces.state != Tile.State.DEAD : now - tile.wantedFrame <= STALE_FRAMES;
			if (tile.state == Tile.State.QUEUED && wanted) {
				this.queue.add(new Job(tile, tile.priority, job.order()));
			} else {
				tile.inQueue = false;
			}
		}
	}

	private void drainBuilt(Gpu gpu) {
		Built b;
		while ((b = this.built.poll()) != null) {
			this.waiting.add(b);
		}
		long budget = UPLOAD_BYTES_PER_FRAME;
		while ((b = this.waiting.peek()) != null && budget > 0) {
			Tile tile = b.tile();
			boolean orphan = tile.replaces != null && (b.mesh() == null || tile.replaces.state == Tile.State.DEAD);
			if (b.version() != this.version || tile.state != Tile.State.BUILDING || orphan) {
				this.waiting.poll();
				this.resultsDiscarded++;
				if (tile.replaces != null && b.version() == this.version) {
					this.abandon(tile, gpu);
				}
				continue;
			}
			TileBuilder.Mesh mesh = b.mesh();
			if (b.terrain() != null) {
				tile.terrain = b.terrain();
				tile.terrainSent = false;
			}
			tile.quads = mesh.quads();
			tile.vertices = mesh.vertexCount();
			tile.indices = mesh.indexCount();
			tile.landIndices = mesh.landIndices();
			tile.firstIndex = mesh.firstIndex();
			tile.groups = mesh.groups();
			tile.smooth = mesh.smooth();
			tile.compact = mesh.compact();
			tile.minY = mesh.minY();
			tile.maxY = mesh.maxY();
			tile.occluders = mesh.occluders();
			tile.holes = mesh.holes();
			if (mesh.vertexCount() == 0) {
				this.waiting.poll();
				tile.state = Tile.State.READY;
				tile.builtNanos = this.frameNanos;
				if (tile.replaces != null) {
					this.swaps.add(tile);
				}
				continue;
			}
			// BUILT before the hand-off: a GPU may report the upload done before upload() even returns
			tile.state = Tile.State.BUILT;
			if (!gpu.upload(tile, b.vertices())) {
				tile.state = Tile.State.BUILDING;
				break;
			}
			budget -= b.mesh().bytes();
			this.waiting.poll();
		}
	}

	/** Called by the renderer once a staged mesh is on the GPU. */
	public void uploaded(Tile tile) {
		if (tile.state == Tile.State.BUILT) {
			tile.state = Tile.State.READY;
			tile.builtNanos = this.frameNanos;
			this.gpuBytes += tile.bytes();
			if (tile.replaces != null) {
				this.swaps.add(tile);
			}
		}
	}

	// ---- changes to the land ----------------------------------------------------------------------------

	/**
	 * Marks the built tiles over every area invalidated since the last frame, at every level; or, for a change
	 * within one chunk inside the area vanilla draws, holds it until the camera moves away.
	 */
	private void markDirty() {
		int[] area;
		while ((area = this.invalidated.poll()) != null) {
			if (this.source == null) {
				continue;
			}
			if (area[0] >> 4 == (area[2] - 1) >> 4 && area[1] >> 4 == (area[3] - 1) >> 4 && this.hidden(area)) {
				long chunk = (long) (area[0] >> 4) << 32 | (area[1] >> 4) & 0xFFFFFFFFL;
				int[] had = this.deferred.get(chunk);
				if (had == null) {
					this.deferred.put(chunk, area);
				} else {
					had[0] = Math.min(had[0], area[0]);
					had[1] = Math.min(had[1], area[1]);
					had[2] = Math.max(had[2], area[2]);
					had[3] = Math.max(had[3], area[3]);
					had[4] += area[4];
				}
				continue;
			}
			this.mark(area);
		}
	}

	/** Whether an area lies wholly where vanilla's chunks draw, so the LOD shows none of it from here. */
	private boolean hidden(int[] area) {
		double fx = Math.max(Math.abs(area[0] - this.viewX), Math.abs(area[2] - this.viewX));
		double fz = Math.max(Math.abs(area[1] - this.viewZ), Math.abs(area[3] - this.viewZ));
		return fx * fx + fz * fz < this.clip * this.clip;
	}

	/** Marks the held changes the camera has moved away from. */
	private void undefer() {
		var it = this.deferred.values().iterator();
		while (it.hasNext()) {
			int[] area = it.next();
			if (!this.hidden(area)) {
				this.mark(area);
				it.remove();
			}
		}
	}

	/** Marks the built tiles over an area out of date, at every level. */
	private void mark(int[] area) {
		long now = this.frameNanos;
		float size = area[4];
		int maxLevel = area.length > 5 ? area[5] : TileKey.MAX_LEVEL;
		for (int level = 0; level <= maxLevel; level++) {
			int span = TileKey.span(level);
			// a tile's samples reach a few cells past its edges: its border, and the square each sample stands for
			int margin = 3 * TileKey.cell(level);
			int tx0 = Math.floorDiv(area[0] - margin, span);
			int tx1 = Math.floorDiv(area[2] - 1 + margin, span);
			int tz0 = Math.floorDiv(area[1] - margin, span);
			int tz1 = Math.floorDiv(area[3] - 1 + margin, span);
			for (int tz = tz0; tz <= tz1; tz++) {
				for (int tx = tx0; tx <= tx1; tx++) {
					long key = TileKey.of(level, tx, tz);
					Tile tile = this.tiles.get(key);
					// a tile still waiting for its build will see the change anyway
					if (tile == null || tile.state == Tile.State.QUEUED || tile.state == Tile.State.DEAD) {
						continue;
					}
					if (tile.dirtyArea <= 0) {
						tile.dirtySince = now;
					}
					tile.dirtyArea += size;
					tile.dirtyLast = now;
					this.dirty.add(key);
				}
			}
		}
	}

	/**
	 * Queues rebuilds for the out-of-date tiles on screen whose changes have settled and are worth it: quiet for a
	 * second, the tile not rebuilt in the last four seconds (more, for coarse tiles), and either a cell's worth of land
	 * changed or the change is old.
	 */
	private void rebuildDirty(LongList draw) {
		this.dirtyDrawn = 0;
		if (this.dirty.isEmpty()) {
			return;
		}
		long now = this.frameNanos;
		for (int i = 0; i < draw.size(); i++) {
			long key = draw.get(i);
			if (!this.dirty.contains(key)) {
				continue;
			}
			Tile tile = this.tiles.get(key);
			if (tile == null || tile.dirtyArea <= 0) {
				this.dirty.remove(key);
				continue;
			}
			this.dirtyDrawn++;
			if (tile.state != Tile.State.READY || tile.rebuild != null || this.rebuilding >= this.workers.size()) {
				continue;
			}
			int cell = TileKey.cell(TileKey.level(key));
			boolean settled = now - tile.dirtyLast >= QUIET_NANOS && now - tile.builtNanos >= REBUILD_NANOS << (TileKey.level(key) / 2);
			boolean worth = tile.dirtyArea >= (float) cell * cell || now - tile.dirtySince >= SMALL_CHANGE_NANOS;
			if (!settled || !worth) {
				continue;
			}
			this.dirty.remove(key);
			tile.dirtyArea = 0;
			Tile next = new Tile(key);
			next.replaces = tile;
			next.priority = tile.priority + REBUILD_PRIORITY;
			next.wantedFrame = this.frame;
			next.inQueue = true;
			tile.rebuild = next;
			this.rebuilding++;
			this.queue.add(new Job(next, next.priority, this.order.incrementAndGet()));
		}
	}

	/** Puts rebuilds that have reached the GPU in their tiles' places, and frees what they replace. */
	private void swap(Gpu gpu) {
		for (Tile next : this.swaps) {
			Tile old = next.replaces;
			if (old == null || next.state != Tile.State.READY) {
				// given up on meanwhile
				continue;
			}
			next.replaces = null;
			old.rebuild = null;
			this.rebuilding--;
			if (old.state == Tile.State.DEAD || this.tiles.get(next.key) != old) {
				this.release(next, gpu);
				continue;
			}
			next.usedFrame = old.usedFrame;
			next.usedNanos = old.usedNanos;
			next.wantedFrame = old.wantedFrame;
			next.priority = old.priority;
			if (old.dirtyArea > 0) {
				// changed again while it was being rebuilt: still out of date
				next.dirtyArea = old.dirtyArea;
				next.dirtySince = old.dirtySince;
				next.dirtyLast = old.dirtyLast;
			}
			this.tiles.put(next.key, next);
			this.release(old, gpu);
			this.rebuilt++;
		}
		this.swaps.clear();
	}

	/** Gives up on a rebuild: its tile went away, or the build failed. */
	private void abandon(Tile next, Gpu gpu) {
		Tile old = next.replaces;
		if (old == null) {
			return;
		}
		next.replaces = null;
		if (old.rebuild == next) {
			old.rebuild = null;
		}
		this.rebuilding--;
		this.release(next, gpu);
	}

	private void release(Tile tile, Gpu gpu) {
		if (tile.state == Tile.State.READY) {
			this.gpuBytes -= tile.bytes();
		}
		if (tile.gpu != null) {
			gpu.free(tile);
		}
		tile.state = Tile.State.DEAD;
	}

	private void evict(Gpu gpu) {
		long now = this.frame;
		boolean over = this.gpuBytes > this.budgetBytes;
		var it = this.tiles.values().iterator();
		while (it.hasNext()) {
			Tile tile = it.next();
			long idle = now - Math.max(tile.usedFrame, tile.wantedFrame);
			boolean drop = switch (tile.state) {
				case QUEUED -> idle > STALE_FRAMES && !tile.inQueue;
				case READY -> this.frameNanos - tile.usedNanos > KEEP_NANOS || over && idle > 2;
				case DEAD -> true;
				default -> false;
			};
			if (drop) {
				if (tile.state == Tile.State.READY) {
					this.gpuBytes -= tile.bytes();
					this.evictedReady++;
				}
				if (tile.gpu != null) {
					gpu.free(tile);
				}
				tile.state = Tile.State.DEAD;
				if (tile.rebuild != null) {
					this.abandon(tile.rebuild, gpu);
				}
				this.dirty.remove(tile.key);
				it.remove();
			}
		}
	}

	// ---- statistics -------------------------------------------------------------------------------------

	public int tileCount() {
		return this.tiles.size();
	}

	public int queued() {
		return this.queue.size();
	}

	public long gpuBytes() {
		return this.gpuBytes;
	}

	public long frame() {
		return this.frame;
	}

	/** Tiles on screen whose land changed since they were built, as of the last check. */
	public int dirtyDrawn() {
		return this.dirtyDrawn;
	}

	/** Rebuilds under way. */
	public int rebuilding() {
		return this.rebuilding;
	}

	/** True once nothing is queued, building or waiting to upload. */
	public boolean idle() {
		if (!this.queue.isEmpty() || !this.built.isEmpty() || !this.waiting.isEmpty() || this.rebuilding > 0 || !this.swaps.isEmpty()) {
			return false;
		}
		for (Tile tile : this.tiles.values()) {
			if (tile.state == Tile.State.BUILDING || tile.state == Tile.State.BUILT
				|| tile.state == Tile.State.QUEUED && this.frame - tile.wantedFrame <= 1) {
				return false;
			}
		}
		return true;
	}

	/** Why the engine is not idle, for tests and the debug screen. */
	public String busy() {
		int queued = 0, building = 0, built = 0;
		for (Tile tile : this.tiles.values()) {
			switch (tile.state) {
				case QUEUED -> queued += this.frame - tile.wantedFrame <= 1 ? 1 : 0;
				case BUILDING -> building++;
				case BUILT -> built++;
				default -> {
				}
			}
		}
		return "jobs " + this.queue.size() + ", results " + this.built.size() + ", waiting " + this.waiting.size()
			+ ", wanted-queued " + queued + ", building " + building + ", uploading " + built + ", rebuilding " + this.rebuilding
			+ ", dirty on screen " + this.dirtyDrawn;
	}

	@Override
	public void close() {
		this.running = false;
		for (Worker worker : this.workers) {
			worker.thread.interrupt();
		}
	}

	private static short[] filled(int n, short value) {
		short[] a = new short[n];
		java.util.Arrays.fill(a, value);
		return a;
	}
}
