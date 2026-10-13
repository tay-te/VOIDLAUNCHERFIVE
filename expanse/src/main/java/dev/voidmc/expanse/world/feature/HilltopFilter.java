package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * {@code expanse:hilltop}: only on the crest of a rise in open country ({@link TerrainModel#crest}), where a lone tree
 * stands against the sky and is seen for miles. Asks the terrain model only, as the land filter does. Off the
 * Earth terrain, nowhere.
 */
public final class HilltopFilter implements PlacementFilter {
	public static final HilltopFilter INSTANCE = new HilltopFilter();
	public static final MapCodec<HilltopFilter> CODEC = MapCodec.unit(INSTANCE);

	private HilltopFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		return context.generator() instanceof EarthChunkGenerator earth && earth.cachedModel() != null
			&& earth.cachedModel().crest(origin.getX(), origin.getZ());
	}

	@Override
	public MapCodec<HilltopFilter> codec() {
		return CODEC;
	}
}
