package dev.voidmc.atlas.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The region index records views and sizes, survives a save, and a damaged one falls back to its backup. */
class RegionIndexTest {
	@TempDir
	Path dir;

	@Test
	void viewsAndSizes() {
		RegionIndex index = new RegionIndex();
		index.sized("surface", 0, 0, 1000, 50);
		assertEquals(new RegionIndex.Entry("surface", 0, 0, 50, 1000), index.get("surface", 0, 0));
		index.viewed("surface", 0, 0, 80);
		assertEquals(new RegionIndex.Entry("surface", 0, 0, 80, 1000), index.get("surface", 0, 0));
		index.viewed("surface", 0, 0, 70);
		assertEquals(80, index.get("surface", 0, 0).lastViewed(), "an older view does not move the time back");
		index.sized("surface", 0, 0, 1500, 999);
		assertEquals(new RegionIndex.Entry("surface", 0, 0, 80, 1500), index.get("surface", 0, 0), "a save does not count as a view");
		index.viewed("cave_-2", -1, 3, 90);
		assertEquals(0, index.get("cave_-2", -1, 3).bytes());
		assertEquals(2, index.size());
		assertEquals(1500, index.totalBytes());
		assertNull(index.get("surface", 1, 0));
	}

	@Test
	void removeAndVersion() {
		RegionIndex index = new RegionIndex();
		long v0 = index.version();
		index.viewed("surface", 1, 1, 5);
		assertTrue(index.version() > v0);
		long v1 = index.version();
		assertFalse(index.remove("surface", 9, 9));
		assertEquals(v1, index.version());
		assertTrue(index.remove("surface", 1, 1));
		assertEquals(0, index.size());
	}

	@Test
	void encodesOneLinePerRegionInStableOrder() {
		RegionIndex index = new RegionIndex();
		index.put(new RegionIndex.Entry("surface", 2, -1, 1759680000000L, 412345));
		index.put(new RegionIndex.Entry("cave_-2", 0, 0, 7, 9));
		index.put(new RegionIndex.Entry("surface", -3, 7, 8, 10));
		assertEquals("cave_-2 0 0 7 9\nsurface -3 7 8 10\nsurface 2 -1 1759680000000 412345\n", index.encode());
		RegionIndex back = RegionIndex.decode(index.encode());
		assertEquals(index.entries(), back.entries());
	}

	@Test
	void decodeRejectsMalformedLines() {
		assertThrows(IllegalArgumentException.class, () -> RegionIndex.decode("surface 1 2 3\n"));
		assertThrows(IllegalArgumentException.class, () -> RegionIndex.decode("surface x 2 3 4\n"));
		assertThrows(IllegalArgumentException.class, () -> RegionIndex.decode("surface 1 2 3 -4\n"));
		assertEquals(1, RegionIndex.decode("\n  surface 1 2 3 4  \n\n").size());
	}

	@Test
	void layerMustBeOneWord() {
		assertThrows(IllegalArgumentException.class, () -> new RegionIndex().viewed("two words", 0, 0, 1));
	}

	@Test
	void savesAndLoadsThroughSafeFile() throws IOException {
		SafeFile files = new SafeFile();
		Path file = this.dir.resolve("regions.idx");
		RegionIndex index = new RegionIndex();
		for (int i = 0; i < 100; i++) {
			index.sized("surface", i % 10, i / 10, 1000 + i, 5000 + i);
		}
		index.save(files, file);
		assertTrue(Files.readString(file).startsWith("#atlas regions v1 "));
		RegionIndex back = RegionIndex.load(files, file);
		assertEquals(index.entries(), back.entries());
		assertEquals(0, back.version());
	}

	@Test
	void missingIndexIsEmpty() {
		assertEquals(0, RegionIndex.load(new SafeFile(), this.dir.resolve("none.idx")).size());
	}

	@Test
	void damagedIndexFallsBackToBackup() throws IOException {
		List<StorageEvent> events = new ArrayList<>();
		SafeFile files = new SafeFile(events::add);
		Path file = this.dir.resolve("regions.idx");
		RegionIndex index = new RegionIndex();
		index.sized("surface", 0, 0, 10, 1);
		index.save(files, file);
		assertTrue(files.backup(file));
		index.sized("surface", 1, 0, 20, 2);
		index.save(files, file);
		Files.writeString(file, Files.readString(file).replace("surface 1 0", "surface 1 X"));
		RegionIndex back = RegionIndex.load(files, file);
		assertEquals(1, back.size());
		assertEquals(StorageEvent.Kind.RESTORED, events.get(events.size() - 1).kind());
	}

	@Test
	void indexThatFailsToParseIsRejectedEvenWithAGoodChecksum() throws IOException {
		SafeFile files = new SafeFile();
		Path file = this.dir.resolve("regions.idx");
		files.write(file, RegionIndex.KIND, RegionIndex.VERSION, "this is not an index\n");
		assertEquals(0, RegionIndex.load(files, file).size());
		assertTrue(Files.exists(SafeFile.corruptCopy(file, 0)));
	}
}
