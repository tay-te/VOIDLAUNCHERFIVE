package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Roofs on posts: lean-tos against a wall (a smith's pentice over the anvil, a lumber shelter, a cart shed) and open
 * sheds standing free (a saw shed, a drying shed), with their posts carried up to whatever the roof turned out to be
 * and down to the ground, so nothing hangs in the air. Roofs are height fields ({@link Roofs}), so a lean-to tucked
 * under a hall's eave and a hall's own roof meet the way a builder would lap them.
 */
public final class Shed {
	private Shed() {
	}

	/**
	 * A single slope over a rectangle rising toward {@code up}, its low edge's stairs at {@code eaveY}, in the style's
	 * roofing (or {@code roofMat}, if given). The slope rises {@code pitch} per block (0.5 a slab roof, 1 a stair roof).
	 */
	public static Roofs.Roof pent(Draft d, int x0, int z0, int x1, int z1, Direction up, int eaveY, double pitch, Mat roofMat) {
		Mat roof = roofMat != null ? roofMat : d.mat(Role.ROOF);
		Mat trim = roofMat != null ? roofMat : d.mat(Role.ROOF_TRIM);
		int ax = Math.max(0, Math.min(x0, x1));
		int bx = Math.min(d.frontage() - 1, Math.max(x0, x1));
		int az = Math.max(-1, Math.min(z0, z1));
		int bz = Math.min(d.depth() - 1, Math.max(z0, z1));
		Roofs.Field f = Roofs.shed(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), up, Roofs.Profile.straight(pitch));
		return Roofs.raster(d, f, eaveY, ax, az, bx, bz, roof, trim, Habits.Ridge.SLAB);
	}

	/** How high the high edge of a pent roof over {@code run} rows would stand above its eave. */
	public static int rise(int run, double pitch) {
		int t = Math.max(1, (int) Math.round((1 + pitch * run) * 2));
		return (t + 1) / 2 - 1;
	}

	/**
	 * A lean-to against wall {@code wall} over its columns {@code c0..c1}, reaching {@code depth} out (plus a row of
	 * overhang where the plot allows), the posts at the outer row standing at level {@code y}, the eave at
	 * {@code eaveY}. The pitch is chosen so the high edge stays at or below {@code topY} (the hall's wall plate or
	 * eave). Posts at the ends and every third column, except at {@code openings} columns. Returns the roof.
	 */
	public static Roofs.Roof leanTo(Draft d, Side wall, int c0, int c1, int depth, int y, int eaveY, int topY, int... openings) {
		Direction out = wall.out();
		int ia = Math.min(wall.index(c0), wall.index(c1));
		int ib = Math.max(wall.index(c0), wall.index(c1));
		int over = 1;
		int rows = depth + over;
		double pitch = 1.0;
		while (pitch > 0.5 && eaveY + rise(rows - 1, pitch) > topY) {
			pitch -= 0.5;
		}
		int ax = Integer.MAX_VALUE;
		int az = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		for (int i = ia - 1; i <= ib + 1; i++) {
			for (int k = 1; k <= rows; k++) {
				int x = wall.outX(i, k);
				int z = wall.outZ(i, k);
				ax = Math.min(ax, x);
				az = Math.min(az, z);
				bx = Math.max(bx, x);
				bz = Math.max(bz, z);
			}
		}
		// the low edge is the outermost row
		Roofs.Roof roof = pent(d, ax, az, bx, bz, out.getOpposite(), eaveY - 0, pitch, null);
		List<int[]> posts = new ArrayList<>();
		for (int i = ia; i <= ib; i++) {
			boolean end = i == ia || i == ib;
			if (!end && (i - ia) % 3 != 0) {
				continue;
			}
			int col = wall.column(i);
			boolean skip = false;
			for (int o : openings) {
				skip |= o == col;
			}
			if (skip) {
				continue;
			}
			posts.add(new int[]{wall.outX(i, depth), wall.outZ(i, depth)});
		}
		posts(d, roof, posts, y);
		return roof;
	}

	/**
	 * Posts standing at level y at each cell, up to the underside of the roof above them, on a footing down to the ground.
	 */
	public static void posts(Draft d, Roofs.Roof roof, List<int[]> cells, int y) {
		Mat frame = d.mat(Role.FRAME);
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : cells) {
				int x = c[0];
				int z = c[1];
				if (!Walls2.inPlot(d, x, z)) {
					continue;
				}
				int top = roof != null && roof.covers(x, z) ? roof.bottomY(x, z) - 1 : y + 2;
				for (int yy = y; yy <= top; yy++) {
					d.set(x, yy, z, frame.log(Direction.Axis.Y));
				}
				int g = d.site().ground(x, z);
				if (g < y && d.isAbsent(x, y - 1, z)) {
					try (var f = d.stage(StageKind.FOUNDATION)) {
						for (int yy = g - 1; yy < y; yy++) {
							d.set(x, yy, z, found.full());
						}
					}
				}
			}
		}
	}

	/**
	 * An open shed standing free over walls {@code [x0..x1] × [z0..z1]}: posts at the corners and every few blocks, a
	 * beam round the head at {@code y + height}, a roof in {@code form} (gable ends boarded) fitted under the plot's
	 * height less {@code reserve}. Sides listed in {@code closed} are boarded up to the beam. Returns the roof (null if
	 * it had to be flat).
	 */
	public static Roofs.Roof open(Draft d, int x0, int z0, int x1, int z1, int y, int height, RoofForm form, Direction.Axis axis, int reserve,
		Direction... closed) {
		return open(d, x0, z0, x1, z1, y, height, form, axis, reserve, null, closed);
	}

	/** {@link #open}, the closed sides built of {@code wallMat} (null: the style's boards). */
	public static Roofs.Roof open(Draft d, int x0, int z0, int x1, int z1, int y, int height, RoofForm form, Direction.Axis axis, int reserve,
		Mat wallMat, Direction... closed) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat board = wallMat != null ? wallMat : d.mat(Role.GABLE);
		int plate = y + height;
		int spacing = Math.max(2, Math.min(4, d.habits().walls().postSpacing() + 1));
		try (var s = d.stage(StageKind.FRAME)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				boolean shut = false;
				for (Direction c : closed) {
					shut |= c == side.out();
				}
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					boolean post = side.isCorner(i) || i % spacing == 0 && i < side.last() - 1;
					for (int yy = y; yy < plate; yy++) {
						if (post) {
							d.set(x, yy, z, frame.log(Direction.Axis.Y));
						} else if (shut) {
							d.set(x, yy, z, board.full());
						}
					}
					d.set(x, plate, z, side.isCorner(i) ? frame.log(Direction.Axis.Y) : beam.log(side.axis()));
				}
			}
		}
		RoofForm f = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		Roofs.Opts o = Hall.fit(d, x0, z0, x1, z1, plate, f, axis, Roofs.Opts.of(d), false, false, d.maxHeight() - 1 - reserve);
		if (o == null || f == RoofForm.FLAT || f == RoofForm.DOME || f == RoofForm.CONE) {
			// a flat deck of boards on the beams
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						d.set(x, plate + 1, z, d.mat(Role.ROOF).slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
					}
				}
			}
			return null;
		}
		return Roofs.plan(d, x0, z0, x1, z1, plate, f, axis, o, false, false).draw(d).roof();
	}

	/**
	 * Closes a steep roof (an oast's pyramid, a mill cap's cone): wherever a column's surface stands more than a block
	 * above a neighbour's, full blocks of {@code m} fill the column down to meet it, so the peak sits on the slope and no
	 * course hangs in the air.
	 */
	public static void solidify(Draft d, Roofs.Roof roof, Mat m) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					if (!roof.covers(x, z)) {
						continue;
					}
					int top = roof.surfaceY(x, z);
					int low = top;
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (roof.covers(nx, nz)) {
							low = Math.min(low, roof.surfaceY(nx, nz));
						}
					}
					for (int y = low + 1; y < top; y++) {
						if (d.isAbsent(x, y, z) || d.isEmpty(x, y, z)) {
							d.set(x, y, z, m.full());
						}
					}
				}
			}
		}
	}

	/** Planks laid as a deck at row y over a rectangle (a shed's floor, a loading platform), over anything not drawn. */
	public static void deck(Draft d, int x0, int z0, int x1, int z1, int y, Mat m) {
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (d.isEmpty(x, y, z)) {
						d.set(x, y, z, m.full());
					}
				}
			}
		}
	}

	/** Upside-down stair brackets under a beam end or eave at (x, y, z), facing back toward {@code wallSide}. */
	public static void bracket(Draft d, int x, int y, int z, Direction toward) {
		Mat beam = d.mat(Role.BEAM);
		if (Walls2.inPlot(d, x, z) && d.isAbsent(x, y, z)) {
			d.inStage(StageKind.FRAME, () -> d.set(x, y, z, beam.has(Form.STAIRS) ? beam.stairs(toward, Half.TOP) : beam.log(Direction.Axis.Y)));
		}
	}

	/** Plot helpers shared by the trade kit. */
	static final class Walls2 {
		private Walls2() {
		}

		static boolean inPlot(Draft d, int x, int z) {
			return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
		}
	}
}
