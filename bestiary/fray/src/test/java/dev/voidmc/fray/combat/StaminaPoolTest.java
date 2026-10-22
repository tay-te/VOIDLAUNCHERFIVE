package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.combat.math.StaminaPool;
import org.junit.jupiter.api.Test;

/** Stamina (CONTRACTS.md §7.2, COMBAT.md §8): costs, the 12-tick regen delay, half rate guarding, Winded. */
class StaminaPoolTest {
	@Test
	void regenWaitsTwelveTicksThenRunsAtTwentyFiveASecond() {
		StaminaPool s = new StaminaPool(100);
		s.spend(50, 100);
		for (long t = 101; t < 112; t++) {
			s.tick(t, 25, false);
			assertEquals(50, s.value, 1e-4, "no regen at tick " + t);
		}
		assertEquals(1, s.regenDelayLeft(111));
		s.tick(112, 25, false);
		assertEquals(51.25, s.value, 1e-4, "25 a second = 1.25 a tick");
		for (long t = 113; t < 132; t++) {
			s.tick(t, 25, false);
		}
		assertEquals(75, s.value, 1e-3, "one second of regen");
	}

	@Test
	void halfWhileGuarding() {
		StaminaPool s = new StaminaPool(100);
		s.spend(50, 0);
		s.tick(20, 25, true);
		assertEquals(50.625, s.value, 1e-4);
	}

	@Test
	void reachingZeroWinds() {
		StaminaPool s = new StaminaPool(100);
		assertFalse(s.spend(60, 0));
		assertTrue(s.canAfford(60), "any stamina left pays for an action");
		assertTrue(s.spend(60, 1), "the spend that empties the pool winds");
		assertEquals(0, s.value);
		assertTrue(s.winded(1));
		assertTrue(s.winded(30));
		assertFalse(s.winded(31), "Winded lasts 30 ticks");
		assertFalse(s.canAfford(5), "nothing left");
		assertTrue(s.canAfford(0));
	}

	@Test
	void neverAboveMax() {
		StaminaPool s = new StaminaPool(100);
		s.restore(40);
		assertEquals(100, s.value);
		s.max = 80;
		s.tick(0, 25, false);
		assertEquals(80, s.value);
	}
}
