package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The section 9 map art's tables and formulas. */
class MapArtTest {
	@Test
	void theTunedBlocksAreTheTable() {
		assertEquals(0x455331, MapArt.tuned("minecraft:grass_block"));
		assertEquals(0x907554, MapArt.tuned("minecraft:oak_planks"));
		assertEquals(0x626365, MapArt.tuned("minecraft:cobblestone"));
		assertEquals(-1, MapArt.tuned("minecraft:granite"));
		assertEquals(-1, MapArt.tuned("expanse:grass_block"), "a mod's block is not the vanilla one");
	}

	@Test
	void plainsGrassIsTheTableColour() {
		float[] lab = MapArt.tinted(0x455331, MapArt.REF_GRASS, false);
		float[] table = Oklab.fromRgb(0x455331);
		assertEquals(table[0], lab[0], 1e-4);
		assertEquals(table[1], lab[1], 1e-4);
		float[] swamp = MapArt.tinted(0x455331, 0x6A7039, false);
		assertTrue(swamp[0] < lab[0], "a darker tint darkens the grass a little");
		assertTrue(Math.hypot(swamp[1], swamp[2]) <= MapArt.CHROMA_CAP + 1e-6);
	}

	@Test
	void biomeFillsFamiliesAndModdedColours() {
		assertEquals(0x9D7B87, MapArt.vanillaFill("minecraft:cherry_grove"));
		assertEquals(MapArt.Family.FORESTS, MapArt.family("minecraft:dark_forest"));
		assertEquals(MapArt.Family.CAVES, MapArt.family("minecraft:deep_dark"));
		int modded = MapArt.moddedFill(0x79B35A, 0x5DA03A);
		float[] lab = Oklab.fromRgb(modded);
		assertTrue(lab[0] >= 0.39F && lab[0] <= 0.61F, "lightness within the vanilla fills' range");
		assertTrue(Math.hypot(lab[1], lab[2]) <= 0.0505, "quiet");
		assertTrue(lab[2] > 0, "keeps its yellow-green hue");
	}

	@Test
	void waterStepsAndWords() {
		assertEquals(0, MapArt.seaStep(1));
		assertEquals(1, MapArt.seaStep(3));
		assertEquals(4, MapArt.seaStep(19));
		assertEquals(6, MapArt.seaStep(40));
		assertEquals("mild", MapArt.temperatureWord(0.6F));
		assertEquals("frozen", MapArt.temperatureWord(0.0F));
		assertEquals("wet", MapArt.rainfallWord(0.8F));
		assertEquals("Cherry Grove", MapArt.biomeLabel("minecraft:cherry_grove"));
	}

	@Test
	void nightAndGlow() {
		float[] day = {0.6F, 0.02F, 0.03F};
		float[] night = MapArt.moonlit(day);
		assertEquals(0.6F * 0.56F + 0.015F, night[0], 1e-6);
		assertEquals(0, MapArt.glow(14, 10), 1e-6, "a torch's glow ends before 10 blocks");
		assertTrue(MapArt.glow(15, 0) > 0.9F);
		float[] lit = MapArt.lit(night, day, 1, false);
		assertTrue(lit[0] > night[0], "light brings the day back");
	}
}
