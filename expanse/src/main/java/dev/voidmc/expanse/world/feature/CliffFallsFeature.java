package dev.voidmc.expanse.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.expanse.api.LandClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;

/**
 * A waterfall off a cliff: where the land breaks off in a face of rock (an escarpment's edge, a gorge's wall, a rock
 * town, a mountain crag), a spring breaks out a couple of blocks under the rim and its water falls the height of the
 * face in a ribbon, pooling where it lands. From far off a white thread down a dark wall.
 *
 * <p>From the placement it looks a few blocks each way for the edge with the longest fall, at least {@code min_drop}
 * blocks; the spring goes in the last column of the upland, in solid ground (soil or rock) that holds it on every side
 * but the face. Nothing on land a settlement has claimed.
 */
public record CliffFallsFeature(int minDrop) implements Feature {
	public static final MapCodec<CliffFallsFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.intRange(6, 120).fieldOf("min_drop").forGetter(CliffFallsFeature::minDrop)
	).apply(i, CliffFallsFeature::new));

	/** Dev counter: waterfalls made. */
	public static final java.util.concurrent.atomic.AtomicInteger MADE = new java.util.concurrent.atomic.AtomicInteger();

	@Override
	public MapCodec<CliffFallsFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x = origin.getX();
		int z = origin.getZ();
		int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		Direction best = null;
		int bestDrop = 0;
		int bestStep = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int last = top;
			for (int k = 1; k <= 6; k++) {
				int h = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dir.getStepX() * k, z + dir.getStepZ() * k);
				if (h > last + 1) {
					break;   // the ground rises that way
				}
				if (last - h >= 3) {
					// the edge: from column k - 1 down to column k; how far does the water fall?
					int foot = h;
					for (int j = k + 1; j <= k + 4; j++) {
						foot = Math.min(foot, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dir.getStepX() * j, z + dir.getStepZ() * j));
					}
					if (top - foot > bestDrop && last >= top - 1) {
						bestDrop = top - foot;
						best = dir;
						bestStep = k - 1;
					}
					break;
				}
				last = h;
			}
		}
		if (best == null || bestDrop < this.minDrop) {
			return false;
		}
		BlockPos rim = new BlockPos(x + best.getStepX() * bestStep, 0, z + best.getStepZ() * bestStep);
		int rimTop = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, rim.getX(), rim.getZ());
		// high in the mountains every crag is a wall: keep the falls rare there, where each one counts
		if (rimTop > 240 && random.nextFloat() < 0.75F) {
			return false;
		}
		BlockPos spring = rim.atY(rimTop - 3);
		if (LandClaims.claimed(level, spring.getX(), spring.getZ())) {
			return false;
		}
		// held in solid natural ground on every side but the face, and under a solid roof
		BlockPos face = spring.relative(best);
		if (!level.getBlockState(face).isAir()) {
			return false;
		}
		for (Direction side : new Direction[]{best.getOpposite(), best.getClockWise(), best.getCounterClockWise(), Direction.UP, Direction.DOWN}) {
			if (!solid(level.getBlockState(spring.relative(side)))) {
				return false;
			}
		}
		if (!solid(level.getBlockState(spring))) {
			return false;
		}
		level.setBlock(spring, Blocks.WATER.defaultBlockState(), 2);
		level.scheduleTick(spring, Fluids.WATER, 0);
		MADE.incrementAndGet();
		return true;
	}

	/** Natural ground that holds water: rock or soil, nothing built, nothing that lets water through. */
	private static boolean solid(BlockState st) {
		return st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || st.is(net.minecraft.tags.BlockTags.SUBSTRATE_OVERWORLD)
			|| st.is(Blocks.SANDSTONE) || st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.BASALT) || st.is(Blocks.COARSE_DIRT)
			|| st.is(dev.voidmc.expanse.registry.ExpanseBlocks.LIMESTONE);
	}
}
