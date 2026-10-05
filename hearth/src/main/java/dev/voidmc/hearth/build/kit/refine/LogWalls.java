package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walls of round logs laid course on course, as a forest lodge or a herdsman's hut is raised: every log runs along its
 * wall, the corners are notched so the logs cross, and on every other course the log ends stand out a block past the
 * corner, first along one wall, then along the other. The ends are what makes a log house read from across a clearing.
 */
public final class LogWalls {
	private LogWalls() {
	}

	/** Whether the culture raises log walls where it builds in the forest (the timber cultures of the north and west). */
	public static boolean suits(Draft d) {
		Culture c = d.style().culture();
		if (c == null) {
			return false;
		}
		return switch (c) {
			case TEMPERATE, WISTERIA, NORTHERN, TUNDRA, ALPINE, MARITIME, BAYOU, MOORLAND -> log(d) != null;
			default -> false;
		};
	}

	/** The logs: the walls' own where they are logs, else the trees of the country, else the frame's timber. */
	public static Mat log(Draft d) {
		for (Role r : new Role[]{Role.WALL, Role.TREE_LOG, Role.FRAME, Role.BEAM}) {
			Mat m = d.mat(r);
			// real round timber only (a mushroom stem's "log" is white plaster to the eye)
			if (m.has(Form.LOG) && m.main(Form.LOG) != null
				&& net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(m.main(Form.LOG)).getPath().endsWith("_log")) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Relays the full blocks of the wall ring {@code [x0..x1] × [z0..z1]} from row {@code y0} to {@code y1} as logs in
	 * {@code logs}, the corners crossing and the ends standing out on alternate courses (only into free cells). Openings
	 * (glass, doors, shutters) are kept. Returns the number of logs laid.
	 */
	public static int lay(Draft d, int x0, int z0, int x1, int z1, int y0, int y1, Mat logs) {
		int n = 0;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				boolean alongX = ((y - y0) & 1) == 0;
				for (int x = x0; x <= x1; x++) {
					for (int z : new int[]{z0, z1}) {
						boolean corner = x == x0 || x == x1;
						n += relay(d, x, y, z, corner ? (alongX ? Direction.Axis.X : Direction.Axis.Z) : Direction.Axis.X, logs);
					}
				}
				for (int z = z0 + 1; z <= z1 - 1; z++) {
					for (int x : new int[]{x0, x1}) {
						n += relay(d, x, y, z, Direction.Axis.Z, logs);
					}
				}
				// the ends past the corners
				for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
					if (!d.isSolid(c[0], y, c[1])) {
						continue;
					}
					int ex = alongX ? c[0] + (c[0] == x0 ? -1 : 1) : c[0];
					int ez = alongX ? c[1] : c[1] + (c[1] == z0 ? -1 : 1);
					// over head height only: an end at a walker's head would bar the way round the corner
					if (d.allowed(ex, y, ez) && d.isAbsent(ex, y, ez) && y >= dev.voidmc.hearth.build.kit.Garden.groundAt(d, ex, ez) + 2) {
						d.set(ex, y, ez, logs.log(alongX ? Direction.Axis.X : Direction.Axis.Z));
						n++;
					}
				}
			}
		}
		return n;
	}

	private static int relay(Draft d, int x, int y, int z, Direction.Axis axis, Mat logs) {
		BlockState s = d.get(x, y, z);
		if (s == null || s.isAir() || !d.isSolid(x, y, z) || s.hasBlockEntity()) {
			return 0;
		}
		d.set(x, y, z, logs.log(axis));
		return 1;
	}
}
