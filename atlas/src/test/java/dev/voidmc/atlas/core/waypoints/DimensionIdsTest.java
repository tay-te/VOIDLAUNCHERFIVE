package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Dimension nicknames from players and other mods map to the game's ids; junk maps to nothing. */
class DimensionIdsTest {
	@Test
	void vanillaNicknames() {
		for (String s : new String[]{"minecraft:overworld", "overworld", "OVERWORLD", " 0 ", "DIM0", "ow"}) {
			assertEquals(DimensionIds.OVERWORLD, DimensionIds.normalise(s), s);
		}
		for (String s : new String[]{"minecraft:the_nether", "the_nether", "Nether", "the nether", "-1", "DIM-1", "dim-1"}) {
			assertEquals(DimensionIds.NETHER, DimensionIds.normalise(s), s);
		}
		for (String s : new String[]{"minecraft:the_end", "the_end", "End", "the end", "1", "DIM1"}) {
			assertEquals(DimensionIds.END, DimensionIds.normalise(s), s);
		}
	}

	@Test
	void moddedIdsPassThroughInLowerCase() {
		assertEquals("twilightforest:twilight_forest", DimensionIds.normalise("TwilightForest:twilight_forest"));
		assertEquals("mymod:space/moon", DimensionIds.normalise("mymod:space/moon"));
	}

	@Test
	void junkIsNull() {
		assertNull(DimensionIds.normalise(null));
		assertNull(DimensionIds.normalise(""));
		assertNull(DimensionIds.normalise("   "));
		assertNull(DimensionIds.normalise("7"));
		assertNull(DimensionIds.normalise("not a dimension"));
		assertNull(DimensionIds.normalise("a:b:c"));
		assertNull(DimensionIds.normalise(":nether"));
		assertEquals(DimensionIds.OVERWORLD, DimensionIds.orOverworld("junk junk"));
	}
}
