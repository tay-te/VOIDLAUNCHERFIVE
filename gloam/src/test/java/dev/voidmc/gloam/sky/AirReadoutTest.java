package dev.voidmc.gloam.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.bridge.AirReadout;
import org.junit.jupiter.api.Test;

class AirReadoutTest {
	private static final org.joml.Vector3fc[] STEPS = {Palette.GOLD, Palette.ORANGE, Palette.ROSE, Palette.VIOLET, Palette.DUSK_BLUE};

	@Test
	void sunsetStepIsTwilightsColour() {
		for (long clock = -48_000; clock < 48_000; clock += 5) {
			assertSame(Twilight.colour(clock), STEPS[AirReadout.sunsetStep(clock)], "clock " + clock);
		}
	}

	@Test
	void hazeRunsFromAFarLightHazeToMurk() {
		assertEquals(Math.log(3.0 / 8192), AirReadout.logExtinction(0), 1e-5);
		assertEquals(Math.log(3.0 / 80), AirReadout.logExtinction(1), 1e-5);
		float last = -100;
		for (float h = 0; h <= 1; h += 0.05F) {
			float x = AirReadout.logExtinction(h);
			assertTrue(x > last && x >= -8 && x <= 8);
			last = x;
		}
	}

	@Test
	void vividMapsTheDaysRangeOntoZeroToOne() {
		assertEquals(0, AirReadout.vivid(0.75F), 1e-6);
		assertEquals(1, AirReadout.vivid(1.5F), 1e-6);
		for (long day = 0; day < 200; day++) {
			float v = AirReadout.vivid(Twilight.vivid(99, day * 24000 + 12500, 0.3F));
			assertTrue(v >= 0 && v <= 1);
		}
	}

	@Test
	void theCurtainIsCapped() {
		assertEquals(0.8F, AirReadout.curtain(1, 1, 1, 1), 1e-6);
		assertEquals(0, AirReadout.curtain(0, 0, 1, 0), 1e-6);
	}
}
