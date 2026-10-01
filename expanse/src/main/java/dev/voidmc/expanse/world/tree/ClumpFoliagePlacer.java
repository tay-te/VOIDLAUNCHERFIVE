package dev.voidmc.expanse.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * Squashed spheres of leaves, one per attachment, sized by the attachment's radius offset — so the
 * trunk placer, not this class, decides the tree's silhouette. An attachment with a
 * {@code foliageHeightOffset} is instead a cone of that height, wrapped round the trunk.
 */
public class ClumpFoliagePlacer extends FoliagePlacer {
	public static final MapCodec<ClumpFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i)
		.and(i.group(
			Codec.intRange(0, 6).fieldOf("height").forGetter(p -> p.height),
			Codec.floatRange(0.0F, 1.0F).fieldOf("edge_hole_chance").forGetter(p -> p.holeChance)
		))
		.apply(i, ClumpFoliagePlacer::new));

	private final int height;
	private final float holeChance;

	public ClumpFoliagePlacer(IntProvider radius, IntProvider offset, int height, float holeChance) {
		super(radius, offset);
		this.height = height;
		this.holeChance = holeChance;
	}

	@Override
	protected FoliagePlacerType<?> type() {
		return ExpanseTreePlacers.CLUMP_FOLIAGE;
	}

	@Override
	protected void createFoliage(
		WorldGenLevel level, FoliageSetter setter, RandomSource random, TreeFeature tree, int treeHeight, FoliageAttachment attachment,
		int foliageHeight, int leafRadius, int offset
	) {
		BlockPos c = attachment.pos();
		int r = Math.max(1, leafRadius + attachment.radiusOffsetXZ());
		if (attachment.foliageHeightOffset() > 0) {
			int h = attachment.foliageHeightOffset();
			for (int y = -1; y <= h; y++) {
				float t = (y + 1F) / (h + 1F);
				this.disc(level, setter, random, tree, c.above(y), Mth.lerp(t, r + 0.6F, 0.4F));
			}
			return;
		}
		float half = this.height + 0.5F;
		for (int y = -1; y <= this.height; y++) {
			float rr = y < 0 ? r - 0.6F : r * Mth.sqrt(Math.max(0.0F, 1.0F - y * y / (half * half))) + 0.35F;
			this.disc(level, setter, random, tree, c.above(y + offset), rr);
		}
	}

	private void disc(WorldGenLevel level, FoliageSetter setter, RandomSource random, TreeFeature tree, BlockPos centre, float rr) {
		int ir = Mth.ceil(rr);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				float d2 = dx * dx + dz * dz;
				if (d2 > rr * rr) {
					continue;
				}
				if (d2 > (rr - 1) * (rr - 1) && random.nextFloat() < this.holeChance) {
					continue;
				}
				tryPlaceLeaf(level, setter, random, tree, p.setWithOffset(centre, dx, 0, dz));
			}
		}
	}

	@Override
	public int foliageHeight(RandomSource random, int treeHeight, TreeFeature tree) {
		return this.height;
	}

	@Override
	protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
		return false;
	}
}
