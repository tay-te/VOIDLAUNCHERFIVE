package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * A karst tower — the limestone pillars of Guilin and Ha Long: sheer, rounded, far taller than they
 * are wide, with a cap of turf and a tree or two on top and vines down their faces.
 *
 * <p>The profile is a column whose radius breathes with height (two sine waves out of phase, so no
 * two towers share a silhouette), leans a little with the square of height, and rounds into a dome
 * over its top tenth. Below the origin it keeps going until it meets solid ground, so a tower on a
 * slope does not stand on a lip of air.
 */
public record KarstPillarFeature(
	Holder<BlockStateProvider> stone, BlockState cap, BlockState soil, IntProvider height, IntProvider radius,
	Optional<Holder<PlacedFeature>> topFeature
) implements Feature {
	public static final MapCodec<KarstPillarFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockStateProvider.CODEC.fieldOf("stone").forGetter(KarstPillarFeature::stone),
		BlockState.CODEC.fieldOf("cap").forGetter(KarstPillarFeature::cap),
		BlockState.CODEC.fieldOf("soil").forGetter(KarstPillarFeature::soil),
		IntProviders.codec(4, 96).fieldOf("height").forGetter(KarstPillarFeature::height),
		IntProviders.codec(1, 8).fieldOf("radius").forGetter(KarstPillarFeature::radius),
		PlacedFeature.CODEC.optionalFieldOf("top_feature").forGetter(KarstPillarFeature::topFeature)
	).apply(i, KarstPillarFeature::new));

	@Override
	public MapCodec<KarstPillarFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int h = this.height.sample(random);
		int r0 = this.radius.sample(random);
		if (origin.getY() + h >= level.getMaxY() - 2) {
			return false;
		}
		float phaseA = random.nextFloat() * Mth.TWO_PI;
		float phaseB = random.nextFloat() * Mth.TWO_PI;
		float leanAngle = random.nextFloat() * Mth.TWO_PI;
		float lean = random.nextFloat() * Math.min(4.0F, h / 12.0F);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[] topY = new int[(2 * 9 + 1) * (2 * 9 + 1)];
		java.util.Arrays.fill(topY, Integer.MIN_VALUE);

		for (int y = -12; y < h; y++) {
			float t = Math.max(0.0F, (float) y / h);
			float rr = r0 * (1.0F - 0.2F * t) + 0.7F * Mth.sin(y * 0.31F + phaseA) + 0.45F * Mth.sin(y * 0.13F + phaseB);
			if (t > 0.88F) {
				rr *= Mth.sqrt(Math.max(0.0F, (1.0F - t) / 0.12F));
			}
			if (y < 0) {
				rr += -y * 0.25F; // a skirt that widens into the ground
			}
			if (rr < 0.5F) {
				continue;
			}
			float cx = Mth.cos(leanAngle) * lean * t * t;
			float cz = Mth.sin(leanAngle) * lean * t * t;
			int ir = Mth.ceil(rr) + 1;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					float fx = dx - cx;
					float fz = dz - cz;
					if (fx * fx + fz * fz > rr * rr) {
						continue;
					}
					p.setWithOffset(origin, dx, y, dz);
					BlockState here = level.getBlockState(p);
					// Below the origin only fill gaps (air, water, plants); real ground stays as it is.
					if (y < 0 && !here.isAir() && !here.canBeReplaced() && here.getFluidState().isEmpty()) {
						continue;
					}
					level.setBlock(p, this.stone.value().getState(level, random, p), 2);
					if (Math.abs(dx) <= 9 && Math.abs(dz) <= 9) {
						int idx = (dx + 9) * 19 + (dz + 9);
						topY[idx] = Math.max(topY[idx], y);
					}
				}
			}
		}

		// Turf on top: grass over two of soil, wherever the column's highest block is near the summit.
		int summit = Integer.MIN_VALUE;
		for (int v : topY) {
			summit = Math.max(summit, v);
		}
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				int ty = topY[(dx + 9) * 19 + (dz + 9)];
				if (ty < summit - 3) {
					continue;
				}
				p.setWithOffset(origin, dx, ty, dz);
				level.setBlock(p, this.cap, 2);
				for (int d = 1; d <= 2; d++) {
					level.setBlock(p.setWithOffset(origin, dx, ty - d, dz), this.soil, 2);
				}
			}
		}

		this.drapeVines(level, random, origin, h);

		BlockPos top = origin.above(summit + 1);
		this.topFeature.ifPresent(f -> f.value().place(level, generator, random, top));
		return true;
	}

	/** Vines down the faces: find the wall along a random ray, hang from the air beside it. */
	private void drapeVines(WorldGenLevel level, RandomSource random, BlockPos origin, int h) {
		int tries = h / 2;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tries; i++) {
			Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int y = Mth.floor(h * (0.25F + random.nextFloat() * 0.7F));
			int side = random.nextInt(5) - 2;
			// Walk inward from outside until the next block in is stone; that air cell gets the vine.
			BlockPos start = origin.above(y).relative(face, 10).relative(face.getClockWise(), side);
			p.set(start);
			for (int step = 0; step < 10; step++) {
				BlockPos inner = p.relative(face.getOpposite());
				if (!level.getBlockState(inner).isAir()) {
					if (level.getBlockState(p).isAir()) {
						BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(face.getOpposite()), true);
						int len = 3 + random.nextInt(10);
						for (int k = 0; k < len && level.getBlockState(p).isAir(); k++) {
							level.setBlock(p, vine, 2);
							p.move(Direction.DOWN);
						}
					}
					break;
				}
				p.set(inner);
			}
		}
	}
}
