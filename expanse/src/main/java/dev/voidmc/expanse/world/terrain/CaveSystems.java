package dev.voidmc.expanse.world.terrain;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Cave systems of their own, each a shape no other cave has, beside the network of passages, tunnels and
 * realms that runs under all the land ({@link CavernModel}). Some are bound to the land above, some turn up
 * anywhere; some are common, some rare:
 * <ul>
 *   <li><b>Lava tubes</b> (every volcano has a few): long smooth tubes radiating down a volcano's flanks just
 *       under the ground, where lava ran under its own crust and drained away; round in section over a flat
 *       floor, opening to the sky through skylights where the thin roof fell in.</li>
 *   <li><b>Maze caves</b> (uncommon, in patches a few hundred blocks across): a labyrinth cut along the
 *       joints of the rock, narrow passages crossing at right angles every ten blocks or so, tall and pinched
 *       at the top, some joints sealed, so they loop and dead-end; a small room where some cross. At the depth
 *       of the shallow passages, which run into them.</li>
 *   <li><b>Abysses</b> (rare: one in a few square kilometres): a shaft twenty to fifty blocks across falling
 *       from the open ground a hundred and fifty blocks and more to a pool at the bottom, its walls swelling and
 *       pinching, ringed with ledges, a spring pouring off the lip.</li>
 *   <li><b>Rift halls</b> (rare): an underground canyon two or three hundred blocks long, narrow and up to a
 *       hundred high, its walls closing overhead, boulders wedged across it high up.</li>
 *   <li><b>Geodes</b> (rare, deep): a hollow egg of rock lined with amethyst, crossed by giant crystal beams;
 *       where a deep passage runs into one there is a way in, elsewhere only for the miner.</li>
 * </ul>
 * As everything in {@link CavernModel}, each is decided per column and measured as a distance inside open
 * space; what grows on them and lines them is {@link CavernLife}'s.
 */
final class CaveSystems {
	private static final int TUBE_REACH_MAX = 760;
	private static final int ABYSS_CELL = 2400;
	private static final int RIFT_CELL = 1800;
	private static final int GEODE_CELL = 1100;

	private final CavernModel caverns;
	private final TerrainModel terrain;
	private final SimplexNoise[] n = new SimplexNoise[12];
	private final long salt;
	private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

	/** An abyss: its axis, radius, the ground at its lip, its bottom, and the phase of its swells and ledges. */
	record Abyss(boolean exists, float x, float z, float radius, float lip, float bottom, float phase, float spring) {
		static final Abyss NONE = new Abyss(false, 0, 0, 0, 0, 0, 0, 0);
	}

	/** A rift hall: its middle, direction, length, widest half width, floor and top, and its wedged boulders. */
	record Rift(boolean exists, float x, float z, float cos, float sin, float length, float width, float bottom, float top, long seed, int chocks) {
		static final Rift NONE = new Rift(false, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
	}

	/** A geode: its centre, its horizontal and vertical radii, and its crystal beams. */
	record Geode(boolean exists, float x, float y, float z, float a, float c, long seed, int beams) {
		static final Geode NONE = new Geode(false, 0, 0, 0, 0, 0, 0, 0);
	}

	private static final class Cache {
		final long[] abyssKeys = new long[16];
		final Abyss[] abysses = new Abyss[16];
		final long[] riftKeys = new long[16];
		final Rift[] rifts = new Rift[16];
		final long[] geodeKeys = new long[16];
		final Geode[] geodes = new Geode[16];
	}

	CaveSystems(CavernModel caverns, TerrainModel terrain, long seed) {
		this.caverns = caverns;
		this.terrain = terrain;
		RandomSource random = RandomSource.create(seed ^ 0x5359_5354_454D_53L);
		for (int i = 0; i < this.n.length; i++) {
			this.n[i] = new SimplexNoise(random);
		}
		this.salt = PlateMap.mix(seed, 0x5359_53L, 0x5445_4DL);
	}

	private double noise(int i, double x, double z) {
		return Mth.clamp(this.n[i].get(x, z), -1.0F, 1.0F);
	}

	/** Works out the systems at a column, into {@code out}; {@code shallow} is the shallow passages' floor here. */
	void compute(int x, int z, CavernModel.Info out, float shallow) {
		Cache c = this.cache.get();
		this.tubes(x, z, out);
		this.maze(x, z, out, shallow);
		this.abyss(c, x, z, out);
		this.rift(c, x, z, out);
		this.geode(c, x, z, out);
		out.systems = out.tube || out.skylight || out.maze || out.abyss || out.rift || out.geode;
	}

	/** How far (blocks) (x, y, z) is inside any of the systems' open space; -64 if none is near. */
	float inside(CavernModel.Info c, int x, int y, int z) {
		float best = -64;
		if (c.tube) {
			best = Math.max(best, this.tubeInside(c, y));
		}
		if (c.skylight) {
			best = Math.max(best, Math.min(Math.min(c.skylightRadius - c.skylightDist, c.skylightTop - y), y - c.tubeFloor));
		}
		if (c.maze) {
			best = Math.max(best, this.mazeInside(c, y));
		}
		if (c.abyss) {
			best = Math.max(best, this.abyssInside(c, x, y, z));
		}
		if (c.rift) {
			best = Math.max(best, this.riftInside(c, y));
		}
		if (c.geode) {
			best = Math.max(best, this.geodeInside(c, x, y, z));
		}
		return best;
	}

	// ------------------------------------------------------------------ lava tubes

	private void tubes(int x, int z, CavernModel.Info out) {
		out.tube = false;
		out.skylight = false;
		Column land = this.terrain.sample(x, z);
		float ground = land.height;
		if (ground < 66 || land.landform == Landform.CANYON) {
			return;
		}
		PlateMap.Plate plate = this.terrain.plates().plate(land.plateX, land.plateZ);
		List<PlateTerrain.Volcano> volcanoes = this.terrain.terrain(plate).volcanoes();
		if (volcanoes.isEmpty()) {
			return;
		}
		float bestIn = -Float.MAX_VALUE;
		for (PlateTerrain.Volcano v : volcanoes) {
			double dx = x - v.x();
			double dz = z - v.z();
			double d = Math.sqrt(dx * dx + dz * dz);
			double r = v.radius();
			if (d > Math.min(TUBE_REACH_MAX, r * 1.65)) {
				continue;
			}
			long vh = PlateMap.mix(this.salt ^ 0x5455_4245L, Math.round(v.x()), Math.round(v.z()));
			int count = 3 + (int) (PlateMap.unit(vh) * 4);
			double angle = Math.atan2(dz, dx);
			for (int i = 0; i < count; i++) {
				long th = PlateMap.mix(vh, i, 31);
				double start = Math.max(r * 0.22, v.crater() * 1.6 + 8);
				double end = r * (1.05 + 0.55 * PlateMap.unit(th >>> 9));
				if (d < start - 6 || d > end + 6) {
					continue;
				}
				// the tube's course: out from the summit at its own bearing, bending slowly as it goes
				double bearing = this.tubeBearing(th, i, count, d, v);
				double off = Mth.wrapDegrees(Math.toDegrees(angle - bearing)) * Math.PI / 180.0;
				if (Math.cos(off) <= 0) {
					continue;
				}
				float lateral = (float) Math.abs(d * Math.sin(off) - this.tubeSwing(i, d, v));
				float half = (float) (3.4 + 2.2 * (0.5 + 0.5 * this.noise(2, d / 70.0, i * 13.7)));
				half *= smooth((float) (d - start + 6) / 22) * smooth((float) (end + 6 - d) / 34);
				if (half < 1.2F || lateral > half + 2) {
					continue;
				}
				float in = half - lateral;
				if (in <= bestIn) {
					continue;
				}
				// just under the ground, a few blocks of crust over it, wherever the land dips beside it too
				float low = Math.min(ground, Math.min(Math.min(this.terrain.sample(x + 7, z).height, this.terrain.sample(x - 7, z).height),
					Math.min(this.terrain.sample(x, z + 7).height, this.terrain.sample(x, z - 7).height)));
				land = this.terrain.sample(x, z);
				float crust = 4.5F + 2.5F * (float) (0.5 + 0.5 * this.noise(3, d / 55.0, i * 5.1));
				float height = 5 + 3.2F * (float) (0.5 + 0.5 * this.noise(4, d / 48.0, i * 9.9));
				float floor = low - crust - height;
				if (floor < 8 || !this.caverns.clear(out, floor, floor + height)) {
					continue;
				}
				bestIn = in;
				out.tube = true;
				out.tubeDist = lateral;
				out.tubeHalfWidth = half;
				out.tubeFloor = floor;
				out.tubeHeight = height;
				// skylights: here and there the crust has fallen in
				out.skylight = false;
				double spacing = 46 + 26 * PlateMap.unit(th >>> 21);
				int k = (int) Math.floor((d - start) / spacing);
				for (int s = k - 1; s <= k + 1; s++) {
					long sh = PlateMap.mix(th, s, 77);
					if (s < 0 || PlateMap.unit(sh) > 0.42) {
						continue;
					}
					double sd = start + (s + 0.3 + 0.4 * PlateMap.unit(sh >>> 11)) * spacing;
					if (sd > end - 10) {
						continue;
					}
					double sb = this.tubeBearing(th, i, count, sd, v);
					double sw = this.tubeSwing(i, sd, v);
					double hx = v.x() + Math.cos(sb) * sd - Math.sin(sb) * sw;
					double hz = v.z() + Math.sin(sb) * sd + Math.cos(sb) * sw;
					float hr = 2.2F + 2.4F * (float) PlateMap.unit(sh >>> 23);
					float hd = (float) Math.hypot(x - hx, z - hz);
					if (hd < hr + 3 && open(land)) {
						out.skylight = true;
						out.skylightDist = hd;
						out.skylightRadius = hr;
						out.skylightTop = land.height + 4;
					}
				}
			}
		}
		if (out.tube) {
			this.caverns.extentOf(out, out.tubeFloor, out.skylight ? out.skylightTop : out.tubeFloor + out.tubeHeight);
		}
	}

	/** The bearing of tube {@code i} at distance {@code d} from its volcano: its own, bending slowly. */
	private double tubeBearing(long th, int i, int count, double d, PlateTerrain.Volcano v) {
		return Mth.TWO_PI * (i + 0.15 + 0.7 * PlateMap.unit(th)) / count + 0.3 * this.noise(0, d / 210.0 + i * 7.1, v.x() / 97.0);
	}

	/** How far (blocks) tube {@code i} swings to the side of its bearing at distance {@code d}: gentle meanders. */
	private double tubeSwing(int i, double d, PlateTerrain.Volcano v) {
		return 6 * this.noise(1, d / 70.0 + i * 3.3, v.z() / 89.0);
	}

	/** Whether the ground here may be opened to the sky: dry land, no river beside it, no crater lake. */
	private static boolean open(Column land) {
		return land.height >= 67 && !land.underWater(3) && (!land.nearRiver() || land.riverDist > land.riverHalfWidth + 6);
	}

	float tubeInside(CavernModel.Info c, int y) {
		float across = Mth.clamp(c.tubeDist / c.tubeHalfWidth, 0, 1);
		// a round vault over a flat floor, where the last of the lava set level
		float roof = c.tubeFloor + c.tubeHeight * (0.25F + 0.75F * (float) Math.sqrt(1 - across * across));
		return Math.min(Math.min(y - c.tubeFloor, roof - y), c.tubeHalfWidth - c.tubeDist);
	}

	// ------------------------------------------------------------------ maze caves

	private void maze(int x, int z, CavernModel.Info out, float shallow) {
		out.maze = false;
		double gate = this.noise(5, x / 1100.0, z / 1100.0) + 0.35 * this.noise(9, x / 310.0 + 50, z / 310.0);
		if (gate < 0.78) {
			return;
		}
		float keep = 0.45F + 0.35F * smooth((float) (gate - 0.78) / 0.14F);
		// the joints run two ways, at a bearing that turns slowly across the patch
		double theta = Math.PI * this.noise(6, x / 2600.0, z / 2600.0);
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);
		double u = x * cos + z * sin;
		double v = -x * sin + z * cos;
		double su = 9.0;
		double sv = 12.0;
		long ms = this.salt ^ 0x4D41_5A45L;
		long iu = Math.round(u / su);
		long iv = Math.round(v / sv);
		double du = Math.abs(u - iu * su);
		double dv = Math.abs(v - iv * sv);
		float dist = Float.MAX_VALUE;
		// a joint along u = iu*su is open between crossings floor(v/sv) and the next, by chance
		if (PlateMap.unit(PlateMap.mix(ms, iu, Math.floorDiv((long) Math.floor(v), (long) sv) * 2)) < keep) {
			dist = (float) du;
		}
		if (PlateMap.unit(PlateMap.mix(ms + 1, Math.floorDiv((long) Math.floor(u), (long) su) * 2 + 1, iv)) < keep) {
			dist = Math.min(dist, (float) dv);
		}
		// a room where some joints cross
		float room = -Float.MAX_VALUE;
		long rh = PlateMap.mix(ms + 2, iu, iv);
		if (PlateMap.unit(rh) < 0.09) {
			float rr = 2.6F + 2.2F * (float) PlateMap.unit(rh >>> 13);
			room = rr - (float) Math.sqrt(du * du + dv * dv);
		}
		float half = 1.15F + 0.6F * (float) (0.5 + 0.5 * this.noise(7, x / 33.0, z / 33.0));
		if (dist > half + 2 && room < -2) {
			return;
		}
		float height = 5.5F + 4 * (float) (0.5 + 0.5 * this.noise(8, x / 41.0, z / 41.0));
		float floor = Math.min(shallow + 1.5F * (float) this.noise(9, x / 23.0, z / 23.0), out.cap - 1 - height);
		if (floor < 4 || !this.caverns.clear(out, floor, floor + height)) {
			return;
		}
		out.maze = true;
		out.mazeDist = Math.min(dist, 99);
		out.mazeHalfWidth = half;
		out.mazeFloor = floor;
		out.mazeHeight = height;
		out.mazeRoom = room;
		this.caverns.extentOf(out, floor, floor + height + 2);
	}

	float mazeInside(CavernModel.Info c, int y) {
		float h = (y - c.mazeFloor) / c.mazeHeight;
		// the joints pinch shut toward the top
		float width = c.mazeHalfWidth * (float) Math.sqrt(Mth.clamp(1 - h * h * h, 0, 1));
		float in = Math.min(Math.min(y - c.mazeFloor, c.mazeFloor + c.mazeHeight - y), width - c.mazeDist);
		if (c.mazeRoom > -2) {
			float roomRoof = c.mazeFloor + c.mazeHeight * 0.8F;
			in = Math.max(in, Math.min(Math.min(y - c.mazeFloor, roomRoof - y), c.mazeRoom));
		}
		return in;
	}

	// ------------------------------------------------------------------ abysses

	private void abyss(Cache c, int x, int z, CavernModel.Info out) {
		out.abyss = false;
		int ax = Math.floorDiv(x, ABYSS_CELL);
		int az = Math.floorDiv(z, ABYSS_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Abyss a = this.abyss(c, ax + dx, az + dz);
				if (!a.exists()) {
					continue;
				}
				float d = (float) Math.hypot(x - a.x(), z - a.z());
				if (d > a.radius() * 1.6F + 14) {
					continue;
				}
				Column land = this.terrain.sample(x, z);
				out.abyss = true;
				out.abyssX = a.x();
				out.abyssZ = a.z();
				out.abyssDist = d;
				out.abyssRadius = a.radius();
				out.abyssTop = land.height + 4;
				out.abyssLip = a.lip();
				out.abyssBottom = a.bottom();
				out.abyssPhase = a.phase();
				out.abyssSpring = a.spring();
				this.caverns.extentOf(out, a.bottom() - 2, land.height + 4);
				return;
			}
		}
	}

	Abyss abyss(int ax, int az) {
		return this.abyss(this.cache.get(), ax, az);
	}

	private Abyss abyss(Cache c, int ax, int az) {
		long key = (long) ax << 32 ^ az & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(5, ax, az) & 15);
		if (c.abysses[slot] != null && c.abyssKeys[slot] == key) {
			return c.abysses[slot];
		}
		Abyss a = this.makeAbyss(ax, az);
		c.abyssKeys[slot] = key;
		c.abysses[slot] = a;
		return a;
	}

	/** Two cells in five have an abyss, where a few tries find dry open ground well away from water and realms. */
	private Abyss makeAbyss(int ax, int az) {
		long h = PlateMap.mix(this.salt ^ 0x4142_5953L, ax, az);
		if (PlateMap.unit(h) >= 0.4) {
			return Abyss.NONE;
		}
		float radius = 10 + 15 * (float) PlateMap.unit(h >>> 13);
		for (int i = 0; i < 8; i++) {
			long hi = PlateMap.mix(h, i, 3);
			float px = (float) ((ax + 0.15 + 0.7 * PlateMap.unit(hi)) * ABYSS_CELL);
			float pz = (float) ((az + 0.15 + 0.7 * PlateMap.unit(hi >>> 21)) * ABYSS_CELL);
			Column land = this.terrain.sample(Mth.floor(px), Mth.floor(pz));
			float lip = land.height;
			if (!open(land) || land.landform == Landform.VOLCANIC || lip < 72 || this.wet(px, pz, radius + 24)
				|| this.nearRealm(px, pz, radius + 40)) {
				continue;
			}
			float bottom = -30 - 14 * (float) PlateMap.unit(hi >>> 33);
			return new Abyss(true, px, pz, radius, lip, bottom, (float) (PlateMap.unit(hi >>> 41) * 1000), (float) (PlateMap.unit(hi >>> 7) * Mth.TWO_PI));
		}
		return Abyss.NONE;
	}

	/** The radius of an abyss at height y: swelling and pinching on the way down, ringed with ledges, ragged. */
	float abyssRadius(CavernModel.Info c, int x, int y, int z) {
		float r = c.abyssRadius * (0.86F + 0.2F * Mth.sin((y + c.abyssPhase) / 19.0F) + 0.08F * Mth.sin((y + c.abyssPhase) / 7.0F));
		// a ledge every twenty-odd blocks: the wall steps in
		float band = Mth.positiveModulo(y + c.abyssPhase * 0.37F, 23.0F);
		if (band < 2.2F) {
			r -= 2.6F;
		}
		double angle = Math.atan2(z - c.abyssZ, x - c.abyssX);
		r += 2.4F * (float) this.n[10].get(Math.cos(angle) * 2.2 + c.abyssPhase, Math.sin(angle) * 2.2 + y / 21.0);
		// the lip has slumped into a funnel
		float lipDepth = c.abyssTop - 4 - y;
		if (lipDepth < 14) {
			r += 7 * sq(1 - Math.max(0, lipDepth) / 14);
		}
		return r;
	}

	float abyssInside(CavernModel.Info c, int x, int y, int z) {
		// a bowl at the bottom, where the pool lies
		float bottom = c.abyssBottom + 5 * sq(Mth.clamp(c.abyssDist / Math.max(1, c.abyssRadius), 0, 1));
		return Math.min(Math.min(this.abyssRadius(c, x, y, z) - c.abyssDist, c.abyssTop - y), y - bottom);
	}

	/** The pool at the bottom of the abyss: its surface. */
	static float abyssPool(CavernModel.Info c) {
		return c.abyssBottom + 6;
	}

	private boolean wet(float x, float z, float reach) {
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			Column land = this.terrain.sample(Mth.floor(x + Math.cos(a) * reach), Mth.floor(z + Math.sin(a) * reach));
			if (land.height < 66 || land.underWater(3) || land.nearRiver() && land.riverDist < land.riverHalfWidth + 6) {
				return true;
			}
		}
		return false;
	}

	private boolean nearRealm(float x, float z, float reach) {
		int hx = Math.floorDiv(Mth.floor(x), CavernModel.HALL_CELL);
		int hz = Math.floorDiv(Mth.floor(z), CavernModel.HALL_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				CavernModel.Hall h = this.caverns.hall(hx + dx, hz + dz);
				if (h.exists() && Math.hypot(h.x() - x, h.z() - z) < h.radius() + 40 + reach) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ rift halls

	private void rift(Cache c, int x, int z, CavernModel.Info out) {
		out.rift = false;
		int rx = Math.floorDiv(x, RIFT_CELL);
		int rz = Math.floorDiv(z, RIFT_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Rift r = this.rift(c, rx + dx, rz + dz);
				if (!r.exists()) {
					continue;
				}
				float px = x - r.x();
				float pz = z - r.z();
				float along = px * r.cos() + pz * r.sin();
				float t = along / r.length() + 0.5F;
				if (t <= 0 || t >= 1) {
					continue;
				}
				// it winds a little along its length
				float wander = 7 * (float) this.noise(11, along / 60.0, r.x() / 50.0);
				float lateral = Math.abs(-px * r.sin() + pz * r.cos() - wander);
				float width = r.width() * (float) Math.pow(Mth.sin(t * Mth.PI), 0.4);
				if (lateral > width + 3) {
					continue;
				}
				float top = Math.min(r.top(), out.cap - 3);
				if (top - r.bottom() < 24 || !this.caverns.clear(out, r.bottom(), top)) {
					continue;
				}
				out.rift = true;
				out.riftDist = lateral;
				out.riftHalfWidth = width;
				out.riftBottom = r.bottom() + 2 * (float) this.noise(9, along / 19.0, r.z() / 40.0);
				out.riftTop = top;
				out.riftAlong = along;
				out.riftSeed = r.seed();
				out.riftChocks = r.chocks();
				out.riftLength = r.length();
				this.caverns.extentOf(out, out.riftBottom, top);
				return;
			}
		}
	}

	Rift rift(int rx, int rz) {
		return this.rift(this.cache.get(), rx, rz);
	}

	private Rift rift(Cache c, int rx, int rz) {
		long key = (long) rx << 32 ^ rz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(6, rx, rz) & 15);
		if (c.rifts[slot] != null && c.riftKeys[slot] == key) {
			return c.rifts[slot];
		}
		long h = PlateMap.mix(this.salt ^ 0x5249_4654L, rx, rz);
		Rift r = Rift.NONE;
		if (PlateMap.unit(h) < 0.4) {
			float x = (float) ((rx + 0.2 + 0.6 * PlateMap.unit(h >>> 7)) * RIFT_CELL);
			float z = (float) ((rz + 0.2 + 0.6 * PlateMap.unit(h >>> 17)) * RIFT_CELL);
			double angle = Math.PI * PlateMap.unit(h >>> 27);
			float length = 150 + 170 * (float) PlateMap.unit(h >>> 37);
			float width = 4 + 5 * (float) PlateMap.unit(h >>> 45);
			float bottom = -26 + 30 * (float) PlateMap.unit(h >>> 51);
			float top = bottom + 70 + 50 * (float) PlateMap.unit(h >>> 3);
			if (!this.nearRealm(x, z, length * 0.5F + 20)) {
				r = new Rift(true, x, z, (float) Math.cos(angle), (float) Math.sin(angle), length, width, bottom, top, h, 3 + (int) (PlateMap.unit(h >>> 11) * 4));
			}
		}
		c.riftKeys[slot] = key;
		c.rifts[slot] = r;
		return r;
	}

	float riftInside(CavernModel.Info c, int y) {
		// a lens in section, widest low down, the walls leaning together overhead
		float h = (y - c.riftBottom) / (c.riftTop - c.riftBottom);
		float m = h < 0.25F ? h / 0.25F : (1 - h) / 0.75F;
		float width = c.riftHalfWidth * (float) Math.sqrt(Mth.clamp(m, 0, 1));
		float in = Math.min(Math.min(y - c.riftBottom, c.riftTop - y), width - c.riftDist);
		// boulders fallen in and wedged across, high up
		for (int k = 0; k < c.riftChocks; k++) {
			long ch = PlateMap.mix(c.riftSeed, k, 91);
			float at = (float) ((PlateMap.unit(ch) - 0.5) * c.riftLength * 0.8);
			float cy = c.riftBottom + (c.riftTop - c.riftBottom) * (0.4F + 0.35F * (float) PlateMap.unit(ch >>> 17));
			float cr = 3 + 2.5F * (float) PlateMap.unit(ch >>> 29);
			float d = (float) Math.sqrt(sq(c.riftAlong - at) + sq(c.riftDist) + sq(y - cy));
			in = Math.min(in, d - cr);
		}
		return in;
	}

	// ------------------------------------------------------------------ geodes

	private void geode(Cache c, int x, int z, CavernModel.Info out) {
		out.geode = false;
		int gx = Math.floorDiv(x, GEODE_CELL);
		int gz = Math.floorDiv(z, GEODE_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Geode g = this.geode(c, gx + dx, gz + dz);
				if (!g.exists() || Math.abs(x - g.x()) > g.a() + 8 || Math.abs(z - g.z()) > g.a() + 8) {
					continue;
				}
				out.geode = true;
				out.geodeX = g.x();
				out.geodeY = g.y();
				out.geodeZ = g.z();
				out.geodeA = g.a();
				out.geodeC = g.c();
				out.geodeSeed = g.seed();
				out.geodeBeams = g.beams();
				this.caverns.extentOf(out, g.y() - g.c() - 4, g.y() + g.c() + 4);
				return;
			}
		}
	}

	Geode geode(int gx, int gz) {
		return this.geode(this.cache.get(), gx, gz);
	}

	private Geode geode(Cache c, int gx, int gz) {
		long key = (long) gx << 32 ^ gz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(7, gx, gz) & 15);
		if (c.geodes[slot] != null && c.geodeKeys[slot] == key) {
			return c.geodes[slot];
		}
		long h = PlateMap.mix(this.salt ^ 0x4745_4F44L, gx, gz);
		Geode g = Geode.NONE;
		if (PlateMap.unit(h) < 0.2) {
			float x = (float) ((gx + 0.2 + 0.6 * PlateMap.unit(h >>> 7)) * GEODE_CELL);
			float z = (float) ((gz + 0.2 + 0.6 * PlateMap.unit(h >>> 17)) * GEODE_CELL);
			float a = 9 + 10 * (float) PlateMap.unit(h >>> 27);
			float y = -36 + 22 * (float) PlateMap.unit(h >>> 37);
			Column land = this.terrain.sample(Mth.floor(x), Mth.floor(z));
			if (land.height > 66 && !this.nearRealm(x, z, a + 20)) {
				g = new Geode(true, x, y, z, a, a * 0.78F, h, 4 + (int) (PlateMap.unit(h >>> 45) * 4));
			}
		}
		c.geodeKeys[slot] = key;
		c.geodes[slot] = g;
		return g;
	}

	float geodeInside(CavernModel.Info c, int x, int y, int z) {
		double q = Math.sqrt(sq((x - c.geodeX) / c.geodeA) + sq((y - c.geodeY) / c.geodeC) + sq((z - c.geodeZ) / c.geodeA));
		return (float) ((1 - q) * Math.min(c.geodeA, c.geodeC)) + 0.8F * (float) this.n[10].get(x / 6.0, y / 6.0 + 300, z / 6.0);
	}

	/**
	 * The crystal beams of a geode, as segments: {@code out[k*7..]} = x0, y0, z0, x1, y1, z1, radius; each
	 * from one side of the hollow to the other, through it.
	 */
	static int beams(CavernModel.Info c, float[] out) {
		int count = Math.min(c.geodeBeams, out.length / 7);
		for (int k = 0; k < count; k++) {
			long b = PlateMap.mix(c.geodeSeed, k, 13);
			double a1 = Mth.TWO_PI * PlateMap.unit(b);
			double e1 = (PlateMap.unit(b >>> 11) - 0.5) * 1.6;
			double a2 = a1 + Math.PI * (0.65 + 0.7 * PlateMap.unit(b >>> 23));
			double e2 = (PlateMap.unit(b >>> 35) - 0.5) * 1.6;
			out[k * 7] = (float) (c.geodeX + Math.cos(a1) * Math.cos(e1) * c.geodeA * 0.95);
			out[k * 7 + 1] = (float) (c.geodeY + Math.sin(e1) * c.geodeC * 0.95);
			out[k * 7 + 2] = (float) (c.geodeZ + Math.sin(a1) * Math.cos(e1) * c.geodeA * 0.95);
			out[k * 7 + 3] = (float) (c.geodeX + Math.cos(a2) * Math.cos(e2) * c.geodeA * 0.95);
			out[k * 7 + 4] = (float) (c.geodeY + Math.sin(e2) * c.geodeC * 0.95);
			out[k * 7 + 5] = (float) (c.geodeZ + Math.sin(a2) * Math.cos(e2) * c.geodeA * 0.95);
			out[k * 7 + 6] = 1.1F + 1.4F * (float) PlateMap.unit(b >>> 47);
		}
		return count;
	}

	private static float sq(double v) {
		return (float) (v * v);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
