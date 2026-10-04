package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Bohemian village (the temperate culture read as the countryside of Kingdom Come: Deliverance II): steep roofs of
 * shingle or thatch, half-hipped as often as gabled, their gables boarded in upright planks over a little pent roof (the
 * {@code podlomenice}) that runs across the gable's foot; whitewashed walls on a fieldstone plinth; a bench by the door,
 * wattle fences round the garden and the yard, a roofed gate into a farmstead.
 */
public final class Bohemia {
	private Bohemia() {
	}

	/** Whether this draft is built the Bohemian way. */
	public static boolean is(Draft d) {
		return d.style().culture() == Culture.TEMPERATE;
	}

	/**
	 * The village's roof forms: mostly steep gables, a quarter half-hips (laid at the culture's own pitch: a hipped end
	 * as steep as the gables would step like a ziggurat), no cross gables or dormers.
	 */
	public static RoofForm form(Draft d) {
		return d.random().nextDouble() < 0.25 ? RoofForm.HALF_HIP : RoofForm.GABLE;
	}

	/**
	 * A steep pitch for a gable (two blocks of rise to one, a stair on a block, or three to two where the plot's height
	 * will not take that) whose eaves stand at {@code plate} and whose slope runs {@code run} blocks from the wall line to
	 * the ridge, kept under the plot's height with room for a chimney over the ridge; a hipped form keeps {@code base}.
	 */
	public static double pitch(Draft d, double base, int plate, double run, RoofForm form) {
		if (form == RoofForm.HALF_HIP || form == RoofForm.HIP) {
			return base;
		}
		double fits = (d.maxHeight() - 3 - (plate + 1)) / Math.max(1.0, run);
		for (double p : new double[]{2.0, 1.5}) {
			if (p <= fits && p > base) {
				return p;
			}
		}
		return base;
	}

	/**
	 * Under a steep roof the ridge course stands more than a block over the courses either side of it: fills the cells
	 * under each ridge block down to the courses beside it with the roofing, so the ridge is carried (it would otherwise
	 * hang over the loft on nothing).
	 */
	public static void carryRidge(Draft d, Roofs.Roof roof) {
		Mat cover = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					int y = roof.surfaceY(x, z);
					if (y == Integer.MIN_VALUE) {
						continue;
					}
					// the lowest neighbouring course across either axis
					int low = Integer.MAX_VALUE;
					boolean ridge = true;
					for (Direction dir : Dirs.HORIZONTAL) {
						int n = roof.surfaceY(x + Dirs.dx(dir), z + Dirs.dz(dir));
						if (n == Integer.MIN_VALUE) {
							continue;
						}
						if (n > y) {
							ridge = false;
						}
						low = Math.min(low, n);
					}
					if (!ridge || low == Integer.MAX_VALUE || y - low < 2) {
						continue;
					}
					for (int yy = y - 1; yy >= low && d.isEmpty(x, yy, z); yy--) {
						d.set(x, yy, z, cover.full());
					}
					// a slab cap in the top half of its block would leave a slot of sky over the roofing under it
					BlockState cap = d.get(x, y, z);
					if (cap != null && cap.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
						&& cap.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == SlabType.TOP) {
						d.set(x, y, z, cover.full());
					}
				}
			}
		}
	}

	/**
	 * Re-lays the plinth course of the walls round a rectangle (levels y0..y1) in fieldstone: wherever the plinth's own
	 * stone stands, the culture's foundation rubble (cobble, mossy cobble, field stone) takes its place.
	 */
	public static void fieldstone(Draft d, int x0, int z0, int x1, int z1, int y0, int y1) {
		Set<Block> plinth = new HashSet<>();
		for (var e : d.mat(Role.PLINTH).palette().entries()) {
			Block b = e.material().block(Form.FULL);
			if (b != null) {
				plinth.add(b);
			}
		}
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = y0; y <= y1; y++) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					BlockState st = d.get(c[0], y, c[1]);
					if (st != null && plinth.contains(st.getBlock())) {
						d.set(c[0], y, c[1], stone.full());
					}
				}
			}
		}
	}

	/**
	 * Boards the gable ends of a pitched roof over the rectangle {@code r} (x0, z0, x1, z1, its walls) whose eaves stand
	 * at {@code plate}: the wall above the plate under the roof is clad in upright boards (stripped timber, the grain
	 * running up), the middle board a darker post, any window in it kept; and at the gable's foot a pent of the roofing
	 * runs across outside the wall under the barge, so the boarded triangle sits on a little roof of its own.
	 */
	public static void gables(Draft d, Roofs.Roof roof, Direction.Axis ridge, int[] r, int plate) {
		boolean alongX = ridge == Direction.Axis.X;
		// dark tarred boards under shingle (against the pale walls and the brown roof), warm ones under golden thatch
		Mat boards = d.habits().roof().thatch() ? d.mat(Role.DOOR) : d.mat(Role.ROOF_TRIM);
		if (!boards.has(Form.LOG) && !boards.has(Form.STRIPPED_LOG)) {
			boards = d.mat(Role.FRAME);
		}
		Mat post = d.mat(Role.BEAM);
		Mat pent = d.mat(Role.ROOF);
		Paint board = boards.strippedLog(Direction.Axis.Y).orElse(boards.full());
		Paint king = post.log(Direction.Axis.Y).orElse(post.full());
		int[][] ends = alongX ? new int[][]{{r[0], -1}, {r[2], 1}} : new int[][]{{r[1], -1}, {r[3], 1}};
		for (int[] e : ends) {
			int fixed = e[0];
			Direction out = alongX ? (e[1] < 0 ? Dirs.LEFT : Dirs.RIGHT) : (e[1] < 0 ? Dirs.FRONT : Dirs.BACK);
			int lo = alongX ? r[1] : r[0];
			int hi = alongX ? r[3] : r[2];
			int mid2 = lo + hi;
			// the boards
			try (var s = d.stage(StageKind.WALLS)) {
				for (int c = lo; c <= hi; c++) {
					int x = alongX ? fixed : c;
					int z = alongX ? c : fixed;
					if (!roof.covers(x, z)) {
						continue;
					}
					int top = roof.bottomY(x, z);
					for (int y = plate + 1; y < top; y++) {
						if (!Openings.isWallBlock(d, x, y, z)) {
							continue;
						}
						d.set(x, y, z, 2 * c == mid2 ? king : board);
					}
				}
			}
			// the pent across the gable's foot, under the barge
			int ox = Dirs.dx(out);
			int oz = Dirs.dz(out);
			int py = plate + 1;
			try (var s = d.stage(StageKind.ROOF)) {
				for (int c = lo + 1; c <= hi - 1; c++) {
					int x = (alongX ? fixed : c) + ox;
					int z = (alongX ? c : fixed) + oz;
					boolean onPlot = x >= 0 && x < d.frontage() && z < d.depth() && (z >= 0 || z == -1 && py >= 3);
					if (!onPlot || !d.isAbsent(x, py, z)) {
						continue;
					}
					// only where the barge stands clear above it (not at the eaves' corners)
					if (roof.covers(x, z) && roof.bottomY(x, z) <= py + 1) {
						continue;
					}
					if (!Openings.isWallBlock(d, x - ox, py, z - oz) && !Openings.isWallBlock(d, x - ox, py - 1, z - oz)) {
						continue;
					}
					d.set(x, py, z, pent.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/**
	 * A bench against the front wall beside the door (where folk sit of an evening): two seats of the furniture's wood
	 * on the doorstep's row, a cell clear of the door, never on the path. Registers its seats.
	 */
	public static void bench(Draft d, int doorX, int wallZ, int y) {
		int z = wallZ - 1;
		if (z < 1) {
			return;
		}
		for (int side : new int[]{1, -1}) {
			int a = doorX + side * 2;
			int b = doorX + side * 3;
			if (!seatable(d, a, y, z, wallZ) || !seatable(d, b, y, z, wallZ)) {
				continue;
			}
			dev.voidmc.hearth.build.kit.Furniture.chair(d, a, y, z, Dirs.BACK);
			dev.voidmc.hearth.build.kit.Furniture.chair(d, b, y, z, Dirs.BACK);
			return;
		}
	}

	private static boolean seatable(Draft d, int x, int y, int z, int wallZ) {
		return x >= 1 && x < d.frontage() - 1 && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.isSolid(x, y - 1, z)
			&& Openings.isWallBlock(d, x, y, wallZ) && d.isEmpty(x, y, z - 1);
	}

	/**
	 * Wattle between the posts of a fence line: along cells x0..x1 of row z (or column x when {@code alongZ}), every
	 * fence block that is not a post (a post every third, at the ends and beside a gate) becomes woven hurdle (mangrove
	 * roots, a woven mesh a block high), so a garden or a yard is closed with withies rather than rails.
	 */
	public static void wattle(Draft d, int x0, int z0, int x1, int z1) {
		Block weave = Blocks.MANGROVE_ROOTS;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int k = x1 > x0 ? x - x0 : z - z0;
					int len = x1 > x0 ? x1 - x0 : z1 - z0;
					for (int y = -2; y <= 6; y++) {
						BlockState st = d.get(x, y, z);
						if (st == null || !(st.getBlock() instanceof FenceBlock)) {
							continue;
						}
						if (k == 0 || k == len || k % 3 == 0 || gateBeside(d, x, y, z) || !d.isEmpty(x, y + 1, z)) {
							continue;
						}
						d.set(x, y, z, Paint.of(weave.defaultBlockState()));
					}
				}
			}
		}
	}

	private static boolean gateBeside(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			BlockState st = d.get(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
			if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A roofed farm gate (the Bohemian {@code vrata}): the gate's posts raised in timber either side of the way in at
	 * (x, 0), a beam across over head height and a little gabled roof of the roofing over it, from the street into the
	 * yard. Returns whether it was built.
	 */
	public static boolean roofedGate(Draft d, int x, int y) {
		// over head height on the yard's side and the street's
		int street = y;
		for (int dx = -2; dx <= 2; dx++) {
			street = Math.max(street, d.site().ground(x + dx, -1));
		}
		int top = street + 3;
		if (top + 2 >= d.maxHeight() || x - 2 < 0 || x + 2 >= d.frontage() || d.depth() < 3) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int yy = y; yy <= top + 1; yy++) {
				BlockState st = d.get(x + dx, yy, 0);
				boolean postCell = dx != 0 && yy < top;
				if (st != null && !st.isAir() && !postCell && yy >= y + 1) {
					return false;
				}
			}
		}
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int dx : new int[]{-1, 1}) {
				for (int yy = y; yy < top; yy++) {
					d.set(x + dx, yy, 0, frame.log(Direction.Axis.Y));
				}
				if (d.isAbsent(x + dx, y - 1, 0) || !d.isSolid(x + dx, y - 1, 0)) {
					d.set(x + dx, y - 1, 0, d.mat(Role.FOUNDATION).full());
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				d.set(x + dx, top, 0, beam.log(Direction.Axis.X));
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dx = -2; dx <= 2; dx++) {
				// a little gable along the street: the slopes falling to the street and the yard
				d.set(x + dx, top, -1, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.set(x + dx, top, 1, roof.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
				d.set(x + dx, top + 1, 0, roof.slab(SlabType.BOTTOM).orElse(roof.stairsOrSlab(Dirs.FRONT, Half.BOTTOM)));
			}
			// the beam's ends carried under the eaves
			for (int dx : new int[]{-2, 2}) {
				d.set(x + dx, top, 0, beam.log(Direction.Axis.X));
			}
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, top - 1, 1, States.lantern(Blocks.LANTERN, true)));
		return true;
	}

	/**
	 * A log beehive (a hollowed trunk standing on a stone, the Bohemian {@code úl}) at (x, y, z) facing {@code facing}, if
	 * the cell is free ground. Returns whether it was set.
	 */
	public static boolean hive(Draft d, int x, int y, int z, Direction facing) {
		if (x < 0 || x >= d.frontage() || z < 1 || z >= d.depth() || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z) || !d.isSolid(x, y - 1, z)) {
			return false;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.BEEHIVE, facing));
		}
		return true;
	}
}
