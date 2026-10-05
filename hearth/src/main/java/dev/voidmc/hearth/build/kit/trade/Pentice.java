package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The roof a workshop throws over the ground in front of it: a pentice that carries a hall's eave on outward at half
 * the pitch (a catslide over the smith's anvil, the carpenter's benches, the baker's stall), on posts at its outer
 * edge; or, where roofs are flat, a cloth awning on poles. Both may reach over the street a row, three blocks up.
 */
public final class Pentice {
	private Pentice() {
	}

	/**
	 * A pentice in front of {@code wall} over its columns {@code c0..c1}, continuing {@code hall}'s eave (the row just
	 * outside the wall) outward to {@code outer} cells beyond the wall, half a block lower a row. Its posts stand at the
	 * outermost row inside the plot, at level y, at the ends and every third column (never at {@code openings}).
	 * Shortens itself until its low edge clears two blocks over the ground and three over the street; returns the
	 * roof, or null if there was no room for one.
	 */
	public static Roofs.Roof over(Draft d, Roofs.Roof hall, Side wall, int c0, int c1, int outer, int y, int... openings) {
		int ia = Math.min(wall.index(c0), wall.index(c1));
		int ib = Math.max(wall.index(c0), wall.index(c1));
		int ye = Integer.MAX_VALUE;
		for (int i = ia; i <= ib; i++) {
			int ex = wall.outX(i, 1);
			int ez = wall.outZ(i, 1);
			if (hall != null && hall.covers(ex, ez)) {
				ye = Math.min(ye, hall.surfaceY(ex, ez));
			}
		}
		if (ye == Integer.MAX_VALUE) {
			return null;
		}
		for (int k = outer; k >= 2; k--) {
			int n = k - 1;
			int t = n + 1;
			int y0 = ye - ((t + 1) / 2) + 1;
			int oz = wall.outZ(ia, k);
			int ox = wall.outX(ia, k);
			boolean street = oz < 0;
			int clear = y0 - Math.max(y, d.site().ground(ox, oz));
			if (street && y0 < 3 || clear < 2) {
				continue;
			}
			// the rows from the second past the wall out to k, plus the end columns one beyond
			int ax = Integer.MAX_VALUE;
			int az = Integer.MAX_VALUE;
			int bx = Integer.MIN_VALUE;
			int bz = Integer.MIN_VALUE;
			for (int i = ia; i <= ib; i++) {
				for (int r = 2; r <= k; r++) {
					ax = Math.min(ax, wall.outX(i, r));
					az = Math.min(az, wall.outZ(i, r));
					bx = Math.max(bx, wall.outX(i, r));
					bz = Math.max(bz, wall.outZ(i, r));
				}
			}
			Roofs.Roof roof = Shed.pent(d, ax, az, bx, bz, wall.out().getOpposite(), y0, 0.5, null);
			// posts on the outermost row inside the plot
			int pk = k;
			while (pk > 1 && !Shed.Walls2.inPlot(d, wall.outX(ia, pk), wall.outZ(ia, pk))) {
				pk--;
			}
			List<int[]> posts = new ArrayList<>();
			for (int i = ia; i <= ib; i++) {
				boolean end = i == ia || i == ib;
				if (!end && (i - ia) % 3 != 0) {
					continue;
				}
				boolean skip = false;
				for (int o : openings) {
					skip |= o == wall.column(i);
				}
				if (!skip) {
					posts.add(new int[]{wall.outX(i, pk), wall.outZ(i, pk)});
				}
			}
			Shed.posts(d, roof, posts, y);
			return roof;
		}
		return null;
	}

	/**
	 * A cloth awning on poles in front of {@code wall} over columns {@code c0..c1}, {@code depth} rows deep, its cloth at
	 * height {@code top} (slabs of the style's cloth, striped with white where the style likes stripes), poles at the
	 * outer corners and every third column, standing at level y.
	 */
	public static void awning(Draft d, Side wall, int c0, int c1, int depth, int y, int top, boolean stripes, int... openings) {
		Mat cloth = d.mat(Role.CLOTH);
		Mat pole = Hall.woodFence(d);
		int ia = Math.min(wall.index(c0), wall.index(c1));
		int ib = Math.max(wall.index(c0), wall.index(c1));
		var white = net.minecraft.world.level.block.Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
		var whiteSlab = Props.block("minecraft:white_wool_slab", white);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = ia; i <= ib; i++) {
				for (int k = 1; k <= depth; k++) {
					int x = wall.outX(i, k);
					int z = wall.outZ(i, k);
					if (!d.allowed(x, top, z) || !d.isAbsent(x, top, z)) {
						continue;
					}
					boolean stripe = stripes && d.habits().stripes() && (i - ia) % 2 == 1;
					if (stripe) {
						d.set(x, top, z, whiteSlab.defaultBlockState().trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
							SlabType.BOTTOM));
					} else {
						d.set(x, top, z, cloth.has(Form.SLAB) ? cloth.slab(SlabType.BOTTOM) : cloth.full());
					}
				}
			}
			int pk = depth;
			while (pk > 1 && !Shed.Walls2.inPlot(d, wall.outX(ia, pk), wall.outZ(ia, pk))) {
				pk--;
			}
			for (int i = ia; i <= ib; i++) {
				boolean end = i == ia || i == ib;
				if (!end && (i - ia) % 3 != 0) {
					continue;
				}
				boolean skip = false;
				for (int o : openings) {
					skip |= o == wall.column(i);
				}
				int x = wall.outX(i, pk);
				int z = wall.outZ(i, pk);
				if (skip || !Shed.Walls2.inPlot(d, x, z)) {
					continue;
				}
				for (int yy = y; yy < top; yy++) {
					d.set(x, yy, z, pole.fence());
				}
			}
		}
	}
}
