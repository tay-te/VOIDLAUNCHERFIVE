package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A cathedral termite mound: a lumpy, tapering spire of baked red earth, taller than a person, with a
 * couple of lesser pinnacles leaning on its flanks. It is bedded a couple of blocks into the ground so
 * it never stands on air at the edge of a slope.
 */
public record TermiteSpireFeature(Holder<BlockStateProvider> material, IntProvider height, IntProvider radius) implements Feature {
	public static final MapCodec<TermiteSpireFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.CODEC.fieldOf("material").forGetter(TermiteSpireFeature::material),
		IntProviders.codec(2, 16).fieldOf("height").forGetter(TermiteSpireFeature::height),
		IntProviders.codec(0, 3).fieldOf("radius").forGetter(TermiteSpireFeature::radius)
	).apply(i, TermiteSpireFeature::new));

	@Override
	public MapCodec<TermiteSpireFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid()) {
			return false;
		}
		int h = this.height.sample(random);
		int r = this.radius.sample(random);
		this.spire(level, random, origin, h, r);
		// Lesser pinnacles on the flanks, a little lower.
		int extra = r == 0 ? random.nextInt(2) : 1 + random.nextInt(2);
		for (int k = 0; k < extra; k++) {
			float a = random.nextFloat() * Mth.TWO_PI;
			BlockPos at = BlockPos.containing(origin.getX() + 0.5 + Mth.cos(a) * (r + 1), origin.getY(), origin.getZ() + 0.5 + Mth.sin(a) * (r + 1));
			this.spire(level, random, at, Math.max(2, h * (5 + random.nextInt(3)) / 10), 0);
		}
		return true;
	}

	private void spire(WorldGenLevel level, RandomSource random, BlockPos base, int h, int r) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = -2; y < h; y++) {
			float t = Math.max(0, y) / (float) h;
			float rr = (r + 0.6F) * (float) Math.pow(1.0F - t, 0.8F) + (random.nextFloat() - 0.5F) * 0.5F;
			int ir = Mth.ceil(rr);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz > rr * rr + 0.3F && !(dx == 0 && dz == 0)) {
						continue;
					}
					p.setWithOffset(base, dx, y, dz);
					if (y >= 0 ? level.isEmptyBlock(p) || level.getBlockState(p).canBeReplaced() : !level.getBlockState(p).isAir()) {
						level.setBlock(p, this.material.value().getState(level, random, p), 2);
					}
				}
			}
		}
	}
}
