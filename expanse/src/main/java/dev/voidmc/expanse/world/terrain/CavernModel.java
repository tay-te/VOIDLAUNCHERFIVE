package dev.voidmc.expanse.world.terrain;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The great tunnels under the Earth terrain, the halls they lead to, and the rivers that run along them.
 *
 * <p>Not open space everywhere, but a network you follow:
 * <ul>
 *   <li><b>Great tunnels.</b> Two families of winding tunnels, each following the zero lines of a warped
 *       noise, a few hundred blocks apart: 14 to 28 blocks wide, 16 to 34 high under an arched roof, their
 *       floors rising and falling slowly between y 12 and 44. One family carries rivers: a channel down the
 *       middle and a dry ledge either side, the water holding one level for long stretches and dropping
 *       six blocks at a time in waterfalls.</li>
 *   <li><b>Halls.</b> Rare domed caverns, up to two hundred blocks across and eighty high, held up by stone
 *       pillars. A hall only opens where a tunnel runs close by, at that tunnel's floor, so every hall is
 *       found by following a tunnel into it. Halls are where underground structures stand
 *       ({@link #hallNear}).</li>
 * </ul>
 * Nothing comes nearer than {@value #ROOF_COVER} blocks to the lowest ground within a few dozen blocks,
 * so no hillside breaks open into a tunnel, and nothing lies under the sea or a lake, which would flood it.
 *
 * <p>Everything is decided per column and cached; the density function then only measures how far a
 * point is inside that space, roughened by a little 3D noise.
 */
public final class CavernModel {
	private static final int ROOF_COVER = 16;
	static final int HALL_CELL = 560;
	private static final int COVER_CELL = 32;

	private final TerrainModel terrain;
	private final SimplexNoise[] n = new SimplexNoise[20];
	private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

	/** What a column holds, underground. */
	public static final class Info {
		/** The lowest a roof may come here: the lowest ground round about, less the rock that must stay. */
		public float cap;
		// the river tunnel family
		public boolean river;
		public float riverDist;
		public float riverHalfWidth;
		public float riverWater;
		public float riverDepth;
		public float tunnelRoof;
		/** The river pours out of its tunnel here into a hall far below: no water in this column. */
		public boolean riverFalls;
		// the dry tunnel family
		public boolean tunnel;
		public float tunnelDist;
		public float tunnelHalfWidth;
		public float tunnelFloor;
		public float tunnelHeight;
		// the nearest hall
		public boolean hall;
		public int hallX;
		public int hallZ;
		public float hallRadius;
		/** How far inside the hall's outline the column is (blocks; negative outside). */
		public float hallEdge;
		public float floor;
		public float roof;
		/** Distance to the nearest pillar's surface (blocks; large if none). */
		public float pillar;

		public boolean inChannel() {
			return this.river && !this.riverFalls && this.riverDist < this.riverHalfWidth;
		}

		/** The water surface as a block height: water fills every y below it. */
		public int waterTop() {
			return Mth.floor(this.riverWater);
		}

		public int bed() {
			float across = this.riverDist / Math.max(0.5F, this.riverHalfWidth);
			float depth = this.riverDepth * (float) Math.sqrt(Math.max(0, 1 - across * across));
			return this.waterTop() - Math.max(1, Math.round(depth));
		}
	}

	/** A hall's centre, size and floor, decided per hall cell. */
	public record Hall(boolean exists, int x, int z, float radius, float floor, float height) {
	}

	private static final class Cache {
		static final int SIZE = 2048;
		final long[] keys = new long[SIZE];
		final Info[] infos = new Info[SIZE];
		final long[] coverKeys = new long[256];
		final float[] cover = new float[256];
		final boolean[] coverSet = new boolean[256];
		final long[] hallKeys = new long[64];
		final Hall[] halls = new Hall[64];
	}

	CavernModel(TerrainModel terrain, long seed) {
		this.terrain = terrain;
		RandomSource random = RandomSource.create(seed ^ 0x4341_5645_524E_53L);
		for (int i = 0; i < this.n.length; i++) {
			this.n[i] = new SimplexNoise(random);
		}
	}

	private double noise(int i, double x, double z) {
		return Mth.clamp(this.n[i].get(x, z), -1.0F, 1.0F);
	}

	/** The column at (x, z), from a small per-thread cache. Read it before asking for another. */
	public Info info(int x, int z) {
		Cache c = this.cache.get();
		long key = (long) x << 32 ^ z & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(1, x, z) & Cache.SIZE - 1);
		Info info = c.infos[slot];
		if (info != null && c.keys[slot] == key) {
			return info;
		}
		if (info == null) {
			info = c.infos[slot] = new Info();
		}
		this.compute(x, z, info, c);
		c.keys[slot] = key;
		return info;
	}

	private void compute(int x, int z, Info out, Cache c) {
		// rock cover: the lowest ground over the 2x2 cover cells round the column
		int cx = Math.floorDiv(x - COVER_CELL / 2, COVER_CELL);
		int cz = Math.floorDiv(z - COVER_CELL / 2, COVER_CELL);
		float ground = Math.min(Math.min(this.cover(c, cx, cz), this.cover(c, cx + 1, cz)), Math.min(this.cover(c, cx, cz + 1), this.cover(c, cx + 1, cz + 1)));
		out.cap = ground < 66 ? -999 : ground - ROOF_COVER;

		// The river tunnels
		out.riverDist = this.tunnelDistance(8, x, z, 900.0, 6);
		out.riverHalfWidth = (float) (7 + 5 * (0.5 + 0.5 * this.noise(9, x / 600.0, z / 600.0)));
		float riverFloor = (float) (28 + 16 * this.noise(10, x / 1000.0, z / 1000.0));
		out.riverWater = 6 * Mth.floor(riverFloor / 6) + 2;
		out.riverDepth = 1.5F + out.riverHalfWidth * 0.3F;
		float span = out.riverHalfWidth + 4;
		float riverHeight = (float) (18 + 14 * (0.5 + 0.5 * this.noise(11, x / 700.0, z / 700.0)));
		out.tunnelRoof = out.riverWater + 1 + riverHeight * (float) Math.sqrt(Math.max(0, 1 - sq(out.riverDist / span)));
		boolean riversHere = this.noise(12, x / 2000.0, z / 2000.0) > -0.2;
		out.river = riversHere && out.riverDist < span && out.riverWater + 1 + riverHeight < out.cap;

		// The dry tunnels
		out.tunnelDist = this.tunnelDistance(14, x, z, 700.0, 7);
		out.tunnelHalfWidth = (float) (6 + 6 * (0.5 + 0.5 * this.noise(15, x / 500.0, z / 500.0)));
		out.tunnelFloor = (float) (28 + 16 * this.noise(16, x / 1100.0, z / 1100.0) + 1.5 * this.noise(17, x / 40.0, z / 40.0));
		out.tunnelHeight = (float) (16 + 18 * (0.5 + 0.5 * this.noise(18, x / 650.0, z / 650.0)));
		out.tunnel = out.tunnelDist < out.tunnelHalfWidth && out.tunnelFloor + out.tunnelHeight < out.cap;

		// The nearest hall
		Hall best = null;
		float bestEdge = -Float.MAX_VALUE;
		int hx = Math.floorDiv(x, HALL_CELL);
		int hz = Math.floorDiv(z, HALL_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Hall h = this.hall(c, hx + dx, hz + dz);
				if (!h.exists()) {
					continue;
				}
				double ragged = Math.hypot(x - h.x(), z - h.z()) + 22 * this.noise(19, x / 70.0, z / 70.0);
				float edge = (float) (h.radius() - ragged);
				if (edge > bestEdge) {
					bestEdge = edge;
					best = h;
				}
			}
		}
		out.hall = best != null && bestEdge > -8;
		out.hallEdge = bestEdge;
		if (best != null) {
			out.hallX = best.x();
			out.hallZ = best.z();
			out.hallRadius = best.radius();
			// a dome: highest at the centre, coming down toward the walls
			float across = Mth.clamp(1 - bestEdge / best.radius(), 0, 1);
			float dome = best.height() * (float) Math.sqrt(Math.max(0.05, 1 - across * across));
			out.floor = best.floor() + (float) (2.5 * this.noise(17, x / 33.0, z / 33.0));
			out.roof = Math.min(out.cap, best.floor() + 6 + dome);
			if (out.roof - out.floor < 6) {
				out.hall = false;
			}
		}
		out.pillar = this.pillarDistance(x, z);

		// a river meeting a hall: across its floor if the floor is near its level, else out of the wall
		out.riverFalls = false;
		if (out.river && out.hall && out.hallEdge > 0) {
			if (out.floor < out.riverWater - 4) {
				out.riverFalls = true;
			} else if (out.floor < out.riverWater + 1) {
				out.floor = out.riverWater + 1;
			}
		}
	}

	/** Distance (blocks) to the zero line of warped noise {@code k} at the given scale: the course of a tunnel. */
	private float tunnelDistance(int k, int x, int z, double scale, int warpNoise) {
		double wx = x + 110 * this.noise(warpNoise, x / 380.0, z / 380.0);
		double wz = z + 110 * this.noise(warpNoise, x / 380.0 + 71, z / 380.0);
		double p = this.noise(k, wx / scale, wz / scale);
		double gx = (this.noise(k, (wx + 2) / scale, wz / scale) - this.noise(k, (wx - 2) / scale, wz / scale)) / 4;
		double gz = (this.noise(k, wx / scale, (wz + 2) / scale) - this.noise(k, wx / scale, (wz - 2) / scale)) / 4;
		return (float) (Math.abs(p) / Math.max(1.0E-4, Math.sqrt(gx * gx + gz * gz)));
	}

	/** The lowest ground height over a cover cell (its corners and centre). */
	private float cover(Cache c, int cx, int cz) {
		long key = (long) cx << 32 ^ cz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(2, cx, cz) & 255);
		if (c.coverSet[slot] && c.coverKeys[slot] == key) {
			return c.cover[slot];
		}
		int x0 = cx * COVER_CELL;
		int z0 = cz * COVER_CELL;
		float low = Float.MAX_VALUE;
		int[][] at = {{0, 0}, {COVER_CELL, 0}, {0, COVER_CELL}, {COVER_CELL, COVER_CELL}, {COVER_CELL / 2, COVER_CELL / 2}};
		for (int[] o : at) {
			low = Math.min(low, this.terrain.sample(x0 + o[0], z0 + o[1]).height);
		}
		c.coverKeys[slot] = key;
		c.cover[slot] = low;
		c.coverSet[slot] = true;
		return low;
	}

	/** The hall of hall cell (hx, hz), if it has one: about half do, where a tunnel runs near its centre. */
	public Hall hall(int hx, int hz) {
		return this.hall(this.cache.get(), hx, hz);
	}

	private Hall hall(Cache c, int hx, int hz) {
		long key = (long) hx << 32 ^ hz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(3, hx, hz) & 63);
		Hall cached = c.halls[slot];
		if (cached != null && c.hallKeys[slot] == key) {
			return cached;
		}
		long h = PlateMap.mix(0x48414C4CL, hx, hz);
		int x = (int) ((hx + 0.25 + 0.5 * PlateMap.unit(h)) * HALL_CELL);
		int z = (int) ((hz + 0.25 + 0.5 * PlateMap.unit(h >>> 21)) * HALL_CELL);
		float radius = 50 + 60 * (float) PlateMap.unit(h >>> 42);
		float height = 40 + 40 * (float) PlateMap.unit(h >>> 7);
		Hall hall = new Hall(false, x, z, radius, 0, 0);
		if (PlateMap.unit(h >>> 30) < 0.55) {
			// it opens off whichever tunnel runs nearest its centre, at that tunnel's floor
			float river = this.tunnelDistance(8, x, z, 900.0, 6);
			float dry = this.tunnelDistance(14, x, z, 700.0, 7);
			if (Math.min(river, dry) < radius * 0.6F) {
				float floor = river < dry
					? 6 * Mth.floor((float) (28 + 16 * this.noise(10, x / 1000.0, z / 1000.0)) / 6) + 3
					: (float) (28 + 16 * this.noise(16, x / 1100.0, z / 1100.0));
				hall = new Hall(true, x, z, radius, floor, height);
			}
		}
		c.hallKeys[slot] = key;
		c.halls[slot] = hall;
		return hall;
	}

	/**
	 * The hall whose centre lies within {@code reach} blocks of (x, z), with room for a structure of the
	 * given height under its roof, or null: where underground structures go.
	 */
	public Hall hallNear(int x, int z, int reach, int height) {
		int hx = Math.floorDiv(x, HALL_CELL);
		int hz = Math.floorDiv(z, HALL_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Hall h = this.hall(hx + dx, hz + dz);
				if (h.exists() && Math.hypot(h.x() - x, h.z() - z) <= reach) {
					Info centre = this.info(h.x(), h.z());
					if (centre.hall && centre.roof - h.floor() >= height + 6) {
						return h;
					}
				}
			}
		}
		return null;
	}

	/** Distance (blocks) from (x, z) to the surface of the nearest stone pillar. */
	private float pillarDistance(int x, int z) {
		int cell = 30;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float best = 99;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x50494C4CL, cx + dx, cz + dz);
				if (PlateMap.unit(h) > 0.4) {
					continue;
				}
				double px = (cx + dx + 0.2 + 0.6 * PlateMap.unit(h >>> 21)) * cell;
				double pz = (cz + dz + 0.2 + 0.6 * PlateMap.unit(h >>> 42)) * cell;
				float r = 3 + 4 * (float) PlateMap.unit(h >>> 7);
				best = Math.min(best, (float) Math.hypot(x - px, z - pz) - r);
			}
		}
		return best;
	}

	/**
	 * How far (blocks) the point is inside open space: positive inside a tunnel or a hall, negative in the
	 * rock, by roughly the distance to the nearest wall, floor or roof.
	 */
	public float inside(int x, int y, int z) {
		Info c = this.info(x, z);
		float best = -64;
		if (c.river) {
			float span = c.riverHalfWidth + 4;
			float bottom = c.riverDist < c.riverHalfWidth ? c.bed() - 0.5F : c.riverWater + 1;
			best = Math.max(best, Math.min(Math.min(y - bottom, c.tunnelRoof - y), span - c.riverDist));
		}
		if (c.tunnel) {
			float roof = c.tunnelFloor + c.tunnelHeight * (float) Math.sqrt(Math.max(0, 1 - sq(c.tunnelDist / c.tunnelHalfWidth)));
			best = Math.max(best, Math.min(Math.min(y - c.tunnelFloor, roof - y), c.tunnelHalfWidth - c.tunnelDist));
		}
		if (c.hall) {
			float vertical = Math.min(y - c.floor, c.roof - y);
			// pillars flare where they meet floor and roof
			float flare = 3 * (1 - Mth.clamp(vertical / 8.0F, 0, 1));
			best = Math.max(best, Math.min(Math.min(vertical, c.hallEdge), c.pillar - flare));
		}
		if (best > -6) {
			best += (float) (2.4 * this.n[13].get(x / 22.0, y / 14.0, z / 22.0));
		}
		return best;
	}

	private static float sq(float v) {
		return v * v;
	}
}
