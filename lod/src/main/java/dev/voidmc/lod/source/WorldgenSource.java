package dev.voidmc.lod.source;

import dev.voidmc.lod.core.Colors;
import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.mixin.MaterialSystemAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * Terrain straight from a world's generator, without generating a chunk: the column height from
 * {@link ChunkGenerator#getBaseHeight} (which walks the noise down one column instead of filling 98,304
 * blocks) and the biome from the biome source. Works for any noise-based world, vanilla or modded; the
 * Expanse source is the fast path for Earth worlds, whose terrain model answers a column in microseconds.
 *
 * <p>Needs the server's generator and seed, so it exists in singleplayer and on a LAN host.
 */
public class WorldgenSource implements TerrainSource {
	protected final ServerLevel level;
	protected final ChunkGenerator generator;
	protected final RandomState randomState;
	protected final BiomeSource biomes;
	/** One resolver per worker: the caching ones keep per-thread state. */
	protected final ThreadLocal<BiomeResolver> resolvers;
	protected final BiomeLooks looks;
	protected final int seaLevel;
	/** The world's own badlands strata, one colour per block of height, and the noise that shifts them. */
	protected final int[] strata;
	private final Noise strataOffset;
	private final long version;
	/**
	 * Time spent in the terrain model, the biome lookup, and the whole of sample(), for the statistics: kept only
	 * when they are logged (dev runs), so the hot path carries no clock reads otherwise.
	 */
	public static final boolean TIMING = Boolean.getBoolean("void.lod.logStats");
	public static final java.util.concurrent.atomic.AtomicLongArray TIMES = new java.util.concurrent.atomic.AtomicLongArray(3);

	public WorldgenSource(ServerLevel level, long version) {
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.biomes = this.generator.getBiomeSource();
		this.resolvers = ThreadLocal.withInitial(() -> this.biomes.createCachingResolver(this.randomState));
		this.looks = new BiomeLooks(level);
		this.seaLevel = this.generator.getSeaLevel();
		this.version = version;
		int[] strata = null;
		Noise offset = null;
		try {
			MaterialSystemAccessor materials = (MaterialSystemAccessor) this.randomState.surfaceSystem();
			BlockState[] bands = materials.voidLod$clayBands();
			strata = new int[bands.length];
			for (int i = 0; i < bands.length; i++) {
				strata[i] = this.looks.colorOf(bands[i]);
			}
			offset = materials.voidLod$clayBandsOffsetNoise();
		} catch (RuntimeException e) {
			// a generator without vanilla's material system: the looks' own strata stand in
		}
		this.strata = strata;
		this.strataOffset = offset;
	}

	@Override
	public long version() {
		return this.version;
	}

	/** Anything about the source worth a line in the statistics. */
	public String diagnostics() {
		return "";
	}

	protected Holder<Biome> biome(int x, int y, int z) {
		return this.resolvers.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	/**
	 * How much of the ground here lies under snow, by vanilla's own test (the biome's temperature, falling with
	 * height, ragged with its noise), so the snow line is where the chunks will put it. A wide cell spans a
	 * stretch of height, so far out the test runs over that stretch and the line softens.
	 */
	protected float snow(Holder<Biome> biome, int x, int y, int z, int cell) {
		Biome b = biome.value();
		if (b.getBaseTemperature() < 0.15F) {
			return 1;
		}
		if (cell <= 2) {
			// close to the chunks: vanilla's own test, block for block
			return b.coldEnoughToSnow(new BlockPos(x, y, z), this.seaLevel) ? 1 : 0;
		}
		// Vanilla's temperature falls 0.05 per 40 blocks above sea level + 17, give or take 8 blocks of noise, and
		// snow lies below 0.15. Out where a cell spans a stretch of slope, that is a soft band around the line.
		float line = this.seaLevel + 17 + (b.getBaseTemperature() - 0.15F) / 0.00125F;
		float soft = 8 + cell;
		return Colors.smooth(line - soft, line + soft, y);
	}

	/** Badlands walls in the world's own strata, where it has them. */
	protected void strata(int x, int z, ColumnSample out) {
		if (out.bands != null && this.strata != null) {
			out.bands = this.strata;
			out.bandHeight = 1;
			out.bandOffset = this.strataOffset == null ? 0 : Math.round((float) this.strataOffset.get(x, 0, z) * 4);
		}
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		int ground = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.level, this.randomState);
		Holder<Biome> biome = this.biome(x, ground, z);
		BiomeLooks.Look look = this.looks.get(biome);
		look.fill(out);
		out.ground = ground;
		this.strata(x, z, out);
		if (ground < this.seaLevel) {
			out.water = this.seaLevel;
			out.top = look.bed();
			out.canopy = 0;
			out.cover = 0;
			return;
		}
		if (out.bands != null) {
			out.side = ColumnSample.rockAt(out.bands, out.bandHeight, out.rock, ground - 1 + out.bandOffset);
		}
		float snow = look.ground() == BiomeLooks.Ground.SNOWCAP ? 0 : this.snow(biome, x, ground, z, cell);
		if (snow > 0) {
			out.top = Colors.mix(out.top, this.looks.snowTop, snow);
			if (look.ground() == BiomeLooks.Ground.GRASS) {
				out.side = Colors.mix(out.side, this.looks.snowSide, snow);
			}
		} else if (ground <= this.seaLevel + 1 && look.ground() == BiomeLooks.Ground.GRASS) {
			// most shores are sand, whatever the biome inland
			out.top = out.side = out.soil = this.looks.sand;
		}
	}
}
