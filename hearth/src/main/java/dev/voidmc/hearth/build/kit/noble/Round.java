package dev.voidmc.hearth.build.kit.noble;

import java.util.ArrayList;
import java.util.List;

/**
 * Round plans the way a builder lays them out in blocks: hand-tuned discs whose outline reads as a circle from every side
 * (no lone corner blocks, no flat facets longer than the circle allows), for towers, turrets, drums and round halls. A
 * disc is centred on a cell and has an odd diameter; its outline is closed face to face, so a ring of wall keeps light
 * and mobs where they belong.
 */
public final class Round {
	private Round() {
	}

	/**
	 * Half-widths of each row of a disc by its distance from the centre row, for diameters 3 to 17 (index d / 2). Row k of
	 * a disc of diameter d spans {@code [-HALF[d/2][k], +HALF[d/2][k]]}.
	 */
	private static final int[][] HALF = {
		{0},
		{1, 0},
		{2, 2, 1},
		{3, 3, 2, 1},
		{4, 4, 3, 3, 1},
		{5, 5, 4, 4, 3, 1},
		{6, 6, 5, 5, 4, 3, 2},
		{7, 7, 7, 6, 6, 5, 4, 2},
		{8, 8, 8, 7, 7, 6, 5, 4, 2},
	};

	/** The half-width of row {@code dz} (distance from the centre row) of a disc of odd diameter {@code diam}; -1 outside. */
	public static int half(int diam, int dz) {
		int r = diam / 2;
		int a = Math.abs(dz);
		if (a > r) {
			return -1;
		}
		if (r < HALF.length) {
			return HALF[r][a];
		}
		return (int) Math.floor(Math.sqrt((r + 0.45) * (r + 0.45) - a * a));
	}

	/** Whether (x, z) lies in the disc of diameter {@code diam} centred on (cx, cz). */
	public static boolean in(int cx, int cz, int diam, int x, int z) {
		int h = half(diam, z - cz);
		return h >= 0 && Math.abs(x - cx) <= h;
	}

	/** The cells of a filled disc. */
	public static List<int[]> disc(int cx, int cz, int diam) {
		List<int[]> out = new ArrayList<>();
		int r = diam / 2;
		for (int dz = -r; dz <= r; dz++) {
			int h = half(diam, dz);
			for (int dx = -h; dx <= h; dx++) {
				out.add(new int[]{cx + dx, cz + dz});
			}
		}
		return out;
	}

	/** Whether a cell of the disc touches the outside face to face (the outline of a one-block wall). */
	public static boolean edge(int cx, int cz, int diam, int x, int z) {
		return in(cx, cz, diam, x, z) && (!in(cx, cz, diam, x + 1, z) || !in(cx, cz, diam, x - 1, z) || !in(cx, cz, diam, x, z + 1)
			|| !in(cx, cz, diam, x, z - 1));
	}

	/** The wall of a round tower {@code thick} blocks thick: the disc less the disc {@code 2 * thick} narrower. */
	public static List<int[]> ring(int cx, int cz, int diam, int thick) {
		List<int[]> out = new ArrayList<>();
		int inner = diam - 2 * thick;
		for (int[] c : disc(cx, cz, diam)) {
			if (thick <= 1 ? edge(cx, cz, diam, c[0], c[1]) : inner < 1 || !in(cx, cz, inner, c[0], c[1]) || edge(cx, cz, diam, c[0], c[1])) {
				out.add(c);
			}
		}
		return out;
	}

	/** The cells inside a ring wall of thickness {@code thick} (the floor of a round room). */
	public static List<int[]> inside(int cx, int cz, int diam, int thick) {
		List<int[]> out = new ArrayList<>();
		java.util.Set<Long> wall = new java.util.HashSet<>();
		for (int[] c : ring(cx, cz, diam, thick)) {
			wall.add(key(c[0], c[1]));
		}
		for (int[] c : disc(cx, cz, diam)) {
			if (!wall.contains(key(c[0], c[1]))) {
				out.add(c);
			}
		}
		return out;
	}

	/** The cells just outside a disc, face to face with it (where a corbel or a talus goes). */
	public static List<int[]> around(int cx, int cz, int diam) {
		List<int[]> out = new ArrayList<>();
		int r = diam / 2 + 1;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!in(cx, cz, diam, x, z) && (in(cx, cz, diam, x + 1, z) || in(cx, cz, diam, x - 1, z) || in(cx, cz, diam, x, z + 1)
					|| in(cx, cz, diam, x, z - 1))) {
					out.add(new int[]{x, z});
				}
			}
		}
		return out;
	}

	/** The local direction from the centre to a cell of the outline: the way the wall there faces. */
	public static net.minecraft.core.Direction outward(int cx, int cz, int x, int z) {
		return dev.voidmc.hearth.build.draft.Dirs.nearest(x - cx, z - cz);
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}
}
