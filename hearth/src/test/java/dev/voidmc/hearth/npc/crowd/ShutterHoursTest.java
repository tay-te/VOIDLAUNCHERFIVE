package dev.voidmc.hearth.npc.crowd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Shutters keep the rhythm's hours (requests/lwA2.md, 2): shut from the curfew bell to the dawn bell, by culture. */
class ShutterHoursTest {
	@Test
	void shutFromTheCurfewBellToTheDawnBell() {
		int curfew = 21 * 60;
		int dawn = 6 * 60;
		assertFalse(Shutters.night(20 * 60 + 59, curfew, dawn));
		assertTrue(Shutters.night(21 * 60, curfew, dawn));
		assertTrue(Shutters.night(2 * 60, curfew, dawn));
		assertTrue(Shutters.night(5 * 60 + 59, curfew, dawn));
		assertFalse(Shutters.night(6 * 60, curfew, dawn));
		// a southern town's late curfew: still open at half past ten
		assertFalse(Shutters.night(22 * 60 + 29, 22 * 60 + 30, dawn));
		assertTrue(Shutters.night(22 * 60 + 30, 22 * 60 + 30, dawn));
		// a curfew past midnight
		assertFalse(Shutters.night(23 * 60, 30, dawn));
		assertTrue(Shutters.night(60, 30, dawn));
	}
}
