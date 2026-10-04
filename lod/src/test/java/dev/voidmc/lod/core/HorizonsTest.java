package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HorizonsTest {
	/** A plain at y 64 with a range 500 blocks high from x = 1000 to 1400. */
	private static final TerrainSource RIDGE = (x, z, cell, out) -> {
		out.ground = x >= 1000 && x <= 1400 ? 564 : 64;
		out.top = 0x55AA55;
	};

	/** The coarse grids around a tile, as the tiles built before it would have left them. */
	private static Heights coarse(TerrainSource source, long key) {
		Heights heights = new Heights();
		int level = TileKey.level(key);
		int cx = TileKey.minX(key), cz = TileKey.minZ(key);
		for (int l = level + 1; l <= TileKey.MAX_LEVEL; l++) {
			int span = TileKey.span(l);
			int reach = Math.max(1, (Horizons.REACH + span) / span);
			int tx = Math.floorDiv(cx, span), tz = Math.floorDiv(cz, span);
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dx = -reach; dx <= reach; dx++) {
					if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
						heights.fill(source, TileKey.of(l, tx + dx, tz + dz));
					}
				}
			}
		}
		return heights;
	}

	private static Horizons lightTile(TerrainSource source, long key) {
		TileBuilder builder = new TileBuilder(false);
		Heights heights = coarse(source, key);
		builder.build(source, key, heights);
		Samples s = builder.samples();
		Horizons h = new Horizons();
		h.compute(s, heights, TileKey.level(key), TileKey.TILE + 1);
		return h;
	}

	@Test
	void aRangeToTheEastHidesTheMorningSunAsHighAsItStands() {
		// a level 3 tile (8-block cells) from x = 0 to 512: the range stands 500 blocks up, 1000 - x blocks east
		long key = TileKey.of(3, 0, 0);
		TileBuilder builder = new TileBuilder(false);
		Heights heights = coarse(RIDGE, key);
		builder.build(RIDGE, key, heights);
		Samples s = builder.samples();
		Horizons h = new Horizons();
		h.compute(s, heights, 3, TileKey.TILE + 1);
		for (int i = 0; i <= TileKey.TILE; i += 16) {
			int k = s.at(i, 32);
			double x = s.x(i);
			int expected = Horizons.encode(500 / (1000 - x));
			// the far land comes from coarser grids, so the range's near edge may sit a cell or two further off
			assertTrue(Math.abs(h.east[k] - expected) <= 6, "x " + x + ": east horizon " + h.east[k] + ", expected " + expected);
			assertEquals(0, h.west[k], "nothing to the west at x " + x);
			assertTrue(h.occlusion[k] > 0 && h.occlusion[k] < 64, "a quarter of the sky at most: " + h.occlusion[k]);
		}
	}

	@Test
	void aValleyFloorSeesLessSkyThanThePlainAroundIt() {
		// a trench 40 blocks deep and 32 wide along z, through the middle of a level 2 tile (4-block cells)
		TerrainSource trench = (x, z, cell, out) -> {
			out.ground = x >= 112 && x < 144 ? 24 : 64;
			out.top = 0x55AA55;
		};
		long key = TileKey.of(2, 0, 0);
		TileBuilder builder = new TileBuilder(false);
		Heights heights = coarse(trench, key);
		builder.build(trench, key, heights);
		Samples s = builder.samples();
		Horizons h = new Horizons();
		h.compute(s, heights, 2, TileKey.TILE + 1);
		int floor = h.occlusion[s.at(32, 32)];
		int plain = h.occlusion[s.at(4, 32)];
		assertTrue(floor > 60, "the trench floor sees a strip of sky: " + floor);
		assertTrue(plain < 8, "the plain sees nearly all of it: " + plain);
		// and its walls hide the sun from both sides when it is low
		assertTrue(h.east[s.at(30, 32)] > 0 && h.west[s.at(34, 32)] > 0);
	}

	@Test
	void flatLandIsOpenAndUnshadowed() {
		TerrainSource flat = (x, z, cell, out) -> {
			out.ground = 64;
			out.top = 0x55AA55;
		};
		Horizons h = lightTile(flat, TileKey.of(4, 3, -2));
		for (int k = 0; k < h.east.length; k++) {
			assertEquals(0, h.east[k]);
			assertEquals(0, h.west[k]);
			assertEquals(0, h.occlusion[k]);
		}
	}
}
