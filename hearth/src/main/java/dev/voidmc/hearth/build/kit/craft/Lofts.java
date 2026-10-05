package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Drying lofts, the signature of the trades that dry what they make: the tanner's and the dyer's open galleries under a
 * great roof, hung with hides and cloth that show between the posts, and the paper mill's slatted louvres. Also the
 * louvred turret on a ridge that lets the reek and the damp out.
 */
public final class Lofts {
	private Lofts() {
	}

	/**
	 * Opens wall {@code side} of storey k into a drying gallery: a post at both corners and every {@code every} cells, the
	 * wall between opened from a sill at the floor up to the plate, and in each open bay goods hanging in the opening from
	 * a beam laid one row inside (so the gallery's walkway runs behind them, under the beam). {@code colours} null: open
	 * bays left bare with a rail of the fence along the sill. Returns the number of cells opened.
	 */
	public static int gallery(Draft d, Body b, int k, Direction side, int every, String[] colours) {
		if (b.isParty(side) || k >= b.storeys()) {
			return 0;
		}
		Side s = b.side(k, side);
		int floor = b.floorY(k);
		int plate = b.floorY(k + 1) - 1;
		if (plate - floor < 3) {
			return 0;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat rail = Hall.woodFence(d);
		Direction in = side.getOpposite();
		int opened = 0;
		for (int i = 1; i < s.last(); i++) {
			int x = s.x(i);
			int z = s.z(i);
			boolean post = i % Math.max(2, every) == 0 && i < s.last() - 1;
			if (post) {
				try (var st = d.stage(StageKind.FRAME)) {
					for (int y = floor; y < plate; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				}
				continue;
			}
			try (var st = d.stage(StageKind.WALLS)) {
				for (int y = floor + 1; y < plate; y++) {
					d.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
				d.set(x, floor, z, rail.fence());
			}
			// a window's sill or box left outside the opened wall goes
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			for (int y = floor - 1; y < plate; y++) {
				BlockState o = Walls.inPlot(d, ox, oz) || oz == -1 ? d.get(ox, y, oz) : null;
				if (o != null && !o.isAir() && !o.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
					&& !(o.getBlock() instanceof net.minecraft.world.level.block.StairBlock && y < floor)) {
					d.remove(ox, y, oz);
				}
			}
			opened++;
			if (colours == null) {
				continue;
			}
			int ix = x + Dirs.dx(in);
			int iz = z + Dirs.dz(in);
			int hy = plate - 1;
			try (var st = d.stage(StageKind.INTERIOR)) {
				d.set(ix, hy, iz, beam.log(s.axis()));
			}
			Hangs.hang(d, x, hy, z, side, colours);
		}
		return opened;
	}

	/**
	 * Louvres in an open wall bay: in each cell of wall {@code side} of storey k between the posts, from the floor up to the
	 * plate, a slat (a closed trapdoor at the cell's head) so the wall is a ladder of boards with air between, the paper
	 * mill's and the tobacco shed's drying wall. Cells already opened (by {@link #gallery}) or wall become slats; posts
	 * every {@code every} stay.
	 */
	public static void louvres(Draft d, Body b, int k, Direction side, int every) {
		if (b.isParty(side)) {
			return;
		}
		Side s = b.side(k, side);
		int floor = b.floorY(k);
		int plate = b.floorY(k + 1) - 1;
		Mat slat = d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER) : d.mat(Role.FURNITURE);
		Mat frame = d.mat(Role.FRAME);
		for (int i = 1; i < s.last(); i++) {
			int x = s.x(i);
			int z = s.z(i);
			if (i % Math.max(2, every) == 0 && i < s.last() - 1) {
				try (var st = d.stage(StageKind.FRAME)) {
					for (int y = floor; y < plate; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				}
				continue;
			}
			try (var st = d.stage(StageKind.OPENINGS)) {
				for (int y = floor; y < plate; y++) {
					d.set(x, y, z, slat.trapdoor(side, Half.TOP, false));
				}
			}
		}
	}

	/**
	 * A louvred turret on the ridge over (x, z): a little box of slats one block long across the ridge and
	 * {@code length} along it, capped with a tiny gable of the roof's trim, so the loft breathes and the roof has a
	 * crown. Only where the plot's height leaves three blocks over the ridge. Returns the highest y drawn, or MIN_VALUE.
	 */
	public static int ridgeVent(Draft d, Roofs.Roof roof, int x, int z, Direction.Axis ridge, int length) {
		if (roof == null || !roof.covers(x, z)) {
			return Integer.MIN_VALUE;
		}
		int ry = roof.ridgeY;
		if (ry + 3 >= d.maxHeight()) {
			return Integer.MIN_VALUE;
		}
		boolean alongX = ridge == Direction.Axis.X;
		Mat slat = d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? d.mat(Role.SHUTTER) : d.mat(Role.FURNITURE);
		Mat post = d.mat(Role.FRAME);
		Mat cap = d.mat(Role.ROOF_TRIM);
		int half = length / 2;
		try (var st = d.stage(StageKind.ROOF)) {
			for (int a = -half; a <= length - 1 - half; a++) {
				int px = alongX ? x + a : x;
				int pz = alongX ? z : z + a;
				if (!roof.covers(px, pz) || roof.surfaceY(px, pz) < ry) {
					continue;
				}
				boolean end = a == -half || a == length - 1 - half;
				// the box: thin posts standing on the ridge cap, open between, so the light shows through
				d.set(px, ry + 1, pz, Hall.woodFence(d).fence());
				// its cap: a ridge of trim slabs
				d.set(px, ry + 2, pz, cap.slab(SlabType.BOTTOM));
			}
		}
		return ry + 2;
	}

	/** Whether a cell of a wall is still solid wall (for callers that open bays by hand). */
	public static boolean wall(Draft d, int x, int y, int z) {
		return Openings.isWallBlock(d, x, y, z);
	}
}
