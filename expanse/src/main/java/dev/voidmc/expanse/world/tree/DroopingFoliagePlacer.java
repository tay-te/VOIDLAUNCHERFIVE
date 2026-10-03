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
 * A low dome with curtains of leaves hanging from its rim: willow fronds, or wisteria racemes.
 *
 * <p>The curtains are leaves, and leaves decay more than six steps from a log, so each strand is cut
 * to whatever length keeps its tip within six face-steps of the branch it hangs from. That is why the
 * crown is a ring of small domes (one per branch, from {@link CrownTrunkPlacer}) rather than one big
 * one: small domes put every rim close enough to a log to hang a long strand.
 */
public class DroopingFoliagePlacer extends FoliagePlacer {
	public static final MapCodec<DroopingFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i)
		.and(i.group(
			Codec.intRange(0, 4).fieldOf("canopy_height").forGetter(p -> p.canopyHeight),
			Codec.floatRange(0.0F, 1.0F).fieldOf("hang_chance").forGetter(p -> p.hangChance),
			Codec.intRange(0, 6).fieldOf("max_hang").forGetter(p -> p.maxHang)
		))
		.apply(i, DroopingFoliagePlacer::new));

	private static final int MAX_LEAF_DISTANCE = 6;
	private final int canopyHeight;
	private final float hangChance;
	private final int maxHang;

	public DroopingFoliagePlacer(IntProvider radius, IntProvider offset, int canopyHeight, float hangChance, int maxHang) {
		super(radius, offset);
		this.canopyHeight = canopyHeight;
		this.hangChance = hangChance;
		this.maxHang = maxHang;
	}

	@Override
	protected FoliagePlacerType<?> type() {
		return ExpanseTreePlacers.DROOPING_FOLIAGE;
	}

	@Override
	protected void createFoliage(
		WorldGenLevel level, FoliageSetter setter, RandomSource random, TreeFeature tree, int treeHeight, FoliageAttachment attachment,
		int foliageHeight, int leafRadius, int offset
	) {
		BlockPos c = attachment.pos().above(offset);
		int r = Math.max(1, leafRadius + attachment.radiusOffsetXZ());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 0; y <= this.canopyHeight; y++) {
			float rr = r - y * 0.8F + 0.4F;
			int ir = Mth.ceil(rr);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz <= rr * rr) {
						tryPlaceLeaf(level, setter, random, tree, p.setWithOffset(c, dx, y, dz));
					}
				}
			}
		}
		// Curtains from the rim of the widest layer.
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				float d = Mth.sqrt(dx * dx + dz * dz);
				if (d > r + 0.4F || d < r - 1.3F || random.nextFloat() >= this.hangChance) {
					continue;
				}
				int reach = MAX_LEAF_DISTANCE - (Math.abs(dx) + Math.abs(dz)) - Math.abs(offset);
				int len = Math.min(this.maxHang, reach) - random.nextInt(2);
				for (int k = 1; k <= len; k++) {
					if (!tryPlaceLeaf(level, setter, random, tree, p.setWithOffset(c, dx, -k, dz))) {
						break;
					}
				}
			}
		}
	}

	@Override
	public int foliageHeight(RandomSource random, int treeHeight, TreeFeature tree) {
		return this.canopyHeight;
	}

	@Override
	protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
		return false;
	}
}
