package dev.voidmc.mettle.mixin.gear;

import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crafting table carries the gear pockets after its own slots (BURDEN.md §6). Its quick-move uses fixed ranges, so a
 * pocket's shift-click already lands in the inventory (the "anything else" branch).
 */
@Mixin(CraftingMenu.class)
abstract class GearCraftingMenuMixin {
	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void mettle$addPockets(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		GearSlots.addPockets(inventory.player, ((AbstractContainerMenuInvoker) this)::mettle$addSlot);
	}
}
