package dev.voidmc.mettle.gear.menu;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearRules;
import dev.voidmc.mettle.gear.GearSpec;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The 40 slots the gear module adds to every inventory menu (BURDEN.md §6): three worn-gear slots at 46–48, then 27 back,
 * 6 belt and 4 quiver pockets. The layout never changes, so client and server always agree; pockets beyond what the worn
 * item has are switched off with {@link Slot#isActive()}.
 *
 * <p>Pocket positions here are the fallback panel's, to the right of the inventory frame (UI.md §5 replaces it later);
 * the client moves them when the panel has to sit elsewhere.
 */
public final class GearSlots {
	/** Where the three worn slots sit in the inventory screen: above the offhand, beside the player figure. */
	public static final int WORN_X = 77;
	public static final int[] WORN_Y = {8, 26, 44};
	/** Fallback pocket panel, relative to the inventory frame. */
	public static final int PANEL_X = 180;
	public static final int PANEL_Y = 0;

	private GearSlots() {
	}

	/** Adds the slots in order 46..85; {@code add} is the menu's own addSlot. */
	public static void addTo(Player player, Consumer<Slot> add) {
		GearInventory gear = GearInventory.of(player);
		WornContainer worn = new WornContainer(gear);
		for (GearSlot slot : GearSlot.values()) {
			add.accept(new WornSlot(worn, gear, slot, WORN_X, WORN_Y[slot.ordinal()]));
		}
		for (GearSlot slot : GearSlot.values()) {
			PocketContainer pockets = new PocketContainer(gear, slot);
			for (int i = 0; i < GearRules.maxPockets(slot); i++) {
				int[] at = pocketPosition(slot, i);
				add.accept(new PocketSlot(pockets, gear, slot, i, at[0], at[1]));
			}
		}
	}

	/** A pocket's position in the fallback panel: backpack rows of 9, then the belt row, then the quiver row. */
	public static int[] pocketPosition(GearSlot slot, int i) {
		int x0 = PANEL_X + 8;
		return switch (slot) {
			case BACK -> new int[] {x0 + (i % 9) * 18, PANEL_Y + 18 + (i / 9) * 18};
			case BELT -> new int[] {x0 + i * 18, PANEL_Y + 18 + 3 * 18 + 14};
			case QUIVER -> new int[] {x0 + i * 18, PANEL_Y + 18 + 4 * 18 + 28};
		};
	}

	public static Identifier emptyIcon(GearSlot slot) {
		return Mettle.id("container/slot/" + slot.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** A worn-gear slot: takes only gear for this slot, one at a time. */
	public static final class WornSlot extends Slot {
		private final GearInventory gear;
		public final GearSlot gearSlot;

		WornSlot(Container container, GearInventory gear, GearSlot slot, int x, int y) {
			super(container, slot.ordinal(), x, y);
			this.gear = gear;
			this.gearSlot = slot;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			GearSpec spec = stack.get(GearComponents.GEAR);
			return spec != null && spec.slot() == gearSlot;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public @Nullable Identifier getNoItemIcon() {
			return emptyIcon(gearSlot);
		}

		public GearInventory gear() {
			return gear;
		}
	}

	/** A pocket of worn gear; active only while the worn item has that many pockets. */
	public static final class PocketSlot extends Slot {
		private final GearInventory gear;
		public final GearSlot gearSlot;
		public final int pocket;

		PocketSlot(Container container, GearInventory gear, GearSlot slot, int pocket, int x, int y) {
			super(container, pocket, x, y);
			this.gear = gear;
			this.gearSlot = slot;
			this.pocket = pocket;
		}

		@Override
		public boolean isActive() {
			return pocket < gear.pocketCount(gearSlot);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return isActive() && gear.fits(gearSlot, stack);
		}

		@Override
		public boolean allowModification(Player player) {
			// Taking part of a stack out is fine even if the pocket would no longer take it (an over-weight item).
			return isActive();
		}
	}

	/** The containers behind the gear slots (creative's slot wrappers only show the container). */
	public interface GearContainer extends Container {
	}

	/** The three worn stacks, read and written through the live model. */
	static final class WornContainer implements GearContainer {
		private final GearInventory gear;

		WornContainer(GearInventory gear) {
			this.gear = gear;
		}

		@Override
		public int getContainerSize() {
			return 3;
		}

		@Override
		public boolean isEmpty() {
			for (GearSlot slot : GearSlot.values()) {
				if (!gear.worn(slot).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			return gear.worn(GearSlot.values()[slot]);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			gear.setWorn(GearSlot.values()[slot], ItemStack.EMPTY);
			return stack;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return removeItem(slot, 1);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			gear.setWorn(GearSlot.values()[slot], stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			gear.commit();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			gear.takeAll();
		}
	}

	/** One worn item's pockets. */
	static final class PocketContainer implements GearContainer {
		private final GearInventory gear;
		private final GearSlot slot;

		PocketContainer(GearInventory gear, GearSlot slot) {
			this.gear = gear;
			this.slot = slot;
		}

		@Override
		public int getContainerSize() {
			return GearRules.maxPockets(slot);
		}

		@Override
		public boolean isEmpty() {
			return gear.pockets(slot).stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int index) {
			return gear.pocket(slot, index);
		}

		@Override
		public ItemStack removeItem(int index, int count) {
			ItemStack stack = gear.pocket(slot, index);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = stack.split(count);
			gear.setPocket(slot, index, stack.isEmpty() ? ItemStack.EMPTY : stack);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int index) {
			ItemStack stack = gear.pocket(slot, index);
			gear.setPocket(slot, index, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int index, ItemStack stack) {
			gear.setPocket(slot, index, stack);
		}

		@Override
		public void setChanged() {
			gear.commit();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < gear.pocketCount(slot); i++) {
				gear.setPocket(slot, i, ItemStack.EMPTY);
			}
		}
	}
}
