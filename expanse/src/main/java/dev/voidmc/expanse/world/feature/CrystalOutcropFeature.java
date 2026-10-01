package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A spray of crystal shards breaking out of the mountainside: a low mound of base stone, several
 * shards leaning out from it at different angles (the tallest two blocks thick at the root), and
 * loose clusters crusted over every face they can cling to.
 */
public record CrystalOutcropFeature(BlockState crystal, BlockState cluster, BlockState base, IntProvider count, IntProvider height)
	implements Feature {
	public static final MapCodec<CrystalOutcropFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("crystal").forGetter(CrystalOutcropFeature::crystal),
		BlockState.CODEC.fieldOf("cluster").forGetter(CrystalOutcropFeature::cluster),
		BlockState.CODEC.fieldOf("base").forGetter(CrystalOutcropFeature::base),
		IntProviders.codec(1, 12).fieldOf("count").forGetter(CrystalOutcropFeature::count),
		IntProviders.codec(2, 20).fieldOf("height").forGetter(CrystalOutcropFeature::height)
	).apply(i, CrystalOutcropFeature::new));

	@Override
	public MapCodec<CrystalOutcropFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (level.getBlockState(origin.below()).isAir()) {
			return false;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		// The mound.
		int mr = 2 + random.nextInt(2);
		for (int dx = -mr; dx <= mr; dx++) {
			for (int dz = -mr; dz <= mr; dz++) {
				for (int dy = -2; dy <= 1; dy++) {
					if (dx * dx + dz * dz + dy * dy * 3 <= mr * mr + random.nextInt(2)) {
						level.setBlock(p.setWithOffset(origin, dx, dy, dz), this.base, 2);
					}
				}
			}
		}
		java.util.List<BlockPos> placed = new java.util.ArrayList<>();
		int n = this.count.sample(random);
		for (int s = 0; s < n; s++) {
			int h = this.height.sample(random);
			if (s > 0) {
				h = Math.max(2, h * 2 / 3);
			}
			float tilt = 0.15F + random.nextFloat() * 0.45F;
			float a = random.nextFloat() * Mth.TWO_PI;
			float sx = Mth.cos(a) * tilt;
			float sz = Mth.sin(a) * tilt;
			BlockPos root = origin.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
			boolean thick = s == 0 && h >= 7;
			for (int k = 0; k < h; k++) {
				BlockPos q = BlockPos.containing(root.getX() + 0.5 + sx * k, root.getY() + k, root.getZ() + 0.5 + sz * k);
				level.setBlock(q, this.crystal, 2);
				placed.add(q);
				if (thick && k < h * 0.6F) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						level.setBlock(q.relative(d), this.crystal, 2);
						placed.add(q.relative(d));
					}
				}
			}
		}
		// Clusters on any open face of the shards and the mound, pointing away from what holds them.
		for (int i = 0; i < placed.size() * 2; i++) {
			BlockPos support = placed.get(random.nextInt(placed.size()));
			Direction d = Direction.getRandom(random);
			BlockPos at = support.relative(d);
			if (level.getBlockState(at).isAir()) {
				level.setBlock(at, this.cluster.trySetValue(AmethystClusterBlock.FACING, d), 2);
			}
		}
		return true;
	}
}
