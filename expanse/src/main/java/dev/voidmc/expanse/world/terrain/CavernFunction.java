package dev.voidmc.expanse.world.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code expanse:caverns}: the {@link CavernModel}'s halls and river tunnels as a density, negative
 * inside open space and positive in the rock, on the same scale as the terrain density (a block is
 * worth 0.08). The Earth noise settings take the minimum of this and the rest of the final density, so
 * it only ever carves.
 */
public record CavernFunction() implements DensityFunction {
	public static final MapCodec<CavernFunction> CODEC = MapCodec.unit(new CavernFunction());

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		long seed = context.createRandom(TerrainModel.SEED_ID).nextLong();
		CavernModel model = TerrainModel.forSeed(seed).caverns();
		return new DensitySampler() {
			@Override
			public void sampleVolume(SamplerContext ctx, net.minecraft.world.level.levelgen.densityfunction.DensityBuffer out,
				net.minecraft.world.level.levelgen.densityfunction.DensityVolume volume) {
				DensitySampler.sampleVolumeNaive(ctx, out, volume, this);
			}

			@Override
			public float sampleValue(SamplerContext ctx, int x, int y, int z) {
				return Mth.clamp(-0.08F * model.inside(x, y, z), -1.0F, 1.0F);
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.of(-1.0F, 1.0F);
	}

	@Override
	public @Axes int domainAxes() {
		return ALL_AXES;
	}

	@Override
	public MapCodec<CavernFunction> codec() {
		return CODEC;
	}
}
