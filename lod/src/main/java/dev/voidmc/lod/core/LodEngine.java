package dev.voidmc.lod.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

	private record Job(Tile tile, float priority, long order) implements Comparable<Job> {
		@Override
		public int compareTo(Job o) {
			int c = Float.compare(this.priority, o.priority);
			return c != 0 ? c : Long.compare(this.order, o.order);
		}
	}

	private record Built(Tile tile, ByteBuffer vertices, TileBuilder.Mesh mesh, long version) {
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
	/** The edges of recently built tiles, which their neighbours copy instead of sampling again. */
	private final SampleCache samples = new SampleCache();
	private final List<Worker> workers = new ArrayList<>();
	private int workerNames;
	private final AtomicLong order = new AtomicLong();
	private volatile TerrainSource source;
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

	// statistics, for the debug screen and the benchmarks
	public final AtomicLong tilesBuilt = new AtomicLong();
	public final AtomicLong buildNanos = new AtomicLong();
	public final AtomicLong quadsBuilt = new AtomicLong();
	public final AtomicLong jobsDropped = new AtomicLong();
	/** Built meshes thrown away (their tile was dropped or reset meanwhile), and ready tiles evicted. */
	public long resultsDiscarded;
	public long evictedReady;

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
		}
		this.tiles.clear();
		this.gpuBytes = 0;
		this.samples.clear();
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
		this.selecting = true;
		this.asked = null;
		LongList draw = this.source == null
			? new LongList()
			: this.selector.select(this, cx, cy, cz, radius, clip, scale, pixelsPerCell, farPixelsPerCell, visibility, outOfView);
		this.selecting = false;
		this.asked = null;
		if (nanos - this.reprioritizedNanos > REPRIORITIZE_NANOS) {
			this.reprioritizedNanos = nanos;
			this.reprioritize();
		}
		this.drainBuilt(gpu);
		if (nanos - this.evictedNanos > EVICT_NANOS) {
			this.evictedNanos = nanos;
			this.evict(gpu);
		}
		return draw;
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
			if (this.frame - tile.wantedFrame > STALE_FRAMES) {
				this.jobsDropped.incrementAndGet();
				continue;
			}
			TerrainSource src = this.source;
			long ver = this.version;
			if (src == null) {
				continue;
			}
			tile.state = Tile.State.BUILDING;
			long t0 = System.nanoTime();
			try {
				TileBuilder.Mesh mesh = builder.build(src, tile.key);
				ByteBuffer copy = ByteBuffer.allocateDirect(Math.max(1, mesh.bytes())).order(ByteOrder.nativeOrder());
				copy.put(mesh.vertices()).flip();
				// the record keeps the counts; its buffer is the builder's own and is about to be reused
				this.built.add(new Built(tile, copy, mesh.withVertices(copy), ver));
				this.quadsBuilt.addAndGet(mesh.quads());
			} catch (Throwable t) {
				// a source that throws for one column should not take the worker with it
				tile.state = Tile.State.DEAD;
				org.slf4j.LoggerFactory.getLogger("VOID LOD").warn("Failed to build {}", TileKey.toString(tile.key), t);
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
			if (tile.state == Tile.State.QUEUED && now - tile.wantedFrame <= STALE_FRAMES) {
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
			if (b.version() != this.version || tile.state != Tile.State.BUILDING) {
				this.waiting.poll();
				this.resultsDiscarded++;
				continue;
			}
			TileBuilder.Mesh mesh = b.mesh();
			tile.quads = mesh.quads();
			tile.vertices = mesh.vertexCount();
			tile.indices = mesh.indexCount();
			tile.firstIndex = mesh.firstIndex();
			tile.smooth = mesh.smooth();
			tile.minY = mesh.minY();
			tile.maxY = mesh.maxY();
			tile.occluders = mesh.occluders();
			tile.holes = mesh.holes();
			if (mesh.vertexCount() == 0) {
				this.waiting.poll();
				tile.state = Tile.State.READY;
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
			this.gpuBytes += tile.bytes();
		}
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

	/** True once nothing is queued, building or waiting to upload. */
	public boolean idle() {
		if (!this.queue.isEmpty() || !this.built.isEmpty() || !this.waiting.isEmpty()) {
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
			+ ", wanted-queued " + queued + ", building " + building + ", uploading " + built;
	}

	@Override
	public void close() {
		this.running = false;
		for (Worker worker : this.workers) {
			worker.thread.interrupt();
		}
	}
}
