package dev.voidmc.atlas.api;

/**
 * The 5 x 5 pixel glyphs markers are drawn with (the design's {@code glyph/*} set). Each is a mask of 25 bits, row by
 * row from the top-left; {@link #accent} marks the cells drawn brighter (a door, a gate).
 */
public enum MarkerGlyph {
	/** A waypoint pin. */
	PIN(rows("01110", "11011", "01110", "00100", "00100"), 0),
	/** A settlement: a house with a door. */
	HOUSE(rows("00100", "01110", "11111", "01010", "01110"), rows("00000", "00000", "00000", "00000", "00110")),
	/** A landmark or building: a tower with a gate. */
	TOWER(rows("10101", "11111", "01110", "01010", "01110"), rows("00000", "00000", "00000", "00010", "01110")),
	/** Something underground: a geode. */
	REALM(rows("00100", "01110", "11011", "01110", "00100"), 0),
	/** Where the player died. */
	CROSS(rows("10001", "01010", "00100", "01010", "10001"), 0),
	/** A camp, a well, a small sight. */
	DOT(rows("00000", "01110", "01110", "01110", "00000"), 0),
	/** A village. */
	VILLAGE(rows("01010", "11111", "01010", "11111", "01010"), 0),
	/** A ruin, a dungeon, a vault. */
	VAULT(rows("11111", "10001", "10101", "10001", "11111"), rows("00000", "00000", "00100", "00000", "00000")),
	/** Somewhere seen but not yet visited. */
	UNKNOWN(rows("01110", "10001", "00110", "00000", "00100"), 0),
	/** A peak. */
	PEAK(rows("00100", "01110", "01110", "11011", "11111"), rows("00100", "00000", "00000", "00000", "00000")),
	/** A lake, a river, a waterfall. */
	WATER(rows("00000", "01001", "10110", "00000", "11011"), 0),
	/** A cave mouth or a ravine: a dark opening. */
	CAVE(rows("01110", "11111", "11011", "10001", "10001"), 0),
	/** A ruined portal. */
	PORTAL(rows("11111", "10101", "10001", "10101", "11111"), 0),
	/** A shipwreck. */
	SHIP(rows("00100", "00110", "00100", "11111", "01110"), 0),
	/** A geode, a gem. */
	GEM(rows("01110", "11111", "10101", "01110", "00100"), rows("00000", "00100", "00000", "00100", "00000"));

	public final int mask;
	public final int accent;

	MarkerGlyph(int mask, int accent) {
		this.mask = mask;
		this.accent = accent;
	}

	public boolean cell(int x, int y) {
		return (this.mask >> (y * 5 + x) & 1) != 0;
	}

	public boolean accent(int x, int y) {
		return (this.accent >> (y * 5 + x) & 1) != 0;
	}

	private static int rows(String... rows) {
		int mask = 0;
		for (int y = 0; y < 5; y++) {
			for (int x = 0; x < 5; x++) {
				if (rows[y].charAt(x) == '1') {
					mask |= 1 << (y * 5 + x);
				}
			}
		}
		return mask;
	}
}
