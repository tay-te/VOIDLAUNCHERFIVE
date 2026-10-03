package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Rooflines for houses that stand in rows: roofs that never slope down onto a neighbour (a hipped or curved roof is
 * gabled at its party walls and hipped only where it is free, so the house at the end of a row turns the corner),
 * party walls raised as coped parapets above the roof with chimney stacks on them, crow-stepped and coped gables, and
 * a dry run that tells how high a roof will rise before anything is drawn, so a house can fit its height.
 */
public final class Rooflines {
	private Rooflines() {
	}

	/** Whether a form slopes down on all four sides (and so needs gabling at a party wall). */
	public static boolean allRound(RoofForm f) {
		return f == RoofForm.HIP || f == RoofForm.PAGODA || f == RoofForm.MANSARD;
	}

	/**
	 * Plans a roof over walls {@code [wx0..wx1] × [wz0..wz1]} like {@link Roofs#plan}, but a roof that would slope down
	 * toward a party wall (hip, half-hip, mansard, curved) is carried on through it as if the row went on, so it ends in
	 * a gable against the neighbour and keeps its hips, flares and curves on the free sides only.
	 */
	public static Roofs.Plan plan(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean partyL,
		boolean partyR) {
		Roofs.Plan p = Roofs.plan(d, wx0, wz0, wx1, wz1, plateY, form, axis, o, partyL, partyR);
		boolean slopes = allRound(form) || form == RoofForm.HALF_HIP && axis == Direction.Axis.X;
		if (!(partyL || partyR) || !slopes) {
			return p;
		}
		boolean round = allRound(form);
		int eaveX = round || axis == Direction.Axis.Z ? o.eaves() : o.gableOverhang();
		int eaveZ = round || axis == Direction.Axis.X ? o.eaves() : o.gableOverhang();
		int over = round ? Math.min(eaveX, eaveZ) : axis == Direction.Axis.X ? eaveZ : eaveX;
		Roofs.Profile profile = switch (form) {
			case GAMBREL, MANSARD -> Roofs.Profile.broken(Math.max(1.5, o.pitch() * 2), 0.5, over + 2);
			case PAGODA -> Roofs.Profile.curved(Math.max(1, o.pitch()));
			default -> Roofs.Profile.straight(o.pitch());
		};
		int far = 64;
		int nx0 = partyL ? wx0 - far : wx0 - eaveX;
		int nx1 = partyR ? wx1 + far : wx1 + eaveX;
		int nz0 = wz0 - eaveZ;
		int nz1 = wz1 + eaveZ;
		Roofs.Field f;
		if (form == RoofForm.HALF_HIP) {
			int keep = Math.max(1, ((nz1 - nz0) / 2) - 2);
			Roofs.Field hh = Roofs.halfHip(nx0, nz0, nx1, nz1, axis, profile, keep);
			f = hh;
		} else {
			f = Roofs.hip(nx0, nz0, nx1, nz1, profile);
		}
		p.add(f, p.ex0, p.ez0, p.ex1, p.ez1);
		return p;
	}

	/**
	 * The surface y a roof plan would reach (its ridge), drawn on a scratch copy of the draft so nothing is placed.
	 */
	public static int ridgeY(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean partyL,
		boolean partyR) {
		Draft scratch = new Draft(d.spec(), d.style(), d.site());
		return plan(scratch, wx0, wz0, wx1, wz1, plateY, form, axis, o, partyL, partyR).draw(scratch).roof().ridgeY;
	}

	/**
	 * Takes the lifted eave tips off the corners of a curved roof that stand at a party wall (they belong to free
	 * corners only): the corner restated as a plain eave stair.
	 */
	public static void untip(Draft d, Roofs.Roof r, boolean atLeft, boolean atRight) {
		Mat roof = d.mat(Role.ROOF);
		for (int[] c : new int[][]{{r.x0, r.z0}, {r.x0, r.z1}, {r.x1, r.z0}, {r.x1, r.z1}}) {
			boolean left = c[0] == r.x0;
			if (left && !atLeft || !left && !atRight || !r.covers(c[0], c[1])) {
				continue;
			}
			int y = r.surfaceY(c[0], c[1]);
			Direction up = c[1] == r.z0 ? Dirs.BACK : Dirs.FRONT;
			d.inStage(StageKind.ROOF, () -> {
				d.set(c[0], y, c[1], roof.stairsOrSlab(up, Half.BOTTOM));
				if (!d.isAbsent(c[0], y + 1, c[1])) {
					d.remove(c[0], y + 1, c[1]);
				}
				if (!d.isAbsent(c[0], y - 1, c[1]) && d.get(c[0], y - 1, c[1]).getBlock() instanceof net.minecraft.world.level.block.LanternBlock) {
					d.remove(c[0], y - 1, c[1]);
				}
			});
		}
	}

	/**
	 * Raises a wall line under a roof into a parapet one block above the roof surface, capped with coping (slabs of
	 * the accent), with a kneeler at each eave: a party wall's fire break, a coped gable. {@code side} is the wall line
	 * (cells along it); only where the roof covers it.
	 */
	public static void parapet(Draft d, Roofs.Roof r, Side side, int maxY) {
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		Mat edge = coping.has(Form.SLAB) ? coping : wall;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int i = 0; i < side.length(); i++) {
				int x = side.x(i);
				int z = side.z(i);
				if (!r.covers(x, z)) {
					continue;
				}
				int s = r.surfaceY(x, z);
				if (s + 1 >= maxY) {
					continue;
				}
				int bottom = r.bottomY(x, z);
				for (int y = bottom; y <= s; y++) {
					d.set(x, y, z, wall.full());
				}
				boolean end = i == 0 || i == side.last();
				d.set(x, s + 1, z, end ? edge.full() : edge.slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * Crow steps up a gable wall that stands under the roof edge (no overhang there): the wall raised over the roof in
	 * steps two cells wide, each capped with a slab, a pinnacle at the apex.
	 */
	public static void crowSteps(Draft d, Roofs.Roof r, Side side, int maxY) {
		crowSteps(d, r, side, maxY, d.mat(Role.GABLE), d.mat(Role.WALL_ACCENT));
	}

	/**
	 * {@link #crowSteps} in given materials: the karst tradition's stepped fire walls are white walls capped with the
	 * roof's own tiles.
	 */
	public static void crowSteps(Draft d, Roofs.Roof r, Side side, int maxY, Mat wall, Mat coping) {
		int n = side.length();
		int[] s = new int[n];
		for (int i = 0; i < n; i++) {
			s[i] = r.covers(side.x(i), side.z(i)) ? r.surfaceY(side.x(i), side.z(i)) : Integer.MIN_VALUE;
		}
		int groups = (n + 3) / 4 + 1;
		int[] level = new int[groups];
		java.util.Arrays.fill(level, Integer.MIN_VALUE);
		int maxG = 0;
		for (int i = 0; i < n; i++) {
			int g = Math.min(i, n - 1 - i) / 2;
			maxG = Math.max(maxG, g);
			if (s[i] != Integer.MIN_VALUE) {
				level[g] = Math.max(level[g], s[i]);
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int i = 0; i < n; i++) {
				int g = Math.min(i, n - 1 - i) / 2;
				if (s[i] == Integer.MIN_VALUE || level[g] == Integer.MIN_VALUE) {
					continue;
				}
				int top = Math.min(level[g] + (g == maxG ? 2 : 1), maxY - 2);
				int x = side.x(i);
				int z = side.z(i);
				for (int y = r.bottomY(x, z); y <= top; y++) {
					d.set(x, y, z, wall.full());
				}
				d.set(x, top + 1, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * A chimney stack built in a wall line at (x, z): flues rising in the wall from {@code fromY}, standing clear of the
	 * roof by two blocks with a corbelled cap and smoke; only when it fits under the height. Returns whether it was built.
	 */
	public static boolean wallStack(Draft d, int x, int z, int fromY, Roofs.Roof roof, int maxY) {
		if (!roof.covers(x, z)) {
			return false;
		}
		int surface = roof.surfaceY(x, z);
		int top = Math.max(surface + 2, Math.min(roof.ridgeY + 1, maxY - 1));
		if (top >= maxY) {
			return false;
		}
		Chimneys.stack(d, x, z, fromY, roof, top);
		return true;
	}
}
