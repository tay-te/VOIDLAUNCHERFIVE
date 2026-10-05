package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Block, region, tile, pixel and mip-level maths, including the negative side (shifts must floor, not truncate). */
class CoordsTest {
	@Test
	void regionsAreFiveHundredTwelveBlocksAndFloorBelowZero() {
		assertEquals(0, Coords.region(0));
		assertEquals(0, Coords.region(511));
		assertEquals(1, Coords.region(512));
		assertEquals(-1, Coords.region(-1));
		assertEquals(-1, Coords.region(-512));
		assertEquals(-2, Coords.region(-513));
		assertEquals(511, Coords.inRegion(-1));
		assertEquals(0, Coords.inRegion(-512));
		assertEquals(5, Coords.inRegion(517));
	}

	@Test
	void everyBlockRoundTripsThroughTileAndPixelAtEveryLevel() {
		for (int level = 0; level <= Coords.MAX_LEVEL; level++) {
			for (int block : new int[]{0, 1, 511, 512, 513, -1, -2, -511, -512, -513, 123_456, -9_876_543, 29_999_999, -29_999_999}) {
				int tile = Coords.tile(block, level);
				int px = Coords.pixel(block, level);
				assertTrue(px >= 0 && px < Coords.TILE_SIZE, "pixel in range");
				int first = Coords.blockOf(tile, px, level);
				// the block lies in its pixel's span of 2^level blocks
				assertTrue(block >= first && block < first + (1 << level), "level " + level + " block " + block);
				assertEquals(Coords.tileOrigin(tile, level), Coords.blockOf(tile, 0, level));
			}
		}
	}

	@Test
	void tilesCoverTwoToTheLevelRegionsAndRegionsSitWhereExpected() {
		assertEquals(512, Coords.tileSpan(0));
		assertEquals(16384, Coords.tileSpan(5));
		assertEquals(512, Coords.regionImageSize(0));
		assertEquals(16, Coords.regionImageSize(5));
		assertEquals(4, Coords.regionsPerTile(2));
		// region 5 at level 2: tile 1, third region along (5 & 3 = 1, x 128 px)
		assertEquals(128, Coords.regionOffsetInTile(5, 2));
		assertEquals(Coords.tile(5 * 512, 2), 5 >> 2);
		// negative regions: region -1 is the last of tile -1
		assertEquals(3 * 128, Coords.regionOffsetInTile(-1, 2));
		assertEquals(-1, Coords.tile(-1, 2));
	}

	@Test
	void theLevelNeverMinifiesTheMap() {
		assertEquals(0, Coords.levelFor(1));
		assertEquals(0, Coords.levelFor(4));
		assertEquals(1, Coords.levelFor(0.75));
		assertEquals(1, Coords.levelFor(0.5));
		assertEquals(2, Coords.levelFor(0.3));
		assertEquals(5, Coords.levelFor(1.0 / 32));
		assertEquals(5, Coords.levelFor(1.0 / 1000));
		for (double zoom = 1.0 / 32; zoom < 2; zoom *= 1.07) {
			int level = Coords.levelFor(zoom);
			// a texel at this level is at least one screen pixel
			assertTrue((1 << level) * zoom >= 0.999 || level == Coords.MAX_LEVEL, "zoom " + zoom);
		}
	}

	@Test
	void packUnpack() {
		for (int x : new int[]{0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 58593}) {
			for (int z : new int[]{0, -7, 7, Integer.MIN_VALUE}) {
				long k = Coords.pack(x, z);
				assertEquals(x, Coords.unpackX(k));
				assertEquals(z, Coords.unpackZ(k));
			}
		}
	}
}
