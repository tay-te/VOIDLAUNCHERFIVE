package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TileKeyTest {
	@Test
	void packsAndUnpacksNegativeAndLargeCoordinates() {
		int[] coords = {0, 1, -1, 12345, -12345, (1 << 28) - 1, -(1 << 28)};
		for (int level = 0; level <= TileKey.MAX_LEVEL; level++) {
			for (int tx : coords) {
				for (int tz : coords) {
					long key = TileKey.of(level, tx, tz);
					assertEquals(level, TileKey.level(key));
					assertEquals(tx, TileKey.tx(key));
					assertEquals(tz, TileKey.tz(key));
				}
			}
		}
	}

	@Test
	void childrenTileTheirParentExactly() {
		long parent = TileKey.of(3, -5, 7);
		int span = TileKey.span(3);
		int childSpan = TileKey.span(2);
		assertEquals(span, 2 * childSpan);
		for (int i = 0; i < 4; i++) {
			long child = TileKey.child(parent, i);
			assertEquals(parent, TileKey.parent(child));
			assertEquals(TileKey.minX(parent) + (i & 1) * childSpan, TileKey.minX(child));
			assertEquals(TileKey.minZ(parent) + (i >> 1) * childSpan, TileKey.minZ(child));
		}
	}

	@Test
	void levelZeroCellsAreBlocks() {
		assertEquals(1, TileKey.cell(0));
		assertEquals(TileKey.TILE, TileKey.span(0));
		assertEquals(-TileKey.TILE, TileKey.minX(TileKey.of(0, -1, 0)));
	}
}
