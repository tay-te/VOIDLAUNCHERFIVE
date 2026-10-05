package dev.voidmc.mettle.mixin.gear;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.gear.GearInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pickups: arrows go back into a worn quiver first, and whatever the 36 slots cannot take overflows into a worn
 * backpack (BURDEN.md §6). Server side, survival only.
 */
@Mixin(Inventory.class)
abstract class GearPlayerInventoryMixin {
	@Shadow
	@Final
	public Player player;

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void mettle$arrowsToQuiver(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir, @Share("gear") LocalBooleanRef tookSome) {
		if (slot != -1 || stack.isEmpty() || player.level().isClientSide() || player.hasInfiniteMaterials()) {
			return;
		}
		GearInventory gear = GearInventory.of(player);
		if (gear.pocketCount(GearSlot.QUIVER) > 0 && gear.insert(GearSlot.QUIVER, stack)) {
			tookSome.set(true);
			if (stack.isEmpty()) {
				cir.setReturnValue(true);
			}
		}
	}

	@ModifyReturnValue(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private boolean mettle$overflowToPack(boolean added, @Local(argsOnly = true) int slot, @Local(argsOnly = true) ItemStack stack,
		@Share("gear") LocalBooleanRef tookSome) {
		if (slot != -1 || stack.isEmpty() || player.level().isClientSide() || player.hasInfiniteMaterials()) {
			return added || tookSome.get();
		}
		GearInventory gear = GearInventory.of(player);
		boolean packed = gear.pocketCount(GearSlot.BACK) > 0 && gear.insert(GearSlot.BACK, stack);
		return added || packed || tookSome.get();
	}
}
