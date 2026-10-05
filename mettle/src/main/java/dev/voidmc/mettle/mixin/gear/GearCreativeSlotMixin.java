package dev.voidmc.mettle.mixin.gear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.gear.GearRules;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The creative inventory sets slots by index and the server only accepts 1–45; the gear slots go up to 85. What lands
 * in a gear slot is still checked: only matching gear in a worn slot, only allowed items in an open pocket.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class GearCreativeSlotMixin {
	@ModifyConstant(method = "handleSetCreativeModeSlot", constant = @Constant(intValue = 45))
	private int mettle$gearSlots(int last) {
		return GearRules.END - 1;
	}

	@WrapOperation(method = "handleSetCreativeModeSlot",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
	private void mettle$checkGearSlot(Slot slot, ItemStack stack, Operation<Void> original) {
		if (slot instanceof GearSlots.WornSlot || slot instanceof GearSlots.PocketSlot) {
			if (!slot.isActive() || !stack.isEmpty() && (!slot.mayPlace(stack) || stack.getCount() > slot.getMaxStackSize(stack))) {
				return;
			}
		}
		original.call(slot, stack);
	}
}
