package dev.voidmc.expanse.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fumarole: a crack in volcanic rock where steam and gas still breathe out. It smokes as a campfire does,
 * now and then hisses, and keeps on whatever the weather: a sign the mountain is not dead.
 */
public class FumaroleBlock extends Block {
	public FumaroleBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!level.getBlockState(pos.above()).isAir()) {
			return;
		}
		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
		level.addAlwaysVisibleParticle(random.nextInt(4) == 0 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
			x, pos.getY() + 1.0, z, 0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, x, pos.getY() + 1.1, z, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(140) == 0) {
			level.playLocalSound(pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 0.6F + random.nextFloat() * 0.3F, false);
		}
	}
}
