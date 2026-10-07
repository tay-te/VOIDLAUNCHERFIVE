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
	/** A village (the design's glyph/village). */
	VILLAGE(rows("00100", "01110", "11111", "01010", "11111"), 0),
	/** A dungeon, a vault. */
	VAULT(rows("11111", "10001", "10101", "10001", "11111"), 0),
	/** Somewhere seen but not yet visited: a "?". */
	UNKNOWN(rows("01110", "00001", "00110", "00000", "00100"), 0),
	/** A peak. */
	PEAK(rows("00100", "01110", "01110", "11111", "11111"), 0),
	/** A temple (desert, jungle). */
	TEMPLE(rows("01110", "11111", "01010", "01010", "11111"), 0),
	/** An ocean monument. */
	MONUMENT(rows("01110", "10101", "11111", "10101", "11111"), 0),
	/** A woodland mansion. */
	MANSION(rows("01010", "11111", "10101", "11111", "11011"), 0),
	/** An ancient city. */
	ANCIENT_CITY(rows("11111", "10001", "10001", "11011", "11011"), 0),
	/** A lake. */
	LAKE(rows("00000", "01100", "10011", "01100", "10011"), 0),
	/** A waterfall. */
	WATERFALL(rows("11111", "00100", "00100", "00100", "01110"), 0),
	/** A ruined portal. */
	PORTAL(rows("11110", "10010", "10000", "10010", "11010"), 0),
	/** A shipwreck. */
	WRECK(rows("00100", "00110", "00100", "11111", "01110"), 0),
	/** A cave mouth, a ravine. */
	CAVE(rows("01110", "11111", "11011", "10001", "10001"), 0),
	/** Trail ruins. */
	TRAIL(rows("01110", "00100", "01110", "11111", "01110"), 0),
	/** A mineshaft. */
	MINESHAFT(rows("11111", "01010", "01010", "01010", "01010"), 0),
	/** Lush caves. */
	LUSH(rows("00011", "00111", "01110", "11100", "10000"), 0),
	/** The deep dark. */
	SCULK(rows("10101", "01110", "11011", "01110", "10101"), 0),
	/** A tracked place. */
	TRACK(rows("00100", "00000", "10101", "00000", "00100"), 0),
	/** A geode. */
	GEM(rows("00100", "01010", "10101", "01010", "00100"), 0),
	/** A pillager outpost, a stronghold, a fortress: a fortified tower. */
	KEEP(rows("10101", "11111", "01110", "01110", "11111"), 0),
	/** Four squares: a plain, small map (the Minimal preset). */
	GRID(rows("11011", "11011", "00000", "11011", "11011"), 0),
	/** A shield (the PvP-safe preset). */
	SHIELD(rows("11111", "11111", "11111", "01110", "00100"), 0),
	/** A quest step: an exclamation mark. */
	QUEST(rows("00100", "00100", "00100", "00000", "00100"), 0),
	/** A town: roofs behind a wall, with a gate. */
	TOWN(rows("01010", "11111", "00000", "11111", "11011"), rows("00000", "00000", "00000", "00000", "00100"));

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
