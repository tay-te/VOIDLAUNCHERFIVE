package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;

/**
 * Ground left as it lies, made good where folk and beasts could not cross it: water filled to its bank, and ditches and
 * hollows made up until no cell lies more than a block below its neighbour (nothing is cut away, so the land keeps its
 * swells). Pastures, orchards and yards use it before they plant.
 */
public final class Land {
	private Land() {
	}

	/**
	 * Makes a rectangle walkable by filling only: water is filled to its surface, then every cell is raised until it is
	 * at most a block below each neighbour. Returns the new standing level per cell, indexed {@code [z - z0][x - x0]}.
	 */
	public static int[][] mendFill(Draft d, int x0, int z0, int x1, int z1) {
		int w = x1 - x0 + 1;
		int h = z1 - z0 + 1;
		int[][] t = new int[h][w];
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				t[z][x] = Terraces.effective(d, x0 + x, z0 + z);
			}
		}
		// raise-only relaxation: a cell is never more than one below a neighbour
		boolean changed = true;
		for (int pass = 0; changed && pass < 64; pass++) {
			changed = false;
			for (int z = 0; z < h; z++) {
				for (int x = 0; x < w; x++) {
					int m = t[z][x];
					if (x > 0) {
						m = Math.max(m, t[z][x - 1] - 1);
					}
					if (x < w - 1) {
						m = Math.max(m, t[z][x + 1] - 1);
					}
					if (z > 0) {
						m = Math.max(m, t[z - 1][x] - 1);
					}
					if (z < h - 1) {
						m = Math.max(m, t[z + 1][x] - 1);
					}
					if (m != t[z][x]) {
						t[z][x] = m;
						changed = true;
					}
				}
			}
		}
		Mat fill = d.mat(Role.FILL);
		Mat ground = d.mat(Role.GROUND);
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = 0; z < h; z++) {
				for (int x = 0; x < w; x++) {
					int lx = x0 + x;
					int lz = z0 + z;
					int g = d.site().ground(lx, lz);
					int lv = t[z][x];
					boolean water = d.site().isWater(lx, lz);
					if (lv <= g && !water) {
						continue;
					}
					// a bank of stone where filled ground meets water still standing outside the plot
					boolean edge = lx == 0 || lz == 0 || lx == d.frontage() - 1 || lz == d.depth() - 1;
					for (int y = g - 1; y < lv - 1; y++) {
						d.set(lx, y, lz, water && edge ? stone.full() : fill.full());
					}
					d.set(lx, lv - 1, lz, water && edge ? stone.full() : ground.full());
					if (water) {
						int wy = d.site().water(lx, lz);
						for (int y = lv; y < wy; y++) {
							d.set(lx, y, lz, stone.full());
						}
						d.drain(lx, lz);
					}
				}
			}
		}
		return t;
	}

	/**
	 * A path over cells laid on the ground as drawn (it climbs and falls with the land, a stair where it rises a block
	 * from one cell to the next), skipping cells where something stands.
	 */
	public static void path(Draft d, java.util.List<int[]> cells) {
		Mat path = d.mat(Role.PATH);
		Mat paving = d.mat(Role.PAVING);
		int prev = Integer.MIN_VALUE;
		int[] prevCell = null;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				int y = Spots.level(d, c[0], c[1]);
				if (!Spots.free(d, c[0], y, c[1]) || !dev.voidmc.hearth.build.kit.Garden.groundLike(d, c[0], y - 1, c[1])) {
					prev = Integer.MIN_VALUE;
					continue;
				}
				if (prev != Integer.MIN_VALUE && y == prev + 1 && prevCell != null && paving.has(dev.voidmc.hearth.build.style.Form.STAIRS)) {
					// a step up: the stair sits in this cell's ground row, facing back down the way
					net.minecraft.core.Direction up = dev.voidmc.hearth.build.draft.Dirs.of(c[0] - prevCell[0], c[1] - prevCell[1]);
					if (up != null) {
						d.set(c[0], y - 1, c[1], path.full());
					}
				} else {
					d.set(c[0], y - 1, c[1], path.full());
				}
				prev = y;
				prevCell = c;
			}
		}
	}

	/** Whether there is water anywhere under a rectangle. */
	public static boolean wet(Draft d, int x0, int z0, int x1, int z1) {
		return d.site().anyWater(x0, z0, x1, z1);
	}

	/** {@link Site#NONE} passthrough for callers. */
	public static final int NONE = Site.NONE;
}
