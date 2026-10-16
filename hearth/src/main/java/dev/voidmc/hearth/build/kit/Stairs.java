package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Stairs folk can walk: straight flights, flights that turn on a landing (L and U), and a three-by-three spiral of slabs
 * for towers, each clearing three blocks of headroom over every step and cutting the stairwell through the floor above.
 * A ladder ({@link dev.voidmc.hearth.build.terrain.Groundwork#ladder}) only where none of these fits.
 */
public final class Stairs {
	private Stairs() {
	}

	/**
	 * A straight flight: {@code rise} steps from (x, y, z) climbing toward {@code dir}, {@code width} wide (extending to
	 * the right of {@code dir}). The first step stands at y (its top half a block up); the last at {@code y + rise - 1}.
	 * Headroom is cleared above every step (through floors). Returns the cell beyond the top step, at the top level.
	 */
	public static int[] straight(Draft d, int x, int y, int z, Direction dir, int rise, int width) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction across = Dirs.right(dir);
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = 0; i < rise; i++) {
				for (int w = 0; w < width; w++) {
					int px = x + Dirs.dx(dir) * i + Dirs.dx(across) * w;
					int pz = z + Dirs.dz(dir) * i + Dirs.dz(across) * w;
					int py = y + i;
					d.set(px, py, pz, wood.stairs(dir, Half.BOTTOM));
					for (int h = 1; h <= 3; h++) {
						d.set(px, py + h, pz, air);
					}
					// a closed string under every step that does not stand on something already
					if (d.isAbsent(px, py - 1, pz)) {
						d.set(px, py - 1, pz, wood.stairs(dir.getOpposite(), Half.TOP));
					}
				}
			}
		}
		return new int[]{x + Dirs.dx(dir) * rise, y + rise, z + Dirs.dz(dir) * rise};
	}

	/**
	 * A U-stair (switchback) in a 2 × (run + 2) well: up half the rise toward {@code dir} in the first lane (x, z), a
	 * landing two wide, then back in the lane beside it (toward {@code across}) to rise {@code rise} in all. Fits a
	 * stairwell two wide against a wall; stacked floor over floor it makes a stair tower.
	 */
	public static int[] switchback(Draft d, int x, int y, int z, Direction dir, int rise, Direction across) {
		int first = rise / 2;
		int second = rise - first;
		Mat wood = d.mat(Role.FURNITURE);
		int[] top = straight(d, x, y, z, dir, first, 1);
		// landing: two cells at the top of the first flight, level with it
		int lx = top[0];
		int lz = top[2];
		int ly = top[1];
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int w = 0; w < 2; w++) {
				int px = lx + Dirs.dx(across) * w;
				int pz = lz + Dirs.dz(across) * w;
				d.set(px, ly - 1, pz, wood.full());
				for (int h = 0; h <= 2; h++) {
					d.set(px, ly + h, pz, air);
				}
			}
		}
		int sx = lx + Dirs.dx(across) - Dirs.dx(dir);
		int sz = lz + Dirs.dz(across) - Dirs.dz(dir);
		return straight(d, sx, ly, sz, dir.getOpposite(), second, 1);
	}

	/**
	 * A spiral of slabs round a central post in a 3 × 3 square centred on (cx, cz), from standing level y0 up {@code rise}
	 * blocks: each of the eight ring cells is half a block above the last, so a full turn climbs four (one storey).
	 * Mobs walk slabs, so folk climb it like a stair.
	 */
	public static void spiral(Draft d, int cx, int cz, int y0, int rise, int startAt) {
		Mat wood = d.mat(Role.FURNITURE);
		Mat post = d.mat(Role.FRAME);
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int y = y0 - 1; y < y0 + rise; y++) {
				d.set(cx, y, cz, post.log(Direction.Axis.Y));
			}
			int steps = rise * 2;
			for (int k = 1; k <= steps; k++) {
				int[] c = ring[(startAt + k) % 8];
				int px = cx + c[0];
				int pz = cz + c[1];
				int half = k;
				int y = y0 + (half - 1) / 2;
				boolean top = (half & 1) == 0;
				d.set(px, y, pz, wood.slab(top ? SlabType.TOP : SlabType.BOTTOM));
				for (int h = 1; h <= 2; h++) {
					d.set(px, y + h, pz, air);
				}
			}
		}
	}

	/**
	 * The balustrade round a stairwell on the floor it arrives at: a straight flight from (x, y, z) climbing toward
	 * {@code dir}, {@code rise} steps, whose last steps come up through a hole in the floor standing at {@code y + rise}.
	 * A rail on the floor beside the hole on the {@code open} side (the side away from the wall the flight runs along),
	 * and one across the hole's lower end, wherever that floor cell is boarded and empty: nobody steps into the
	 * stairwell, everybody walks off its head. Returns how many rails went in.
	 */
	public static int well(Draft d, int x, int y, int z, Direction dir, int rise, Direction open) {
		int floor = y + rise;
		Mat rail = d.mat(Role.RAILING).has(dev.voidmc.hearth.build.style.Form.FENCE) ? d.mat(Role.RAILING)
			: dev.voidmc.hearth.build.kit.trade.Hall.woodFence(d);
		int n = 0;
		java.util.List<int[]> cells = new java.util.ArrayList<>();
		// the hole runs over the steps whose head room needs it (each from three rows under the floor up)
		int first = -1;
		for (int i = 0; i < rise - 1; i++) {
			int px = x + Dirs.dx(dir) * i;
			int pz = z + Dirs.dz(dir) * i;
			if (d.isEmpty(px, floor - 1, pz)) {
				if (first < 0) {
					first = i;
				}
				// a rail beside the hole only where a lane stays open beyond it (a rail must never close a passage)
				int ox = px + Dirs.dx(open);
				int oz = pz + Dirs.dz(open);
				if (lane(d, ox + Dirs.dx(open), floor, oz + Dirs.dz(open))) {
					cells.add(new int[]{ox, oz});
				}
			}
		}
		// across the hole's lower end, in line with the flight, where the lane beside it stays open
		if (first >= 1 && lane(d, x + Dirs.dx(dir) * (first - 1) + Dirs.dx(open), floor, z + Dirs.dz(dir) * (first - 1) + Dirs.dz(open))) {
			cells.add(new int[]{x + Dirs.dx(dir) * (first - 1), z + Dirs.dz(dir) * (first - 1)});
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int[] c : cells) {
				boolean door = false;
				for (Direction h : Dirs.HORIZONTAL) {
					BlockState b = d.get(c[0] + Dirs.dx(h), floor, c[1] + Dirs.dz(h));
					door |= b != null && b.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
				}
				if (!door && d.isEmpty(c[0], floor, c[1]) && d.isEmpty(c[0], floor + 1, c[1]) && d.isSolid(c[0], floor - 1, c[1])) {
					d.set(c[0], floor, c[1], rail.fence());
					n++;
				}
			}
		}
		return n;
	}

	/**
	 * The head room of every flight in a finished draft (the audit's rule: two blocks of air over every tread, so folk
	 * climb what the player climbs): over each tread of a run of two or more stairs climbing one way (not a roof's
	 * courses), what the furnishing or the light hung in the way (a lamp, a chain, a joist, a shelf) is taken away, and a
	 * floor board over the second row is cut where nothing stands on it. The landing beyond the top tread is cleared of
	 * props the same way. Returns how many cells were opened.
	 */
	public static int finish(Draft d) {
		java.util.List<long[]> treads = new java.util.ArrayList<>();
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			if ((c.stage() == StageKind.FLOORS || c.stage() == StageKind.INTERIOR) && s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
				&& s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == Half.BOTTOM) {
				treads.add(new long[]{Draft.key(x, y, z), s.getValue(net.minecraft.world.level.block.StairBlock.FACING).get2DDataValue()});
			}
		});
		int opened = 0;
		for (long[] t : treads) {
			int x = Draft.keyX(t[0]);
			int y = Draft.keyY(t[0]);
			int z = Draft.keyZ(t[0]);
			Direction f = Direction.from2DDataValue((int) t[1]);
			// part of a flight: a stair of the same run before it (one down, one back) or after it (one up, one on)
			if (!sameRun(d, x - Dirs.dx(f), y - 1, z - Dirs.dz(f), f) && !sameRun(d, x + Dirs.dx(f), y + 1, z + Dirs.dz(f), f)) {
				continue;
			}
			for (int h = 1; h <= 2; h++) {
				opened += open(d, x, y + h, z, h == 2) ? 1 : 0;
			}
			// the top tread: the floor beyond it free of props to step onto
			if (!sameRun(d, x + Dirs.dx(f), y + 1, z + Dirs.dz(f), f)) {
				for (int h = 1; h <= 2; h++) {
					int lx = x + Dirs.dx(f);
					int lz = z + Dirs.dz(f);
					Draft.Cell c = d.cell(lx, y + h, lz);
					if (c != null && c.stage() == StageKind.INTERIOR && !c.state().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
						net.minecraft.core.BlockPos.ZERO) && open(d, lx, y + h, lz, false)) {
						opened++;
					}
				}
				// a flight that turns with no landing (a stair one up before or beside its top tread, as in a corkscrew):
				// whoever steps up round the corner needs a third row of air over the top tread, so a board there is cut
				for (Direction side : Dirs.HORIZONTAL) {
					if (side != f.getOpposite()) {
						BlockState next = d.get(x + Dirs.dx(side), y + 1, z + Dirs.dz(side));
						if (next != null && next.getBlock() instanceof net.minecraft.world.level.block.StairBlock
							&& next.getValue(net.minecraft.world.level.block.StairBlock.HALF) == Half.BOTTOM
							&& d.isEmpty(x, y + 1, z) && d.isEmpty(x, y + 2, z) && open(d, x, y + 3, z, true)) {
							opened++;
							break;
						}
					}
				}
			}
		}
		return opened;
	}

	private static boolean sameRun(Draft d, int x, int y, int z, Direction f) {
		Draft.Cell c = d.cell(x, y, z);
		return c != null && (c.stage() == StageKind.FLOORS || c.stage() == StageKind.INTERIOR) && c.state().getBlock() instanceof net.minecraft.world.level.block.StairBlock
			&& c.state().getValue(net.minecraft.world.level.block.StairBlock.HALF) == Half.BOTTOM
			&& c.state().getValue(net.minecraft.world.level.block.StairBlock.FACING) == f;
	}

	/**
	 * Opens a cell over a tread: a prop or a fitting (interior, exterior or openings stage, or a framing beam: a joist)
	 * goes; a floor board ({@code board}: the second row over the tread) is cut only where nothing stands on it and it
	 * carries nothing hung under it. Walls, roofs and doors stay.
	 */
	private static boolean open(Draft d, int x, int y, int z, boolean board) {
		Draft.Cell c = d.cell(x, y, z);
		if (c == null || dev.voidmc.hearth.build.kit.light.Spaces.passable(c.state())) {
			return false;
		}
		BlockState s = c.state();
		if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || s.getBlock() instanceof net.minecraft.world.level.block.BedBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.StairBlock && s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == Half.BOTTOM) {
			return false;
		}
		StageKind st = c.stage();
		// nothing rests on it and nothing hangs from it (a carpet, a lantern on a chain)
		BlockState above = d.get(x, y + 1, z);
		BlockState below = d.get(x, y - 1, z);
		if (above != null && !above.isAir() && !(above.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
			&& !dev.voidmc.hearth.build.kit.light.Spaces.hangs(above) || below != null && dev.voidmc.hearth.build.kit.light.Spaces.hangs(below)) {
			return false;
		}
		boolean fitting = st == StageKind.INTERIOR && !s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			|| st == StageKind.INTERIOR && dev.voidmc.hearth.build.kit.light.Spaces.hangs(s)
			|| st == StageKind.FRAME && s.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock && !d.isSolid(x, y - 1, z)
			&& !d.isSolid(x, y + 1, z);
		boolean floorBoard = board && st == StageKind.FLOORS && d.isEmpty(x, y + 1, z) && d.isEmpty(x, y + 2, z);
		if (!fitting && !floorBoard) {
			return false;
		}
		d.air(x, y, z);
		return true;
	}

	/** A boarded cell with two rows of air over it at a standing level: somewhere to walk. */
	private static boolean lane(Draft d, int x, int y, int z) {
		return d.isSolid(x, y - 1, z) && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z);
	}

	/** A ladder up the wall behind (x, z) from y0 to y1, facing out of the wall. */
	public static void ladder(Draft d, int x, int z, int y0, int y1, Direction facing) {
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int y = y0; y <= y1; y++) {
				d.set(x, y, z, States.facing(Blocks.LADDER, facing));
			}
		}
	}
}
