package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
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
 * A baobab: a bottle of a trunk, three wide and swelling at the foot, with a short crown of thick
 * branches that each carry a flat pad of leaves. It is the silhouette that makes a savanna read as
 * one from a kilometre away, so the trunk is deliberately oversized.
 */
public class BaobabTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<BaobabTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(IntProviders.codec(2, 8).fieldOf("branch_count").forGetter(p -> p.branchCount))
		.apply(i, BaobabTrunkPlacer::new));

	private final IntProvider branchCount;

	public BaobabTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, IntProvider branchCount) {
		super(baseHeight, heightRandA, heightRandB);
		this.branchCount = branchCount;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return ExpanseTreePlacers.BAOBAB_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				placeBelowTrunkBlock(level, setter, random, origin.offset(dx, -1, dz), tree);
			}
		}
		for (int y = 0; y < height; y++) {
			float t = (float) y / height;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean corner = dx != 0 && dz != 0;
					// Full square at the swollen foot, a rounded column above, corners returning near the top.
					if (corner && t > 0.35F && t < 0.8F && random.nextInt(3) != 0) {
						continue;
					}
					logs.place(origin.offset(dx, y, dz), Direction.Axis.Y);
				}
			}
		}
		// The foot flares out a block on every side, then into the ground.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int along = -1; along <= 1; along++) {
				if (random.nextInt(3) == 0) {
					continue;
				}
				BlockPos p = origin.relative(d, 2).relative(d.getClockWise(), along);
				int h = random.nextInt(3);
				for (int y = h; y >= -3; y--) {
					BlockPos q = p.above(y);
					if (y < 0 && !TreeFeature.validTreePos(level, q)) {
						break;
					}
					logs.place(q, Direction.Axis.Y);
				}
			}
		}

		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();
		BlockPos top = origin.above(height - 1);
		int n = this.branchCount.sample(random);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int b = 0; b < n; b++) {
			angle += Mth.TWO_PI / n + (random.nextFloat() - 0.5F) * 0.5F;
			int len = 3 + random.nextInt(3);
			BlockPos tip = BlockPos.containing(
				top.getX() + 0.5 + Mth.cos(angle) * len, top.getY() + 2 + random.nextInt(3), top.getZ() + 0.5 + Mth.sin(angle) * len);
			BlockPos end = Logs.line(logs, top, tip);
			crown.add(new FoliagePlacer.FoliageAttachment(end.above(), 0, 0, 1, 1));
		}
		crown.add(new FoliagePlacer.FoliageAttachment(top.above(), -1, 0, 1, 1));
		return crown;
	}
}
