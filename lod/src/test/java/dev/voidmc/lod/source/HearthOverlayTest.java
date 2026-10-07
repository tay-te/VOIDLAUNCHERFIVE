package dev.voidmc.lod.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.TerrainSource;
import org.junit.jupiter.api.Test;

class HearthOverlayTest {
	private static final int WALL = 0xE8E0D0, ROOF = 0x9A3B2A;
	/** Flat grass at 64. */
	private static final TerrainSource LAND = (x, z, cell, out) -> {
		out.ground = 64;
		out.top = 0x60A040;
		out.side = 0x806040;
		out.cover = 0.5F;
	};
	/** One house, 9 x 9 from (0, 0), eaves at 68, ridge at 72. */
	private static final HearthOverlay.Footprints HOUSE = new HearthOverlay.Footprints() {
		@Override
		public long version(int regionX, int regionZ) {
			return 1;
		}

		@Override
		public boolean sample(int x, int z, int[] out) {
			if (x < 0 || z < 0 || x > 8 || z > 8) {
				return false;
			}
			out[0] = WALL | 0xFF000000;
			out[1] = ROOF | 0xFF000000;
			out[2] = 68 << 16 | 72;
			return true;
		}
	};

	private static ColumnSample sample(int x, int z, int cell) {
		ColumnSample out = new ColumnSample();
		out.clear();
		new HearthOverlay(LAND, HOUSE).sample(x, z, cell, out);
		return out;
	}

	@Test
	void aHouseHasWallsToItsEavesAndARoofUpToItsRidge() {
		ColumnSample edge = sample(0, 4, 1), middle = sample(4, 4, 1), outside = sample(12, 4, 1);
		assertEquals(69, edge.ground, 1e-4, "the edge stands to the eaves");
		assertEquals(73, middle.ground, 1e-4, "the middle, four blocks in, reaches the ridge");
		assertEquals(ROOF, middle.top);
		assertEquals(WALL, middle.side);
		assertEquals(0, middle.cover, 1e-6);
		assertEquals(64, outside.ground, 1e-4, "beside it, the land");
	}

	@Test
	void fartherOutATownIsTheShareOfTheCellItCovers() {
		// a 16-block cell over the house: about a quarter of it under roof
		ColumnSample far = sample(8, 8, 16);
		assertTrue(far.top != 0x60A040, "the roofs tint the cell");
		assertTrue(far.light > 4, "and it glows at night");
		ColumnSample empty = sample(1000, 1000, 16);
		assertEquals(0x60A040, empty.top);
	}
}
