package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.detect.StructureScan;
import dev.voidmc.atlas.core.under.Reveal;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Feeds the client's chunks to the workers. On the client thread it only queues (a chunk arrived, a block changed, the
 * player moved underground); the reading is done on the workers: the surface ({@link ChunkScanner}), the structures in
 * the blocks ({@link StructureScan}), and, round the player while underground, the reveal of what they can see
 * ({@link Reveal}). Changed chunks are read again once their changes have been quiet a moment, so an explosion or a long
 * /fill is one read, not thousands.
 */
public final class Ingest {
	private static final long QUIET_NANOS = 750_000_000L;
	private static final long LONGEST_NANOS = 4_000_000_000L;
	/** Client-thread time each frame may spend handing chunks to the workers. */
	private static final long BUDGET_NANOS = 400_000L;
	/** The player must move this far (blocks) before the underground is revealed round them again... */
	private static final double REVEAL_STEP = 3;
	/** ...or this long must pass (things change: a door opens, a wall is mined). */
	private static final long REVEAL_EVERY_NANOS = 2_000_000_000L;

	private static final int SURFACE = 1;
	private static final int DETECT = 4;

	private final MapSession session;
	private final ClientLevel level;
	private final DimensionData dimension;
	private final ArrayDeque<LevelChunk> arrived = new ArrayDeque<>();
	private final Long2LongOpenHashMap firstChange = new Long2LongOpenHashMap();
	private final Long2LongOpenHashMap lastChange = new Long2LongOpenHashMap();
	/** Scans queued and not yet started: chunk key to what to read. */
	private final ConcurrentHashMap<Long, Integer> queued = new ConcurrentHashMap<>();
	private int band = Integer.MIN_VALUE;
	private long lastSettle;
	private final AtomicBoolean revealing = new AtomicBoolean();
	private double revealX = Double.NaN;
	private double revealY;
	private double revealZ;
	private long revealAt;
	private volatile Reveal.Result lastWalk;
	private int walks;

	public Ingest(MapSession session, ClientLevel level) {
		this.session = session;
		this.level = level;
		this.dimension = session.dimension(level.dimension().identifier().toString(), level.dimensionType().hasCeiling());
	}

	public DimensionData dimension() {
		return this.dimension;
	}

	public ClientLevel level() {
		return this.level;
	}

	/** A chunk arrived (client thread). */
	public void loaded(LevelChunk chunk) {
		this.arrived.add(chunk);
	}

	/** Queues every chunk the client already holds round a point: those that came before Atlas was listening. */
	public void loadedAround(int cx, int cz, int radius) {
		for (int r = 0; r <= radius; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.abs(dx) == r || Math.abs(dz) == r) {
						LevelChunk chunk = this.level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
						if (chunk != null) {
							this.arrived.add(chunk);
						}
					}
				}
			}
		}
	}

	/** A block changed (client thread). */
	public void changed(int x, int z) {
		long key = ChunkPos.pack(x >> 4, z >> 4);
		long now = System.nanoTime();
		this.lastChange.put(key, now);
		if (!this.firstChange.containsKey(key)) {
			this.firstChange.put(key, now);
		}
	}

	public int backlog() {
		return this.arrived.size() + this.queued.size() + this.lastChange.size() + (this.revealing.get() ? 1 : 0);
	}

	/**
	 * Once per frame on the client thread: hands work to the workers within a small time budget. {@code underground}:
	 * the player is under the ground (or in a dimension with a roof), so what they can see round them is revealed.
	 */
	public void frame(double playerX, double playerY, double playerZ, boolean underground) {
		long start = System.nanoTime();
		if (underground) {
			int band = DimensionData.bandOf((int) Math.floor(playerY));
			if (this.band == Integer.MIN_VALUE || band != this.band && (playerY < this.band * DimensionData.BAND - 3 || playerY >= this.band * DimensionData.BAND + DimensionData.BAND + 3)) {
				// a little hysteresis: walking along a band's edge must not flip the view every step
				this.band = band;
			}
			this.revealIfMoved(playerX, playerY, playerZ, start);
		}
		if (!this.lastChange.isEmpty() && start - this.lastSettle > 100_000_000L) {
			this.lastSettle = start;
			var it = this.lastChange.long2LongEntrySet().fastIterator();
			while (it.hasNext()) {
				var e = it.next();
				long key = e.getLongKey();
				if (start - e.getLongValue() >= QUIET_NANOS || start - this.firstChange.get(key) >= LONGEST_NANOS) {
					LevelChunk chunk = this.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
					if (chunk != null) {
						this.submit(chunk, SURFACE | DETECT);
					}
					this.firstChange.remove(key);
					it.remove();
				}
			}
		}
		while (!this.arrived.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
			LevelChunk chunk = this.arrived.poll();
			this.submit(chunk, SURFACE | DETECT);
		}
		Perf.ingest(System.nanoTime() - start);
	}

	private void revealIfMoved(double x, double y, double z, long now) {
		boolean moved = Double.isNaN(this.revealX) || Math.abs(x - this.revealX) + Math.abs(y - this.revealY) + Math.abs(z - this.revealZ) >= REVEAL_STEP;
		if (!moved && now - this.revealAt < REVEAL_EVERY_NANOS || !this.revealing.compareAndSet(false, true)) {
			return;
		}
		this.revealX = x;
		this.revealY = y;
		this.revealZ = z;
		this.revealAt = now;
		int px = (int) Math.floor(x);
		int py = (int) Math.floor(y);
		int pz = (int) Math.floor(z);
		int radius = this.dimension.ceiling ? Reveal.NETHER_RADIUS : Reveal.RADIUS;
		this.session.workers.execute(() -> {
			try {
				if (this.session.closed()) {
					return;
				}
				long t0 = System.nanoTime();
				ChunkScanner scanner = this.session.scanner();
				LevelBlocks blocks = new LevelBlocks(this.level, scanner);
				// the previous walk is kept to skip what it already saw; every tenth walk
				// writes everything again, so blocks mined or placed since are caught
				Reveal.Result r = this.session.reveal().walk(blocks, px, py, pz, radius);
				Reveal.Result before = ++this.walks % 10 == 0 ? null : this.lastWalk;
				Underground.apply(this.dimension, r, before, blocks, scanner);
				this.lastWalk = r;
				Perf.REVEAL_NANOS.addAndGet(System.nanoTime() - t0);
				Perf.REVEALS.incrementAndGet();
				Perf.REVEAL_OPENED.addAndGet(r.opened);
			} catch (Throwable t) {
				// a chunk changing under the walk: it is walked again on the next step
				if (Boolean.getBoolean("atlas.logStats")) {
					MapSession.LOGGER.debug("Atlas: underground reveal failed: {}", t.toString());
				}
			} finally {
				this.revealing.set(false);
			}
		});
	}

	private void submit(LevelChunk chunk, int what) {
		ChunkPos pos = chunk.getPos();
		long key = ChunkPos.pack(pos.x(), pos.z());
		boolean[] schedule = {false};
		this.queued.compute(key, (k, v) -> {
			if (v == null) {
				schedule[0] = true;
				return what;
			}
			return v | what;
		});
		if (!schedule[0]) {
			return;
		}
		this.session.workers.execute(() -> {
			Integer flags = this.queued.remove(key);
			if (flags == null || this.session.closed()) {
				return;
			}
			try {
				// the newest copy of the chunk the client holds (it may have been sent again since it was queued)
				LevelChunk latest = this.level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
				this.scan(latest != null ? latest : chunk, flags);
			} catch (Throwable t) {
				// a chunk changing under the reader: it is read again on its next change
				if (Boolean.getBoolean("atlas.logStats")) {
					MapSession.LOGGER.debug("Atlas: scan of {} failed: {}", pos, t.toString());
				}
			}
		});
	}

	private void scan(LevelChunk chunk, int flags) {
		long t0 = System.nanoTime();
		ChunkScanner scanner = this.session.scanner();
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		if ((flags & SURFACE) != 0 && !this.dimension.ceiling) {
			LayerData layer = this.dimension.layer(DimensionData.SURFACE);
			RegionRaw raw = layer.raw(Coords.region(x0), Coords.region(z0), true);
			if (scanner.surface(chunk, raw)) {
				layer.dirty(x0, z0, x0 + 16, z0 + 16);
			}
		}
		long t1 = System.nanoTime();
		Perf.SCAN_NANOS.addAndGet(t1 - t0);
		Perf.SCANNED.incrementAndGet();
		if ((flags & DETECT) != 0) {
			String dim = this.dimension.id;
			LevelBlocks blocks = new LevelBlocks(this.level, scanner);
			StructureScan.Tally tally = scanner.detect(chunk, blocks, this.dimension.ceiling, s -> this.session.sight(dim, s));
			this.dimension.detections.chunk(pos.x(), pos.z(), tally, s -> this.session.sight(dim, s));
			Perf.DETECT_NANOS.addAndGet(System.nanoTime() - t1);
			Perf.DETECTED.incrementAndGet();
		}
	}

	/** The band the player was last in underground, or {@link Integer#MIN_VALUE}. */
	public int band() {
		return this.band;
	}
}
