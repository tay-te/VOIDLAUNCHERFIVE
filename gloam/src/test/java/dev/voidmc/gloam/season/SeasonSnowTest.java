package dev.voidmc.gloam.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.bridge.SeasonSnow;
import org.junit.jupiter.api.Test;

/** The bridge's snowline height agrees with the snowline's own rule, solved the other way round. */
class SeasonSnowTest {
	private static final int SEA = 63;

	/** Vanilla's height-adjusted temperature (without its small noise). */
	private static float vanilla(float base, int y) {
		return base - Math.max(0, y - (SEA + 17)) * 0.05F / 40.0F;
	}

	@Test
	void itSnowsJustAboveTheHeightAndRainsJustBelow() {
		for (float year = 0; year < 1; year += 1 / 32.0F) {
			SeasonColours c = SeasonColours.at(year);
			float cold = c.cold(), summer = Math.max(0, c.warmth());
			for (float base : new float[]{0.25F, 0.5F, 0.7F, 0.8F, 0.95F, 1.2F}) {
				float h = SeasonSnow.height(base, SEA, cold, summer);
				if (h <= SeasonSnow.ALWAYS || h >= SeasonSnow.NEVER) {
					continue;
				}
				int above = (int) Math.ceil(h) + 1, below = (int) Math.floor(h) - 1;
				assertTrue(Snowline.temperature(vanilla(base, above), base, above, SEA, c) < 0.15F, "snow above at " + year + " " + base);
				if (below > SEA + 17) {
					assertTrue(Snowline.temperature(vanilla(base, below), base, below, SEA, c) >= 0.15F, "rain below at " + year + " " + base);
				}
			}
		}
	}

	@Test
	void aColdBiomeSnowsEverywhereAndADesertNever() {
		assertEquals(SeasonSnow.ALWAYS, SeasonSnow.height(0.0F, SEA, 0, 0));
		assertEquals(SeasonSnow.NEVER, SeasonSnow.height(2.0F, SEA, 0, 1));
	}
}
