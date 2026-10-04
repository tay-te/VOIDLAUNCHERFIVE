package dev.voidmc.hub.model;

/**
 * One vanilla container click, exactly as {@code MultiPlayerGameMode.handleContainerInput} takes
 * it: the menu slot index (or {@link #OUTSIDE}), the button number, and the action. The action
 * names mirror {@code net.minecraft.world.inventory.ContainerInput} one to one.
 */
public record Click(int slot, int button, Action action) {

	/** Vanilla's "clicked outside the panel" slot id: drops the carried stack. */
	public static final int OUTSIDE = -999;

	public enum Action {
		PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL
	}

	public static Click pickup(int slot) {
		return new Click(slot, 0, Action.PICKUP);
	}

	public static Click pickupHalf(int slot) {
		return new Click(slot, 1, Action.PICKUP);
	}

	public static Click quickMove(int slot) {
		return new Click(slot, 0, Action.QUICK_MOVE);
	}

	/** Number key {@code hotbar} (0-8) over {@code slot}; 40 swaps with the offhand. */
	public static Click hotbarSwap(int slot, int hotbar) {
		return new Click(slot, hotbar, Action.SWAP);
	}

	/** Q over a slot: one item, or the whole stack with Ctrl. */
	public static Click drop(int slot, boolean wholeStack) {
		return new Click(slot, wholeStack ? 1 : 0, Action.THROW);
	}

	public static Click collect(int slot) {
		return new Click(slot, 0, Action.PICKUP_ALL);
	}

	/**
	 * Minecraft 26.x numbers mouse buttons the SDL way (1 left, 2 middle, 3 right); container
	 * clicks number them 0 left, 1 right. Same mapping as AbstractContainerScreen's.
	 */
	public static int containerButton(int mouseButton) {
		return switch (mouseButton) {
			case 1 -> 0;
			case 3 -> 1;
			default -> mouseButton;
		};
	}
}
