package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A bog pool: a little pond of standing water let into flat ground, level with it, the way the pools of a
 * muskeg lie among the moss. The outline is a wobbly disc; only columns whose ground stands at the pool's
 * own level (on solid ground, not over a cave), and any cell with open air beside it is pared away until every
 * cell of the pool is walled by ground or by more pool, so the water never spills down a slope. It is one block
 * deep at the margin and two in the middle, lined with {@code liner} (peat), and the ground round the edge
 * turns to {@code rim} (sphagnum) here and there.
 */
public record BogPoolFeature(BlockState liner, BlockState rim, float rimChance, IntProvider radius) implements Feature {
	public static final MapCodec<BogPoolFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("liner").forGetter(BogPoolFeature::liner),
		BlockState.CODEC.fieldOf("rim").forGetter(BogPoolFeature::rim),
		Codec.floatRange(0.0F, 1.0F).fieldOf("rim_chance").forGetter(BogPoolFeature::rimChance),
		IntProviders.codec(1, 6).fieldOf("radius").forGetter(BogPoolFeature::radius)
	).apply(i, BogPoolFeature::new));

	@Override
	public MapCodec<BogPoolFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos centre = origin.below();
		if (!isGround(level.getBlockState(centre))) {
			return false;
		}
		int r = this.radius.sample(random);
		float p1 = random.nextFloat() * Mth.TWO_PI;
		float p2 = random.nextFloat() * Mth.TWO_PI;
		float k1 = 0.1F + random.nextFloat() * 0.2F;
		float k2 = random.nextFloat() * 0.15F;
		LongSet pool = new LongOpenHashSet();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				float a = (float) Mth.atan2(dz, dx);
				float rr = r * (1.0F + k1 * Mth.cos(2 * a + p1) + k2 * Mth.cos(3 * a + p2)) + 0.4F;
				if (dx * dx + dz * dz > rr * rr) {
					continue;
				}
				p.setWithOffset(centre, dx, 0, dz);
				if (isGround(level.getBlockState(p)) && isOpen(level.getBlockState(p.above())) && holdsWater(level.getBlockState(p.below()))) {
					pool.add(p.asLong());
				}
			}
		}
		// Pare away any cell that would leak: each side must be more pool, or ground that holds water.
		boolean changed = true;
		while (changed && !pool.isEmpty()) {
			changed = false;
			for (long cell : pool.toLongArray()) {
				BlockPos c = BlockPos.of(cell);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = c.relative(d);
					if (!pool.contains(n.asLong()) && !holdsWater(level.getBlockState(n))) {
						pool.remove(cell);
						changed = true;
						break;
					}
				}
			}
		}
		if (pool.size() < 3) {
			return false;
		}
		BlockState water = Blocks.WATER.defaultBlockState();
		for (long cell : pool) {
			BlockPos c = BlockPos.of(cell);
			// Two deep where the cell is surrounded by pool on all eight sides, so the floor is never seen dry.
			boolean deep = true;
			for (int ox = -1; ox <= 1 && deep; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					if (!pool.contains(c.offset(ox, 0, oz).asLong())) {
						deep = false;
						break;
					}
				}
			}
			BlockState above = level.getBlockState(c.above());
			if (!above.isAir() && above.canBeReplaced()) {
				level.setBlock(c.above(), Blocks.AIR.defaultBlockState(), 2);
			}
			level.setBlock(c, water, 2);
			BlockPos floor = c.below();
			if (deep && holdsWater(level.getBlockState(floor)) && holdsWater(level.getBlockState(floor.below()))) {
				level.setBlock(floor, water, 2);
				floor = floor.below();
			}
			if (level.getBlockState(floor).is(BlockTags.SUBSTRATE_OVERWORLD)) {
				level.setBlock(floor, this.liner, 2);
			}
		}
		// The sphagnum creeping in at the margin.
		for (long cell : pool) {
			BlockPos c = BlockPos.of(cell);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = c.relative(d);
				if (!pool.contains(n.asLong()) && level.getBlockState(n).is(BlockTags.GRASS_BLOCKS) && random.nextFloat() < this.rimChance) {
					level.setBlock(n, this.rim, 2);
				}
			}
		}
		return true;
	}

	/** Soil the pool can be let into: grass, dirt, peat, moss, mud. */
	private static boolean isGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD);
	}

	/** What may stand over a cell of the pool: air, or a plant or snow that the water takes the place of. */
	private static boolean isOpen(BlockState state) {
		return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	/** A side that keeps the water in: anything solid and dry. */
	private static boolean holdsWater(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES);
	}
}
