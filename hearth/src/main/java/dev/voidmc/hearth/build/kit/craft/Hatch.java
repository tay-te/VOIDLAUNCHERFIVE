package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The way up into a loft where no flight fits: a ladder against a wall of the room below, through a hatch in the loft's
 * floor, at a place where the loft has a man's height over the hatch and a board to step off onto beside it. Every
 * trade with a loft over its shop (the joiner's boards, the baker's flour, the netmaker's nets, the store's sacks) gets
 * its ladder this way, so the loft its gable door and hoist promise can be walked to. The ladder's foot is kept free on
 * the floor below before anything else is placed round it.
 */
public final class Hatch {
	private Hatch() {
	}

	/**
	 * A ladder from storey {@code k - 1} up into storey {@code k} against the first of {@code walls} (LEFT, RIGHT, FRONT,
	 * BACK of the inside of the room below) where it fits, its foot reserved on {@code below} (the floor plan of storey
	 * k - 1, if not null). Cells along each wall are tried from the middle out. Returns {x, z} of the ladder, or null.
	 */
	public static int[] ladder(Draft d, Hall hall, int k, Floorplan below, Direction... walls) {
		if (k < 1 || k >= hall.storeys()) {
			return null;
		}
		int[] r = hall.inner(k - 1);
		int y0 = hall.floorY(k - 1);
		int fy = hall.floorY(k) - 1;
		// a knee loft under the roof the roof opened to the room below (no way up was known then) is boarded over again,
		// so there is a floor to climb into; if no ladder fits after all it is opened again
		int[] up = hall.inner(k);
		boolean knee = k == hall.storeys() - 1 && hall.body.height(k) <= 2 && d.isEmpty((up[0] + up[2]) / 2, fy, (up[1] + up[3]) / 2);
		if (knee) {
			Floors.boards(d, up[0] + 1, up[1] + 1, up[2] - 1, up[3] - 1, fy);
		}
		for (Direction wall : walls) {
			for (int[] c : along(r, wall)) {
				if (fits(d, c[0], c[1], wall, y0, fy, below)) {
					if (below != null) {
						below.keep(c[0], c[1]);
					}
					place(d, c[0], c[1], wall, y0, fy);
					return c;
				}
			}
		}
		if (knee) {
			hall.openLoft(k);
		}
		return null;
	}

	/**
	 * Keeps the loft's hatch free on the loft's floor plan: the cell over the ladder and the boards round it, where one
	 * steps off, so nothing furnishing the loft is set on the hatch.
	 */
	public static void keep(Floorplan up, int[] ladder) {
		up.keepArea(ladder[0], ladder[1], ladder[0], ladder[1]);
		for (Direction s : Dirs.HORIZONTAL) {
			up.keep(ladder[0] + Dirs.dx(s), ladder[1] + Dirs.dz(s));
		}
	}

	/** The inside cells against {@code wall} of the rectangle {x0, z0, x1, z1}, from the middle out. */
	private static List<int[]> along(int[] r, Direction wall) {
		List<int[]> out = new ArrayList<>();
		boolean alongX = wall == Dirs.FRONT || wall == Dirs.BACK;
		int a0 = alongX ? r[0] : r[1];
		int a1 = alongX ? r[2] : r[3];
		int fixed = switch (wall) {
			case SOUTH -> r[1];
			case NORTH -> r[3];
			case WEST -> r[0];
			default -> r[2];
		};
		int mid = (a0 + a1) / 2;
		for (int o = 0; o <= a1 - a0; o++) {
			for (int a : o == 0 ? new int[]{mid} : new int[]{mid + o, mid - o}) {
				if (a < a0 || a > a1) {
					continue;
				}
				out.add(alongX ? new int[]{a, fixed} : new int[]{fixed, a});
			}
		}
		return out;
	}

	private static boolean fits(Draft d, int x, int z, Direction wall, int y0, int fy, Floorplan below) {
		if (below != null && !below.free(x, z)) {
			return false;
		}
		// the wall behind carries the ladder all the way up; the shaft below the floor is clear
		int wx = x + Dirs.dx(wall);
		int wz = z + Dirs.dz(wall);
		for (int y = y0; y <= fy; y++) {
			if (!d.isSolid(wx, y, wz)) {
				return false;
			}
		}
		for (int y = y0; y < fy; y++) {
			if (!d.isEmpty(x, y, z)) {
				return false;
			}
		}
		if (!d.isSolid(x, fy, z) || !d.isEmpty(x, fy + 1, z) || !d.isEmpty(x, fy + 2, z)) {
			return false;
		}
		// a board to step off onto beside the hatch (along the wall or out from it), with headroom over it
		for (Direction s : new Direction[]{Dirs.right(wall), Dirs.right(wall).getOpposite(), wall.getOpposite()}) {
			int sx = x + Dirs.dx(s);
			int sz = z + Dirs.dz(s);
			if (d.isSolid(sx, fy, sz) && d.isEmpty(sx, fy + 1, sz) && d.isEmpty(sx, fy + 2, sz)) {
				return true;
			}
		}
		return false;
	}

	private static void place(Draft d, int x, int z, Direction wall, int y0, int fy) {
		try (var s = d.stage(StageKind.FLOORS)) {
			// the hatch: the board taken up, the head room over it opened
			d.set(x, fy, z, Blocks.AIR.defaultBlockState());
			d.set(x, fy + 1, z, Blocks.AIR.defaultBlockState());
			d.set(x, fy + 2, z, Blocks.AIR.defaultBlockState());
		}
		Stairs.ladder(d, x, z, y0, fy, wall.getOpposite());
	}

	/**
	 * A loading door in a wall over a drop (a loft's gable, a warehouse's upper floors): a half door, the lower leaf a rail
	 * of the culture's wooden fence at the sill, the upper leaf an open shutter, so goods swing in on the hoist and
	 * nobody walks out into the air. {@code out} is the wall's outward face.
	 */
	public static void hoistDoor(Draft d, int x, int y, int z, Direction out) {
		Mat shutter = d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER) : d.mat(Role.DOOR).has(Form.TRAPDOOR) ? d.mat(Role.DOOR) : null;
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, Hall.woodFence(d).fence());
			if (shutter != null) {
				d.set(x, y + 1, z, shutter.trapdoor(out, Half.TOP, true));
			} else {
				d.set(x, y + 1, z, Blocks.AIR.defaultBlockState());
			}
		}
	}
}
