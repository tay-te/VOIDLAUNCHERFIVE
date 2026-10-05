package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Waypoint;
import org.junit.jupiter.api.Test;

/** Bright colours from other mods land on the muted Atlas colour of the same family. */
class AtlasColoursTest {
	private static final int INK = 0, SAGE = 1, SKY = 2, LILAC = 3, ROSE = 4, CLAY = 5, OCHRE = 6, STONE = 7;

	@Test
	void everyAtlasColourMapsToItself() {
		for (int i = 0; i < Waypoint.COLOURS.length; i++) {
			assertEquals(i, AtlasColours.nearest(Waypoint.COLOURS[i]), Waypoint.COLOUR_NAMES[i]);
		}
	}

	@Test
	void greysGoToInkOrStoneByLightness() {
		assertEquals(INK, AtlasColours.nearest(0xFFFFFF));
		assertEquals(INK, AtlasColours.nearest(0xF0F0F0));
		assertEquals(STONE, AtlasColours.nearest(0x000000));
		assertEquals(STONE, AtlasColours.nearest(0x555555));
		assertEquals(STONE, AtlasColours.nearest(0xAAAAAA));
	}

	@Test
	void huesGoToTheirFamily() {
		assertEquals(CLAY, AtlasColours.nearest(0xFF0000));
		assertEquals(CLAY, AtlasColours.nearest(0xFF5555));
		assertEquals(OCHRE, AtlasColours.nearest(0xFFAA00));
		assertEquals(SAGE, AtlasColours.nearest(0x00FF00));
		assertEquals(SKY, AtlasColours.nearest(0x0000FF));
		assertEquals(SKY, AtlasColours.nearest(0x5555FF));
		int purple = AtlasColours.nearest(0xAA00AA);
		assertTrue(purple == LILAC || purple == ROSE, "purple went to " + purple);
	}

	@Test
	void alphaIsIgnored() {
		assertEquals(AtlasColours.nearest(0xFF0000), AtlasColours.nearest(0x80FF0000));
	}

	@Test
	void chatColours() {
		assertEquals(STONE, AtlasColours.fromChatColour(0));
		assertEquals(SKY, AtlasColours.fromChatColour(1));
		assertEquals(SAGE, AtlasColours.fromChatColour(2));
		assertEquals(CLAY, AtlasColours.fromChatColour(4));
		assertEquals(OCHRE, AtlasColours.fromChatColour(6));
		assertEquals(STONE, AtlasColours.fromChatColour(7));
		assertEquals(STONE, AtlasColours.fromChatColour(8));
		assertEquals(SKY, AtlasColours.fromChatColour(9));
		assertEquals(SAGE, AtlasColours.fromChatColour(10));
		assertEquals(CLAY, AtlasColours.fromChatColour(12));
		assertEquals(OCHRE, AtlasColours.fromChatColour(14));
		assertEquals(INK, AtlasColours.fromChatColour(15));
		// out of range wraps rather than failing
		assertEquals(AtlasColours.fromChatColour(0), AtlasColours.fromChatColour(16));
		assertEquals(AtlasColours.fromChatColour(15), AtlasColours.fromChatColour(-1));
		for (int i = 0; i < 16; i++) {
			int c = AtlasColours.fromChatColour(i);
			assertTrue(c >= 0 && c < Waypoint.COLOURS.length);
		}
	}
}
