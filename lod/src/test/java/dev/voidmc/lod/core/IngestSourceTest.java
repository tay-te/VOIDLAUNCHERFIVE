package dev.voidmc.lod.core;

import static dev.voidmc.lod.core.ColumnStoreTest.CONCRETE;
import static dev.voidmc.lod.core.ColumnStoreTest.GRASS;
import static dev.voidmc.lod.core.ColumnStoreTest.chunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngestSourceTest {
	/** The generator's land: flat grass at 64. */
	private static final TerrainSource GENERATED = (x, z, cell, out) -> {
		out.ground = 64;
		out.top = 0x60A040;
		out.side = 0x806040;
		out.canopy = 10;
		out.cover = 0.5F;
		out.leaves = 0x305020;
		out.topMaterial = 77;
	};

	private static ColumnSample sample(TerrainSource source, int x, int z, int cell) {
		ColumnSample out = new ColumnSample();
		out.clear();
		source.sample(x, z, cell, out);
		return out;
	}

	@Test
	void seenLandWinsOverTheGenerator() {
		ColumnStore store = new ColumnStore();
		// a red concrete plateau at 100 over chunks (0..3, 0..3): 64 x 64 blocks
		for (int cz = 0; cz < 4; cz++) {
			for (int cx = 0; cx < 4; cx++) {
				store.put(cx, cz, chunk(cx, cz, CONCRETE, (x, z) -> 100));
			}
		}
		IngestSource source = new IngestSource(store, GENERATED);
		for (int cell = 1; cell <= 32; cell *= 2) {
			ColumnSample s = sample(source, 32, 32, cell);
			assertEquals(100, s.ground, 1e-4, "cell " + cell);
			assertEquals(CONCRETE.top(), s.top, "cell " + cell);
			assertEquals(CONCRETE.topMaterial(), s.topMaterial, "cell " + cell);
			assertEquals(0, s.cover, 1e-6, "no trees on concrete, cell " + cell);
		}
		// where nothing has been seen, the generator's land
		ColumnSample far = sample(source, 1000, 1000, 4);
		assertEquals(64, far.ground, 1e-6);
		assertEquals(77, far.topMaterial);
	}

	@Test
	void theEdgeOfTheSeenLandBlendsIntoTheGenerated() {
		ColumnStore store = new ColumnStore();
		store.put(0, 0, chunk(0, 0, CONCRETE, (x, z) -> 100));
		IngestSource source = new IngestSource(store, GENERATED);
		// a 32-block cell centred on (16, 16) covers the chunk (0..16) and three unseen quarters: a quarter seen
		ColumnSample s = sample(source, 16, 16, 32);
		assertEquals(64 + 0.25F * 36, s.ground, 1e-3);
		assertEquals(77, s.topMaterial, "the generator covers more of the cell");
		assertEquals(Colors.mix(0x60A040, CONCRETE.top(), 0.25F), s.top);
	}

	@Test
	void crownsAreBlocksUpCloseAndACanopyFarOut() {
		ColumnStore store = new ColumnStore();
		int[] columns = new int[256];
		for (int i = 0; i < 256; i++) {
			// every other column has leaves topping out 8 blocks over the ground
			columns[i] = ChunkColumns.pack(70, 0, (i & 1) == 0 ? 8 : 0, 0);
		}
		store.put(0, 0, new ChunkColumns(columns, new Surface[]{GRASS}));
		IngestSource source = new IngestSource(store, null);
		ColumnSample crown = sample(source, 0, 0, 1);
		assertEquals(78, crown.ground, 1e-6, "a leaf block stands where it stands");
		assertEquals(GRASS.leaves(), crown.top);
		assertEquals(GRASS.leavesMaterial(), crown.topMaterial);
		ColumnSample floor = sample(source, 1, 0, 1);
		assertEquals(70, floor.ground, 1e-6);
		assertEquals(GRASS.top(), floor.top);
		// far out: the ground with a canopy 8 tall over half of it
		ColumnSample canopy = sample(source, 8, 8, 8);
		assertEquals(70, canopy.ground, 1e-6);
		assertEquals(8, canopy.canopy, 1e-6);
		assertEquals(0.5F, canopy.cover, 1e-6);
		assertEquals(Colors.scale(GRASS.leaves(), ColumnSummary.CROWN_SHADE), canopy.leaves);
		assertEquals(GRASS.top(), canopy.top);
	}

	@Test
	void waterIsWaterWhereMostOfTheCellIsWet() {
		ColumnStore store = new ColumnStore();
		int[] columns = new int[256];
		for (int i = 0; i < 256; i++) {
			int x = i & 15;
			// a lake 6 deep over x < 12, a shore of grass beyond
			columns[i] = x < 12 ? ChunkColumns.pack(57, 6, 0, 0) : ChunkColumns.pack(64, 0, 0, 0);
		}
		store.put(0, 0, new ChunkColumns(columns, new Surface[]{GRASS}));
		IngestSource source = new IngestSource(store, null);
		ColumnSample lake = sample(source, 8, 8, 16);
		assertEquals(63, lake.water);
		assertTrue(lake.water > lake.ground);
		assertEquals(57, lake.ground, 1e-4);
		// a cell mostly of shore is land, with the water's colour mixed in for the share it covers
		ColumnSample shore = sample(source, 12, 8, 4);
		assertEquals(Integer.MIN_VALUE, shore.water);
		assertEquals(64, shore.ground, 1e-4);
		assertTrue(shore.top != GRASS.top(), "a thread of water shows");
	}

	@Test
	void onAServerUnseenLandIsEmptyAndItsTilesAreSkipped() {
		ColumnStore store = new ColumnStore();
		store.put(10, 10, chunk(10, 10, GRASS, (x, z) -> 70));
		IngestSource source = new IngestSource(store, null);
		assertTrue(sample(source, 0, 0, 4).empty);
		assertFalse(sample(source, 165, 165, 1).empty);
		assertFalse(source.covers(0, 0, 128, 128));
		assertTrue(source.covers(128, 128, 192, 192));
		assertTrue(new IngestSource(store, GENERATED).covers(0, 0, 128, 128));
	}

	@Test
	void theVoidIsEmpty() {
		ColumnStore store = new ColumnStore();
		store.put(0, 0, new ChunkColumns(new int[256], new Surface[0]));
		IngestSource source = new IngestSource(store, GENERATED);
		assertTrue(sample(source, 3, 3, 1).empty);
		assertTrue(sample(source, 8, 8, 16).empty);
	}

	@Test
	void everyLevelAgreesOnASquare() {
		// the same land summed as columns, as chunks and as the pyramid gives the same cell
		ColumnStore store = new ColumnStore();
		for (int cz = 0; cz < 8; cz++) {
			for (int cx = 0; cx < 8; cx++) {
				store.put(cx, cz, chunk(cx, cz, (cx ^ cz) % 2 == 0 ? GRASS : CONCRETE, (x, z) -> 60 + (x * 3 + z * 5) % 17));
			}
		}
		IngestSource source = new IngestSource(store, null);
		// a 64-block cell at (64, 64): four 32-block pyramid cells; the same land as 16 chunk summaries, or columns
		ColumnSample pyramid = sample(source, 64, 64, 64);
		ColumnSummary direct = new ColumnSummary();
		for (int z = 32; z < 96; z++) {
			for (int x = 32; x < 96; x++) {
				ChunkColumns c = store.chunk(x >> 4, z >> 4);
				int packed = c.column(x & 15, z & 15);
				direct.add(packed, c.surface(packed), false);
			}
		}
		ColumnSample columns = new ColumnSample();
		columns.clear();
		direct.write(columns);
		assertEquals(columns.ground, pyramid.ground, 1e-4);
		assertEquals(columns.top, pyramid.top);
		assertEquals(columns.side, pyramid.side);
		assertEquals(columns.rock, pyramid.rock);
	}

	@Test
	void tilesOverSeenLandBuildQuickly() {
		// 64 x 64 chunks of hills (1 km square), tiles built over them at every level, timed
		ColumnStore store = new ColumnStore();
		for (int cz = 0; cz < 64; cz++) {
			for (int cx = 0; cx < 64; cx++) {
				store.put(cx, cz, chunk(cx, cz, (cx + cz) % 3 == 0 ? CONCRETE : GRASS, (x, z) -> 64 + (int) (30 * Math.sin(x / 90.0) * Math.cos(z / 70.0))));
			}
		}
		IngestSource source = new IngestSource(store, null);
		TileBuilder builder = new TileBuilder();
		StringBuilder line = new StringBuilder("tiles over seen land:");
		for (int level = 0; level <= 6; level++) {
			long key = TileKey.of(level, 0, 0);
			for (int i = 0; i < 3; i++) {
				builder.build(source, key);
			}
			long t0 = System.nanoTime();
			int n = 10;
			for (int i = 0; i < n; i++) {
				builder.build(source, key);
			}
			line.append(String.format(java.util.Locale.ROOT, " L%d %.2f ms", level, (System.nanoTime() - t0) / 1e6 / n));
		}
		System.out.println(line);
		assertTrue(builder.build(source, TileKey.of(2, 0, 0)).vertexCount() > 0);
	}
}
