package dev.voidmc.mettle.mixin.gear;

import dev.voidmc.mettle.gear.menu.ContainerPockets;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shulker boxes carry the gear pockets after their own slots (BURDEN.md §6). */
@Mixin(ShulkerBoxMenu.class)
abstract class GearShulkerBoxMenuMixin extends AbstractContainerMenu {
	@Shadow
	@Final
	private Container container;

	@Unique
	private int mettle$vanillaEnd;

	GearShulkerBoxMenuMixin(MenuType<?> type, int id) {
		super(type, id);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;)V", at = @At("TAIL"))
	private void mettle$addPockets(int id, Inventory inventory, Container box, CallbackInfo ci) {
		mettle$vanillaEnd = slots.size();
		GearSlots.addPockets(inventory.player, this::addSlot);
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mettle$route(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack result = ContainerPockets.quickMove(this, this::moveItemStackTo, player, index, container.getContainerSize(), mettle$vanillaEnd);
		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}
