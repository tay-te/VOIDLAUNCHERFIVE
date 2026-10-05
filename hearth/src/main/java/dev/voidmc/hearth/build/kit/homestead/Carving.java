package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The carpenter's finish on a timber house, the way the north does it: barge boards whose ends run on a block past the
 * ridge and cross over each gable's peak (horns, on halls and stave buildings only), and window trim: a thin board over
 * every window and door (a drip cap) answering a thin sill board below, so the openings read framed against the logs
 * without dotting the wall. The trim is a trapdoor or a slab, never a full block, in the frame's timber; only the better
 * station's houses paint it in the style's {@link Role#WINDOW} joinery (white where the culture paints its trim). The
 * horns are its {@link Role#BEAM} (tarred dark).
 */
public final class Carving {
	private Carving() {
	}

	/**
	 * Horns over a gable's peak: from the ridge cells {@code a..b} of the gable's outermost row or column ({@code fixed}),
	 * the two barge boards run on one block past the ridge and part like horns (more reads as antlers at a building's
	 * scale). {@code across} is the axis the gable face spans (X for a gable facing the street: the cells are x = a..b at
	 * z = fixed).
	 */
	public static void horns(Draft d, Roofs.Roof roof, int a, int b, int fixed, Direction.Axis across) {
		boolean alongX = across == Direction.Axis.X;
		int ry = alongX ? roof.surfaceY(a, fixed) : roof.surfaceY(fixed, a);
		if (ry == Integer.MIN_VALUE || ry + 2 >= d.maxHeight() || alongX && fixed < 0 && ry + 1 < 3) {
			return;
		}
		Mat beam = d.mat(Role.BEAM);
		Direction lo = alongX ? Dirs.LEFT : Dirs.FRONT;
		Direction hi = lo.getOpposite();
		int ax = alongX ? a - 1 : fixed;
		int az = alongX ? fixed : a - 1;
		int bx = alongX ? b + 1 : fixed;
		int bz = alongX ? fixed : b + 1;
		try (var s = d.stage(StageKind.ROOF)) {
			if ((in(d, ax, az) || az == -1) && d.isAbsent(ax, ry + 1, az)) {
				d.set(ax, ry + 1, az, beam.stairsOrSlab(lo, Half.BOTTOM));
			}
			if ((in(d, bx, bz) || bz == -1) && d.isAbsent(bx, ry + 1, bz)) {
				d.set(bx, ry + 1, bz, beam.stairsOrSlab(hi, Half.BOTTOM));
			}
		}
	}

	/**
	 * Horns over both gables of a pitched roof whose ridge runs along {@code ridge}: at the roof's outermost rows (or
	 * columns) across the ridge, from its highest cells; none on a roof steeper than one in one at the ridge. For halls
	 * and stave buildings (a mead hall, a tavern hall): a cottage's or a shed's gable stays plain.
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
			if (best == Integer.MIN_VALUE) {
				continue;
			}
			// on a steep roof (falling two or more a row off the ridge) horns would stand off it in the air: none
			// (requests/b-sociallife.md §6)
			int before = a - 1 >= lo ? (alongX ? roof.surfaceY(e, a - 1) : roof.surfaceY(a - 1, e)) : Integer.MIN_VALUE;
			int after = b + 1 <= hi ? (alongX ? roof.surfaceY(e, b + 1) : roof.surfaceY(b + 1, e)) : Integer.MIN_VALUE;
			if (before != Integer.MIN_VALUE && best - before >= 2 || after != Integer.MIN_VALUE && best - after >= 2) {
				continue;
			}
			horns(d, roof, a, b, e, alongX ? Direction.Axis.Z : Direction.Axis.X);
		}
	}

	/**
	 * Window trim on the walls of a rectangle between levels y0 and y1: above every glass pane, lattice or door whose
	 * head meets the wall, a thin board outside (a drip cap), and under every window the sill restated as a thin board,
	 * both in {@link #trim} (a sill of the window joinery left by {@link Openings} would be a white step at every window).
	 */
	public static void dripCaps(Draft d, int x0, int z0, int x1, int z1, int y0, int y1) {
		Mat joinery = d.mat(Role.WINDOW);
		Mat trim = trim(d);
		Paint head = board(trim, Half.BOTTOM);
		Paint sill = board(trim, Half.TOP);
		if (head == null) {
			return;
		}
		for (Side s : Side.of(x0, z0, x1, z1)) {
			Direction out = s.out();
			for (int i = 1; i < s.length() - 1; i++) {
				int x = s.x(i);
				int z = s.z(i);
				int ox = x + Dirs.dx(out);
				int oz = z + Dirs.dz(out);
				for (int y = y0; y <= y1; y++) {
					if (!opening(d.get(x, y, z))) {
						continue;
					}
					// the sill: the joinery's step under a window's lowest pane becomes a board
					int sy = y - 1;
					if (!opening(d.get(x, sy, z)) && !(d.get(x, y, z).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
						&& joineryStep(joinery, d.get(ox, sy, oz))) {
						d.inStage(StageKind.OPENINGS, () -> d.set(ox, sy, oz, sill));
					}
					if (opening(d.get(x, y + 1, z)) || !Openings.isWallBlock(d, x, y + 1, z)) {
						continue;
					}
					int cy = y + 1;
					if (!in(d, ox, oz) && !(oz == -1 && cy >= 3) || !d.isAbsent(ox, cy, oz) || !d.isAbsent(ox, cy - 1, oz) && !shutterLike(d.get(ox, cy - 1, oz))) {
						continue;
					}
					d.inStage(StageKind.OPENINGS, () -> d.set(ox, cy, oz, head));
				}
			}
		}
	}

	/**
	 * The timber of window trim on a log or board wall: the window joinery (white where the culture paints it) on the
	 * better station's houses (a merchant's, a priest's, an official's, or a house dressed at tier 3 and up), the frame's
	 * timber on everyone else's, so a cottage's openings read in the tone of its logs.
	 */
	public static Mat trim(Draft d) {
		dev.voidmc.hearth.api.Station st = d.station();
		boolean better = st != null ? st.ordinal() >= dev.voidmc.hearth.api.Station.MERCHANT.ordinal() : d.finish() >= 3;
		if (better && d.mat(Role.WINDOW).has(Form.TRAPDOOR)) {
			return d.mat(Role.WINDOW);
		}
		for (Role r : new Role[]{Role.FRAME, Role.BEAM, Role.WINDOW}) {
			if (d.mat(r).has(Form.TRAPDOOR) || d.mat(r).has(Form.SLAB)) {
				return d.mat(r);
			}
		}
		return d.mat(Role.WINDOW);
	}

	/**
	 * A thin board of trim: a closed trapdoor in the {@code half} of its cell (TOP for a sill under a window, BOTTOM for a
	 * cap over one), else a slab in that half; null where the timber cuts neither (never a full block).
	 */
	public static @org.jetbrains.annotations.Nullable Paint board(Mat trim, Half half) {
		if (trim.has(Form.TRAPDOOR)) {
			return trim.trapdoor(Dirs.FRONT, half, false);
		}
		if (trim.has(Form.SLAB)) {
			return trim.slab(half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
		}
		return null;
	}

	/** A stair or slab of the window joinery (the sill {@link Openings} lays under a window). */
	private static boolean joineryStep(Mat joinery, BlockState s) {
		if (s == null) {
			return false;
		}
		return s.getBlock() == joinery.main(Form.STAIRS) || s.getBlock() == joinery.main(Form.SLAB);
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
