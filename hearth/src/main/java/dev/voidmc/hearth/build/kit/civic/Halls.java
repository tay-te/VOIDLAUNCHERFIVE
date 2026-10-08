package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Parts every hall uses: its floor, a portal with a hood over its door, double doors, the boundary of its yard with a
 * roofed gate, string courses and the like.
 */
public final class Halls {
	private Halls() {
	}

	/** A floor at block row y over a rectangle: stone paving in masonry halls, boards in timber ones; only where empty. */
	public static void floor(Draft d, int x0, int z0, int x1, int z1, int y, boolean stone) {
		Mat m = d.mat(stone ? Role.PAVING : Role.FLOOR);
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

	/** Whether the wall kind is masonry (stone, brick, plaster, adobe). */
	public static boolean masonry(Draft d) {
		Habits.WallKind k = d.habits().walls().kind();
		return k == Habits.WallKind.STONE || k == Habits.WallKind.BRICK || k == Habits.WallKind.PLASTER || k == Habits.WallKind.ADOBE;
	}

	/**
	 * A door at column {@code col} of wall {@code s} (one leaf) standing at y, framed by a portal one block out from the
	 * wall: two posts or colonnettes, an arched head, and a little gabled hood of the roof, with lanterns either side
	 * from towns up. The portal is only drawn where the row outside is in the plot; the door is registered as the main
	 * entrance either way.
	 */
	public static void portal(Draft d, Side s, int col, int y, Tradition t, int tier) {
		Openings.door(d, s, col, y, 1, new Openings.DoorOpts(false, false, true));
		int i = s.index(col);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		boolean timber = !t.masonry() && !masonry(d);
		Direction right = s.along();
		Direction left = right.getOpposite();
		Direction in = s.out().getOpposite();
		int[] cols = {i - 1, i + 1};
		boolean room = s.outZ(i, 1) >= 1 || s.out() != Dirs.FRONT;
		for (int c : cols) {
			int ox = s.outX(c, 1);
			int oz = s.outZ(c, 1);
			if (!Walls.inPlot(d, ox, oz) || c < 0 || c > s.last()) {
				room = false;
			}
		}
		if (!room) {
			// on the front row the portal's posts would bar the way along the street: a hood over the door alone
			if (y + 3 >= 3) {
				Openings.hood(d, s, i - 1, i + 1, y + 3);
			}
			return;
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int c : cols) {
				int ox = s.outX(c, 1);
				int oz = s.outZ(c, 1);
				for (int yy = Math.min(y, d.site().ground(ox, oz)) - 1; yy < y; yy++) {
					d.set(ox, yy, oz, d.mat(Role.FOUNDATION).full());
				}
				Paint post = timber ? frame.log(Direction.Axis.Y) : accent.has(Form.WALL) ? accent.wall() : accent.full();
				d.set(ox, y, oz, timber ? post : accent.full());
				d.set(ox, y + 1, oz, post);
				d.set(ox, y + 2, oz, timber ? d.mat(Role.BEAM).log(s.axis()) : accent.full());
			}
			int mx = s.outX(i, 1);
			int mz = s.outZ(i, 1);
			// the head over the doorway: a round arch in masonry, a beam with knee braces in timber
			if (timber) {
				d.set(mx, y + 2, mz, d.mat(Role.BEAM).log(s.axis()));
			} else {
				d.set(mx, y + 2, mz, accent.slab(SlabType.TOP));
			}
		}
		// the hood: a little gable of the roof over the portal
		try (var st = d.stage(StageKind.ROOF)) {
			int hy = y + 3;
			d.set(s.outX(i - 1, 1), hy, s.outZ(i - 1, 1), roof.stairsOrSlab(right, Half.BOTTOM));
			d.set(s.outX(i + 1, 1), hy, s.outZ(i + 1, 1), roof.stairsOrSlab(left, Half.BOTTOM));
			d.set(s.outX(i, 1), hy, s.outZ(i, 1), roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : roof.full());
			d.set(s.outX(i, 1), hy + 1, s.outZ(i, 1), d.mat(Role.ROOF_TRIM).has(Form.SLAB) ? d.mat(Role.ROOF_TRIM).slab(SlabType.BOTTOM)
				: roof.slab(SlabType.BOTTOM));
		}
		if (tier >= 3) {
			Openings.wallLantern(d, s, i - 2, y + 1);
			Openings.wallLantern(d, s, i + 2, y + 1);
		}
	}

	/**
	 * An iwan: the middle of a front carried a block forward and up past the roof (to {@code top}) as a frontispiece of
	 * {@code 2 * half + 1} columns centred on {@code col}, a tall pointed arch recessed into it with the door at its back,
	 * jambs and a crest of the wall-accent stone. The door is registered as the main entrance. Only drawn where the row
	 * outside is in the plot; otherwise the door alone.
	 */
	public static void iwan(Draft d, Side s, int col, int y, int top, int half) {
		Openings.door(d, s, col, y, 1, new Openings.DoorOpts(false, false, true));
		int i = s.index(col);
		int fz = s.outZ(i, 1);
		if (s.out() == Dirs.FRONT && fz < 1 || i - half < 0 || i + half > s.last()) {
			return;
		}
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Direction right = s.along();
		Direction left = right.getOpposite();
		int open = Math.max(4, Math.min(5, top - y - 2));
		try (var st = d.stage(StageKind.WALLS)) {
			for (int k = -half; k <= half; k++) {
				int x = s.outX(i + k, 1);
				int z = s.outZ(i + k, 1);
				for (int yy = Math.min(y, d.site().ground(x, z)) - 1; yy < y; yy++) {
					d.set(x, yy, z, d.mat(Role.FOUNDATION).full());
				}
				for (int yy = y; yy <= top; yy++) {
					boolean recess = Math.abs(k) <= half - 1 && yy < y + open;
					boolean jamb = Math.abs(k) == half;
					d.set(x, yy, z, recess ? Paint.of(Blocks.AIR.defaultBlockState()) : jamb || yy == top ? accent.full() : wall.full());
				}
				// a crest of merlons on the frontispiece
				if (Math.abs(k) % 2 == half % 2 && d.allowed(x, top + 1, z)) {
					d.set(x, top + 1, z, accent.has(Form.SLAB) ? accent.slab(SlabType.BOTTOM) : accent.full());
				}
			}
			// the pointed head of the recess: shoulders a course under its top, the apex a course above them
			int hy = y + open - 1;
			d.set(s.outX(i - (half - 1), 1), hy, s.outZ(i - (half - 1), 1), accent.stairsOrSlab(left, Half.TOP));
			d.set(s.outX(i + (half - 1), 1), hy, s.outZ(i + (half - 1), 1), accent.stairsOrSlab(right, Half.TOP));
			d.set(s.outX(i, 1), y + open, s.outZ(i, 1), Paint.of(Blocks.AIR.defaultBlockState()));
			d.set(s.outX(i, 1), y + open + 1, s.outZ(i, 1), d.mat(Role.ACCENT).full());
		}
		Openings.wallLantern(d, s.shifted(1), i - half - 1, y + 1);
		Openings.wallLantern(d, s.shifted(1), i + half + 1, y + 1);
	}

	/**
	 * Double doors at columns {@code col, col + 1} of wall {@code s}, standing at y, under a lintel; the first double door
	 * drawn is the main entrance when {@code main}.
	 */
	public static void doubleDoor(Draft d, Side s, int col, int y, boolean main) {
		Openings.door(d, s, col, y, 2, new Openings.DoorOpts(false, false, main));
	}

	/**
	 * The boundary of a yard: the style's fence (a wall, a hedge) along the front row {@code z0} from x0 to x1 and down the
	 * plot's edge on the side away from the building, with a roofed gate in the front run.
	 */
	public static void yardEdge(Draft d, int x0, int z0, int x1, int z1, int y, Direction toBuilding) {
		if (z0 < 0 || x1 - x0 < 2) {
			return;
		}
		List<int[]> run = new ArrayList<>();
		int gate = (x1 - x0) / 2;
		int gateAt = -1;
		for (int x = x0; x <= x1; x++) {
			if (d.site().isWater(x, z0)) {
				continue;
			}
			if (x == x0 + gate) {
				gateAt = run.size();
			}
			run.add(new int[]{x, z0});
		}
		Garden.fence(d, run, gateAt, Dirs.FRONT);
		int edgeX = toBuilding == Dirs.LEFT ? x1 : x0;
		if (edgeX == 0 || edgeX == d.frontage() - 1) {
			List<int[]> side = new ArrayList<>();
			for (int z = z0 + 1; z <= z1; z++) {
				if (!d.site().isWater(edgeX, z)) {
					side.add(new int[]{edgeX, z});
				}
			}
			Garden.fence(d, side, -1, Dirs.FRONT);
		}
		lychgate(d, x0 + gate, z0, y);
	}

	/** A roofed gate over a yard's gateway at (x, z): two posts and a little gable of the roof over them. */
	public static void lychgate(Draft d, int x, int z, int y) {
		Mat frame = d.mat(Role.FRAME);
		Mat roof = d.mat(Role.ROOF);
		int g = Garden.groundAt(d, x, z);
		if (Math.abs(g - y) > 1 || x - 1 < 0 || x + 1 >= d.frontage()) {
			return;
		}
		for (int px : new int[]{x - 1, x + 1}) {
			if (Math.abs(Garden.groundAt(d, px, z) - g) > 0) {
				return;
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int px : new int[]{x - 1, x + 1}) {
				for (int yy = g; yy < g + 3; yy++) {
					d.set(px, yy, z, frame.log(Direction.Axis.Y));
				}
			}
			d.set(x, g + 2, z, d.mat(Role.BEAM).log(Direction.Axis.X));
			d.set(x - 1, g + 3, z, roof.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
			d.set(x + 1, g + 3, z, roof.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			d.set(x, g + 3, z, roof.full());
			d.set(x, g + 4, z, roof.slab(SlabType.BOTTOM));
			if (d.isEmpty(x, g, z)) {
				d.set(x, g, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** A horizontal course of the wall-accent stone along a wall at height y (a string course), where wall is drawn. */
	public static void stringCourse(Draft d, Side s, int y) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < s.length(); i++) {
				if (Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
					d.set(s.x(i), y, s.z(i), accent.full());
				}
			}
		}
	}

	/**
	 * A lantern hung on a chain from whatever is drawn over (x, z): the first block found above {@code yMin + 2}, the
	 * lantern no lower than {@code yMin + 2} (head room), at most {@code maxLinks} links. Nothing when nothing is above
	 * within reach.
	 */
	public static void lanternUnder(Draft d, int x, int yMin, int z, int maxLinks) {
		for (int y = yMin + 3; y <= yMin + 3 + maxLinks + 8; y++) {
			if (!d.isEmpty(x, y, z)) {
				net.minecraft.world.level.block.state.BlockState above = d.get(x, y, z);
				if (above == null || above.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
					net.minecraft.core.BlockPos.ZERO).isEmpty()) {
					return;
				}
				int links = Math.max(0, Math.min(maxLinks, y - 1 - (yMin + 2)));
				dev.voidmc.hearth.build.kit.Furniture.hangingLantern(d, x, y, z, links);
				return;
			}
		}
	}

	/** A post for lamps and flags: the style's fence if it has one, else the railing's. */
	public static Paint post(Draft d) {
		Mat f = d.mat(Role.FENCE);
		if (f.has(Form.FENCE)) {
			return f.fence();
		}
		Mat r = d.mat(Role.RAILING);
		return r.has(Form.FENCE) ? r.fence() : d.mat(Role.FRAME).log(Direction.Axis.Y);
	}

	/** A lamp on a post (the style's lantern on a fence post), standing on the ground at (x, y, z). */
	public static void lamp(Draft d, int x, int y, int z, int height) {
		if (x < 0 || x >= d.frontage() || z < 0 || z >= d.depth() || d.site().isWater(x, z)) {
			return;
		}
		int g = Groundwork.surface(d, x, z);
		if (Math.abs(g - y) > 1 || !d.isEmpty(x, g, z)) {
			return;
		}
		y = g;
		Paint p = post(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, p);
			}
			d.set(x, y + height, z, net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState().trySetValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, false));
		}
	}

	/**
	 * Furniture for a flat roof inside its parapet: an awning of canvas on four posts over a bench, pots along
	 * the parapet, a VIEWPOINT under the awning. {@code r} is the roof's wall rectangle, {@code deck} its standing level.
	 */
	public static void roofTerrace(Draft d, int[] r, int deck, boolean right) {
		int w = r[2] - r[0] - 1;
		int dd = r[3] - r[1] - 1;
		if (w < 4 || dd < 4) {
			return;
		}
		int ax0 = right ? r[2] - 4 : r[0] + 1;
		int ax1 = ax0 + 3;
		int az0 = r[1] + 1;
		int az1 = az0 + 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : new int[][]{{ax0, az0}, {ax1, az0}, {ax0, az1}, {ax1, az1}}) {
				if (!d.isEmpty(c[0], deck, c[1])) {
					return;
				}
			}
			for (int[] c : new int[][]{{ax0, az0}, {ax1, az0}, {ax0, az1}, {ax1, az1}}) {
				d.set(c[0], deck, c[1], post(d));
				d.set(c[0], deck + 1, c[1], post(d));
			}
		}
		// canvas of one colour, not the town's every cloth cell by cell
		dev.voidmc.hearth.build.kit.townhome.Life.awning(d, ax0, az0, ax1, az1, deck + 2);
		dev.voidmc.hearth.build.kit.Furniture.chair(d, ax0 + 1, deck, az0 + 1, Dirs.BACK);
		d.poi(dev.voidmc.hearth.api.PoiType.VIEWPOINT, ax0 + 2, deck, az0 + 1, Dirs.FRONT);
		for (int x = r[0] + 2; x < r[2] - 1; x += 3) {
			if (d.isEmpty(x, deck, r[3] - 1)) {
				Garden.pot(d, x, deck, r[3] - 1);
			}
		}
	}

	/**
	 * A cornice: a course of upside-down stairs of the wall-accent stone standing a block out from the walls of
	 * {@code r} (a rectangle {x0, z0, x1, z1}) at height y, corners mitred by the shape resolver; only where the cell
	 * outside is free and in the plot (or over the street, high enough).
	 */
	public static void cornice(Draft d, int[] r, int y) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		if (!accent.has(Form.STAIRS)) {
			return;
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (Side s : Side.of(r[0], r[1], r[2], r[3])) {
				for (int i = -1; i <= s.length(); i++) {
					int x = s.outX(i, 1);
					int z = s.outZ(i, 1);
					boolean inside = i >= 0 && i < s.length();
					if (!d.allowed(x, y, z) || !d.isEmpty(x, y, z) || !inside && (i < -1 || i > s.length())) {
						continue;
					}
					if (!inside) {
						continue;
					}
					d.set(x, y, z, accent.stairs(s.out().getOpposite(), Half.TOP));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------- rails, sills, landings

	/**
	 * A rail someone leans on and cannot step over: the given wall-capable material's wall (a balustrade of stone), else
	 * the railing's fence, else a post. A slab or a full block will not do: a slab is a step and a block a jump.
	 */
	public static Paint rail(Draft d, Mat stone) {
		if (stone != null && stone.has(Form.WALL)) {
			return stone.wall();
		}
		Mat r = d.mat(Role.RAILING);
		return r.has(Form.FENCE) ? r.fence() : post(d);
	}

	/** {@link #rail(Draft, Mat)} in a tower's dressing. */
	public static Paint rail(Draft d, dev.voidmc.hearth.build.kit.landmark.Stone stone) {
		if (stone != null && stone.has(Form.WALL)) {
			return stone.wall();
		}
		return rail(d, (Mat) null);
	}

	/**
	 * Sills in a belfry's openings: every open cell of the ring {@code x0..x1 × z0..z1} at row y (the row someone standing
	 * on the belfry floor walks into) takes a rail, as a real belfry has louvres or a balustrade: nobody walks out onto the
	 * roofs from the bells.
	 */
	public static void sills(Draft d, int x0, int z0, int x1, int z1, int y, Paint rail) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				if (d.isEmpty(c[0], y, c[1]) && d.allowed(c[0], y, c[1])) {
					d.set(c[0], y, c[1], rail);
				}
			}
		}
	}

	/**
	 * Landings up a tower's shaft, so its ladder never climbs past a drop of four: floors over the interior
	 * {@code x0..x1 × z0..z1} between the floor row {@code floorY} (the row under the foot) and the top floor row
	 * {@code topY}, evenly spaced at most five apart (never closer than three, which keeps two rows of head room), each
	 * holed at the ladder's cell {@code (lx, lz)} and given a chest or a barrel in the corner farthest from it (a stair
	 * room with nothing in it reads as forgotten). Only empty cells are floored. Returns the landing rows.
	 */
	public static int[] landings(Draft d, int x0, int z0, int x1, int z1, int floorY, int topY, int lx, int lz, Paint floor) {
		int span = topY - floorY;
		int n = (span + 4) / 5;
		if (n <= 1 || span < 6) {
			return new int[0];
		}
		int[] rows = new int[n - 1];
		int fx = Math.abs(lx - x0) >= Math.abs(lx - x1) ? x0 : x1;
		int fz = Math.abs(lz - z0) >= Math.abs(lz - z1) ? z0 : z1;
		for (int k = 1; k < n; k++) {
			int y = floorY + (int) Math.round(k * span / (double) n);
			rows[k - 1] = y;
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						if ((x != lx || z != lz) && d.isEmpty(x, y, z)) {
							d.set(x, y, z, floor);
						}
					}
				}
			}
			if ((fx != lx || fz != lz) && d.isEmpty(fx, y + 1, fz) && d.isEmpty(fx, y + 2, fz)) {
				Direction face = fx == lx ? (fz < lz ? Dirs.BACK : Dirs.FRONT) : fx < lx ? Dirs.RIGHT : Dirs.LEFT;
				if ((k & 1) == 1) {
					dev.voidmc.hearth.build.kit.Furniture.barrel(d, fx, y + 1, fz, face);
				} else {
					dev.voidmc.hearth.build.kit.Furniture.chest(d, fx, y + 1, fz, face, List.of(new Object[]{"minecraft:candle", 4},
						new Object[]{"minecraft:string", 6}));
				}
			}
		}
		return rows;
	}

	/**
	 * The last touches every civic and law building takes before its ground is cleared: a lintel over every doorway, a rail
	 * in the foot of every light a body could walk out of, and a parapet along made ground standing over a fall
	 * ({@link #lintels}, {@link #walkThroughLights}, {@link Grounds#edgeRails}); the roofed corners nobody furnished take
	 * a bench or a rain butt ({@link Nooks#furnish}); then whatever a body can still walk to beside a fall is guarded
	 * ({@link Falls#guard}).
	 */
	public static void finish(Draft d) {
		lintels(d);
		walkThroughLights(d);
		Grounds.edgeRails(d);
		Nooks.furnish(d);
		Falls.guard(d);
	}

	/**
	 * Keeps the cells of a room at the open side of a stairwell or a ladder's hatch free of furniture: a bed, a chest or a
	 * table there is a step up from which the next step is the drop. Call before furnishing it, once the stair is cut.
	 */
	public static Rooms.Room keepOffWells(Draft d, Rooms.Room room) {
		int y = room.y;
		for (int z = room.z0; z <= room.z1; z++) {
			for (int x = room.x0; x <= room.x1; x++) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					// (where the well falls three or more: over a flight's upper steps a bed beside it is harmless)
					if (d.isEmpty(nx, y, nz) && d.isEmpty(nx, y - 1, nz) && d.isEmpty(nx, y - 2, nz) && d.isEmpty(nx, y - 3, nz)
						&& !d.isAbsent(nx, y - 1, nz)) {
						room.keepArea(x, z, x, z);
					}
				}
			}
		}
		return room;
	}

	/** A cell of a wall's line at row y beside a light: the wall, or the next light of a wide window (a lattice or a sill under it). */
	private static boolean inWallLine(Draft d, int x, int y, int z) {
		net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
		return d.isSolid(x, y, z) || floor(d, x, y - 1, z) && s != null && !s.isAir()
			|| s != null && s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
	}

	/** Something to stand on at y: a full block, an upper slab or a stair (a floor, a gallery's boards, a bench). */
	private static boolean floor(Draft d, int x, int y, int z) {
		if (d.isSolid(x, y, z)) {
			return true;
		}
		net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) != SlabType.BOTTOM);
	}

	/**
	 * Every latticed light a body could walk through (a trapdoor lattice in a wall over a solid sill, with floor and head
	 * room on one side of it and nothing in the way on the other) takes a rail in its foot instead, as
	 * {@link #windowRails} does for one ring: nobody steps out of an upper room onto the eaves or a wing's roof.
	 */
	public static void walkThroughLights(Draft d) {
		Paint rail = rail(d, (Mat) null);
		java.util.List<int[]> hits = new java.util.ArrayList<>();
		for (long k : d.keys()) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			net.minecraft.world.level.block.state.BlockState st = d.get(x, y, z);
			// (over a sill: a full block, or a stair or a slab laid as one, a roof's edge run under it)
			if (st == null || !(st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) || d.isEmpty(x, y - 1, z)
				|| d.get(x, y - 1, z).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
				continue;
			}
			for (Direction across : new Direction[]{Direction.EAST, Direction.SOUTH}) {
				Direction along = across.getClockWise();
				// in a wall: solid (or a light's frame) either side of it along the wall
				boolean wall = inWallLine(d, x + along.getStepX(), y, z + along.getStepZ()) && inWallLine(d, x - along.getStepX(), y, z - along.getStepZ());
				if (!wall) {
					continue;
				}
				int ax = x + across.getStepX();
				int az = z + across.getStepZ();
				int bx = x - across.getStepX();
				int bz = z - across.getStepZ();
				// (open: head room, and nothing full at the feet: air, or a roof's stair or slab to step out onto)
				boolean aOpen = !d.isSolid(ax, y, az) && d.isEmpty(ax, y + 1, az);
				boolean bOpen = !d.isSolid(bx, y, bz) && d.isEmpty(bx, y + 1, bz);
				// (something to stand on level with the sill or a row below it: a floor, a slab, a bed or a bench, from which the
				// sill is a step up into the light)
				boolean aFloor = aOpen && (!d.isEmpty(ax, y - 1, az) || !d.isEmpty(ax, y - 2, az));
				boolean bFloor = bOpen && (!d.isEmpty(bx, y - 1, bz) || !d.isEmpty(bx, y - 2, bz));
				if (aOpen && bOpen && (aFloor || bFloor)) {
					hits.add(new int[]{x, y, z});
					break;
				}
			}
		}
		if (hits.isEmpty()) {
			return;
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int[] c : hits) {
				d.set(c[0], c[1], c[2], rail);
				// a tall light's next row too: a bench or a step before it would carry a body up over a rail of one
				net.minecraft.world.level.block.state.BlockState above = d.get(c[0], c[1] + 1, c[2]);
				if (above != null && above.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
					d.set(c[0], c[1] + 1, c[2], rail);
				}
			}
		}
	}

	/**
	 * A head over every doorway in the draft: where the cell over a door's upper leaf is open (a tall light's lattice or
	 * glass, or nothing) and it stands in a wall (solid either side of the door along its wall), that cell takes the wall's
	 * dressed stone. A doorway needs a lintel: without one, a body can climb on top of the door.
	 */
	public static void lintels(Draft d) {
		List<int[]> doors = new ArrayList<>();
		for (long k : d.keys()) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
			if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
				&& s.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				doors.add(new int[]{x, y, z, s.getValue(net.minecraft.world.level.block.DoorBlock.FACING).getAxis() == Direction.Axis.Z ? 0 : 1});
			}
		}
		Paint head = d.mat(Role.WALL_ACCENT).has(Form.FULL) ? d.mat(Role.WALL_ACCENT).full() : d.mat(Role.WALL).full();
		for (int[] c : doors) {
			int x = c[0];
			int y = c[1] + 2;
			int z = c[2];
			net.minecraft.world.level.block.state.BlockState above = d.get(x, y, z);
			boolean open = above == null || above.isAir() || above.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
				|| above.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock || above.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock;
			// the wall runs across the door's facing: x for a door facing along z
			int ax = c[3] == 0 ? 1 : 0;
			int az = c[3] == 0 ? 0 : 1;
			boolean inWall = d.isSolid(x - ax, y, z - az) || d.isSolid(x + ax, y, z + az) || d.isSolid(x - ax, y - 1, z - az) && d.isSolid(x + ax, y - 1, z + az);
			if (open && inWall && d.allowed(x, y, z)) {
				d.inStage(StageKind.WALLS, () -> d.set(x, y, z, head));
			}
		}
	}

	/**
	 * Low rails in the feet of latticed windows: on the wall ring {@code x0..x1 × z0..z1}, every window cell at row y filled
	 * with a trapdoor lattice (which a body walks through) over a solid sill takes the railing's fence instead, so the
	 * window of a raised or a karst hall is a balustraded light and nobody climbs out of it onto the eaves.
	 */
	public static void windowRails(Draft d, int x0, int z0, int x1, int z1, int y) {
		Paint rail = rail(d, (Mat) null);
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				net.minecraft.world.level.block.state.BlockState st = d.get(c[0], y, c[1]);
				if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && d.isSolid(c[0], y - 1, c[1])) {
					d.set(c[0], y, c[1], rail);
				}
			}
		}
	}

	/**
	 * Takes away every bed in {@code x0..x1 × z0..z1} on row y that nobody can get into: neither half has a cell beside it
	 * with a floor and two rows of air (a bed under the eaves' slope, or boxed in by its neighbours), or that stands at the
	 * open side of a stairwell. Returns how many beds stay.
	 */
	public static int dropBlockedBeds(Draft d, int x0, int z0, int x1, int z1, int y) {
		int kept = 0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
				if (s == null || !(s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
					|| s.getValue(net.minecraft.world.level.block.BedBlock.PART) != net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
					continue;
				}
				Direction f = s.getValue(net.minecraft.world.level.block.BedBlock.FACING);
				int hx = x + Dirs.dx(f);
				int hz = z + Dirs.dz(f);
				boolean ok = false;
				for (int[] c : new int[][]{{x, z}, {hx, hz}}) {
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = c[0] + Dirs.dx(dir);
						int nz = c[1] + Dirs.dz(dir);
						if ((nx != x || nz != z) && (nx != hx || nz != hz) && d.isEmpty(nx, y, nz) && d.isEmpty(nx, y + 1, nz) && d.isSolid(nx, y - 1, nz)) {
							ok = true;
						}
					}
				}
				// (nor a bed beside a stairwell's hole: whoever stands on it to make it stands over the drop)
				for (int[] c : new int[][]{{x, z}, {hx, hz}}) {
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = c[0] + Dirs.dx(dir);
						int nz = c[1] + Dirs.dz(dir);
						if (d.isEmpty(nx, y, nz) && d.isEmpty(nx, y - 1, nz) && d.isEmpty(nx, y - 2, nz) && !d.isAbsent(nx, y - 1, nz)) {
							ok = false;
						}
					}
				}
				if (ok) {
					kept++;
				} else {
					d.set(x, y, z, Blocks.AIR.defaultBlockState());
					d.set(hx, y, hz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		return kept;
	}

	/** The hinge for the left leaf of a pair. */
	static DoorHingeSide hinge(boolean leftLeaf) {
		return leftLeaf ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
	}

	/** Earth helper: the standing level the draft has made of a cell so far. */
	public static int surface(Draft d, int x, int z) {
		return Groundwork.surface(d, x, z);
	}
}
