package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.under.DepthWindow;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** A world 704 blocks high (VOID Expanse's Earth: y -64 to 639, peaks near 600): nothing in Atlas assumes 384. */
class TallWorldTest {
	private static final int MIN_Y = -64;
	private static final int MAX_Y = 639;

	@Test
	void heightsFromTheBottomToTheTopAreKept() throws IOException {
		RegionRaw raw = new RegionRaw(0, 0);
		raw.set(1, 1, MAX_Y, 1, 0, 1, RegionRaw.KNOWN);
		raw.set(2, 2, MIN_Y, 1, 0, 1, RegionRaw.KNOWN);
		raw.set(3, 3, 600, 1, 255, 1, RegionRaw.KNOWN);
		RegionRaw back = RegionRaw.decode(0, 0, raw.encode());
		assertEquals(MAX_Y, back.height[RegionRaw.index(1, 1)]);
		assertEquals(MIN_Y, back.height[RegionRaw.index(2, 2)]);
		assertEquals(600, back.height[RegionRaw.index(3, 3)]);
		assertEquals(255, back.depth(RegionRaw.index(3, 3)), "the deepest water a column records");
	}

	@Test
	void theDepthScrubberReachesTheWholeHeight() {
		// at cave depth it is the vanilla window
		assertArrayEquals(new int[]{-64, 128}, DepthWindow.of(MIN_Y, MAX_Y, -24, -64, 192));
		// a cave in a 600-block mountain: the window goes up with the slice, inside the world
		int[] high = DepthWindow.of(MIN_Y, MAX_Y, 560, -64, 192);
		assertTrue(high[0] <= 560 && 560 < high[1], "the slice is in the window");
		assertEquals(640, high[1], "up to the top of the world, no further");
		// a vanilla overworld's top is 319
		assertEquals(320, DepthWindow.of(-64, 319, 300, -64, 192)[1]);
		// the Nether's floor is 0
		assertEquals(0, DepthWindow.of(0, 255, 8, 64, 192)[0]);
		// it stays put while the slice moves inside it
		assertArrayEquals(new int[]{200, 392}, DepthWindow.of(MIN_Y, MAX_Y, 300, 200, 192));
	}

	@Test
	void elevationBandsAndPeaksTakeTallLand() {
		// the classic look's bands stop at their last one; nothing overflows at 600
		assertEquals(Palette.MAX_BANDS, Palette.band(600));
		assertEquals(Palette.MAX_BANDS, Palette.band(MAX_Y));
		// a 600-block cone on a plain at the sea: its prominence is its height over the plain
		int g = 3 * Coords.REGION_SIZE / 4;
		short[] grid = new short[g * g];
		int c = g / 2;
		for (int z = 0; z < g; z++) {
			for (int x = 0; x < g; x++) {
				double d = Math.hypot(x - c, z - c);
				grid[z * g + x] = (short) Math.max(63, 600 - d * 4);
			}
		}
		int prominence = dev.voidmc.atlas.core.landmarks.RegionAnalysisAccess.prominence(grid, c * g + c);
		assertTrue(prominence >= 500, "prominence " + prominence);
	}
}
