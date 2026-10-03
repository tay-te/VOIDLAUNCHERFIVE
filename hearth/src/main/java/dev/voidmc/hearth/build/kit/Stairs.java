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
					// a closed string under the upper steps
					if (i > 0 && d.isAbsent(px, py - 1, pz)) {
						d.set(px, py - 1, pz, wood.stairs(dir.getOpposite(), Half.TOP));
					}
				}
			}
		}
		return new int[]{x + Dirs.dx(dir) * rise, y + rise, z + Dirs.dz(dir) * rise};
	}

	/**
	 * A U-stair (switchback) in a 2 × (run + 2) well: up {@code first} steps toward {@code dir} on the left lane, a landing,
	 * then back on the right lane to rise {@code rise} in all. Fits a stairwell of width 2 against a wall.
	 */
	public static int[] switchback(Draft d, int x, int y, int z, Direction dir, int rise) {
		int first = rise / 2;
		int second = rise - first;
		Mat wood = d.mat(Role.FURNITURE);
		Direction across = Dirs.right(dir);
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

	/** A ladder up the wall behind (x, z) from y0 to y1, facing out of the wall. */
	public static void ladder(Draft d, int x, int z, int y0, int y1, Direction facing) {
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int y = y0; y <= y1; y++) {
				d.set(x, y, z, States.facing(Blocks.LADDER, facing));
			}
		}
	}
}
