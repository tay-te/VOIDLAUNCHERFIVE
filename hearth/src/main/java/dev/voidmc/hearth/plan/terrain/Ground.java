package dev.voidmc.hearth.plan.terrain;

import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The planner's view of a {@link TerrainProbe}: ground and water heights through a cache, because planning reads the
 * same columns many times and a generator-backed probe is not free.
 *
 * <p>Columns are kept in 16×16 tiles in a store shared by every planning call on the same probe (bounded, least
 * recently used tiles dropped), and filled lazily one column at a time, so a coarse site survey does not pay for the
 * columns between its samples. Values are pure functions of the probe, so sharing them between threads cannot change a
 * plan; tiles are written racily but every racing writer writes the same value. Each {@code Ground} also keeps the
 * tiles it touched in a small local map so a call does not take the store's lock for every column. Not thread-safe
 * itself: make one per planning call.
 */
public final class Ground {
	private static final short UNKNOWN = Short.MIN_VALUE;
	private static final short DRY = Short.MIN_VALUE + 1;
	private static final Map<TerrainProbe, TileStore> STORES = Collections.synchronizedMap(new WeakHashMap<>());
	private static volatile int storeCapacity = 4096;

	private final TerrainProbe probe;
	private final TileStore store;
	private final Long2ObjectOpenHashMap<Tile> local = new Long2ObjectOpenHashMap<>();
	private long lastKey = Long.MIN_VALUE;
	private Tile lastTile;

	public Ground(TerrainProbe probe) {
		this.probe = probe;
		this.store = STORES.computeIfAbsent(probe, p -> new TileStore(storeCapacity));
	}

	/** Bounds the shared store (in tiles of 256 columns, about 1 KB each). Applies to stores created afterwards. */
	public static void setStoreCapacity(int tiles) {
		storeCapacity = Math.max(64, tiles);
	}

	public TerrainProbe probe() {
		return this.probe;
	}

	public int seaLevel() {
		return this.probe.seaLevel();
	}

	/** The natural ground's walking level: one above the highest solid block (under water, the bed). */
	public int ground(int x, int z) {
		Tile t = tile(x, z);
		int i = ((z & 15) << 4) | (x & 15);
		short g = t.ground[i];
		if (g == UNKNOWN) {
			g = (short) this.probe.groundY(x, z);
			t.ground[i] = g;
		}
		return g;
	}

	/** The water surface's walking level over (x, z) (the first non-water y above it), or {@link TerrainProbe#NO_WATER}. */
	public int water(int x, int z) {
		Tile t = tile(x, z);
		int i = ((z & 15) << 4) | (x & 15);
		short w = t.water[i];
		if (w == UNKNOWN) {
			int v = this.probe.waterY(x, z);
			w = v == TerrainProbe.NO_WATER ? DRY : (short) v;
			t.water[i] = w;
		}
		return w == DRY ? TerrainProbe.NO_WATER : w;
	}

	public boolean isWater(int x, int z) {
		return water(x, z) != TerrainProbe.NO_WATER;
	}

	/** Where one stands over (x, z): on the ground, or on the water's surface (a deck just above it). */
	public int surface(int x, int z) {
		int w = water(x, z);
		return w == TerrainProbe.NO_WATER ? ground(x, z) : Math.max(w, ground(x, z));
	}

	/**
	 * The surface smoothed over a few blocks (a weighted mean of five columns two apart): whole-block heights make every
	 * single step look like a cliff or a flat, so grades between neighbouring columns are measured on this instead.
	 */
	public double smooth(int x, int z) {
		return (surface(x, z) * 2.0 + surface(x + 2, z) + surface(x - 2, z) + surface(x, z + 2) + surface(x, z - 2)) / 6.0;
	}

	/** Water depth over (x, z), 0 on dry land. */
	public int depth(int x, int z) {
		int w = water(x, z);
		return w == TerrainProbe.NO_WATER ? 0 : Math.max(0, w - ground(x, z));
	}

	/** Lowest and highest ground in a rectangle (inclusive bounds), sampling every {@code step} blocks plus the edges. */
	public int[] range(int x0, int z0, int x1, int z1, int step) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int z = z0; ; z = Math.min(z + step, z1)) {
			for (int x = x0; ; x = Math.min(x + step, x1)) {
				int g = ground(x, z);
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
				if (x == x1) {
					break;
				}
			}
			if (z == z1) {
				break;
			}
		}
		return new int[]{lo, hi};
	}

	/** Steepest fall over a short baseline around (x, z): rise per block. */
	public double slope(int x, int z, int r) {
		double gx = (ground(x + r, z) - ground(x - r, z)) / (2.0 * r);
		double gz = (ground(x, z + r) - ground(x, z - r)) / (2.0 * r);
		return Math.sqrt(gx * gx + gz * gz);
	}

	private Tile tile(int x, int z) {
		long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
		if (key == this.lastKey) {
			return this.lastTile;
		}
		Tile t = this.local.get(key);
		if (t == null) {
			t = this.store.get(key);
			this.local.put(key, t);
		}
		this.lastKey = key;
		this.lastTile = t;
		return t;
	}

	private static final class Tile {
		final short[] ground = new short[256];
		final short[] water = new short[256];

		Tile() {
			Arrays.fill(this.ground, UNKNOWN);
			Arrays.fill(this.water, UNKNOWN);
		}
	}

	private static final class TileStore {
		private final LinkedHashMap<Long, Tile> tiles;

		TileStore(int capacity) {
			this.tiles = new LinkedHashMap<>(256, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Long, Tile> eldest) {
					return size() > capacity;
				}
			};
		}

		synchronized Tile get(long key) {
			return this.tiles.computeIfAbsent(key, k -> new Tile());
		}
	}
}
