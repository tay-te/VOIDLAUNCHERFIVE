package dev.voidmc.mettle.mixin.heavyweapons;

import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Logs a player places are remembered, so they never count as forest (Old Growth, Timber). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void mettle$heavyMarkPlacedLog(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer
			&& Woodcraft.isLog(context.getLevel().getBlockState(context.getClickedPos()))) {
			PlacedBlocks.mark(context.getLevel(), context.getClickedPos());
		}
	}
}
