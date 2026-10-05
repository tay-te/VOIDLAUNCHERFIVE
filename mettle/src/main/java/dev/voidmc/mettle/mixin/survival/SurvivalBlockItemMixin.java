package dev.voidmc.mettle.mixin.survival;

import dev.voidmc.mettle.character.skill.survival.Foraging;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Plants a player puts down (flowers, berry bushes, grass) are remembered, so picking them again teaches nothing. */
@Mixin(BlockItem.class)
abstract class SurvivalBlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void mettle$survivalMarkPlanted(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer) {
			Foraging.placed(context.getLevel(), context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()));
		}
	}
}
