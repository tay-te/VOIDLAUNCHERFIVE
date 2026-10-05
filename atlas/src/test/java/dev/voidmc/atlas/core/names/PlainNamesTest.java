package dev.voidmc.atlas.core.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.voidmc.atlas.api.Place;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** On vanilla, places carry the plain names of the land: the game's biome names, and what a landmark is. */
class PlainNamesTest {
	private static final PlainNames.Translator GAME = Map.of(
		"minecraft:forest", "Forest",
		"minecraft:deep_ocean", "Deep Ocean",
		"minecraft:dripstone_caves", "Dripstone Caves",
		"minecraft:river", "River")::get;

	@Test
	void regionsAreTheirBiomeInTheGamesWords() {
		assertEquals("Forest", PlainNames.name(Place.REGION, "minecraft:forest", GAME));
		assertEquals("Dripstone Caves", PlainNames.name(Place.REGION, "minecraft:dripstone_caves", GAME));
		PlainNames.Translator french = Map.of("minecraft:forest", "Forêt")::get;
		assertEquals("Forêt", PlainNames.name(Place.REGION, "minecraft:forest", french), "the player's language wins over the key");
	}

	@Test
	void withoutATranslationTheKeyIsTitleCased() {
		assertEquals("Deep Dark", PlainNames.name(Place.REGION, "minecraft:deep_dark", PlainNames.UNTRANSLATED));
		assertEquals("Old Growth Spruce Taiga", PlainNames.name(Place.REGION, "minecraft:old_growth_spruce_taiga", id -> null));
		assertEquals("Karst Highlands", PlainNames.name(Place.REGION, "expanse:karst_highlands", id -> " "), "a blank translation is no translation");
	}

	@Test
	void waterIsItsOceanOrRiverOrALake() {
		assertEquals("Deep Ocean", PlainNames.name(Place.LAKE, "minecraft:deep_ocean", GAME));
		assertEquals("Lake", PlainNames.name(Place.LAKE, "minecraft:plains", GAME), "a lake in the plains is not called Plains");
		assertEquals("River", PlainNames.name(Place.RIVER, "minecraft:river", GAME));
		assertEquals("Frozen River", PlainNames.name(Place.RIVER, "minecraft:frozen_river", GAME));
		assertEquals("River", PlainNames.name(Place.RIVER, "minecraft:forest", GAME));
	}

	@Test
	void landmarksAreWhatTheyAre() {
		assertEquals("Peak", PlainNames.name(Place.PEAK, "minecraft:jagged_peaks", GAME));
		assertEquals("Waterfall", PlainNames.name(Place.WATERFALL, "minecraft:forest", GAME));
		assertEquals("Ravine", PlainNames.name(Place.RAVINE, "minecraft:forest", GAME));
		assertEquals("Cave mouth", PlainNames.name(Place.CAVE_MOUTH, "minecraft:forest", GAME));
	}

	@Test
	void worldSeedIsStableAndMixesBothInputs() {
		assertEquals(WorldSeed.hash("survival.example.net", 8_675_309L), WorldSeed.hash("survival.example.net", 8_675_309L));
		assertNotEquals(WorldSeed.hash("a", 1L), WorldSeed.hash("b", 1L));
		assertNotEquals(WorldSeed.hash("a", 1L), WorldSeed.hash("a", 2L));
		assertNotEquals(WorldSeed.hash("", 0L), WorldSeed.hash("ab", 0L));
		assertEquals(WorldSeed.hash(null, 7L), WorldSeed.hash("", 7L));
	}
}
