package dev.voidmc.expanse.world.terrain;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;

/**
 * The countries of the land: regions some five kilometres across, each with a character of its own laid over the
 * plates' eroded relief, so a journey crosses from one country into the next rather than from patch to patch of
 * biome. They are drawn from the Bohemia of the old maps (the KCD II look the Expanse is after) and each reshapes
 * the land its own way:
 *
 * <ul>
 *   <li><b>Heartland</b>: rolling loess country, its hills worn low over broad vales (fields and woods).</li>
 *   <li><b>Rock city</b>: sandstone country where the plateau has weathered into towns of rock, blocks and towers
 *   twenty to fifty blocks high split by narrow corridors, forest on their tops (Bohemian Paradise, Adršpach).</li>
 *   <li><b>Volcanic hills</b>: rolling country studded with lone bell-shaped hills, the old volcanoes' cores, a
 *   cap of basalt on the steeper ones (the České středohoří, where the castles stand).</li>
 *   <li><b>Lake district</b>: land the ice left: scoured low, swarms of drumlins all lying one way, more lakes.</li>
 *   <li><b>Gorges</b>: a limestone plateau, level-topped, its rivers sunk in steep-walled gorges (Moravian Karst).</li>
 *   <li><b>Old forest</b>: broad, rounded, forested uplands, the worn-down roots of old mountains (Šumava).</li>
 * </ul>
 *
 * <p>The map is a Voronoi diagram of jittered sites on a {@value #CELL}-block grid, its borders bent by a broad
 * warp. A country is strongest inside and fades over {@value #FADE} blocks toward a border with another kind, so
 * neighbouring countries grade into each other. What kind a site is follows the climate at it: the Bohemian
 * countries in the temperate zone, glacial lakes and old forest in the cold, gorges and volcanic hills here and
 * there in the warm; elsewhere (the steppe, deserts and tropics) the land keeps its own landforms.
 */
final class Countries {
	static final int NONE = 0;
	static final int HEARTLAND = 1;
	static final int ROCK_CITY = 2;
	static final int VOLCANIC_HILLS = 3;
	static final int LAKE_DISTRICT = 4;
	static final int GORGES = 5;
	static final int OLD_FOREST = 6;
	static final String[] NAMES = {"none", "heartland", "rock_city", "volcanic_hills", "lake_district", "gorges", "old_forest"};

	/** The rock a column is made of under its soil (the chunk generator lays it in place of stone). */
	static final int STONE = 0;
	static final int SANDSTONE = 1;
	static final int LIMESTONE = 2;
	static final int BASALT = 3;

	private static final int CELL = 5200;
	private static final float FADE = 900;
	private static final double WARP = 1500;

	private final TerrainModel model;
	private final long seed;
	/** The kind of each site, by cell (worked out once from the climate there). */
	private final ConcurrentHashMap<Long, Integer> kinds = new ConcurrentHashMap<>();

	Countries(TerrainModel model, long seed) {
		this.model = model;
		this.seed = seed;
	}

	/** Where (x, z) lies on the map of countries. */
	static final class Here {
		/** (the sites of the 9 x 9 cells round (siteX, siteZ), and their kinds once asked: -1 not yet) */
		int siteX = Integer.MIN_VALUE;
		int siteZ;
		final double[] px = new double[81];
		final double[] pz = new double[81];
		final int[] kinds = new int[81];
		int kind;
		/** 1 well inside the country, falling to 0 at a border with another kind. */
		float strength;
		/** A number of the country's own (its drumlins' bearing and the like). */
		long seed;
	}

	void locate(double x, double z, Here out) {
		double wx = x + WARP * this.model.noise(TerrainModel.N_COUNTRY, x / 4100.0, z / 4100.0);
		double wz = z + WARP * this.model.noise(TerrainModel.N_COUNTRY, x / 4100.0 + 57, z / 4100.0 - 31);
		int cx = Math.floorDiv((int) wx, CELL);
		int cz = Math.floorDiv((int) wz, CELL);
		// the sites round about, worked out once for every column near them (cells are kilometres across)
		if (out.siteX == Integer.MIN_VALUE || Math.abs(cx - out.siteX) > 1 || Math.abs(cz - out.siteZ) > 1) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					int i = (dx + 4) * 9 + dz + 4;
					long h = PlateMap.mix(this.seed ^ 0x434F_554EL, cx + dx, cz + dz);
					out.px[i] = (cx + dx + 0.1 + 0.8 * PlateMap.unit(h)) * CELL;
					out.pz[i] = (cz + dz + 0.1 + 0.8 * PlateMap.unit(h >>> 21)) * CELL;
					out.kinds[i] = -1;
				}
			}
			out.siteX = cx;
			out.siteZ = cz;
		}
		int ox0 = out.siteX - 4;
		int oz0 = out.siteZ - 4;
		// the nearest site, then how far it is to the border with the nearest site of another kind
		double best = Double.MAX_VALUE;
		int bx = 0;
		int bz = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int i = (cx + dx - ox0) * 9 + cz + dz - oz0;
				double d = (wx - out.px[i]) * (wx - out.px[i]) + (wz - out.pz[i]) * (wz - out.pz[i]);
				if (d < best) {
					best = d;
					bx = cx + dx;
					bz = cz + dz;
				}
			}
		}
		int bi = (bx - ox0) * 9 + bz - oz0;
		double sx = out.px[bi];
		double sz = out.pz[bi];
		int kind = this.kindAt(out, bi, bx, bz);
		double border = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int ox = bx + dx;
				int oz = bz + dz;
				int i = (ox - ox0) * 9 + oz - oz0;
				double px = out.px[i];
				double pz = out.pz[i];
				// distance to the bisector between the two sites
				double ex = px - sx;
				double ez = pz - sz;
				double len = Math.sqrt(ex * ex + ez * ez);
				double toBisector = (((px * px + pz * pz) - (sx * sx + sz * sz)) * 0.5 - (wx * ex + wz * ez)) / len;
				if (toBisector < border && this.kindAt(out, i, ox, oz) != kind) {
					border = toBisector;
				}
			}
		}
		out.kind = kind;
		out.strength = smooth((float) (border / FADE));
		out.seed = PlateMap.mix(this.seed ^ 0x5345_4544L, bx, bz);
	}

	/** The kind of site {@code i} of the Here's block (cell (cx, cz)), asked once. */
	private int kindAt(Here out, int i, int cx, int cz) {
		int k = out.kinds[i];
		if (k < 0) {
			k = out.kinds[i] = this.kindOf(cx, cz, out.px[i], out.pz[i]);
		}
		return k;
	}

	/** A per-thread cache in front of {@link #kinds} (a column asks after 25 sites; the shared map boxes every key). */
	private final ThreadLocal<long[]> nearKeys = ThreadLocal.withInitial(() -> {
		long[] a = new long[256];
		java.util.Arrays.fill(a, Long.MIN_VALUE);
		return a;
	});
	private final ThreadLocal<int[]> nearKinds = ThreadLocal.withInitial(() -> new int[256]);

	private int kindOf(int cx, int cz, double px, double pz) {
		long key = (long) cx << 32 ^ cz & 0xFFFF_FFFFL;
		long[] keys = this.nearKeys.get();
		int slot = (int) (PlateMap.mix(9, cx, cz) & 255);
		if (keys[slot] == key) {
			return this.nearKinds.get()[slot];
		}
		int found = this.sharedKindOf(key, cx, cz, px, pz);
		keys[slot] = key;
		this.nearKinds.get()[slot] = found;
		return found;
	}

	private int sharedKindOf(long packed, int cx, int cz, double px, double pz) {
		long key = PlateMap.scramble(packed);   // (the packed pair alone hashes to cx ^ cz: a diagonal in one bucket)
		Integer k = this.kinds.get(key);
		if (k == null) {
			float[] climate = this.model.climate(px, pz);
			k = choose(climate[0], climate[1], PlateMap.unit(PlateMap.mix(this.seed ^ 0x4B49_4E44L, cx, cz)));
			this.kinds.putIfAbsent(key, k);
		}
		return k;
	}

	/** A country's kind from the climate at its site and a roll. */
	static int choose(float temperature, float humidity, double roll) {
		// (the bands are vanilla's biome table's: below -0.45 the cold, to 0.2 the temperate, to 0.55 the warm where the
		// savannas and jungles grow, and over that the hot)
		boolean dry = humidity < -0.2F;
		if (temperature < -0.45F) {
			return roll < 0.45 ? LAKE_DISTRICT : roll < 0.75 ? OLD_FOREST : NONE;
		}
		if (temperature >= 0.55F) {
			return roll < 0.12 && !dry ? GORGES : NONE;
		}
		if (temperature >= 0.2F) {
			// the warm country: limestone gorges and old volcanoes, as round the Mediterranean
			return roll < 0.22 ? GORGES : roll < 0.42 ? VOLCANIC_HILLS : NONE;
		}
		if (dry) {
			return roll < 0.25 ? GORGES : NONE;    // the dry temperate country is steppe, its own
		}
		// the temperate zone: Bohemia
		double[] share = {0.30, 0.14, 0.16, 0.14, 0.12, 0.14};
		int[] kind = {HEARTLAND, ROCK_CITY, VOLCANIC_HILLS, LAKE_DISTRICT, GORGES, OLD_FOREST};
		double acc = 0;
		for (int i = 0; i < share.length; i++) {
			acc += share[i];
			if (roll < acc) {
				return kind[i];
			}
		}
		return HEARTLAND;
	}

	// ------------------------------------------------------------------------------------------------ the shapes

	/** A volcanic hill: its centre, radius, relief and whether its top is level. */
	record Hill(float x, float z, float r, float relief, boolean level) {
	}

	private static final Hill NO_HILL = new Hill(0, 0, 0, 0, false);
	/** Volcanic hills by cell, once sited (a river's course is looked up for each). */
	private final ConcurrentHashMap<Long, Hill> hills = new ConcurrentHashMap<>();

	/**
	 * Volcanic hills: on a 500-block grid, four cells in five hold a hill, bell-shaped, 110 to 260 blocks in radius
	 * (its outline wavering), 45 to 115 blocks high; one in four stands with a level top, where a castle would go.
	 * Rivers flow round them: a hill is made smaller where a river passes near, and left out if it would have to
	 * shrink too far. Returns the rise; how far up the steeper part of a hill the column is (where its basalt
	 * shows) goes in {@code core}.
	 */
	float volcanicHill(int x, int z, long countrySeed, float[] core) {
		int cell = 500;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float rise = 0;
		core[0] = 0;
		core[1] = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Hill hill = this.hill(cx + dx, cz + dz, cell, countrySeed);
				if (hill == NO_HILL) {
					continue;
				}
				double ddx = x - hill.x();
				double ddz = z - hill.z();
				double d = Math.sqrt(ddx * ddx + ddz * ddz);
				if (d > hill.r() * 1.15) {
					continue;
				}
				double bearing = Math.atan2(ddz, ddx);
				double rr = hill.r() * (1 + 0.13 * this.model.noise(TerrainModel.N_DOME, Math.cos(bearing) * 1.3 + hill.x() / 91.0,
					Math.sin(bearing) * 1.3 + hill.z() / 91.0));
				double t = d / rr;
				if (t >= 1) {
					continue;
				}
				float bell = (float) Math.pow(1 - t * t, 2.2);
				float h = hill.relief() * bell;
				boolean top = false;
				if (hill.level()) {
					top = h >= hill.relief() * 0.82F;
					h = Math.min(h, hill.relief() * 0.82F);
				}
				if (h > rise) {
					rise = h;
					core[0] = smooth((bell - 0.35F) / 0.3F);
					core[1] = top ? 1 : 0;
				}
			}
		}
		return rise;
	}

	private Hill hill(int cx, int cz, int cell, long countrySeed) {
		long h = PlateMap.mix(countrySeed ^ 0x4849_4C4CL, cx, cz);
		if (PlateMap.unit(h) > 0.8) {
			return NO_HILL;
		}
		long key = PlateMap.mix(countrySeed, cx, cz);
		Hill hill = this.hills.get(key);
		if (hill == null) {
			float px = (float) ((cx + 0.2 + 0.6 * PlateMap.unit(h >>> 11)) * cell);
			float pz = (float) ((cz + 0.2 + 0.6 * PlateMap.unit(h >>> 31)) * cell);
			float r = 110 + 150 * (float) PlateMap.unit(h >>> 5);
			float clearance = this.model.riverClearance(px, pz);
			r = Math.min(r, (clearance - 30) / 1.15F);
			hill = r < 85 ? NO_HILL : new Hill(px, pz, r, (45 + 70 * (float) PlateMap.unit(h >>> 17)) * Math.min(1, r / 150), PlateMap.unit(h >>> 41) < 0.25);
			if (this.hills.size() > 20000) {
				this.hills.clear();
			}
			this.hills.putIfAbsent(key, hill);
		}
		return hill;
	}

	/**
	 * A town of rock: where it stands (a patchy noise over the country, never close to a river), the ground is
	 * split into blocks about a dozen wide by corridors two to four wide, and each block stands as a tower of rock
	 * 16 to 50 blocks high (lower toward the edge of the town), one in five left as a glade. Returns the rise.
	 */
	float rockTown(int x, int z, float strength, float riverClear) {
		float patch = smooth((float) (this.model.noise(TerrainModel.N_ROCK, x / 620.0, z / 620.0) - 0.12) / 0.22F) * strength * riverClear;
		if (patch <= 0.01F) {
			return 0;
		}
		// the blocks: a jittered grid, the corridors where two cells meet
		float g = 12;
		int ix = Mth.floor(x / g);
		int iz = Mth.floor(z / g);
		double f1 = Double.MAX_VALUE;
		double f2 = Double.MAX_VALUE;
		long id = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(this.seed ^ 0x524F_434BL, ix + dx, iz + dz);
				double px = (ix + dx + 0.15 + 0.7 * PlateMap.unit(h)) * g;
				double pz = (iz + dz + 0.15 + 0.7 * PlateMap.unit(h >>> 21)) * g;
				double d = PlateMap.len(x - px, z - pz);
				if (d < f1) {
					f2 = f1;
					f1 = d;
					id = h;
				} else if (d < f2) {
					f2 = d;
				}
			}
		}
		if (PlateMap.unit(id >>> 7) < 0.2) {
			return 0;   // a glade among the rocks
		}
		float crack = (float) (f2 - f1);   // twice the distance to the corridor's middle
		float width = 2.2F + 2.4F * (float) PlateMap.unit(id >>> 29);
		float wall = smooth((crack - width) / 1.6F);
		float tall = (16 + 34 * (float) PlateMap.unit(id >>> 13)) * (0.35F + 0.65F * smooth((patch - 0.1F) / 0.5F));
		// rounded off at the top edge, as sandstone weathers
		float round = 1 - 0.12F * (1 - smooth((crack - width - 1) / 5));
		return tall * wall * round;
	}

	/**
	 * Drumlins: swarms of low whaleback hills all lying along the way the ice flowed, each 120 to 200 blocks long and
	 * 50 to 75 wide, 6 to 16 high, blunt at the end the ice came from. Returns the rise.
	 */
	float drumlins(int x, int z, long countrySeed) {
		double bearing = PlateMap.unit(countrySeed >>> 3) * Math.PI;
		double cos = Math.cos(bearing);
		double sin = Math.sin(bearing);
		double u = x * cos + z * sin;
		double v = -x * sin + z * cos;
		double cu = 230;
		double cv = 95;
		int iu = Mth.floor(u / cu);
		int iv = Mth.floor(v / cv);
		float rise = 0;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				int ju = iu + du;
				int jv = iv + dv;
				long h = PlateMap.mix(countrySeed ^ 0x4452_554DL, ju, jv);
				if (PlateMap.unit(h) > 0.72) {
					continue;
				}
				double pu = (ju + 0.5 * (jv & 1) + 0.2 + 0.6 * PlateMap.unit(h >>> 9)) * cu;
				double pv = (jv + 0.25 + 0.5 * PlateMap.unit(h >>> 29)) * cv;
				double a = 60 + 40 * PlateMap.unit(h >>> 15);
				double b = 25 + 12 * PlateMap.unit(h >>> 37);
				double eu = u - pu;
				double ev = v - pv;
				if (eu < 0) {
					a *= 0.7;   // the blunt end, where the ice came from
				}
				double q = (eu / a) * (eu / a) + (ev / b) * (ev / b);
				if (q >= 1) {
					continue;
				}
				float hh = (6 + 10 * (float) PlateMap.unit(h >>> 45)) * (float) Math.pow(1 - q, 1.4);
				rise = Math.max(rise, hh);
			}
		}
		return rise;
	}

	/**
	 * Sinkholes on a karst plateau: on a 70-block grid, one cell in three holds a round hollow 6 to 13 blocks across
	 * and 3 to 7 deep, bowl-shaped. Returns the depth.
	 */
	float doline(int x, int z) {
		int g = 70;
		int ix = Math.floorDiv(x, g);
		int iz = Math.floorDiv(z, g);
		float depth = 0;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				int cx = ix + (x - ix * g < g / 2 ? dx - 1 : dx);
				int cz = iz + (z - iz * g < g / 2 ? dz - 1 : dz);
				long h = PlateMap.mix(this.seed ^ 0x444F_4C49L, cx, cz);
				if (PlateMap.unit(h) > 0.33) {
					continue;
				}
				double px = (cx + 0.25 + 0.5 * PlateMap.unit(h >>> 9)) * g;
				double pz = (cz + 0.25 + 0.5 * PlateMap.unit(h >>> 29)) * g;
				double r = 6 + 7 * PlateMap.unit(h >>> 17);
				double t = PlateMap.len(x - px, z - pz) / r;
				if (t < 1) {
					depth = Math.max(depth, (3 + 4 * (float) PlateMap.unit(h >>> 41)) * (float) (1 - t * t));
				}
			}
		}
		return depth;
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
