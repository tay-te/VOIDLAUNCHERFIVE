package dev.voidmc.expanse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ocotillo: bundles of thin whip-like stems standing several blocks tall, stacked like sugar cane, with
 * the red flower spikes on whichever block is the {@link #TIP}. It stands on dry ground or on itself and
 * falls when what it stands on goes.
 */
public class OcotilloBlock extends Block {
	public static final BooleanProperty TIP = BlockStateProperties.TIP;
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 16.0);

	public OcotilloBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(TIP, !context.getLevel().getBlockState(context.getClickedPos().above()).is(this));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(this) || below.is(BlockTags.SUPPORTS_DRY_VEGETATION);
	}

	@Override
	protected BlockState updateShape(
		BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
		BlockState neighbourState, RandomSource random
	) {
		if (directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		if (directionToNeighbour == Direction.UP) {
			return state.setValue(TIP, !neighbourState.is(this));
		}
		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIP);
	}
}
