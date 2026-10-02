package dev.voidmc.expanse.world.terrain;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The world under the Earth terrain: the whole of it, since the Earth noise settings carve no caves of
 * vanilla's and run no carvers. A network you follow, from the surface down:
 * <ul>
 *   <li><b>Ways in.</b> Cave mouths at the foot of hillsides and river banks, an adit running into the hill
 *       and down; sinkholes, shafts that open over a chamber; ravines, where a fissure breaks the surface.
 *       None of them opens under the sea, in a river or in a lake.</li>
 *   <li><b>Passages.</b> Three levels of winding, branching passages, each a sparse network of links between
 *       the nodes of a jittered grid: a shallow one that climbs with the land under mountains, one at the
 *       depth of the great tunnels (bending to meet them wherever they pass), and a deep one that comes out
 *       in the walls of the realms. Some nodes open into chambers, from a dozen to fifty blocks across.</li>
 *   <li><b>Fissures.</b> Tall narrow cracks through all the levels, here and there reaching down into the
 *       lava below y {@value #LAVA_LEVEL}.</li>
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
 *       ({@link #WILDS}, {@link #LUSH}, ...), taken from the land above it (its climate, the sea, mountains
 *       and volcanoes) with some chance, that decides its lakes, its relief and, in {@link CavernLife}, what
 *       grows there.</li>
 * </ul>
 * Apart from the ways in, nothing comes nearer than {@value #ROOF_COVER} blocks to the lowest ground within a
 * few dozen blocks, so no hillside breaks open into a tunnel, and nothing lies under the sea or a lake, which
 * would flood it. Aquifers are kept out of all of it ({@link #dry}); the water and lava here are poured, by
 * {@link EarthChunkGenerator}, where the model says, except below y {@value #LAVA_LEVEL}, where every open
 * space is lava as in vanilla, so the deepest realms have lava seas.
 *
 * <p>Everything is decided per column and cached; the density functions then only measure how far a point
 * is inside that space, roughened by a little 3D noise: {@link #coarse} for the great tunnels and realms
 * (sampled on vanilla's 4 x 8 x 4 cells), {@link #fine} for the passages, chambers, fissures and ways in,
 * which are too small for that and are sampled on a finer grid.
 */
public final class CavernModel {
	private static final int ROOF_COVER = 16;
	static final int HALL_CELL = 640;
	private static final int COVER_CELL = 32;
	/** No realm floor below this: under y -54 every open space is lava. */
	private static final float DEEPEST = -50;
	/** How far round every open space the aquifers are kept out (blocks). */
	private static final int DRY_MARGIN = 24;
	/** Below this every open space is lava, as vanilla's global fluid has it. */
	public static final int LAVA_LEVEL = -54;

	/** A forest of giant glowcaps on moss: the realm lit from beneath its mushroom caps. */
	public static final int WILDS = 0;
	/** Lush caves at realm scale: moss hills, azaleas, ponds of dripleaf, vines hanging thick. */
	public static final int LUSH = 1;
	/** Stalagmite mountains and hanging spires, terraced flowstone, a few still pools. */
	public static final int DRIPSTONE = 2;
	/** Terraced mesas of calcite and tuff, crusted with amethyst and prismite. */
	public static final int CRYSTAL = 3;
	/** The deepest realms: basalt and magma round a lake of lava. */
	public static final int EMBER = 4;
	/** A lake realm: islands and stalagmite stacks standing in still water, glowing pickles on its bed. */
	public static final int MERE = 5;
	/** Under the cold lands: a frozen lake, packed and blue ice, ice-clad spires, icicles. */
	public static final int FROZEN = 6;
	/** Under the forests: giant roots hanging from the vault and arching across, rooted earth, glowing roots. */
	public static final int ROOTS = 7;
	/** Under dry lands: sand and sandstone mesas, dry as a desert, the bones of giants lying in the sand. */
	public static final int FOSSIL = 8;
	/** Near the coasts: flowstone terraces stepping down to a still salt lake, tide pools on every step. */
	public static final int TIDAL = 9;
	/** Under the conifer woods and the steppe: resin and glowing amber, insects caught in it, honey pools. */
	public static final int AMBER = 10;
	/** Under volcanoes and mountains: basalt causeways, sulfur, hot springs and geysers. */
	public static final int BRIMSTONE = 11;
	/** How many characters there are. */
	public static final int THEMES = 12;

	/** The passages' levels: their grid of nodes, how often neighbouring nodes are linked, their chambers. */
	private static final int LEVELS = 3;
	private static final double[] CELL = {104, 124, 144};
	private static final float[] LINK = {0.46F, 0.5F, 0.56F};
	private static final float[] CHAMBER = {0.16F, 0.2F, 0.26F};
	private static final float[] HALF_WIDTH = {2.2F, 2.6F, 3.0F};
	private static final float[] WIDER = {1.4F, 1.8F, 2.6F};
	private static final float[] HEIGHT = {4.5F, 5.0F, 6.5F};
	private static final float[] TALLER = {3.0F, 4.0F, 5.5F};
	/** The lowest floor each level may take. */
	private static final float[] LOWEST = {6, -30, -50};
	/** The link directions from a node: east, south, and the two diagonals. */
	private static final int[][] LINKS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
	/** The ways in from hillsides: one at most per cell of this many blocks. */
	private static final int ADIT_CELL = 256;
	/** How far from a great tunnel anything asks after it (blocks): a realm's choice of tunnel, the rest. */
	private static final float FAR = 150;
	private static final float NEAR = 64;

	private final TerrainModel terrain;
	private final SimplexNoise[] n = new SimplexNoise[48];
	private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);
	/** Mixed into the hashes that place things, so each world has its own. */
	private final long salt;

	/** One level of passages at a column. */
	public static final class Passage {
		/** A passage runs here: distance from its middle line, half its width, its floor and its height. */
		public boolean on;
		public float dist;
		public float halfWidth;
		public float floor;
		public float height;
		/** A chamber covers the column: how far inside its outline (blocks), its floor and roof here. */
		public boolean chamber;
		public float chamberIn;
		public float chamberFloor;
		public float chamberRoof;
	}

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
		/** The realm's character, one of {@link #WILDS}... {@link #BRIMSTONE}. */
		public int theme;
		/** The surface of the realm's lake: open space below it is filled (very low if the realm has none). */
		public float lake;
		/** Whether that lake is lava. */
		public boolean lava;
		/** The surface of a plunge pool under a waterfall, filled with water (very low if none). */
		public float pool;
		/** Near a realm. */
		boolean nearRealm;
		/** A cenote: a shaft from the realm's vault up to the open sky, its distance from the axis, radius at the lip, top and bottom. */
		public boolean cenote;
		public float cenoteDist;
		public float cenoteRadius;
		public float cenoteTop;
		public float cenoteBottom;

		// ---- the small spaces (see fine())
		/** The passages of each level. */
		public final Passage[] passages = {new Passage(), new Passage(), new Passage()};
		/** A fissure: distance from its line, its widest half width, its bottom and top; open to the sky if a ravine. */
		public boolean fissure;
		public float fissureDist;
		public float fissureHalfWidth;
		public float fissureBottom;
		public float fissureTop;
		public boolean ravine;
		/** A way in from a hillside: distance from its line, half its width, its floor and height here. */
		public boolean adit;
		public float aditDist;
		public float aditHalfWidth;
		public float aditFloor;
		public float aditHeight;
		/** A sinkhole's shaft: distance from its axis, its radius, the chamber roof it opens into, the ground. */
		public boolean shaft;
		public float shaftDist;
		public float shaftRadius;
		public float shaftBottom;
		public float shaftTop;
		/** Whether any of the small spaces is here, and the heights between which they lie. */
		public boolean fine;
		public float fineLow;
		public float fineHigh;

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
		float scale, float terrace, float lake, int theme, float spires, float hangs, float columns, float tunnelFloor, int cenotes) {
		static final Hall NONE = new Hall(false, 0, 0, 0, 0, 0, 0, 0, 1, 0, -999, 0, 0, 0, 0, 0, 0);
	}

	/** A way in from a hillside: from its mouth at (x, z) on the ground, along (dx, dz) into the hill and down. */
	record Adit(boolean exists, float x, float z, float dx, float dz, float bend, float length, float mouth, float target, float halfWidth,
		float height) {
		static final Adit NONE = new Adit(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
		final long[] aditKeys = new long[64];
		final Adit[] adits = new Adit[64];
		// scratch for the passage networks
		final double[] nodeX = new double[9];
		final double[] nodeZ = new double[9];
		final long[] nodeHash = new long[9];
		double warpX;
		double warpZ;
	}

	CavernModel(TerrainModel terrain, long seed) {
		this.terrain = terrain;
		RandomSource random = RandomSource.create(seed ^ 0x4341_5645_524E_53L);
		for (int i = 0; i < this.n.length; i++) {
			this.n[i] = new SimplexNoise(random);
		}
		this.salt = PlateMap.mix(seed, 0x5341_4C54L, 0x4445_4550L);
	}

	/** The land above, whose climate the caves follow. */
	public TerrainModel terrain() {
		return this.terrain;
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

		// The river tunnels (nothing reads them further off than this, so far from one nothing more is worked out)
		out.riverDist = this.tunnelDistance(8, x, z, 900.0, 6, NEAR);
		if (out.riverDist < NEAR) {
			out.riverHalfWidth = (float) (7 + 5 * (0.5 + 0.5 * this.noise(9, x / 600.0, z / 600.0)));
			out.riverWater = this.riverLevel(x, z);
			out.riverDepth = 1.5F + out.riverHalfWidth * 0.3F;
			float span = out.riverHalfWidth + 4;
			float riverHeight = (float) (18 + 14 * (0.5 + 0.5 * this.noise(11, x / 700.0, z / 700.0)));
			out.riverTop = out.riverWater + 1 + riverHeight;
			out.tunnelRoof = out.riverWater + 1 + riverHeight * (float) Math.sqrt(Math.max(0, 1 - sq(out.riverDist / span)));
			out.riverOn = this.noise(12, x / 2000.0, z / 2000.0) > -0.2 && out.riverTop < out.cap;
			out.river = out.riverOn && out.riverDist < span;
		} else {
			out.riverHalfWidth = 7;
			out.riverWater = -999;
			out.riverDepth = 1;
			out.riverTop = -999;
			out.tunnelRoof = -999;
			out.riverOn = false;
			out.river = false;
		}

		// The dry tunnels
		out.tunnelDist = this.tunnelDistance(14, x, z, 700.0, 7, NEAR);
		if (out.tunnelDist < NEAR) {
			out.tunnelHalfWidth = (float) (6 + 6 * (0.5 + 0.5 * this.noise(15, x / 500.0, z / 500.0)));
			out.tunnelFloor = (float) (this.dryLevel(x, z) + 1.5 * this.noise(17, x / 40.0, z / 40.0));
			out.tunnelHeight = (float) (16 + 18 * (0.5 + 0.5 * this.noise(18, x / 650.0, z / 650.0)));
			out.tunnelOn = out.tunnelFloor + out.tunnelHeight < out.cap;
			out.tunnel = out.tunnelOn && out.tunnelDist < out.tunnelHalfWidth;
		} else {
			out.tunnelHalfWidth = 6;
			out.tunnelFloor = -999;
			out.tunnelHeight = 0;
			out.tunnelOn = false;
			out.tunnel = false;
		}

		// The nearest realm: the one whose (ragged) outline the column is deepest inside
		Hall best = null;
		float bestEdge = -Float.MAX_VALUE;
		int hx = Math.floorDiv(x, HALL_CELL);
		int hz = Math.floorDiv(z, HALL_CELL);
		double raggedA = Double.NaN;
		double raggedB = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Hall h = this.hall(c, hx + dx, hz + dz);
				if (!h.exists()) {
					continue;
				}
				if (Double.isNaN(raggedA)) {
					raggedA = this.noise(19, x / 110.0, z / 110.0);
					raggedB = this.noise(20, x / 38.0, z / 38.0);
				}
				float rough = 10 + 22 * Math.min(1, h.radius() / 160);
				double ragged = Math.sqrt(sq(x - h.x()) + sq(z - h.z())) + rough * raggedA + 0.45 * rough * raggedB;
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
		out.cenote = false;
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

		// The small spaces between: passages, chambers, fissures and the ways in
		out.fine = false;
		out.fineLow = Float.MAX_VALUE;
		out.fineHigh = -Float.MAX_VALUE;
		out.shaft = false;
		float cover = this.smoothCover(c, x, z);
		// the passages wind: one warp of the ground for every level
		c.warpX = 13 * this.noise(32, x / 90.0, z / 90.0);
		c.warpZ = 13 * this.noise(33, x / 90.0, z / 90.0);
		for (int k = 0; k < LEVELS; k++) {
			this.passages(k, x, z, out, cover, c);
		}
		this.fissure(x, z, out);
		this.adits(c, x, z, out);
	}

	/** The land of a realm at one column: its floor, its roof, its columns and what fills its hollows. */
	private void realm(Hall h, int x, int z, float edge, Info out) {
		out.hallX = h.x();
		out.hallZ = h.z();
		out.hallRadius = h.radius();
		out.theme = h.theme();
		out.lava = h.theme() == EMBER;
		float inward = Math.max(0, edge);
		float centre = (float) Math.sqrt(sq(x - h.x()) + sq(z - h.z()));

		// A lens, as great caverns are: the floor a bowl of hills (or terraced mesas) whose rubble slopes rise
		// to the walls, the vault curving down to meet them
		float across = Mth.clamp(inward / (h.radius() * 0.55F), 0, 1);
		float wallward = sq(1 - across);
		double s = h.scale();
		// broad swells, ridgelines with valleys between, and a little roughness
		float ridged = (float) (1 - 2 * Math.abs(this.noise(26, x / (s * 1.3), z / (s * 1.3))));
		float hills = (float) (0.55 * this.noise(21, x / s, z / s) + 0.15 * this.noise(22, x / (s / 3.0), z / (s / 3.0)))
			+ 0.45F * ridged * smooth((float) (0.5 + this.noise(27, x / 300.0, z / 300.0)));
		if (h.theme() == FOSSIL) {
			// dunes: long ridges, steep on one side
			hills += 0.25F * (float) Math.abs(this.noise(22, x / 70.0, z / 140.0));
		}
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
			float fan = Math.min(NEAR - 4, out.tunnelHalfWidth + 4 + 0.4F * inward);
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

		// Cenotes: under hot, wet country the rock over a realm has fallen in here and there, open to the sky
		for (int i = 0; i < h.cenotes(); i++) {
			long ch = PlateMap.mix(this.salt ^ 0x4345_4E4FL, h.x() * 31L + i, h.z());
			double angle = Mth.TWO_PI * PlateMap.unit(ch);
			double dist = Math.max(60, h.radius() * (0.3 + 0.3 * PlateMap.unit(ch >>> 21)));
			float px = (float) (h.x() + Math.cos(angle) * dist);
			float pz = (float) (h.z() + Math.sin(angle) * dist);
			float lip = 5 + 6 * (float) PlateMap.unit(ch >>> 40);
			float d = (float) Math.sqrt(sq(x - px) + sq(z - pz));
			if (d < lip * 1.6F + 3 && inward > lip * 1.6F + 12) {
				Column middle = this.terrain.sample(Mth.floor(px), Mth.floor(pz));
				if (!this.openable(middle, 16) || this.nearWater(Mth.floor(px), Mth.floor(pz), 20)) {
					continue;
				}
				Column land = this.terrain.sample(x, z);
				float ground = land.height;
				if (this.openable(land, 12)) {
					out.cenote = true;
					out.cenoteDist = d;
					out.cenoteRadius = lip;
					out.cenoteTop = ground + 4;
					out.cenoteBottom = roof - 8;
				}
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
				double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
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
				best = Math.min(best, (float) Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) - r);
			}
		}
		return best;
	}

	/**
	 * Distance (blocks) to the zero line of warped noise {@code k} at the given scale: the course of a tunnel.
	 * Beyond {@code far} it is only a lower bound, found without measuring the noise's slope (it never climbs
	 * faster than about 7.4 per unit), which saves the work well away from the tunnels.
	 */
	private float tunnelDistance(int k, int x, int z, double scale, int warpNoise, float far) {
		double wx = x + 110 * this.noise(warpNoise, x / 380.0, z / 380.0);
		double wz = z + 110 * this.noise(warpNoise, x / 380.0 + 71, z / 380.0);
		double p = this.noise(k, wx / scale, wz / scale);
		if (Math.abs(p) * scale / 7.4 > far) {
			return (float) (Math.abs(p) * scale / 7.4);
		}
		double gx = (this.noise(k, (wx + 2) / scale, wz / scale) - p) / 2;
		double gz = (this.noise(k, wx / scale, (wz + 2) / scale) - p) / 2;
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

	/** The lowest ground round about, smoothly: the cover cells' lows, blended between their centres. */
	private float smoothCover(Cache c, int x, int z) {
		float gx = (x - COVER_CELL / 2) / (float) COVER_CELL;
		float gz = (z - COVER_CELL / 2) / (float) COVER_CELL;
		int cx = Mth.floor(gx);
		int cz = Mth.floor(gz);
		float fx = gx - cx;
		float fz = gz - cz;
		float a = Mth.lerp(fx, this.cover(c, cx, cz), this.cover(c, cx + 1, cz));
		float b = Mth.lerp(fx, this.cover(c, cx, cz + 1), this.cover(c, cx + 1, cz + 1));
		return Mth.lerp(fz, a, b);
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

	// ------------------------------------------------------------------ the realms

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
		long h = PlateMap.mix(0x48414C4CL ^ this.salt, hx, hz);
		if (PlateMap.unit(h >>> 30) >= 0.6) {
			return Hall.NONE;
		}
		int x = (int) ((hx + 0.3 + 0.4 * PlateMap.unit(h)) * HALL_CELL);
		int z = (int) ((hz + 0.3 + 0.4 * PlateMap.unit(h >>> 21)) * HALL_CELL);
		// most are a hundred to two hundred blocks across; one in five is a great realm of three or four hundred
		double u = PlateMap.unit(h >>> 42);
		float radius = (float) (55 + 165 * u * u);
		// it opens off whichever tunnel runs nearest its centre
		float river = this.tunnelDistance(8, x, z, 900.0, 6, FAR);
		float dry = this.tunnelDistance(14, x, z, 700.0, 7, FAR);
		if (Math.min(river, dry) > Math.max(40, radius * 0.5F)) {
			return Hall.NONE;
		}
		float tunnelFloor = river < dry ? this.riverLevel(x, z) + 1 : this.dryLevel(x, z);
		float cover = this.capAt(c, x, z);
		if (cover < tunnelFloor + 30) {
			return Hall.NONE;
		}

		int theme = this.character(x, z, PlateMap.unit(h >>> 11));
		Column land = this.terrain.sample(x, z);
		boolean karst = land.temperature > 0.2F && land.humidity > 0.1F;
		double v = PlateMap.unit(h >>> 17);
		double w = PlateMap.unit(h >>> 23);
		// the bigger the realm, the further below its tunnels it lies
		float drop = Mth.clamp(10 + 0.2F * radius, 16, 50);
		float relief = switch (theme) {
			case WILDS, MERE, AMBER -> 12 + 8 * (float) v;
			case LUSH, EMBER, ROOTS, TIDAL -> 10 + 8 * (float) v;
			case DRIPSTONE -> 14 + 10 * (float) v;
			case CRYSTAL -> 18 + 10 * (float) v;
			case FROZEN -> 12 + 10 * (float) v;
			case FOSSIL -> 16 + 12 * (float) v;
			default -> 9 + 7 * (float) v;
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
		float terrace = switch (theme) {
			case CRYSTAL -> 6 + 3 * (float) v;
			case DRIPSTONE -> w < 0.5 ? 5 : 0;
			case FOSSIL -> w < 0.6 ? 5 + 3 * (float) v : 0;
			case TIDAL -> 4;
			default -> 0;
		};
		float lake = switch (theme) {
			case MERE -> base + 0.4F * relief;
			case LUSH -> base - 0.15F * relief;
			case WILDS -> base - 0.45F * relief;
			case DRIPSTONE -> base - 0.6F * relief;
			case CRYSTAL, AMBER -> base - 0.35F * relief;
			case FROZEN -> base + 0.05F * relief;
			case ROOTS -> base - 0.5F * relief;
			case FOSSIL -> -999;
			case TIDAL -> base - 0.1F * relief;
			case BRIMSTONE -> base - 0.3F * relief;
			default -> base - 0.1F * relief;
		};
		float spires = switch (theme) {
			case DRIPSTONE -> 0.75F;
			case MERE, EMBER, TIDAL, FROZEN -> 0.45F;
			case CRYSTAL, FOSSIL -> 0.35F;
			case LUSH, AMBER, BRIMSTONE -> 0.25F;
			case ROOTS -> 0.1F;
			default -> 0.2F;
		};
		float hangs = switch (theme) {
			case DRIPSTONE -> 0.8F;
			case FROZEN -> 0.7F;
			case ROOTS -> 0.55F;
			case CRYSTAL, TIDAL, AMBER -> 0.5F;
			default -> 0.35F;
		};
		float columns = radius < 90 ? 0.15F : 0.3F + 0.3F * (float) v;
		// the plateau stands clear of the lake
		float plateau = Math.max(base + 0.25F * relief + 3, lake + 3);
		// under hot, wet country (jungle, karst) a big realm's vault has fallen in, open to the sky, in a place or two
		int cenotes = karst && radius >= 90 && PlateMap.unit(h >>> 27) < 0.6 ? 1 + (int) (PlateMap.unit(PlateMap.mix(h, 2, 2)) * 2.99) : 0;
		return new Hall(true, x, z, radius, plateau, height, base, relief, 45 + 50 * (float) w, terrace, lake, theme, spires,
			hangs, columns, tunnelFloor, cenotes);
	}

	/**
	 * A realm's character, after the land above its centre, with chance enough that any can turn up anywhere
	 * now and then: frozen under the cold lands, fossil deeps under the deserts, roots under the woods, amber
	 * under the conifers and the steppe, tide grottos near the sea, brimstone under volcanoes and mountains.
	 */
	private int character(int x, int z, double pick) {
		Column land = this.terrain.sample(x, z);
		float t = land.temperature;
		float wet = land.humidity;
		float coast = land.coast;
		float uplift = land.uplift;
		boolean volcanic = land.landform == Landform.VOLCANIC;
		float[] weight = new float[THEMES];
		java.util.Arrays.fill(weight, 0.15F);
		weight[EMBER] = 0.5F;
		if (t < -0.45F) {
			// snow country
			weight[FROZEN] += 7;
			weight[CRYSTAL] += 1;
			weight[MERE] += 0.6F;
		} else if (t > 0.55F) {
			if (wet < 0.1F) {
				// desert and badlands
				weight[FOSSIL] += 6;
				weight[BRIMSTONE] += 1.2F;
				weight[EMBER] += 1.2F;
				weight[CRYSTAL] += 1;
			} else {
				// hot and wet: karst and jungle
				weight[LUSH] += 2.5F;
				weight[FOSSIL] += 2;
				weight[MERE] += 1.5F;
				weight[WILDS] += 1;
			}
		} else if (t > 0.2F) {
			if (wet < -0.1F) {
				// savanna and steppe
				weight[FOSSIL] += 3;
				weight[AMBER] += 3;
				weight[BRIMSTONE] += 0.8F;
				weight[DRIPSTONE] += 1;
			} else if (wet > 0.1F) {
				// jungle
				weight[LUSH] += 4;
				weight[ROOTS] += 2.5F;
				weight[WILDS] += 1.5F;
				weight[MERE] += 1;
			} else {
				weight[ROOTS] += 3;
				weight[LUSH] += 2;
				weight[WILDS] += 1;
				weight[DRIPSTONE] += 1;
			}
		} else if (wet > 0.1F) {
			// the forests and the taiga: conifers give amber
			if (t < -0.15F) {
				weight[AMBER] += 3.5F;
				weight[ROOTS] += 2.5F;
			} else {
				weight[ROOTS] += 4;
				weight[AMBER] += 1;
			}
			weight[WILDS] += wet > 0.3F ? 3 : 1;
			weight[MERE] += 1;
		} else {
			// plains and meadows
			weight[DRIPSTONE] += 3;
			weight[MERE] += 2;
			weight[CRYSTAL] += 1.5F;
			weight[WILDS] += 1;
			weight[LUSH] += 1;
		}
		if (coast < 300) {
			weight[TIDAL] += 9;
			weight[MERE] += 1;
		} else if (coast < 600) {
			weight[TIDAL] += 2;
		}
		if (volcanic) {
			weight[BRIMSTONE] += 12;
			weight[EMBER] += 4;
		} else if (uplift > 0.9F && t >= -0.45F) {
			weight[CRYSTAL] += 2.5F;
			weight[BRIMSTONE] += 1.5F;
			weight[DRIPSTONE] += 1.5F;
		}
		float total = 0;
		for (float f : weight) {
			total += f;
		}
		float at = (float) pick * total;
		for (int i = 0; i < THEMES; i++) {
			at -= weight[i];
			if (at < 0) {
				return i;
			}
		}
		return DRIPSTONE;
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

	// ------------------------------------------------------------------ the passages

	/**
	 * Level {@code k} of passages at a column: the links of a sparse network over a jittered grid of nodes
	 * (each link there or not by chance, so the network branches, loops and ends), winding where the grid is
	 * warped; and the chambers some nodes open into.
	 */
	private void passages(int k, int x, int z, Info out, float cover, Cache c) {
		Passage p = out.passages[k];
		p.on = false;
		p.chamber = false;
		double s = CELL[k];
		double wx = x + c.warpX;
		double wz = z + c.warpZ;
		int cx = Mth.floor(wx / s);
		int cz = Mth.floor(wz / s);
		long levelSalt = this.salt ^ 0x5041_5353L * (k + 1);
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				long h = PlateMap.mix(levelSalt, cx + i - 1, cz + j - 1);
				c.nodeX[i * 3 + j] = (cx + i - 1 + 0.2 + 0.6 * PlateMap.unit(h)) * s;
				c.nodeZ[i * 3 + j] = (cz + j - 1 + 0.2 + 0.6 * PlateMap.unit(h >>> 21)) * s;
				c.nodeHash[i * 3 + j] = h;
			}
		}
		// the nearest link
		double best = Double.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				for (int d = 0; d < 4; d++) {
					int i2 = i + LINKS[d][0];
					int j2 = j + LINKS[d][1];
					if (i2 > 2 || j2 < 0 || j2 > 2 || !this.linked(levelSalt, k, cx + i - 1, cz + j - 1, d)) {
						continue;
					}
					double ax = c.nodeX[i * 3 + j];
					double az = c.nodeZ[i * 3 + j];
					double vx = c.nodeX[i2 * 3 + j2] - ax;
					double vz = c.nodeZ[i2 * 3 + j2] - az;
					double t = Mth.clamp(((wx - ax) * vx + (wz - az) * vz) / (vx * vx + vz * vz), 0, 1);
					double ex = wx - ax - t * vx;
					double ez = wz - az - t * vz;
					best = Math.min(best, ex * ex + ez * ez);
				}
			}
		}
		best = Math.sqrt(best);
		// the nearest chamber: a node with one, and a link to it
		float chamberIn = -Float.MAX_VALUE;
		long chamberHash = 0;
		float chamberR = 0;
		for (int i = 0; i < 9; i++) {
			long h = c.nodeHash[i];
			if (PlateMap.unit(h >>> 7) >= CHAMBER[k]) {
				continue;
			}
			float radius = (float) (s * (0.09 + (k == 2 ? 0.24 : 0.17) * PlateMap.unit(h >>> 13)));
			double ex = wx - c.nodeX[i];
			double ez = wz - c.nodeZ[i];
			if (ex * ex + ez * ez > (radius + 2) * (radius + 2)) {
				continue;
			}
			float r = (float) Math.sqrt(ex * ex + ez * ez);
			if (radius - r > chamberIn && r < radius + 2 && this.hasLink(levelSalt, k, cx + i / 3 - 1, cz + i % 3 - 1)) {
				chamberIn = radius - r;
				chamberHash = h;
				chamberR = radius;
			}
		}
		if (best > HALF_WIDTH[k] + WIDER[k] + 2 && chamberIn < -1) {
			return;
		}

		float level = this.levelFloor(k, x, z, out, cover);
		float floor = level;
		float vary = (float) (0.5 + 0.5 * this.noise(29, x / 60.0, z / 60.0 + 10 * k));
		float halfWidth = HALF_WIDTH[k] + WIDER[k] * vary;
		float height = HEIGHT[k] + TALLER[k] * (float) (0.5 + 0.5 * this.noise(30, x / 47.0, z / 47.0 + 10 * k));
		// under the rock cap, or not at all
		floor = Math.min(floor, out.cap - 1 - height);
		if (best <= halfWidth + 2 && floor >= LOWEST[k] && this.clear(out, floor, floor + height)) {
			p.on = true;
			p.dist = (float) best;
			p.halfWidth = halfWidth;
			p.floor = floor;
			p.height = height;
			this.extent(out, floor, floor + height);
		}

		if (chamberIn > -2) {
			float tall = (k == 2 ? 10 : 8) + (k == 2 ? 14 : 10) * (float) PlateMap.unit(chamberHash >>> 33);
			float q = Mth.clamp(1 - chamberIn / chamberR, 0, 1);
			q *= q;
			float base = level - 2;
			base = Math.min(base, out.cap - 2 - tall);
			// some deep chambers sink into a bowl, its bottom under the lava line
			float bowl = k == 2 && PlateMap.unit(chamberHash >>> 40) < 0.3 ? 12 : 2.5F;
			float chamberFloor = base - bowl * (1 - q);
			float chamberRoof = base + tall * (float) Math.sqrt(1 - q);
			if (base >= LOWEST[k] - 4 && this.clear(out, chamberFloor, chamberRoof)) {
				p.chamber = true;
				p.chamberIn = chamberIn;
				p.chamberFloor = chamberFloor;
				p.chamberRoof = chamberRoof;
				this.extent(out, chamberFloor, chamberRoof);
				// a sinkhole: a shaft from a shallow chamber up to the ground
				long more = PlateMap.mix(chamberHash, 5, 0);
				if (k == 0 && PlateMap.unit(more) < 0.2) {
					float radius = 2.5F + 3 * (float) PlateMap.unit(more >>> 21);
					float r = chamberR - chamberIn;
					if (r < radius + 7) {
						this.shaft(x, z, out, r, radius, chamberRoof - 1);
					}
				}
			}
		}
	}

	/** Whether the link from node (i, j) in direction {@code d} is there. Diagonals are rarer. */
	private boolean linked(long levelSalt, int k, int i, int j, int d) {
		return PlateMap.unit(PlateMap.mix(levelSalt + 1 + d, i, j)) < LINK[k] * (d >= 2 ? 0.55F : 1);
	}

	/** Whether node (i, j) has any link. */
	private boolean hasLink(long levelSalt, int k, int i, int j) {
		for (int d = 0; d < 4; d++) {
			if (this.linked(levelSalt, k, i, j, d) || this.linked(levelSalt, k, i - LINKS[d][0], j - LINKS[d][1], d)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The floor of passage level {@code k}: a slow field at the level's depth. The shallow level climbs with
	 * the land under mountains; the shallow and middle levels bend to meet the great tunnels where they pass.
	 */
	private float levelFloor(int k, int x, int z, Info out, float cover) {
		double a = this.noise(38 + k, x / 680.0, z / 680.0);
		double b = this.noise(38 + k, x / 160.0 + 40, z / 160.0);
		float floor = switch (k) {
			case 0 -> (float) (34 + 24 * a + 7 * b);
			case 1 -> (float) (16 + 16 * a + 6 * b);
			default -> (float) (-28 + 13 * a + 5 * b);
		};
		if (k == 0) {
			floor = Math.max(floor, cover - 64 + 12 * (float) b);
		}
		if (k < 2) {
			if (out.tunnelOn && out.tunnelDist < out.tunnelHalfWidth + 36) {
				float w = smooth(1 - (out.tunnelDist - out.tunnelHalfWidth) / 36);
				floor = Mth.lerp(w, floor, out.tunnelFloor);
			}
			if (out.riverOn && out.riverDist < out.riverHalfWidth + 40) {
				float w = smooth(1 - (out.riverDist - out.riverHalfWidth - 4) / 36);
				floor = Mth.lerp(w, floor, out.riverWater + 1);
			}
		}
		return floor;
	}

	/**
	 * Whether a small space between heights {@code low} and {@code high} may be cut here: not into a realm but
	 * through its walls, never below its lake, and never through an underground river's bed.
	 */
	private boolean clear(Info out, float low, float high) {
		if (out.nearRealm && out.hallEdge > -10) {
			if (low < out.liquid() + 2) {
				return false;
			}
			if (out.hallEdge > 2 && high > out.floor - 8 && low < out.roof + 8) {
				return false;
			}
		}
		if (out.riverOn && out.riverDist < out.riverHalfWidth + 2 && high > out.riverWater - out.riverDepth - 8 && low < out.riverWater + 1) {
			return false;
		}
		return true;
	}

	private void extent(Info out, float low, float high) {
		out.fine = true;
		out.fineLow = Math.min(out.fineLow, low);
		out.fineHigh = Math.max(out.fineHigh, high);
	}

	/**
	 * A sinkhole's shaft at a column {@code r} from its axis, from the chamber roof below to the ground, if
	 * the ground here may be opened: dry land above the sea, no river near, no crater lake, no volcano.
	 */
	private void shaft(int x, int z, Info out, float r, float radius, float bottom) {
		if (out.cap < -998) {
			return;
		}
		Column land = this.terrain.sample(x, z);
		float ground = land.height;
		if (!this.openable(land, 10)) {
			return;
		}
		out.shaft = true;
		out.shaftDist = r;
		out.shaftRadius = radius;
		out.shaftBottom = bottom;
		out.shaftTop = ground + 4;
		this.extent(out, bottom, ground + 4);
	}

	/**
	 * Whether the ground of a column may be opened into the caves below: dry land above the sea, no crater
	 * lake, no volcano, no river within {@code margin} blocks of its channel. Read straight from a fresh sample.
	 */
	private boolean openable(Column land, float margin) {
		if (land.height < 68 || land.landform == Landform.VOLCANIC || land.crater > land.height - 3) {
			return false;
		}
		return !land.nearRiver() || land.riverDist > land.riverHalfWidth + margin;
	}

	// ------------------------------------------------------------------ fissures

	/**
	 * Fissures: tall narrow cracks along the zero lines of a warped noise, in stretches, from deep down (now
	 * and then into the lava) to just under the rock cap; where the land allows, a stretch breaks the surface
	 * as a ravine.
	 */
	private void fissure(int x, int z, Info out) {
		out.fissure = false;
		out.ravine = false;
		double gate = this.noise(2, x / 300.0, z / 300.0);
		if (gate < 0.25) {
			return;
		}
		float dist = this.tunnelDistance(0, x, z, 460.0, 1, 12);
		float widest = (float) (1.6 + 2.6 * (0.5 + 0.5 * this.noise(3, x / 150.0, z / 150.0))) * smooth((float) (gate - 0.25) / 0.15F);
		if (dist > widest + 3) {
			return;
		}
		float bottom = (float) (-34 + 24 * this.noise(4, x / 500.0, z / 500.0));
		float top = out.cap - 2;
		boolean ravine = false;
		if (this.noise(5, x / 700.0, z / 700.0) > 0.45 && out.cap > -998) {
			Column land = this.terrain.sample(x, z);
			float ground = land.height;
			if (this.openable(land, 12) && !this.nearWater(x, z, 12)) {
				top = ground + 5;
				ravine = true;
				// a ravine open to the sky stops short of the lava
				bottom = Math.max(bottom, -30);
			}
		}
		if (top - bottom < 20 || !this.clear(out, bottom, top) || out.nearRealm && out.hallEdge > -4) {
			return;
		}
		out.fissure = true;
		out.ravine = ravine;
		out.fissureDist = dist;
		out.fissureHalfWidth = widest;
		out.fissureBottom = bottom;
		out.fissureTop = top;
		this.extent(out, bottom, top);
	}

	/** Whether a river channel or crater lake lies within {@code reach} blocks along the axes. */
	private boolean nearWater(int x, int z, int reach) {
		int[][] around = {{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
		for (int[] o : around) {
			Column land = this.terrain.sample(x + o[0], z + o[1]);
			if (land.height < 66 || land.crater > land.height - 3 || land.nearRiver() && land.riverDist < land.riverHalfWidth + 4) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ the ways in

	/** The adits whose cells lie round the column: the nearest one that reaches it. */
	private void adits(Cache c, int x, int z, Info out) {
		out.adit = false;
		int ax = Math.floorDiv(x, ADIT_CELL);
		int az = Math.floorDiv(z, ADIT_CELL);
		float bestDist = Float.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Adit a = this.adit(c, ax + dx, az + dz);
				if (!a.exists()) {
					continue;
				}
				// a quick look first: is the column anywhere near it?
				float mx = a.x() + a.dx() * a.length() * 0.5F;
				float mz = a.z() + a.dz() * a.length() * 0.5F;
				if (Math.abs(x - mx) > a.length() * 0.5F + Math.abs(a.bend()) + 8 || Math.abs(z - mz) > a.length() * 0.5F + Math.abs(a.bend()) + 8) {
					continue;
				}
				// along its curve, in twelve straight pieces
				float px = a.x();
				float pz = a.z();
				float along = 0;
				float near = Float.MAX_VALUE;
				for (int i = 1; i <= 12; i++) {
					float t = i / 12.0F;
					float bend = a.bend() * Mth.sin(t * Mth.PI);
					float qx = a.x() + a.dx() * a.length() * t - a.dz() * bend;
					float qz = a.z() + a.dz() * a.length() * t + a.dx() * bend;
					float vx = qx - px;
					float vz = qz - pz;
					float f = Mth.clamp(((x - px) * vx + (z - pz) * vz) / Math.max(1.0E-3F, vx * vx + vz * vz), 0, 1);
					float ex = x - px - f * vx;
					float ez = z - pz - f * vz;
					float d = (float) Math.sqrt(ex * ex + ez * ez);
					if (d < near) {
						near = d;
						along = (i - 1 + f) / 12.0F * a.length();
					}
					px = qx;
					pz = qz;
				}
				if (near > a.halfWidth() + 2 || near >= bestDist) {
					continue;
				}
				// level from the mouth for a little, then down into the hill until it reaches its depth
				float floor = Math.max(a.target(), a.mouth() - 0.55F * Math.max(0, along - 8));
				if (!this.clear(out, floor, floor + a.height())) {
					continue;
				}
				// where it runs near the surface, only under dry land, and never beside a river's water
				Column land = this.terrain.sample(x, z);
				if (floor + a.height() > land.height - 5 && (land.height < 66 || land.crater > land.height - 3)
					|| land.nearRiver() && land.riverDist < land.riverHalfWidth + 3) {
					continue;
				}
				bestDist = near;
				out.adit = true;
				out.aditDist = near;
				out.aditHalfWidth = a.halfWidth();
				out.aditFloor = floor;
				out.aditHeight = a.height();
			}
		}
		if (out.adit) {
			this.extent(out, out.aditFloor, out.aditFloor + out.aditHeight);
		}
	}

	private Adit adit(Cache c, int ax, int az) {
		long key = (long) ax << 32 ^ az & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(4, ax, az) & 63);
		Adit cached = c.adits[slot];
		if (cached != null && c.aditKeys[slot] == key) {
			return cached;
		}
		Adit adit = this.makeAdit(c, ax, az);
		c.aditKeys[slot] = key;
		c.adits[slot] = adit;
		return adit;
	}

	/**
	 * The way in of adit cell (ax, az), if it has one: the best of a few places at the foot of a hillside
	 * (better still by a river), where the ground rises steeply ahead; from there a passage runs level into
	 * the hill, then down, a hundred blocks or so.
	 */
	private Adit makeAdit(Cache c, int ax, int az) {
		long h = PlateMap.mix(0x4144_4954L ^ this.salt, ax, az);
		if (PlateMap.unit(h) >= 0.85) {
			return Adit.NONE;
		}
		float bestScore = 0.75F;
		Adit best = Adit.NONE;
		for (int i = 0; i < 10; i++) {
			long hi = PlateMap.mix(h, i, 7);
			int px = (int) ((ax + 0.1 + 0.8 * PlateMap.unit(hi)) * ADIT_CELL);
			int pz = (int) ((az + 0.1 + 0.8 * PlateMap.unit(hi >>> 21)) * ADIT_CELL);
			Column land = this.terrain.sample(px, pz);
			float ground = land.height;
			if (!this.openable(land, 4)) {
				continue;
			}
			boolean byRiver = land.nearRiver() && land.riverDist < land.riverHalfWidth + 30;
			if (this.capAt(c, px, pz) < -998) {
				continue;
			}
			float east = this.terrain.sample(px + 6, pz).height;
			float west = this.terrain.sample(px - 6, pz).height;
			float south = this.terrain.sample(px, pz + 6).height;
			float north = this.terrain.sample(px, pz - 6).height;
			float gx = (east - west) / 12;
			float gz = (south - north) / 12;
			float slope = (float) Math.hypot(gx, gz);
			if (slope < 0.3F) {
				continue;
			}
			float ux = gx / slope;
			float uz = gz / slope;
			float rise = this.terrain.sample(Math.round(px + 14 * ux), Math.round(pz + 14 * uz)).height - ground;
			float far = this.terrain.sample(Math.round(px + 32 * ux), Math.round(pz + 32 * uz)).height - ground;
			float front = this.terrain.sample(Math.round(px - 10 * ux), Math.round(pz - 10 * uz)).height - ground;
			if (rise < 6 || far < 14 || front > 2) {
				continue;
			}
			float score = slope + 0.02F * Math.min(far, 50) + (byRiver ? 0.8F : 0);
			if (score <= bestScore) {
				continue;
			}
			bestScore = score;
			float length = 80 + 70 * (float) PlateMap.unit(h >>> 17);
			float mouth = ground - 1;
			float target = Math.max(8, mouth - 16 - 22 * (float) PlateMap.unit(h >>> 23));
			best = new Adit(true, px, pz, ux, uz, (float) (PlateMap.unit(h >>> 29) - 0.5) * 36, length, mouth, target,
				2.4F + 1.4F * (float) PlateMap.unit(h >>> 35), 4.5F + 2.5F * (float) PlateMap.unit(h >>> 41));
		}
		return best;
	}

	// ------------------------------------------------------------------ density

	/**
	 * How far (blocks) the point is inside open space: positive inside a tunnel, a realm or any of the small
	 * spaces, negative in the rock, by roughly the distance to the nearest wall, floor or roof.
	 */
	public float inside(int x, int y, int z) {
		return Math.max(this.coarse(x, y, z), this.fine(x, y, z));
	}

	/** {@link #inside}, for the great tunnels and the realms only: big enough for vanilla's coarse cells. */
	public float coarse(int x, int y, int z) {
		return this.coarse(this.info(x, z), x, y, z);
	}

	/** {@link #coarse(int, int, int)} with the column's {@link Info} in hand. */
	public float coarse(Info c, int x, int y, int z) {
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
		if (c.cenote) {
			// a bell: narrowest at the lip, opening out below
			float down = Mth.clamp((c.cenoteTop - y) / Math.max(1, c.cenoteTop - c.cenoteBottom), 0, 1);
			float radius = c.cenoteRadius * (1 + 0.6F * down * down);
			best = Math.max(best, Math.min(Math.min(radius - c.cenoteDist, c.cenoteTop - y), y - c.cenoteBottom));
		}
		if (best > -6) {
			best += (float) (2.4 * this.n[13].get(x / 22.0, y / 14.0, z / 22.0));
		}
		return best;
	}

	/** {@link #inside}, for the passages, chambers, fissures and ways in: sampled on a finer grid. */
	public float fine(int x, int y, int z) {
		return this.fine(this.info(x, z), x, y, z);
	}

	/** {@link #fine(int, int, int)} with the column's {@link Info} in hand. */
	public float fine(Info c, int x, int y, int z) {
		if (!c.fine || y < c.fineLow - 6 || y > c.fineHigh + 6) {
			return -64;
		}
		float best = -64;
		float rough = 1.5F;
		for (int k = 0; k < LEVELS; k++) {
			Passage p = c.passages[k];
			if (p.on) {
				float across = Mth.clamp(p.dist / p.halfWidth, 0, 1);
				float roof = p.floor + p.height * (0.35F + 0.65F * (float) Math.sqrt(1 - across * across));
				float in = Math.min(Math.min(y - p.floor, roof - y), p.halfWidth - p.dist);
				if (in > best) {
					best = in;
					rough = 0.3F * p.halfWidth;
				}
			}
			if (p.chamber) {
				float in = Math.min(Math.min(y - p.chamberFloor, p.chamberRoof - y), p.chamberIn);
				if (in > best) {
					best = in;
					rough = 1.6F;
				}
			}
		}
		if (c.fissure) {
			// a lens in section, widest a third of the way up; a ravine opens out toward the top instead
			float h = (y - c.fissureBottom) / (c.fissureTop - c.fissureBottom);
			float width;
			if (c.ravine) {
				width = c.fissureHalfWidth * (0.5F + 0.9F * Mth.clamp(h, 0, 1)) * (float) Math.sqrt(Mth.clamp(h * 6, 0, 1));
			} else {
				float m = h < 0.35F ? h / 0.35F : (1 - h) / 0.65F;
				width = c.fissureHalfWidth * (float) Math.sqrt(Mth.clamp(m, 0, 1));
			}
			float in = Math.min(Math.min(y - c.fissureBottom, c.fissureTop - y), width - c.fissureDist);
			if (in > best) {
				best = in;
				rough = 0.6F;
			}
		}
		if (c.adit) {
			float roof = c.aditFloor + c.aditHeight * (0.4F + 0.6F * (float) Math.sqrt(Math.max(0, 1 - sq(c.aditDist / c.aditHalfWidth))));
			best = Math.max(best, Math.min(Math.min(y - c.aditFloor, roof - y), c.aditHalfWidth - c.aditDist));
		}
		if (c.shaft) {
			// a funnel at the top, where the ground has slumped in
			float funnel = 6 * sq(Mth.clamp(1 - (c.shaftTop - 4 - y) / 12, 0, 1));
			best = Math.max(best, Math.min(Math.min(y - c.shaftBottom, c.shaftTop - y), c.shaftRadius + funnel - c.shaftDist));
		}
		if (best > -5) {
			best += (float) (rough * this.n[28].get(x / 9.0, y / 7.0, z / 9.0));
		}
		return best;
	}

	/**
	 * Whether the aquifers are kept out of (x, y, z): everywhere. The only open spaces underground are the
	 * model's, and they hold only the water and lava poured into them (and vanilla's lava below y
	 * {@value #LAVA_LEVEL}, which comes before any aquifer).
	 */
	public boolean dry(int x, int y, int z) {
		return true;
	}

	private static float sq(float v) {
		return v * v;
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
