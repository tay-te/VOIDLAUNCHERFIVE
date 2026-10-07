package dev.voidmc.gloam.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyTest {
	@Test
	void nightLightFollowsMoonAndCloud() {
		assertEquals(1.0F, Night.light(1, 0), 1e-4F, "a clear full moon is vanilla's night");
		assertEquals(Night.FLOOR, Night.light(0, 1), 1e-4F, "a cloudy new moon is next to nothing");
		assertTrue(Night.light(0, 0) > Night.light(0, 1), "starlight on a clear moonless night");
		assertTrue(Night.light(1, 1) < 0.2F, "thick cloud hides even a full moon");
		assertTrue(Night.light(0.5F, 0.3F) < Night.light(0.5F, 0.1F), "more cloud, less light");
	}

	@Test
	void darkNightsScaleOnlyAtNight() {
		assertEquals(1.0F, Night.scale(0.03F, 0, 1), 1e-6F, "the day is left alone");
		assertEquals(1.0F, Night.scale(0.03F, 1, 0), 1e-6F, "darkNights 0 is vanilla");
		assertEquals(0.03F, Night.scale(0.03F, 1, 1), 1e-6F);
	}

	@Test
	void starsHideBehindCloud() {
		assertEquals(0.0F, Night.stars(1, 1), 1e-6F);
		assertTrue(Night.stars(0, 0) > Night.stars(1, 0), "a moonless sky shows more stars");
	}

	@Test
	void twilightMirrorsDawnOnDusk() {
		assertEquals(11929, Twilight.duskTime(71), 1e-3F);
		assertEquals(12243, Twilight.duskTime(23757), 1e-3F);
		assertEquals(12000, Twilight.duskTime(12000 + 24000 * 5), 1e-3F);
		assertEquals(0.0F, Twilight.night(6000), 1e-6F);
		assertEquals(1.0F, Twilight.night(18000), 1e-6F);
		assertEquals(0.0F, Twilight.golden(6000), 1e-6F);
		assertTrue(Twilight.golden(12000) > 0.9F, "the golden hour before sunset");
		assertTrue(Twilight.blue(13300) > 0.9F, "the blue hour after it");
		assertEquals(0.0F, Twilight.blue(18000), 1e-6F);
	}

	@Test
	void sunsetStepsThroughThePalette() {
		assertEquals(Palette.GOLD, Twilight.colour(11400), "gold while the sun is up");
		assertEquals(Palette.ORANGE, Twilight.colour(12300));
		assertEquals(Palette.ROSE, Twilight.colour(12900), "rose just after it sets");
		assertEquals(Palette.VIOLET, Twilight.colour(13700));
		assertEquals(Palette.DUSK_BLUE, Twilight.colour(14100), "dusk blue as the afterglow goes");
		assertEquals(Palette.ROSE, Twilight.colour(36000 - 12900), "the morning backwards");
		assertEquals(Palette.ROSE, Twilight.colour(12700), "a step holds until the next");
	}

	@Test
	void stepsAreFlat() {
		assertEquals(0.0F, Palette.step(0.1F, 3), 1e-6F);
		assertEquals(1 / 3.0F, Palette.step(0.3F, 3), 1e-6F);
		assertEquals(1.0F, Palette.step(0.9F, 3), 1e-6F);
		org.joml.Vector3f t = Palette.tone(Palette.OVERCAST_SKY, new org.joml.Vector3f(0.2F), 1);
		assertEquals(0.2F, Palette.luma(t), 1e-3F, "a tint takes the brightness of what it colours");
	}

	@Test
	void hazeKeepsAHorizon() {
		assertTrue(Haze.haziness(72) > 0.9F, "a cloud forest is about as murky as it gets");
		assertEquals(0.0F, Haze.haziness(1024), 1e-6F, "clear air");
		assertTrue(Haze.haziness(150) > Haze.haziness(300), "the shorter the old fog, the hazier");
		assertTrue(Haze.reach(1, 80) >= 80, "never nearer than the floor");
		assertEquals(1024, Haze.reach(0, 80), 1, "clear air reaches vanilla's fog end");
		assertTrue(Haze.daily(0.5F, 13) < 0.6F, "a damp land's haze thins at midday");
		assertTrue(Haze.daily(0.95F, 13) > 0.85F, "a cloud forest stays murky");
		assertEquals(1.0F, Haze.daily(0.5F, 6), 1e-6F, "all of it at dawn");
		assertTrue(Haze.curtain(1) < 0.8F, "the curtain fades the far land, never walls it");
		for (Haze.Entry e : Haze.BIOMES.values()) {
			assertTrue(e.reach() > 0);
		}
	}

	@Test
	void aSummitAboveTheCloudsHasASkyAndNoRain() {
		int[] tall = {208, 393, 619};
		assertEquals(1.0F, GloamSky.overhead(tall, 192, 100), 1e-4F, "in the valley all the cloud is overhead");
		assertEquals(0.2F, GloamSky.overhead(tall, 192, 590), 0.02F, "on a summit only the high veil");
		assertEquals(0.0F, GloamSky.rainBelow(tall, 192, 590), 1e-4F, "no rain above the rain cloud");
		assertEquals(1.0F, GloamSky.rainBelow(tall, 192, 300), 1e-4F, "rain under the main deck");
		assertEquals(1.0F, GloamSky.overhead(null, 192, 100), 1e-4F, "vanilla's one deck");
		assertEquals(0.0F, GloamSky.overhead(null, 192, 260), 1e-4F);
	}
}
