package dev.voidmc.mettle.mixin.gear;

import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearRules;
import dev.voidmc.mettle.gear.GearSpec;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The gear and pocket slots (indices 46–85) and their shift-click routing (BURDEN.md §6). */
@Mixin(InventoryMenu.class)
abstract class GearInventoryMenuMixin extends AbstractCraftingMenu {
	GearInventoryMenuMixin(MenuType<?> type, int id, int width, int height) {
		super(type, id, width, height);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mettle$addGearSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		GearSlots.addTo(owner, this::addSlot);
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mettle$route(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (index < 0 || index >= slots.size()) {
			return;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return;
		}
		ItemStack stack = slot.getItem();
		GearInventory gear = GearInventory.of(player);
		GearSpec spec = stack.get(GearComponents.GEAR);
		EquipmentSlot equip = player.getEquipmentSlotForItem(stack);
		boolean vanillaEquips = equip.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !slots.get(8 - equip.getIndex()).hasItem()
			|| equip == EquipmentSlot.OFFHAND && index != 45 && !slots.get(45).hasItem();
		GearRules.Route route = GearRules.quickMove(index, new GearRules.MoveContext(
			spec == null ? null : spec.slot(), spec != null && gear.worn(spec.slot()).isEmpty(), vanillaEquips,
			gear.fits(GearSlot.QUIVER, stack), gear.pocketCount(GearSlot.QUIVER),
			gear.fits(GearSlot.BACK, stack), gear.pocketCount(GearSlot.BACK),
			player.hasInfiniteMaterials()));
		if (route == null) {
			return;
		}
		ItemStack clicked = stack.copy();
		for (int[] range : route.ranges()) {
			if (stack.isEmpty()) {
				break;
			}
			moveItemStackTo(stack, range[0], range[1], false);
		}
		if (route.thenVanilla() && !stack.isEmpty()) {
			// What is left goes the vanilla way.
			if (stack.getCount() != clicked.getCount()) {
				slot.setChanged();
			}
			return;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY, clicked);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == clicked.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		slot.onTake(player, stack);
		cir.setReturnValue(clicked);
	}
}
