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
 *   <li>A job nobody has asked for in {@link #STALE_FRAMES} frames is dropped unbuilt: flying fast never
 *       leaves the workers grinding through terrain long behind.</li>
 *   <li>Uploads are bounded per frame by the renderer's staging space; what does not fit waits, so a
 *       burst of finished tiles costs a few frames a little each instead of one frame a lot.</li>
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

	static final int STALE_FRAMES = 90;
	private static final int REPRIORITIZE_FRAMES = 15;
	private static final int EVICT_FRAMES = 30;
	private static final int KEEP_FRAMES = 1200;

	private record Job(Tile tile, float priority, long order) implements Comparable<Job> {
		@Override
		public int compareTo(Job o) {
			int c = Float.compare(this.priority, o.priority);
			return c != 0 ? c : Long.compare(this.order, o.order);
		}
	}

	private record Built(Tile tile, ByteBuffer vertices, int quads, int minY, int maxY, long version) {
	}

	private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
	private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
	private final ConcurrentLinkedQueue<Built> built = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<Built> waiting = new ArrayDeque<>();
	private final LodSelector selector = new LodSelector();
	private final List<Thread> workers = new ArrayList<>();
	private final AtomicLong order = new AtomicLong();
	private volatile TerrainSource source;
	private volatile long version;
	private volatile long frame;
	private volatile boolean running = true;
	private long gpuBytes;
	private long budgetBytes;

	// statistics, for the debug screen and the benchmarks
	public final AtomicLong tilesBuilt = new AtomicLong();
	public final AtomicLong buildNanos = new AtomicLong();
	public final AtomicLong quadsBuilt = new AtomicLong();
	public final AtomicLong jobsDropped = new AtomicLong();

	public LodEngine(int threads, long budgetBytes) {
		this.budgetBytes = budgetBytes;
		for (int i = 0; i < threads; i++) {
			Thread t = new Thread(this::work, "VOID LOD worker " + i);
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
			this.workers.add(t);
		}
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
		this.source = source;
	}

	public TerrainSource source() {
		return this.source;
	}

	/**
	 * One frame: select, queue, upload, evict. Returns the tiles to draw, valid until the next call.
	 */
	public LongList update(Gpu gpu, double cx, double cy, double cz, double radius, double clip, double scale, double pixelsPerCell) {
		long now = ++this.frame;
		LongList draw = this.source == null
			? new LongList()
			: this.selector.select(this, cx, cy, cz, radius, clip, scale, pixelsPerCell);
		if (now % REPRIORITIZE_FRAMES == 0) {
			this.reprioritize();
		}
		this.drainBuilt(gpu);
		if (now % EVICT_FRAMES == 0) {
			this.evict(gpu);
		}
		return draw;
	}

	/** The tile under {@code key}, or null. Render thread only. */
	public Tile tile(long key) {
		return this.tiles.get(key);
	}

	// ---- LodSelector.Store ------------------------------------------------------------------------------

	@Override
	public boolean ready(long key) {
		Tile tile = this.tiles.get(key);
		return tile != null && tile.state == Tile.State.READY;
	}

	@Override
	public boolean heightRange(long key, int[] out) {
		Tile tile = this.tiles.get(key);
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
		Tile tile = this.tiles.get(key);
		if (tile == null) {
			tile = new Tile(key);
			this.tiles.put(key, tile);
		}
		tile.wantedFrame = this.frame;
		tile.priority = priority;
		if (tile.state == Tile.State.QUEUED && !tile.inQueue) {
			tile.inQueue = true;
			this.queue.add(new Job(tile, priority, this.order.incrementAndGet()));
		}
	}

	@Override
	public void touch(long key) {
		Tile tile = this.tiles.get(key);
		if (tile != null) {
			tile.usedFrame = this.frame;
		}
	}

	// ---- workers ----------------------------------------------------------------------------------------

	private void work() {
		TileBuilder builder = new TileBuilder();
		while (this.running) {
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
				this.built.add(new Built(tile, copy, mesh.quads(), mesh.minY(), mesh.maxY(), ver));
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
		while ((b = this.waiting.peek()) != null) {
			Tile tile = b.tile();
			if (b.version() != this.version || tile.state != Tile.State.BUILDING) {
				this.waiting.poll();
				continue;
			}
			tile.quads = b.quads();
			tile.minY = b.minY();
			tile.maxY = b.maxY();
			if (b.quads() == 0) {
				this.waiting.poll();
				tile.state = Tile.State.READY;
				continue;
			}
			if (!gpu.upload(tile, b.vertices())) {
				break;
			}
			this.waiting.poll();
			tile.state = Tile.State.BUILT;
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
				case READY -> idle > KEEP_FRAMES || over && idle > 2;
				case DEAD -> true;
				default -> false;
			};
			if (drop) {
				if (tile.state == Tile.State.READY) {
					this.gpuBytes -= tile.bytes();
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

	@Override
	public void close() {
		this.running = false;
		for (Thread t : this.workers) {
			t.interrupt();
		}
	}
}
