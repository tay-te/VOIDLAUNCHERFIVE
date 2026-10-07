package dev.voidmc.hearth.build.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.build.archetype.defense.TownWall;
import org.junit.jupiter.api.Test;

/** The town wall's (and the palisade's) coping steps in runs of three to five on a slope, never column by column. */
class WallCrestTest {
	@Test
	void stepsInRunsOnASlope() {
		for (int[] levels : new int[][]{ramp(24, 1), ramp(24, 2), ramp(30, 3), {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7}}) {
			int[] q = TownWall.crest(levels, 2);
			int run = 1;
			for (int i = 1; i <= q.length; i++) {
				if (i < q.length && q[i] == q[i - 1]) {
					run++;
					continue;
				}
				// every level but the last holds at least three steps (two runs may meet at one level, so up to ten)
				assertTrue(run <= 10, "run of " + run);
				assertTrue(run >= 3 || i == q.length, "run of " + run + " ending at " + i);
				run = 1;
			}
			for (int i = 0; i < q.length; i++) {
				assertTrue(q[i] >= levels[i] && q[i] - levels[i] <= 2, "coping " + q[i] + " over walk " + levels[i]);
			}
		}
	}

	@Test
	void flatGroundIsOneLevel() {
		int[] q = TownWall.crest(new int[20], 2);
		for (int v : q) {
			assertEquals(0, v);
		}
	}

	/** Levels rising a block every {@code every} steps. */
	private static int[] ramp(int n, int every) {
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = i / every;
		}
		return out;
	}
}
