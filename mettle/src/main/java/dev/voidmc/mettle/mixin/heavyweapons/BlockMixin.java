package dev.voidmc.mettle.mixin.heavyweapons;

import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rhythm of the Axe counts chopped logs here because this runs on both sides when a player breaks a block: the client
 * predicts the faster swing from its own count, the server agrees from its own.
 */
@Mixin(Block.class)
abstract class BlockMixin {
	@Inject(method = "playerWillDestroy", at = @At("HEAD"))
	private void mettle$heavyRhythm(Level level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<BlockState> cir) {
		Woodcraft.willDestroy(level, player, state);
	}
}
