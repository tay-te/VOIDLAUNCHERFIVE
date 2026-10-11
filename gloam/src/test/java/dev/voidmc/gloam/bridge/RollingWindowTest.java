package dev.voidmc.gloam.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RollingWindowTest {
	/** Each cell's texel names the cell, so the tile can be checked texel by texel. */
	private static final RollingWindow.Cells NAMED = (cx, cz) -> name(cx, cz) & 0xFFFFFFFFL | (long) ~name(cx, cz) << 32;

	private static int name(int cx, int cz) {
		return (cx & 0xFFFF) | (cz & 0xFFFF) << 16;
	}

	@Test
	void aWalkKeepsEveryCellOfTheWindowInItsTexel() {
		for (int size : new int[]{256, 128}) {
			RollingWindow w = new RollingWindow(size, Contract.F0, Contract.F1);
			byte[] bytes = new byte[Contract.WIDTH * Contract.HEIGHT * 4];
			List<String> marks = new ArrayList<>();
			LandTiles.Dirty dirty = (tile, column, line) -> marks.add(tile + ":" + column + ":" + line);
			int cx = -3, cz = 1_000_000;
			TileStrip.write(bytes, w.clear(0), dirty);
			for (int z : w.restart(cx, cz)) {
				TileStrip.write(bytes, w.row(z, NAMED, 0), dirty);
			}
			check(w, bytes);
			Random random = new Random(size);
			for (int step = 0; step < 400; step++) {
				cx += random.nextInt(5) - 2;
				cz += random.nextInt(5) - 2;
				for (int line; (line = w.step(cx, cz)) != Integer.MIN_VALUE; ) {
					TileStrip s = w.line(w.lastColumn, line, NAMED, 0);
					assertEquals(size, s.length());
					TileStrip.write(bytes, s, dirty);
				}
				assertEquals(0, w.distance(cx, cz));
				check(w, bytes);
			}
			assertTrue(marks.contains(Contract.F0 + ":false:-1"));
		}
	}

	@Test
	void restartGivesTheRowsNearestFirst() {
		RollingWindow w = new RollingWindow(128, Contract.U, -1);
		List<Integer> rows = w.restart(10, -20);
		assertEquals(128, rows.size());
		assertEquals(-20, rows.get(0));
		for (int i = 1; i < rows.size(); i++) {
			assertTrue(Math.abs(rows.get(i) + 20) >= Math.abs(rows.get(i - 1) + 20));
		}
		assertTrue(w.contains(10 - 63, -20 + 64));
		assertFalse(w.contains(10 - 64, -20));
		assertFalse(w.contains(10, -20 + 65));
	}

	private static void check(RollingWindow w, byte[] bytes) {
		for (int z = w.centreZ() - w.below; z <= w.centreZ() + w.above; z++) {
			for (int x = w.centreX() - w.below; x <= w.centreX() + w.above; x++) {
				assertEquals(name(x, z), TileStrip.read(bytes, w.tileA, x, z), () -> "tile A");
				assertEquals(~name(x, z), TileStrip.read(bytes, w.tileB, x, z), () -> "tile B");
			}
		}
	}
}
