package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.model.Click;
import dev.voidmc.hub.model.ClickPlanner;
import dev.voidmc.hub.model.StackView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Click mapping for the VOID-only gestures (Sort, Move all) and the vanilla click encoding they and
 * the screen use. Plans are replayed on a model of vanilla's left-click PICKUP rules.
 */
class ClickPlannerTest {

	static StackView s(String item, int count) {
		return new StackView(item, item.startsWith("tool") ? "0:" + item : "4:" + item, count, item.startsWith("tool") ? 1 : 64);
	}

	static int[] slots(int first, int n) {
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = first + i;
		}
		return out;
	}

	/** Replays a plan; every click must be a left PICKUP on a slot of the region. */
	static ClickPlanner.PickupModel replay(int[] slots, StackView[] start, List<Click> plan) {
		ClickPlanner.PickupModel m = new ClickPlanner.PickupModel(start);
		Map<Integer, Integer> index = new HashMap<>();
		for (int i = 0; i < slots.length; i++) {
			index.put(slots[i], i);
		}
		for (Click c : plan) {
			assertEquals(Click.Action.PICKUP, c.action());
			assertEquals(0, c.button(), "sort only ever left-clicks");
			assertTrue(index.containsKey(c.slot()), "click on slot " + c.slot() + " outside the region");
			m.leftClick(index.get(c.slot()));
		}
		return m;
	}

	static Map<String, Integer> totals(StackView[] stacks) {
		Map<String, Integer> t = new HashMap<>();
		for (StackView v : stacks) {
			if (!v.isEmpty()) {
				t.merge(v.key(), v.count(), Integer::sum);
			}
		}
		return t;
	}

	static void assertSortedAndMerged(int[] slots, StackView[] start, List<Click> plan) {
		ClickPlanner.PickupModel m = replay(slots, start, plan);
		assertTrue(m.carried().isEmpty(), "a sort must end with nothing on the cursor");
		StackView[] end = new StackView[m.size()];
		for (int i = 0; i < end.length; i++) {
			end[i] = m.get(i);
		}
		assertEquals(totals(start), totals(end), "no item may be lost or duplicated");
		boolean seenEmpty = false;
		Map<String, Integer> partials = new HashMap<>();
		for (int i = 0; i < end.length; i++) {
			if (end[i].isEmpty()) {
				seenEmpty = true;
				continue;
			}
			assertTrue(!seenEmpty, "filled slots first, empties last");
			if (end[i].count() < end[i].max()) {
				partials.merge(end[i].key(), 1, Integer::sum);
			}
			if (i > 0 && !end[i - 1].isEmpty()) {
				assertTrue(ClickPlanner.ORDER.compare(end[i - 1], end[i]) <= 0, "out of order at " + i);
			}
		}
		for (Map.Entry<String, Integer> e : partials.entrySet()) {
			assertEquals(1, e.getValue(), "more than one partial stack of " + e.getKey() + " left unmerged");
		}
	}

	@Test
	void sortMergesAndOrders() {
		int[] slots = slots(9, 9);
		StackView[] start = {s("dirt", 10), StackView.EMPTY, s("stone", 64), s("dirt", 60), s("tool:pick", 1), StackView.EMPTY, s("apple", 3), s("dirt", 64), s("stone", 5)};
		List<Click> plan = ClickPlanner.sort(slots, start);
		assertSortedAndMerged(slots, start, plan);
	}

	@Test
	void sortingASortedRegionClicksNothing() {
		int[] slots = slots(0, 5);
		StackView[] start = {s("tool:axe", 1), s("apple", 5), s("dirt", 64), s("dirt", 3), StackView.EMPTY};
		assertEquals(List.of(), ClickPlanner.sort(slots, start));
	}

	@RepeatedTest(200)
	void sortHoldsForRandomRegions(org.junit.jupiter.api.RepetitionInfo info) {
		Random r = new Random(info.getCurrentRepetition() * 7919L);
		String[] items = {"dirt", "stone", "apple", "sand", "tool:sword", "tool:pick"};
		int n = 1 + r.nextInt(27);
		StackView[] start = new StackView[n];
		for (int i = 0; i < n; i++) {
			if (r.nextInt(4) == 0) {
				start[i] = StackView.EMPTY;
			} else {
				String it = items[r.nextInt(items.length)];
				start[i] = s(it, it.startsWith("tool") ? 1 : 1 + r.nextInt(64));
			}
		}
		int[] slots = slots(54, n);
		assertSortedAndMerged(slots, start, ClickPlanner.sort(slots, start));
	}

	@Test
	void moveAllShiftClicksEveryFilledSlotInOrder() {
		int[] slots = slots(0, 6);
		StackView[] start = {s("dirt", 1), StackView.EMPTY, s("stone", 64), StackView.EMPTY, StackView.EMPTY, s("apple", 2)};
		List<Click> plan = ClickPlanner.moveAll(slots, start);
		assertEquals(List.of(Click.quickMove(0), Click.quickMove(2), Click.quickMove(5)), plan);
		for (Click c : plan) {
			assertEquals(Click.Action.QUICK_MOVE, c.action());
		}
		assertEquals(List.of(), ClickPlanner.moveAll(slots(0, 3), new StackView[] {StackView.EMPTY, StackView.EMPTY, StackView.EMPTY}));
	}

	@Test
	void gesturesEncodeAsVanillaClicks() {
		// Minecraft 26.x mouse buttons are SDL's: 1 left, 2 middle, 3 right; container clicks are 0/1.
		assertEquals(0, Click.containerButton(1));
		assertEquals(1, Click.containerButton(3));
		assertEquals(2, Click.containerButton(2));
		// Number key 1 over slot 12 swaps with hotbar 0; F swaps with the offhand (40).
		assertEquals(new Click(12, 0, Click.Action.SWAP), Click.hotbarSwap(12, 0));
		assertEquals(new Click(12, 40, Click.Action.SWAP), Click.hotbarSwap(12, 40));
		// Q drops one; Ctrl+Q drops the stack.
		assertEquals(new Click(9, 0, Click.Action.THROW), Click.drop(9, false));
		assertEquals(new Click(9, 1, Click.Action.THROW), Click.drop(9, true));
		assertEquals(new Click(3, 1, Click.Action.PICKUP), Click.pickupHalf(3));
		assertEquals(new Click(3, 0, Click.Action.PICKUP_ALL), Click.collect(3));
		assertEquals(-999, Click.OUTSIDE);
		// Same names as net.minecraft.world.inventory.ContainerInput, so valueOf maps one to one.
		List<String> names = new ArrayList<>();
		for (Click.Action a : Click.Action.values()) {
			names.add(a.name());
		}
		assertEquals(List.of("PICKUP", "QUICK_MOVE", "SWAP", "CLONE", "THROW", "QUICK_CRAFT", "PICKUP_ALL"), names);
	}

	@Test
	void pickupModelFollowsVanillaRules() {
		ClickPlanner.PickupModel m = new ClickPlanner.PickupModel(new StackView[] {s("dirt", 40), s("dirt", 50), s("stone", 1), StackView.EMPTY});
		m.leftClick(0);  // pick up 40 dirt
		assertEquals(40, m.carried().count());
		m.leftClick(1);  // merge into 50: 64, 26 left on the cursor
		assertEquals(64, m.get(1).count());
		assertEquals(26, m.carried().count());
		m.leftClick(2);  // different item: swap
		assertEquals("dirt", m.get(2).key());
		assertEquals("stone", m.carried().key());
		m.leftClick(3);  // empty: put down
		assertTrue(m.carried().isEmpty());
		assertEquals("stone", m.get(3).key());
	}
}
