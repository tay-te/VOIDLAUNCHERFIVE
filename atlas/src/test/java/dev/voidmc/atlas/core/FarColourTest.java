package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Far colours go through the block palette as names and come back near what they were. */
class FarColourTest {
	@Test
	void roundTripsWithinAQuantisationStep() {
		for (int rgb : new int[]{0x000000, 0xFFFFFF, 0x5A7A3C, 0x3F76E4, 0x808080}) {
			int back = FarColour.colour(FarColour.name(rgb));
			for (int shift = 0; shift <= 16; shift += 8) {
				int a = rgb >> shift & 0xFF;
				int b = back >> shift & 0xFF;
				assertTrue(Math.abs(a - b) <= 17, Integer.toHexString(rgb) + " came back as " + Integer.toHexString(back));
			}
		}
	}

	@Test
	void namesAreFewAndNeverBlocks() {
		assertEquals("atlas:far#5a7", FarColour.name(0xFF5AAA7C));
		assertTrue(FarColour.is(FarColour.name(0x123456)));
		assertFalse(FarColour.is("minecraft:grass_block"));
		assertEquals(-1, FarColour.colour("minecraft:stone"));
		assertEquals(-1, FarColour.colour("atlas:far#zz9"));
		assertEquals(FarColour.name(0x5A7A3C), FarColour.name(0x5F7F3F), "one 4-bit step is one name");
	}
}
