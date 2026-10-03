package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The small buildings of a farm: open-fronted lean-to sheds and field shelters, closed little gabled sheds and byres in
 * the culture's walls, granaries raised on staddle stones out of the rats' reach, hen houses. Each grounds itself
 * (footings to the ground, its floor made up or dug out) and keeps to its rectangle plus one row of eaves where the plot
 * allows.
 */
public final class Sheds {
	private Sheds() {
	}

	/** What a board wall of a shed is made of in this culture: logs in log cultures, masonry where walls are masonry. */
	public static Paint boards(Draft d, Direction.Axis along) {
		WallKind kind = d.habits().walls().kind();
		return switch (kind) {
			case LOG -> d.mat(Role.WALL).log(along).orElse(d.mat(Role.WALL).full());
			case STONE, PLASTER, ADOBE, BRICK -> d.mat(Role.WALL).full();
			// a timber-framed culture boards its sheds and barns with the planks of its joinery, not its plaster
			case TIMBER_FRAME -> d.mat(Role.DOOR).full();
			default -> d.mat(Role.GABLE).full();
		};
	}

	/**
	 * A lean-to over {@code [x0..x1] × [z0..z1]} (its walls included) whose floor is at standing level {@code y}: posts at
	 * the corners, the side {@code open} left open under a beam {@code clear} blocks up (or closed with a door in its
	 * middle when {@code door}), the other three sides boarded up to a roof that rises away from the open side and
	 * overhangs it by a row where the plot allows. Returns the roof.
	 */
	public static Roofs.Roof lean(Draft d, int x0, int z0, int x1, int z1, int y, Direction open, int clear, boolean door) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Groundwork.footings(d, x0, z0, x1, z1, y - 1);
		try (var s = d.stage(StageKind.FLOORS)) {
			Mat floor = door ? d.mat(Role.FLOOR) : d.mat(Role.PAVING);
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, y - 1, z, floor.full());
				}
			}
		}
		Direction up = open.getOpposite();
		// the roof: a row of eaves all round where the plot allows (none over the high back)
		int rx0 = x0 - (inPlotX(d, x0 - 1) && up != Dirs.LEFT ? 1 : 0);
		int rx1 = x1 + (inPlotX(d, x1 + 1) && up != Dirs.RIGHT ? 1 : 0);
		int rz0 = z0 - (z0 - 1 >= 0 && up != Dirs.FRONT ? 1 : 0);
		int rz1 = z1 + (z1 + 1 < d.depth() && up != Dirs.BACK ? 1 : 0);
		// the field is laid out as if the open-side eave were always there, so the beam sits under the same course
		int fx0 = open == Dirs.LEFT ? x0 - 1 : rx0;
		int fx1 = open == Dirs.RIGHT ? x1 + 1 : rx1;
		int fz0 = open == Dirs.FRONT ? z0 - 1 : rz0;
		int fz1 = open == Dirs.BACK ? z1 + 1 : rz1;
		Roofs.Field f = Roofs.shed(fx0, fz0, fx1, fz1, up, Roofs.Profile.straight(0.5));
		int y0 = y + clear;
		Roofs.Roof roof;
		try (var s = d.stage(StageKind.ROOF)) {
			roof = Roofs.raster(d, f, y0, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
		Side[] sides = Side.of(x0, z0, x1, z1);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				for (int yy = y; yy < roof.bottomY(c[0], c[1]); yy++) {
					d.set(c[0], yy, c[1], frame.log(Direction.Axis.Y));
				}
			}
		}
		for (Side side : sides) {
			boolean openSide = side.out() == open;
			for (int i = 1; i < side.last(); i++) {
				int x = side.x(i);
				int z = side.z(i);
				int under = roof.bottomY(x, z);
				if (openSide && !door) {
					// a beam across the opening, posts every third block on a long front
					d.inStage(StageKind.FRAME, () -> {
						for (int yy = y0; yy < under; yy++) {
							d.set(x, yy, z, beam.log(side.axis()));
						}
					});
					if (side.length() >= 6 && i % 3 == 0 && i < side.last() - 1) {
						d.inStage(StageKind.FRAME, () -> {
							for (int yy = y; yy < y0; yy++) {
								d.set(x, yy, z, frame.log(Direction.Axis.Y));
							}
						});
					}
					for (int yy = y; yy < y0; yy++) {
						d.set(x, yy, z, Blocks.AIR.defaultBlockState());
					}
					continue;
				}
				Paint p = boards(d, side.axis());
				d.inStage(StageKind.WALLS, () -> {
					for (int yy = y; yy < under; yy++) {
						d.set(x, yy, z, p);
					}
				});
			}
		}
		d.carve(x0 + 1, y, z0 + 1, x1 - 1, y0 + 4, z1 - 1);
		if (door) {
			Side front = Side.of(x0, z0, x1, z1, open);
			int mid = front.middle();
			Openings.door(d, front, front.column(mid), y, 1, new Openings.DoorOpts(false, false, false));
			if (front.length() >= 5) {
				int wi = mid + 2 <= front.last() - 1 ? mid + 2 : mid - 2;
				Openings.window(d, front, front.column(wi), y + 1, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, false));
			}
		}
		return roof;
	}

	/**
	 * The floor level for a little building over {@code [x0..x1] × [z0..z1]} opening toward {@code open}, on the ground
	 * as drawn so far: the mean of the row in front of it, if the ground under it and that row varies by no more than
	 * {@code tolerance}; MIN_VALUE if it does. Levels the row in front to the floor and eases the row beyond it to
	 * within a step, so folk can walk in.
	 */
	public static int pad(Draft d, int x0, int z0, int x1, int z1, Direction open, int tolerance) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		Side front = Side.of(x0, z0, x1, z1, open);
		long sum = 0;
		for (int i = 0; i <= front.last(); i++) {
			int fx = front.outX(i, 1);
			int fz = front.outZ(i, 1);
			if (fx < 0 || fz < 0 || fx >= d.frontage() || fz >= d.depth()) {
				return Integer.MIN_VALUE;
			}
			int g = Spots.level(d, fx, fz);
			sum += g;
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int g = Spots.level(d, x, z);
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
			}
		}
		if (hi - lo > tolerance) {
			return Integer.MIN_VALUE;
		}
		int y = Math.round((float) sum / front.length());
		// the row in front at the floor, the one beyond within a step of it
		for (int i = 0; i <= front.last(); i++) {
			int fx = front.outX(i, 1);
			int fz = front.outZ(i, 1);
			Groundwork.level(d, fx, fz, fx, fz, y);
			int bx = front.outX(i, 2);
			int bz = front.outZ(i, 2);
			if (bx >= 0 && bz >= 0 && bx < d.frontage() && bz < d.depth()) {
				int g = Spots.level(d, bx, bz);
				if (Math.abs(g - y) > 1) {
					Groundwork.level(d, bx, bz, bx, bz, Math.clamp(g, y - 1, y + 1));
				}
			}
		}
		return y;
	}

	private static boolean inPlotX(Draft d, int x) {
		return x >= 0 && x < d.frontage();
	}

	/**
	 * A small gabled building over {@code [x0..x1] × [z0..z1]} in the culture's walls and roof: one storey
	 * {@code height} high, the ridge along {@code ridge}, a door in the middle of wall {@code doorSide}, windows by the
	 * facade's habits. Returns the roof (null if the culture roofs it flat).
	 */
	public static Roofs.Roof small(Draft d, int x0, int z0, int x1, int z1, int y, int height, Direction.Axis ridge, Direction doorSide, int doorCol,
		boolean windows) {
		Body b = new Body(x0, z0, x1, z1, y, new int[]{height}, false, false);
		Groundwork.footings(d, x0, z0, x1, z1, y - 1);
		Floors.boards(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y - 1);
		Facade f = Facade.plan(d, b, doorSide == Dirs.FRONT ? doorCol : -1, 1, 1, windows ? 0.5 : 0.0);
		Walls.storey(d, b, 0, f);
		d.carve(x0 + 1, y, z0 + 1, x1 - 1, y + height - 2, z1 - 1);
		Side ds = b.side(0, doorSide);
		if (doorSide == Dirs.FRONT) {
			Integer col = null;
			for (Facade.Opening o : f.openings(Dirs.FRONT)) {
				if (o.kind() == Facade.Kind.DOOR) {
					col = o.at();
				}
			}
			Openings.door(d, ds, col != null ? col : ds.column(ds.middle()), y, 1, new Openings.DoorOpts(false, false, false));
		} else {
			Openings.door(d, ds, ds.contains(doorCol) ? doorCol : ds.column(ds.middle()), y, 1, new Openings.DoorOpts(false, false, false));
		}
		if (windows) {
			for (Side s : b.sides(0)) {
				for (Facade.Opening o : f.openings(s.out())) {
					if (o.kind() == Facade.Kind.WINDOW && s.contains(o.at())) {
						Openings.window(d, s, o.at(), y + 1, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, true));
					}
				}
			}
		}
		RoofForm form = d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.FLAT, RoofForm.PAGODA);
		if (form == RoofForm.FLAT) {
			Roofs.flat(d, x0, z0, x1, z1, b.plateY(), true);
			return null;
		}
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(Math.min(1, Roofs.Opts.of(d).eaves()), Math.min(1, Roofs.Opts.of(d).gableOverhang()));
		return Roofs.build(d, x0, z0, x1, z1, b.plateY(), form, ridge, o, false, false).roof();
	}

	/**
	 * A granary on staddle stones: a little boarded store on four stone mushrooms (a post of wall stone under a cap
	 * slab) so rats cannot climb in, its floor at {@code y + 2}, a steep roof, steps up to its door that stop short of
	 * the doorstep. {@code doorSide} is the wall the door is in.
	 */
	public static Roofs.Roof granary(Draft d, int x0, int z0, int x1, int z1, int y, Direction doorSide) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat frame = d.mat(Role.FRAME);
		Mat floor = d.mat(Role.FLOOR);
		int fy = y + 2;
		// the staddles
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				int g = d.site().ground(c[0], c[1]);
				for (int yy = Math.min(g - 1, y - 1); yy < y; yy++) {
					d.set(c[0], yy, c[1], stone.full());
				}
				d.set(c[0], y, c[1], stone.has(Form.WALL) ? stone.wall() : stone.full());
				d.set(c[0], y + 1, c[1], stone.has(Form.SLAB) ? stone.slab(SlabType.TOP) : stone.full());
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, fy - 1, z0, d.mat(Role.BEAM).log(Direction.Axis.X));
				d.set(x, fy - 1, z1, d.mat(Role.BEAM).log(Direction.Axis.X));
			}
		}
		Floors.boards(d, x0, z0 + 1, x1, z1 - 1, fy - 1);
		Body b = new Body(x0, z0, x1, z1, fy, new int[]{3}, false, false);
		Paint boards = boards(d, Direction.Axis.X);
		for (Side side : b.sides(0)) {
			for (int i = 0; i <= side.last(); i++) {
				int x = side.x(i);
				int z = side.z(i);
				boolean corner = side.isCorner(i);
				Paint p = corner ? frame.log(Direction.Axis.Y) : boards(d, side.axis());
				d.inStage(StageKind.WALLS, () -> {
					for (int yy = fy; yy < fy + 3; yy++) {
						d.set(x, yy, z, p);
					}
				});
			}
		}
		d.carve(x0 + 1, fy, z0 + 1, x1 - 1, fy + 1, z1 - 1);
		Side ds = b.side(0, doorSide);
		int mid = ds.middle();
		Openings.door(d, ds, ds.column(mid), fy, 1, new Openings.DoorOpts(false, false, false));
		// steps up to the door: a stair and a stone block, the last step left open (a rat cannot jump it)
		int sx = ds.outX(mid, 1);
		int sz = ds.outZ(mid, 1);
		if (sx >= 0 && sz >= 0 && sx < d.frontage() && sz < d.depth()) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				int g = d.site().ground(sx, sz);
				for (int yy = Math.min(g - 1, y - 1); yy < y + 1; yy++) {
					d.set(sx, yy, sz, stone.full());
				}
				d.set(sx, y + 1, sz, stone.stairsOrSlab(doorSide.getOpposite(), Half.BOTTOM));
			}
			int s2x = ds.outX(mid, 2);
			int s2z = ds.outZ(mid, 2);
			if (s2x >= 0 && s2z >= 0 && s2x < d.frontage() && s2z < d.depth()) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					int g = d.site().ground(s2x, s2z);
					for (int yy = Math.min(g - 1, y - 1); yy < y; yy++) {
						d.set(s2x, yy, s2z, stone.full());
					}
					d.set(s2x, y, s2z, stone.stairsOrSlab(doorSide.getOpposite(), Half.BOTTOM));
				}
			}
		}
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(Math.max(1.0, Roofs.Opts.of(d).pitch())).withEaves(1, 1);
		RoofForm form = d.habits().roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.PAGODA);
		Direction.Axis ridge = (x1 - x0) >= (z1 - z0) ? Direction.Axis.X : Direction.Axis.Z;
		Roofs.Roof roof = Roofs.build(d, x0, z0, x1, z1, b.plateY(), form, ridge, o, false, false).roof();
		// sacks of grain inside
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x < x1; x++) {
				int z = z1 - 1;
				if (d.isEmpty(x, fy, z) && doorSide != Dirs.BACK) {
					d.set(x, fy, z, Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
		}
		return roof;
	}

	/**
	 * A hen house: a boarded box on short legs with a slab roof, a hatch and a ramp of a trapdoor down to the run, the
	 * run fenced in front. {@code facing} is the way the hatch looks.
	 */
	public static void coop(Draft d, int x, int y, int z, Direction facing) {
		Mat fence = d.mat(Role.FENCE);
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		Direction across = Dirs.right(facing);
		int[][] box = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
		// the box: 2 × 2 on legs, its floor at y + 1
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : box) {
				int px = x + Dirs.dx(across) * c[0] - Dirs.dx(facing) * c[1];
				int pz = z + Dirs.dz(across) * c[0] - Dirs.dz(facing) * c[1];
				d.set(px, y, pz, frame.fence().orElse(fence.fence()));
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : box) {
				int px = x + Dirs.dx(across) * c[0] - Dirs.dx(facing) * c[1];
				int pz = z + Dirs.dz(across) * c[0] - Dirs.dz(facing) * c[1];
				d.set(px, y + 1, pz, boards(d, across.getAxis()));
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : box) {
				int px = x + Dirs.dx(across) * c[0] - Dirs.dx(facing) * c[1];
				int pz = z + Dirs.dz(across) * c[0] - Dirs.dz(facing) * c[1];
				d.set(px, y + 2, pz, c[1] == 0 ? roof.stairsOrSlab(facing.getOpposite(), Half.BOTTOM) : roof.slab(SlabType.TOP));
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// the hatch and its ramp
			d.set(x, y + 1, z, d.mat(Role.SHUTTER).trapdoor(facing, Half.BOTTOM, true));
			int rx = x + Dirs.dx(facing);
			int rz = z + Dirs.dz(facing);
			if (rx >= 0 && rz >= 0 && rx < d.frontage() && rz < d.depth() && d.isAbsent(rx, y, rz)) {
				d.set(rx, y, rz, d.mat(Role.FURNITURE).stairsOrSlab(facing.getOpposite(), Half.BOTTOM));
			}
		}
	}

	/** A door in a wall as a stage-ordered pair (for doors cut after the walls). */
	static void door(Draft d, int x, int y, int z, Direction facing) {
		d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), facing,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
	}

	/** Barrels standing against a wall, upright, with a lid of a trapdoor on one. */
	public static void stores(Draft d, int x, int y, int z, Direction run, int n) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < n; i++) {
				d.set(x + Dirs.dx(run) * i, y, z + Dirs.dz(run) * i, States.facing(Blocks.BARREL, Direction.UP));
			}
		}
	}
}
