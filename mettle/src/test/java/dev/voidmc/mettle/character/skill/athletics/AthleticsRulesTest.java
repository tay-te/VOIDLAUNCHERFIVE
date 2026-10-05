package dev.voidmc.mettle.character.skill.athletics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules.Move;
import org.junit.jupiter.api.Test;

/** BALANCE.md §3's Athletics XP and TREES.md §8's numbers. */
class AthleticsRulesTest {
	private static final double EPS = 1e-6;

	@Test
	void xpPerDistance() {
		assertEquals(1, AthleticsRules.xp(Move.SPRINT, 4000, LoadBand.LIGHT), EPS);
		assertEquals(1, AthleticsRules.xp(Move.SWIM, 2000, LoadBand.LIGHT), EPS);
		assertEquals(1, AthleticsRules.xp(Move.CLIMB, 1000, LoadBand.LIGHT), EPS);
		assertEquals(2, AthleticsRules.xp(Move.SPRINT, 4000, LoadBand.BURDENED), EPS);
		assertEquals(3, AthleticsRules.xp(Move.SPRINT, 4000, LoadBand.OVERBURDENED), EPS);
		assertEquals(3, AthleticsRules.xp(Move.CLIMB, 1000, LoadBand.PINNED), EPS);
	}

	@Test
	void capIsPerMinute() {
		AthleticsRules.Meter meter = new AthleticsRules.Meter();
		assertEquals(100, meter.take(100, 0), EPS);
		assertEquals(20, meter.take(50, 600), EPS);
		assertEquals(0, meter.take(5, 1199), EPS);
		// A new minute pays again.
		assertEquals(5, meter.take(5, 1200), EPS);
		assertEquals(5, meter.spent(), EPS);
		assertEquals(115, meter.remaining(1300), EPS);
		assertEquals(120, meter.remaining(2400), EPS);
	}

	@Test
	void porterBands() {
		BandRules d = BandRules.DEFAULT;
		assertEquals(d, AthleticsRules.bands(d, false, false, false, false, 0.5f));
		// Strong Back: Burdened from 85 %.
		BandRules strong = AthleticsRules.bands(d, true, false, false, false, 0.5f);
		assertEquals(0.85f, strong.burdened(), EPS);
		assertEquals(1.0f, strong.overburdened(), EPS);
		assertEquals(1.25f, strong.pinned(), EPS);
		assertEquals(1.0f, strong.penaltyScale(), EPS);
		assertTrue(strong.canBePinned());
		// Beast of Burden: never pinned.
		assertFalse(AthleticsRules.bands(d, false, true, false, false, 2f).canBePinned());
		// Ox: half the penalty.
		assertEquals(0.5f, AthleticsRules.bands(d, false, false, true, false, 0.5f).penaltyScale(), EPS);
		// Steady Pace: no burden penalty while Burdened (its own −5 % stands in), the full one outside it.
		assertEquals(0f, AthleticsRules.bands(d, false, false, false, true, 0.8f).penaltyScale(), EPS);
		assertEquals(1f, AthleticsRules.bands(d, false, false, false, true, 0.6f).penaltyScale(), EPS);
		assertEquals(1f, AthleticsRules.bands(d, false, false, false, true, 1.1f).penaltyScale(), EPS);
		// With Strong Back, 80 % isn't Burdened yet, so Steady Pace has nothing to soften.
		assertEquals(1f, AthleticsRules.bands(d, true, false, false, true, 0.8f).penaltyScale(), EPS);
		assertEquals(0f, AthleticsRules.bands(d, true, false, false, true, 0.9f).penaltyScale(), EPS);
		// Ox over Steady Pace outside the Burdened band.
		assertEquals(0.5f, AthleticsRules.bands(d, true, true, true, true, 1.1f).penaltyScale(), EPS);
		assertEquals(-0.05, AthleticsRules.steadyPaceSpeed(false), EPS);
		assertEquals(-0.025, AthleticsRules.steadyPaceSpeed(true), EPS);
	}

	@Test
	void sprintGate() {
		// Vanilla's yes stands, and Pinned is never lifted.
		assertTrue(AthleticsRules.canSprint(true, true, LoadBand.LIGHT, false, false));
		assertFalse(AthleticsRules.canSprint(false, false, LoadBand.PINNED, true, true));
		// Marathon: any hunger, but not past burden's Overburdened refusal.
		assertTrue(AthleticsRules.canSprint(false, false, LoadBand.LIGHT, true, false));
		assertTrue(AthleticsRules.canSprint(false, false, LoadBand.BURDENED, true, false));
		assertFalse(AthleticsRules.canSprint(false, false, LoadBand.OVERBURDENED, true, false));
		assertFalse(AthleticsRules.canSprint(false, false, LoadBand.LIGHT, false, false));
		// With food enough, a no came from someone else.
		assertFalse(AthleticsRules.canSprint(false, true, LoadBand.LIGHT, true, false));
		// Hauler's Stride: sprinting while Overburdened, if not starving (or with Marathon).
		assertTrue(AthleticsRules.canSprint(false, true, LoadBand.OVERBURDENED, false, true));
		assertFalse(AthleticsRules.canSprint(false, false, LoadBand.OVERBURDENED, false, true));
		assertTrue(AthleticsRules.canSprint(false, false, LoadBand.OVERBURDENED, true, true));
	}

	@Test
	void sprintNumbers() {
		assertEquals(0.09, AthleticsRules.sprintSpeed(3, false), EPS);
		assertEquals(0.15, AthleticsRules.sprintSpeed(0, true), EPS);
		// Hauler's Stride halves vanilla's +30 % sprint.
		assertEquals(1.15, 1.3 * (1 + AthleticsRules.HAULERS_SPRINT), EPS);
		assertEquals(0.5f, AthleticsRules.sprintExhaustion(1f, true, false), EPS);
		assertEquals(0f, AthleticsRules.sprintExhaustion(1f, true, true), EPS);
		assertEquals(1f, AthleticsRules.sprintExhaustion(1f, false, false), EPS);
		assertEquals(73.25, AthleticsRules.scrambleTop(70), EPS);
	}

	@Test
	void springLiftsTheApexHalfABlock() {
		assertEquals(1.25, apex(0.42), 0.01);
		assertEquals(1.75, apex(0.42 + AthleticsRules.SPRING_JUMP), 0.05);
	}

	@Test
	void peaksAreFarApart() {
		long[] none = {};
		assertTrue(AthleticsRules.newPeak(none, 0, 0));
		long[] one = {AthleticsRules.packPeak(-300, 1000)};
		assertEquals(-300, AthleticsRules.peakX(one[0]));
		assertEquals(1000, AthleticsRules.peakZ(one[0]));
		assertFalse(AthleticsRules.newPeak(one, 0, 1000));
		assertTrue(AthleticsRules.newPeak(one, 200, 1000));
		assertTrue(AthleticsRules.newPeak(one, -300, 1500));
	}

	@Test
	void structureNames() {
		assertEquals("Village Plains", AthleticsRules.pretty("village_plains"));
		assertEquals("Ruined Portal", AthleticsRules.pretty("ruined_portal"));
	}

	/** Vanilla's jump arc: move by velocity, then gravity 0.08 and drag 0.98. */
	private static double apex(double v) {
		double y = 0;
		while (v > 0) {
			y += v;
			v = (v - 0.08) * 0.98;
		}
		return y;
	}
}
