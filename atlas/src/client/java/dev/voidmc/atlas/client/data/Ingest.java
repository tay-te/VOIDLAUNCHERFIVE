package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Feeds the client's chunks to the workers. On the client thread it only queues (a chunk arrived, a block changed,
 * the player moved into another slice); the reading is done on the workers ({@link ChunkScanner}). Changed chunks are
 * read again once their changes have been quiet a moment, so an explosion or a long /fill is one read, not thousands.
 */
public final class Ingest {
	private static final long QUIET_NANOS = 750_000_000L;
	private static final long LONGEST_NANOS = 4_000_000_000L;
	/** Client-thread time each frame may spend handing chunks to the workers. */
	private static final long BUDGET_NANOS = 400_000L;
	/** How far round the player a new slice is read when the player moves into it. */
	private static final int SLICE_RADIUS = 10;

	private final MapSession session;
	private final ClientLevel level;
	private final DimensionData dimension;
	private final ArrayDeque<LevelChunk> arrived = new ArrayDeque<>();
	/** Chunks round the player to read the new slice of, after a move into another slice. */
	private final ArrayDeque<Long> sliceQueue = new ArrayDeque<>();
	private final Long2LongOpenHashMap firstChange = new Long2LongOpenHashMap();
	private final Long2LongOpenHashMap lastChange = new Long2LongOpenHashMap();
	/** Scans queued and not yet started: chunk key to what to read (1 surface, 2 slice). */
	private final ConcurrentHashMap<Long, Integer> queued = new ConcurrentHashMap<>();
	private int band = Integer.MIN_VALUE;
	private int sliceX;
	private int sliceZ;
	private long lastSettle;
	/** The player's slice, as the workers should read it: also read for each chunk that arrives. */
	private volatile int scanBand = Integer.MIN_VALUE;

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
		return this.arrived.size() + this.sliceQueue.size() + this.queued.size() + this.lastChange.size();
	}

	/** Once per frame on the client thread: hands work to the workers within a small time budget. */
	public void frame(double playerX, double playerY, double playerZ) {
		long start = System.nanoTime();
		int band = Math.floorDiv((int) Math.floor(playerY), 16);
		if (this.band != Integer.MIN_VALUE && band != this.band && playerY >= this.band * 16 - 6 && playerY < this.band * 16 + 22) {
			// a little hysteresis: walking along a slice's edge must not reread the slices round the player every step
			band = this.band;
		}
		int pcx = (int) Math.floor(playerX) >> 4;
		int pcz = (int) Math.floor(playerZ) >> 4;
		if (band != this.band) {
			// a new slice: read it round the player
			this.band = band;
			this.scanBand = band;
			this.sliceX = pcx;
			this.sliceZ = pcz;
			this.sliceQueue.clear();
			for (int r = 0; r <= SLICE_RADIUS; r++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dx = -r; dx <= r; dx++) {
						if (Math.abs(dx) == r || Math.abs(dz) == r) {
							this.sliceQueue.add(ChunkPos.pack(pcx + dx, pcz + dz));
						}
					}
				}
			}
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
						this.submit(chunk, true, true);
					}
					this.firstChange.remove(key);
					it.remove();
				}
			}
		}
		while (!this.arrived.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
			LevelChunk chunk = this.arrived.poll();
			this.submit(chunk, true, true);
		}
		while (!this.sliceQueue.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
			long key = this.sliceQueue.poll();
			LevelChunk chunk = this.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
			if (chunk != null) {
				this.submit(chunk, false, true);
			}
		}
		Perf.ingest(System.nanoTime() - start);
	}

	private void submit(LevelChunk chunk, boolean surface, boolean slice) {
		ChunkPos pos = chunk.getPos();
		long key = ChunkPos.pack(pos.x(), pos.z());
		int what = (surface ? 1 : 0) | (slice ? 2 : 0);
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
				this.scan(latest != null ? latest : chunk, (flags & 1) != 0 && !this.dimension.ceiling, (flags & 2) != 0);
			} catch (Throwable t) {
				// a chunk changing under the reader: it is read again on its next change
				if (Boolean.getBoolean("atlas.logStats")) {
					MapSession.LOGGER.debug("Atlas: scan of {} failed: {}", pos, t.toString());
				}
			}
		});
	}

	private void scan(LevelChunk chunk, boolean surface, boolean slice) {
		long t0 = System.nanoTime();
		ChunkScanner scanner = this.session.scanner();
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		int rx = Coords.region(x0);
		int rz = Coords.region(z0);
		if (surface) {
			LayerData layer = this.dimension.layer(DimensionData.SURFACE);
			RegionRaw raw = layer.raw(rx, rz, true);
			if (scanner.surface(chunk, raw)) {
				layer.dirty(x0, z0, x0 + 16, z0 + 16);
			}
		}
		int band = this.scanBand;
		if (slice && band != Integer.MIN_VALUE) {
			LayerData layer = this.dimension.layer("cave_" + band);
			RegionRaw raw = layer.raw(rx, rz, true);
			if (scanner.slice(chunk, raw, band)) {
				layer.dirty(x0, z0, x0 + 16, z0 + 16);
			}
		}
		Perf.SCAN_NANOS.addAndGet(System.nanoTime() - t0);
		Perf.SCANNED.incrementAndGet();
	}

	/** The player's slice band, as last seen. */
	public int band() {
		return this.band;
	}
}
