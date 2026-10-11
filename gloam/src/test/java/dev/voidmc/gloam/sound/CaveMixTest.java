package dev.voidmc.gloam.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Climate;
import dev.voidmc.gloam.air.Season;
import dev.voidmc.gloam.air.Wind;
import org.junit.jupiter.api.Test;

class CaveMixTest {
	private static final float MIDSUMMER = 0.375F;
	private static final Climate PLAINS = new Climate(0.8F, 0.4F, 0.3F, 0);

	/** A dripping passage three blocks wide, 30 blocks down. */
	private static final Hollow TUNNEL = cave(1, 2.5F, 5, 0.05F, 0, 0, 0, 0, 0, 0, 0, 0.8F, 0, CaveKinds.REALM_NONE);
	/** A great cavern: wide, a 70-block vault, a few drips. */
	private static final Hollow CAVERN = cave(1, 30, 70, 1, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, CaveKinds.WILDS);
	/** The bank of a great river, glowworms on the vault, bats in the dark. */
	private static final Hollow GREAT_RIVER = cave(1, 20, 50, 0.85F, 1, 1, 0, 0, 0, 1, 1, 0.9F, 0, CaveKinds.REALM_NONE);
	/** A small cavern brook in a passage. */
	private static final Hollow BROOK = cave(1, 3, 6, 0.1F, 0.9F, 0, 0, 0, 0, 0, 0, 0.9F, 0, CaveKinds.REALM_NONE);
	/** The shore of a buried sea. */
	private static final Hollow SEA_SHORE = cave(1, 32, 90, 1, 0, 0, 1, 0, 0, 0, 0.6F, 0.6F, 0, CaveKinds.SEA_REALM);
	/** A gallery of a great mine. */
	private static final Hollow MINE = cave(1, 2, 4, 0.05F, 0, 0, 0, 0, 0, 0, 0, 0.2F, 1, CaveKinds.REALM_NONE);
	/** The frozen realm: a cavern of ice. */
	private static final Hollow FROZEN = cave(1, 20, 40, 0.9F, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, CaveKinds.FROZEN);

	private static Hollow cave(float under, float space, float vault, float open, float river, float great, float sea, float lake, float lava,
		float glow, float bats, float drip, float mine, int realm) {
		int water = river > 0 ? CaveKinds.RIVER : sea > 0 ? CaveKinds.SEA : lake > 0 ? CaveKinds.LAKE : CaveKinds.WATER_NONE;
		return new Hollow(under, 30, space, vault, open, river, great, sea, lake, lava, glow, bats, drip, mine, 6, 0, -6, 0, 0, 5, realm, water);
	}

	private static AirSample air(float hour, float wind, float rain, float wetness) {
		return new AirSample(MIDSUMMER, Season.of(MIDSUMMER), 1, hour, new Wind(1, 0, wind, 0.3F), rain, rain, rain, 0, 0, 0, wetness, PLAINS, 1);
	}

	private static final AirSample NOON = air(12, 3, 0, 0);

	private static float[] mix(AirSample air, Hollow cave) {
		float[] out = new float[Bed.values().length];
		CaveMix.volumes(air, cave, out);
		return out;
	}

	private static float of(float[] mix, Bed bed) {
		return mix[bed.ordinal()];
	}

	@Test
	void underTheSkyEveryCaveBedIsSilent() {
		Hollow open = new Hollow(0, 0, 30, 70, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, CaveKinds.LUSH, CaveKinds.RIVER);
		float[] out = mix(air(19.5F, 10, 1, 1), open);
		for (Bed bed : Bed.values()) {
			if (bed.underground()) {
				assertEquals(0, of(out, bed), 1e-6, bed + " under the sky");
			}
		}
	}

	@Test
	void caveMixLeavesTheLandsBedsAlone() {
		float[] out = new float[Bed.values().length];
		java.util.Arrays.fill(out, 0.123F);
		CaveMix.volumes(NOON, GREAT_RIVER, out);
		for (Bed bed : Bed.values()) {
			if (!bed.underground()) {
				assertEquals(0.123F, out[bed.ordinal()], 0, bed + " is Mix's");
			}
		}
	}

	@Test
	void dripsAreCloseInAPassageAndEchoInACavern() {
		float[] tunnel = mix(NOON, TUNNEL);
		float[] cavern = mix(NOON, CAVERN);
		assertTrue(of(tunnel, Bed.DRIP_NEAR) > 0.2F, "drips in a wet passage: " + of(tunnel, Bed.DRIP_NEAR));
		assertTrue(of(tunnel, Bed.DRIP_NEAR) > of(tunnel, Bed.DRIP_HALL) * 5, "close, not echoing, in the passage");
		assertTrue(of(cavern, Bed.DRIP_HALL) > of(cavern, Bed.DRIP_NEAR) * 5, "echoing in the cavern");
	}

	@Test
	void rainAboveSoaksDownIntoMoreDrips() {
		float dry = of(mix(air(12, 3, 0, 0), TUNNEL), Bed.DRIP_NEAR);
		float soaked = of(mix(air(12, 3, 0.8F, 1), TUNNEL), Bed.DRIP_NEAR);
		assertTrue(soaked > dry * 1.4F, "after rain " + soaked + " vs dry " + dry);
	}

	@Test
	void theFrozenRealmHardlyDripsAndItsDripsRingHigher() {
		Hollow wilds = cave(1, 20, 40, 0.9F, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, CaveKinds.WILDS);
		assertTrue(of(mix(NOON, FROZEN), Bed.DRIP_HALL) < of(mix(NOON, wilds), Bed.DRIP_HALL) * 0.3F);
		assertTrue(CaveMix.pitch(Bed.DRIP_HALL, FROZEN, NOON) > 1);
	}

	@Test
	void aGreatRiverRoarsLowAndABrookRushesHigher() {
		float great = of(mix(NOON, GREAT_RIVER), Bed.UNDER_RIVER);
		float brook = of(mix(NOON, BROOK), Bed.UNDER_RIVER);
		assertTrue(great > 0.6F, "the great river roars: " + great);
		assertTrue(brook > 0.1F && brook < great * 0.6F, "the brook is quieter: " + brook);
		assertTrue(CaveMix.pitch(Bed.UNDER_RIVER, GREAT_RIVER, NOON) < 0.85F);
		assertTrue(CaveMix.pitch(Bed.UNDER_RIVER, BROOK, NOON) > 1.05F);
		assertEquals(0, of(mix(NOON, TUNNEL), Bed.UNDER_RIVER), 1e-6, "no river, no rush");
	}

	@Test
	void theRiverAndTheSeaAreHeardFromWhereTheyLie() {
		float[] river = CaveMix.offset(Bed.UNDER_RIVER, GREAT_RIVER);
		float[] sea = CaveMix.offset(Bed.BURIED_SEA, SEA_SHORE);
		assertTrue(river[0] > 0, "the river lies east");
		assertTrue(sea[0] < 0, "the sea lies west");
		assertEquals(0, CaveMix.offset(Bed.DRIP_NEAR, TUNNEL)[0], 0, "drips are all around");
	}

	@Test
	void theBuriedSeaLapsAndALakeLapsSofterAndHigher() {
		Hollow lake = cave(1, 20, 40, 0.8F, 0, 0, 0, 1, 0, 0, 0, 0.6F, 0, CaveKinds.LUSH);
		float sea = of(mix(NOON, SEA_SHORE), Bed.BURIED_SEA);
		float lakeVol = of(mix(NOON, lake), Bed.BURIED_SEA);
		assertTrue(sea > 0.5F, "the sea: " + sea);
		assertTrue(lakeVol > 0.1F && lakeVol < sea * 0.6F, "the lake: " + lakeVol);
		assertTrue(CaveMix.pitch(Bed.BURIED_SEA, lake, NOON) > CaveMix.pitch(Bed.BURIED_SEA, SEA_SHORE, NOON));
	}

	@Test
	void batsChatterByDayAndWakeAtDusk() {
		float day = of(mix(air(12, 3, 0, 0), GREAT_RIVER), Bed.BATS);
		float dusk = of(mix(air(19.7F, 3, 0, 0), GREAT_RIVER), Bed.BATS);
		assertTrue(day > 0.1F, "a murmur in the roost by day: " + day);
		assertTrue(dusk > day * 1.8F, "waking at dusk: " + dusk + " vs " + day);
		assertEquals(0, of(mix(air(19.7F, 3, 0, 0), TUNNEL), Bed.BATS), 1e-6, "no roost, no bats");
	}

	@Test
	void minesCreakAndOtherCavesDoNot() {
		assertTrue(of(mix(NOON, MINE), Bed.MINE_CREAK) > 0.3F);
		assertEquals(0, of(mix(NOON, TUNNEL), Bed.MINE_CREAK), 1e-6);
	}

	@Test
	void theGreatCavernsMoanAndThePassagesDoNot() {
		float cavern = of(mix(NOON, CAVERN), Bed.CAVERN_WIND);
		assertTrue(cavern > 0.2F, "the cavern moans: " + cavern);
		assertEquals(0, of(mix(NOON, TUNNEL), Bed.CAVERN_WIND), 1e-3, "not in a passage");
		float gale = of(mix(air(12, 14, 0, 0), CAVERN), Bed.CAVERN_WIND);
		assertTrue(gale > cavern * 1.2F, "the caves breathe harder in a gale above");
		Hollow grotto = cave(1, 30, 70, 1, 0, 0, 0, 0, 0, 1, 0, 0.3F, 0, CaveKinds.TIDAL);
		assertTrue(of(mix(NOON, grotto), Bed.CAVERN_WIND) < cavern * 0.5F, "the glowworms' air is still");
		assertTrue(CaveMix.pitch(Bed.CAVERN_WIND, CAVERN, NOON) < CaveMix.pitch(Bed.CAVERN_WIND, TUNNEL, NOON), "a tall vault moans deeper");
	}

	@Test
	void everyVolumeStaysWithinItsGain() {
		Hollow everything = new Hollow(1, 60, 32, 120, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 8, 0, 0, 8, 8, 8, CaveKinds.DRIPSTONE, CaveKinds.RIVER);
		float[] out = mix(air(19.7F, 20, 1, 1), everything);
		for (Bed bed : Bed.values()) {
			if (bed.underground()) {
				assertTrue(out[bed.ordinal()] >= 0 && out[bed.ordinal()] <= bed.gain() + 1e-6, bed + " " + out[bed.ordinal()]);
			}
		}
	}

	@Test
	void aCaveMouthFadesTheCavesIn() {
		Hollow half = new Hollow(0.5F, 30, 2.5F, 5, 0.05F, 0, 0, 0, 0, 0, 0, 0, 0.8F, 0, 0, 0, 0, 0, 0, 0, CaveKinds.REALM_NONE,
			CaveKinds.WATER_NONE);
		assertEquals(of(mix(NOON, TUNNEL), Bed.DRIP_NEAR) * 0.5F, of(mix(NOON, half), Bed.DRIP_NEAR), 1e-4);
	}
}
