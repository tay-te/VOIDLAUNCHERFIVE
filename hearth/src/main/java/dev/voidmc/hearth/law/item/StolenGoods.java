package dev.voidmc.hearth.law.item;

import dev.voidmc.hearth.api.LawService.Owner;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Stolen marks: putting them on, reading them as they fade, and taking stolen goods back. */
public final class StolenGoods {
	private StolenGoods() {
	}

	public static void mark(ItemStack stack, Owner owner, String ownerName, String settlementName, long day, UUID thief) {
		stack.set(LawComponents.STOLEN, new StolenMark(owner, ownerName, settlementName, day, thief));
	}

	/** The stolen mark if it has not faded by {@code today}, else null. */
	public static @Nullable StolenMark mark(ItemStack stack, long today, int fadeDays) {
		StolenMark mark = stack.isEmpty() ? null : stack.get(LawComponents.STOLEN);
		if (mark == null || fadeDays > 0 && today - mark.day() >= fadeDays) {
			return null;
		}
		return mark;
	}

	/** Whether a mark made on {@code markDay} has faded by {@code today}. Pure. */
	public static boolean faded(long markDay, long today, int fadeDays) {
		return fadeDays > 0 && today - markDay >= fadeDays;
	}

	/** Takes faded marks off everything in an inventory; returns how many stacks were cleaned. */
	public static int fade(Inventory inventory, long today, int fadeDays) {
		if (fadeDays <= 0) {
			return 0;
		}
		int cleaned = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			StolenMark mark = stack.isEmpty() ? null : stack.get(LawComponents.STOLEN);
			if (mark != null && faded(mark.day(), today, fadeDays)) {
				stack.remove(LawComponents.STOLEN);
				cleaned++;
			}
		}
		return cleaned;
	}

	/**
	 * Removes the stolen goods that match from an inventory (an arrest, a search) and returns them.
	 *
	 * @param which which marks to take (this town's, this thief's, any)
	 */
	public static java.util.List<ItemStack> confiscate(Inventory inventory, long today, int fadeDays, Predicate<StolenMark> which) {
		java.util.List<ItemStack> taken = new java.util.ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			StolenMark mark = mark(stack, today, fadeDays);
			if (mark != null && which.test(mark)) {
				taken.add(stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (!taken.isEmpty()) {
			inventory.setChanged();
		}
		return taken;
	}

	/** Whether the inventory holds goods stolen by this thief from this settlement (what a search would find). */
	public static boolean carries(Inventory inventory, long today, int fadeDays, Predicate<StolenMark> which) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			StolenMark mark = mark(inventory.getItem(i), today, fadeDays);
			if (mark != null && which.test(mark)) {
				return true;
			}
		}
		return false;
	}
}
