package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.TerrainSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

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
	private final long version;

	public WorldgenSource(ServerLevel level, long version) {
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.biomes = this.generator.getBiomeSource();
		this.resolvers = ThreadLocal.withInitial(() -> this.biomes.createCachingResolver(this.randomState));
		this.looks = new BiomeLooks(level);
		this.seaLevel = this.generator.getSeaLevel();
		this.version = version;
	}

	@Override
	public long version() {
		return this.version;
	}

	protected Holder<Biome> biome(int x, int y, int z) {
		return this.resolvers.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		int ground = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.level, this.randomState);
		Holder<Biome> biome = this.biome(x, ground, z);
		BiomeLooks.Look look = this.looks.get(biome);
		out.ground = ground;
		out.groundColor = look.groundColor();
		out.waterColor = look.waterColor();
		if (ground < this.seaLevel) {
			out.water = this.seaLevel;
			return;
		}
		if (biome.value().coldEnoughToSnow(new BlockPos(x, ground, z), this.seaLevel)) {
			out.groundColor = BiomeLooks.snow();
		} else if (ground <= this.seaLevel + 1 && look.ground() == BiomeLooks.Ground.GRASS) {
			// most shores are sand, whatever the biome inland
			out.groundColor = BiomeLooks.sand();
		}
		out.canopy = look.canopy();
		out.cover = look.cover();
		out.canopyColor = look.canopyColor();
	}
}
