package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.Expanse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The Earth-like terrain of one world seed: tectonic plates ({@link PlateMap}), each eroded into ranges,
 * valleys and river networks ({@link PlateTerrain}), sampled one column at a time.
 *
 * <p>A column's height comes from its plate's eroded node grid, smoothly interpolated, with finer detail
 * on top: rough on mountains, calm on valley floors. Near a plate border the two plates' landscapes are
 * blended, mirrored across the border, so they meet without a seam. Near a river the ground is shaped
 * into a valley floor a block above the water, and the river's channel and water are cut and poured by
 * the chunk generator. From the same column come the climate values the biome source reads: inland or
 * coastal (distance from the sea), mountainous or flat (uplift), valley or peak (height above the
 * nearest river), and how much colder the air is with height.
 */
public final class TerrainModel {
	public static final Identifier SEED_ID = Expanse.id("terrain");
	private static final Map<Long, TerrainModel> MODELS = new ConcurrentHashMap<>();
	private static final int MAX_PLATES = 40;
	private static final double BLEND = 170.0;
	public static final java.util.concurrent.atomic.AtomicInteger BUILDS = new java.util.concurrent.atomic.AtomicInteger();

	static final int N_COAST = 0;
	static final int N_COAST_FINE = 1;
	static final int N_UPLIFT = 2;
	static final int N_RIDGE = 3;
	static final int N_ROUGH = 4;
	static final int N_ISLAND = 5;
	static final int N_DETAIL_A = 6;
	static final int N_DETAIL_B = 7;
	static final int N_DETAIL_C = 8;
	static final int N_MEANDER_X = 9;
	static final int N_MEANDER_Z = 10;
	static final int N_SIGN = 11;
	static final int N_EROSION = 12;
	static final int N_BED = 13;
	static final int N_LANDFORM = 14;
	static final int N_TEPUI = 15;
	static final int N_PAN = 16;
	private static final int NOISES = 17;

	private final long seed;
	private final PlateMap plates;
	private final SimplexNoise[] noises = new SimplexNoise[NOISES];
	private final ConcurrentHashMap<Long, Entry> terrains = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
	private final CavernModel caverns;
	/** Vanilla's climate noises, made from the same parameters (so the biomes come out in the same proportions). */
	private final Noise temperature;
	private final Noise vegetation;
	private final Noise offset;

	private static final class Entry {
		final CompletableFuture<PlateTerrain> terrain = new CompletableFuture<>();
		volatile long used;

		Entry(long used) {
			this.used = used;
		}
	}

	/** Per-thread working memory and a direct-mapped cache of recent columns. */
	private static final class Scratch {
		static final int SIZE = 4096;
		final long[] keys = new long[SIZE];
		final Column[] columns = new Column[SIZE];
		final PlateMap.Hit hit = new PlateMap.Hit();
		final PlateTerrain.Raw a = new PlateTerrain.Raw();
		final PlateTerrain.Raw b = new PlateTerrain.Raw();
		final PlateTerrain.Raw c = new PlateTerrain.Raw();
		final PlateMap.Hit hitC = new PlateMap.Hit();
	}

	private TerrainModel(long seed) {
		this.seed = seed;
		this.plates = new PlateMap(seed);
		RandomSource random = RandomSource.create(seed ^ 0x5445_5252_4149_4EL);
		for (int i = 0; i < NOISES; i++) {
			this.noises[i] = new SimplexNoise(random);
		}
		this.caverns = new CavernModel(this, seed);
		var climate = WorldgenRandom.Algorithm.XOROSHIRO.newInstance(seed ^ 0x434C_494D_4154_45L).forkPositional();
		this.temperature = NormalNoise.builder().setBaseAmplitude(1.2453007926713473).setBaseOctave(-10).setOctaveCount(6)
			.setAmplitudeModifier(0, 1.5).setAmplitudeModifier(1, 0).setAmplitudeModifier(2, 1).setAmplitudeModifier(3, 0)
			.setAmplitudeModifier(4, 0).setAmplitudeModifier(5, 0).build().create(climate.fromHashOf("temperature"));
		this.vegetation = NormalNoise.builder().setBaseAmplitude(0.9494731054427978).setBaseOctave(-8).setOctaveCount(6)
			.setAmplitudeModifier(2, 0).setAmplitudeModifier(3, 0).setAmplitudeModifier(4, 0).setAmplitudeModifier(5, 0)
			.build().create(climate.fromHashOf("vegetation"));
		this.offset = NormalNoise.builder().setBaseAmplitude(0.9381732587751005).setBaseOctave(-3).setOctaveCount(4)
			.setAmplitudeModifier(3, 0).build().create(climate.fromHashOf("offset"));
	}

	/** The halls and underground rivers beneath this terrain. */
	public CavernModel caverns() {
		return this.caverns;
	}

	public static TerrainModel forSeed(long seed) {
		return MODELS.computeIfAbsent(seed, TerrainModel::new);
	}

	/** The model seed for a world seed: what {@code createRandom(SEED_ID)} gives a density function. */
	public static long seedFor(long worldSeed) {
		return WorldgenRandom.Algorithm.XOROSHIRO.newInstance(worldSeed).forkPositional().fromHashOf(SEED_ID).nextLong();
	}

	PlateMap plates() {
		return this.plates;
	}

	/** Simplex noise number {@code which}, kept to [-1, 1]. */
	double noise(int which, double x, double z) {
		return Mth.clamp(this.noises[which].get(x, z), -1.0F, 1.0F);
	}

	PlateTerrain terrain(PlateMap.Plate plate) {
		Entry entry = this.terrains.get(plate.key());
		if (entry == null) {
			// stamped as used now: a fresh entry must not look like the oldest one to the eviction below
			Entry mine = new Entry(this.clock.incrementAndGet());
			entry = this.terrains.putIfAbsent(plate.key(), mine);
			if (entry == null) {
				entry = mine;
				try {
					BUILDS.incrementAndGet();
					mine.terrain.complete(new PlateTerrain(this, plate));
				} catch (Throwable t) {
					mine.terrain.completeExceptionally(t);
					this.terrains.remove(plate.key(), mine);
					throw t;
				}
				this.evict();
			}
		}
		entry.used = this.clock.incrementAndGet();
		return entry.terrain.join();
	}

	/** Drops the least recently used plates once there are too many: each holds a few megabytes. */
	private void evict() {
		while (this.terrains.size() > MAX_PLATES) {
			Map.Entry<Long, Entry> oldest = null;
			for (Map.Entry<Long, Entry> e : this.terrains.entrySet()) {
				if (e.getValue().terrain.isDone() && (oldest == null || e.getValue().used < oldest.getValue().used)) {
					oldest = e;
				}
			}
			if (oldest == null) {
				return;
			}
			this.terrains.remove(oldest.getKey(), oldest.getValue());
		}
	}

	/** The column at (x, z), from a small per-thread cache. Read it before sampling another. */
	public Column sample(int x, int z) {
		Scratch s = this.scratch.get();
		long key = (long) x << 32 ^ z & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(0, x, z) & Scratch.SIZE - 1);
		Column column = s.columns[slot];
		if (column != null && s.keys[slot] == key) {
			return column;
		}
		if (column == null) {
			column = s.columns[slot] = new Column();
		}
		this.compute(x, z, column, s);
		s.keys[slot] = key;
		return column;
	}

	private void compute(int x, int z, Column out, Scratch s) {
		PlateMap.Hit hit = s.hit;
		this.plates.locate(x, z, hit);
		PlateMap.Plate own = hit.own;
		PlateTerrain.Raw a = s.a;
		// Rivers wander either side of their course: long swings and short wiggles.
		double mx = x + 9 * this.noise(N_MEANDER_X, x / 110.0, z / 110.0) + 3 * this.noise(N_MEANDER_Z, x / 37.0 + 5, z / 37.0);
		double mz = z + 9 * this.noise(N_MEANDER_Z, x / 110.0, z / 110.0) + 3 * this.noise(N_MEANDER_X, x / 37.0 - 5, z / 37.0);
		this.terrain(own).sample(x, z, mx, mz, true, a);
		float height = a.height;
		float uplift = a.uplift;
		float coast = a.coast;
		float base = a.drainBase;
		if (hit.across != null && hit.border < BLEND) {
			// Mirror the point across the border into the neighbouring plate and blend toward it, so the two
			// meet at the border at the same height from either side.
			PlateTerrain.Raw b = s.b;
			double rx = x + 2 * hit.border * hit.normalX;
			double rz = z + 2 * hit.border * hit.normalZ;
			this.terrain(hit.across).sample(rx, rz, rx, rz, false, b);
			float t = 0.5F * (1 - Mth.smoothstep((float) (hit.border / BLEND)));
			height = Mth.lerp(t, height, b.height);
			uplift = Mth.lerp(t, uplift, b.uplift);
			coast = Mth.lerp(t, coast, b.coast);
			base = Mth.lerp(t, base, b.drainBase);
		}

		// A river is not a canal: it narrows and broadens along its course.
		boolean river = !Float.isNaN(a.riverWater);
		if (river) {
			a.riverHalfWidth *= (float) (0.8 + 0.35 * this.noise(N_BED, x / 160.0 + 40, z / 160.0));
		}
		// Detail: rugged on mountains, calm on valley floors and the sea bed.
		float valley = river ? Mth.clamp((a.riverDist - a.riverHalfWidth) / (12 + a.riverHalfWidth), 0, 1) : 1;
		float amp = 1.2F + 0.045F * Math.max(0, height - 70) + 2.6F * Mth.smoothstep(Mth.clamp((uplift - 0.6F) / 1.4F, 0, 1));
		amp *= 0.25F + 0.75F * Mth.smoothstep(valley);
		if (height < PlateTerrain.SEA - 2) {
			amp = 1.6F;
		}
		float detail = (float) (0.6 * this.noise(N_DETAIL_A, x / 71.0, z / 71.0) + 0.3 * this.noise(N_DETAIL_B, x / 29.0, z / 29.0)
			+ 0.12 * this.noise(N_DETAIL_C, x / 11.0, z / 11.0));
		height += amp * detail;

		// The climate, which the landforms below ask after: vanilla's temperature and humidity noises, sampled
		// as vanilla samples them (shifted by the offset noise) at a fifth of the block scale rather than a
		// quarter, for climate zones a little larger than vanilla's. The lapse is added once the height is known.
		double sx = this.offset.get(x * 0.25, 0, z * 0.25) * 4;
		double sz = this.offset.get(z * 0.25, x * 0.25, 0) * 4;
		float temperature = this.temperature.get(x * 0.2 + sx, 0, z * 0.2 + sz);
		float humidity = this.vegetation.get(x * 0.2 + sx, 0, z * 0.2 + sz);
		out.landform = Landform.NONE;
		boolean land = height > PlateTerrain.SEA + 3;

		// Canyon country: dry uplands, terraced into mesas and buttes, their rivers sunk in sheer canyons.
		float arid = smooth((-0.1F - humidity) / 0.25F) * smooth((temperature - 0.2F) / 0.25F);
		float canyon = land ? arid * smooth((uplift - 0.22F) / 0.35F)
			* smooth((float) (this.noise(N_LANDFORM, x / 2600.0, z / 2600.0) + 0.4) / 0.35F) : 0;
		if (canyon > 0) {
			float step = 9 + 3 * (float) this.noise(N_LANDFORM, x / 900.0 + 31, z / 900.0);
			float v = (height - PlateTerrain.SEA) / step;
			float k = Mth.floor(v);
			float terraced = PlateTerrain.SEA + step * (k + smooth((v - k - 0.72F) / 0.28F));
			height = Mth.lerp(canyon, height, terraced);
			if (canyon > 0.45F) {
				out.landform = Landform.CANYON;
			}
		}

		// Salt flats: in arid lowlands, basins of dead-flat white crust.
		float pan = land && height < 120 ? smooth((-0.15F - humidity) / 0.2F) * smooth((temperature - 0.1F) / 0.25F)
			* smooth((0.5F - uplift) / 0.25F) * smooth((float) (this.noise(N_PAN, x / 1500.0, z / 1500.0) + 0.05) / 0.18F) : 0;
		if (pan > 0) {
			float level = Math.max(PlateTerrain.SEA + 2, base + 2);
			height = Mth.lerp(pan, height, level + 0.4F * (float) this.noise(N_DETAIL_C, x / 40.0, z / 40.0));
			if (pan > 0.6F) {
				out.landform = Landform.SALT_FLAT;
			}
		}

		// Tepuis: in the wet tropics, sheer-sided table mountains standing out of the forest.
		float tropical = smooth((humidity - 0.0F) / 0.2F) * smooth((temperature - 0.3F) / 0.2F);
		if (land && tropical > 0.4F) {
			float tepui = this.tepui(x, z, s, tropical);
			if (tepui > 0) {
				height = Math.max(height, Mth.lerp(tepui, height, s.c.height));
				if (tepui > 0.5F) {
					out.landform = Landform.TEPUI;
				}
			}
		}

		// Volcanoes: the eroded massif the uplift made, crowned with a stratovolcano's cone, its flanks scored
		// by radial gullies, a crater at the top holding lava or a lake. Rivers still cut their gorges into it.
		out.crater = -999;
		out.craterLava = false;
		PlateTerrain plate = this.terrain(own);
		java.util.List<PlateTerrain.Volcano> volcanoes = plate.volcanoes();
		for (int v = 0; v < volcanoes.size(); v++) {
			PlateTerrain.Volcano volcano = volcanoes.get(v);
			double dx = x - volcano.x();
			double dz = z - volcano.z();
			double d = Math.sqrt(dx * dx + dz * dz);
			double r = volcano.radius();
			if (d >= r) {
				continue;
			}
			float apex = plate.summit(v) + 40 + 35 * (float) volcano.strength();
			float foot = PlateTerrain.SEA + 6;
			double t = 1 - d / r;
			double angle = Math.atan2(dz, dx);
			double gully = 1 - Math.abs(this.noise(N_DETAIL_B, angle * r / 55.0, d / 140.0));
			float cone = (float) (foot + (apex - foot) * Math.pow(t, 1.7) - (3 + 0.05 * (apex - foot) * t) * gully * gully);
			height = Math.max(height, cone);
			if (d < r * 0.62 && height > PlateTerrain.SEA + 4) {
				out.landform = Landform.VOLCANIC;
			}
			double cr = volcano.crater() * (1 + 0.1 * this.noise(N_BED, x / 23.0, z / 23.0));
			if (d < cr && (!river || a.riverDist > cr + 24)) {
				float rim = (float) (foot + (apex - foot) * Math.pow(1 - cr / r, 1.7));
				float floor = rim - (float) (0.55 * volcano.crater());
				height = Math.min(height, floor + (rim - floor) * (float) Math.pow(d / cr, 2.4));
				out.crater = floor + 0.4F * (rim - floor);
				out.craterLava = volcano.active();
			}
		}

		// The river valley: a floor a block above the water, rising smoothly into the land round it.
		if (river) {
			float water = a.riverWater;
			float floor = water + 1;
			float hw = a.riverHalfWidth;
			// in canyon country the valley is a canyon: the walls stand straight up from the banks
			float plain = Mth.lerp(canyon, hw + 3 + hw * 0.8F + 10, hw + 2.5F);
			if (a.riverDist < hw) {
				height = floor;
			} else if (a.riverDist < plain) {
				float t = Mth.smoothstep((a.riverDist - hw) / (plain - hw));
				height = Math.max(floor, Mth.lerp(t, floor, height));
			}
		}

		// Fjords: on cold mountain coasts the ice ground the river valleys into U-shaped troughs, deepest at
		// the coast, where the sea has come in: sheer walls round a long arm of the sea.
		out.fjord = false;
		float glacial = smooth((-0.05F - temperature) / 0.3F) * smooth((1500 - coast) / 600) * smooth((uplift - 0.35F) / 0.4F);
		if (river && glacial > 0) {
			float hw = a.riverHalfWidth;
			float wide = Math.min(58, 20 + 1.4F * hw + 26 * glacial);
			if (a.riverDist < wide) {
				float trough = Mth.lerp(glacial * smooth((1300 - coast) / 1100), a.riverWater + 1, PlateTerrain.SEA - 22);
				float t = smooth((a.riverDist - 0.5F * wide) / (0.5F * wide));
				height = Math.min(height, trough + (height - trough) * t * t);
				if (trough < PlateTerrain.SEA - 1 && a.riverDist < wide * 0.55F) {
					out.fjord = true;  // the sea's, not the river's: no river poured here
				}
			}
		}
		if (glacial > 0.75F && coast < 650 && out.landform == Landform.NONE && height > PlateTerrain.SEA + 2) {
			out.landform = Landform.FJORD;  // the whole cold mountain coast the fjords cut into
		}
		out.height = height;
		out.riverDist = a.riverDist;
		out.riverHalfWidth = a.riverHalfWidth;
		out.riverDepth = a.riverDepth;
		out.riverWater = a.riverWater;

		// Climate for the biome source.
		out.coast = coast;
		out.uplift = uplift;
		out.aboveRiver = height - base;
		out.continentalness = continentalness(coast);
		// Erosion, as the biome source reads it: low on mountains, high on plains. Lowlands sit mostly in
		// vanilla's plains-and-forest band, reaching its swamp band only here and there.
		out.erosion = (float) Mth.clamp(0.42 - 1.25 * Mth.smoothstep(Mth.clamp((uplift - 0.12F) / 1.5F, 0, 1))
			+ 0.22 * this.noise(N_EROSION, x / 800.0, z / 800.0), -1, 1);
		// Peaks and valleys: from height above the nearest river, lowland on the floodplain, peak on the
		// ridge crests; the river biome itself only along the channel.
		float pv = Mth.clamp(-0.7F + out.aboveRiver / 55.0F, -0.7F, 1);
		if (river && a.riverDist < a.riverHalfWidth + 3) {
			pv = -1;
		}
		float sign = this.noise(N_SIGN, x / 1100.0, z / 1100.0) >= 0 ? 1 : -1;
		out.weirdness = out.landform != Landform.NONE ? Landform.weirdness(out.landform) : sign * (pv + 1) / 3;
		out.lapse = -0.8F * Mth.clamp((height - 110) / 260.0F, 0, 1);
		out.temperature = temperature + out.lapse;
		out.humidity = humidity;
		out.plateX = own.gx();
		out.plateZ = own.gz();
		out.continental = own.continental();
	}

	/**
	 * How far (x, z) is up onto a tepui (0 off it, 1 on its top), with the top's height left in
	 * {@code s.c.height}. Tepuis stand one at most in each 900-block cell where the tropics are wet: a ragged
	 * outline a few hundred blocks across, cliffs a handful of blocks wide, a top 70 to 120 blocks over the
	 * land at its foot.
	 */
	private float tepui(int x, int z, Scratch s, float tropical) {
		int cell = 900;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float best = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x54455055L, cx + dx, cz + dz);
				if (PlateMap.unit(h) > 0.6) {
					continue;
				}
				double px = (cx + dx + 0.25 + 0.5 * PlateMap.unit(h >>> 21)) * cell;
				double pz = (cz + dz + 0.25 + 0.5 * PlateMap.unit(h >>> 42)) * cell;
				double r = 150 + 150 * PlateMap.unit(h >>> 7);
				double d = Math.hypot(x - px, z - pz) + 0.32 * r * this.noise(N_TEPUI, x / 160.0, z / 160.0)
					+ 0.1 * r * this.noise(N_TEPUI, x / 41.0 + 9, z / 41.0);
				float edge = (float) (r - d);
				if (edge <= -2) {
					continue;
				}
				// full height wherever it stands; a little smaller where the tropics are drier
				float t = smooth((edge + 2 - (float) r * 0.35F * (1 - smooth((tropical - 0.4F) / 0.4F))) / 7);
				if (t > best) {
					best = t;
					this.plates.locate(px, pz, s.hitC);
					this.terrain(s.hitC.own).sample(px, pz, px, pz, false, s.c);
					float rise = 70 + 50 * (float) PlateMap.unit(h >>> 29);
					s.c.height = s.c.height + rise + 3 * (float) this.noise(N_DETAIL_A, x / 37.0, z / 37.0);
				}
			}
		}
		return best;
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** Distance from the coast (blocks, positive inland) to the biome source's continentalness. */
	static float continentalness(float coast) {
		if (coast >= 1800) {
			return 0.55F;
		}
		if (coast >= 600) {
			return Mth.lerp((coast - 600) / 1200, 0.18F, 0.55F);
		}
		if (coast >= 150) {
			return Mth.lerp((coast - 150) / 450, -0.05F, 0.18F);
		}
		if (coast >= 0) {
			return Mth.lerp(coast / 150, -0.18F, -0.05F);
		}
		if (coast >= -400) {
			return Mth.lerp(-coast / 400, -0.19F, -0.40F);
		}
		if (coast >= -1500) {
			return Mth.lerp((-coast - 400) / 1100, -0.40F, -0.85F);
		}
		return -0.9F;
	}

	/** Whether the river's first ring of bank at (x, z) lies level with the water: about half of it, in stretches. */
	public boolean levelShore(int x, int z) {
		return this.noise(N_BED, x / 45.0 + 300, z / 45.0) > -0.1;
	}

	/** A riverbed block for (x, z): gravel mostly, with sand bars and clay in patches. */
	public int bedKind(int x, int z) {
		double n = this.noise(N_BED, x / 9.0, z / 9.0);
		return n > 0.35 ? 1 : n < -0.5 ? 2 : 0;
	}
}
