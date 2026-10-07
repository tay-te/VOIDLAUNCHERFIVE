package dev.voidmc.lod.core;

import static dev.voidmc.lod.core.ColumnStoreTest.CONCRETE;
import static dev.voidmc.lod.core.ColumnStoreTest.GRASS;
import static dev.voidmc.lod.core.ColumnStoreTest.SAND;
import static dev.voidmc.lod.core.ColumnStoreTest.chunk;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColumnFilesTest {
	/** Names materials "m<id>" when saving; on loading, hands out ids of its own, as a new session would. */
	private static final class Renaming implements ColumnFiles.Names {
		final Map<String, Integer> ids = new HashMap<>();
		final int offset;

		Renaming(int offset) {
			this.offset = offset;
		}

		@Override
		public String name(int id) {
			return "m" + id;
		}

		@Override
		public int id(String name) {
			return this.ids.computeIfAbsent(name, n -> Integer.parseInt(n.substring(1)) + this.offset);
		}
	}

	/** A varied chunk: hills, a lake, trees, two surfaces, a hole to the void, torchlight here and there. */
	private static ChunkColumns varied(int cx, int cz) {
		int[] columns = new int[256];
		byte[] light = new byte[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int wx = cx * 16 + x;
				int wz = cz * 16 + z;
				int ground = 60 + (wx * 7 + wz * 3) % 13;
				int depth = ground < 64 ? 64 - ground : 0;
				int canopy = (wx * wz) % 11 == 0 && depth == 0 ? 6 : 0;
				columns[x + z * 16] = x == 15 && z == 15 ? ChunkColumns.VOID : ChunkColumns.pack(ground, depth, canopy, (x + z) & 1);
				light[x + z * 16] = (byte) ((wx * 5 + wz * 11) % 7 == 0 ? Math.floorMod(wx + wz, 16) : 0);
			}
		}
		return new ChunkColumns(columns, new Surface[]{GRASS, SAND}, light);
	}

	@Test
	void aRegionRoundTrips() throws Exception {
		ColumnStore store = new ColumnStore();
		store.put(-32, 64, varied(-32, 64));
		store.put(-1, 95, varied(-1, 95));
		store.put(-17, 70, chunk(-17, 70, CONCRETE, (x, z) -> 120));
		ColumnStore.Region region = store.region(-1, 2);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int written = ColumnFiles.write(region, new Renaming(0), bytes);
		assertEquals(3, written);

		ColumnStore loaded = new ColumnStore();
		Renaming names = new Renaming(1000);
		ChunkColumns[] chunks = ColumnFiles.read(new ByteArrayInputStream(bytes.toByteArray()), names, loaded, -1, 2);
		int found = 0;
		for (int i = 0; i < chunks.length; i++) {
			ChunkColumns before = region.chunk(i);
			if (before == null) {
				assertNull(chunks[i]);
				continue;
			}
			found++;
			assertArrayEquals(before.columns, chunks[i].columns);
			assertArrayEquals(before.light, chunks[i].light, "the block light comes back");
			for (int p = 0; p < before.palette.length; p++) {
				assertEquals(before.palette[p].biome(), chunks[i].palette[p].biome(), "the biome comes back");
			}
			assertEquals(before.palette.length, chunks[i].palette.length);
			for (int p = 0; p < before.palette.length; p++) {
				Surface a = before.palette[p];
				Surface b = chunks[i].palette[p];
				assertEquals(a.top(), b.top());
				assertEquals(a.rock(), b.rock());
				assertEquals(a.soilDepth(), b.soilDepth());
				// materials come back under the loading session's own ids
				assertEquals(a.topMaterial() + 1000, b.topMaterial());
				assertEquals(a.leavesMaterial() == 0 ? 0 : a.leavesMaterial() + 1000, b.leavesMaterial());
			}
			for (int s = 0; s < ColumnSummary.SUMS; s++) {
				assertEquals(before.summary[s], chunks[i].summary[s]);
			}
		}
		assertEquals(3, found);
		System.out.printf("region of 3 chunks: %d bytes%n", bytes.size());
	}

	@Test
	void savedLandComesBackInTheNextSession(@TempDir Path dir) throws Exception {
		ColumnStore first = new ColumnStore();
		for (int cz = 0; cz < 20; cz++) {
			for (int cx = 0; cx < 20; cx++) {
				first.put(cx, cz, varied(cx, cz));
			}
		}
		first.put(900, 900, chunk(900, 900, CONCRETE, (x, z) -> 120));
		ColumnFiles files = new ColumnFiles(dir, first, new Renaming(0));
		files.close().get(10, TimeUnit.SECONDS);
		assertTrue(Files.exists(dir.resolve("r.0.0.vlod")));
		assertTrue(Files.exists(dir.resolve("r.28.28.vlod")));
		long size = Files.size(dir.resolve("r.0.0.vlod"));
		System.out.printf("400 chunks: %d bytes on disk, %.0f bytes a chunk, written in %.1f ms%n", size, size / 400.0,
			files.writeNanos.get() / 1e6);

		// the next session sees one chunk change before the files are read
		ColumnStore second = new ColumnStore();
		ChunkColumns changed = chunk(5, 5, CONCRETE, (x, z) -> 99);
		second.put(5, 5, changed);
		ColumnFiles again = new ColumnFiles(dir, second, new Renaming(0));
		again.loadAround(100, 100, 1000).get(10, TimeUnit.SECONDS);
		assertEquals(400, second.chunkCount(), "the near region, not the far one");
		assertSame(changed, second.chunk(5, 5), "what was seen this session wins over what was saved");
		assertArrayEquals(first.chunk(7, 3).columns, second.chunk(7, 3).columns);
		assertNull(second.chunk(900, 900));
		again.loadAround(900 * 16, 900 * 16, 1000).get(10, TimeUnit.SECONDS);
		assertNotNull(second.chunk(900, 900));

		// and its change is saved over the old file, keeping everything else in it
		again.close().get(10, TimeUnit.SECONDS);
		ColumnStore third = new ColumnStore();
		new ColumnFiles(dir, third, new Renaming(0)).loadAround(0, 0, 100).get(10, TimeUnit.SECONDS);
		assertEquals(400, third.chunkCount());
		assertArrayEquals(changed.columns, third.chunk(5, 5).columns);
	}

	@Test
	void farRegionsAreSavedAndForgotten(@TempDir Path dir) throws Exception {
		ColumnStore store = new ColumnStore();
		store.put(0, 0, varied(0, 0));
		store.put(320, 0, varied(320, 0));
		ColumnFiles files = new ColumnFiles(dir, store, new Renaming(0));
		files.unloadBeyond(0, 0, 2000).get(10, TimeUnit.SECONDS);
		assertNotNull(store.region(0, 0));
		assertNull(store.region(10, 0), "5 km away");
		assertTrue(Files.exists(dir.resolve("r.10.0.vlod")), "saved before it was forgotten");
		files.loadAround(5120, 0, 100).get(10, TimeUnit.SECONDS);
		assertNotNull(store.chunk(320, 0), "and read again on the way back");
	}
}
