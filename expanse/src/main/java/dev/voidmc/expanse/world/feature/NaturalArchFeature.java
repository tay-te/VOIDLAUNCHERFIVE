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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A free-standing stone arch, like those of Utah's canyon country: two feet on the ground, a
 * half-sine span between them, thick at the feet and thin at the crown, banded in strata by height.
 *
 * <p>The span is drawn as a chain of overlapping ellipsoids along the curve. Each foot is then
 * extended down to the ground it stands on, which may be lower than the other foot's.
 * Spans are capped at 26 so the whole arch stays inside the 3x3 chunks a feature may write to.
 */
public record NaturalArchFeature(List<BlockState> layers, int bandHeight, IntProvider span, IntProvider height, IntProvider thickness)
	implements Feature {
	public static final MapCodec<NaturalArchFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.listOf(1, 16).fieldOf("layers").forGetter(NaturalArchFeature::layers),
		Codec.intRange(1, 8).fieldOf("band_height").forGetter(NaturalArchFeature::bandHeight),
		IntProviders.codec(6, 26).fieldOf("span").forGetter(NaturalArchFeature::span),
		IntProviders.codec(4, 32).fieldOf("height").forGetter(NaturalArchFeature::height),
		IntProviders.codec(1, 5).fieldOf("thickness").forGetter(NaturalArchFeature::thickness)
	).apply(i, NaturalArchFeature::new));

	@Override
	public MapCodec<NaturalArchFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int span = this.span.sample(random);
		int h = this.height.sample(random);
		float th = this.thickness.sample(random);
		float angle = random.nextFloat() * Mth.TWO_PI;
		float dx = Mth.cos(angle);
		float dz = Mth.sin(angle);
		float half = span / 2.0F;
		double ax = origin.getX() - dx * half;
		double az = origin.getZ() - dz * half;
		double bx = origin.getX() + dx * half;
		double bz = origin.getZ() + dz * half;
		int ga = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, Mth.floor(ax), Mth.floor(az));
		int gb = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, Mth.floor(bx), Mth.floor(bz));
		if (Math.abs(ga - gb) > h / 2 || Math.max(ga, gb) + h + th >= level.getMaxY()) {
			return false;
		}
		int samples = span * 3;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int s = 0; s <= samples; s++) {
			float t = (float) s / samples;
			double x = Mth.lerp(t, ax, bx);
			double z = Mth.lerp(t, az, bz);
			double y = Mth.lerp(t, ga, gb) + h * Mth.sin(Mth.PI * t);
			float r = th * (1.0F + 0.9F * (1.0F - Mth.sin(Mth.PI * t)));
			this.blob(level, p, x, y, z, r, r * 0.8F);
		}
		// Feet down to whatever ground they stand on.
		this.foot(level, p, ax, ga, az, th + 1.0F);
		this.foot(level, p, bx, gb, bz, th + 1.0F);
		return true;
	}

	private void blob(WorldGenLevel level, BlockPos.MutableBlockPos p, double x, double y, double z, float rxz, float ry) {
		int ir = Mth.ceil(rxz);
		int iry = Mth.ceil(ry);
		for (int ix = -ir; ix <= ir; ix++) {
			for (int iy = -iry; iy <= iry; iy++) {
				for (int iz = -ir; iz <= ir; iz++) {
					double fx = Mth.floor(x) + ix + 0.5 - x;
					double fy = Mth.floor(y) + iy + 0.5 - y;
					double fz = Mth.floor(z) + iz + 0.5 - z;
					if (fx * fx / (rxz * rxz) + fy * fy / (ry * ry) + fz * fz / (rxz * rxz) <= 1.0) {
						p.set(Mth.floor(x) + ix, Mth.floor(y) + iy, Mth.floor(z) + iz);
						level.setBlock(p, this.layer(p.getY()), 2);
					}
				}
			}
		}
	}

	private void foot(WorldGenLevel level, BlockPos.MutableBlockPos p, double x, int ground, double z, float r) {
		int ir = Mth.ceil(r);
		for (int ix = -ir; ix <= ir; ix++) {
			for (int iz = -ir; iz <= ir; iz++) {
				if (ix * ix + iz * iz > r * r) {
					continue;
				}
				for (int y = ground + 2; y > ground - 8; y--) {
					p.set(Mth.floor(x) + ix, y, Mth.floor(z) + iz);
					BlockState here = level.getBlockState(p);
					if (y < ground && !here.isAir() && !here.canBeReplaced() && here.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(p, this.layer(y), 2);
				}
			}
		}
	}

	private BlockState layer(int y) {
		int band = Math.floorMod(Math.floorDiv(y, this.bandHeight), this.layers.size());
		return this.layers.get(band);
	}
}
