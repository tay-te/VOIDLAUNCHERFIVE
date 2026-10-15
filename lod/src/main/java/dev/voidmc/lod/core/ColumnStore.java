package dev.voidmc.lod.core;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Everything the LOD has seen of a world's real chunks, by region: each loaded chunk's columns
 * ({@link ChunkColumns}) and, over them, a pyramid of summaries for the far field's coarse cells (32 to 256 blocks
 * across), kept up to date as chunks come in, so a cell of any size is at most four lookups.
 *
 * <p>Thread-safe the cheap way round: workers read without locks (every chunk and every summary is immutable,
 * swapped in whole through atomic arrays), and the few writers (the client thread ingesting chunks, the file
 * thread loading them) lock only the region they change. A listener hears of every change, so the tiles over it
 * can be rebuilt.
 */
public final class ColumnStore {
	/** Told the block area (max exclusive) whose columns just changed, and how many of them did. Called on the writer's thread. */
	public interface Listener {
		void changed(int minX, int minZ, int maxX, int maxZ, int columns);
	}

	/** A region is 32 x 32 chunks, 512 blocks a side: one coarsest LOD cell, and one file on disk. */
	public static final int REGION_SHIFT = 5;
	public static final int REGION_CHUNKS = 1 << REGION_SHIFT;
	public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
	/** Where each level of the pyramid starts (level 1: 32-block cells, 16 x 16 to a region; up to level 4: 256 blocks, 2 x 2). */
	private static final int[] LEVEL_AT = {0, 0, 256, 320, 336};
	/** The largest square {@link #summary} answers for, in blocks. */
	public static final int MAX_SUMMARY = 16 << (LEVEL_AT.length - 1);
	private static final int PYRAMID = 340;

	public static final class Region {
		public final int rx;
		public final int rz;
		final AtomicReferenceArray<ChunkColumns> chunks = new AtomicReferenceArray<>(REGION_CHUNKS * REGION_CHUNKS);
		final AtomicReferenceArray<int[]> pyramid = new AtomicReferenceArray<>(PYRAMID);
		/** Bumped with every change from ingestion (not from loading), so the files know what needs saving. */
		volatile long changes;
		volatile int count;

		Region(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
		}

		/** The chunk at {@code index} ({@code x + z * 32} within the region), or null. */
		public ChunkColumns chunk(int index) {
			return this.chunks.get(index);
		}

		public long changes() {
			return this.changes;
		}

		public int count() {
			return this.count;
		}
	}

	private final ConcurrentHashMap<Long, Region> regions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Surface, Surface> surfaces = new ConcurrentHashMap<>();
	private volatile Listener listener;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	/** A region's key in {@link #regions}: {@link #key} mixed, since x and z packed side by side collide along diagonals in a hash map. */
	private static long slot(int rx, int rz) {
		return it.unimi.dsi.fastutil.HashCommon.mix(key(rx, rz));
	}

	public static long key(int rx, int rz) {
		return (long) rx << 32 | rz & 0xFFFFFFFFL;
	}

	/** The one instance of an equal surface, so chunks share them and compare them by identity. */
	public Surface intern(Surface surface) {
		Surface had = this.surfaces.putIfAbsent(surface, surface);
		return had != null ? had : surface;
	}

	public Region region(int rx, int rz) {
		return this.regions.get(slot(rx, rz));
	}

	public Collection<Region> regions() {
		return this.regions.values();
	}

	public ChunkColumns chunk(int cx, int cz) {
		Region r = this.region(cx >> REGION_SHIFT, cz >> REGION_SHIFT);
		return r == null ? null : r.chunks.get(index(cx, cz));
	}

	static int index(int cx, int cz) {
		return (cx & (REGION_CHUNKS - 1)) + (cz & (REGION_CHUNKS - 1)) * REGION_CHUNKS;
	}

	/**
	 * Puts a chunk just read from the world. Returns false if nothing about it changed since the last time it was
	 * put (the same chunk sent again, a cave dug, a chest opened). The listener hears only of what a far view shows.
	 */
	public boolean put(int cx, int cz, ChunkColumns data) {
		int rx = cx >> REGION_SHIFT;
		int rz = cz >> REGION_SHIFT;
		Region r = this.regions.computeIfAbsent(slot(rx, rz), k -> new Region(rx, rz));
		long change;
		synchronized (r) {
			int i = index(cx, cz);
			ChunkColumns old = r.chunks.get(i);
			if (old != null && old.equals(data)) {
				return false;
			}
			// the whole chunk is new, or only the columns that differ
			change = old == null ? 15 << 8 | 15 << 12 | 256L << 16 : data.changedSince(old);
			r.chunks.set(i, data);
			if (old == null) {
				r.count++;
			}
			r.changes++;
			this.update(r, cx & (REGION_CHUNKS - 1), cz & (REGION_CHUNKS - 1));
		}
		Listener l = this.listener;
		if (l != null && change >= 0) {
			int x = cx << 4;
			int z = cz << 4;
			int b = (int) change;
			l.changed(x + (b & 15), z + (b >> 4 & 15), x + (b >> 8 & 15) + 1, z + (b >> 12 & 15) + 1, (int) (change >> 16));
		}
		return true;
	}

	/**
	 * Puts a region's chunks read back from disk ({@code chunks[index]}, nulls skipped), only where nothing newer is
	 * known: a chunk ingested since the world was joined wins over its saved copy. One change for the whole region.
	 */
	public void load(int rx, int rz, ChunkColumns[] chunks) {
		Region r = this.regions.computeIfAbsent(slot(rx, rz), k -> new Region(rx, rz));
		int added = 0;
		synchronized (r) {
			for (int i = 0; i < chunks.length; i++) {
				if (chunks[i] != null && r.chunks.get(i) == null) {
					r.chunks.set(i, chunks[i]);
					added++;
				}
			}
			r.count += added;
			ColumnSummary sum = new ColumnSummary();
			for (int level = 1; level <= 4; level++) {
				int n = REGION_CHUNKS >> level;
				for (int pz = 0; pz < n; pz++) {
					for (int px = 0; px < n; px++) {
						this.cell(r, level, px, pz, sum);
					}
				}
			}
		}
		Listener l = this.listener;
		if (added > 0 && l != null) {
			l.changed(rx * REGION_BLOCKS, rz * REGION_BLOCKS, (rx + 1) * REGION_BLOCKS, (rz + 1) * REGION_BLOCKS, added * 256);
		}
	}

	/** Forgets a region, unless it changed since {@code changes} (it was saved at that count). */
	public boolean drop(int rx, int rz, long changes) {
		Region r = this.region(rx, rz);
		if (r == null) {
			return true;
		}
		synchronized (r) {
			return r.changes == changes && this.regions.remove(slot(rx, rz), r);
		}
	}

	/** Re-sums the pyramid over chunk (lx, lz) of a region, level by level up to its 256-block cell. */
	private void update(Region r, int lx, int lz) {
		ColumnSummary sum = new ColumnSummary();
		for (int level = 1; level <= 4; level++) {
			this.cell(r, level, lx >> level, lz >> level, sum);
		}
	}

	private void cell(Region r, int level, int px, int pz, ColumnSummary sum) {
		sum.clear();
		int below = REGION_CHUNKS >> (level - 1);
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < 2; i++) {
				int cx = 2 * px + i;
				int cz = 2 * pz + j;
				int[] child;
				if (level == 1) {
					ChunkColumns c = r.chunks.get(cx + cz * REGION_CHUNKS);
					child = c == null ? null : c.summary;
				} else {
					child = r.pyramid.get(LEVEL_AT[level - 1] + cx + cz * below);
				}
				if (child != null) {
					sum.merge(child);
				}
			}
		}
		boolean any = sum.known() + sum.none() > 0;
		r.pyramid.set(LEVEL_AT[level] + px + pz * (REGION_CHUNKS >> level), any ? sum.freeze() : null);
	}

	/**
	 * The summary of the {@code size}-block square at block {@code (x, z)}, aligned to its size (16: a chunk, up to
	 * 256), or null where nothing in it is known.
	 */
	static int[] summary(Region r, int x, int z, int size) {
		if (size == 16) {
			ChunkColumns c = r.chunks.get(index(x >> 4, z >> 4));
			return c == null ? null : c.summary;
		}
		int level = Integer.numberOfTrailingZeros(size) - 4;
		int n = REGION_CHUNKS >> level;
		int px = (x & (REGION_BLOCKS - 1)) / size;
		int pz = (z & (REGION_BLOCKS - 1)) / size;
		return r.pyramid.get(LEVEL_AT[level] + px + pz * n);
	}

	/** Whether any chunk in the block area (max exclusive) is known: a tile with none, and no generator, is not drawn. */
	public boolean any(int minX, int minZ, int maxX, int maxZ) {
		int cx0 = minX >> 4;
		int cz0 = minZ >> 4;
		int cx1 = (maxX - 1) >> 4;
		int cz1 = (maxZ - 1) >> 4;
		int rx0 = cx0 >> REGION_SHIFT;
		int rz0 = cz0 >> REGION_SHIFT;
		int rx1 = cx1 >> REGION_SHIFT;
		int rz1 = cz1 >> REGION_SHIFT;
		if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > this.regions.size()) {
			// a huge area: ask the regions there are, not the ones there could be
			for (Region r : this.regions.values()) {
				if (r.count > 0 && r.rx >= rx0 && r.rx <= rx1 && r.rz >= rz0 && r.rz <= rz1 && this.any(r, cx0, cz0, cx1, cz1)) {
					return true;
				}
			}
			return false;
		}
		for (int rz = rz0; rz <= rz1; rz++) {
			for (int rx = rx0; rx <= rx1; rx++) {
				Region r = this.region(rx, rz);
				if (r != null && r.count > 0 && this.any(r, cx0, cz0, cx1, cz1)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean any(Region r, int cx0, int cz0, int cx1, int cz1) {
		int bx = r.rx << REGION_SHIFT;
		int bz = r.rz << REGION_SHIFT;
		int x0 = Math.max(cx0, bx) - bx;
		int z0 = Math.max(cz0, bz) - bz;
		int x1 = Math.min(cx1, bx + REGION_CHUNKS - 1) - bx;
		int z1 = Math.min(cz1, bz + REGION_CHUNKS - 1) - bz;
		if (x0 == 0 && z0 == 0 && x1 == REGION_CHUNKS - 1 && z1 == REGION_CHUNKS - 1) {
			return true;
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (r.chunks.get(x + z * REGION_CHUNKS) != null) {
					return true;
				}
			}
		}
		return false;
	}

	public int chunkCount() {
		int n = 0;
		for (Region r : this.regions.values()) {
			n += r.count;
		}
		return n;
	}

	public int surfaceCount() {
		return this.surfaces.size();
	}
}
