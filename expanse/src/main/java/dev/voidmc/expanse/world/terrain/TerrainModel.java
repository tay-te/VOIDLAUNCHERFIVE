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
	private static final int NOISES = 14;

	private final long seed;
	private final PlateMap plates;
	private final SimplexNoise[] noises = new SimplexNoise[NOISES];
	private final ConcurrentHashMap<Long, Entry> terrains = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

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
	}

	private TerrainModel(long seed) {
		this.seed = seed;
		this.plates = new PlateMap(seed);
		RandomSource random = RandomSource.create(seed ^ 0x5445_5252_4149_4EL);
		for (int i = 0; i < NOISES; i++) {
			this.noises[i] = new SimplexNoise(random);
		}
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

		// The river valley: a floor a block above the water, rising smoothly into the land round it.
		if (river) {
			float water = a.riverWater;
			float floor = water + 1;
			float hw = a.riverHalfWidth;
			float plain = hw + 3 + hw * 0.8F + 10;
			if (a.riverDist < hw) {
				height = floor;
			} else if (a.riverDist < plain) {
				float t = Mth.smoothstep((a.riverDist - hw) / (plain - hw));
				height = Math.max(floor, Mth.lerp(t, floor, height));
			}
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
		out.weirdness = sign * (pv + 1) / 3;
		out.lapse = -0.8F * Mth.clamp((height - 110) / 260.0F, 0, 1);
		out.plateX = own.gx();
		out.plateZ = own.gz();
		out.continental = own.continental();
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

	/** A riverbed block for (x, z): gravel mostly, with sand bars and clay in patches. */
	public int bedKind(int x, int z) {
		double n = this.noise(N_BED, x / 9.0, z / 9.0);
		return n > 0.35 ? 1 : n < -0.5 ? 2 : 0;
	}
}
