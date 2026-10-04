package dev.voidmc.hub.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans Sort and Move-all as sequences of ordinary vanilla clicks.
 *
 * <p>There is no server side to this mod, so nothing here moves an item the player could not move
 * by hand: Sort is left-click pick up / put down / swap, Move-all is shift-click. Every click goes
 * through the real container, the server checks each one, and a client whose prediction is wrong
 * is corrected the same way a fast player's would be.
 */
public final class ClickPlanner {

	private ClickPlanner() {
	}

	/** Shift-click every non-empty slot of the source region, in order. */
	public static List<Click> moveAll(int[] sourceSlots, StackView[] stacks) {
		List<Click> out = new ArrayList<>();
		for (int i = 0; i < sourceSlots.length; i++) {
			if (!stacks[i].isEmpty()) {
				out.add(Click.quickMove(sourceSlots[i]));
			}
		}
		return out;
	}

	/**
	 * Merges partial stacks, then orders the region by sort key (then larger stacks first), filled
	 * slots first and empties last. Left clicks only (PICKUP button 0), and the plan always ends
	 * with nothing on the cursor.
	 *
	 * @param slots the region's menu slot indices, in reading order
	 * @param start the region's stacks, parallel to {@code slots}; not modified
	 */
	public static List<Click> sort(int[] slots, StackView[] start) {
		PickupModel m = new PickupModel(start);
		List<Click> out = new ArrayList<>();

		// 1. Merge: fold every later partial stack of an item into the earliest partial one.
		for (int a = 0; a < m.size(); a++) {
			for (int b = a + 1; b < m.size(); b++) {
				StackView sa = m.get(a);
				StackView sb = m.get(b);
				if (!sa.sameItem(sb) || sa.count() >= sa.max()) {
					continue;
				}
				m.click(b, out, slots);  // pick up b
				m.click(a, out, slots);  // merge into a; any overflow stays on the cursor
				if (!m.carried().isEmpty()) {
					m.click(b, out, slots);  // put the overflow back where it came from
				}
			}
		}

		// 2. Order: selection sort by swaps, matching on (key, count) so equal stacks never move.
		List<StackView> want = new ArrayList<>();
		for (int i = 0; i < m.size(); i++) {
			if (!m.get(i).isEmpty()) {
				want.add(m.get(i));
			}
		}
		want.sort(ORDER);
		for (int p = 0; p < want.size(); p++) {
			StackView target = want.get(p);
			if (same(m.get(p), target)) {
				continue;
			}
			int q = -1;
			for (int j = p + 1; j < m.size(); j++) {
				if (same(m.get(j), target)) {
					q = j;
					break;
				}
			}
			if (q < 0) {
				continue;  // cannot happen for a consistent model; never risk a stray click
			}
			m.click(q, out, slots);        // carry the wanted stack
			m.click(p, out, slots);        // drop it at p, picking up whatever was there
			if (!m.carried().isEmpty()) {
				m.click(q, out, slots);    // and park that where the wanted stack came from
			}
		}
		return out;
	}

	/** Sort order: sort key, then bigger stacks first. */
	public static final Comparator<StackView> ORDER = Comparator.comparing(StackView::sortKey)
		.thenComparing(StackView::key)
		.thenComparing(Comparator.comparingInt(StackView::count).reversed());

	private static boolean same(StackView a, StackView b) {
		return a.count() == b.count() && a.key().equals(b.key()) && !a.isEmpty();
	}

	/**
	 * A region of slots plus the cursor, with vanilla's left-click PICKUP rules: pick up a whole
	 * stack, put a whole stack down, merge into the same item up to its max (the rest stays on
	 * the cursor), or swap with a different item. Public so tests can replay a plan.
	 */
	public static final class PickupModel {
		private final StackView[] slots;
		private StackView carried = StackView.EMPTY;

		public PickupModel(StackView[] start) {
			this.slots = start.clone();
		}

		public int size() {
			return this.slots.length;
		}

		public StackView get(int i) {
			return this.slots[i];
		}

		public StackView carried() {
			return this.carried;
		}

		void click(int i, List<Click> out, int[] slotIds) {
			out.add(Click.pickup(slotIds[i]));
			this.leftClick(i);
		}

		/** Applies one left click on region index {@code i}. */
		public void leftClick(int i) {
			StackView s = this.slots[i];
			if (this.carried.isEmpty()) {
				this.carried = s;
				this.slots[i] = StackView.EMPTY;
			} else if (s.isEmpty()) {
				this.slots[i] = this.carried;
				this.carried = StackView.EMPTY;
			} else if (s.sameItem(this.carried)) {
				int room = Math.max(0, s.max() - s.count());
				int moved = Math.min(room, this.carried.count());
				this.slots[i] = s.withCount(s.count() + moved);
				this.carried = this.carried.withCount(this.carried.count() - moved);
			} else {
				this.slots[i] = this.carried;
				this.carried = s;
			}
		}
	}
}
