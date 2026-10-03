package dev.voidmc.expanse.block;

import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A speleothem of the world underground, after pointed dripstone: icicles growing from packed ice, amber
 * spikes from amber. It hangs or stands, falls when its root is broken and hurts what it lands on, and
 * grows slowly from the block it belongs to (no dripping: that is dripstone's alone).
 */
public class DeepSpeleothemBlock extends SpeleothemBlock {
	private final int landing;

	public DeepSpeleothemBlock(BlockState growsOn, @LevelEvent.Value int landingSound, BlockBehaviour.Properties properties) {
		super(growsOn, properties);
		this.landing = landingSound;
	}

	@Override
	protected @LevelEvent.Value int getStalactiteLandingSound() {
		return this.landing;
	}

	@Override
	protected int getMaxGrowthLength() {
		return 5;
	}
}
