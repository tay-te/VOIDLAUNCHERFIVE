package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * A palm's trunk: it rises straight and then curves away, the lean growing with the square of the
 * height, which is the shape of a trunk that has spent its life bending into an onshore wind.
 */
public class PalmTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<PalmTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(IntProviders.codec(0, 8).fieldOf("bend").forGetter(p -> p.bend))
		.apply(i, PalmTrunkPlacer::new));

	private final IntProvider bend;

	public PalmTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, IntProvider bend) {
		super(baseHeight, heightRandA, heightRandB);
		this.bend = bend;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return ExpanseTreePlacers.PALM_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		placeBelowTrunkBlock(level, setter, random, origin.below(), tree);
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int bend = this.bend.sample(random);
		BlockPos prev = origin;
		logs.place(prev, Direction.Axis.Y);
		for (int y = 1; y < height; y++) {
			float t = (float) y / (height - 1);
			int off = Mth.floor(bend * t * t + 0.5F);
			BlockPos p = origin.above(y).relative(lean, off);
			// Climb first, then step sideways, so the trunk shows a vertical log under each bend.
			if (p.getX() != prev.getX() || p.getZ() != prev.getZ()) {
				logs.place(prev.above(), Direction.Axis.Y);
				logs.place(p, Direction.Axis.Y);
			} else {
				logs.place(p, Direction.Axis.Y);
			}
			prev = p;
		}
		return List.of(new FoliagePlacer.FoliageAttachment(prev, 0, 0, 1, 1));
	}
}
