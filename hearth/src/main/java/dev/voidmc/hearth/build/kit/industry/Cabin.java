package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;

/**
 * A working building of the camp and the yard: in the forest cultures a log cabin (round logs laid in courses, the
 * corners notched so alternate courses run past each other, a sill of fieldstone, small shuttered windows, a boarded
 * gable), elsewhere the culture's own walls; either way roofed in the culture's manner under the plot's height. Returns
 * the {@link Hall}, so the archetype can furnish it and put its chimney through the roof.
 */
public final class Cabin {
	private Cabin() {
	}

	/**
	 * A one-storey cabin over walls [x0..x1] × [z0..z1] standing at 0, {@code height} tall to its wall plate, its door in
	 * the wall toward {@code doorSide} at column (or row) {@code doorAt}, windows by density, roofed in {@code form} with
	 * the ridge along {@code ridge}. Log walls where the culture builds in timber (and {@code logs}), else the style's.
	 */
	public static Hall build(Draft d, int x0, int z0, int x1, int z1, int height, Direction doorSide, int doorAt, RoofForm form, Direction.Axis ridge,
		boolean logs) {
		int[] heights = Hall.budget(d, x0, z0, x1, z1, 0, new int[]{height}, false, form, 2, ridge);
		Hall h = Hall.of(d, x0, z0, x1, z1, 0, heights, false, false);
		boolean front = doorSide == Dirs.FRONT;
		h.facade(front ? doorAt : -1, 1, 1, 0.3).ground(false);
		if (logs) {
			logWalls(d, h);
			h.openings(false);
		} else {
			h.shell().openings(false);
		}
		if (!front || h.door == null) {
			h.door(0, doorSide, doorAt, 1, true, true);
		}
		h.roof(form, ridge, 2, null);
		return h;
	}

	/** Log walls for the hall's one storey, a fieldstone sill, the floor boarded and the room carved. */
	static void logWalls(Draft d, Hall h) {
		var b = h.body;
		int[] r = b.rect(0);
		Mat log = Works.logs(d);
		Mat sill = d.mat(Role.FOUNDATION);
		Mat floor = d.mat(Role.FLOOR);
		int top = b.plateY();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = r[1] + 1; z <= r[3] - 1; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					d.set(x, -1, z, floor.full());
				}
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(r[0], r[1], r[2], r[3])) {
				Direction.Axis axis = side.axis();
				Direction.Axis other = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					boolean corner = side.isCorner(i);
					for (int y = 0; y <= top; y++) {
						if (y == 0 && !corner) {
							d.set(x, y, z, sill.full());
						} else if (corner) {
							d.set(x, y, z, log.log((y & 1) == 0 ? axis : other));
						} else {
							d.set(x, y, z, log.log(axis));
						}
					}
				}
				// the notched corners: alternate courses run a block past the wall
				for (int end : new int[]{0, side.last()}) {
					int ox = side.x(end) + (end == 0 ? -Dirs.dx(side.along()) : Dirs.dx(side.along()));
					int oz = side.z(end) + (end == 0 ? -Dirs.dz(side.along()) : Dirs.dz(side.along()));
					if (!Works.in(d, ox, oz)) {
						continue;
					}
					for (int y = 1; y <= top; y++) {
						if ((y & 1) == 0 && d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, log.log(axis));
						}
					}
				}
			}
		}
		d.carve(r[0] + 1, 0, r[1] + 1, r[2] - 1, top - 1, r[3] - 1);
		d.eave(top);
		d.body(r[0], r[1], r[2], r[3]);
	}

	/** Whether a culture builds its camps of logs (the timber cultures and the marsh). */
	public static boolean logs(Draft d) {
		Works.Tradition t = Works.tradition(d);
		return (t == Works.Tradition.TIMBER || t == Works.Tradition.MARSH) && Works.logs(d).has(Form.LOG);
	}

	static Habits.WallKind kind(Draft d) {
		return d.habits().walls().kind();
	}

	static void window(Draft d, Side s, int col) {
		Openings.window(d, s, col, 1, 1, 1, Openings.WindowStyle.DRESSED);
	}
}
