package dev.voidmc.atlas.core.landmarks;

import static dev.voidmc.atlas.core.landmarks.RegionAnalysisTest.FOREST;
import static dev.voidmc.atlas.core.landmarks.RegionAnalysisTest.PLAINS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.core.landmarks.AreaIndex.Area;
import dev.voidmc.atlas.core.landmarks.RegionAnalysisTest.Col;
import dev.voidmc.atlas.core.landmarks.RegionAnalysisTest.Land;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Patches joined across region borders into named areas, which keep their ids as they grow and merge. */
class AreaIndexTest {
	private static final int CELL_BLOCKS = RegionFeatures.CELL;
	private static final long FULL = RegionFeatures.CELL_AREA;

	/** A region analysed from a function of world block coordinates. */
	private static RegionFeatures features(int rx, int rz, Land world) {
		int ox = rx * 512;
		int oz = rz * 512;
		return RegionAnalysisTest.analyse(RegionAnalysisTest.region(rx, rz, (x, z) -> world.at(ox + x, oz + z)));
	}

	private static RegionFeatures all(int rx, int rz, int biome) {
		return features(rx, rz, (x, z) -> Col.ground(64, biome));
	}

	private static List<Area> of(List<Area> areas, String kind) {
		return areas.stream().filter(a -> a.kind().equals(kind)).toList();
	}

	@Test
	void oneBiomeAcrossABorderIsOneRegion() {
		AreaIndex index = new AreaIndex();
		index.put(all(0, 0, PLAINS));
		index.put(all(1, 0, PLAINS));
		List<Area> areas = index.rebuild();
		assertEquals(1, areas.size(), areas.toString());
		Area a = areas.get(0);
		assertEquals(Place.REGION, a.kind());
		assertEquals(PLAINS, a.biome());
		assertEquals(2 * FULL, a.cells());
		assertTrue(a.major());
		assertEquals(a.id(), index.regionAt(100, 100));
		assertEquals(a.id(), index.regionAt(600, 100));
		assertNull(index.waterAt(100, 100));
	}

	@Test
	void differentBiomesAcrossABorderDoNotJoin() {
		AreaIndex index = new AreaIndex();
		index.put(all(0, 0, PLAINS));
		index.put(all(1, 0, FOREST));
		List<Area> areas = index.rebuild();
		assertEquals(2, areas.size(), areas.toString());
		assertTrue(areas.stream().allMatch(a -> a.cells() == FULL));
		assertNotEquals(index.regionAt(100, 100), index.regionAt(600, 100));
	}

	@Test
	void aLakeSplitByABorderIsOneLake() {
		// 32 x 80 blocks of water across the border at x = 512: 20 cells each side, under a lake's threshold alone
		Land land = (x, z) -> x >= 496 && x < 528 && z >= 200 && z < 280 ? Col.water(62, 3, PLAINS) : Col.ground(64, PLAINS);
		AreaIndex index = new AreaIndex();
		index.put(features(0, 0, land));
		index.put(features(1, 0, land));
		List<Area> areas = index.rebuild();
		List<Area> lakes = of(areas, Place.LAKE);
		assertEquals(1, lakes.size(), areas.toString());
		Area lake = lakes.get(0);
		assertEquals(40, lake.cells());
		assertTrue(40 / 2 < AreaIndex.LAKE_MIN_CELLS, "each half alone is too small");
		assertEquals(lake.id(), index.waterAt(500, 240));
		assertEquals(lake.id(), index.waterAt(520, 240));
		assertNull(index.waterAt(400, 240));
	}

	@Test
	void smallAreasAreNotNamed() {
		// forest with a plains glade of 15 x 15 cells and a pond of 4 x 4 cells
		AreaIndex index = new AreaIndex();
		index.put(features(0, 0, (x, z) -> {
			if (x >= 300 && x < 332 && z >= 300 && z < 332) {
				return Col.water(62, 2, FOREST);
			}
			if (x >= 40 && x < 40 + 15 * CELL_BLOCKS && z >= 40 && z < 40 + 15 * CELL_BLOCKS) {
				return Col.ground(64, PLAINS);
			}
			return Col.ground(64, FOREST);
		}));
		List<Area> areas = index.rebuild();
		assertEquals(1, areas.size(), areas.toString());
		assertEquals(FOREST, areas.get(0).biome());
		assertTrue(of(areas, Place.LAKE).isEmpty());
		assertNull(index.regionAt(100, 100), "the glade has no name");
		assertNull(index.waterAt(310, 310), "nor the pond");
		assertEquals(areas.get(0).id(), index.regionAt(450, 450));
	}

	@Test
	void areasAreFoundAtNegativeCoordinates() {
		// region (-1, -1) is blocks -512..-1; a lake at -300..-221
		AreaIndex index = new AreaIndex();
		index.put(features(-1, -1, (x, z) -> x >= -300 && x < -220 && z >= -300 && z < -220 ? Col.water(62, 3, PLAINS) : Col.ground(64, PLAINS)));
		List<Area> areas = index.rebuild();
		assertEquals(2, areas.size(), areas.toString());
		String region = index.regionAt(-100, -100);
		assertNotNull(region);
		assertEquals(region, index.regionAt(-1, -1));
		assertEquals(region, index.regionAt(-512, -512));
		String lake = index.waterAt(-260, -260);
		assertNotNull(lake);
		assertEquals(Place.LAKE, of(areas, Place.LAKE).get(0).kind());
		assertNull(index.waterAt(-100, -100), "dry land");
		assertNull(index.regionAt(-513, -100), "a region not analysed");
		assertNull(index.regionAt(0, 0));
		assertNull(index.regionAt(100, -100));
		Area l = of(areas, Place.LAKE).get(0);
		assertTrue(l.labelX() < 0 && l.labelZ() < 0, "its label is where it is");
		assertEquals(lake, index.waterAt(l.labelX(), l.labelZ()));
	}

	@Test
	void anAreaKeepsItsIdAsItGrows() {
		// first only the west half of region (0, 0) is seen
		AreaIndex first = new AreaIndex();
		first.put(features(0, 0, (x, z) -> x < 256 ? Col.ground(64, PLAINS) : null));
		List<Area> before = first.rebuild();
		assertEquals(1, before.size());
		Area old = before.get(0);

		// next session: the whole region and its east neighbour
		AreaIndex grown = new AreaIndex();
		grown.remember(before);
		grown.put(all(0, 0, PLAINS));
		grown.put(all(1, 0, PLAINS));
		List<Area> after = grown.rebuild();
		assertEquals(1, after.size());
		assertEquals(old.id(), after.get(0).id());
		assertEquals(2 * FULL, after.get(0).cells());

		// without the earlier ids it would have had another one
		AreaIndex fresh = new AreaIndex();
		fresh.put(all(0, 0, PLAINS));
		fresh.put(all(1, 0, PLAINS));
		assertNotEquals(old.id(), fresh.rebuild().get(0).id(), "the test needs the label to have moved");

		// and in the same session, rebuilt after more land came in
		first.put(all(0, 0, PLAINS));
		first.put(all(1, 0, PLAINS));
		assertEquals(old.id(), first.rebuild().get(0).id());
	}

	@Test
	void twoAreasMergingKeepTheBiggerOnesId() {
		AreaIndex index = new AreaIndex();
		index.put(all(0, 0, PLAINS));
		// region (2, 0): plains in its west half, forest in its east
		index.put(features(2, 0, (x, z) -> Col.ground(64, x < 1024 + 256 ? PLAINS : FOREST)));
		List<Area> before = index.rebuild();
		assertEquals(3, before.size(), before.toString());
		List<Area> plains = before.stream().filter(a -> a.biome() == PLAINS).toList();
		assertEquals(2, plains.size());
		Area big = plains.stream().filter(a -> a.cells() == FULL).findFirst().orElseThrow();
		Area small = plains.stream().filter(a -> a.cells() == FULL / 2).findFirst().orElseThrow();
		Area forest = before.stream().filter(a -> a.biome() == FOREST).findFirst().orElseThrow();

		// region (1, 0) joins them
		AreaIndex next = new AreaIndex();
		next.remember(before);
		next.put(all(0, 0, PLAINS));
		next.put(features(2, 0, (x, z) -> Col.ground(64, x < 1024 + 256 ? PLAINS : FOREST)));
		next.put(all(1, 0, PLAINS));
		List<Area> after = next.rebuild();
		assertEquals(2, after.size(), after.toString());
		Area merged = after.stream().filter(a -> a.biome() == PLAINS).findFirst().orElseThrow();
		assertEquals(2 * FULL + FULL / 2, merged.cells());
		assertEquals(big.id(), merged.id(), "the bigger one's id");
		assertNotEquals(small.id(), merged.id());
		assertEquals(forest.id(), after.stream().filter(a -> a.biome() == FOREST).findFirst().orElseThrow().id());
		assertEquals(merged.id(), next.regionAt(1100, 100));
		assertEquals(merged.id(), next.regionAt(100, 100));
	}

	@Test
	void rebuildingIsDeterministic() {
		RegionFeatures[] grid = {all(0, 0, PLAINS), all(1, 0, PLAINS), all(0, 1, PLAINS), all(1, 1, PLAINS), all(2, 0, FOREST), all(2, 1, FOREST)};
		AreaIndex a = new AreaIndex();
		for (RegionFeatures f : grid) {
			a.put(f);
		}
		List<Area> first = a.rebuild();
		assertEquals(first, a.rebuild(), "the same again");
		// the same land put in the other order
		AreaIndex b = new AreaIndex();
		for (int i = grid.length - 1; i >= 0; i--) {
			b.put(grid[i]);
		}
		assertEquals(first, b.rebuild());
	}

	@Test
	void anEmptyIndexHasNoAreas() {
		AreaIndex index = new AreaIndex();
		assertTrue(index.rebuild().isEmpty());
		assertNull(index.regionAt(0, 0));
		assertNull(index.waterAt(-5, -5));
	}
}
