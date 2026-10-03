package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
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

	/** The hinge for the left leaf of a pair. */
	static DoorHingeSide hinge(boolean leftLeaf) {
		return leftLeaf ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
	}

	/** Earth helper: the standing level the draft has made of a cell so far. */
	public static int surface(Draft d, int x, int z) {
		return Groundwork.surface(d, x, z);
	}
}
