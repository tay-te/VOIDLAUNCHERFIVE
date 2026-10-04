package dev.voidmc.lod.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How high the land stands, at every level of the quadtree, for the lighting: a grid of surface heights on
 * each tile's cell corners, the same columns the smooth mesh is built on. A tile's lighting looks past its
 * own samples out to the horizon through these grids, coarser the further it looks.
 *
 * <p>Grids come only from the tiles as they are built: sampling one nobody built costs as much as building the
 * tile (thousands of columns), and lighting a tile must cost a fraction of building it. Where a level's grid is
 * missing the next coarser one stands in. Tiles are built coarse first and a tile's ancestors stay resident as
 * its fallback, so the coarse grids that carry the long shadows are nearly always there; what a tile may miss is
 * a fine grid next to it, which only moves a shadow's edge by a coarse cell.
 *
 * <p>Thread-safe: every worker reads and fills it. Bounded: past {@value #MAX_GRIDS} fine grids the oldest go.
 */
public final class Heights {
	static final int V = TileKey.TILE + 1;
	/** Fine grids kept, 8 KB each. */
	private static final int MAX_GRIDS = 2048;
	/** Grids from this level up (2 km tiles and coarser) are never let go: a 16 km reach has a few hundred. */
	private static final int COARSE = 5;
	/** A column with no ground (the void, an End island's edge): nothing to cast a shadow. */
	static final short NONE = Short.MIN_VALUE;

	/** Lookups no grid at the asked level could answer, which a coarser one did (or none). */
	public final AtomicLong coarser = new AtomicLong();

	private final ConcurrentHashMap<Long, short[]> grids = new ConcurrentHashMap<>();
	/** The fine grids, oldest first: only these are let go. The coarse ones carry every long shadow, and are few. */
	private final ConcurrentLinkedQueue<Long> fine = new ConcurrentLinkedQueue<>();

	/** The surface a column shows from afar: water, or the ground under a forest's canopy. */
	static float surface(float ground, int water, float canopy, float cover) {
		if (water != Integer.MIN_VALUE && water > ground) {
			return water;
		}
		return ground + Smooth.blanket(canopy, cover);
	}

	/** Takes a built tile's corner grid, row by row ({@code V x V}, in blocks). */
	void put(long key, short[] grid) {
		if (this.grids.putIfAbsent(key, grid) == null && TileKey.level(key) < COARSE) {
			this.fine.add(key);
			this.trim();
		}
	}

	/** Samples tile {@code key}'s grid from {@code source} and keeps it, as building the tile would: for tests and tools. */
	void fill(TerrainSource source, long key) {
		int cell = TileKey.cell(TileKey.level(key));
		int x0 = TileKey.minX(key), z0 = TileKey.minZ(key);
		ColumnSample s = new ColumnSample();
		short[] g = new short[V * V];
		for (int j = 0; j < V; j++) {
			for (int i = 0; i < V; i++) {
				s.clear();
				source.sample(x0 + i * cell, z0 + j * cell, cell, s);
				g[i + j * V] = s.empty ? NONE : clamp(surface(s.ground, s.water, s.canopy, s.cover));
			}
		}
		this.put(key, g);
	}

	/**
	 * The surface at block {@code (x, z)}, interpolated over the grid there at level {@code level} or, missing that,
	 * the first coarser one built; NaN where no grid covers it or the land has no ground.
	 */
	float at(double x, double z, int level) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int l = level; l <= TileKey.MAX_LEVEL; l++) {
			int span = TileKey.span(l);
			int tx = Math.floorDiv(bx, span), tz = Math.floorDiv(bz, span);
			short[] g = this.grids.get(TileKey.of(l, tx, tz));
			if (g != null) {
				return interpolate(g, x, z, l, tx, tz);
			}
			if (l == level) {
				this.coarser.incrementAndGet();
			}
		}
		return Float.NaN;
	}

	private static float interpolate(short[] g, double x, double z, int level, int tx, int tz) {
		int span = TileKey.span(level);
		int cell = TileKey.cell(level);
		double fx = (x - (double) tx * span) / cell;
		double fz = (z - (double) tz * span) / cell;
		int i = Math.min(V - 2, Math.max(0, (int) fx));
		int j = Math.min(V - 2, Math.max(0, (int) fz));
		double u = fx - i, w = fz - j;
		short a = g[i + j * V], b = g[i + 1 + j * V], c = g[i + (j + 1) * V], d = g[i + 1 + (j + 1) * V];
		if (a == NONE || b == NONE || c == NONE || d == NONE) {
			return Float.NaN;
		}
		return (float) ((a * (1 - u) + b * u) * (1 - w) + (c * (1 - u) + d * u) * w);
	}

	static short clamp(float h) {
		return (short) Math.max(-32767, Math.min(32767, Math.round(h)));
	}

	private void trim() {
		while (this.fine.size() > MAX_GRIDS) {
			Long oldest = this.fine.poll();
			if (oldest == null) {
				return;
			}
			this.grids.remove(oldest);
		}
	}
}
