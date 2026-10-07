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
 * on top: rough on mountains, calm on valley floors. Near a plate border the plates' landscapes are
 * blended, mirrored across the border, so they meet without a seam (where three plates meet, with each
 * neighbour by how near its border is). Near a river the ground is shaped
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
	static final int N_LAKE = 17;
	private static final int NOISES = 18;
	/** One lake at most in each cell this wide. */
	private static final int LAKE_CELL = 560;
	/** One great lake at most in each cell this wide. */
	private static final int GREAT_CELL = 1400;

	private final long seed;
	private final PlateMap plates;
	private final SimplexNoise[] noises = new SimplexNoise[NOISES];
	private final ConcurrentHashMap<Long, Entry> terrains = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
	/** Great lakes by cell, once found (a few hundred bytes each; the cells are 1.4 km across). */
	private final ConcurrentHashMap<Long, GreatLake> greatLakes = new ConcurrentHashMap<>();
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
		final PlateTerrain.Raw e = new PlateTerrain.Raw();
		/** (seams) A neighbouring plate's river here, and how much each neighbour counts (1 on the border). */
		final PlateTerrain.Raw n = new PlateTerrain.Raw();
		final float[] nearWeight = new float[24];
		/** (tepuis) Per 900-block cell: its key, and its tepui's wetness and the height of the ground at its centre. */
		final long[] tepuiKey = new long[64];
		final float[] tepuiTropical = new float[64];
		final float[] tepuiGround = new float[64];
		final PlateMap.Hit hitC = new PlateMap.Hit();
		/** (lakes) Per lake cell: its key and its lake; the sampler for its site; how far inside the shore. */
		final long[] lakeKey = new long[64];
		final Lake[] lakes = new Lake[64];
		final PlateTerrain.Raw l = new PlateTerrain.Raw();
		final PlateMap.Hit hitL = new PlateMap.Hit();
		float lakeEdge;
		/** (great lakes) Per great-lake cell: its key and its lake. */
		final long[] greatKey = new long[32];
		final GreatLake[] greats = new GreatLake[32];

		Scratch() {
			java.util.Arrays.fill(this.tepuiKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.lakeKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.greatKey, Long.MIN_VALUE);
		}

		/** (tepuis) How high the talus stands over the ground here, at the foot of the nearest cliff. */
		float apron;
	}

	/**
	 * A lake: its centre and radius, the y of its top water block, the depth of its bowl, whether it has
	 * islands, and a seed for its shape.
	 */
	/**
	 * A great lake: a lowland river valley flooded behind a moraine. Its centre on the river, the radius it may
	 * reach, the y of its top water block, and the bearing (radians) and half width of the arc where the valley
	 * leaves it, across which the moraine stands.
	 */
	record GreatLake(boolean exists, float x, float z, float r, float level, float outlet, float arc) {
		static final GreatLake NONE = new GreatLake(false, 0, 0, 0, 0, 0, 0);
	}

	record Lake(boolean exists, float x, float z, float r, float level, float depth, boolean islands, long seed) {
		static final Lake NONE = new Lake(false, 0, 0, 0, 0, 0, false, 0);
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
		// Near a border, mirror the point across it into the neighbouring plate and blend toward that, so the
		// two meet at the border at the same height from either side. Every neighbour within reach takes part,
		// each by how near its border is (half and half on a border, as before between two plates): where three
		// plates meet, blending toward only the nearest would jump from one to the other along a line.
		float weight = 1;
		float sumHeight = height;
		float sumUplift = uplift;
		float sumCoast = coast;
		float sumBase = base;
		for (int n = 0; n < hit.nearCount; n++) {
			double border = hit.nearBorder[n];
			s.nearWeight[n] = 0;
			if (border >= BLEND) {
				continue;
			}
			PlateTerrain.Raw b = s.b;
			double rx = x + 2 * border * hit.nearNormalX[n];
			double rz = z + 2 * border * hit.nearNormalZ[n];
			this.terrain(hit.near[n]).sample(rx, rz, rx, rz, false, b);
			float t = 0.5F * (1 - Mth.smoothstep((float) (Math.max(0, border) / BLEND)));
			s.nearWeight[n] = 2 * t;
			float q = t / (1 - t);
			weight += q;
			sumHeight += q * b.height;
			sumUplift += q * b.uplift;
			sumCoast += q * b.coast;
			sumBase += q * b.drainBase;
		}
		height = sumHeight / weight;
		uplift = sumUplift / weight;
		coast = sumCoast / weight;
		base = sumBase / weight;

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

		// The Bohemian heartland: temperate country well inland and away from the ranges rolls rather than climbs.
		// Its hills stand lower over their valleys (by up to nearly half), so the land opens out into long gentle
		// slopes and broad vales, while the mountains and the coasts keep their relief.
		if (land) {
			float heart = smooth((temperature + 0.4F) / 0.3F) * smooth((0.6F - temperature) / 0.3F) * smooth((humidity + 0.25F) / 0.3F)
				* smooth((coast - 400) / 700) * smooth((0.95F - uplift) / 0.5F);
			if (heart > 0 && height > base) {
				height = base + (height - base) * (1 - 0.45F * heart);
			}
		}

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

		// Tepuis: in the wet tropics, sheer-sided table mountains standing out of the forest, the lower part of
		// every cliff buried in the talus that has fallen from it.
		if (land) {
			float ground = height;
			float tepui = this.tepui(x, z, s, ground);
			if (s.apron > 0) {
				height = Math.max(height, ground + s.apron);
			}
			if (tepui > 0) {
				height = Math.max(height, Mth.lerp(tepui, ground, s.c.height));
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
				// One level for the whole crater, from its unruffled rim: the noise shapes the bowl, not the lake,
				// which would otherwise stand at a different height in every column and run down itself.
				out.crater = (float) (foot + (apex - foot) * Math.pow(1 - volcano.crater() / r, 1.7) - 0.33 * volcano.crater());
				out.craterLava = volcano.active();
			}
		}

		// Lakes: quiet hollows of open country holding still water, a bowl shelving from the shore, islands in
		// the bigger ones; small deep tarns high up. The land round each comes down to a block above the water,
		// and holds it in.
		out.lake = -999;
		out.lakeEdge = -999;
		float dry = height;
		if (land) {
			Lake lake = this.lakeNear(x, z, s);
			float shore = lake == null ? 0 : 5 + 0.12F * lake.r();
			if (lake != null && s.lakeEdge > -shore) {
				float edge = s.lakeEdge;
				float level = lake.level();
				if (edge > 0) {
					float t = smooth(edge / Math.max(6, 0.4F * lake.r()));
					float bed = level - 0.6F - lake.depth() * t + 1.2F * t * (float) this.noise(N_LAKE, x / 17.0, z / 17.0);
					height = Math.min(height, bed);
					if (lake.islands() && edge > 8) {
						// islands, each shoaling into the water round it
						float isl = (float) this.noise(N_LAKE, x / 38.0 + (lake.seed() & 1023), z / 38.0 - 300);
						if (isl > 0.45F) {
							height = Math.max(height, level - 2 + 9 * (isl - 0.45F) / 0.35F);
						}
					}
				} else {
					float t = smooth(-edge / shore);
					height = Math.max(level + 1, Mth.lerp(t, level + 1.2F, height));
				}
				out.lake = level;
				out.lakeEdge = edge;
			}
		}

		// The river valley: a floor a block above the water, rising smoothly into the land round it. The
		// deeper the river lies below the land, the wider its valley sides spread, so they climb at a slope a
		// walker can take rather than standing as walls over the floor.
		if (river) {
			height = valley(height, a.riverDist, a.riverHalfWidth, a.riverWater, canyon);
		}
		// A river of the plate next door runs near the border: its valley comes across too, fully on the border
		// and fading out over the blend, and only ever down into the land. Otherwise the valley's side stops dead
		// where the plates meet, and stands there as a wall over the valley floor.
		if (!river || a.riverDist > a.riverHalfWidth + 3) {
			for (int n = 0; n < hit.nearCount; n++) {
				float w = s.nearWeight[n];
				if (w < 0.02F) {
					continue;
				}
				PlateTerrain.Raw nr = s.n;
				this.terrain(hit.near[n]).sample(x, z, mx, mz, true, nr);
				if (Float.isNaN(nr.riverWater) || nr.riverDist > 3 * nr.riverHalfWidth + 60) {
					continue;
				}
				float hw = nr.riverHalfWidth * (float) (0.8 + 0.35 * this.noise(N_BED, x / 160.0 + 40, z / 160.0));
				float carved = valley(height, Math.max(nr.riverDist, hw + 1), hw, nr.riverWater, canyon);
				height = Math.min(height, Mth.lerp(w, height, carved));
			}
		}

		// Great lakes: here and there a lowland river valley lies flooded behind a moraine the ice left across it.
		// The water stands over everything below its level, so the shore follows the lie of the land, out along
		// the river's side valleys; the river comes in at the head and spills out through a notch in the moraine.
		if (land && out.lake < -900) {
			GreatLake g = this.greatLakeNear(x, z, s);
			if (g != null) {
				float before = height;
				height = this.flood(g, x, z, height, river && a.riverDist < a.riverHalfWidth + 1.5F, out);
				if (out.lake > -900) {
					dry = before;
				}
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
		// (the climate reads the land as it lay before a lake filled its hollow, so a lake takes its biome
		// from the country round it)
		out.aboveRiver = (out.lake > -900 ? Math.max(height, dry) : height) - base;
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
	 * Ground shaped by a river valley: the channel's floor a block above the water, rising smoothly into the land
	 * round it. The deeper the river lies below the land, the wider its valley sides spread, so they climb at a
	 * slope a walker can take; in canyon country the walls stand straight up from the banks.
	 */
	private static float valley(float height, float dist, float hw, float water, float canyon) {
		float floor = water + 1;
		float depth = Math.max(0, height - floor);
		// big rivers lie in broad flat floodplains, half again as wide as the river either side
		float plain = Mth.lerp(canyon, hw + 13 + hw * 1.3F + Math.min(40, depth), hw + 2.5F);
		if (dist < hw) {
			return floor;
		}
		if (dist < plain) {
			float t = Mth.smoothstep((dist - hw) / (plain - hw));
			return Math.max(floor, Mth.lerp(t, floor, height));
		}
		return height;
	}

	/** The great lake whose water, shore or moraine may reach (x, z); null if none. */
	private GreatLake greatLakeNear(int x, int z, Scratch s) {
		int gx = Math.floorDiv(x, GREAT_CELL);
		int gz = Math.floorDiv(z, GREAT_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				GreatLake g = this.greatLake(gx + dx, gz + dz, s);
				if (g.exists() && Math.hypot(x - g.x(), z - g.z()) < g.r() * 1.15 + 36) {
					return g;
				}
			}
		}
		return null;
	}

	/**
	 * The ground at (x, z) as a great lake leaves it, marking the column's water or shore in {@code out}. Toward the
	 * rim (whose reach wavers with the bearing, so no edge of it is ever an arc) only ever deeper ground is drowned,
	 * so the shores close in on the valley floor; the shallower ground there is built up into a broad moraine plug
	 * across the valley rather than a dam along a line, with a notch where the river runs out through it.
	 */
	private float flood(GreatLake g, int x, int z, float height, boolean channel, Column out) {
		double dx = x - g.x();
		double dz = z - g.z();
		float d = (float) Math.sqrt(dx * dx + dz * dz);
		double bearing = Math.atan2(dz, dx);
		float r = g.r() * (1 + 0.14F * (float) this.noise(N_LAKE, Math.cos(bearing) * 1.7 + g.x() / 97.0, Math.sin(bearing) * 1.7 + g.z() / 97.0));
		float level = g.level();
		float rim = smooth((d - (r - 90)) / 86);        // 0 well inside, 1 at the rim
		float drown = level + 0.5F - 7 * rim;
		if (d < r - 4 && height < drown) {
			// the water: the drowned valley floor, a little deeper where it lay lowest
			float deep = level - height;
			out.lake = level;
			out.lakeEdge = 1 + 3 * Math.max(0, deep + 0.5F);
			return Math.min(height, level - 0.6F - 0.3F * Math.max(0, deep));
		}
		float bank = level + 1 + 1.5F * rim;
		if (d < r - 4 && height < level + 2.5F + 1.5F * rim) {
			// the shore, and toward the rim the moraine, a block or three over the water
			out.lake = level;
			out.lakeEdge = -Math.max(0.5F, height - level - 0.5F) * 1.5F;
			return channel ? height : Math.max(height, bank);
		}
		// beyond the rim the moraine's outer face falls away into the valley below
		if (d >= r - 4 && d < r + 34 && !channel && height < bank) {
			float face = Mth.lerp(smooth((d - r + 4) / 38), bank, height);
			height = Math.max(height, face);
		}
		return height;
	}

	private GreatLake greatLake(int gx, int gz, Scratch s) {
		long key = (long) gx << 32 ^ gz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(3, gx, gz) & 31);
		if (s.greatKey[slot] != key) {
			GreatLake g = this.siteOf(gx, gz, s);
			// two lakes never overlap (their water would stand at two levels): the higher-ranked cell keeps its own
			if (g.exists()) {
				long rank = PlateMap.mix(this.seed ^ 0x52414E4BL, gx, gz);
				for (int dx = -1; dx <= 1 && g.exists(); dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dz == 0) {
							continue;
						}
						GreatLake o = this.siteOf(gx + dx, gz + dz, s);
						if (o.exists() && PlateMap.mix(this.seed ^ 0x52414E4BL, gx + dx, gz + dz) > rank
							&& Math.hypot(o.x() - g.x(), o.z() - g.z()) < (o.r() + g.r()) * 1.15 + 60) {
							g = GreatLake.NONE;
							break;
						}
					}
				}
			}
			s.greats[slot] = g;
			s.greatKey[slot] = key;
		}
		return s.greats[slot];
	}

	/**
	 * A cell's great lake site, whether or not a neighbour's overrules it. Finding one samples the plate a few
	 * thousand times, so it is done once per cell for every thread.
	 */
	private GreatLake siteOf(int gx, int gz, Scratch s) {
		long key = (long) gx << 32 ^ gz & 0xFFFF_FFFFL;
		GreatLake g = this.greatLakes.get(key);
		if (g == null) {
			g = this.makeGreatLake(gx, gz, s);
			GreatLake had = this.greatLakes.putIfAbsent(key, g);
			g = had != null ? had : g;
		}
		return g;
	}

	/**
	 * Three cells in five may have a great lake, on a river of some size in open lowland country: well inland, temperate
	 * or cool and not dry, no volcano or plate border near. The cell is searched on a grid for points on such a
	 * river. The water stands five or six blocks over the river there; the lake's reach grows (up to 700 blocks) until
	 * the valley upstream has risen out of the water, so the rim has one low gap only, the valley going on downstream,
	 * where the moraine stands. Lowland rivers fall so gently that most such lakes are long ones, drowning the valley
	 * for a kilometre and more. A fair share of the land within must lie under the water.
	 */
	private GreatLake makeGreatLake(int gx, int gz, Scratch s) {
		long h = PlateMap.mix(this.seed ^ 0x4752_4541L, gx, gz);
		if (PlateMap.unit(h) > 0.6) {
			return GreatLake.NONE;
		}
		PlateTerrain.Raw raw = s.l;
		int samples = 36;
		float[] low = new float[samples];
		int grid = 10;
		int offset = (int) (PlateMap.unit(h >>> 13) * grid * grid);
		int tried = 0;
		for (int n = 0; n < grid * grid && tried < 6; n++) {
			int c = (n * 37 + offset) % (grid * grid);   // the grid in a scrambled order
			double px = (gx + 0.15 + 0.7 * (c % grid + 0.5) / grid) * GREAT_CELL;
			double pz = (gz + 0.15 + 0.7 * (c / grid + 0.5) / grid) * GREAT_CELL;
			this.plates.locate(px, pz, s.hitL);
			if (s.hitL.border < BLEND + 140) {
				continue;
			}
			PlateTerrain plate = this.terrain(s.hitL.own);
			plate.sample(px, pz, px, pz, true, raw);
			if (Float.isNaN(raw.riverWater) || raw.riverDist > raw.riverHalfWidth + 2 || raw.riverHalfWidth < 3
				|| raw.height > 150 || raw.coast < 700) {
				continue;
			}
			tried++;
			float centre = raw.riverWater;
			double sx = this.offset.get(px * 0.25, 0, pz * 0.25) * 4;
			double sz = this.offset.get(pz * 0.25, px * 0.25, 0) * 4;
			float temperature = (float) this.temperature.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			float humidity = (float) this.vegetation.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			if (humidity < -0.2F || smooth(humidity / 0.2F) * smooth((temperature - 0.3F) / 0.2F) > 0.3F) {
				continue;
			}
			boolean volcano = false;
			for (PlateTerrain.Volcano v : plate.volcanoes()) {
				volcano |= Math.hypot(px - v.x(), pz - v.z()) < v.radius() + 380;
			}
			if (volcano) {
				continue;
			}
			float level = Mth.floor(centre) + 5 + (int) (PlateMap.unit(h >>> (20 + n % 30)) * 2);
			for (float r = 140; r <= 700; r += 40) {
				if (s.hitL.border < BLEND + r) {
					break;
				}
				int lows = 0;
				int arcs = 0;
				int start = -1;
				for (int k = 0; k < samples; k++) {
					double a = k * Mth.TWO_PI / samples;
					plate.sample(px + Math.cos(a) * r, pz + Math.sin(a) * r, 0, 0, false, raw);
					low[k] = raw.height;
				}
				boolean[] under = new boolean[samples];
				for (int k = 0; k < samples; k++) {
					under[k] = low[k] < level + 1;
					lows += under[k] ? 1 : 0;
				}
				for (int k = 0; k < samples; k++) {
					if (under[k] && !under[(k + samples - 1) % samples]) {
						arcs++;
						start = k;
					}
				}
				if (arcs > 1) {
					continue;   // the valley still runs on below the water upstream: reach further
				}
				if (arcs == 0 || lows > 5) {
					break;
				}
				int drowned = 0;
				for (int k = 0; k < 24; k++) {
					double a = k * Mth.TWO_PI / 24;
					for (double f : new double[]{0.3, 0.6}) {
						plate.sample(px + Math.cos(a) * r * f, pz + Math.sin(a) * r * f, 0, 0, false, raw);
						drowned += raw.height < level ? 1 : 0;
					}
				}
				if (drowned < 8) {
					break;
				}
				float step = Mth.TWO_PI / samples;
				float outlet = (start + (lows - 1) * 0.5F) * step;
				return new GreatLake(true, (float) px, (float) pz, r, level, outlet, (lows * 0.5F + 1) * step);
			}
		}
		return GreatLake.NONE;
	}

	/**
	 * The lake whose water or shore reaches (x, z), with how far inside its shoreline (x, z) is left in
	 * {@code s.lakeEdge} (negative outside); null if none. The shoreline wanders: the radius is bent by a
	 * broad noise into deep bays and long points, and ruffled by a finer one.
	 */
	private Lake lakeNear(int x, int z, Scratch s) {
		int lx = Math.floorDiv(x, LAKE_CELL);
		int lz = Math.floorDiv(z, LAKE_CELL);
		Lake best = null;
		float bestEdge = -Float.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Lake lake = this.lake(lx + dx, lz + dz, s);
				if (!lake.exists()) {
					continue;
				}
				double d = Math.hypot(x - lake.x(), z - lake.z());
				if (d > lake.r() * 1.6 + 30) {
					continue;
				}
				double bent = d + 0.42 * lake.r() * this.noise(N_LAKE, x / (lake.r() * 0.9) + 70, z / (lake.r() * 0.9))
					+ 0.09 * lake.r() * this.noise(N_LAKE, x / 23.0 - 40, z / 23.0);
				float edge = (float) (lake.r() - bent);
				if (edge > bestEdge) {
					bestEdge = edge;
					best = lake;
				}
			}
		}
		s.lakeEdge = bestEdge;
		return best;
	}

	private Lake lake(int lx, int lz, Scratch s) {
		long key = (long) lx << 32 ^ lz & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(2, lx, lz) & 63);
		if (s.lakeKey[slot] != key) {
			s.lakes[slot] = this.makeLake(lx, lz, s);
			s.lakeKey[slot] = key;
		}
		return s.lakes[slot];
	}

	/**
	 * Three cells in five have a lake, where one of a few tries finds a quiet hollow of open land: well inland,
	 * no river, volcano or plate border near, not in the dry country (its basins are salt flats) or the wet
	 * tropics (their tepuis), and the ground round it within a dozen blocks of level. The water stands a block
	 * under the lowest point of the rim, so the lake holds itself in. Down in the lowlands a lake is broad,
	 * the biggest with islands; high up it is a small deep tarn.
	 */
	private Lake makeLake(int lx, int lz, Scratch s) {
		long h = PlateMap.mix(this.seed ^ 0x4C41_4B45L, lx, lz);
		if (PlateMap.unit(h) > 0.6) {
			return Lake.NONE;
		}
		PlateTerrain.Raw raw = s.l;
		for (int i = 0; i < 6; i++) {
			long hi = PlateMap.mix(h, i, 11);
			double px = (lx + 0.15 + 0.7 * PlateMap.unit(hi)) * LAKE_CELL;
			double pz = (lz + 0.15 + 0.7 * PlateMap.unit(hi >>> 21)) * LAKE_CELL;
			this.plates.locate(px, pz, s.hitL);
			if (s.hitL.border < BLEND + 120) {
				continue;
			}
			PlateTerrain plate = this.terrain(s.hitL.own);
			plate.sample(px, pz, px, pz, true, raw);
			float ground = raw.height;
			if (ground < PlateTerrain.SEA + 8 || raw.coast < 300) {
				continue;
			}
			double sx = this.offset.get(px * 0.25, 0, pz * 0.25) * 4;
			double sz = this.offset.get(pz * 0.25, px * 0.25, 0) * 4;
			float temperature = (float) this.temperature.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			float humidity = (float) this.vegetation.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			if (humidity < -0.15F || smooth(humidity / 0.2F) * smooth((temperature - 0.3F) / 0.2F) > 0.3F) {
				continue;
			}
			float alpine = smooth((ground - 150) / 60);
			float r = Mth.lerp(alpine, 22 + 48 * (float) PlateMap.unit(hi >>> 9), 10 + 14 * (float) PlateMap.unit(hi >>> 9));
			if (!Float.isNaN(raw.riverWater) && raw.riverDist < r + 48) {
				continue;
			}
			boolean volcano = false;
			for (PlateTerrain.Volcano v : plate.volcanoes()) {
				volcano |= Math.hypot(px - v.x(), pz - v.z()) < v.radius() + r + 40;
			}
			if (volcano) {
				continue;
			}
			// the rim: the lowest and highest ground round it; and further out, where its longest bays can reach,
			// the lowest ground again, which the water may stand at most a couple of blocks over (the shore is
			// built up that much to hold it)
			float lo = ground;
			float top = ground;
			float far = Float.MAX_VALUE;
			for (int k = 0; k < 32; k++) {
				double a = k * Math.PI / 8;
				double reach = k < 16 ? r + 4 : r * 1.5 + 4;
				plate.sample(px + Math.cos(a) * reach, pz + Math.sin(a) * reach, 0, 0, false, raw);
				if (k < 16) {
					lo = Math.min(lo, raw.height);
					top = Math.max(top, raw.height);
				} else {
					far = Math.min(far, raw.height);
				}
			}
			if (top - lo > Mth.lerp(alpine, 10, 18)) {
				continue;
			}
			float level = Mth.floor(Math.min(lo, far + 2)) - 1;
			if (level < PlateTerrain.SEA + 4) {
				continue;
			}
			float depth = Mth.lerp(alpine, 5 + 6 * (float) PlateMap.unit(hi >>> 23), 7 + 8 * (float) PlateMap.unit(hi >>> 23));
			boolean islands = r > 42 && PlateMap.unit(hi >>> 31) < 0.6;
			return new Lake(true, (float) px, (float) pz, r, level, depth, islands, hi);
		}
		return Lake.NONE;
	}

	/**
	 * How far (x, z) is up onto a tepui (0 off it, 1 on its top), with the top's height left in
	 * {@code s.c.height} and the talus at its foot in {@code s.apron}. Tepuis stand one at most in each
	 * 900-block cell whose centre lies in the wet tropics: a ragged outline a few hundred blocks across, cliffs
	 * a handful of blocks wide, a top 70 to 120 blocks over the land at its centre. Each is made whole from the
	 * climate at its centre, so it has one level top, and where that climate is only just wet enough it is a
	 * smaller, lower mesa; the climate of the ground it stands on never cuts one off along a line.
	 *
	 * <p>A cliff that high sheds rock, and what falls piles up against its foot: a talus apron, steepest
	 * where it meets the wall (about the angle loose rock rests at) and spreading out concave into the forest,
	 * its top a fifth to a half of the way up the cliff as it stands there. Along the wall it rises into spurs
	 * where buttresses have fallen and dips into gullies between them, so the forest climbs the apron in ribs.
	 */
	private float tepui(int x, int z, Scratch s, float ground) {
		int cell = 900;
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		float best = 0;
		s.apron = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long h = PlateMap.mix(0x54455055L, cx + dx, cz + dz);
				if (PlateMap.unit(h) > 0.6) {
					continue;
				}
				double px = (cx + dx + 0.25 + 0.5 * PlateMap.unit(h >>> 21)) * cell;
				double pz = (cz + dz + 0.25 + 0.5 * PlateMap.unit(h >>> 42)) * cell;
				double r = 150 + 150 * PlateMap.unit(h >>> 7);
				double centre = Math.hypot(x - px, z - pz);
				if (centre > r * 1.4 + TALUS_REACH + 4) {
					continue;  // beyond its widest outline and talus
				}
				int slot = this.tepuiCell(cx + dx, cz + dz, px, pz, s);
				float tropical = s.tepuiTropical[slot];
				if (tropical <= 0.4F) {
					continue;
				}
				float top = s.tepuiGround[slot] + 70 + 50 * (float) PlateMap.unit(h >>> 29);
				// smaller where the tropics are drier: the cliff's foot moves in
				float inset = (float) r * 0.35F * (1 - smooth((tropical - 0.4F) / 0.4F));
				double d = centre + 0.32 * r * this.noise(N_TEPUI, x / 160.0, z / 160.0) + 0.05 * r * this.noise(N_TEPUI, x / 41.0 + 9, z / 41.0);
				float edge = (float) (r - d);
				float outside = inset - 2 - edge;     // how far out from the foot of the cliff
				if (outside < TALUS_REACH) {
					float cliff = top - ground;      // the cliff as it stands here
					float share = 0.36F + 0.14F * (float) this.noise(N_TEPUI, x / 46.0 + 71, z / 46.0)
						+ 0.03F * (float) this.noise(N_TEPUI, x / 19.0 - 30, z / 19.0);
					float talus = share * cliff;
					float spread = Math.min(TALUS_REACH, talus / 0.5F);
					if (talus > 0 && outside < spread) {
						float u = outside <= 0 ? 1 : 1 - outside / spread;
						s.apron = Math.max(s.apron, talus * u * (float) Math.sqrt(u));
					}
				}
				float t = smooth((edge + 2 - inset) / 7);
				if (t > best) {
					best = t;
					s.c.height = top + 3 * (float) this.noise(N_DETAIL_A, x / 37.0, z / 37.0);
				}
			}
		}
		return best;
	}

	/**
	 * The slot in the per-thread cache holding a tepui cell's centre: how wet its tropics are there (0 to 1;
	 * tepuis need more than 0.4) and the eroded ground's height.
	 */
	private int tepuiCell(int cellX, int cellZ, double px, double pz, Scratch s) {
		long key = (long) cellX << 32 ^ cellZ & 0xFFFF_FFFFL;
		int slot = (int) (PlateMap.mix(1, cellX, cellZ) & 63);
		if (s.tepuiKey[slot] != key) {
			double sx = this.offset.get(px * 0.25, 0, pz * 0.25) * 4;
			double sz = this.offset.get(pz * 0.25, px * 0.25, 0) * 4;
			float temperature = (float) this.temperature.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			float humidity = (float) this.vegetation.get(px * 0.2 + sx, 0, pz * 0.2 + sz);
			s.tepuiTropical[slot] = smooth(humidity / 0.2F) * smooth((temperature - 0.3F) / 0.2F);
			this.plates.locate(px, pz, s.hitC);
			this.terrain(s.hitC.own).sample(px, pz, px, pz, false, s.e);
			s.tepuiGround[slot] = s.e.height;
			s.tepuiKey[slot] = key;
		}
		return slot;
	}

	/** How far the talus at a tepui's foot spreads into the forest at most. */
	private static final float TALUS_REACH = 110;

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
