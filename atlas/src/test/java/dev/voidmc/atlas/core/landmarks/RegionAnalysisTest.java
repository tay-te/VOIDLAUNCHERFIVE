package dev.voidmc.atlas.core.landmarks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.landmarks.RegionFeatures.Patch;
import dev.voidmc.atlas.core.landmarks.RegionFeatures.PatchKind;
import dev.voidmc.atlas.core.landmarks.RegionFeatures.Point;
import dev.voidmc.atlas.core.landmarks.RegionFeatures.PointKind;
import dev.voidmc.atlas.core.names.BiomeFamily;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a region's map is read for: peaks by prominence, waterfalls, ravines, and patches of biome and water. */
class RegionAnalysisTest {
	static final int GRASS = 1;
	static final int LEAVES = 2;
	static final int SAND = 3;
	static final int PLAINS = 1;
	static final int OCEAN = 2;
	static final int RIVER = 3;
	static final int FOREST = 4;

	static final RegionAnalysis.Lookup LOOKUP = new RegionAnalysis.Lookup() {
		@Override
		public boolean leaves(int block) {
			return block == LEAVES;
		}

		@Override
		public BiomeFamily family(int biome) {
			return switch (biome) {
				case PLAINS -> BiomeFamily.PLAINS;
				case OCEAN -> BiomeFamily.OCEAN;
				case RIVER -> BiomeFamily.RIVER;
				case FOREST -> BiomeFamily.FOREST;
				default -> BiomeFamily.OTHER;
			};
		}
	};

	/** One column of a synthetic region; return null from a {@link Land} for an unexplored column. */
	record Col(int height, int block, int depth, int biome) {
		static Col ground(int height, int biome) {
			return new Col(height, GRASS, 0, biome);
		}

		static Col water(int surface, int depth, int biome) {
			return new Col(surface, SAND, depth, biome);
		}
	}

	interface Land {
		Col at(int x, int z);
	}

	/** A region filled from a function of local block coordinates (0..511). */
	static RegionRaw region(int rx, int rz, Land land) {
		RegionRaw raw = new RegionRaw(rx, rz);
		for (int z = 0; z < RegionRaw.SIZE; z++) {
			for (int x = 0; x < RegionRaw.SIZE; x++) {
				Col c = land.at(x, z);
				if (c != null) {
					raw.set(x, z, c.height(), c.block(), c.depth(), c.biome(), RegionRaw.KNOWN);
				}
			}
		}
		return raw;
	}

	/** Analyses a region alone (its neighbours unexplored). */
	static RegionFeatures analyse(RegionRaw centre) {
		RegionRaw[] around = new RegionRaw[9];
		around[4] = centre;
		return RegionAnalysis.analyse(around, LOOKUP);
	}

	static List<Point> points(RegionFeatures f, PointKind kind) {
		return f.points.stream().filter(p -> p.kind() == kind).toList();
	}

	static int cone(int x, int z, int cx, int cz, int summit) {
		return summit - (int) Math.round(Math.hypot(x - cx, z - cz));
	}

	// --- peaks -----------------------------------------------------------------------------------------------------

	@Test
	void aMountainIsAPeakWithItsProminence() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(Math.max(64, cone(x, z, 256, 256, 184)), PLAINS)));
		List<Point> peaks = points(f, PointKind.PEAK);
		assertEquals(1, peaks.size(), peaks.toString());
		Point p = peaks.get(0);
		assertTrue(Math.abs(p.x() - 256) <= 4 && Math.abs(p.z() - 256) <= 4, "at the summit: " + p);
		assertEquals(184, p.y());
		assertTrue(Math.abs(p.size() - 120) <= 4, "prominence " + p.size());
		assertEquals(PLAINS, p.biome());
	}

	@Test
	void gentleBumpsAreNoPeaks() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(95 + (int) Math.round(5 * Math.sin(x / 10.0) * Math.sin(z / 10.0)), PLAINS)));
		assertTrue(points(f, PointKind.PEAK).isEmpty());
	}

	@Test
	void aLowerSummitsProminenceIsItsHeightAboveTheSaddle() {
		// A (184) and B (164) 160 blocks apart, joined by a ridge at 124
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(twin(x, z, 164), PLAINS)));
		List<Point> peaks = points(f, PointKind.PEAK).stream().sorted(Comparator.comparingInt(Point::x)).toList();
		assertEquals(2, peaks.size(), peaks.toString());
		Point a = peaks.get(0);
		Point b = peaks.get(1);
		assertTrue(Math.abs(a.x() - 176) <= 4 && Math.abs(b.x() - 336) <= 4, peaks.toString());
		assertTrue(Math.abs(a.size() - 120) <= 4, "the higher one's prominence: " + a.size());
		assertTrue(Math.abs(b.size() - 40) <= 4, "the lower one's prominence above the saddle: " + b.size());

		// a lower summit only 20 above the saddle is not a peak of its own
		RegionFeatures low = analyse(region(0, 0, (x, z) -> Col.ground(twin(x, z, 144), PLAINS)));
		List<Point> one = points(low, PointKind.PEAK);
		assertEquals(1, one.size(), one.toString());
		assertTrue(Math.abs(one.get(0).x() - 176) <= 4);
	}

	private static int twin(int x, int z, int second) {
		int h = Math.max(64, Math.max(cone(x, z, 176, 256, 184), cone(x, z, 336, 256, second)));
		if (x >= 176 && x <= 336) {
			h = Math.max(h, 124 - 2 * Math.abs(z - 256));
		}
		return h;
	}

	@Test
	void prominenceStopsAtUnexploredLand() {
		// the land east of x = 296 (the mountain's slope at about 144) has not been seen
		RegionFeatures f = analyse(region(0, 0, (x, z) -> x >= 296 ? null : Col.ground(Math.max(64, cone(x, z, 256, 256, 184)), PLAINS)));
		for (Point p : points(f, PointKind.PEAK)) {
			assertTrue(p.size() <= 184 - 140, "not claimed past what was seen: " + p);
		}
	}

	@Test
	void treesAreNoPeaks() {
		// a raised plain with a canopy six blocks over it in clumps, and one tall stand
		RegionFeatures f = analyse(region(0, 0, (x, z) -> {
			boolean tree = (x % 12) < 5 && (z % 12) < 5;
			return tree ? new Col(106, LEAVES, 0, FOREST) : Col.ground(100, FOREST);
		}));
		assertTrue(points(f, PointKind.PEAK).isEmpty(), f.points.toString());
	}

	// --- waterfalls ------------------------------------------------------------------------------------------------

	@Test
	void waterStandingAboveTheWaterNextToItIsAWaterfall() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> {
			if (z >= 250 && z < 254 && x >= 100 && x < 300) {
				return Col.water(x < 200 ? 80 : 64, 2, PLAINS);
			}
			return Col.ground(90, PLAINS);
		}));
		List<Point> falls = points(f, PointKind.WATERFALL);
		assertEquals(1, falls.size(), falls.toString());
		Point fall = falls.get(0);
		assertEquals(199, fall.x());
		assertEquals(80, fall.y(), "at the top");
		assertEquals(16, fall.size());
		assertTrue(fall.z() >= 250 && fall.z() < 254);
	}

	@Test
	void aGentleStreamHasNoWaterfall() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> {
			if (z >= 250 && z < 254 && x >= 100 && x < 300) {
				return Col.water(120 - (x - 100) / 2, 1, PLAINS);
			}
			return Col.ground(130, PLAINS);
		}));
		assertTrue(points(f, PointKind.WATERFALL).isEmpty());
	}

	// --- ravines ---------------------------------------------------------------------------------------------------

	@Test
	void aLongNarrowTrenchIsARavine() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(x >= 200 && x < 240 && z >= 250 && z < 255 ? 39 : 64, PLAINS)));
		List<Point> ravines = points(f, PointKind.RAVINE);
		assertEquals(1, ravines.size(), ravines.toString());
		Point r = ravines.get(0);
		assertEquals(39, r.y());
		assertEquals(25, r.size(), "its depth");
		assertTrue(r.length() >= 30 && r.length() <= 40, "its length: " + r.length());
		assertTrue(r.x() >= 200 && r.x() < 240 && r.z() >= 250 && r.z() < 255, r.toString());
	}

	@Test
	void aWideBowlIsNoRavine() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(Math.hypot(x - 256, z - 256) < 40 ? 39 : 64, PLAINS)));
		assertTrue(points(f, PointKind.RAVINE).isEmpty(), f.points.toString());
	}

	@Test
	void aClearingBetweenTallTreesIsNoRavine() {
		// tall trees, their canopy 20 above the ground, round a narrow clearing
		RegionFeatures f = analyse(region(0, 0, (x, z) -> x >= 200 && x < 240 && z >= 250 && z < 255 ? Col.ground(64, FOREST)
			: new Col(84, LEAVES, 0, FOREST)));
		assertTrue(points(f, PointKind.RAVINE).isEmpty(), f.points.toString());
	}

	// --- patches ---------------------------------------------------------------------------------------------------

	@Test
	void aLakeIsOnePatchLabelledInsideIt() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> x >= 200 && x < 260 && z >= 200 && z < 260 ? Col.water(62, 3, PLAINS)
			: Col.ground(64, PLAINS)));
		assertEquals(1, f.water.size(), f.water.toString());
		Patch lake = f.water.get(0);
		assertEquals(PatchKind.LAKE, lake.kind());
		assertTrue(lake.cells() >= 49 && lake.cells() <= 64, "about 60 x 60 blocks: " + lake.cells());
		int lx = lake.labelX(0);
		int lz = lake.labelZ(0);
		assertTrue(lx >= 216 && lx < 244 && lz >= 216 && lz < 244, "well inside: " + lx + "," + lz);
		// the lake's cells point at it; land outside it does not
		int inside = (lz / RegionFeatures.CELL) * RegionFeatures.CELLS + lx / RegionFeatures.CELL;
		assertEquals(0, f.waterAt(inside));
		assertEquals(RegionFeatures.NONE, f.waterAt(0));
		// the lake lies in the plains around it
		assertEquals(1, f.land.size());
		assertEquals(PLAINS, f.land.get(0).biome());
		assertEquals(RegionFeatures.CELL_AREA, f.land.get(0).cells());
	}

	@Test
	void theSeaIsNoLakeButItsBiomeIsAPatch() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> x < 256 ? Col.water(62, 12, OCEAN) : Col.ground(66, PLAINS)));
		assertTrue(f.water.isEmpty(), f.water.toString());
		assertEquals(2, f.land.size());
		assertEquals(List.of(PLAINS, OCEAN), f.land.stream().map(Patch::biome).sorted().toList());
		for (Patch p : f.land) {
			assertEquals(PatchKind.BIOME, p.kind());
			assertEquals(RegionFeatures.CELL_AREA / 2, p.cells());
		}
	}

	@Test
	void riverWaterIsARiverAndNoRegion() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> z >= 240 && z < 256 ? Col.water(62, 3, RIVER) : Col.ground(64, PLAINS)));
		assertEquals(1, f.water.size(), f.water.toString());
		Patch river = f.water.get(0);
		assertEquals(PatchKind.RIVER, river.kind());
		assertEquals(2 * RegionFeatures.CELLS, river.cells());
		// the river splits the plains in two and is part of neither
		assertEquals(2, f.land.size());
		assertTrue(f.land.stream().allMatch(p -> p.biome() == PLAINS));
		int riverCell = (248 / RegionFeatures.CELL) * RegionFeatures.CELLS + 10;
		assertEquals(RegionFeatures.NONE, f.landAt(riverCell));
		assertEquals(0, f.waterAt(riverCell));
	}

	@Test
	void landPatchesSplitByBiome() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> Col.ground(64, x < 256 ? PLAINS : FOREST)));
		assertEquals(2, f.land.size());
		int west = f.landAt(10 * RegionFeatures.CELLS + 5);
		int east = f.landAt(10 * RegionFeatures.CELLS + 50);
		assertFalse(west == east);
		assertEquals(PLAINS, f.land.get(west).biome());
		assertEquals(FOREST, f.land.get(east).biome());
		assertEquals(RegionFeatures.CELL_AREA / 2, f.land.get(west).cells());
		assertTrue(f.water.isEmpty());
	}

	@Test
	void unexploredCellsBelongToNoPatch() {
		RegionFeatures f = analyse(region(0, 0, (x, z) -> x < 256 ? Col.ground(64, PLAINS) : null));
		assertEquals(1, f.land.size());
		assertEquals(RegionFeatures.CELL_AREA / 2, f.land.get(0).cells());
		for (int cz = 0; cz < RegionFeatures.CELLS; cz++) {
			for (int cx = RegionFeatures.CELLS / 2; cx < RegionFeatures.CELLS; cx++) {
				int c = cz * RegionFeatures.CELLS + cx;
				assertEquals(RegionFeatures.NONE, f.landAt(c));
				assertEquals(RegionFeatures.NONE, f.waterAt(c));
			}
		}
	}

	@Test
	void featuresRoundTrip() throws IOException {
		RegionFeatures f = analyse(region(-2, 3, (x, z) -> {
			if (x >= 300 && x < 360 && z >= 300 && z < 360) {
				return Col.water(62, 3, PLAINS);
			}
			if (z >= 40 && z < 56) {
				return Col.water(62, 3, RIVER);
			}
			return Col.ground(Math.max(64, cone(x, z, 150, 200, 190)), x < 256 ? FOREST : PLAINS);
		}));
		assertFalse(f.points.isEmpty());
		assertFalse(f.water.isEmpty());
		RegionFeatures back = RegionFeatures.decode(-2, 3, f.encode());
		assertEquals(-2, back.rx);
		assertEquals(3, back.rz);
		assertEquals(f.points, back.points);
		assertEquals(f.land, back.land);
		assertEquals(f.water, back.water);
		assertArrayEquals(f.landCells, back.landCells);
		assertArrayEquals(f.waterCells, back.waterCells);
		// and an empty one
		RegionFeatures empty = RegionFeatures.decode(0, 0, RegionFeatures.empty(0, 0).encode());
		assertTrue(empty.points.isEmpty() && empty.land.isEmpty() && empty.water.isEmpty());
		assertEquals(RegionFeatures.NONE, empty.landAt(0));
	}
}
