package dev.voidmc.expanse.world.terrain;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code expanse:earth}: the overworld's chunk generator.
 *
 * <p>The shape of the land comes from the {@link TerrainModel}, which reaches the game through the noise
 * settings' router as {@code expanse:terrain} density functions; vanilla's noise machinery then does
 * what it already does well (caves, aquifers, ore veins, surface rules, carvers), unchanged. That part
 * is delegated to a {@link NoiseBasedChunkGenerator}, which is final, so this wraps one rather than
 * extending it.
 *
 * <p>What the noise machinery cannot do is water above sea level: its aquifers fill to the sea or to
 * levels of their own underground. So after it has built a chunk, this cuts each river's channel to the
 * block, fills it with water at the river's own level (falling stretch by stretch toward the sea, in
 * small rapids where it steps down), firms up the banks, and lays a bed of gravel, sand and clay. The
 * base-height queries that structures use to find dry ground are told about the water too.
 */
public final class EarthChunkGenerator extends ChunkGenerator {
	public static final MapCodec<EarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
		NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(g -> g.settings)
	).apply(i, i.stable(EarthChunkGenerator::new)));

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState[] BED = {Blocks.GRAVEL.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.CLAY.defaultBlockState()};
	private static final BlockState BANK = Blocks.DIRT.defaultBlockState();

	private final Holder<NoiseGeneratorSettings> settings;
	private final NoiseBasedChunkGenerator noise;
	private volatile long modelWorldSeed;
	private volatile TerrainModel model;

	public EarthChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
		super(biomeSource);
		this.settings = settings;
		this.noise = new NoiseBasedChunkGenerator(biomeSource, settings);
	}

	public Holder<NoiseGeneratorSettings> generatorSettings() {
		return this.settings;
	}

	public TerrainModel model(RandomState randomState) {
		TerrainModel m = this.model;
		long worldSeed = randomState.seed();
		if (m == null || this.modelWorldSeed != worldSeed) {
			m = TerrainModel.forSeed(TerrainModel.seedFor(worldSeed));
			this.model = m;
			this.modelWorldSeed = worldSeed;
		}
		return m;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	protected BiomeResolver decorateBiomeResolver(Blender blender, ChunkAccess protoChunk, BiomeResolver biomeResolver) {
		return BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(biomeResolver), protoChunk);
	}

	@Override
	public ChunkPos getOrigin(RandomState randomState) {
		return this.noise.getOrigin(randomState);
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
		BiomeManager biomeManager, WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
		TerrainModel m = this.model(randomState);
		return this.noise.buildTerrain(chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes)
			.thenApply(built -> {
				this.pourRivers(built, m);
				this.pourUndergroundRivers(built, m.caverns());
				return built;
			});
	}

	private void pourRivers(ChunkAccess chunk, TerrainModel m) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int minY = chunk.getMinY();
		boolean changed = false;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = pos.getMinBlockX() + lx;
				int z = pos.getMinBlockZ() + lz;
				Column c = m.sample(x, z);
				if (!c.nearRiver() || c.riverDist >= c.riverHalfWidth + 2) {
					continue;
				}
				int top = c.waterTop();
				boolean channel = c.inChannel();
				int bed = c.bed();
				boolean level = m.levelShore(x, z);
				changed = true;
				if (channel) {
					// clear the valley floor above the water, then fill the channel
					for (int y = top + 3; y >= top; y--) {
						if (!chunk.getBlockState(p.set(x, y, z)).isAir()) {
							chunk.setBlockState(p, AIR);
						}
					}
					for (int y = top - 1; y >= bed && y > minY; y--) {
						chunk.setBlockState(p.set(x, y, z), WATER);
					}
					BlockState under = chunk.getBlockState(p.set(x, bed - 1, z));
					if (!under.isAir() && under.getFluidState().isEmpty()) {
						chunk.setBlockState(p, BED[m.bedKind(x, z)]);
					}
					// where the river steps down to a lower stretch, let it run: a small rapid
					if (this.stepsDown(m, x, z, top)) {
						chunk.markPosForPostProcessing(p.set(x, top - 1, z));
					}
				} else {
					// the bank: solid up to the lip, so the river stays in its bed
					for (int y = top; y >= top - 3 && y > minY; y--) {
						BlockState s = chunk.getBlockState(p.set(x, y, z));
						if (s.isAir() || !s.getFluidState().isEmpty()) {
							chunk.setBlockState(p, BANK);
						}
					}
					// In places the first ring of bank sits level with the water instead of a block above it:
					// a shore where reeds and sugar cane can grow. It still holds the water, which can only
					// spread sideways into air at its own level.
					boolean shore = level && c.riverDist < c.riverHalfWidth + 1.2F;
					BlockState lip = chunk.getBlockState(p.set(x, top, z));
					if (shore && !lip.isAir() && lip.getFluidState().isEmpty() && chunk.getBlockState(p.set(x, top + 1, z)).isAir()) {
						chunk.setBlockState(p.set(x, top - 1, z), lip);
						chunk.setBlockState(p.set(x, top, z), AIR);
					}
				}
			}
		}
		if (changed) {
			Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
		}
	}

	/**
	 * The rivers of the caverns: the same treatment as on the surface, in their tunnels. A column is only
	 * touched where its tunnel is really open (air just above the water), so a river never fills solid rock.
	 */
	private void pourUndergroundRivers(ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int minY = chunk.getMinY();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = pos.getMinBlockX() + lx;
				int z = pos.getMinBlockZ() + lz;
				CavernModel.Info c = caverns.info(x, z);
				if (!c.river || c.riverFalls || c.riverDist >= c.riverHalfWidth + 2) {
					continue;
				}
				int top = c.waterTop();
				int bed = c.bed();
				boolean channel = c.inChannel();
				if (!chunk.getBlockState(p.set(x, top + 1, z)).isAir() && !chunk.getBlockState(p.set(x, top + 2, z)).isAir()) {
					continue;
				}
				if (channel) {
					for (int y = top; y <= top + 1; y++) {
						BlockState s = chunk.getBlockState(p.set(x, y, z));
						if (!s.isAir() && s.getFluidState().isEmpty()) {
							chunk.setBlockState(p, AIR);
						}
					}
					for (int y = top - 1; y >= bed && y > minY; y--) {
						chunk.setBlockState(p.set(x, y, z), WATER);
					}
					BlockState under = chunk.getBlockState(p.set(x, bed - 1, z));
					if (under.isAir() || !under.getFluidState().isEmpty()) {
						chunk.setBlockState(p, BED[0]);
					}
					// where it steps down a level or pours out into a hall, let it run
					if (this.fallsAway(caverns, x, z, top)) {
						chunk.markPosForPostProcessing(p.set(x, top - 1, z));
					}
				} else {
					for (int y = top; y >= top - 3 && y > minY; y--) {
						BlockState s = chunk.getBlockState(p.set(x, y, z));
						if (s.isAir() || !s.getFluidState().isEmpty()) {
							chunk.setBlockState(p, Blocks.STONE.defaultBlockState());
						}
					}
				}
			}
		}
	}

	private boolean fallsAway(CavernModel caverns, int x, int z, int top) {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : around) {
			CavernModel.Info n = caverns.info(x + o[0], z + o[1]);
			if (n.river && (n.riverFalls || n.inChannel() && n.waterTop() < top)) {
				return true;
			}
		}
		return false;
	}

	private boolean stepsDown(TerrainModel m, int x, int z, int top) {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : around) {
			Column n = m.sample(x + o[0], z + o[1]);
			if (n.inChannel() && n.waterTop() < top) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		super.applyBiomeDecoration(level, chunk, structureManager);
		if (level instanceof WorldGenRegion region) {
			CavernLife.decorate(level, chunk, this.model(region.getLevel().getChunkSource().randomState()).caverns());
		}
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		int base = this.noise.getBaseHeight(x, z, type, heightAccessor, randomState);
		Column c = this.model(randomState).sample(x, z);
		if (!c.inChannel()) {
			return base;
		}
		// in a river channel: the water counts as surface, the bed as the floor
		return switch (type) {
			case OCEAN_FLOOR, OCEAN_FLOOR_WG -> Math.min(base, c.bed());
			default -> c.waterTop();
		};
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		NoiseColumn column = this.noise.getBaseColumn(x, z, heightAccessor, randomState);
		Column c = this.model(randomState).sample(x, z);
		if (c.inChannel()) {
			int top = c.waterTop();
			int bed = c.bed();
			int minY = heightAccessor.getMinY();
			int maxY = heightAccessor.getMaxY();
			for (int y = Math.max(minY, bed); y <= Math.min(maxY, top + 3); y++) {
				column.setBlock(y, y < top ? WATER : AIR);
			}
		}
		return column;
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
		this.noise.addDebugScreenInfo(result, randomState, feetPos, samplerContext);
		Column c = this.model(randomState).sample(feetPos.getX(), feetPos.getZ());
		result.add(String.format(Locale.ROOT, "Expanse plate %d,%d %s · uplift %.2f · coast %.0f · above river %.0f%s",
			c.plateX, c.plateZ, c.continental ? "continental" : "oceanic", c.uplift, c.coast, c.aboveRiver,
			c.nearRiver() ? String.format(Locale.ROOT, " · river %.0f wide, water %.1f, %.1f away", c.riverHalfWidth * 2, c.riverWater, c.riverDist) : ""));
	}

	@Override
	public int getGenDepth() {
		return this.noise.getGenDepth();
	}

	@Override
	public int getSeaLevel() {
		return this.noise.getSeaLevel();
	}

	@Override
	public int getMinY() {
		return this.noise.getMinY();
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
		this.noise.spawnOriginalMobs(worldGenRegion);
	}
}
