package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeepOutTest {
	// Hearth's quest panel, at its tallest: (6, 6) to (180, 69), GUI pixels (here the HUD's unit is the GUI's)
	private static final float[] HEARTH = {6, 6, 180, 69};
	private static final float SW = 960;
	private static final float SH = 540;

	@Test
	void nothingReservedMovesNothing() {
		assertArrayEquals(new float[]{8, 8}, KeepOut.place(8, 8, 120, 22, SW, SH, 8, new float[0], 0));
		assertArrayEquals(new float[]{400, 8}, KeepOut.place(400, 8, 120, 22, SW, SH, 8, HEARTH, 1), 0.001F, "clear of the panel already");
	}

	@Test
	void anEdgeChipAtTheTopLeftStepsOutOfThePanel() {
		// a beam label held at the screen's top-left corner ("Harbour 101 m")
		float[] at = KeepOut.place(8, 8, 110, 22, SW, SH, 8, HEARTH, 1);
		assertFalse(KeepOut.hits(at[0], at[1], 110, 22, HEARTH, 1), "never left over the panel");
		assertTrue(at[1] >= 69 + KeepOut.GAP - 0.01F || at[0] >= 180 + KeepOut.GAP - 0.01F, "below it or beside it");
		assertArrayEquals(new float[]{8, 69 + KeepOut.GAP}, at, 0.001F, "the least move: just below the panel, at the edge still");
	}

	@Test
	void aTopLeftMinimapGoesBelowThePanel() {
		float[] at = KeepOut.place(16, 16, 180, 226, SW, SH, 8, HEARTH, 1);
		assertFalse(KeepOut.hits(at[0], at[1], 180, 226, HEARTH, 1));
		assertArrayEquals(new float[]{16, 69 + KeepOut.GAP}, at, 0.001F);
		// the usual, shorter panel: it moves less
		float[] usual = KeepOut.place(16, 16, 180, 226, SW, SH, 8, new float[]{6, 6, 180, 40}, 1);
		assertArrayEquals(new float[]{16, 40 + KeepOut.GAP}, usual, 0.001F);
	}

	@Test
	void aPieceWithNoRoomBelowGoesBeside() {
		// a tall piece on a short screen: below would leave the screen, so it moves across
		float[] at = KeepOut.place(8, 8, 100, 500, SW, SH, 8, HEARTH, 1);
		assertFalse(KeepOut.hits(at[0], at[1], 100, 500, HEARTH, 1));
		assertArrayEquals(new float[]{180 + KeepOut.GAP, 8}, at, 0.001F);
	}

	@Test
	void twoPanelsSideBySideAreBothCleared() {
		float[] two = {6, 6, 180, 69, 186, 6, 400, 120};
		float[] at = KeepOut.place(150, 8, 120, 22, SW, SH, 8, two, 2);
		assertFalse(KeepOut.hits(at[0], at[1], 120, 22, two, 2), "out of both");
	}
}
