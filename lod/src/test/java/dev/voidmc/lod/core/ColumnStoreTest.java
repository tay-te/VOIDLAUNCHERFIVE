package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnStoreTest {
	static final Surface GRASS = new Surface(0x5D9A3A, 0x7A6A40, 0x866043, 0x7E7E7E, 0x3F7A22, 0x3F76E4, 3, false, false, 1, 2, 3, 4, 5,
		BiomeIds.id("minecraft:plains"));
	static final Surface SAND = new Surface(0xDBCFA3, 0xDBCFA3, 0xDBCFA3, 0xD8CB9B, 0, 0x3F76E4, 3, false, false, 6, 7, 7, 8, 0);
	static final Surface CONCRETE = new Surface(0x8E2121, 0x8E2121, 0x8E2121, 0x8E2121, 0, 0x3F76E4, 0, false, false, 9, 10, 10, 10, 0);

	/** A chunk of {@code surface} with the ground at {@code ground(x, z)} (world block coordinates). */
	static ChunkColumns chunk(int cx, int cz, Surface surface, java.util.function.IntBinaryOperator ground) {
		int[] columns = new int[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				columns[x + z * 16] = ChunkColumns.pack(ground.applyAsInt(cx * 16 + x, cz * 16 + z), 0, 0, 0);
			}
		}
		return new ChunkColumns(columns, new Surface[]{surface});
	}

	@Test
	void columnsPackAndUnpack() {
		int packed = ChunkColumns.pack(-37, 12, 9, 41);
		assertEquals(-37, ChunkColumns.ground(packed));
		assertEquals(12, ChunkColumns.depth(packed));
		assertEquals(9, ChunkColumns.canopy(packed));
		assertEquals(41, ChunkColumns.look(packed));
		// the lowest possible ground is still a column, not the void
		assertTrue(ChunkColumns.pack(-5000, 0, 0, 0) != ChunkColumns.VOID);
		assertEquals(ChunkColumns.MAX_DEPTH, ChunkColumns.depth(ChunkColumns.pack(0, 999, 0, 0)));
	}

	@Test
	void putTellsTheListenerOnlyWhatChanged() {
		ColumnStore store = new ColumnStore();
		List<int[]> changes = new ArrayList<>();
		store.setListener((x0, z0, x1, z1, columns) -> changes.add(new int[]{x0, z0, x1, z1, columns}));
		ChunkColumns flat = chunk(3, -2, GRASS, (x, z) -> 70);
		assertTrue(store.put(3, -2, flat));
		assertArrayEquals(new int[]{48, -32, 64, -16, 256}, changes.get(0), "a new chunk changes all of it");
		// the same chunk again: nothing a far view could see has changed
		assertFalse(store.put(3, -2, chunk(3, -2, GRASS, (x, z) -> 70)));
		assertEquals(1, changes.size());
		// one pillar placed: only its column
		ChunkColumns pillar = chunk(3, -2, GRASS, (x, z) -> x == 53 && z == -25 ? 90 : 70);
		assertTrue(store.put(3, -2, pillar));
		assertArrayEquals(new int[]{53, -25, 54, -24, 1}, changes.get(1));
		assertSame(pillar, store.chunk(3, -2));
		assertEquals(1, store.chunkCount());
		// grass under a block turned to dirt: kept, but nothing a far view shows changed
		Surface dirtUnder = new Surface(GRASS.top(), GRASS.side(), 0x866043, 0x707070, GRASS.leaves(), GRASS.waterColor(), 2, false, false,
			GRASS.topMaterial(), GRASS.sideMaterial(), 11, 12, GRASS.leavesMaterial());
		int[] columns = pillar.columns().clone();
		ChunkColumns under = new ChunkColumns(columns, new Surface[]{dirtUnder});
		assertTrue(store.put(3, -2, under));
		assertSame(under, store.chunk(3, -2));
		assertEquals(2, changes.size());
	}

	@Test
	void thePyramidIsTheSumOfItsChunks() {
		ColumnStore store = new ColumnStore();
		// a region with some chunks known, some not, hills of two kinds
		for (int cz = 0; cz < 32; cz++) {
			for (int cx = 0; cx < 32; cx++) {
				if ((cx * 7 + cz * 3) % 5 == 0) {
					continue;
				}
				Surface s = (cx + cz) % 3 == 0 ? SAND : GRASS;
				store.put(cx, cz, chunk(cx, cz, s, (x, z) -> 64 + (int) (20 * Math.sin(x / 40.0) * Math.cos(z / 33.0))));
			}
		}
		ColumnStore.Region region = store.region(0, 0);
		for (int size = 32; size <= 256; size *= 2) {
			for (int z = 0; z < 512; z += size) {
				for (int x = 0; x < 512; x += size) {
					ColumnSummary direct = new ColumnSummary();
					for (int cz = z >> 4; cz < (z + size) >> 4; cz++) {
						for (int cx = x >> 4; cx < (x + size) >> 4; cx++) {
							ChunkColumns c = store.chunk(cx, cz);
							if (c != null) {
								for (int packed : c.columns) {
									direct.add(packed, c.surface(packed), false);
								}
							}
						}
					}
					int[] pyramid = ColumnStore.summary(region, x, z, size);
					assertNotNull(pyramid);
					int[] expected = direct.freeze();
					for (int i = 0; i < ColumnSummary.SUMS; i++) {
						assertEquals(expected[i], pyramid[i], "sum " + i + " of the " + size + " cell at " + x + "," + z);
					}
				}
			}
		}
	}

	@Test
	void loadingNeverOverwritesWhatWasSeenSince() {
		ColumnStore store = new ColumnStore();
		ChunkColumns fresh = chunk(0, 0, CONCRETE, (x, z) -> 100);
		store.put(0, 0, fresh);
		long changes = store.region(0, 0).changes();
		ChunkColumns[] saved = new ChunkColumns[ColumnStore.REGION_CHUNKS * ColumnStore.REGION_CHUNKS];
		saved[0] = chunk(0, 0, GRASS, (x, z) -> 70);
		saved[1] = chunk(1, 0, GRASS, (x, z) -> 71);
		store.load(0, 0, saved);
		assertSame(fresh, store.chunk(0, 0), "the chunk seen this session wins");
		assertSame(saved[1], store.chunk(1, 0));
		assertEquals(changes, store.region(0, 0).changes(), "loading is not a change to save");
		assertEquals(2, store.chunkCount());
	}

	@Test
	void anyAnswersForAreasBigAndSmall() {
		ColumnStore store = new ColumnStore();
		store.put(100, -40, chunk(100, -40, GRASS, (x, z) -> 70));
		assertTrue(store.any(1600, -640, 1616, -624));
		assertTrue(store.any(1610, -630, 1611, -629));
		assertFalse(store.any(1616, -640, 1700, -600));
		assertTrue(store.any(-1 << 20, -1 << 20, 1 << 20, 1 << 20));
		assertFalse(store.any(0, 0, 1 << 20, 1 << 20));
	}

	@Test
	void dropKeepsARegionThatChangedSinceItWasSaved() {
		ColumnStore store = new ColumnStore();
		store.put(0, 0, chunk(0, 0, GRASS, (x, z) -> 70));
		long saved = store.region(0, 0).changes();
		store.put(1, 0, chunk(1, 0, GRASS, (x, z) -> 70));
		assertFalse(store.drop(0, 0, saved));
		assertNotNull(store.region(0, 0));
		assertTrue(store.drop(0, 0, store.region(0, 0).changes()));
		assertNull(store.region(0, 0));
	}
}
