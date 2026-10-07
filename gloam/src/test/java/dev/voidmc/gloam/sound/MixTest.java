package dev.voidmc.gloam.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Climate;
import dev.voidmc.gloam.air.Season;
import dev.voidmc.gloam.air.Wind;
import org.junit.jupiter.api.Test;

class MixTest {
	private static final float MIDSPRING = 0.17F, MIDSUMMER = 0.375F, MIDWINTER = 0.875F;
	private static final Climate PLAINS = new Climate(0.8F, 0.4F, 0.3F, 0);
	private static final Climate SAVANNA = new Climate(1.2F, 0.0F, 0.3F, 0);
	/** A meadow with a wood nearby. */
	private static final Surroundings MEADOW = new Surroundings(0, 0.4F, 0.9F, 0, 0, 0, 0, 0, 6, 0.1F, 0, 0, 0, 0, 0, 0, 0, 0);
	/** Deep in a wood, under the leaves. */
	private static final Surroundings WOOD = new Surroundings(1, 1, 0.8F, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	/** A pond's edge. */
	private static final Surroundings POND = new Surroundings(0, 0.2F, 0.7F, 0, 0, 0.9F, 0, 0, 2, 0, 0, 0, 5, 0, 0, 0, 0, 0);
	/** A bare summit, 140 blocks above the sea. */
	private static final Surroundings SUMMIT = new Surroundings(0, 0, 0.2F, 0, 0, 0, 0, 0, 140, 1, 0, 0, 0, 0, 0, 0, 0, 0);
	/** Inside a house. */
	private static final Surroundings HOUSE = new Surroundings(0, 0.2F, 0.8F, 0, 0, 0, 0, 0, 5, 0, 1, 0, 0, 0, 0, 0, 0, 0);

	private static AirSample air(float year, float hour, float wind, float rain, float indoors, Climate climate) {
		return new AirSample(year, Season.of(year), (float) Math.cos(2 * Math.PI * (year - 0.375)), hour, new Wind(1, 0, wind, 0.3F),
			rain, rain, rain, 0, 0, 0, 0, climate, indoors);
	}

	private static float[] mix(AirSample air, Surroundings land) {
		float[] out = new float[Bed.values().length];
		Mix.volumes(air, land, out);
		return out;
	}

	private static float of(float[] mix, Bed bed) {
		return mix[bed.ordinal()];
	}

	@Test
	void theDawnChorusFillsASpringMorningAndIsGoneByNoon() {
		float dawn = of(mix(air(MIDSPRING, 5.8F, 2, 0, 0, PLAINS), MEADOW), Bed.DAWN_CHORUS);
		float noon = of(mix(air(MIDSPRING, 12, 2, 0, 0, PLAINS), MEADOW), Bed.DAWN_CHORUS);
		float winter = of(mix(air(MIDWINTER, 5.8F, 2, 0, 0, PLAINS), MEADOW), Bed.DAWN_CHORUS);
		assertTrue(dawn > 0.2F, "a spring dawn rings with birds: " + dawn);
		assertEquals(0, noon, 1e-4, "the chorus is over by noon");
		assertTrue(winter < dawn * 0.2F, "a winter dawn is nearly silent: " + winter);
	}

	@Test
	void summerNightsHaveCricketsAndWinterNightsNoInsectsAtAll() {
		float[] summer = mix(air(MIDSUMMER, 23, 2, 0, 0, PLAINS), MEADOW);
		float[] winter = mix(air(MIDWINTER, 23, 2, 0, 0, PLAINS), MEADOW);
		assertTrue(of(summer, Bed.CRICKETS) > 0.15F, "crickets on a summer night: " + of(summer, Bed.CRICKETS));
		for (Bed insect : new Bed[]{Bed.CRICKETS, Bed.CICADAS, Bed.FROGS}) {
			assertEquals(0, of(winter, insect), 1e-4, insect + " in winter");
		}
		assertEquals(0, of(mix(air(MIDSUMMER, 12, 2, 0, 0, PLAINS), MEADOW), Bed.CRICKETS), 1e-4, "no crickets at noon");
	}

	@Test
	void frogsCallOnlyNearStillWater() {
		assertTrue(of(mix(air(MIDSPRING + 0.04F, 21, 2, 0, 0, PLAINS), POND), Bed.FROGS) > 0.1F);
		assertEquals(0, of(mix(air(MIDSPRING + 0.04F, 21, 2, 0, 0, PLAINS), MEADOW), Bed.FROGS), 1e-4);
	}

	@Test
	void cicadasSingOnHotDaysInWarmLandsOnly() {
		assertTrue(of(mix(air(MIDSUMMER + 0.05F, 14, 2, 0, 0, SAVANNA), WOOD), Bed.CICADAS) > 0.1F);
		assertEquals(0, of(mix(air(MIDSUMMER + 0.05F, 14, 2, 0, 0, PLAINS), WOOD), Bed.CICADAS), 1e-4, "not in temperate plains");
	}

	@Test
	void theWindPicksItsVoiceFromTheLand() {
		float[] meadow = mix(air(MIDSUMMER, 12, 8, 0, 0, PLAINS), MEADOW);
		float[] wood = mix(air(MIDSUMMER, 12, 8, 0, 0, PLAINS), WOOD);
		float[] summit = mix(air(MIDSUMMER, 12, 8, 0, 0, PLAINS), SUMMIT);
		assertTrue(of(meadow, Bed.WIND_OPEN) > of(meadow, Bed.WIND_LEAVES), "grass wind in the meadow");
		assertTrue(of(wood, Bed.WIND_LEAVES) > of(wood, Bed.WIND_OPEN), "leaves in the wood");
		assertTrue(of(summit, Bed.WIND_HIGH) > of(summit, Bed.WIND_OPEN), "the hollow wind on the summit");
		float calm = of(mix(air(MIDSUMMER, 12, 0.8F, 0, 0, PLAINS), MEADOW), Bed.WIND_OPEN);
		assertTrue(of(meadow, Bed.WIND_OPEN) > calm * 2, "louder in a stiff breeze than in a calm");
	}

	@Test
	void indoorsTheRainIsOnTheRoofAndTheOutdoorsIsMuffled() {
		float[] outside = mix(air(MIDSUMMER, 12, 4, 1, 0, PLAINS), MEADOW);
		float[] inside = mix(air(MIDSUMMER, 12, 4, 1, 1, PLAINS), HOUSE);
		assertTrue(of(outside, Bed.RAIN_GROUND) > 0.3F);
		assertEquals(0, of(outside, Bed.RAIN_ROOF), 1e-4);
		assertTrue(of(inside, Bed.RAIN_ROOF) > of(inside, Bed.RAIN_GROUND) * 3, "on the roof, not the ground");
		assertTrue(of(inside, Bed.WIND_OPEN) < of(outside, Bed.WIND_OPEN) * 0.1F, "the walls stop the wind");
	}

	@Test
	void windowsCrossMidnightAndTheYearWraps() {
		assertEquals(1, Mix.window(0, 20, 21, 3, 4), 1e-4);
		assertEquals(0, Mix.window(12, 20, 21, 3, 4), 1e-4);
		assertEquals(1, Mix.window(12, 8, 9, 17, 18), 1e-4);
		float[] table = {0, 1, 0, 0, 0, 0, 0, 0};
		assertEquals(1, Mix.yearly(table, 1.5F / 8), 1e-4);
		assertEquals(Mix.yearly(table, 0.01F), Mix.yearly(table, 1.01F), 1e-5);
	}
}
