package dev.voidmc.mettle.character.skill.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The Survival tree's sums: kill shares, food fill, the weather clock, Pack Horse's distance and the anti-farm taper. */
class SurvivalRulesTest {
	@Test
	void killBelongsToWhoeverDealtHalf() {
		assertTrue(SurvivalRules.ownsKill(5, 10));
		assertTrue(SurvivalRules.ownsKill(10, 10));
		assertFalse(SurvivalRules.ownsKill(1, 11));
		assertFalse(SurvivalRules.ownsKill(0, 0));
	}

	@Test
	void fieldCookAndWildFood() {
		assertEquals(1.0, SurvivalRules.fillMultiplier(false, 3, false, true));
		assertEquals(1.3, SurvivalRules.fillMultiplier(true, 3, false, false), 1e-9);
		assertEquals(2.0, SurvivalRules.fillMultiplier(false, 0, true, true));
		assertEquals(1.0, SurvivalRules.fillMultiplier(false, 0, true, false));
		// Cooked beef (8) with Field Cook III: 10.4, rounds to 10; sweet berries (2) doubled: 4.
		assertEquals(10, SurvivalRules.nutrition(8, 1.3));
		assertEquals(4, SurvivalRules.nutrition(2, 2.0));
		// A tiny bonus never rounds a bite down.
		assertEquals(1, SurvivalRules.nutrition(1, 1.1));
	}

	@Test
	void rainClock() {
		assertEquals(-1, SurvivalRules.ticksUntilRain(0, 500, true));
		assertEquals(2401, SurvivalRules.ticksUntilRain(2400, 1, false));
		assertEquals(2400, SurvivalRules.ticksUntilRain(0, 2400, false));
		assertEquals(-1, SurvivalRules.ticksUntilRain(0, 0, false));
	}

	@Test
	void stormClock() {
		// Raining, thunder due before the rain stops: a storm then.
		assertEquals(2400, SurvivalRules.ticksUntilStorm(0, 9000, true, 2400, false));
		// Raining, but the rain stops first: no storm.
		assertEquals(-1, SurvivalRules.ticksUntilStorm(0, 1000, true, 2400, false));
		// Thundering dry; rain arrives while it still thunders.
		assertEquals(2400, SurvivalRules.ticksUntilStorm(0, 2400, false, 5000, true));
		assertEquals(-1, SurvivalRules.ticksUntilStorm(0, 6000, false, 5000, true));
		// Both due: the storm starts with the later one.
		assertEquals(3000, SurvivalRules.ticksUntilStorm(0, 3000, false, 1000, false));
		assertEquals(-1, SurvivalRules.ticksUntilStorm(0, 100, true, 100, true));
	}

	@Test
	void warningLandsOnceTwoMinutesOut() {
		int warnings = 0;
		for (int t = 3000; t > 0; t -= Weatherwise.INTERVAL) {
			if (SurvivalRules.warnNow(t, Weatherwise.INTERVAL)) {
				warnings++;
			}
		}
		assertEquals(1, warnings);
		assertFalse(SurvivalRules.warnNow(-1, 20));
	}

	@Test
	void packHorseCountsOnlyHeavyWalking() {
		assertEquals(5.0, SurvivalRules.packHorseStep(3, 4, true, true), 1e-9);
		assertEquals(0.0, SurvivalRules.packHorseStep(3, 4, false, true));
		assertEquals(0.0, SurvivalRules.packHorseStep(3, 4, true, false));
		// A teleport is not a walk.
		assertEquals(0.0, SurvivalRules.packHorseStep(100, 0, true, true));
		double walked = 0;
		for (int i = 0; i < 250; i++) {
			walked += SurvivalRules.packHorseStep(4, 0, true, true);
		}
		assertEquals(1000.0, walked, 1e-9);
	}

	@Test
	void repetitionTapersAndRecovers() {
		Repetition rep = new Repetition();
		for (int i = 0; i < Repetition.FREE; i++) {
			assertEquals(1.0, rep.next(i));
		}
		assertEquals(Repetition.TAPERED, rep.next(Repetition.FREE));
		// Five minutes on, the old ones have aged out.
		assertEquals(1.0, rep.next(Repetition.WINDOW + Repetition.FREE + 1));
	}

	@Test
	void landmarkKinds() {
		assertEquals(Identifier.fromNamespaceAndPath("minecraft", "abandoned_camp"),
			Landmarks.kind(Identifier.fromNamespaceAndPath("minecraft", "abandoned_camp_swamp")));
		assertEquals(Identifier.fromNamespaceAndPath("minecraft", "village_plains"),
			Landmarks.kind(Identifier.fromNamespaceAndPath("minecraft", "village_plains")));
		assertEquals("Village plains", Landmarks.prettify("village_plains"));
	}
}
