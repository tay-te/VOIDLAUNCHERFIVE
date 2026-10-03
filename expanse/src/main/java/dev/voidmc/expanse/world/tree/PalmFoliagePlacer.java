package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * Fronds radiating from the top of a palm in eight directions, running level and then drooping at
 * the tip. Diagonal fronds step x then z so each leaf shares a face with the last (leaves that only
 * meet at an edge do not count as connected and would decay), which makes them shorter — as on a
 * real crown, where the fronds are not all the same length.
 */
public class PalmFoliagePlacer extends FoliagePlacer {
	public static final MapCodec<PalmFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i)
		.and(Codec.floatRange(0.0F, 1.0F).fieldOf("missing_frond_chance").forGetter(p -> p.missingChance))
		.apply(i, PalmFoliagePlacer::new));

	private static final int MAX_LEAF_DISTANCE = 6;
	private final float missingChance;

	public PalmFoliagePlacer(IntProvider radius, IntProvider offset, float missingChance) {
		super(radius, offset);
		this.missingChance = missingChance;
	}

	@Override
	protected FoliagePlacerType<?> type() {
		return ExpanseTreePlacers.PALM_FOLIAGE;
	}

	@Override
	protected void createFoliage(
		WorldGenLevel level, FoliageSetter setter, RandomSource random, TreeFeature tree, int treeHeight, FoliageAttachment attachment,
		int foliageHeight, int leafRadius, int offset
	) {
		BlockPos top = attachment.pos();
		BlockPos crown = top.above();
		tryPlaceLeaf(level, setter, random, tree, crown);
		if (random.nextBoolean()) {
			tryPlaceLeaf(level, setter, random, tree, crown.above());
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0 || random.nextFloat() < this.missingChance) {
					continue;
				}
				boolean diagonal = dx != 0 && dz != 0;
				int len = leafRadius + random.nextInt(2) - (diagonal ? 2 : 0);
				BlockPos cur = crown;
				int steps = 1;
				for (int i = 1; i <= len && steps < MAX_LEAF_DISTANCE; i++) {
					if (dx != 0) {
						cur = cur.offset(dx, 0, 0);
						steps++;
						tryPlaceLeaf(level, setter, random, tree, cur);
					}
					if (dz != 0 && steps < MAX_LEAF_DISTANCE) {
						cur = cur.offset(0, 0, dz);
						steps++;
						tryPlaceLeaf(level, setter, random, tree, cur);
					}
					if (i >= len - 1 && steps < MAX_LEAF_DISTANCE) {
						cur = cur.below();
						steps++;
						tryPlaceLeaf(level, setter, random, tree, cur);
					}
				}
			}
		}
	}

	@Override
	public int foliageHeight(RandomSource random, int treeHeight, TreeFeature tree) {
		return 2;
	}

	@Override
	protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
		return false;
	}
}
