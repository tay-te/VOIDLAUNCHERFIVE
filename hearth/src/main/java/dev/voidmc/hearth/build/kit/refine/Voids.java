package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.BitSet;

/**
 * Sealed voids, filled as a mason fills the core of a spire or a pier: the empty cells of a box that no way of air
 * joins to its sides, its top, or (through its floor) to what stands under it. Such a void is never walked, never seen,
 * and dark: a mob could spawn in it, so the light pass would otherwise hang an invisible light in it. Filling it with a
 * rubble core is what a builder does.
 */
public final class Voids {
	private Voids() {
	}

	/**
	 * Fills the sealed empty cells of the local box with {@code core}. Air joins through empty cells and through what folk
	 * pass or see through (doors, hatches, gates, ladders, panes and bars, rails, fences, carpets, lamps), so a room with a
	 * door or a window is never taken for a void; full blocks, stairs and slabs stop it. The box's four sides and its top
	 * are open to the world, its bottom row is open where the cell under it lets air through. Cells outside the plot
	 * ({@link Draft#allowed}) are left alone. Returns how many cells were filled.
	 */
	public static int seal(Draft d, int x0, int y0, int z0, int x1, int y1, int z1, Paint core) {
		int sx = x1 - x0 + 1;
		int sy = y1 - y0 + 1;
		int sz = z1 - z0 + 1;
		if (sx <= 2 || sz <= 2 || sy <= 1) {
			return 0;
		}
		int n = sx * sy * sz;
		BitSet open = new BitSet(n);
		IntArrayList queue = new IntArrayList();
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean face = x == x0 || x == x1 || z == z0 || z == z1 || y == y1 || y == y0 && airy(d, x, y - 1, z);
					if (face && airy(d, x, y, z)) {
						int i = (y - y0) * sx * sz + (z - z0) * sx + (x - x0);
						open.set(i);
						queue.add(i);
					}
				}
			}
		}
		int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int q = 0; q < queue.size(); q++) {
			int i = queue.getInt(q);
			int x = x0 + i % sx;
			int z = z0 + i / sx % sz;
			int y = y0 + i / (sx * sz);
			for (int[] s : steps) {
				int nx = x + s[0];
				int ny = y + s[1];
				int nz = z + s[2];
				if (nx < x0 || nx > x1 || ny < y0 || ny > y1 || nz < z0 || nz > z1) {
					continue;
				}
				int j = (ny - y0) * sx * sz + (nz - z0) * sx + (nx - x0);
				if (!open.get(j) && airy(d, nx, ny, nz)) {
					open.set(j);
					queue.add(j);
				}
			}
		}
		int filled = 0;
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = (y - y0) * sx * sz + (z - z0) * sx + (x - x0);
					if (!open.get(i) && d.isEmpty(x, y, z) && d.allowed(x, y, z)) {
						d.set(x, y, z, core);
						filled++;
					}
				}
			}
		}
		return filled;
	}

	/** {@link #seal} over the whole plot from {@code y0} up to its height, in the given stage. */
	public static int sealAll(Draft d, int y0, Paint core, dev.voidmc.hearth.api.StageKind stage) {
		try (var s = d.stage(stage)) {
			return seal(d, 0, y0, 0, d.frontage() - 1, d.maxHeight() - 1, d.depth() - 1, core);
		}
	}

	/**
	 * The last stroke of a building: every sealed void in the plot (between a ceiling and a roof, inside a pier or a
	 * turret, under a stair nobody can get under) filled with a core: rubble where the culture builds in stone, boards
	 * where it builds in timber. A void nobody can reach is no room to light.
	 */
	public static int finish(Draft d) {
		Paint core = dev.voidmc.hearth.build.kit.trade.Hall.masonry(d) ? d.mat(dev.voidmc.hearth.build.style.Role.FOUNDATION).full()
			: d.mat(dev.voidmc.hearth.build.style.Role.FLOOR).full();
		return sealAll(d, 0, core, dev.voidmc.hearth.api.StageKind.ROOF);
	}

	/** Air passes: nothing drawn, or something folk walk, climb or look through. */
	static boolean airy(Draft d, int x, int y, int z) {
		var s = d.get(x, y, z);
		if (s == null || s.isAir()) {
			return true;
		}
		var b = s.getBlock();
		if (b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.SlabBlock) {
			return false;
		}
		return s.getFluidState().isEmpty()
			&& !s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
	}
}
