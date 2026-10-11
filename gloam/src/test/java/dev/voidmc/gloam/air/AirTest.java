package dev.voidmc.gloam.air;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirTest {
	private final Air air = new Air(8675309L, 8);

	@Test
	void theYearRunsThroughTheSeasonsInOrder() {
		long day = Air.TICKS_PER_DAY;
		assertEquals(Season.SPRING, this.air.season(0));
		Season last = Season.SPRING;
		int changes = 0;
		for (long d = 0; d < 32; d++) {
			Season s = this.air.season(d * day);
			if (s != last) {
				assertEquals(Season.values()[(last.ordinal() + 1) % 4], s);
				last = s;
				changes++;
			}
		}
		assertEquals(4, changes, "a 32-day year passes through every season once");
		assertEquals(this.air.year(0), this.air.year(32 * day), 1e-5);
	}

	@Test
	void midsummerIsWarmAndMidwinterCold() {
		assertEquals(1, Air.warmth(0.375F), 1e-4);
		assertEquals(-1, Air.warmth(0.875F), 1e-4);
	}

	@Test
	void theSameSeedGivesTheSameSky() {
		Air other = new Air(8675309L, 8);
		for (int i = 0; i < 50; i++) {
			assertEquals(this.air.front(i * 731.0, i * -517.0, i * 1000L, 0.3F, 0.6F), other.front(i * 731.0, i * -517.0, i * 1000L, 0.3F, 0.6F));
		}
	}

	@Test
	void frontsCoverPartOfTheLandAndDriftEast() {
		int cloudy = 0, showering = 0, n = 0;
		for (int x = -40; x < 40; x++) {
			for (int z = -40; z < 40; z++) {
				float f = this.air.front(x * 400.0, z * 400.0, 0, 0.3F, 0.6F);
				cloudy += Air.cloudCover(f, 0) > 0.5F ? 1 : 0;
				showering += Air.showers(f) > 0 ? 1 : 0;
				n++;
			}
		}
		assertTrue(cloudy > n / 10 && cloudy < n * 7 / 10, "cloud over some of the land, not all: " + cloudy + "/" + n);
		assertTrue(showering > 0 && showering < n / 4, "showers are local: " + showering + "/" + n);
		// ten minutes on, the front that was at x has moved 3.6 km east (the field also changes shape a little)
		long later = 20 * 600;
		double moved = Air.FRONT_DRIFT * 600;
		double same = 0;
		for (int i = 0; i < 200; i++) {
			same += Math.abs(this.air.front(i * 150.0, 0, 0, 0.3F, 0.6F) - this.air.front(i * 150.0 + moved, moved * 0.2, later, 0.3F, 0.6F));
		}
		assertTrue(same / 200 < 0.05, "the front keeps its shape as it drifts: " + same / 200);
	}

	@Test
	void vanillaRainRainsEverywhereHardestUnderTheFronts() {
		for (float front = 0; front <= 1; front += 0.05F) {
			float rain = Air.rain(1, front, false);
			assertTrue(rain >= Air.DRIZZLE * 0.999F, "at least a drizzle under vanilla rain, front " + front + ": " + rain);
			if (front >= 0.3F) {
				assertEquals(1, rain, 1e-4, "full rain under the front at " + front);
			}
		}
		assertTrue(Air.rain(1, 0.0F, false) < 0.5F, "the clearest gaps only drizzle");
		assertTrue(Air.rain(0.15F, 0.0F, false) < 0.01F, "rain just setting in leaves the clear gaps dry");
		assertEquals(0, Air.rain(0, 0.5F, false), 1e-6, "no vanilla rain, no showers: dry");
		assertTrue(Air.rain(0, 0.85F, true) > 0, "a passing shower under a clear vanilla sky");
	}

	@Test
	void mistGathersAtDawnInValleysAndBurnsOffByNoon() {
		Climate valley = new Climate(0.7F, 0.8F, 1.0F, 0.5F);
		Climate ridge = new Climate(0.7F, 0.8F, 0.0F, 0.0F);
		long sunrise = 0, noon = 6000;
		assertTrue(Air.mist(sunrise, 0.5F, valley, 0) > 0.7F, "an autumn valley at sunrise is thick with mist");
		assertTrue(Air.mist(sunrise, 0.5F, ridge, 0) < Air.mist(sunrise, 0.5F, valley, 0), "ridges stand out of it");
		assertEquals(0, Air.mist(noon, 0.5F, valley, 0), 1e-6, "gone by noon");
		assertEquals(0, Air.mist(sunrise, 0.5F, new Climate(2.0F, 0.0F, 1.0F, 0.0F), 0), 1e-6, "none in the desert");
	}

	@Test
	void theWindStaysSane() {
		for (long t = 0; t < 20 * 3600 * 4; t += 997) {
			Wind w = this.air.wind(t, 0);
			assertEquals(1, Math.hypot(w.x(), w.z()), 1e-4);
			assertTrue(w.speed() >= 0.8F && w.speed() <= 8.0F);
			assertTrue(w.gust() >= 0 && w.gust() <= 1);
		}
	}

	@Test
	void theMistsTimePartPeaksAtSunriseAndIsGoneByNoon() {
		float spring = 0.15F;
		// clock 0 is sunrise (6 am), 6000 noon, 18000 midnight
		assertEquals(0, Air.mistTime(6000, spring), 1e-6, "noon");
		assertTrue(Air.mistTime(0, spring) > 0.9F, "sunrise");
		assertTrue(Air.mistTime(22000, spring) > Air.mistTime(20000, spring), "it gathers toward dawn");
		assertTrue(Air.mistTime(15000, spring) > 0 && Air.mistTime(15000, spring) < 0.5F, "a thin veil after sunset");
		assertTrue(Air.mistTime(0, 0.55F) >= Air.mistTime(0, 0.3F), "autumn makes more than summer");
	}

	@Test
	void theMistsPlacePartRisesWithLownessWaterAndWet() {
		float flat = Air.mistPotential(0, 0, 0.5F, 0, 0);
		assertTrue(Air.mistPotential(1, 0, 0.5F, 0, 0) > flat, "hollows");
		assertTrue(Air.mistPotential(0, 1, 0.5F, 0, 0) > flat, "water");
		assertTrue(Air.mistPotential(0, 0, 0.5F, 1, 0) > flat, "wet ground");
		assertEquals(0, Air.mistPotential(1, 1, 1, 1, 1), 1e-6, "deserts have none");
		// at dawn in a wet valley the two parts together come near the whole mist the camera sees
		Climate valley = new Climate(0.7F, 0.8F, 0.9F, 0.3F);
		float whole = Air.mist(0, 0.15F, valley, 0);
		float split = Air.mistTime(0, 0.15F) * Air.mistPotential(0.9F, 0.3F, 0.8F, 0, 0);
		assertEquals(whole, split, 0.15F);
	}
}
