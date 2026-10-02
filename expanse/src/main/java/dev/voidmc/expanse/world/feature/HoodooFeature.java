package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A hoodoo: a pillar of soft banded rock weathered out from under a harder cap. It swells at the foot,
 * pinches in to a neck below the top, and wears its caprock like a hat a little wider than the neck.
 * The bands run level through every hoodoo in the same order, as strata do.
 */
public record HoodooFeature(List<BlockState> layers, int bandHeight, BlockState cap, IntProvider height, IntProvider radius) implements Feature {
	public static final MapCodec<HoodooFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.listOf(1, 16).fieldOf("layers").forGetter(HoodooFeature::layers),
		Codec.intRange(1, 8).fieldOf("band_height").forGetter(HoodooFeature::bandHeight),
		BlockState.CODEC.fieldOf("cap").forGetter(HoodooFeature::cap),
		IntProviders.codec(3, 32).fieldOf("height").forGetter(HoodooFeature::height),
		IntProviders.codec(1, 5).fieldOf("radius").forGetter(HoodooFeature::radius)
	).apply(i, HoodooFeature::new));

	@Override
	public MapCodec<HoodooFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid()) {
			return false;
		}
		int h = this.height.sample(random);
		float r = this.radius.sample(random);
		if (origin.getY() + h + 2 >= level.getMaxY()) {
			return false;
		}
		float phase = random.nextFloat() * Mth.TWO_PI;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = -4; y < h; y++) {
			float t = Math.max(0, y) / (float) h;
			// foot flare, then a taper to the neck at about three quarters up, then a slight swell under the cap
			float profile = 1.0F + 0.6F * (1.0F - Mth.clamp(t * 4.0F, 0, 1)) - 0.45F * Mth.clamp((t - 0.35F) / 0.4F, 0, 1)
				+ 0.15F * Mth.clamp((t - 0.85F) / 0.15F, 0, 1);
			float rr = r * profile + 0.35F + 0.25F * Mth.sin(phase + y * 0.9F);
			int ir = Mth.ceil(rr);
			BlockState band = this.layers.get(Math.floorMod(origin.getY() + y, this.layers.size() * this.bandHeight) / this.bandHeight);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz > rr * rr) {
						continue;
					}
					p.setWithOffset(origin, dx, y, dz);
					if (y < 0 && level.getBlockState(p).isSolid()) {
						continue;
					}
					level.setBlock(p, band, 2);
				}
			}
		}
		// The caprock: a slab of harder stone a block or two thick, overhanging the neck.
		float capR = r * 0.75F + 1.1F;
		int thick = 1 + random.nextInt(2);
		int ic = Mth.ceil(capR);
		for (int y = 0; y < thick; y++) {
			float cr = y == thick - 1 && thick > 1 ? capR - 0.6F : capR;
			for (int dx = -ic; dx <= ic; dx++) {
				for (int dz = -ic; dz <= ic; dz++) {
					if (dx * dx + dz * dz <= cr * cr) {
						level.setBlock(p.setWithOffset(origin, dx, h + y, dz), this.cap, 2);
					}
				}
			}
		}
		return true;
	}
}
