package dev.voidmc.expanse.world.terrain;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The world under the Earth terrain: great tunnels, and the realms they lead to.
 *
 * <p>Not open space everywhere, but a network you follow:
 * <ul>
 *   <li><b>Great tunnels.</b> Two families of winding tunnels, each following the zero lines of a warped
 *       noise, a few hundred blocks apart: 12 to 24 blocks wide, 16 to 34 high under an arched roof, their
 *       floors rising and falling slowly between y 12 and 44. One family carries rivers: a channel down the
 *       middle and a dry ledge either side, the water holding one level for long stretches and dropping
 *       six blocks at a time in waterfalls.</li>
 *   <li><b>Realms.</b> Where a tunnel passes, about one cell in two of a {@value #HALL_CELL}-block grid holds a
 *       realm: a cavern from a hundred to over four hundred blocks across with a landscape of its own. The
 *       tunnel comes out high on its wall: a dry tunnel as a balcony with a scree ramp running down from it,
 *       a river as a waterfall into a plunge pool. Below lie hills or terraced mesas, lakes, stalagmite
 *       mountains (the tallest reach the roof as pillars), and a plateau in the middle where underground
 *       structures stand ({@link #hallNear}). Above, a vault up to 130 blocks high follows the land above it,
 *       hung with inverted peaks and held up by hourglass columns. Each realm has a character
 *       ({@link #WILDS}, {@link #LUSH}, ...) that decides its lakes, its relief and, in {@link CavernLife}, what
 *       grows there.</li>
 * </ul>
 * Nothing comes nearer than {@value #ROOF_COVER} blocks to the lowest ground within a few dozen blocks,
 * so no hillside breaks open into a tunnel, and nothing lies under the sea or a lake, which would flood it.
 * Aquifers are kept out of all of it ({@link #dry}); the water and lava here are poured, by
 * {@link EarthChunkGenerator}, where the model says, except below y -54, where every open space is lava
 * as in vanilla, so the deepest realms have lava seas.
 *
 * <p>Everything is decided per column and cached; the density function then only measures how far a
 * point is inside that space, roughened by a little 3D noise.
 */
public final class CavernModel {
	private static final int ROOF_COVER = 16;
	static final int HALL_CELL = 640;
	private static final int COVER_CELL = 32;
	/** No realm floor below this: under y -54 every open space is lava. */
	private static final float DEEPEST = -50;
	/** How far round every open space the aquifers are kept out (blocks). */
	private static final int DRY_MARGIN = 24;

	/** A forest of giant glowcaps on moss: the realm lit from beneath its mushroom caps. */
	public static final int WILDS = 0;
	/** Vanilla's lush caves at realm scale: moss hills, azaleas, ponds of dripleaf, vines hanging thick. */
	public static final int LUSH = 1;
	/** Stalagmite mountains and hanging spires, terraced flowstone, a few still pools. */
	public static final int DRIPSTONE = 2;
	/** Terraced mesas of calcite and tuff, crusted with amethyst and prismite. */
	public static final int CRYSTAL = 3;
	/** The deepest realms: basalt and magma round a lake of lava. */
	public static final int EMBER = 4;
	/** A lake realm: islands and stalagmite stacks standing in still water, glowing pickles on its bed. */
	public static final int MERE = 5;

	private final TerrainModel terrain;
	private final SimplexNoise[] n = new SimplexNoise[32];
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
		/** The river pours out of its tunnel here into a realm far below: no water in this column. */
		public boolean riverFalls;
		boolean riverOn;
		float riverTop;
		// the dry tunnel family
		public boolean tunnel;
		public float tunnelDist;
		public float tunnelHalfWidth;
		public float tunnelFloor;
		public float tunnelHeight;
		boolean tunnelOn;
		// the nearest realm
		public boolean hall;
		public int hallX;
		public int hallZ;
		public float hallRadius;
		/** How far inside the realm's outline the column is (blocks; negative outside). */
		public float hallEdge;
		public float floor;
		public float roof;
		/** Distance to the surface of the nearest column (blocks; large if none). */
		public float pillar;
		/** The realm's character, one of {@link #WILDS}... {@link #MERE}. */
		public int theme;
		/** The surface of the realm's lake: open space below it is filled (very low if the realm has none). */
		public float lake;
		/** Whether that lake is lava. */
		public boolean lava;
		/** The surface of a plunge pool under a waterfall, filled with water (very low if none). */
		public float pool;
		/** Near a realm, for {@link #dry}. */
		boolean nearRealm;

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

		/** The liquid surface of the realm here (lake or plunge pool), very low if none. */
		public float liquid() {
			return Math.max(this.lake, this.pool);
		}
	}

	/**
	 * A realm's centre, size and character, decided per realm cell. {@code floor} is the level of the
	 * plateau in its middle, where structures stand; {@code base} the general level of its floor,
	 * {@code height} the height of its vault above that.
	 */
	public record Hall(boolean exists, int x, int z, float radius, float floor, float height, float base, float relief,
		float scale, float terrace, float lake, int theme, float spires, float hangs, float columns, float tunnelFloor) {
		static final Hall NONE = new Hall(false, 0, 0, 0, 0, 0, 0, 0, 1, 0, -999, 0, 0, 0, 0, 0);
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
		out.cap = this.capAt(c, x, z);

		// The river tunnels
		out.riverDist = this.tunnelDistance(8, x, z, 900.0, 6);
		out.riverHalfWidth = (float) (7 + 5 * (0.5 + 0.5 * this.noise(9, x / 600.0, z / 600.0)));
		out.riverWater = this.riverLevel(x, z);
		out.riverDepth = 1.5F + out.riverHalfWidth * 0.3F;
		float span = out.riverHalfWidth + 4;
		float riverHeight = (float) (18 + 14 * (0.5 + 0.5 * this.noise(11, x / 700.0, z / 700.0)));
		out.riverTop = out.riverWater + 1 + riverHeight;
		out.tunnelRoof = out.riverWater + 1 + riverHeight * (float) Math.sqrt(Math.max(0, 1 - sq(out.riverDist / span)));
		out.riverOn = this.noise(12, x / 2000.0, z / 2000.0) > -0.2 && out.riverTop < out.cap;
		out.river = out.riverOn && out.riverDist < span;

		// The dry tunnels
		out.tunnelDist = this.tunnelDistance(14, x, z, 700.0, 7);
		out.tunnelHalfWidth = (float) (6 + 6 * (0.5 + 0.5 * this.noise(15, x / 500.0, z / 500.0)));
		out.tunnelFloor = (float) (this.dryLevel(x, z) + 1.5 * this.noise(17, x / 40.0, z / 40.0));
		out.tunnelHeight = (float) (16 + 18 * (0.5 + 0.5 * this.noise(18, x / 650.0, z / 650.0)));
		out.tunnelOn = out.tunnelFloor + out.tunnelHeight < out.cap;
		out.tunnel = out.tunnelOn && out.tunnelDist < out.tunnelHalfWidth;

		// The nearest realm: the one whose (ragged) outline the column is deepest inside
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
				float rough = 10 + 22 * Math.min(1, h.radius() / 160);
				double ragged = Math.hypot(x - h.x(), z - h.z()) + rough * this.noise(19, x / 110.0, z / 110.0)
					+ 0.45 * rough * this.noise(20, x / 38.0, z / 38.0);
				float edge = (float) (h.radius() - ragged);
				if (edge > bestEdge) {
					bestEdge = edge;
					best = h;
				}
			}
		}
		out.hall = false;
		out.nearRealm = false;
		out.hallEdge = bestEdge;
		out.lake = -999;
		out.pool = -999;
		out.lava = false;
		out.pillar = 99;
		if (best != null && bestEdge > -DRY_MARGIN - 8) {
			this.realm(best, x, z, bestEdge, out);
		}

		// a river meeting a realm pours out of its tunnel mouth; one across a realm's floor runs on through it
		out.riverFalls = false;
		if (out.river && out.hall && out.hallEdge > 0) {
			if (out.floor < out.riverWater - 4) {
				out.riverFalls = true;
			} else if (out.floor < out.riverWater + 1) {
				out.floor = out.riverWater + 1;
			}
		}
	}

	/** The land of a realm at one column: its floor, its roof, its columns and what fills its hollows. */
	private void realm(Hall h, int x, int z, float edge, Info out) {
		out.hallX = h.x();
		out.hallZ = h.z();
		out.hallRadius = h.radius();
		out.theme = h.theme();
		out.lava = h.theme() == EMBER;
		float inward = Math.max(0, edge);
		float centre = (float) Math.hypot(x - h.x(), z - h.z());

		// A lens, as great caverns are: the floor a bowl of hills (or terraced mesas) whose rubble slopes rise
		// to the walls, the vault curving down to meet them
		float across = Mth.clamp(inward / (h.radius() * 0.55F), 0, 1);
		float wallward = sq(1 - across);
		double s = h.scale();
		// broad swells, ridgelines with valleys between, and a little roughness
		float ridged = (float) (1 - 2 * Math.abs(this.noise(26, x / (s * 1.3), z / (s * 1.3))));
		float hills = (float) (0.55 * this.noise(21, x / s, z / s) + 0.15 * this.noise(22, x / (s / 3.0), z / (s / 3.0)))
			+ 0.45F * ridged * smooth((float) (0.5 + this.noise(27, x / 300.0, z / 300.0)));
		float relief = h.relief() * hills;
		if (h.terrace() > 0) {
			float t = h.terrace();
			float k = Mth.floor(relief / t);
			float f = relief / t - k;
			relief = t * (k + smooth((f - 0.62F) / 0.38F));
		}
		float floor = h.base() + relief + 0.42F * h.height() * wallward;
		// stalagmite mountains, the tallest reaching the roof, kept off the plateau in the middle
		float clear = smooth((centre - 34) / 18);
		floor += clear * this.cones(0x5350_4952L, x, z, 52, h.spires(), h.height() * 0.95F, 0.18F, 0.3F, false) * (0.4F + 0.6F * across);
		float plain = floor;

		// The middle: a plateau for whatever stands there
		float plateau = smooth((40 - centre) / 14);
		floor = Mth.lerp(plateau, floor, h.floor());

		// The roof: the vault, under the land above, hung with inverted peaks
		float roof = h.base() + h.height() * (0.4F + 0.6F * (1 - wallward));
		roof += (float) (7 * this.noise(23, x / 45.0, z / 45.0) + 3 * this.noise(25, x / 14.0, z / 14.0));
		roof -= this.cones(0x4841_4E47L, x, z, 46, h.hangs(), h.height() * 0.5F, 0.35F, 0.3F, true) * (0.3F + 0.7F * across);
		roof = Math.min(roof, out.cap);

		// Where the dry tunnels come in: a scree ramp down from the tunnel mouth, fanning out
		if (out.tunnelOn && edge > -6) {
			float fan = out.tunnelHalfWidth + 4 + 0.4F * inward;
			if (out.tunnelDist < fan) {
				float ramp = out.tunnelFloor - 0.62F * inward - 0.9F * Math.max(0, out.tunnelDist - out.tunnelHalfWidth * 0.7F);
				floor = Math.max(floor, ramp);
			}
		}
		// Where a river comes in: a plunge pool under the falls, scoured below the floor round it
		if (out.riverOn && edge > -6 && out.riverDist < out.riverHalfWidth + 12 && inward < 34) {
			float scour = (1 - inward / 34) * (1 - out.riverDist / (out.riverHalfWidth + 12));
			float bottom = h.base() + 0.3F * relief - 5;
			if (scour > 0.15F && bottom < floor) {
				floor = Mth.lerp(Math.min(1, scour * 2.2F), floor, bottom);
				out.pool = Math.min(plain, h.base() + 0.3F * relief) - 1;
			}
		}

		out.floor = floor;
		out.roof = roof;
		out.lake = h.lake();
		out.pillar = this.columnDistance(x, z, h.columns());
		out.nearRealm = true;
		out.hall = edge > -8 && roof - floor > 3;
	}

	/**
	 * The height added by cones on a grid of {@code cell}-block cells, one in each cell with the given
	 * chance: concave, so they stand as needles and peaks rather than mounds; or {@code rounded}, as the
	 * pendants of a cave roof hang, too thick at the end for the rock's roughness to break off.
	 */
	private float cones(long salt, int x, int z, int cell, float chance, float tallest, float narrow, float wide, boolean rounded) {
		if (chance <= 0) {
			return 0;
		}
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float top = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(salt, cx + dx, cz + dz);
				if (PlateMap.unit(h) >= chance) {
					continue;
				}
				double px = (cx + dx + 0.15 + 0.7 * PlateMap.unit(h >>> 21)) * cell;
				double pz = (cz + dz + 0.15 + 0.7 * PlateMap.unit(h >>> 42)) * cell;
				double u = PlateMap.unit(h >>> 7);
				float height = tallest * (float) (0.2 + 0.8 * u * u);
				float radius = height * (narrow + wide * (float) PlateMap.unit(h >>> 13)) + 4;
				double d = Math.hypot(x - px, z - pz);
				if (d < radius) {
					double t = d / radius;
					top = Math.max(top, height * (float) (rounded ? 1 - t * t : Math.pow(1 - t, 1.6)));
				}
			}
		}
		return top;
	}

	/** Distance (blocks) to the surface of the nearest of a realm's great columns. */
	private float columnDistance(int x, int z, float chance) {
		if (chance <= 0) {
			return 99;
		}
		int cell = 110;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float best = 99;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x434F_4C55L, cx + dx, cz + dz);
				if (PlateMap.unit(h) >= chance) {
					continue;
				}
				double px = (cx + dx + 0.2 + 0.6 * PlateMap.unit(h >>> 21)) * cell;
				double pz = (cz + dz + 0.2 + 0.6 * PlateMap.unit(h >>> 42)) * cell;
				float r = 5 + 9 * (float) PlateMap.unit(h >>> 7);
				best = Math.min(best, (float) Math.hypot(x - px, z - pz) - r);
			}
		}
		return best;
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

	/** The river tunnels' water level: steady for long stretches, stepping down six blocks at a time. */
	private float riverLevel(int x, int z) {
		float floor = (float) (28 + 16 * this.noise(10, x / 1000.0, z / 1000.0));
		return 6 * Mth.floor(floor / 6) + 2;
	}

	/** The dry tunnels' floor, before its small roughness. */
	private float dryLevel(int x, int z) {
		return (float) (28 + 16 * this.noise(16, x / 1100.0, z / 1100.0));
	}

	/** The lowest a roof may come at (x, z): the lowest ground over the 2x2 cover cells, less the rock that must stay. */
	private float capAt(Cache c, int x, int z) {
		int cx = Math.floorDiv(x - COVER_CELL / 2, COVER_CELL);
		int cz = Math.floorDiv(z - COVER_CELL / 2, COVER_CELL);
		float ground = Math.min(Math.min(this.cover(c, cx, cz), this.cover(c, cx + 1, cz)), Math.min(this.cover(c, cx, cz + 1), this.cover(c, cx + 1, cz + 1)));
		return ground < 66 ? -999 : ground - ROOF_COVER;
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

	/** The realm of realm cell (hx, hz), if it has one: about half do, where a tunnel runs near its centre. */
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
		Hall hall = this.makeHall(c, hx, hz);
		c.hallKeys[slot] = key;
		c.halls[slot] = hall;
		return hall;
	}

	private Hall makeHall(Cache c, int hx, int hz) {
		long h = PlateMap.mix(0x48414C4CL, hx, hz);
		if (PlateMap.unit(h >>> 30) >= 0.6) {
			return Hall.NONE;
		}
		int x = (int) ((hx + 0.3 + 0.4 * PlateMap.unit(h)) * HALL_CELL);
		int z = (int) ((hz + 0.3 + 0.4 * PlateMap.unit(h >>> 21)) * HALL_CELL);
		// most are a hundred to two hundred blocks across; one in five is a great realm of three or four hundred
		double u = PlateMap.unit(h >>> 42);
		float radius = (float) (55 + 165 * u * u);
		// it opens off whichever tunnel runs nearest its centre
		float river = this.tunnelDistance(8, x, z, 900.0, 6);
		float dry = this.tunnelDistance(14, x, z, 700.0, 7);
		if (Math.min(river, dry) > Math.max(40, radius * 0.5F)) {
			return Hall.NONE;
		}
		float tunnelFloor = river < dry ? this.riverLevel(x, z) + 1 : this.dryLevel(x, z);
		float cover = this.capAt(c, x, z);
		if (cover < tunnelFloor + 30) {
			return Hall.NONE;
		}

		double pick = PlateMap.unit(h >>> 11);
		int theme = pick < 0.2 ? WILDS : pick < 0.4 ? LUSH : pick < 0.6 ? DRIPSTONE : pick < 0.72 ? CRYSTAL : pick < 0.82 ? EMBER : MERE;
		double v = PlateMap.unit(h >>> 17);
		double w = PlateMap.unit(h >>> 23);
		// the bigger the realm, the further below its tunnels it lies
		float drop = Mth.clamp(10 + 0.2F * radius, 16, 50);
		float relief = switch (theme) {
			case WILDS -> 12 + 8 * (float) v;
			case LUSH -> 10 + 8 * (float) v;
			case DRIPSTONE -> 14 + 10 * (float) v;
			case CRYSTAL -> 18 + 10 * (float) v;
			case EMBER -> 10 + 8 * (float) v;
			default -> 12 + 8 * (float) v;
		};
		float base = tunnelFloor - drop;
		if (theme == EMBER) {
			base = Math.min(base, -38);
		}
		// lava below y -54 belongs to the ember realms alone
		base = Math.max(base, theme == EMBER ? DEEPEST : -53 + 1.1F * relief);
		// tall enough that the tunnels come in on its slopes or high on its walls, clear of the rock cap
		float height = Math.min(cover - base - 6, Math.max(2.4F * (drop - 4), 55 + 60 * (float) w));
		if (height < 40) {
			return Hall.NONE;
		}
		float terrace = theme == CRYSTAL ? 6 + 3 * (float) v : theme == DRIPSTONE && w < 0.5 ? 5 : 0;
		float lake = switch (theme) {
			case MERE -> base + 0.4F * relief;
			case LUSH -> base - 0.15F * relief;
			case WILDS -> base - 0.45F * relief;
			case DRIPSTONE -> base - 0.6F * relief;
			case CRYSTAL -> base - 0.35F * relief;
			default -> base - 0.1F * relief;
		};
		float spires = switch (theme) {
			case DRIPSTONE -> 0.75F;
			case MERE, EMBER -> 0.45F;
			case CRYSTAL -> 0.35F;
			case LUSH -> 0.25F;
			default -> 0.2F;
		};
		float hangs = theme == DRIPSTONE ? 0.8F : theme == CRYSTAL ? 0.5F : 0.35F;
		float columns = radius < 90 ? 0.15F : 0.3F + 0.3F * (float) v;
		// the plateau stands clear of the lake
		float plateau = Math.max(base + 0.25F * relief + 3, lake + 3);
		return new Hall(true, x, z, radius, plateau, height, base, relief, 45 + 50 * (float) w, terrace, lake, theme, spires,
			hangs, columns, tunnelFloor);
	}

	/**
	 * The realm whose centre lies within {@code reach} blocks of (x, z), with room for a structure of the
	 * given height over its plateau, or null: where underground structures go.
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

	/**
	 * How far (blocks) the point is inside open space: positive inside a tunnel or a realm, negative in the
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
			// the walls overhang and draw back with height
			float wall = c.hallEdge + (float) (5 * this.n[24].get(x / 40.0, y / 22.0, z / 40.0));
			// columns flare where they meet floor and roof
			float flare = 12 * sq(1 - Mth.clamp(vertical / 30.0F, 0, 1));
			best = Math.max(best, Math.min(Math.min(vertical, wall), c.pillar - flare));
		}
		if (best > -6) {
			best += (float) (2.4 * this.n[13].get(x / 22.0, y / 14.0, z / 22.0));
		}
		return best;
	}

	/**
	 * Whether the aquifers are kept out of (x, y, z): anywhere within {@value #DRY_MARGIN} blocks of a
	 * tunnel or a realm, so their open space holds only the water and lava poured into it.
	 */
	public boolean dry(int x, int y, int z) {
		Info c = this.info(x, z);
		int m = DRY_MARGIN;
		if (c.nearRealm && c.hallEdge > -m && y > c.floor - m && y < c.roof + m) {
			return true;
		}
		if (c.riverOn && c.riverDist < c.riverHalfWidth + 4 + m && y > c.riverWater - c.riverDepth - m && y < c.riverTop + m) {
			return true;
		}
		return c.tunnelOn && c.tunnelDist < c.tunnelHalfWidth + m && y > c.tunnelFloor - m && y < c.tunnelFloor + c.tunnelHeight + m;
	}

	private static float sq(float v) {
		return v * v;
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
