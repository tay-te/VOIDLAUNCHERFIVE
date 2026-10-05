package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * A dormer cut into a finished pitched roof: the commonest way a household gains a usable room in its attic. A window
 * bay standing on the wall line, its front in the house's gable infill between two posts, its own little gable roof
 * running back into the main slope, the eave broken in front of it, the attic opened into it. Put on the street side
 * where the slope faces it, else the back; on a long slope a pair. Only where there is an attic floor to stand at the
 * window and a slope that rises far enough to take the dormer's ridge under the main one.
 */
public final class Dormer {
	private Dormer() {
	}

	/** Adds a dormer (or a pair on a long slope) to the house. Returns whether one was built. */
	public static boolean add(HouseScan h, long seed) {
		if (h.flat) {
			return false;
		}
		int sill = attic(h);
		if (sill == Integer.MIN_VALUE) {
			if (DBG) {
				System.out.println("DORMER no attic: floors " + h.floors + " eave " + h.eave);
			}
			return false;
		}
		List<Direction> sides = new ArrayList<>(List.of(Dirs.FRONT, Dirs.BACK, Dirs.RIGHT, Dirs.LEFT));
		if ((seed & 3) == 0) {
			sides.set(0, Dirs.BACK);
			sides.set(1, Dirs.FRONT);
		}
		for (Direction out : sides) {
			if (h.party(out)) {
				continue;
			}
			Side s = h.side(out);
			int last = s.last();
			int width = last >= 10 ? 2 : 1;
			// candidate bays from the middle outward, their cheeks off the corners
			List<Integer> starts = new ArrayList<>();
			int mid = (last - width + 1) / 2;
			for (int k = 0; k <= last; k++) {
				int i = k % 2 == 0 ? mid + k / 2 : mid - (k + 1) / 2;
				if (i - 1 >= 1 && i + width <= last - 1 && !starts.contains(i)) {
					starts.add(i);
				}
			}
			for (int i0 : starts) {
				if (fits(h, s, i0, width, sill)) {
					build(h, s, i0, width, sill);
					// a pair on a long slope: the mirror bay, if it is far enough off
					int j0 = last - (i0 + width - 1);
					if (last >= 12 && Math.abs(j0 - i0) >= width + 4 && fits(h, s, j0, width, sill)) {
						build(h, s, j0, width, sill);
					}
					return true;
				}
			}
		}
		return false;
	}

	static final boolean DBG = System.getenv("HEARTH_AGE_DBG") != null;

	static void dbg(Side s, int i0, String why) {
		if (DBG) {
			System.out.println("DORMER " + s.out() + " " + i0 + ": " + why);
		}
	}

	/** The attic's standing level (a floor at or above two under the eave), or MIN_VALUE when the roof has no floor in it. */
	static int attic(HouseScan h) {
		int top = h.floors.get(h.floors.size() - 1);
		if (top == 0 || top < h.eave - 2 || top > h.eave) {
			return Integer.MIN_VALUE;
		}
		return top;
	}

	/** The highest roof-stage cell at a column from the eave up, or MIN_VALUE. */
	static int roofTop(Draft d, int x, int z, int from) {
		int best = Integer.MIN_VALUE;
		for (int y = from; y < d.maxHeight(); y++) {
			Draft.Cell c = d.cell(x, y, z);
			if (c != null && !c.state().isAir() && c.stage() == StageKind.ROOF) {
				best = y;
			}
		}
		return best;
	}

	private static double pitch(Draft d) {
		return Math.clamp(d.habits().roof().pitch(), 0.75, 1.0);
	}

	private static boolean fits(HouseScan h, Side s, int i0, int width, int sill) {
		Draft d = h.d;
		Direction in = s.out().getOpposite();
		int base = sill + 2;
		double pitch = pitch(d);
		int half = (width + 3) / 2;
		int ridge = base + (int) Math.ceil(pitch * half);
		for (int i = i0 - 2; i <= i0 + width + 1; i++) {
			// a slope rising inward over the wall line, from the eave
			int x0 = s.x(i);
			int z0 = s.z(i);
			int r0 = roofTop(d, x0, z0, h.eave - 1);
			int r2 = roofTop(d, x0 + Dirs.dx(in) * 2, z0 + Dirs.dz(in) * 2, h.eave - 1);
			if (r0 == Integer.MIN_VALUE || r2 == Integer.MIN_VALUE || r2 < r0 + 1 || r0 > base + 1) {
				dbg(s, i0, "slope r0=" + r0 + " r2=" + r2 + " base=" + base + " at " + i);
				return false;
			}
			// the main roof stands above the dormer's ridge somewhere inward
			boolean meets = false;
			for (int k = 1; k <= 8 && !meets; k++) {
				meets = roofTop(d, x0 + Dirs.dx(in) * k, z0 + Dirs.dz(in) * k, h.eave - 1) >= ridge + 1;
			}
			if (!meets) {
				dbg(s, i0, "meets ridge=" + ridge);
				return false;
			}
		}
		// the window bay: a plain wall under the eave at the sill, room to stand behind it
		for (int i = i0; i < i0 + width; i++) {
			int x = s.x(i);
			int z = s.z(i);
			int ix = x + Dirs.dx(in);
			int iz = z + Dirs.dz(in);
			if (!h.standable(ix, sill, iz) && !(d.isEmpty(ix, sill, iz) && d.isEmpty(ix, sill + 1, iz))) {
				dbg(s, i0, "stand");
				return false;
			}
			for (int y = sill; y < h.eave; y++) {
				Draft.Cell c = d.cell(x, y, z);
				if (c == null || c.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
					dbg(s, i0, "wall");
					return false;
				}
			}
		}
		// nothing in front of the window but the roof's own overhang
		for (int i = i0 - 1; i <= i0 + width; i++) {
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (!dev.voidmc.hearth.build.kit.townhome.Fronts.allowed(d, ox, base, oz)) {
				dbg(s, i0, "allowed");
				return false;
			}
			for (int y = sill; y <= base; y++) {
				Draft.Cell c = d.cell(ox, y, oz);
				if (c != null && !c.state().isAir() && c.stage() != StageKind.ROOF) {
					dbg(s, i0, "front " + c.state());
					return false;
				}
			}
		}
		return true;
	}

	private static void build(HouseScan h, Side s, int i0, int width, int sill) {
		Draft d = h.d;
		Direction out = s.out();
		Direction in = out.getOpposite();
		Mat wall = d.mat(Role.GABLE);
		Mat frame = d.mat(Role.FRAME);
		Mat glass = d.mat(Role.GLASS);
		int base = sill + 2;
		double pitch = pitch(d);
		// the dormer's roof: a gable over the bay, a block of overhang all round, its ridge running back into the slope
		int c0 = i0 - 2;
		int c1 = i0 + width + 1;
		int ax = Math.min(s.outX(c0, 1), s.outX(c1, -9));
		int bx = Math.max(s.outX(c0, 1), s.outX(c1, -9));
		int az = Math.min(s.outZ(c0, 1), s.outZ(c1, -9));
		int bz = Math.max(s.outZ(c0, 1), s.outZ(c1, -9));
		Direction.Axis ridgeAxis = in.getAxis();
		Roofs.Field gable = Roofs.gable(ax, az, bx, bz, ridgeAxis, Roofs.Profile.straight(pitch));
		// only where it stands proud of the main roof
		int[] mainTop = new int[(bx - ax + 1) * (bz - az + 1)];
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				mainTop[(z - az) * (bx - ax + 1) + (x - ax)] = roofTop(d, x, z, h.eave - 1);
			}
		}
		final int fax = ax;
		final int faz = az;
		final int fbx = bx;
		Roofs.Field proud = (x, z) -> {
			double v = gable.h(x, z);
			if (Double.isNaN(v)) {
				return v;
			}
			int y = base + (int) Math.round(v * 2) / 2 - 1;
			int m = mainTop[(z - faz) * (fbx - fax + 1) + (x - fax)];
			// the overhang row out front always; inward only while above the main roof
			boolean front = s.out().getAxis() == Direction.Axis.Z ? z == s.outZ(0, 1) : x == s.outX(0, 1);
			return front || m == Integer.MIN_VALUE || y > m ? v : Double.NaN;
		};
		// open the eave in front of the bay and the attic into it: the main roof's blocks inside the dormer go
		for (int i = i0 - 1; i <= i0 + width; i++) {
			if (i == i0 - 1 || i == i0 + width) {
				continue;
			}
			for (int k = -1; k <= 9; k++) {
				int x = s.outX(i, -k);
				int z = s.outZ(i, -k);
				double v = proud.h(x, z);
				if (Double.isNaN(v)) {
					if (k > 0) {
						break;
					}
					continue;
				}
				int top = base + (int) Math.round(v * 2) / 2 - 1;
				int from = k < 0 ? sill : Math.max(sill, h.eave);
				for (int y = from; y <= top + 1; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (c != null && c.stage() == StageKind.ROOF) {
						final int yy = y;
						d.inStage(StageKind.CLEAR, () -> d.air(x, yy, z));
					}
				}
			}
		}
		Roofs.Roof dr;
		try (var sc = d.stage(StageKind.ROOF)) {
			dr = Roofs.raster(d, proud, base, ax, az, bx, bz, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
		// the front: posts at the cheeks, the gable infill, the window
		try (var sc = d.stage(StageKind.WALLS)) {
			for (int i = i0 - 1; i <= i0 + width; i++) {
				int x = s.x(i);
				int z = s.z(i);
				boolean cheek = i == i0 - 1 || i == i0 + width;
				int under = dr.covers(x, z) ? dr.bottomY(x, z) : base;
				for (int y = h.eave; y < under; y++) {
					d.set(x, y, z, cheek ? frame.log(Direction.Axis.Y) : wall.full());
				}
				// the cheeks run back until the dormer's roof meets the slope
				if (cheek) {
					for (int k = 1; k <= 9; k++) {
						int cx = s.outX(i, -k);
						int cz = s.outZ(i, -k);
						if (!dr.covers(cx, cz)) {
							break;
						}
						int m = roofTop(d, cx, cz, h.eave - 1);
						int u = dr.bottomY(cx, cz);
						for (int y = Math.max(sill, Math.min(u, m + 1)); y < u; y++) {
							if (d.isEmpty(cx, y, cz) || d.cell(cx, y, cz).stage() == StageKind.ROOF) {
								d.set(cx, y, cz, wall.full());
							}
						}
					}
				}
			}
		}
		try (var sc = d.stage(StageKind.OPENINGS)) {
			for (int i = i0; i < i0 + width; i++) {
				for (int y = sill; y <= sill + 1; y++) {
					d.set(s.x(i), y, s.z(i), glass.pane());
				}
			}
		}
		// the attic opens into the dormer
		for (int i = i0; i < i0 + width; i++) {
			for (int k = 1; k <= 9; k++) {
				int x = s.outX(i, -k);
				int z = s.outZ(i, -k);
				if (!dr.covers(x, z)) {
					break;
				}
				int u = dr.bottomY(x, z);
				for (int y = sill; y < u; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (c != null && !c.state().isAir() && c.stage() == StageKind.ROOF) {
						final int yy = y;
						d.inStage(StageKind.CLEAR, () -> d.air(x, yy, z));
					}
				}
			}
		}
	}

}
