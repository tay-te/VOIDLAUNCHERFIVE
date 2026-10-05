package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.api.GearSlot;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The gear module's rules as plain logic (unit-tested): the fixed menu layout (BURDEN.md §6), what a pocket takes,
 * shift-click routing and the capacity gear adds.
 */
public final class GearRules {
	/** Inventory menu index of the Back slot; Belt and Quiver follow. */
	public static final int WORN_START = 46;
	public static final int BACK_POCKETS = 49;
	public static final int BELT_POCKETS = 76;
	public static final int QUIVER_POCKETS = 82;
	/** One past the last gear index: the inventory menu always has exactly this many slots. */
	public static final int END = 86;
	public static final int MAX_POCKETS_ANY = 27;
	/** Belt pockets are for small things (BURDEN.md §5): one item may weigh at most this. */
	public static final float BELT_MAX_KG = 1.0f;

	private GearRules() {
	}

	public static int maxPockets(GearSlot slot) {
		return switch (slot) {
			case BACK -> 27;
			case BELT -> 6;
			case QUIVER -> 4;
		};
	}

	public static int wornIndex(GearSlot slot) {
		return WORN_START + slot.ordinal();
	}

	public static int pocketStart(GearSlot slot) {
		return switch (slot) {
			case BACK -> BACK_POCKETS;
			case BELT -> BELT_POCKETS;
			case QUIVER -> QUIVER_POCKETS;
		};
	}

	/** Which pocket group a menu index belongs to, or null outside the pockets. */
	public static @Nullable GearSlot pocketGroup(int index) {
		if (index >= QUIVER_POCKETS && index < END) {
			return GearSlot.QUIVER;
		}
		if (index >= BELT_POCKETS) {
			return index < QUIVER_POCKETS ? GearSlot.BELT : null;
		}
		return index >= BACK_POCKETS ? GearSlot.BACK : null;
	}

	/**
	 * Whether a pocket of worn gear takes an item. No gear inside gear; a tag on the gear limits it (quivers take arrows);
	 * belts take only small things.
	 *
	 * @param isGear  the item is itself gear
	 * @param inTag   the item is in the gear's {@code accepts} tag (ignored when the gear has none)
	 * @param hasTag  the gear has an {@code accepts} tag
	 * @param kgEach  the weight of one of the item
	 */
	public static boolean fits(GearSlot slot, boolean isGear, boolean hasTag, boolean inTag, float kgEach) {
		if (isGear) {
			return false;
		}
		if (hasTag) {
			return inTag;
		}
		return slot != GearSlot.BELT || kgEach <= BELT_MAX_KG;
	}

	/** Carry capacity the worn gear adds, in kg. */
	public static float capacity(List<@Nullable GearSpec> worn) {
		float total = 0f;
		for (GearSpec spec : worn) {
			if (spec != null) {
				total += spec.capacityKg();
			}
		}
		return total;
	}

	/** What the shift-clicked stack is, from the gear module's point of view. */
	public record MoveContext(
		@Nullable GearSlot gearSlot,   // where the stack is worn, if it is gear
		boolean gearSlotFree,          // that slot is empty
		boolean vanillaEquips,         // vanilla would put it on (armour or offhand slot free)
		boolean quiverTakes, int quiverPockets,
		boolean backTakes, int backPockets,
		boolean creative) {
	}

	/** Menu ranges to try in order, {@code [from, to)}, and whether vanilla's routing then gets the rest. */
	public record Route(List<int[]> ranges, boolean thenVanilla) {
	}

	/**
	 * Shift-click routing in the inventory (BURDEN.md §6): gear to its slot, arrows to the quiver, a worn pack first for
	 * everything else, pockets and worn gear back to the main inventory. Belt pockets are only ever filled by hand.
	 *
	 * @return null when vanilla should handle the click untouched
	 */
	public static @Nullable Route quickMove(int index, MoveContext c) {
		if (index >= WORN_START && index < END) {
			return new Route(List.<int[]>of(new int[] {9, 45}), false);
		}
		if (index < 9 || index > 45) {
			return null;
		}
		if (c.gearSlot() != null && c.gearSlotFree()) {
			int at = wornIndex(c.gearSlot());
			return new Route(List.<int[]>of(new int[] {at, at + 1}), true);
		}
		if (c.vanillaEquips() || c.creative()) {
			// Creative hides the pockets, so nothing may vanish into them there.
			return null;
		}
		List<int[]> ranges = new ArrayList<>();
		if (c.quiverTakes() && c.quiverPockets() > 0) {
			ranges.add(new int[] {QUIVER_POCKETS, QUIVER_POCKETS + c.quiverPockets()});
		}
		if (c.backTakes() && c.backPockets() > 0) {
			ranges.add(new int[] {BACK_POCKETS, BACK_POCKETS + c.backPockets()});
		}
		return ranges.isEmpty() ? null : new Route(ranges, true);
	}
}
