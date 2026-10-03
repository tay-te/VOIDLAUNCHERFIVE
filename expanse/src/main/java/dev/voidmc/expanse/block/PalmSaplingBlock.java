package dev.voidmc.expanse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Palms grow where the beach is: sand as well as soil. */
public class PalmSaplingBlock extends SaplingBlock {
	public PalmSaplingBlock(TreeGrower grower, BlockBehaviour.Properties properties) {
		super(grower, properties);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.SAND) || super.mayPlaceOn(state, level, pos);
	}
}
