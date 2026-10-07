package dev.voidmc.atlas.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.store.SafeFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the player has been: cells within reach, counted once, kept across a restart and a damaged file. */
class VisitedCellsTest {
	@TempDir
	Path dir;

	@Test
	void cellsWithinReachCountOnce() {
		VisitedCells v = new VisitedCells();
		int first = v.visit("minecraft:overworld", 100, -100);
		assertTrue(first > 40, "a circle of cells: " + first);
		assertEquals(0, v.visit("minecraft:overworld", 101, -99), "the same cells again");
		assertTrue(v.visited("minecraft:overworld", 100 + 30, -100));
		assertFalse(v.visited("minecraft:overworld", 100 + 48, -100));
		assertFalse(v.visited("minecraft:the_nether", 100, -100));
		assertEquals(first, v.count("minecraft:overworld"));
	}

	@Test
	void roundTripsThroughItsFile() throws Exception {
		SafeFile files = new SafeFile();
		Path file = this.dir.resolve("visited.dat");
		VisitedCells v = new VisitedCells();
		v.visit("minecraft:overworld", -5000, 12000);
		v.visit("minecraft:the_nether", 3, 3);
		assertTrue(v.dirty());
		v.save(files, file);
		assertFalse(v.dirty());
		VisitedCells back = VisitedCells.load(files, file);
		assertEquals(v.count("minecraft:overworld"), back.count("minecraft:overworld"));
		assertEquals(v.count("minecraft:the_nether"), back.count("minecraft:the_nether"));
		assertTrue(back.visited("minecraft:overworld", -5000, 12000));
	}

	@Test
	void aDamagedFileStartsEmpty() throws Exception {
		Path file = this.dir.resolve("visited.dat");
		Files.writeString(file, "nonsense");
		assertEquals(0, VisitedCells.load(new SafeFile(), file).count("minecraft:overworld"));
	}
}
