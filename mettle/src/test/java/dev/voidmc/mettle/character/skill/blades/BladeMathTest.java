package dev.voidmc.mettle.character.skill.blades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BladeMathTest {
	@Test
	void rootsScaleByRank() {
		assertEquals(0.2f, BladeMath.quickDraw(1), 1e-6);
		assertEquals(0.6f, BladeMath.quickDraw(3), 1e-6);
		assertEquals(0f, BladeMath.quickDraw(0), 1e-6);
		assertEquals(0.15, BladeMath.fencer(3), 1e-9);
		assertEquals(0.15, BladeMath.fencer(7), 1e-9);
	}

	@Test
	void momentumBuildsOnOneFoeAndCapsAtFour() {
		int stacks = BladeMath.momentum(0, false, 0);
		assertEquals(0, stacks);
		for (int expected = 1; expected <= 4; expected++) {
			stacks = BladeMath.momentum(stacks, true, 12);
			assertEquals(expected, stacks);
		}
		assertEquals(4, BladeMath.momentum(stacks, true, 12));
		assertEquals(0.4f, BladeMath.momentumBonus(4), 1e-6);
		// A pause longer than 2 s, or a new foe, starts again.
		assertEquals(0, BladeMath.momentum(4, true, 41));
		assertEquals(1, BladeMath.momentum(0, true, 40));
		assertEquals(0, BladeMath.momentum(3, false, 5));
	}

	@Test
	void executesBelowAQuarter() {
		assertTrue(BladeMath.executes(4.9f, 20f));
		assertFalse(BladeMath.executes(5f, 20f));
		assertFalse(BladeMath.executes(0f, 20f));
		assertTrue(BladeMath.finishingBlow(10f, 6f, 20f));
		assertFalse(BladeMath.finishingBlow(20f, 6f, 20f));
	}

	@Test
	void unbrokenNeedsFiveQuietSeconds() {
		assertTrue(BladeMath.unbrokenReady(1000, 900, 0));
		assertFalse(BladeMath.unbrokenReady(1000, 901, 0));
		assertFalse(BladeMath.unbrokenReady(1000, 0, 950));
		assertTrue(BladeMath.unbrokenReady(1000, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2));
	}

	@Test
	void parryFacesForwards() {
		assertTrue(BladeMath.inFront(0, 1, 0.2, 3));
		assertFalse(BladeMath.inFront(0, 1, 0.2, -3));
		assertFalse(BladeMath.inFront(1, 0, 0, 2));
	}

	@Test
	void woundsStackToThreeAndBleedFourSeconds() {
		BladeMath.Wounds w = new BladeMath.Wounds();
		assertEquals(1, w.open());
		assertEquals(1, w.tick());
		assertEquals(2, w.open());
		assertEquals(3, w.open());
		// A fourth wound reopens the one nearest its end instead of stacking.
		assertEquals(3, w.open());
		int total = 0;
		while (!w.healed()) {
			total += w.tick();
		}
		// First wound: 1 + 4 after reopening; the other two: 4 each.
		assertEquals(12, total);
		assertEquals(0, w.tick());

		BladeMath.Wounds one = new BladeMath.Wounds();
		one.open();
		int dealt = 0;
		for (int s = 0; s < 6; s++) {
			dealt += one.tick();
		}
		assertEquals(4, dealt);
	}
}
