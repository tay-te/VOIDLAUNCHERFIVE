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
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * Terrain straight from a world's generator, without generating a chunk: the column height from
 * {@link ChunkGenerator#getBaseHeight} (which walks the noise down one column instead of filling 98,304
 * blocks) and the biome from the biome source. Works for any noise-based world, vanilla or modded; the
 * Expanse source is the fast path for Earth worlds, whose terrain model answers a column in microseconds.
 *
 * <p>Worlds whose generator fills with air instead of water (the End, sky islands) float over the void: there
 * a column is read whole, for its top and the underside of the island it belongs to, and a column with no
 * ground at all is empty. Such worlds have no sea.
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
	/** The world's material registry: shared with chunk ingestion and the renderer. */
	protected final Materials materials;
	protected final int seaLevel;
	/** Whether the world has a sea at all: not where the generator fills with air. */
	protected final boolean sea;
	/** Islands over the void (the End): columns are read whole, for where their ground ends below. */
	protected final boolean floating;
	/** The world's own badlands strata, one colour per block of height, and the noise that shifts them. */
	protected final int[] strata;
	/** The strata's blocks, as material ids. */
	protected final int[] strataMaterials;
	private final Noise strataOffset;
	private final long version;
	/**
	 * Time spent in the terrain model, the biome lookup, and the whole of sample(), for the statistics: kept only
	 * when they are logged (dev runs), so the hot path carries no clock reads otherwise.
	 */
	public static final boolean TIMING = Boolean.getBoolean("void.lod.logStats");
	public static final java.util.concurrent.atomic.AtomicLongArray TIMES = new java.util.concurrent.atomic.AtomicLongArray(3);

	public WorldgenSource(ServerLevel level, long version) {
		this(level, version, new Materials());
	}

	public WorldgenSource(ServerLevel level, long version, Materials materials) {
		this.materials = materials;
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.biomes = this.generator.getBiomeSource();
		this.resolvers = ThreadLocal.withInitial(() -> this.biomes.createCachingResolver(this.randomState));
		this.looks = new BiomeLooks(level, materials);
		this.seaLevel = this.generator.getSeaLevel();
		this.floating = this.generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().value().defaultFluid().isAir();
		this.sea = !this.floating;
		this.version = version;
		int[] strata = null;
		int[] strataMaterials = null;
		Noise offset = null;
		try {
			MaterialSystemAccessor system = (MaterialSystemAccessor) this.randomState.surfaceSystem();
			BlockState[] bands = system.voidLod$clayBands();
			strata = new int[bands.length];
			for (int i = 0; i < bands.length; i++) {
				strata[i] = this.looks.colorOf(bands[i]);
			}
			strataMaterials = this.looks.materials(bands, false);
			offset = system.voidLod$clayBandsOffsetNoise();
		} catch (RuntimeException e) {
			// a generator without vanilla's material system: the looks' own strata stand in
		}
		this.strata = strata;
		this.strataMaterials = strataMaterials;
		this.strataOffset = offset;
	}

	@Override
	public long version() {
		return this.version;
	}

	public Materials materials() {
		return this.materials;
	}

	/** Whether this world's land floats over the void (the End): it has no sea for the haze to settle toward. */
	public boolean floating() {
		return this.floating;
	}

	/** Anything about the source worth a line in the statistics. */
	public String diagnostics() {
		return "";
	}

	protected Holder<Biome> biome(int x, int y, int z) {
		return this.resolvers.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	/** Per worker: a small direct-mapped cache of biomes shared by the samples of a far square. */
	private static final class BiomeCache {
		final long[] keys = new long[512];
		final int[] cells = new int[512];
		@SuppressWarnings("unchecked")
		final Holder<Biome>[] biomes = new Holder[512];
	}

	private final ThreadLocal<BiomeCache> biomeCache = ThreadLocal.withInitial(BiomeCache::new);

	/**
	 * The biome for a sample of a cell this wide. Far out, where a cell is 8 blocks or more, the samples of each
	 * square two cells across share one lookup: biomes change over hundreds of blocks, their borders are
	 * softened at this scale anyway, and the lookup is a third of what a far sample costs. The lookup is the
	 * square's first sample's own (its corner, at its height), whose column the terrain model has just worked
	 * out, so the climate behind the biome comes from its cache.
	 */
	protected Holder<Biome> biome(int x, int y, int z, int cell) {
		if (cell < 8) {
			return this.biome(x, y, z);
		}
		int grid = 2 * cell;
		int qx = Math.floorDiv(x, grid);
		int qz = Math.floorDiv(z, grid);
		long key = (long) qx << 32 ^ qz & 0xFFFFFFFFL;
		BiomeCache cache = this.biomeCache.get();
		int slot = (qx * 0x9E3779B1 ^ qz * 0x85EBCA77) >>> 23;
		Holder<Biome> biome = cache.biomes[slot];
		if (biome == null || cache.keys[slot] != key || cache.cells[slot] != cell) {
			biome = this.biome(x, y, z);
			cache.biomes[slot] = biome;
			cache.keys[slot] = key;
			cache.cells[slot] = cell;
		}
		return biome;
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

	/** Snow on the boughs where it lies on the ground: the crowns' tops white, as far as the snow goes. */
	protected void boughs(ColumnSample out, float snow) {
		if (out.canopy > 0) {
			out.leavesTop = Colors.mix(out.leaves, this.looks.snowTop, snow * 0.9F);
		}
	}

	/** Badlands walls in the world's own strata, where it has them. */
	protected void strata(int x, int z, ColumnSample out) {
		if (out.bands != null && this.strata != null) {
			out.bands = this.strata;
			out.bandMaterials = this.strataMaterials;
			out.bandHeight = 1;
			out.bandOffset = this.strataOffset == null ? 0 : Math.round((float) this.strataOffset.get(x, 0, z) * 4);
		}
	}

	/**
	 * Over the void, the column's top and the underside of the ground it stands on, read from the whole column:
	 * {@code out.bottom} gets the underside. Returns the top, or the world's floor for a column with no ground.
	 */
	private int island(int x, int z, ColumnSample out) {
		NoiseColumn column = this.generator.getBaseColumn(x, z, this.level, this.randomState);
		var solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
		int floor = this.level.getMinY();
		int y = this.level.getMaxY();
		while (y >= floor && !solid.test(column.getBlock(y))) {
			y--;
		}
		if (y < floor) {
			return floor;
		}
		int under = y;
		while (under > floor && solid.test(column.getBlock(under - 1))) {
			under--;
		}
		out.bottom = under;
		return y + 1;
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		int ground = this.floating ? this.island(x, z, out)
			: this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.level, this.randomState);
		if (ground <= this.level.getMinY()) {
			// nothing at all down to the bottom of the world: the void
			out.empty = true;
			out.ground = ground;
			return;
		}
		Holder<Biome> biome = this.biome(x, ground, z, cell);
		BiomeLooks.Look look = this.looks.get(biome);
		look.fill(out);
		out.ground = ground;
		this.strata(x, z, out);
		if (ground < this.seaLevel && this.sea) {
			out.water = this.seaLevel;
			out.top = look.bed();
			out.canopy = 0;
			out.cover = 0;
			return;
		}
		if (out.bands != null) {
			out.side = ColumnSample.rockAt(out.bands, out.bandHeight, out.rock, ground - 1 + out.bandOffset);
			out.sideMaterial = ColumnSample.rockMaterialAt(out.bandMaterials, out.bandHeight, out.rockMaterial, ground - 1 + out.bandOffset);
		}
		float snow = look.ground() == BiomeLooks.Ground.SNOWCAP ? 0 : this.snow(biome, x, ground, z, cell);
		if (snow > 0) {
			out.top = Colors.mix(out.top, this.looks.snowTop, snow);
			if (snow >= 0.5F) {
				out.topMaterial = this.looks.snowTopMaterial;
			}
			if (look.ground() == BiomeLooks.Ground.GRASS) {
				out.side = Colors.mix(out.side, this.looks.snowSide, snow);
				if (snow >= 0.5F) {
					out.sideMaterial = this.looks.snowSideMaterial;
				}
			}
			this.boughs(out, snow);
		} else if (this.sea && ground <= this.seaLevel + 1 && look.ground() == BiomeLooks.Ground.GRASS) {
			// most shores are sand, whatever the biome inland
			out.top = out.side = out.soil = this.looks.sand;
			out.topMaterial = this.looks.sandTopMaterial;
			out.sideMaterial = out.soilMaterial = this.looks.sandSideMaterial;
		}
	}
}
