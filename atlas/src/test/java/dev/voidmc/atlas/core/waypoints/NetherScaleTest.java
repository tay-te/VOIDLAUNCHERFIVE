package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.waypoints.NetherScale.Point;
import org.junit.jupiter.api.Test;

/** Overworld and nether positions, including the negative side, which must round down rather than towards zero. */
class NetherScaleTest {
	private static final String OW = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String END = "minecraft:the_end";

	@Test
	void overworldToNetherDividesXAndZByEight() {
		assertEquals(new Point(12, 64, -25), NetherScale.convert(100, 64, -200, OW, NETHER));
		assertEquals(new Point(0, 70, 0), NetherScale.convert(7, 70, 7, OW, NETHER));
		assertEquals(new Point(1, 70, 1), NetherScale.convert(8, 70, 8, OW, NETHER));
	}

	@Test
	void negativesRoundDown() {
		assertEquals(-1, NetherScale.toNether(-1));
		assertEquals(-1, NetherScale.toNether(-8));
		assertEquals(-2, NetherScale.toNether(-9));
		assertEquals(-26, NetherScale.toNether(-201));
		assertEquals(new Point(-1, 64, -1), NetherScale.convert(-1, 64, -1, OW, NETHER));
	}

	@Test
	void netherToOverworldMultipliesByEight() {
		assertEquals(new Point(96, 64, -200), NetherScale.convert(12, 64, -25, NETHER, OW));
		assertEquals(new Point(-8, 0, 8), NetherScale.convert(-1, 0, 1, NETHER, OW));
	}

	@Test
	void netherToOverworldStaysInsideTheWorld() {
		assertEquals(30_000_000, NetherScale.toOverworld(29_999_999));
		assertEquals(-30_000_000, NetherScale.toOverworld(Integer.MIN_VALUE));
	}

	@Test
	void heightIsUnchanged() {
		assertEquals(-60, NetherScale.convert(0, -60, 0, OW, NETHER).y());
		assertEquals(250, NetherScale.convert(0, 250, 0, NETHER, OW).y());
	}

	@Test
	void roundTripLandsInTheSameEightBlockCell() {
		for (int v : new int[]{0, 1, 7, 8, 9, 100, -1, -7, -8, -9, -100, 29_999_999, -29_999_999}) {
			int back = NetherScale.toOverworld(NetherScale.toNether(v));
			assertTrue(back <= v && v < back + 8, "value " + v + " came back as " + back);
		}
	}

	@Test
	void sameDimensionIsUnchanged() {
		assertEquals(new Point(1, 2, 3), NetherScale.convert(1, 2, 3, OW, OW));
		assertEquals(new Point(1, 2, 3), NetherScale.convert(1, 2, 3, END, END));
		assertEquals(new Point(1, 2, 3), NetherScale.convert(1, 2, 3, "mymod:moon", "mymod:moon"));
	}

	@Test
	void theEndAndModdedDimensionsDoNotConvert() {
		assertNull(NetherScale.convert(100, 64, 100, OW, END));
		assertNull(NetherScale.convert(100, 64, 100, END, NETHER));
		assertNull(NetherScale.convert(100, 64, 100, NETHER, END));
		assertNull(NetherScale.convert(100, 64, 100, OW, "mymod:moon"));
		assertFalse(NetherScale.convertible(OW, END));
		assertTrue(NetherScale.convertible(OW, NETHER));
		assertTrue(NetherScale.convertible(END, END));
	}

	@Test
	void unknownOrMissingDimensionsGiveNull() {
		assertNull(NetherScale.convert(1, 2, 3, null, NETHER));
		assertNull(NetherScale.convert(1, 2, 3, OW, ""));
		assertNull(NetherScale.convert(1, 2, 3, "not a dimension", OW));
		assertFalse(NetherScale.convertible(null, null));
	}

	@Test
	void nicknamesAreUnderstood() {
		assertEquals(new Point(12, 64, -25), NetherScale.convert(100, 64, -200, "overworld", "nether"));
		assertEquals(new Point(12, 64, -25), NetherScale.convert(100, 64, -200, "0", "-1"));
		assertEquals(new Point(96, 64, -200), NetherScale.convert(12, 64, -25, "the_nether", "Overworld"));
		assertEquals(new Point(12, 64, -25), NetherScale.convert(100, 64, -200, "DIM0", "DIM-1"));
	}
}
