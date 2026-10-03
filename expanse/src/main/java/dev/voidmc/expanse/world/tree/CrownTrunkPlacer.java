package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.Codec;
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
 * A short trunk that wanders as it rises and then opens into a ring of arching branches — the willow,
 * and with more wander, the wisteria. Each branch end is a foliage attachment, so the canopy is a
 * ring of domes rather than one ball, which is what lets the curtains hang all the way round.
 */
public class CrownTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<CrownTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(i.group(
			IntProviders.codec(1, 8).fieldOf("branch_count").forGetter(p -> p.branchCount),
			IntProviders.codec(1, 6).fieldOf("branch_length").forGetter(p -> p.branchLength),
			Codec.floatRange(0.0F, 1.0F).fieldOf("wander").forGetter(p -> p.wander)
		))
		.apply(i, CrownTrunkPlacer::new));

	private final IntProvider branchCount;
	private final IntProvider branchLength;
	private final float wander;

	public CrownTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, IntProvider branchCount, IntProvider branchLength, float wander) {
		super(baseHeight, heightRandA, heightRandB);
		this.branchCount = branchCount;
		this.branchLength = branchLength;
		this.wander = wander;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return ExpanseTreePlacers.CROWN_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		placeBelowTrunkBlock(level, setter, random, origin.below(), tree);
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();

		BlockPos cur = origin;
		logs.place(cur, Direction.Axis.Y);
		for (int y = 1; y < height; y++) {
			BlockPos next = cur.above();
			if (y > 1 && random.nextFloat() < this.wander) {
				Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
				BlockPos moved = next.relative(d);
				// Never wander more than two blocks from the root, or the tree tips over visually.
				if (Math.abs(moved.getX() - origin.getX()) <= 2 && Math.abs(moved.getZ() - origin.getZ()) <= 2) {
					logs.place(cur.relative(d), d.getAxis());
					next = moved;
				}
			}
			logs.place(next, Direction.Axis.Y);
			cur = next;
		}
		crown.add(new FoliagePlacer.FoliageAttachment(cur.above(), 0, 0, 1, 1));

		int n = this.branchCount.sample(random);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int b = 0; b < n; b++) {
			angle += Mth.TWO_PI / n + (random.nextFloat() - 0.5F) * 0.6F;
			int len = this.branchLength.sample(random);
			BlockPos from = cur.below(random.nextInt(Math.min(3, height - 1)));
			BlockPos tip = BlockPos.containing(
				from.getX() + 0.5 + Mth.cos(angle) * len, from.getY() + 1 + random.nextInt(2), from.getZ() + 0.5 + Mth.sin(angle) * len);
			BlockPos end = Logs.line(logs, from, tip);
			crown.add(new FoliagePlacer.FoliageAttachment(end, 0, 0, 1, 1));
		}
		return crown;
	}
}
