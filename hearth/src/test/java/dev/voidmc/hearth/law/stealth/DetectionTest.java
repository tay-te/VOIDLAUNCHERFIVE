package dev.voidmc.hearth.law.stealth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DetectionTest {
	private static final double RANGE = 24;
	private static final Detection.Observer EYES = Detection.Observer.ORDINARY;

	private static Detection.Stance stance(int light, boolean sneak, int armour) {
		return new Detection.Stance(light, sneak, false, true, armour, false);
	}

	@Test
	void darknessShortensSight() {
		double lit = Detection.sightRange(RANGE, 1.0, stance(15, false, 0), EYES);
		double dark = Detection.sightRange(RANGE, 1.0, stance(0, false, 0), EYES);
		assertEquals(RANGE, lit, 1e-9);
		assertEquals(RANGE * Detection.DARK_FLOOR, dark, 1e-9);
		// with light ignored, the dark hides nothing
		assertEquals(RANGE, Detection.sightRange(RANGE, 0.0, stance(0, false, 0), EYES), 1e-9);
	}

	@Test
	void sneakingHidesAQuietBodyBetterThanAnArmouredOne() {
		double walking = Detection.sightRange(RANGE, 1.0, stance(12, false, 0), EYES);
		double sneaking = Detection.sightRange(RANGE, 1.0, stance(12, true, 0), EYES);
		double sneakingInPlate = Detection.sightRange(RANGE, 1.0, stance(12, true, 20), EYES);
		assertTrue(sneaking < walking * 0.6);
		assertTrue(sneakingInPlate > sneaking);
		assertTrue(sneakingInPlate < walking);
	}

	@Test
	void aLitRoomIsADangerousRoomToRob() {
		// a thief crouched eight blocks from a folk: seen in a lit room (the light rule), unseen in a dark one
		double lit = Detection.sight(8, true, RANGE, 1.0, stance(12, true, 0), EYES);
		double dark = Detection.sight(8, true, RANGE, 1.0, stance(1, true, 0), EYES);
		assertTrue(lit > 0);
		assertEquals(0, dark, 1e-9);
	}

	@Test
	void outOfViewIsUnseenAndCloseIsPlain() {
		assertEquals(0, Detection.sight(2, false, RANGE, 1.0, stance(15, false, 0), EYES), 1e-9);
		assertEquals(1.0, Detection.sight(1, true, RANGE, 1.0, stance(15, false, 0), EYES), 1e-9);
		double near = Detection.sight(5, true, RANGE, 1.0, stance(15, false, 0), EYES);
		double far = Detection.sight(20, true, RANGE, 1.0, stance(15, false, 0), EYES);
		assertTrue(near > far && far > 0);
	}

	@Test
	void sharpEyesSeeFurther() {
		double ordinary = Detection.sightRange(RANGE, 1.0, stance(8, true, 0), EYES);
		double guard = Detection.sightRange(RANGE, 1.0, stance(8, true, 0), new Detection.Observer(1.25));
		assertEquals(ordinary * 1.25, guard, 1e-9);
	}

	@Test
	void hearingCarriesLoudDeedsAndWallsMuffleThem() {
		Detection.Stance still = new Detection.Stance(8, true, false, false, 0, false);
		double open = Detection.hearing(6, 12, still, 1.0, true, EYES, false);
		double walled = Detection.hearing(6, 12, still, 1.0, false, EYES, false);
		double quiet = Detection.hearing(6, 12, still, 0.0, true, EYES, false);
		assertTrue(open > walled);
		assertEquals(0, quiet, 1e-9);
		assertTrue(Detection.hearing(3, 12, still, 1.0, true, EYES, true) < Detection.hearing(3, 12, still, 1.0, true, EYES, false));
	}

	@Test
	void onlyLoudDeedsCountAsWitnessedByEar() {
		assertTrue(Detection.witnessed(0.1, 0, 0));
		assertTrue(Detection.witnessed(0, 0.6, 1.0));
		assertFalse(Detection.witnessed(0, 0.9, 0.3));
		assertFalse(Detection.witnessed(0, 0.4, 1.0));
	}

	@Test
	void armourIsNoisyOnTheMove() {
		Detection.Stance cloth = new Detection.Stance(8, false, false, true, 0, false);
		Detection.Stance plate = new Detection.Stance(8, false, false, true, 20, false);
		Detection.Stance sneakCloth = new Detection.Stance(8, true, false, true, 0, false);
		assertTrue(plate.bodyNoise() > cloth.bodyNoise());
		assertTrue(sneakCloth.bodyNoise() < 0.1);
	}
}
