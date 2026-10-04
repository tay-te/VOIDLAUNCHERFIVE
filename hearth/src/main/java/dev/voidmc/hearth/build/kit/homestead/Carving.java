package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The carpenter's finish on a timber house, the way the north does it: barge boards whose ends run on past the ridge and
 * cross over each gable's peak (horns), and a drip cap over every window and door (a little pent of the window joinery
 * above the opening, answering the sill below), so the openings read framed against the logs. The joinery is the style's
 * {@link Role#WINDOW} (white where the culture paints its trim), the horns its {@link Role#BEAM} (tarred dark).
 */
public final class Carving {
	private Carving() {
	}

	/**
	 * Horns over a gable's peak: from the ridge cells {@code a..b} of the gable's outermost row or column ({@code fixed}),
	 * the two barge boards run on a block or two past the ridge and part like horns. {@code across} is the axis the gable
	 * face spans (X for a gable facing the street: the cells are x = a..b at z = fixed).
	 */
	public static void horns(Draft d, Roofs.Roof roof, int a, int b, int fixed, Direction.Axis across) {
		horns(d, roof, a, b, fixed, across, 1);
	}

	/** {@link #horns(Draft, Roofs.Roof, int, int, int, Direction.Axis)} reaching {@code reach} blocks past the ridge. */
	public static void horns(Draft d, Roofs.Roof roof, int a, int b, int fixed, Direction.Axis across, int maxReach) {
		boolean alongX = across == Direction.Axis.X;
		int ry = alongX ? roof.surfaceY(a, fixed) : roof.surfaceY(fixed, a);
		if (ry == Integer.MIN_VALUE || ry + 2 >= d.maxHeight() || alongX && fixed < 0 && ry + 1 < 3) {
			return;
		}
		Mat beam = d.mat(Role.BEAM);
		Direction lo = alongX ? Dirs.LEFT : Dirs.FRONT;
		Direction hi = lo.getOpposite();
		int reach = Math.min(maxReach, ry + 3 < d.maxHeight() ? 2 : 1);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k = 1; k <= reach; k++) {
				int ax = alongX ? a - k : fixed;
				int az = alongX ? fixed : a - k;
				int bx = alongX ? b + k : fixed;
				int bz = alongX ? fixed : b + k;
				if ((in(d, ax, az) || az == -1) && d.isAbsent(ax, ry + k, az)) {
					d.set(ax, ry + k, az, beam.stairsOrSlab(lo, Half.BOTTOM));
				}
				if ((in(d, bx, bz) || bz == -1) && d.isAbsent(bx, ry + k, bz)) {
					d.set(bx, ry + k, bz, beam.stairsOrSlab(hi, Half.BOTTOM));
				}
			}
		}
	}

	/**
	 * Horns over both gables of a pitched roof whose ridge runs along {@code ridge}: at the roof's outermost rows (or
	 * columns) across the ridge, from its highest cells.
	 */
	public static void hornsAtGables(Draft d, Roofs.Roof roof, Direction.Axis ridge) {
		boolean alongX = ridge == Direction.Axis.X;
		int[] ends = alongX ? new int[]{roof.x0, roof.x1} : new int[]{roof.z0, roof.z1};
		for (int e : ends) {
			int best = Integer.MIN_VALUE;
			int a = 0;
			int b = 0;
			int lo = alongX ? roof.z0 : roof.x0;
			int hi = alongX ? roof.z1 : roof.x1;
			for (int c = lo; c <= hi; c++) {
				int y = alongX ? roof.surfaceY(e, c) : roof.surfaceY(c, e);
				if (y > best) {
					best = y;
					a = c;
					b = c;
				} else if (y == best && c == b + 1) {
					b = c;
				}
			}
			if (best != Integer.MIN_VALUE) {
				horns(d, roof, a, b, e, alongX ? Direction.Axis.Z : Direction.Axis.X);
			}
		}
	}

	/**
	 * Drip caps over the openings of the walls of a rectangle between levels y0 and y1: above every glass pane, lattice or
	 * door whose head meets the wall, a stair of the window joinery outside, sloping off the wall.
	 */
	public static void dripCaps(Draft d, int x0, int z0, int x1, int z1, int y0, int y1) {
		Mat joinery = d.mat(Role.WINDOW);
		for (Side s : Side.of(x0, z0, x1, z1)) {
			Direction out = s.out();
			for (int i = 1; i < s.length() - 1; i++) {
				int x = s.x(i);
				int z = s.z(i);
				for (int y = y0; y <= y1; y++) {
					if (!opening(d.get(x, y, z)) || opening(d.get(x, y + 1, z)) || !Openings.isWallBlock(d, x, y + 1, z)) {
						continue;
					}
					int ox = x + Dirs.dx(out);
					int oz = z + Dirs.dz(out);
					int cy = y + 1;
					if (!in(d, ox, oz) && !(oz == -1 && cy >= 3) || !d.isAbsent(ox, cy, oz) || !d.isAbsent(ox, cy - 1, oz) && !shutterLike(d.get(ox, cy - 1, oz))) {
						continue;
					}
					d.inStage(StageKind.OPENINGS, () -> d.set(ox, cy, oz, joinery.stairsOrSlab(out.getOpposite(), Half.BOTTOM)));
				}
			}
		}
	}

	/** A pane, a lattice in the wall, a door's upper half. */
	private static boolean opening(BlockState s) {
		if (s == null) {
			return false;
		}
		return s.getBlock() instanceof IronBarsBlock || s.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock
			|| s.getBlock() instanceof TrapDoorBlock && s.getValue(TrapDoorBlock.OPEN)
			|| s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
	}

	private static boolean shutterLike(BlockState s) {
		return s != null && (s.getBlock() instanceof TrapDoorBlock || s.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.LanternBlock);
	}

	private static boolean in(Draft d, int x, int z) {
		return x >= 0 && x < d.frontage() && z >= 0 && z < d.depth();
	}
}
