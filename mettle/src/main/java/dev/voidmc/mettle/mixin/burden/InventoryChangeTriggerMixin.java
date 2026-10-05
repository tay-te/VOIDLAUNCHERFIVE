package dev.voidmc.mettle.mixin.burden;

import dev.voidmc.mettle.burden.LoadTracker;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The inventory changed: vanilla fires this from the menu's per-tick diff, so it also catches edits inside bundles and
 * shulker boxes. The load is recomputed at the end of the tick (BURDEN.md §2).
 */
@Mixin(InventoryChangeTrigger.class)
abstract class InventoryChangeTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("HEAD"))
	private void mettle$burdenChanged(ServerPlayer player, Inventory inventory, ItemStack changed, CallbackInfo ci) {
		LoadTracker.markDirty(player);
	}
}
