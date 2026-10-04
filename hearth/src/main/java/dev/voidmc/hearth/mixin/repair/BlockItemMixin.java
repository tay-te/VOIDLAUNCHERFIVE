package dev.voidmc.hearth.mixin.repair;

import dev.voidmc.hearth.repair.RepairHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks players place: a damaged block put back by a player, or litter in a doorway. */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("RETURN"))
	private void hearth$repairPlaced(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level
			&& RepairHooks.active(level)) {
			RepairHooks.placed(level, player, context.getClickedPos());
		}
	}
}
