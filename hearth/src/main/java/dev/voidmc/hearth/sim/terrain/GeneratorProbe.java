package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.hearth.api.TerrainProbe;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * The land as the world generator makes it, before features, read one column at a time without building a chunk:
 * {@link ChunkGenerator#getBaseColumn} walks the noise down a single column (vanilla, modded or Expanse worlds alike),
 * and one walk gives both the ground and any water over it. Biomes come from the biome source.
 *
 * <p>Thread-safe: every thread has its own direct-mapped column cache and biome resolver, so world generation workers,
 * the atlas and the server thread never contend.
 */
public class GeneratorProbe implements TerrainProbe {
	private static final int CACHE = 1 << 13;
	/** Columns computed (cache misses) and the nanoseconds they took, for the dev harness. */
	public static final LongAdder SAMPLES = new LongAdder();
	public static final LongAdder NANOS = new LongAdder();

	protected final ChunkGenerator generator;
	protected final RandomState randomState;
	protected final LevelHeightAccessor heights;
	protected final int seaLevel;
	private final long seed;
	private final ThreadLocal<BiomeResolver> resolvers;
	private final ThreadLocal<Cache> caches = ThreadLocal.withInitial(Cache::new);

	public GeneratorProbe(ServerLevel level) {
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.heights = level;
		this.seaLevel = this.generator.getSeaLevel();
		this.seed = level.getSeed();
		this.resolvers = ThreadLocal.withInitial(() -> this.generator.getBiomeSource().createCachingResolver(this.randomState));
	}

	private static final class Cache {
		final long[] keys = new long[CACHE];
		final int[] ground = new int[CACHE];
		final int[] water = new int[CACHE];
		final float[] temperature = new float[CACHE];

		Cache() {
			java.util.Arrays.fill(this.keys, Long.MIN_VALUE);
		}
	}

	/** One column's answer, written by {@link #compute}. */
	protected static final class Sample {
		public int ground;
		public int water = NO_WATER;
		/** NaN: take it from the biome. */
		public float temperature = Float.NaN;
	}

	private int slot(Cache c, int x, int z) {
		long key = (long) x << 32 ^ z & 0xFFFF_FFFFL;
		int h = (int) ((key * 0x9E3779B97F4A7C15L) >>> 51) & (CACHE - 1);
		if (c.keys[h] != key) {
			long start = System.nanoTime();
			Sample s = new Sample();
			compute(x, z, s);
			float t = Float.isNaN(s.temperature) ? biomeTemperature(x, z, Math.max(s.ground, this.seaLevel)) : s.temperature;
			c.keys[h] = key;
			c.ground[h] = s.ground;
			c.water[h] = s.water;
			c.temperature[h] = t;
			SAMPLES.increment();
			NANOS.add(System.nanoTime() - start);
		}
		return h;
	}

	/** Fills in the ground and water of a column from the generator. Subclasses with faster sources override it. */
	protected void compute(int x, int z, Sample out) {
		NoiseColumn column = this.generator.getBaseColumn(x, z, this.heights, this.randomState);
		int top = this.heights.getMaxY();
		int bottom = this.heights.getMinY();
		int water = NO_WATER;
		int y = top;
		for (; y >= bottom; y--) {
			BlockState state = column.getBlock(y);
			if (state.isAir()) {
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				if (water == NO_WATER) {
					water = y + 1;
				}
				continue;
			}
			break;
		}
		out.ground = y + 1;
		out.water = water;
	}

	@Override
	public long seed() {
		return this.seed;
	}

	@Override
	public int minY() {
		return this.heights.getMinY();
	}

	@Override
	public int maxY() {
		return this.heights.getMaxY();
	}

	@Override
	public int seaLevel() {
		return this.seaLevel;
	}

	@Override
	public int groundY(int x, int z) {
		Cache c = this.caches.get();
		return c.ground[slot(c, x, z)];
	}

	@Override
	public int waterY(int x, int z) {
		Cache c = this.caches.get();
		return c.water[slot(c, x, z)];
	}

	@Override
	public float temperature(int x, int z) {
		Cache c = this.caches.get();
		return c.temperature[slot(c, x, z)];
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return biomeHolder(x, z).unwrapKey().orElse(null);
	}

	/** The surface biome as a holder (what the culture mapping takes). */
	public Holder<Biome> biomeHolder(int x, int z) {
		int y = Math.max(groundY(x, z), this.seaLevel);
		return biomeAt(x, y, z);
	}

	protected Holder<Biome> biomeAt(int x, int y, int z) {
		return this.resolvers.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	/** Vanilla's height-adjusted temperature, without its small noise: it cools above the snow line. */
	private float biomeTemperature(int x, int z, int y) {
		Biome biome = biomeAt(x, y, z).value();
		float t = biome.getBaseTemperature();
		int snowLevel = this.seaLevel + 17;
		if (y > snowLevel) {
			t -= (y - snowLevel) * 0.05F / 40.0F;
		}
		return t;
	}

	/**
	 * A cheaper view for surveys that only compare places (the atlas choosing a site): the same probe here; the Expanse
	 * probe returns its model without the grid correction.
	 */
	public GeneratorProbe rough() {
		return this;
	}

	/** Which source answers columns, for logs and the harness. */
	public String describe() {
		return "generator (" + this.generator.getClass().getSimpleName() + ")";
	}
}
