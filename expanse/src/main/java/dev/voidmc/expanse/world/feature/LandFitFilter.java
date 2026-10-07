package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * {@code expanse:land_fit}: lets a tree grow where the land would have one ({@link TerrainModel#woodland}): woods
 * on the hills and slopes with clean edges, open fields and meadows in the vales, the country's own share of each,
 * a treeline up high. Every natural tree carries it, so the land reads from far off as it does in Bohemia: dark
 * woods on the rises, light open country between. Off the Earth terrain it lets everything through.
 *
 * <p>Asks the terrain model only, never the level, so it works wherever features run (the LOD grows far trees
 * in a stand-in level).
 */
public final class LandFitFilter implements PlacementFilter {
	public static final LandFitFilter INSTANCE = new LandFitFilter();
	public static final MapCodec<LandFitFilter> CODEC = MapCodec.unit(INSTANCE);

	private LandFitFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		if (!(context.generator() instanceof EarthChunkGenerator earth) || earth.cachedModel() == null) {
			return true;
		}
		return random.nextFloat() < earth.cachedModel().woodland(origin.getX(), origin.getZ());
	}

	@Override
	public MapCodec<LandFitFilter> codec() {
		return CODEC;
	}
}
