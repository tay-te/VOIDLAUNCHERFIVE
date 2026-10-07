package dev.voidmc.atlas.core.under;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Cave biomes seen underground join into named areas per band; small corners of one are not named. */
class CaveAreasTest {
	@Test
	void cellsOfOneBiomeInABandJoinIntoOneArea() {
		CaveAreas caves = new CaveAreas();
		for (int x = 0; x < 64; x += 4) {
			caves.put(-3, x, 10, "minecraft:dripstone_caves");
		}
		List<CaveAreas.Area> areas = caves.areas();
		assertEquals(1, areas.size());
		CaveAreas.Area a = areas.get(0);
		assertEquals("minecraft:dripstone_caves", a.biome());
		assertEquals(4, a.cells());
		assertEquals(-3, a.band());
		assertTrue(a.x() >= 0 && a.x() < 64 && a.z() >= 0 && a.z() < 16, "its label is inside it");
	}

	@Test
	void otherBandsAndOtherBiomesAreOtherAreas() {
		CaveAreas caves = new CaveAreas();
		for (int x = 0; x < 48; x += 16) {
			caves.put(-3, x, 0, "minecraft:lush_caves");
			caves.put(-4, x, 0, "minecraft:lush_caves");
			caves.put(-3, x, 16, "minecraft:dripstone_caves");
		}
		assertEquals(3, caves.areas().size());
	}

	@Test
	void aCornerOfACaveIsNotNamed() {
		CaveAreas caves = new CaveAreas();
		caves.put(-6, 0, 0, "minecraft:deep_dark");
		caves.put(-6, 16, 0, "minecraft:deep_dark");
		assertTrue(caves.areas().isEmpty(), "two cells are too few");
		caves.put(-6, 32, 0, "minecraft:deep_dark");
		assertEquals(1, caves.areas().size());
	}

	@Test
	void negativeCoordinatesAndBandsSurviveSavingAndLoading() {
		CaveAreas caves = new CaveAreas();
		for (int x = -100; x < -40; x += 16) {
			caves.put(-8, x, -3000, "minecraft:deep_dark");
		}
		assertFalse(caves.put(-8, -100, -3000, "minecraft:deep_dark"), "seen again: nothing new");
		CaveAreas back = new CaveAreas();
		back.load(caves.save() + "garbage line\n");
		assertEquals(caves.areas(), back.areas());
		CaveAreas.Area a = back.areas().get(0);
		assertTrue(a.x() < -40 && a.z() < -2900 && a.band() == -8);
	}

	@Test
	void loadsFilesWithWindowsOrOldMacLineEndings() {
		CaveAreas caves = new CaveAreas();
		for (int x = 0; x < 64; x += 16) {
			caves.put(-2, x, 0, "minecraft:lush_caves");
		}
		for (String ending : new String[]{"\r\n", "\r"}) {
			CaveAreas back = new CaveAreas();
			back.load(caves.save().replace("\n", ending));
			assertEquals(caves.areas(), back.areas(), "lines ending in " + ending.replace("\r", "CR").replace("\n", "LF"));
			assertEquals(caves.save(), back.save());
		}
	}
}
