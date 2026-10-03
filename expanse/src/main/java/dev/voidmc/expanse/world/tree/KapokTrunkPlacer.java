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
 * A kapok, the emergent of the rainforest: plank buttresses fanning out round the foot, a long bare
 * column standing clear of the canopy, and at the top a few near-level limbs that spread a flat
 * umbrella of leaf pads over everything else. A second, shorter tier of limbs a few blocks lower gives
 * the crown some depth from below.
 *
 * <p>{@code trunk_width} 1 is the young tree a single sapling grows, 2 the giant of four saplings and of
 * the world generator.
 */
public class KapokTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<KapokTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(i.group(
			Codec.intRange(1, 2).fieldOf("trunk_width").forGetter(p -> p.width),
			IntProviders.codec(0, 12).fieldOf("buttress_count").forGetter(p -> p.buttressCount),
			IntProviders.codec(1, 6).fieldOf("buttress_length").forGetter(p -> p.buttressLength),
			IntProviders.codec(2, 8).fieldOf("limb_count").forGetter(p -> p.limbCount),
			IntProviders.codec(2, 10).fieldOf("limb_length").forGetter(p -> p.limbLength)
		))
		.apply(i, KapokTrunkPlacer::new));

	private final int width;
	private final IntProvider buttressCount;
	private final IntProvider buttressLength;
	private final IntProvider limbCount;
	private final IntProvider limbLength;

	public KapokTrunkPlacer(
		int baseHeight, int heightRandA, int heightRandB, int width, IntProvider buttressCount, IntProvider buttressLength, IntProvider limbCount,
		IntProvider limbLength
	) {
		super(baseHeight, heightRandA, heightRandB);
		this.width = width;
		this.buttressCount = buttressCount;
		this.buttressLength = buttressLength;
		this.limbCount = limbCount;
		this.limbLength = limbLength;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return WarmTreePlacers.KAPOK_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();
		for (int dx = 0; dx < this.width; dx++) {
			for (int dz = 0; dz < this.width; dz++) {
				placeBelowTrunkBlock(level, setter, random, origin.offset(dx, -1, dz), tree);
				for (int y = 0; y < height; y++) {
					this.placeLog(level, setter, random, origin.offset(dx, y, dz), tree);
				}
			}
		}
		this.buttresses(level, logs, random, origin, height);

		double cx = origin.getX() + this.width / 2.0;
		double cz = origin.getZ() + this.width / 2.0;
		BlockPos top = origin.above(height - 1);
		// The umbrella: limbs leaving the top nearly level, each ending in a wide pad, with a smaller pad
		// part-way along so the canopy reads as one sheet rather than a ring of islands.
		int n = this.limbCount.sample(random);
		float angle = random.nextFloat() * Mth.TWO_PI;
		for (int b = 0; b < n; b++) {
			angle += Mth.TWO_PI / n + (random.nextFloat() - 0.5F) * 0.5F;
			int len = this.limbLength.sample(random) + this.width - 1;
			BlockPos from = top.below(random.nextInt(2));
			double reach = this.width / 2.0 + len;
			BlockPos tip = BlockPos.containing(cx + Mth.cos(angle) * reach, from.getY() + 1 + random.nextInt(2), cz + Mth.sin(angle) * reach);
			BlockPos end = Logs.line(logs, from, tip);
			crown.add(new FoliagePlacer.FoliageAttachment(end, 1, 0, 1, 1));
			if (len >= 4) {
				BlockPos mid = BlockPos.containing(cx + Mth.cos(angle) * reach * 0.55, from.getY() + 1, cz + Mth.sin(angle) * reach * 0.55);
				crown.add(new FoliagePlacer.FoliageAttachment(mid, 0, 0, 1, 1));
			}
		}
		crown.add(new FoliagePlacer.FoliageAttachment(top.above(), this.width, 0, 1, 1));

		// The lower tier: shorter limbs a few blocks down, between the upper ones.
		if (height > 14) {
			BlockPos low = top.below(4 + random.nextInt(3));
			int m = Math.max(2, n - 2);
			float a = angle + Mth.PI / m;
			for (int b = 0; b < m; b++) {
				a += Mth.TWO_PI / m + (random.nextFloat() - 0.5F) * 0.6F;
				int len = Math.max(2, this.limbLength.sample(random) * 2 / 3);
				double reach = this.width / 2.0 + len;
				BlockPos tip = BlockPos.containing(cx + Mth.cos(a) * reach, low.getY() + 1 + random.nextInt(2), cz + Mth.sin(a) * reach);
				crown.add(new FoliagePlacer.FoliageAttachment(Logs.line(logs, low, tip), 0, 0, 1, 1));
			}
		}
		return crown;
	}

	/**
	 * Plank buttresses: thin fins of trunk running out from each face and down into the ground, tallest
	 * against the trunk and tapering to the tip, so the tree looks braced against the soft ground.
	 */
	private void buttresses(WorldGenLevel level, Logs.Placer logs, RandomSource random, BlockPos origin, int height) {
		int n = this.buttressCount.sample(random);
		for (int f = 0; f < n; f++) {
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int along = random.nextInt(this.width);
			int out = this.buttressLength.sample(random) + this.width - 1;
			int rise = Math.min(height / 3, 2 + out + random.nextInt(3));
			BlockPos edge = origin.offset(
				dir.getStepX() > 0 ? this.width - 1 : (dir.getStepX() < 0 ? 0 : along),
				0,
				dir.getStepZ() > 0 ? this.width - 1 : (dir.getStepZ() < 0 ? 0 : along));
			for (int o = 1; o <= out; o++) {
				BlockPos p = edge.relative(dir, o);
				int h = Math.round(rise * (1.0F - (float) o / (out + 1)));
				for (int y = h; y >= -5; y--) {
					BlockPos q = p.above(y);
					if (y < 0 && !TreeFeature.validTreePos(level, q)) {
						break;
					}
					logs.place(q, Direction.Axis.Y);
				}
			}
		}
	}
}
