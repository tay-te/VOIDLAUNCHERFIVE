package dev.voidmc.expanse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * A saguaro: a ribbed column that joins up with its neighbours the way a chorus plant does, so a trunk
 * can put out an arm sideways that turns and climbs. It stands on dry ground (anything dead bushes take)
 * or on itself, and an arm stands on the trunk beside it as long as that trunk stands on something; take
 * the foot away and the whole cactus comes down a block at a time. It pricks like a cactus.
 *
 * <p>The tip of every column carries a small cap, a block high, so a cactus flower sits on it the way it
 * sits on a vanilla cactus (the block is in {@code #support_override_cactus_flower}).
 */
public class SaguaroBlock extends PipeBlock {
	public SaguaroBlock(BlockBehaviour.Properties properties) {
		super(12.0F, properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
			.setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	/** The state with each side joined wherever there is more saguaro (or, below, the ground it grows from). */
	public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction d : Direction.values()) {
			state = state.setValue(PROPERTY_BY_DIRECTION.get(d), joins(state.getBlock(), d, level.getBlockState(pos.relative(d))));
		}
		return state;
	}

	private static boolean joins(Block self, Direction direction, BlockState neighbour) {
		return neighbour.is(self) || direction == Direction.DOWN && neighbour.is(BlockTags.SUPPORTS_DRY_VEGETATION);
	}

	@Override
	protected BlockState updateShape(
		BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
		BlockState neighbourState, RandomSource random
	) {
		if (!state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), joins(this, directionToNeighbour, neighbourState));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (this.standsOn(level, pos)) {
			return true;
		}
		// An arm: held by the trunk beside it, if that trunk is standing.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			if (level.getBlockState(side).is(this) && this.standsOn(level, side)) {
				return true;
			}
		}
		return false;
	}

	private boolean standsOn(LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(this) || below.is(BlockTags.SUPPORTS_DRY_VEGETATION);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		entity.hurt(level.damageSources().cactus(), 1.0F);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
