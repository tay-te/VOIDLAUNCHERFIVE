package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Timber towers for the cultures that build in logs, boards and bamboo: a stone base stage under a pent skirt of
 * shingles, framed stages above it (corner posts, girts, boarded or log infill, small lights), and a louvred belfry.
 */
public final class Wooden {
	private Wooden() {
	}

	/** Whether the style builds in timber rather than masonry (logs, boards, bamboo walls of wood). */
	public static boolean timberCulture(Draft d) {
		WallKind k = d.habits().walls().kind();
		return (k == WallKind.LOG || k == WallKind.PLANK || k == WallKind.BAMBOO) && !Stone.stony(d, Role.WALL);
	}

	/** The infill paint of a timber wall running along {@code axis}: logs for log walls, boards otherwise. */
	public static Paint infill(Draft d, Direction.Axis axis) {
		WallKind k = d.habits().walls().kind();
		Mat wall = d.mat(Role.WALL);
		if (k == WallKind.LOG && wall.has(Form.LOG)) {
			return wall.log(axis);
		}
		return k == WallKind.PLANK ? d.mat(Role.GABLE).full() : wall.full();
	}

	/**
	 * A belfry tower in timber over a stone base: the base stage of rows 0-5 in the tower's stone with its plinth, a pent
	 * skirt round its top, framed stages to the belfry floor {@code yb} (corner and middle posts, girts, braces, boarded
	 * or log infill, small lights), then the belfry jettied out a block all round on brackets, louvred between its posts,
	 * to the cornice row {@code yc}, where a beam ring and a deck close it. Returns the square the crown stands on.
	 */
	public static int[] belfryTower(Draft d, Tower t, int yb, int yc, int keepX, int keepZ) {
		int baseTop = 5;
		t.plinth(d, 0, 1, true, keepX, keepZ);
		t.shaft(d, 2, baseTop);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		Mat shutter = d.mat(Role.SHUTTER);
		Mat floor = d.mat(Role.FLOOR);
		boolean logs = d.habits().walls().kind() == WallKind.LOG;
		// a broad tower steps in above its stone base: the framed shaft one block narrower all round
		int inset = t.size >= 7 ? 1 : 0;
		int sx0 = t.x0 + inset;
		int sz0 = t.z0 + inset;
		int sx1 = t.x1 - inset;
		int sz1 = t.z1 - inset;
		Side[] shaft = Side.of(sx0, sz0, sx1, sz1);
		// the pent skirt over the base, round the foot of the shaft
		try (var s = d.stage(StageKind.ROOF)) {
			if (inset > 0) {
				for (Side f : t.faces()) {
					for (int i = 0; i < f.length(); i++) {
						d.set(f.x(i), baseTop + 1, f.z(i), roof.stairsOrSlab(f.out().getOpposite(), Half.BOTTOM));
						int ox = f.outX(i, 1);
						int oz = f.outZ(i, 1);
						if (d.allowed(ox, baseTop, oz) && d.isEmpty(ox, baseTop, oz)) {
							d.set(ox, baseTop, oz, roof.stairsOrSlab(f.out().getOpposite(), Half.BOTTOM));
						}
					}
				}
			} else {
				Set<Long> fp = t.footprint(true);
				for (int[] c : Shapes.around(fp)) {
					if (!d.allowed(c[0], baseTop + 1, c[1]) || !d.isEmpty(c[0], baseTop + 1, c[1])) {
						continue;
					}
					Direction in = Tower.towardShape(fp, c[0], c[1]);
					if (in != null) {
						d.set(c[0], baseTop + 1, c[1], roof.stairsOrSlab(in, Half.BOTTOM));
					}
				}
			}
		}
		if (inset > 0) {
			// the base's top course, closing the stone stage under the skirt
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = t.z0 + 1; z < t.z1; z++) {
					for (int x = t.x0 + 1; x < t.x1; x++) {
						if (x == sx0 || x == sx1 || z == sz0 || z == sz1) {
							d.set(x, baseTop, z, t.body.full());
						}
					}
				}
			}
		}
		int shaftFrom = baseTop + 1 + inset;
		// framed stages: posts at the corners and the middle, girts, a brace in each bay
		for (int y = shaftFrom; y <= yb; y++) {
			boolean girt = y == shaftFrom || y == yb || (y - shaftFrom) % 6 == 0;
			for (Side f : shaft) {
				int mid = f.middle();
				for (int i = 0; i < f.length(); i++) {
					boolean post = f.isCorner(i) || i == mid && !logs && f.length() >= 7;
					Paint p = post ? frame.log(Direction.Axis.Y) : girt ? beam.log(f.axis()) : infill(d, f.axis());
					try (var s = d.stage(post || girt ? StageKind.FRAME : StageKind.WALLS)) {
						d.set(f.x(i), y, f.z(i), p);
					}
				}
			}
		}
		if (!logs) {
			// braces rising from the corner posts in each stage
			for (int y0 = shaftFrom + 1; y0 + 2 < yb; y0 += 6) {
				for (Side f : shaft) {
					int span = Math.min(3, f.length() / 2 - 1);
					for (int k = 1; k <= span; k++) {
						try (var s = d.stage(StageKind.FRAME)) {
							d.set(f.x(k), y0 + k - 1, f.z(k), frame.log(Direction.Axis.Y));
							d.set(f.x(f.last() - k), y0 + k - 1, f.z(f.last() - k), frame.log(Direction.Axis.Y));
						}
					}
				}
			}
		}
		// little lights in the framed stages
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int y = shaftFrom + 3; y + 2 < yb; y += 6) {
				for (Side f : shaft) {
					int i = f.middle();
					d.set(f.x(i), y, f.z(i), d.mat(Role.GLASS).pane());
					d.set(f.x(i), y + 1, f.z(i), d.mat(Role.GLASS).pane());
				}
			}
		}
		if (inset > 0) {
			// raking brackets from the shaft out under the belfry's overhang
			try (var s = d.stage(StageKind.FRAME)) {
				for (Side f : shaft) {
					for (int i = 0; i < f.length(); i += 2) {
						int ox = f.outX(i, 1);
						int oz = f.outZ(i, 1);
						d.set(ox, yb - 2, oz, beam.stairsOrSlab(f.out().getOpposite(), Half.TOP));
						d.set(ox, yb - 1, oz, beam.log(f.out().getAxis()));
					}
				}
			}
		}
		// the belfry, a block out all round: floor on brackets, posts, louvres, a girt, the beam ring and a deck
		int bx0 = t.x0 - 1;
		int bz0 = t.z0 - 1;
		int bx1 = t.x1 + 1;
		int bz1 = t.z1 + 1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					boolean inShaft = x > sx0 && x < sx1 && z > sz0 && z < sz1;
					if (d.allowed(x, yb, z) && !inShaft) {
						d.set(x, yb, z, floor.full());
					}
				}
			}
			for (Side f : Side.of(t.x0, t.z0, t.x1, t.z1)) {
				for (int i = 0; i < f.length(); i += 2) {
					int ox = f.outX(i, 1);
					int oz = f.outZ(i, 1);
					if (d.allowed(ox, yb - 1, oz) && d.isEmpty(ox, yb - 1, oz)) {
						d.set(ox, yb - 1, oz, beam.stairsOrSlab(f.out().getOpposite(), Half.TOP));
					}
				}
			}
		}
		Side[] bs = Side.of(bx0, bz0, bx1, bz1);
		for (int y = yb + 1; y <= yc; y++) {
			for (Side f : bs) {
				int mid = f.middle();
				for (int i = 0; i < f.length(); i++) {
					int x = f.x(i);
					int z = f.z(i);
					if (!d.allowed(x, y, z)) {
						continue;
					}
					boolean corner = f.isCorner(i);
					boolean louvre = Math.abs(i - mid) <= 1 && y < yc - 1;
					Paint p;
					StageKind st;
					if (corner || i == mid - 2 || i == mid + 2) {
						p = frame.log(Direction.Axis.Y);
						st = StageKind.FRAME;
					} else if (y >= yc - 1) {
						p = beam.log(f.axis());
						st = StageKind.FRAME;
					} else if (louvre && y == yb + 1) {
						// the louvres stand on a rail: an open louvre is a gap a person steps into and out of
						p = d.mat(Role.RAILING).fence();
						st = StageKind.EXTERIOR;
					} else if (louvre) {
						p = shutter.has(Form.TRAPDOOR) ? shutter.trapdoor(f.out().getOpposite(), Half.TOP, true) : frame.fence();
						st = StageKind.OPENINGS;
					} else {
						p = y == yb + 1 ? d.mat(Role.RAILING).fence() : infill(d, f.axis());
						st = y == yb + 1 ? StageKind.EXTERIOR : StageKind.WALLS;
					}
					try (var s = d.stage(st)) {
						d.set(x, y, z, p);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = bz0 + 1; z <= bz1 - 1; z++) {
				for (int x = bx0 + 1; x <= bx1 - 1; x++) {
					d.set(x, yc, z, floor.full());
				}
			}
		}
		return new int[]{bx0, bz0, bx1, bz1};
	}
}
