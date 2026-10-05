package dev.voidmc.mettle.mixin.gear;

import dev.voidmc.mettle.gear.menu.ContainerPockets;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chests, barrels and the ender chest carry the gear pockets after their own slots (BURDEN.md §6). */
@Mixin(ChestMenu.class)
abstract class GearChestMenuMixin extends AbstractContainerMenu {
	@Shadow
	@Final
	private int containerRows;

	@Unique
	private int mettle$vanillaEnd;

	GearChestMenuMixin(MenuType<?> type, int id) {
		super(type, id);
	}

	@Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
		at = @At("TAIL"))
	private void mettle$addPockets(MenuType<?> type, int id, Inventory inventory, Container container, int rows, CallbackInfo ci) {
		mettle$vanillaEnd = slots.size();
		GearSlots.addPockets(inventory.player, this::addSlot);
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mettle$route(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack result = ContainerPockets.quickMove(this, this::moveItemStackTo, player, index, containerRows * 9, mettle$vanillaEnd);
		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}
