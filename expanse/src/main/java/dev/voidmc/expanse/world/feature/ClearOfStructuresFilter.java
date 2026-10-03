package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.world.StructureClearance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * Drops a placement within {@code margin} blocks (horizontally) of any surface structure, so trees and
 * terrain features leave buildings standing in a clearing instead of growing through them. The margin
 * should cover the feature's own reach: a karst tower is wide, a flower is not.
 */
public record ClearOfStructuresFilter(int margin) implements PlacementFilter {
	public static final MapCodec<ClearOfStructuresFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.intRange(0, 48).fieldOf("margin").forGetter(ClearOfStructuresFilter::margin)
	).apply(i, ClearOfStructuresFilter::new));

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		return StructureClearance.distance(StructureClearance.nearbyBoxes(context.getLevel()), origin.getX(), origin.getZ()) > this.margin;
	}

	@Override
	public MapCodec<ClearOfStructuresFilter> codec() {
		return CODEC;
	}
}
