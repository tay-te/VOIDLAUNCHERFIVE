package dev.voidmc.gloam.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class CaveSurveyTest {
	private static final int EYE_Y = 20;

	private static CaveProbe open(float space) {
		return new CaveProbe(true, space, EYE_Y - (int) space, EYE_Y + (int) space, CaveKinds.WATER_NONE, Integer.MIN_VALUE, 1000, CaveKinds.WILDS,
			0, false, false, true, false);
	}

	private static CaveProbe river(float distance, float great, int surface) {
		return new CaveProbe(true, 20, EYE_Y - 4, EYE_Y + 40, CaveKinds.RIVER, surface, distance, CaveKinds.REALM_NONE, great, great > 0.25F,
			great > 0.25F, true, false);
	}

	/** Runs one whole survey, the eye and each ring slot answered by the functions given. */
	private static Hollow survey(CaveProbe eye, IntFunction<CaveProbe> ring, float cover, int sky) {
		CaveSurvey s = new CaveSurvey();
		boolean done = false;
		for (int k = 0; k < CaveSurvey.CYCLE; k++) {
			int slot = s.next();
			assertFalse(done, "the survey ended early");
			done = s.put(slot == CaveSurvey.EYE ? eye : ring.apply(slot));
		}
		assertTrue(done, "a survey takes CYCLE ticks");
		return s.result(EYE_Y, cover, sky, 0);
	}

	@Test
	void everyRingSlotIsAskedOnceAndTheEyeEveryThirdTick() {
		CaveSurvey s = new CaveSurvey();
		boolean[] seen = new boolean[16];
		int eyes = 0;
		for (int k = 0; k < CaveSurvey.CYCLE; k++) {
			int slot = s.next();
			if (slot == CaveSurvey.EYE) {
				eyes++;
			} else {
				assertFalse(seen[slot], "slot " + slot + " twice");
				seen[slot] = true;
			}
			s.put(null);
		}
		for (boolean b : seen) {
			assertTrue(b);
		}
		assertEquals(CaveSurvey.CYCLE / 3, eyes);
	}

	@Test
	void aTightPassageIsClosedAndAGreatCavernOpen() {
		Hollow passage = survey(open(2), slot -> CaveProbe.rock(EYE_Y), 30, 0);
		Hollow cavern = survey(open(30), slot -> open(25), 30, 0);
		assertTrue(passage.openness() < 0.05F, "passage " + passage.openness());
		assertTrue(cavern.openness() > 0.95F, "cavern " + cavern.openness());
		assertEquals(60, cavern.vault(), 1e-4);
		assertEquals(1, passage.under(), 1e-4);
	}

	@Test
	void aCaveMouthThatSeesTheSkyIsNotUndergroundYet() {
		assertSame(Hollow.NONE, survey(open(4), slot -> open(4), 30, 15));
		assertSame(Hollow.NONE, survey(open(4), slot -> open(4), 1, 0), "under a ledge");
		Hollow halfway = survey(open(4), slot -> open(4), 6, 0);
		assertTrue(halfway.under() > 0.2F && halfway.under() < 0.8F, "half in: " + halfway.under());
	}

	@Test
	void theRiverIsHeardFromItsSideAndItsGreatnessCarries() {
		// the river runs along the east: only the probes out east see it close
		Hollow h = survey(open(6), slot -> CaveSurvey.offsetX(slot) > 5 ? river(2, 1, EYE_Y - 2) : open(6), 30, 0);
		assertTrue(h.river() > 0.7F, "near: " + h.river());
		assertEquals(1, h.great(), 1e-4);
		assertTrue(h.riverX() > 3 && Math.abs(h.riverZ()) < 1, "east: " + h.riverX() + ", " + h.riverZ());
		assertEquals(CaveKinds.RIVER, h.water());
		assertTrue(h.greatNearness() > 0.7F);
		assertTrue(h.glowworms() > 0 && h.bats() > 0);
	}

	@Test
	void aRiverFarBelowIsHardlyHeard() {
		Hollow level = survey(river(4, 1, EYE_Y - 2), slot -> river(4, 1, EYE_Y - 2), 30, 0);
		Hollow below = survey(river(4, 1, EYE_Y - 70), slot -> river(4, 1, EYE_Y - 70), 30, 0);
		assertTrue(level.river() > 0.8F);
		assertEquals(0, below.river(), 1e-4, "seventy blocks of rock between");
		assertEquals(CaveKinds.WATER_NONE, below.water());
	}

	@Test
	void waterAllAroundHasNoDirection() {
		Hollow h = survey(river(0, 0, EYE_Y - 1), slot -> river(0, 0, EYE_Y - 1), 30, 0);
		assertTrue(Math.abs(h.riverX()) < 0.5F && Math.abs(h.riverZ()) < 0.5F, h.riverX() + ", " + h.riverZ());
	}

	@Test
	void unaskedProbesAreLeftOut() {
		Hollow h = survey(open(30), slot -> null, 30, 0);
		assertTrue(h.openness() > 0.95F, "the eye alone: " + h.openness());
		Hollow none = survey(null, slot -> null, 30, 0);
		assertEquals(0, none.openness(), 0);
		assertTrue(none.under() > 0.9F, "under the land, from the cover alone");
	}

	@Test
	void theUniformsMatchTheContract() {
		Hollow h = survey(river(0, 1, EYE_Y - 1), slot -> river(0, 1, EYE_Y - 1), 60, 0);
		float[] u = h.undergroundUniform(), c = h.caveUniform();
		assertEquals(4, u.length);
		assertEquals(4, c.length);
		assertTrue(u[0] > 0.99F, "deep: " + u[0]);
		assertEquals(CaveKinds.REALM_NONE, u[2], 0);
		assertEquals(CaveKinds.RIVER, u[3], 0);
		assertEquals(1, c[0], 1e-4);
		assertEquals(1, c[1], 1e-4);
		assertEquals(1, c[2], 1e-4);
		assertEquals(1, c[3], 1e-4);
	}

	@Test
	void kindsRoundTripAndUnknownsAreNone() {
		for (String r : new String[]{"wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal", "amber",
			"brimstone", "sea"}) {
			assertEquals(r, CaveKinds.realmName(CaveKinds.realm(r)));
		}
		assertEquals(CaveKinds.SEA_REALM, CaveKinds.realm("sea"));
		assertEquals(CaveKinds.REALM_NONE, CaveKinds.realm("moon"));
		assertEquals(CaveKinds.LAVA, CaveKinds.water("lava"));
		assertEquals(CaveKinds.WATER_NONE, CaveKinds.water(null));
	}

	@Test
	void easingReachesItsTargetAndSwitchesIdsAtOnce() {
		Hollow target = survey(river(2, 1, EYE_Y - 2), slot -> river(2, 1, EYE_Y - 2), 30, 0);
		Hollow h = Hollow.NONE.toward(target, 0.5F);
		assertEquals(target.river() * 0.5F, h.river(), 1e-5);
		assertEquals(CaveKinds.RIVER, h.water());
		assertEquals(target, h.toward(target, 1));
	}
}
