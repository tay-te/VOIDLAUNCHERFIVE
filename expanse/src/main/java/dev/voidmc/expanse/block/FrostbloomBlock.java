package dev.voidmc.expanse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A flower of the tundra, so it roots in packed snow as well as soil. */
public class FrostbloomBlock extends FlowerBlock {
	public FrostbloomBlock(Holder<MobEffect> effect, float seconds, BlockBehaviour.Properties properties) {
		super(effect, seconds, properties);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || super.mayPlaceOn(state, level, pos);
	}
}
