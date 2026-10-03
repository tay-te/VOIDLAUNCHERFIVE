package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * What lifts loads in a working yard: a timber jib crane (a braced mast, a jib, a chain with its load and a windlass),
 * a gantry of two posts and a beam over a pit or a stone stack, and a hoist beam out of a loft door. Each is cut to
 * the plot's height: the mast shortens rather than poke through the sky limit, and a chain always hangs from wood.
 */
public final class Lifts {
	private Lifts() {
	}

	/**
	 * A jib crane: the mast at (x, z) standing at level y on a stone pad with four stays, a jib at the top reaching
	 * {@code len} toward {@code reach}, a chain from its end with {@code load} hanging two blocks above the ground, and a
	 * windlass log at the foot. The mast is at most {@code height} tall and kept under the plot's limit. Returns the
	 * jib's height, or MIN_VALUE if there was no room for a crane.
	 */
	public static int crane(Draft d, int x, int z, int y, int height, Direction reach, int len, BlockState load) {
		Mat mast = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat fence = Hall.woodFence(d);
		int top = Math.min(y + height, d.maxHeight() - 2);
		if (top - y < 5) {
			return Integer.MIN_VALUE;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int yy = y - 1; yy < top; yy++) {
				d.set(x, yy, z, mast.log(Direction.Axis.Y));
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int fx = x + Dirs.dx(dir);
				int fz = z + Dirs.dz(dir);
				if (!Shed.Walls2.inPlot(d, fx, fz) || !d.isAbsent(fx, y, fz)) {
					continue;
				}
				d.set(fx, y - 1, fz, d.mat(Role.FOUNDATION).full());
				d.set(fx, y, fz, beam.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
			}
			for (int i = 0; i <= len; i++) {
				int jx = x + Dirs.dx(reach) * i;
				int jz = z + Dirs.dz(reach) * i;
				d.set(jx, top, jz, beam.log(reach.getAxis()));
			}
			// the tail of the jib behind the mast, a counterweight on it
			int tx = x - Dirs.dx(reach);
			int tz = z - Dirs.dz(reach);
			if (Shed.Walls2.inPlot(d, tx, tz)) {
				d.set(tx, top, tz, beam.log(reach.getAxis()));
				d.set(tx, top - 1, tz, d.mat(Role.FOUNDATION).full());
			}
			if (top + 1 < d.maxHeight()) {
				d.set(x, top + 1, z, fence.fence());
			}
			// a brace from the mast up to the jib
			d.set(x + Dirs.dx(reach), top - 1, z + Dirs.dz(reach), beam.stairsOrSlab(reach.getOpposite(), Half.TOP));
			int ex = x + Dirs.dx(reach) * len;
			int ez = z + Dirs.dz(reach) * len;
			int loadY = y + 2;
			for (int yy = top - 1; yy > loadY; yy--) {
				d.set(ex, yy, ez, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			if (load != null) {
				d.set(ex, loadY, ez, load);
			}
		}
		// the windlass beside the foot
		int wx = x - Dirs.dx(Dirs.right(reach));
		int wz = z - Dirs.dz(Dirs.right(reach));
		if (Shed.Walls2.inPlot(d, wx, wz) && d.isAbsent(wx, y, wz)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(wx, y, wz, beam.log(reach.getAxis())));
		}
		return top;
	}

	/**
	 * A gantry: two posts at (x0, z0) and (x1, z1) (in line along x or z) standing at level y, a beam across their heads
	 * at {@code y + height}, a chain from the middle of the beam lowering {@code load} to {@code loadY}. Returns the
	 * beam's height, or MIN_VALUE if it does not fit.
	 */
	public static int gantry(Draft d, int x0, int z0, int x1, int z1, int y, int height, int loadY, BlockState load) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		int top = Math.min(y + height, d.maxHeight() - 1);
		if (top - loadY < 3) {
			return Integer.MIN_VALUE;
		}
		boolean alongX = z0 == z1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : new int[][]{{x0, z0}, {x1, z1}}) {
				int g = d.site().ground(p[0], p[1]);
				for (int yy = Math.min(y - 1, g - 1); yy < top; yy++) {
					d.set(p[0], yy, p[1], post.log(Direction.Axis.Y));
				}
				// raking stays either side of the post
				for (int k : new int[]{-1, 1}) {
					int sx = p[0] + (alongX ? 0 : k);
					int sz = p[1] + (alongX ? k : 0);
					if (Shed.Walls2.inPlot(d, sx, sz) && d.isAbsent(sx, y, sz)) {
						Direction face = alongX ? (k < 0 ? Dirs.BACK : Dirs.FRONT) : (k < 0 ? Dirs.RIGHT : Dirs.LEFT);
						d.set(sx, y, sz, beam.stairsOrSlab(face, Half.BOTTOM));
					}
				}
			}
			int ax = Math.min(x0, x1);
			int bx = Math.max(x0, x1);
			int az = Math.min(z0, z1);
			int bz = Math.max(z0, z1);
			for (int xx = ax; xx <= bx; xx++) {
				for (int zz = az; zz <= bz; zz++) {
					d.set(xx, top, zz, beam.log(alongX ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
			int mx = (ax + bx) / 2;
			int mz = (az + bz) / 2;
			for (int yy = top - 1; yy > loadY; yy--) {
				d.set(mx, yy, mz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			if (load != null) {
				d.set(mx, loadY, mz, load);
			}
		}
		return top;
	}

	/**
	 * A hoist beam out of wall cell i at height y, reaching {@code len} out, braced underneath, with a chain and a
	 * load hanging from its end down to {@code loadY} (above the ground, or above the street by three). Returns
	 * whether it fit.
	 */
	public static boolean hoist(Draft d, Side wall, int i, int y, int len, int loadY, BlockState load) {
		Mat beam = d.mat(Role.BEAM);
		for (int k = 1; k <= len; k++) {
			if (!d.allowed(wall.outX(i, k), y, wall.outZ(i, k)) || !d.isEmpty(wall.outX(i, k), y, wall.outZ(i, k))) {
				return false;
			}
		}
		int ex = wall.outX(i, len);
		int ez = wall.outZ(i, len);
		if (!d.allowed(ex, loadY, ez)) {
			return false;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int k = 1; k <= len; k++) {
				d.set(wall.outX(i, k), y, wall.outZ(i, k), beam.log(wall.out().getAxis()));
			}
			int bx = wall.outX(i, 1);
			int bz = wall.outZ(i, 1);
			if (d.isAbsent(bx, y - 1, bz) && d.allowed(bx, y - 1, bz)) {
				d.set(bx, y - 1, bz, beam.has(Form.STAIRS) ? beam.stairs(wall.out().getOpposite(), Half.TOP) : beam.log(Direction.Axis.Y));
			}
			for (int yy = y - 1; yy > loadY; yy--) {
				if (d.isAbsent(ex, yy, ez)) {
					d.set(ex, yy, ez, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				}
			}
			if (load != null) {
				d.set(ex, loadY, ez, load);
			}
		}
		return true;
	}
}
