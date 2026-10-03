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
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * A redwood: a long bare column with flared buttress roots, then a crown of short, level branches
 * that shorten as they climb, each carrying a clump of needles, and a spire at the top.
 *
 * <p>The foliage is laid out here rather than in the foliage placer, as attachments whose
 * {@code radiusOffsetXZ} shrinks with height: that is what turns a column of clumps into a cone.
 * The last attachment carries a {@code foliageHeightOffset}, which {@link ClumpFoliagePlacer} reads as
 * "draw a cone this tall around the trunk" rather than a clump.
 *
 * <p>{@code trunk_width} 1 is a young tree, 2 the giant grown from four saplings, 3 the old growth
 * only the world generator places.
 */
public class RedwoodTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<RedwoodTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
		.and(i.group(
			Codec.intRange(1, 3).fieldOf("trunk_width").forGetter(p -> p.width),
			Codec.floatRange(0.1F, 0.9F).fieldOf("crown_start").forGetter(p -> p.crownStart),
			Codec.intRange(0, 4).fieldOf("max_branch_length").forGetter(p -> p.maxBranchLength)
		))
		.apply(i, RedwoodTrunkPlacer::new));

	private final int width;
	private final float crownStart;
	private final int maxBranchLength;

	public RedwoodTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int width, float crownStart, int maxBranchLength) {
		super(baseHeight, heightRandA, heightRandB);
		this.width = width;
		this.crownStart = crownStart;
		this.maxBranchLength = maxBranchLength;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return ExpanseTreePlacers.REDWOOD_TRUNK;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, int height, BlockPos origin, TreeFeature tree
	) {
		List<FoliagePlacer.FoliageAttachment> crown = new ArrayList<>();
		Logs.Placer logs = Logs.placer(tree, level, setter, random);
		for (int dx = 0; dx < this.width; dx++) {
			for (int dz = 0; dz < this.width; dz++) {
				placeBelowTrunkBlock(level, setter, random, origin.offset(dx, -1, dz), tree);
			}
		}

		// The column. An old-growth trunk narrows in two steps so the spire is not three blocks wide.
		for (int y = 0; y < height; y++) {
			float t = (float) y / height;
			for (int dx = 0; dx < this.width; dx++) {
				for (int dz = 0; dz < this.width; dz++) {
					if (this.width == 3) {
						boolean corner = dx != 1 && dz != 1;
						if (corner && t > 0.55F || (dx != 1 || dz != 1) && t > 0.85F) {
							continue;
						}
					} else if (this.width == 2 && t > 0.9F && (dx != 0 || dz != 0)) {
						continue;
					}
					this.placeLog(level, setter, random, origin.offset(dx, y, dz), tree);
				}
			}
		}

		if (this.width > 1) {
			this.buttress(level, logs, random, origin);
		}

		// Branches: one every two or three blocks through the crown, turning a quarter-ish each time so
		// the clumps spiral round the trunk instead of stacking on one side. They stop where the spire
		// begins; the spire is a cone drawn around the last stretch of trunk, so every needle in it is
		// within a few blocks of a log and nothing decays.
		int start = Math.max(3, Mth.floor(height * this.crownStart));
		int spire = Math.max(4, (height - start) / 4);
		int spireBase = height - spire;
		float angle = random.nextFloat() * Mth.TWO_PI;
		double cx = origin.getX() + (this.width - 1) / 2.0;
		double cz = origin.getZ() + (this.width - 1) / 2.0;
		for (int y = start; y < spireBase; y += 2 + random.nextInt(2)) {
			float t = (float) (y - start) / Math.max(1, spireBase - start);
			angle += 1.6F + random.nextFloat() * 1.4F;
			int length = Math.max(1, Math.round(Mth.lerp(t, this.maxBranchLength, 1)) + random.nextInt(2));
			double reach = this.width / 2.0 + length;
			BlockPos base = BlockPos.containing(cx, origin.getY() + y, cz);
			BlockPos tip = BlockPos.containing(cx + Mth.cos(angle) * reach, origin.getY() + y + (length > 2 ? 1 : 0), cz + Mth.sin(angle) * reach);
			BlockPos end = Logs.line(logs, base, tip);
			int radius = Math.round(Mth.lerp(t, 1.0F, -1.0F));
			crown.add(new FoliagePlacer.FoliageAttachment(end, radius, 0, 1, 1));
		}

		BlockPos spireAt = BlockPos.containing(cx, origin.getY() + spireBase, cz);
		crown.add(new FoliagePlacer.FoliageAttachment(spireAt, this.width - 1, spire + 2, 1, 1));
		return crown;
	}

	/**
	 * Root flares: on each face of the trunk, a ridge of logs one to three high that runs out a block or
	 * two and then down into the ground, so a giant on a slope still looks planted.
	 */
	private void buttress(WorldGenLevel level, Logs.Placer logs, RandomSource random, BlockPos origin) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int flares = 1 + random.nextInt(this.width);
			for (int f = 0; f < flares; f++) {
				int along = random.nextInt(this.width);
				int out = 1 + random.nextInt(this.width == 3 ? 3 : 2);
				int rise = 1 + random.nextInt(this.width + 1);
				BlockPos edge = origin.offset(
					dir.getStepX() > 0 ? this.width - 1 : (dir.getStepX() < 0 ? 0 : along),
					0,
					dir.getStepZ() > 0 ? this.width - 1 : (dir.getStepZ() < 0 ? 0 : along));
				for (int o = 1; o <= out; o++) {
					BlockPos p = edge.relative(dir, o);
					int h = Math.max(0, rise - o);
					for (int y = h; y >= -4; y--) {
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
}
