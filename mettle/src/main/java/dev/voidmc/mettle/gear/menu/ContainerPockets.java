package dev.voidmc.mettle.gear.menu;

import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.gear.GearInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shift-click for container menus that carry the pockets after their own slots (chest, barrel, ender chest, shulker
 * box). Vanilla moves container → player into {@code [containerEnd, slots.size())}, which would now fill pockets before
 * the hotbar; this keeps it to the player's 36 slots and lets what does not fit go into the pack. From a pocket, items go
 * into the container first, like any other player item.
 */
public final class ContainerPockets {
	/** The menu's protected {@code moveItemStackTo}. */
	@FunctionalInterface
	public interface Mover {
		boolean move(ItemStack stack, int from, int to, boolean backwards);
	}

	private ContainerPockets() {
	}

	/**
	 * @param containerEnd the container's slots are {@code [0, containerEnd)}
	 * @param vanillaEnd   the menu's slot count before the pockets; the player's 36 are {@code [containerEnd, vanillaEnd)}
	 * @return the vanilla-style result, or null to let vanilla handle a player-inventory slot
	 */
	public static @Nullable ItemStack quickMove(AbstractContainerMenu menu, Mover mover, Player player, int index, int containerEnd, int vanillaEnd) {
		if (index < 0 || index >= menu.slots.size() || vanillaEnd <= containerEnd) {
			return null;
		}
		boolean fromPocket = index >= vanillaEnd;
		if (!fromPocket && index >= containerEnd) {
			return null;
		}
		Slot slot = menu.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack clicked = stack.copy();
		boolean moved;
		if (fromPocket) {
			moved = mover.move(stack, 0, containerEnd, false);
			if (!stack.isEmpty()) {
				moved |= mover.move(stack, containerEnd, vanillaEnd, true);
			}
		} else {
			moved = mover.move(stack, containerEnd, vanillaEnd, true);
			int pack = GearInventory.of(player).pocketCount(GearSlot.BACK);
			if (!stack.isEmpty() && pack > 0) {
				moved |= mover.move(stack, vanillaEnd, vanillaEnd + pack, false);
			}
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return clicked;
	}
}
