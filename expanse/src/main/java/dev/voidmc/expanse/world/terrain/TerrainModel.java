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
	static final int N_COUNTRY = 18;
	static final int N_ROCK = 19;
	static final int N_DOME = 20;
	static final int N_WOOD = 21;
	static final int N_ESC = 22;
	private static final int NOISES = 23;
	/** One lake at most in each cell this wide. */
	private static final int LAKE_CELL = 560;
	/** One great lake at most in each cell this wide. */
	private static final int GREAT_CELL = 1400;

	private final long seed;
	private final PlateMap plates;
	private final SimplexNoise[] noises = new SimplexNoise[NOISES];
	private final ConcurrentHashMap<Long, Entry> terrains = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	/** How many built plates are kept: {@link #MAX_PLATES}, or more while a far view asks for it ({@link #reservePlates}). */
	private volatile int maxPlates = Integer.getInteger("expanse.maxPlates", MAX_PLATES);
	private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
	/** Great lakes by cell, once found (a few hundred bytes each; the cells are 1.4 km across). */
	private final ConcurrentHashMap<Long, GreatLake> greatLakes = new ConcurrentHashMap<>();
	/** Lakes by cell, once found (shared, so a far view's thousands of cells are each searched once). */
	private final ConcurrentHashMap<Long, Lake> lakes = new ConcurrentHashMap<>();
	private final CavernModel caverns;
	/** The countries laid over the relief ({@link Countries}). */
	private final Countries countries;
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
		/** (far view) its own working memory, so it never disturbs a column being computed */
		final PlateMap.Hit farHit = new PlateMap.Hit();
		final PlateTerrain.Raw farA = new PlateTerrain.Raw();
		final PlateTerrain.Raw farB = new PlateTerrain.Raw();
		final Countries.Here farHere = new Countries.Here();
		/** (lakes) Per lake cell: its key and its lake; the sampler for its site; how far inside the shore. */
		final long[] lakeKey = new long[64];
		final Lake[] lakes = new Lake[64];
		final PlateTerrain.Raw l = new PlateTerrain.Raw();
		final PlateMap.Hit hitL = new PlateMap.Hit();
		float lakeEdge;
		/** (great lakes) Per great-lake cell: its key and its lake. */
		final long[] greatKey = new long[32];
		final GreatLake[] greats = new GreatLake[32];
		/** (countries) Where the column lies on the map of countries; how far up a volcanic hill's core. */
		final Countries.Here here = new Countries.Here();
		final Countries.Here hereL = new Countries.Here();
		final float[] core = new float[2];
		/** (climate) the climate grid's nodes, direct-mapped */
		static final int CLIMATE = 1024;
		final long[] climKey = new long[CLIMATE];
		final float[] climT = new float[CLIMATE];
		final float[] climH = new float[CLIMATE];
		final PlateMap.Hit climHit = new PlateMap.Hit();
		/** (climate) one hit for each of the upwind and windward samples, so each keeps its own plates round it */
		final PlateMap.Hit[] climHits = {new PlateMap.Hit(), new PlateMap.Hit(), new PlateMap.Hit(), new PlateMap.Hit(), new PlateMap.Hit(),
			new PlateMap.Hit(), new PlateMap.Hit(), new PlateMap.Hit(), new PlateMap.Hit()};
		final PlateTerrain.Raw climRaw = new PlateTerrain.Raw();

		Scratch() {
			java.util.Arrays.fill(this.tepuiKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.lakeKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.greatKey, Long.MIN_VALUE);
			java.util.Arrays.fill(this.climKey, Long.MIN_VALUE);
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
		this.countries = new Countries(this, seed);
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

	/** The temperature and humidity at (x, z) before the lapse with height, as the columns have them. */
	float[] climate(double x, double z) {
		return this.climateAt(x, z);
	}

	// ------------------------------------------------------------------------------------------------ the climate

	/** The climate grid's spacing (blocks): climate changes over kilometres, so it is worked out at nodes and blended. */
	private static final int CLIMATE_CELL = 128;

	/**
	 * The temperature and humidity at (x, z) before the lapse with height: the climate as the land makes it, so every
	 * country lies where it does for a reason. The air warms and cools over broad zones tens of kilometres across
	 * (vanilla's temperature noise, stretched); the rain comes in off the sea on a westerly wind, so the coasts are
	 * wet and the land dries the further it lies inland, and a great range wrings the rain out of the wind, leaving the
	 * country in its lee dry for tens of kilometres (the rain shadow) while its windward foothills are the wettest of
	 * all. Over that a broad noise, as vanilla's humidity, so no two coasts are alike. Worked out on a grid of
	 * {@value #CLIMATE_CELL}-block nodes from the far view's coarse plates (never building a full plate) and blended
	 * between them; per-thread cached.
	 */
	float[] climateAt(double x, double z) {
		return this.climateAt(x, z, CLIMATE_CELL);
	}

	/** The far view's climate grid's spacing (blocks): its cells are half a kilometre and more. */
	private static final int CLIMATE_FAR_CELL = 1024;

	/** {@link #climateAt}, on a grid of {@code cell}-block nodes ({@value #CLIMATE_CELL}, or the far view's). */
	private float[] climateAt(double x, double z, int cell) {
		Scratch s = this.scratch.get();
		double fx = x / cell;
		double fz = z / cell;
		int ix = Mth.floor(fx);
		int iz = Mth.floor(fz);
		float tx = (float) (fx - ix);
		float tz = (float) (fz - iz);
		float t = 0;
		float h = 0;
		for (int c = 0; c < 4; c++) {
			int nx = ix + (c & 1);
			int nz = iz + (c >> 1);
			float w = ((c & 1) == 0 ? 1 - tx : tx) * ((c >> 1) == 0 ? 1 - tz : tz);
			int slot = this.climateNode(nx, nz, cell, s);
			t += w * s.climT[slot];
			h += w * s.climH[slot];
		}
		return new float[]{t, h};
	}

	private int climateNode(int nx, int nz, int cell, Scratch s) {
		// (the two grids share the slots, told apart by their keys)
		long key = ((long) nx << 32 ^ nz & 0xFFFF_FFFFL) + (cell == CLIMATE_CELL ? 0 : 0x4641_5200_0000_0000L);
		int slot = (int) (PlateMap.mix(cell, nx, nz) & (Scratch.CLIMATE - 1));
		if (s.climKey[slot] != key) {
			double x = (double) nx * cell;
			double z = (double) nz * cell;
			double sx = this.offset.get(x * 0.25, 0, z * 0.25) * 4;
			double sz = this.offset.get(z * 0.25, x * 0.25, 0) * 4;
			// broad zones: vanilla's noises at a third of the scale
			float temperature = (float) this.temperature.get(x * 0.07 + sx, 0, z * 0.07 + sz);
			float noise = (float) this.vegetation.get(x * 0.08 + sx, 0, z * 0.08 + sz);
			// the sea's rain and the ranges' shadow, from the coarse plates
			PlateTerrain.Raw r = s.climRaw;
			this.plates.locate(x, z, s.climHit);
			this.farTerrain(s.climHit.own).sample(x, z, x, z, false, r);
			float coast = r.coast;
			float maritime = 0.3F * (1 - smooth(coast / 4500)) - 0.25F * smooth((coast - 3500) / 6000);
			float shadow = 0;
			float windward = 0;
			int k = 0;
			for (int d = 1500; d <= 9000; d += 1500) {
				PlateMap.Hit hit = s.climHits[k++];
				this.plates.locate(x - d, z, hit);
				this.farTerrain(hit.own).sample(x - d, z, x - d, z, false, r);
				float range = smooth((r.uplift - 0.9F) / 1.0F);
				shadow = Math.max(shadow, range * (1 - d / 10000.0F));
			}
			for (int d = 600; d <= 2400; d += 900) {
				PlateMap.Hit hit = s.climHits[k++];
				this.plates.locate(x + d, z, hit);
				this.farTerrain(hit.own).sample(x + d, z, x + d, z, false, r);
				windward = Math.max(windward, smooth((r.uplift - 0.9F) / 1.0F) * (1 - d / 3000.0F));
			}
			float humidity = Mth.clamp(0.8F * noise + maritime - 0.5F * shadow + 0.2F * windward * (1 - shadow), -1.2F, 1.2F);
			s.climKey[slot] = key;
			s.climT[slot] = temperature;
			s.climH[slot] = humidity;
		}
		return slot;
	}

	// ------------------------------------------------------------------------------------------------ the far view

	/**
	 * The node spacing of the far view's plates (blocks): {@link #sampleFar} is the call for cells this size and up;
	 * for smaller ones, {@link #sample}.
	 */
	public static final int FAR_GRID = 512;
	/** How many far-view plates are kept (some 30 KB each: a 100 km horizon holds about 2,500). */
	private static final int MAX_FAR_PLATES = 4096;
	/** How much higher the far view's land stands than its coarse plates make it (see sampleFar). */
	private static final float FAR_LIFT = 7.5F;
	private final ConcurrentHashMap<Long, Entry> farTerrains = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicBoolean farEvicting = new java.util.concurrent.atomic.AtomicBoolean();

	/** A far-view column ({@link #sampleFar}): the land averaged over a cell of half a kilometre and more. */
	public static final class FarColumn {
		/** The ground's height, smoothed over the cell (the mean of the fine ground in it). */
		public float height;
		/** The cell's high ground: its ridges and peaks (about the 90th percentile of the fine ground), for skylines. */
		public float peak;
		/** The water's surface (y) where water stands over the ground (the sea), NaN on dry land. */
		public float water;
		public boolean ocean;
		/** The climate values the biome source reads, as {@link Column} has them. */
		public float continentalness;
		public float erosion;
		public float weirdness;
		public float lapse;
		public float temperature;
		public float humidity;
		public int landform;
		public float coast;
		public float uplift;
		public int rock;
		public int country;
	}

	/**
	 * The land at (x, z) for a far view (VOID LOD's horizon ring past 32 km, cells of 512 to 2048 blocks): the same
	 * plates, uplift, drainage, climate and countries as {@link #sample}, but each plate worked out on a grid of
	 * {@value #FAR_GRID}-block nodes (some 30 KB rather than megabytes, cached apart, never building a full plate),
	 * without rivers, lakes or anything smaller than the cell; what the countries raise above the ground on average
	 * (hills, towns of rock) is added as an average. Thread-safe; a few microseconds a call once its plates are built.
	 */
	public void sampleFar(int x, int z, FarColumn out) {
		Scratch s = this.scratch.get();
		PlateMap.Hit hit = s.farHit;
		this.plates.locate(x, z, hit);
		PlateTerrain.Raw a = s.farA;
		PlateTerrain plate = this.farTerrain(hit.own);
		plate.sample(x, z, x, z, false, a);
		float weight = 1;
		float sumHeight = a.height;
		float sumUplift = a.uplift;
		float sumCoast = a.coast;
		float sumBase = a.drainBase;
		float sumLowland = a.lowland;
		for (int n = 0; n < hit.nearCount; n++) {
			double border = hit.nearBorder[n];
			if (border >= BLEND) {
				continue;
			}
			PlateTerrain.Raw b = s.farB;
			double rx = x + 2 * border * hit.nearNormalX[n];
			double rz = z + 2 * border * hit.nearNormalZ[n];
			this.farTerrain(hit.near[n]).sample(rx, rz, rx, rz, false, b);
			float t = 0.5F * (1 - Mth.smoothstep((float) (Math.max(0, border) / BLEND)));
			float q = t / (1 - t);
			weight += q;
			sumHeight += q * b.height;
			sumUplift += q * b.uplift;
			sumCoast += q * b.coast;
			sumBase += q * b.drainBase;
			sumLowland += q * b.lowland;
		}
		float height = sumHeight / weight;
		float uplift = sumUplift / weight;
		float coast = sumCoast / weight;
		float base = sumBase / weight;
		float lowland = sumLowland / weight;
		// the coarse grid's drainage paths take longer steps between nodes, and so climb a little less: lifted back
		// by what that comes to on average (measured against the fine grid, -Pslice=far)
		height += FAR_LIFT * smooth((height - PlateTerrain.SEA) / 20);
		float[] clim = this.climateAt(x, z, CLIMATE_FAR_CELL);
		float temperature = clim[0];
		float humidity = clim[1];
		boolean land = height > PlateTerrain.SEA + 3;
		if (land) {
			float alpine = smooth((uplift - 0.9F) / 1.3F);
			if (alpine > 0 && height > lowland) {
				height = lowland + (height - lowland) * (1 + 0.65F * alpine);
			}
			if (height > 560) {
				height = 560 + 60 * (1 - (float) Math.exp(-(height - 560) / 60));
			}
			float heart = smooth((temperature + 0.42F) / 0.25F) * smooth((0.24F - temperature) / 0.2F) * smooth((humidity + 0.2F) / 0.3F)
				* smooth((coast - 400) / 700) * smooth((0.95F - uplift) / 0.5F);
			if (heart > 0) {
				if (height > lowland) {
					height = lowland + (height - lowland) * (1 - 0.3F * heart);
				}
				float swell = smooth((float) (this.noise(N_DOME, x / 1500.0 - 70, z / 1500.0 + 20) + 0.35) / 1.1F);
				height += heart * 38 * swell * smooth((coast - 300) / 500);
			}
		}
		Countries.Here here = s.farHere;
		this.countries.locate(x, z, here);
		out.country = here.kind;
		out.rock = Countries.STONE;
		if (land && here.kind != Countries.NONE && here.strength > 0) {
			float k = here.strength;
			float above = Math.max(0, height - lowland);
			float edge = ESCARPED[here.kind] * k * smooth((coast - 250) / 400) * smooth((1.0F - uplift) / 0.4F);
			if (edge > 0) {
				float n = (float) (this.noise(N_ESC, x / 3200.0, z / 3200.0) + 0.22 * this.noise(N_ESC, x / 640.0 + 40, z / 640.0));
				height += edge * (30 + 25 * (float) (0.5 + 0.5 * this.noise(N_ESC, x / 1900.0 - 77, z / 1900.0))) * smooth((n - 0.15F) / 0.035F);
			}
			switch (here.kind) {
				case Countries.HEARTLAND -> height -= above * 0.25F * k;
				case Countries.OLD_FOREST -> {
					height -= above * 0.2F * k;
					height += k * 30 * smooth((float) (this.noise(N_DOME, x / 2600.0, z / 2600.0) + 0.3) / 0.9F) * smooth((coast - 300) / 600)
						* smooth((200 - height) / 60);
				}
				case Countries.VOLCANIC_HILLS -> {
					height -= above * 0.3F * k;
					height += k * 9 * smooth((220 - height) / 50);     // the hills, on average over the cell
				}
				case Countries.ROCK_CITY -> {
					height -= above * 0.25F * k;
					float patch = smooth((float) (this.noise(N_ROCK, x / 620.0, z / 620.0) - 0.12) / 0.22F) * k;
					height += 14 * patch * smooth((190 - height) / 40);      // the towers, on average
					out.rock = k > 0.4F ? Countries.SANDSTONE : Countries.STONE;
				}
				case Countries.LAKE_DISTRICT -> height -= above * 0.3F * k;
				case Countries.GORGES -> {
					float plateau = lowland + 32 + 12 * (float) this.noise(N_DOME, x / 1800.0 + 11, z / 1800.0);
					float lowlands = k * smooth((coast - 250) / 500) * smooth((1.05F - uplift) / 0.4F);
					height = Mth.lerp(lowlands, height, plateau + 0.3F * (height - plateau)) - 4 * lowlands;   // less the gorges
					out.rock = lowlands > 0.4F ? Countries.LIMESTONE : Countries.STONE;
				}
				default -> {
				}
			}
		}
		// The coarse grid can't hold the mountains' ridges, only the broad valleys between them, so the higher the
		// land is pushed the lower it comes out against the fine ground averaged over the cell: lifted back by what
		// that comes to (measured by uplift, -Pslice=farpeaks: about 37 blocks for each unit of uplift over 0.6).
		// The cell's high ground (its ridges and peaks, about the 90th percentile of the fine ground in it) stands
		// a further 22 blocks and more over that: the far view's skyline.
		float ranges = Math.max(0, uplift - 0.6F);
		float peak = 0;
		if (land) {
			height += 37 * Math.min(ranges, 3.0F);
			peak = 22 + 40 * Math.min(ranges, 2.2F);
		}
		// The coarse grid's coastline steps from the land down to the sea floor across a whole cell, which pulls the
		// coastal plains under the sea (land read as sea, and the shore a dozen blocks low): lifted back by up to 16
		// blocks at the shore, nothing two kilometres inland (-Pslice=farcoast). What is left wrong is the cell or so
		// either side of the shore that the coarse grid can't place.
		if (coast > 0) {
			height += 16 * (1 - smooth(coast / 2000));
		}
		// the volcanoes' cones, which stand out over any distance
		out.landform = Landform.NONE;
		java.util.List<PlateTerrain.Volcano> volcanoes = plate.volcanoes();
		for (int v = 0; v < volcanoes.size(); v++) {
			PlateTerrain.Volcano volcano = volcanoes.get(v);
			double d = PlateMap.len(x - volcano.x(), z - volcano.z());
			double r = volcano.radius();
			if (d >= r) {
				continue;
			}
			float apex = plate.summit(v) + 40 + 35 * (float) volcano.strength();
			float foot = PlateTerrain.SEA + 6;
			height = Math.max(height, (float) (foot + (apex - foot) * Math.pow(1 - d / r, 1.7)));
			if (d < r * 0.62 && height > PlateTerrain.SEA + 4) {
				out.landform = Landform.VOLCANIC;
			}
		}
		out.height = height;
		out.peak = out.landform == Landform.VOLCANIC ? height : Math.min(height + peak, 630);
		out.ocean = height < PlateTerrain.SEA;
		out.water = out.ocean ? PlateTerrain.SEA : Float.NaN;
		out.coast = coast;
		out.uplift = uplift;
		out.continentalness = continentalness(coast);
		out.erosion = (float) Mth.clamp(0.42 - 1.25 * Mth.smoothstep(Mth.clamp((uplift - 0.12F) / 1.5F, 0, 1))
			+ 0.22 * this.noise(N_EROSION, x / 800.0, z / 800.0), -1, 1);
		float pv = Mth.clamp(-0.7F + (height - base) / 55.0F, -0.7F, 1);
		float sign = this.noise(N_SIGN, x / 1100.0, z / 1100.0) >= 0 ? 1 : -1;
		out.weirdness = out.landform != Landform.NONE ? Landform.weirdness(out.landform) : sign * (pv + 1) / 3;
		out.lapse = -0.8F * Mth.clamp((height - 110) / 260.0F, 0, 1);
		out.temperature = temperature + out.lapse;
		out.humidity = humidity;
	}

	private PlateTerrain farTerrain(PlateMap.Plate plate) {
		Entry entry = this.farTerrains.get(plate.key());
		if (entry == null) {
			Entry mine = new Entry(this.clock.incrementAndGet());
			entry = this.farTerrains.putIfAbsent(plate.key(), mine);
			if (entry == null) {
				entry = mine;
				try {
					mine.terrain.complete(new PlateTerrain(this, plate, FAR_GRID));
				} catch (Throwable t) {
					mine.terrain.completeExceptionally(t);
					this.farTerrains.remove(plate.key(), mine);
					throw t;
				}
				if (this.farTerrains.size() > MAX_FAR_PLATES && this.farEvicting.compareAndSet(false, true)) {
					// the least recently used half goes at once (rather than one at a time, each a scan of thousands), one
					// thread at a time; sorted on a snapshot of when each was used, which other threads go on changing
					try {
						record Used(Long key, Entry entry, long used) {
						}
						java.util.List<Used> all = new java.util.ArrayList<>();
						for (Map.Entry<Long, Entry> e : this.farTerrains.entrySet()) {
							if (e.getValue().terrain.isDone()) {
								all.add(new Used(e.getKey(), e.getValue(), e.getValue().used));
							}
						}
						all.sort(java.util.Comparator.comparingLong(Used::used));
						for (int i = 0; i < Math.min(all.size(), MAX_FAR_PLATES / 2); i++) {
							this.farTerrains.remove(all.get(i).key(), all.get(i).entry());
						}
					} finally {
						this.farEvicting.set(false);
					}
				}
			}
		}
		entry.used = this.clock.incrementAndGet();
		return entry.terrain.join();
	}

	/** How much of each kind of country (Countries order) breaks off in escarpments. */
	private static final float[] ESCARPED = {0, 0.6F, 1.0F, 0.4F, 0, 0.8F, 0.6F};

	/** How open each kind of country is (Countries order): the share of its land left as fields and meadow. */
	private static final float[] OPENNESS = {0.12F, 0.42F, -0.35F, 0.38F, 0.3F, 0.22F, -0.45F};
	/** How strongly the woods keep to the rises and slopes in each kind of country (the heartland's swells are farmed). */
	private static final float[] RISES = {1, 0.65F, 1, 0.85F, 1, 1, 1};

	/**
	 * How likely a tree is to stand at (x, z), 0 to 1: the woods and the open country. A broad noise lays out woods
	 * and clearings with edges a dozen blocks wide, so a wood ends rather than thins out; the woods keep to the
	 * rises and slopes (height over the drainage, steepness) and leave the vale floors and the floodplains open;
	 * each country has its own share of open land (the heartland is mostly fields, the old forest mostly wood, rock
	 * towns stand in forest); a few lone trees stand out in the open; above the treeline the trees give out.
	 */
	public float woodland(int x, int z) {
		Column c = this.sample(x, z);
		float height = c.height;
		float above = c.aboveRiver;
		int country = c.country;
		float strength = c.countryStrength;
		boolean nearRiver = c.nearRiver();
		float riverDist = c.riverDist - c.riverHalfWidth;
		if (height < PlateTerrain.SEA + 1) {
			return 1;
		}
		float gx = this.sample(x + 4, z).height - height;
		float gz = this.sample(x, z + 4).height - height;
		float slope = (float) Math.sqrt(gx * gx + gz * gz) / 4;
		float mask = (float) (0.8 * this.noise(N_WOOD, x / 540.0, z / 540.0) + 0.3 * this.noise(N_WOOD, x / 150.0 + 70, z / 150.0));
		float rises = 1 + (RISES[country] - 1) * strength;
		mask += rises * (0.012F * Mth.clamp(above - 14, -14, 40) + 0.6F * Math.min(slope, 1));
		mask -= 0.12F + (OPENNESS[country] - 0.12F) * strength;
		float keep = 0.05F + 0.95F * smooth((mask + 0.07F) / 0.14F);
		if (nearRiver && riverDist < 22 && above < 5) {
			keep *= 0.35F;     // the floodplain meadows (the bank trees come with the water's edge)
		}
		keep *= 1 - 0.92F * smooth((height - 185) / 40);     // the treeline
		return keep;
	}

	/**
	 * Whether (x, z) is on the crest of a rise in open country: no ground within 18 blocks round it stands higher, it
	 * stands 6 blocks and more over the ground 40 blocks off, and no wood grows there. Where a lone tree belongs.
	 */
	public boolean crest(int x, int z) {
		Column c = this.sample(x, z);
		float height = c.height;
		if (height < PlateTerrain.SEA + 6 || height > 175 || c.nearRiver() && c.riverDist < c.riverHalfWidth + 30
			|| this.woodland(x, z) >= 0.3F) {
			return false;
		}
		float lower = 0;
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			if (this.sample(x + (int) Math.round(Math.cos(a) * 18), z + (int) Math.round(Math.sin(a) * 18)).height > height + 0.3F) {
				return false;
			}
			lower += this.sample(x + (int) Math.round(Math.cos(a) * 40), z + (int) Math.round(Math.sin(a) * 40)).height;
		}
		return height - lower / 8 > 6;
	}

	/** How far from (x, z) the nearest river's bank is (blocks; far when there is none near). */
	float riverClearance(double x, double z) {
		PlateMap.Hit hit = new PlateMap.Hit();
		this.plates.locate(x, z, hit);
		PlateTerrain.Raw raw = new PlateTerrain.Raw();
		this.terrain(hit.own).sample(x, z, x, z, true, raw);
		return Float.isNaN(raw.riverWater) ? 1000 : raw.riverDist - raw.riverHalfWidth;
	}

	/** The kind of country at (x, z) ({@link Countries}), for a cell's site search. */
	private int countryAt(double x, double z, Scratch s) {
		this.countries.locate(x, z, s.hereL);
		return s.hereL.strength > 0.5F ? s.hereL.kind : Countries.NONE;
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

	/**
	 * Keeps up to {@code plates} built plates rather than {@link #MAX_PLATES}: for a far view (VOID LOD), which
	 * samples across many plates at once, where building each once is much faster than building some over and
	 * over. Asking for fewer again lets the least recently used go at once.
	 */
	public void reservePlates(int plates) {
		this.maxPlates = Math.max(MAX_PLATES, plates);
		this.evict();
	}

	/** Drops the least recently used plates once there are too many: each holds a few megabytes. */
	private void evict() {
		while (this.terrains.size() > this.maxPlates) {
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
		// Rivers wander either side of their course: long swings and short wiggles; in some country (a broad noise)
		// the swings grow to twice that and more, and the rivers loop through their vales.
		double swing = 9 + 11 * smooth((float) (this.noise(N_MEANDER_X, x / 4200.0 + 90, z / 4200.0 - 40) + 0.1) / 0.6F);
		double mx = x + swing * this.noise(N_MEANDER_X, x / (100 + swing), z / (100 + swing)) + 3 * this.noise(N_MEANDER_Z, x / 37.0 + 5, z / 37.0);
		double mz = z + swing * this.noise(N_MEANDER_Z, x / (100 + swing), z / (100 + swing)) + 3 * this.noise(N_MEANDER_X, x / 37.0 - 5, z / 37.0);
		this.terrain(own).sample(x, z, mx, mz, true, a);
		float height = a.height;
		float uplift = a.uplift;
		float coast = a.coast;
		float base = a.drainBase;
		float lowland = a.lowland;
		// Near a border, mirror the point across it into the neighbouring plate and blend toward that, so the
		// two meet at the border at the same height from either side. Every neighbour within reach takes part,
		// each by how near its border is (half and half on a border, as before between two plates): where three
		// plates meet, blending toward only the nearest would jump from one to the other along a line.
		float weight = 1;
		float sumHeight = height;
		float sumUplift = uplift;
		float sumCoast = coast;
		float sumBase = base;
		float sumLowland = lowland;
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
			sumLowland += q * b.lowland;
		}
		height = sumHeight / weight;
		uplift = sumUplift / weight;
		coast = sumCoast / weight;
		base = sumBase / weight;
		lowland = sumLowland / weight;

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
		float[] clim = this.climateAt(x, z);
		float temperature = clim[0];
		float humidity = clim[1];
		out.landform = Landform.NONE;
		boolean land = height > PlateTerrain.SEA + 3;

		// The great ranges: where the land is pushed up hardest, its relief over the lowland is drawn out by up to
		// two thirds again, so the ranges stand 500 blocks and more over the sea: a wall of rock and snow seen from
		// far across the country in front of them. The top of the world (y 639) is approached, never reached.
		if (land) {
			float alpine = smooth((uplift - 0.9F) / 1.3F);
			if (alpine > 0 && height > lowland) {
				height = lowland + (height - lowland) * (1 + 0.65F * alpine);
			}
			if (height > 560) {
				height = 560 + 60 * (1 - (float) Math.exp(-(height - 560) / 60));
			}
		}

		// The Bohemian heartland: temperate country well inland and away from the ranges rolls rather than climbs.
		// Its small hills are worn lower over their valleys, and the land swells instead into long broad rises 20 to
		// 40 blocks over the vales between them, a kilometre and more across, so ridge behind ridge stands against
		// the sky; the mountains and the coasts keep their relief.
		if (land) {
			float heart = smooth((temperature + 0.42F) / 0.25F) * smooth((0.24F - temperature) / 0.2F) * smooth((humidity + 0.2F) / 0.3F)
				* smooth((coast - 400) / 700) * smooth((0.95F - uplift) / 0.5F);
			if (heart > 0) {
				if (height > lowland) {
					height = lowland + (height - lowland) * (1 - 0.3F * heart);
				}
				float swell = smooth((float) (this.noise(N_DOME, x / 1500.0 - 70, z / 1500.0 + 20) + 0.35) / 1.1F);
				height += heart * 38 * swell * smooth((coast - 300) / 500);
			}
		}

		// The countries: each reshapes its land its own way (Countries). A river keeps its valley through them.
		Countries.Here here = s.here;
		this.countries.locate(x, z, here);
		out.country = here.kind;
		out.countryStrength = here.strength;
		out.rock = Countries.STONE;
		out.site = Column.SITE_NONE;
		float gorge = 0;
		if (land && here.kind != Countries.NONE && here.strength > 0) {
			float k = here.strength;
			// (a column knows its river only within the river's box, 56 blocks and more out: be clear of it by then)
			float clear = river ? smooth((a.riverDist - a.riverHalfWidth - 10) / 40) : 1;
			float above = Math.max(0, height - lowland);
			// Escarpments: here and there a long edge where the upland ends in a cliff over the country below, 30 to
			// 55 blocks high, following a broad noise's contour so it winds for kilometres (the cuestas of the
			// sandstone and limestone country, the view from a castle). Rivers cut their way through it.
			float edge = ESCARPED[here.kind] * k * smooth((coast - 250) / 400) * smooth((1.0F - uplift) / 0.4F);
			if (edge > 0) {
				float n = (float) (this.noise(N_ESC, x / 3200.0, z / 3200.0) + 0.22 * this.noise(N_ESC, x / 640.0 + 40, z / 640.0));
				float step = smooth((n - 0.15F) / 0.035F);
				height += edge * (30 + 25 * (float) (0.5 + 0.5 * this.noise(N_ESC, x / 1900.0 - 77, z / 1900.0))) * step;
				if (step > 0.7F && step < 0.995F && n - 0.15F < 0.06F && edge > 0.5F) {
					out.site = Column.SITE_RIM;
				}
			}
			switch (here.kind) {
				case Countries.HEARTLAND -> height -= above * 0.25F * k;
				case Countries.OLD_FOREST -> {
					height -= above * 0.2F * k;
					height += k * 30 * smooth((float) (this.noise(N_DOME, x / 2600.0, z / 2600.0) + 0.3) / 0.9F) * smooth((coast - 300) / 600)
						* smooth((200 - height) / 60);
				}
				case Countries.VOLCANIC_HILLS -> {
					height -= above * 0.3F * k;
					// (lone hills on rolling country: none on the high ranges)
					float rise = k * this.countries.volcanicHill(x, z, here.seed, s.core) * smooth((220 - height) / 50);
					height += rise;
					if (rise > 18 && s.core[0] > 0.5F) {
						out.rock = Countries.BASALT;
					}
					if (s.core[1] > 0 && rise > 30) {
						out.site = Column.SITE_HILLTOP;
					}
				}
				case Countries.ROCK_CITY -> {
					height -= above * 0.25F * k;
					float rise = this.countries.rockTown(x, z, k, clear) * smooth((190 - height) / 40);
					height += rise;
					if (k > 0.4F) {
						out.rock = Countries.SANDSTONE;
					}
				}
				case Countries.LAKE_DISTRICT -> {
					height -= above * 0.3F * k;
					height += k * clear * this.countries.drumlins(x, z, here.seed);
				}
				case Countries.GORGES -> {
					// a level-topped plateau over the drainage, its rivers sunk in gorges (the valley below makes the walls)
					// (rolling a little on top, as karst uplands do, and dimpled with shallow sinkholes)
					float plateau = lowland + 32 + 12 * (float) this.noise(N_DOME, x / 1800.0 + 11, z / 1800.0)
						+ 9 * (float) this.noise(N_DOME, x / 420.0 - 40, z / 420.0) + 3 * (float) this.noise(N_ROCK, x / 120.0 + 300, z / 120.0)
						- this.countries.doline(x, z);
					// (a plateau of the lowland and the hill country: the high ranges keep their peaks)
					float lowlands = k * smooth((coast - 250) / 500) * smooth((1.05F - uplift) / 0.4F);
					height = Mth.lerp(lowlands, height, plateau + 0.3F * (height - plateau));
					gorge = 0.9F * lowlands;
					if (lowlands > 0.4F) {
						out.rock = Countries.LIMESTONE;
					}
				}
				default -> {
				}
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
			double angle = Math.atan2(dz, dx);
			// no cone is perfect: its foot wanders in and out, one flank stands fuller than another (old flows,
			// a collapse), and the gullies wander as water cuts them rather than running out like spokes
			double flank = 1 + 0.2 * this.noise(N_DOME, Math.cos(angle) * 1.4 + volcano.x() / 83.0, Math.sin(angle) * 1.4 + volcano.z() / 83.0)
				+ 0.08 * this.noise(N_DOME, Math.cos(angle) * 4.1 - volcano.x() / 61.0, Math.sin(angle) * 4.1 + 17);
			double t = Math.max(0, 1 - d / (r * (0.82 + 0.18 * flank)));
			double bend = angle + 0.5 * this.noise(N_ROUGH, x / 230.0 + 13, z / 230.0) * (d / r);
			// (on a circle round the summit, so the pattern closes on itself with no seam where the bearing wraps)
			double gully = 1 - Math.abs(this.noise(N_DETAIL_B, Math.cos(bend) * 2.6 + d / 120.0, Math.sin(bend) * 2.6 - d / 120.0));
			float cone = (float) (foot + (apex - foot) * Math.pow(t, 1.7) * (0.88 + 0.12 * flank)
				- (4 + 0.07 * (apex - foot) * t) * gully * gully * gully * (0.6 + 0.4 * flank));
			// (a smooth maximum, so the cone's foot spreads into the land round it rather than meeting it at a line;
			// exactly the higher of the two wherever they differ by more than 16)
			float ground = height;
			float k = Math.max(0, 16 - Math.abs(cone - ground));
			height = Math.max(ground, cone) + k * k / 64;
			// the volcano's own country is the cone where it stands clear of the land, not a circle round it
			if (cone > ground + 8 && d < r * 0.8 && height > PlateTerrain.SEA + 4) {
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
			height = valley(height, a.riverDist, a.riverHalfWidth, a.riverWater, Math.max(canyon, gorge));
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
				float carved = valley(height, Math.max(nr.riverDist, hw + 1), hw, nr.riverWater, Math.max(canyon, gorge));
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
		// ...and high in the great ranges, wherever they are, the ice ground the valleys into troughs too: broad
		// floors between steep walls, the river winding along the bottom
		glacial = Math.max(glacial, 0.85F * smooth((uplift - 1.3F) / 0.8F) * smooth((height - 240) / 120));
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
				if (g.exists() && PlateMap.len(x - g.x(), z - g.z()) < g.r() * 1.15 + 36) {
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
							&& PlateMap.len(o.x() - g.x(), o.z() - g.z()) < (o.r() + g.r()) * 1.15 + 60) {
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
		key = PlateMap.scramble(key);   // (so the keys hash evenly: the packed pair alone hashes to gx ^ gz)
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
		if (PlateMap.unit(h) > 0.6 && this.countryAt((gx + 0.5) * GREAT_CELL, (gz + 0.5) * GREAT_CELL, s) != Countries.LAKE_DISTRICT) {
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
			float[] clim = this.climateAt(px, pz);
			float temperature = clim[0];
			float humidity = clim[1];
			if (humidity < -0.2F || smooth(humidity / 0.2F) * smooth((temperature - 0.3F) / 0.2F) > 0.3F) {
				continue;
			}
			boolean volcano = false;
			for (PlateTerrain.Volcano v : plate.volcanoes()) {
				volcano |= PlateMap.len(px - v.x(), pz - v.z()) < v.radius() + 380;
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
				double d = PlateMap.len(x - lake.x(), z - lake.z());
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
			// a lake's site search samples the plate a few dozen times: done once per cell for every thread
			// (a far view samples thousands of cells, far more than a thread's slots hold)
			long shared = PlateMap.scramble(key);
			Lake lake = this.lakes.get(shared);
			if (lake == null) {
				lake = this.makeLake(lx, lz, s);
				if (this.lakes.size() > 400_000) {
					this.lakes.clear();
				}
				this.lakes.putIfAbsent(shared, lake);
			}
			s.lakes[slot] = lake;
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
		// three cells in five have a lake, and in the lake district all of them (asked only when it matters)
		if (PlateMap.unit(h) > 0.6 && this.countryAt((lx + 0.5) * LAKE_CELL, (lz + 0.5) * LAKE_CELL, s) != Countries.LAKE_DISTRICT) {
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
			float[] clim = this.climateAt(px, pz);
			float temperature = clim[0];
			float humidity = clim[1];
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
				volcano |= PlateMap.len(px - v.x(), pz - v.z()) < v.radius() + r + 40;
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
				double centre = PlateMap.len(x - px, z - pz);
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
			float[] clim = this.climateAt(px, pz);
			float temperature = clim[0];
			float humidity = clim[1];
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

	/** A patchy noise (-1 to 1, some ten blocks across) for where bare rock breaks through on steep ground. */
	public double outcrop(int x, int z) {
		return this.noise(N_ROCK, x / 9.0 - 500, z / 9.0 + 230);
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
