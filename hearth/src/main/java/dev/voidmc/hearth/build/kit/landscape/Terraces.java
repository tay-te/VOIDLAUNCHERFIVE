package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Laying out open ground on a slope as a few level terraces from the street back, each a band of rows, rather than one
 * platform cut deep into the hill or banked high above it: the front terrace meets the street, the ones behind follow
 * the ground up (or down) by at most three blocks a step, each held by a retaining wall of the foundation stone with a
 * coping on top (a parapet where the wall stands three or more), and a flight of steps where a path climbs from one to
 * the next.
 */
public final class Terraces {
	private Terraces() {
	}

	/**
	 * Levels per row (local z, index 0 = the front row) over the plot: the front band at the street (0), further bands
	 * where the ground behind rises or falls by two or more, at least {@code minBand} rows deep each.
	 */
	public static int[] plan(Draft d, int x0, int x1, int minBand, int maxStep) {
		int dp = d.depth();
		int[] rowGround = new int[dp];
		for (int z = 0; z < dp; z++) {
			rowGround[z] = d.site().meanGround(x0, z, x1, z);
		}
		int[] level = new int[dp];
		int start = 0;
		int current = 0;
		while (start < dp) {
			int end = Math.min(dp - 1, start + minBand - 1);
			// extend the band while the ground stays within a block of the band's level
			while (end + 1 < dp && Math.abs(rowGround[end + 1] - current) <= 1) {
				end++;
			}
			if (dp - 1 - end < minBand) {
				end = dp - 1;
			}
			for (int z = start; z <= end; z++) {
				level[z] = current;
			}
			if (end + 1 < dp) {
				int next = rowGround[Math.min(dp - 1, end + 1 + minBand / 2)];
				current = Math.clamp(next, current - maxStep, current + maxStep);
				if (current == level[end]) {
					current = level[end];
				}
			}
			start = end + 1;
		}
		return level;
	}

	/** Whether a plan has more than one level. */
	public static boolean stepped(int[] level) {
		for (int l : level) {
			if (l != level[0]) {
				return true;
			}
		}
		return false;
	}

	/** Levels every band of the plan (water embanked), retaining walls with a coping where a band stands above the next. */
	public static void build(Draft d, int x0, int x1, int[] level) {
		int dp = level.length;
		int start = 0;
		while (start < dp) {
			int end = start;
			while (end + 1 < dp && level[end + 1] == level[start]) {
				end++;
			}
			Groundwork.level(d, x0, start, x1, end, level[start], true);
			start = end + 1;
		}
		Mat found = d.mat(Role.FOUNDATION);
		Mat cap = Manner.masonry(d);
		for (int z = 1; z < dp; z++) {
			if (level[z] == level[z - 1]) {
				continue;
			}
			int hi = Math.max(level[z], level[z - 1]);
			int lo = Math.min(level[z], level[z - 1]);
			int wz = level[z] > level[z - 1] ? z : z - 1;
			try (var s = d.stage(StageKind.EARTHWORK)) {
				for (int x = x0; x <= x1; x++) {
					int g = d.site().ground(x, wz);
					for (int y = Math.min(g, lo) - 1; y < hi; y++) {
						d.set(x, y, wz, found.full());
					}
				}
			}
			// a coping course on the wall's top edge, so nobody walks off the terrace unawares; over a drop of three or more
			// a parapet (a wall of the masonry) instead, which nobody steps up onto
			boolean parapet = hi - lo >= 3 && cap.has(Form.WALL);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, hi, wz, parapet ? cap.wall() : cap.has(Form.SLAB) ? cap.slab(SlabType.BOTTOM) : cap.full());
				}
			}
		}
	}

	/**
	 * A flight of steps at column x climbing from each terrace to the next (in the lower band, against the wall, with
	 * the coping cut away above it), and the path's width; returns nothing. Call after {@link #build}.
	 */
	public static void steps(Draft d, int x, int width, int[] level) {
		int dp = level.length;
		Mat stone = Manner.masonry(d);
		Mat found = d.mat(Role.FOUNDATION);
		for (int z = 1; z < dp; z++) {
			if (level[z] == level[z - 1]) {
				continue;
			}
			boolean up = level[z] > level[z - 1];
			int lo = Math.min(level[z], level[z - 1]);
			int hi = Math.max(level[z], level[z - 1]);
			int wall = up ? z : z - 1;
			// the flight climbs away from the low terrace into the wall's row and beyond on the high side
			for (int w = 0; w < width; w++) {
				int cx = x + w;
				for (int k = 0; k < hi - lo; k++) {
					// steps in the low band leading up to the wall row, the last one in the wall row itself
					int sz = up ? wall - (hi - lo - 1) + k : wall + (hi - lo - 1) - k;
					int sy = lo + k;
					if (sz < 0 || sz >= dp) {
						continue;
					}
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(cx, sy, sz, stone.has(Form.STAIRS) ? stone.stairs(up ? Dirs.BACK : Dirs.FRONT, Half.BOTTOM) : stone.full());
						for (int yy = Math.min(d.site().ground(cx, sz), sy) - 1; yy < sy; yy++) {
							d.set(cx, yy, sz, found.full());
						}
						for (int yy = sy + 1; yy <= hi + 2; yy++) {
							d.set(cx, yy, sz, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
				// the coping is cut away where the flight arrives
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(cx, hi, wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
	}
}
