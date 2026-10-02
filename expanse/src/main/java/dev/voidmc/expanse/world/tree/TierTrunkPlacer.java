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
 * A pagoda tree, the tropical almond of warm beaches: a straight trunk with whorls of level branches at
 * intervals up it, each tier shorter than the one below, every branch carrying a flat pad of leaves. From
 * the shore it reads as a stack of plates.
 */
public class TierTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<TierTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(i.group(
			IntProviders.codec(1, 6).fieldOf("tier_count").forGetter(p -> p.tierCount),
			IntProviders.codec(1, 5).fieldOf("tier_spacing").forGetter(p -> p.tierSpacing),
			IntProviders.codec(2, 7).fieldOf("branch_count").forGetter(p -> p.branchCount),
			IntProviders.codec(1, 6).fieldOf("branch_length").forGetter(p -> p.branchLength)
		))
		.apply(i, TierTrunkPlacer::new));

	private final IntProvider tierCount;
	private final IntProvider tierSpacing;
	private final IntProvider branchCount;
	private final IntProvider branchLength;

	public TierTrunkPlacer(
		int baseHeight, int heightRandA, int heightRandB, IntProvider tierCount, IntProvider tierSpacing, IntProvider branchCount, IntProvider branchLength
	) {
		super(baseHeight, heightRandA, heightRandB);
		this.tierCount = tierCount;
		this.tierSpacing = tierSpacing;
		this.branchCount = branchCount;
		this.branchLength = branchLength;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return WarmTreePlacers.TIER_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		placeBelowTrunkBlock(level, setter, random, origin.below(), tree);
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		for (int y = 0; y < height; y++) {
			logs.place(origin.above(y), Direction.Axis.Y);
		}
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();
		int tiers = this.tierCount.sample(random);
		int spacing = this.tierSpacing.sample(random);
		int y = Math.max(3, height - 1 - (tiers - 1) * spacing);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int t = 0; t < tiers && y < height; t++, y += spacing) {
			BlockPos hub = origin.above(y);
			int n = this.branchCount.sample(random);
			int len = Math.max(1, this.branchLength.sample(random) - t);
			for (int b = 0; b < n; b++) {
				angle += Mth.TWO_PI / n + (random.nextFloat() - 0.5F) * 0.5F;
				BlockPos tip = BlockPos.containing(hub.getX() + 0.5 + Mth.cos(angle) * len, hub.getY(), hub.getZ() + 0.5 + Mth.sin(angle) * len);
				BlockPos end = Logs.line(logs, hub, tip);
				crown.add(new FoliagePlacer.FoliageAttachment(end, len >= 3 ? 1 : 0, 0, 1, 1));
			}
			// half a turn between tiers, so the branches of one stand between those of the next
			angle += Mth.PI / n;
		}
		crown.add(new FoliagePlacer.FoliageAttachment(origin.above(height - 1), 0, 0, 1, 1));
		return crown;
	}
}
