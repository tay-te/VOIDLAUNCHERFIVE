package dev.voidmc.fray.client.combat.hud;

import dev.voidmc.fray.api.StatusType;

/**
 * 7 × 7 pixel icons for Fray's statuses (Figma H3: "7×7 icon at 2 px"), drawn as fills; '#' is the status colour,
 * '+' a lighter tone. Vanilla effects use their own sprites.
 */
public final class Icons {
	private Icons() {
	}

	private static final String[][] ICONS = new String[StatusType.VALUES.length][];

	static {
		set(StatusType.BLEED, "...#...", "..###..", ".#####.", ".##+##.", "#######", ".#####.", "..###..");
		set(StatusType.BURN, "...#...", "..##...", "..###..", ".##+##.", ".#+++#.", "##+++##", ".#####.");
		set(StatusType.CHILL, "#..#..#", ".#.#.#.", "..###..", "###+###", "..###..", ".#.#.#.", "#..#..#");
		set(StatusType.SHOCK, "....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##....");
		set(StatusType.POISON, "..###..", "...#...", "..###..", ".#####.", "##+####", "###+###", ".#####.");
		set(StatusType.HEX, ".#####.", "#.....#", "#.###.#", "#.#+#.#", "#.###.#", "#.....#", ".#####.");
		set(StatusType.BLIGHT, ".#####.", "#######", "#.###.#", "#######", ".#####.", ".#.#.#.", ".......");
		set(StatusType.FROZEN, "#######", "#+#+#+#", "#######", "#+#+#+#", "#######", "#+#+#+#", "#######");
		set(StatusType.STAGGER, "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#");
		set(StatusType.DAZE, ".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#.####.", ".......");
		set(StatusType.EXPOSED, "#######", "#..#..#", "#.#...#", "#..#..#", ".#..#.#", "..#.#..", "...#...");
		set(StatusType.ROOTED, "...#...", "...#...", ".#.#.#.", "..###..", ".#.#.#.", "#..#..#", "...#...");
		set(StatusType.SILENCED, "..###..", ".#...#.", "#..#..#", "#.#.#.#", "#..#..#", ".#...#.", "..###..");
		set(StatusType.WET, ".#...#.", ".#...#.", "###.###", "###.###", ".#...#.", "...#...", "..###..");
		set(StatusType.OILED, "...#...", "..###..", ".#####.", "##+####", "#######", "#######", ".#####.");
		set(StatusType.MARKED, "..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###..");
	}

	private static void set(StatusType s, String... rows) {
		ICONS[s.ordinal()] = rows;
	}

	/** Draws a status icon {@code size} design px across (7 cells) with its top-left at (x, y). */
	public static void status(Paint p, StatusType s, float x, float y, float size, float alpha) {
		String[] rows = ICONS[s.ordinal()];
		if (rows == null) {
			return;
		}
		float c = size / 7F;
		int main = Paint.alpha(s.colour, alpha);
		int light = Paint.alpha(Paint.lerpColour(0.55F, s.colour, 0xFFFFFFFF), alpha);
		for (int r = 0; r < 7; r++) {
			String row = rows[r];
			int run = -1;
			char runCh = '.';
			for (int i = 0; i <= 7; i++) {
				char ch = i < 7 ? row.charAt(i) : '.';
				if (ch != runCh) {
					if (runCh != '.') {
						p.rect(x + run * c, y + r * c, (i - run) * c, c, runCh == '+' ? light : main);
					}
					run = i;
					runCh = ch;
				}
			}
		}
	}

	/** The Whirlwind/Earthshaker spiral (H10). */
	public static void spiral(Paint p, float cx, float cy, float size, int col) {
		p.ring(cx, cy, size / 2, size / 12, col, 0, 1, 28, false);
		p.ring(cx, cy, size / 3.2F, size / 12, col, 0.15F, 1, 22, false);
		p.ring(cx, cy, size / 6, size / 12, col, 0.3F, 1, 14, false);
		p.disc(cx, cy, size / 8, col);
	}

	/** A small shield (guard broken, affix chips). */
	public static void shield(Paint p, float x, float y, float size, int col) {
		String[] rows = {"#######", "#+++++#", "#+++++#", "#+++++#", ".#+++#.", "..#+#..", "...#..."};
		float c = size / 7F;
		for (int r = 0; r < 7; r++) {
			for (int i = 0; i < 7; i++) {
				char ch = rows[r].charAt(i);
				if (ch != '.') {
					p.rect(x + i * c, y + r * c, c, c, ch == '#' ? col : Paint.alpha(col, 0.45F));
				}
			}
		}
	}
}
