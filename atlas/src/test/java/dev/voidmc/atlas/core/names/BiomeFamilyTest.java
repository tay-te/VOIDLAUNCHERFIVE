package dev.voidmc.atlas.core.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every vanilla biome lands in a sensible family, and modded ids fall back by the keywords in their paths. */
class BiomeFamilyTest {
	@Test
	void everyVanillaBiomeHasItsFamily() {
		Map<String, BiomeFamily> expected = new LinkedHashMap<>();
		for (String id : new String[]{"ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
			"cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean"}) {
			expected.put(id, BiomeFamily.OCEAN);
		}
		expected.put("river", BiomeFamily.RIVER);
		expected.put("frozen_river", BiomeFamily.RIVER);
		expected.put("beach", BiomeFamily.BEACH);
		expected.put("snowy_beach", BiomeFamily.BEACH);
		expected.put("stony_shore", BiomeFamily.BEACH);
		expected.put("plains", BiomeFamily.PLAINS);
		expected.put("sunflower_plains", BiomeFamily.PLAINS);
		expected.put("meadow", BiomeFamily.MEADOW);
		expected.put("forest", BiomeFamily.FOREST);
		expected.put("flower_forest", BiomeFamily.FOREST);
		expected.put("birch_forest", BiomeFamily.BIRCH_FOREST);
		expected.put("old_growth_birch_forest", BiomeFamily.BIRCH_FOREST);
		expected.put("dark_forest", BiomeFamily.DARK_FOREST);
		expected.put("pale_garden", BiomeFamily.PALE);
		expected.put("taiga", BiomeFamily.TAIGA);
		expected.put("old_growth_pine_taiga", BiomeFamily.TAIGA);
		expected.put("old_growth_spruce_taiga", BiomeFamily.TAIGA);
		expected.put("snowy_taiga", BiomeFamily.SNOWY);
		expected.put("snowy_plains", BiomeFamily.SNOWY);
		expected.put("ice_spikes", BiomeFamily.SNOWY);
		expected.put("grove", BiomeFamily.SNOWY);
		expected.put("snowy_slopes", BiomeFamily.SNOWY);
		expected.put("windswept_hills", BiomeFamily.MOUNTAIN);
		expected.put("windswept_gravelly_hills", BiomeFamily.MOUNTAIN);
		expected.put("windswept_forest", BiomeFamily.MOUNTAIN);
		expected.put("windswept_savanna", BiomeFamily.MOUNTAIN);
		expected.put("jagged_peaks", BiomeFamily.PEAKS);
		expected.put("frozen_peaks", BiomeFamily.PEAKS);
		expected.put("stony_peaks", BiomeFamily.PEAKS);
		expected.put("desert", BiomeFamily.DESERT);
		expected.put("badlands", BiomeFamily.BADLANDS);
		expected.put("eroded_badlands", BiomeFamily.BADLANDS);
		expected.put("wooded_badlands", BiomeFamily.BADLANDS);
		expected.put("savanna", BiomeFamily.SAVANNA);
		expected.put("savanna_plateau", BiomeFamily.SAVANNA);
		expected.put("jungle", BiomeFamily.JUNGLE);
		expected.put("sparse_jungle", BiomeFamily.JUNGLE);
		expected.put("bamboo_jungle", BiomeFamily.JUNGLE);
		expected.put("swamp", BiomeFamily.SWAMP);
		expected.put("mangrove_swamp", BiomeFamily.MANGROVE);
		expected.put("mushroom_fields", BiomeFamily.MUSHROOM);
		expected.put("cherry_grove", BiomeFamily.CHERRY);
		expected.put("dripstone_caves", BiomeFamily.CAVE);
		expected.put("lush_caves", BiomeFamily.CAVE);
		expected.put("deep_dark", BiomeFamily.CAVE);
		expected.put("nether_wastes", BiomeFamily.NETHER);
		expected.put("crimson_forest", BiomeFamily.NETHER);
		expected.put("warped_forest", BiomeFamily.NETHER);
		expected.put("soul_sand_valley", BiomeFamily.NETHER);
		expected.put("basalt_deltas", BiomeFamily.NETHER);
		expected.put("the_end", BiomeFamily.END);
		expected.put("end_highlands", BiomeFamily.END);
		expected.put("end_midlands", BiomeFamily.END);
		expected.put("end_barrens", BiomeFamily.END);
		expected.put("small_end_islands", BiomeFamily.END);

		expected.forEach((path, family) -> {
			assertEquals(family, BiomeFamily.of("minecraft:" + path), path);
			// the keyword rules agree with the hand-made table, so a mod reusing a vanilla name lands in the same place;
			// the one exception is a bare "grove", which only vanilla means as a snowy slope
			if (!path.equals("grove")) {
				assertEquals(family, BiomeFamily.of("othermod:" + path), "othermod:" + path);
			}
		});
	}

	@Test
	void moddedIdsFallBackByKeyword() {
		assertEquals(BiomeFamily.MOUNTAIN, BiomeFamily.of("expanse:karst_spires"));
		assertEquals(BiomeFamily.SNOWY, BiomeFamily.of("expanse:frostbloom_tundra"));
		assertEquals(BiomeFamily.CHERRY, BiomeFamily.of("terralith:sakura_valley"));
		assertEquals(BiomeFamily.SWAMP, BiomeFamily.of("foo:salt_marsh"));
		assertEquals(BiomeFamily.OTHER, BiomeFamily.of("foo:unknown_place"));
		assertEquals(BiomeFamily.DESERT, BiomeFamily.of("foo:red_dunes"));
		assertEquals(BiomeFamily.MEADOW, BiomeFamily.of("foo:tall_grassland"));
		assertEquals(BiomeFamily.TAIGA, BiomeFamily.of("foo:boreal_forest"));
		assertEquals(BiomeFamily.OCEAN, BiomeFamily.of("foo:shallow_sea"));
		assertEquals(BiomeFamily.CAVE, BiomeFamily.of("foo:crystal_caverns"));
		assertEquals(BiomeFamily.FOREST, BiomeFamily.of("foo:grove"));
	}

	@Test
	void idsAreReadLeniently() {
		assertEquals(BiomeFamily.SNOWY, BiomeFamily.of("snowy_taiga"));
		assertEquals(BiomeFamily.SNOWY, BiomeFamily.of("Minecraft:Snowy_Taiga"));
		assertEquals(BiomeFamily.OTHER, BiomeFamily.of(null));
		assertEquals(BiomeFamily.OTHER, BiomeFamily.of(""));
		assertEquals(BiomeFamily.OTHER, BiomeFamily.of("minecraft:the_void"));
	}

	@Test
	void onlyOceansAndRiversAreWater() {
		for (BiomeFamily family : BiomeFamily.values()) {
			if (family == BiomeFamily.OCEAN || family == BiomeFamily.RIVER) {
				assertTrue(family.isWater(), family.name());
			} else {
				assertFalse(family.isWater(), family.name());
			}
		}
	}
}
