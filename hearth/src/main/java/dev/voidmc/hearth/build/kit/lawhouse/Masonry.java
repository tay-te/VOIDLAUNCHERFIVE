package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * The walls of the law's buildings, laid by hand rather than by the style's wall kind: a court, a jail and a watch house
 * are masonry whatever the houses round them are built of. The walling is the style's own walls where they are masonry
 * or render (white lime in Bohemia, adobe in the south, rubble on the moors), else its stone; the corners are quoined in
 * the dressing, toothed long and short; the foot is a course of the plinth stone.
 */
public final class Masonry {
	private Masonry() {
	}

	/** The walling: the style's walls where they are masonry or render, else its best stone. */
	public static Stone walling(Draft d) {
		return Stone.stony(d, Role.WALL) ? Stone.of(d, Role.WALL) : Stone.masonry(d);
	}

	/** The plinth stone (dressed where the style has it, else the footing). */
	public static Stone plinth(Draft d) {
		return Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
	}

	/**
	 * Storey k of a body in {@code wall}, quoined in {@code quoin} (toothed when {@code toothed}), a plinth course at the
	 * foot of the ground storey; the floor laid (stone paving below, boards above) and the room carved.
	 */
	public static void storey(Draft d, Body b, int k, Stone wall, Stone quoin, Stone plinth, boolean toothed) {
		int[] r = b.rect(k);
		Halls.floor(d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k) - 1, k == 0);
		if (k > 0) {
			Floors.boards(d, r[0], r[1], r[2], r[3], b.floorY(k) - 1);
		}
		int ya = Walls.bottom(b, k);
		int yg = b.floorY(k + 1) - 1;
		try (var st = d.stage(StageKind.WALLS)) {
			for (Side s : b.sides(k)) {
				for (int i = 0; i < s.length(); i++) {
					boolean corner = s.isCorner(i);
					boolean next = i == 1 || i == s.length() - 2;
					for (int y = ya; y <= yg; y++) {
						Paint p;
						if (k == 0 && y == b.baseY && !corner) {
							p = plinth.full();
						} else if (corner || toothed && next && ((y - b.baseY) & 1) == 0) {
							p = quoin.full();
						} else {
							p = wall.full();
						}
						d.set(s.x(i), y, s.z(i), p);
					}
				}
			}
		}
		d.carve(r[0] + 1, b.floorY(k), r[1] + 1, r[2] - 1, b.floorY(k + 1) - 2, r[3] - 1);
	}

	/**
	 * Window bays along a wall of {@code length} cells (corners at 0 and length - 1): cell indices about {@code pitch}
	 * apart, symmetric about the middle, two clear of each corner.
	 */
	public static List<Integer> bays(int length, int pitch) {
		List<Integer> out = new ArrayList<>();
		int lo = 2;
		int hi = length - 3;
		if (hi < lo) {
			return out;
		}
		int span = hi - lo;
		int n = Math.max(1, (int) Math.round(span / (double) pitch) + 1);
		if (n == 1) {
			out.add((lo + hi) / 2);
			return out;
		}
		for (int k = 0; k < n; k++) {
			int i = lo + (int) Math.round(k * span / (double) (n - 1));
			if (out.isEmpty() || out.get(out.size() - 1) != i) {
				out.add(i);
			}
		}
		return out;
	}
}
