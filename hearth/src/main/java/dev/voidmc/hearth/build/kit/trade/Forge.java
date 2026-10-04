package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Fire built into masonry: a smith's forge (a hearth of glowing coals and a blast furnace between stone cheeks, a hood
 * over them and a broad stack through the roof), a baker's oven (a vault of brick round two ovens, its own chimney), a
 * domed bread oven standing in a yard. The stacks are two blocks broad where there is room, finished with a smoking
 * pot and a cap, and stay under the plot's height.
 */
public final class Forge {
	private Forge() {
	}

	/**
	 * A forge against a wall: along the wall from (x, z) running {@code run} for four cells — a stone cheek, the hearth
	 * (a cauldron of glowing coals), the blast furnace facing {@code into} the room, a cheek — and a hood over them. With
	 * {@code breast}, the chimney breast stands out of the wall behind (the wall line and one cell beyond it must be
	 * the plot's): the wall turns to masonry behind the forge, a breast four wide climbs the outside with weathered
	 * shoulders, and a stack two by two rises from it past the roof to {@code stackTop}; otherwise a stack two wide rises
	 * from the hearth inside. Returns {hearthX, hearthZ, furnaceX, furnaceZ, smokeTop}.
	 */
	public static int[] smith(Draft d, int x, int y, int z, Direction run, Direction into, int stackTop, Roofs.Roof roof, boolean breast, int plateY) {
		Mat stone = d.mat(Role.CHIMNEY);
		Mat cheek = d.mat(Role.FOUNDATION);
		int[][] cells = new int[4][];
		for (int i = 0; i < 4; i++) {
			cells[i] = new int[]{x + Dirs.dx(run) * i, z + Dirs.dz(run) * i};
		}
		int hx = cells[1][0];
		int hz = cells[1][1];
		int fx = cells[2][0];
		int fz = cells[2][1];
		Direction out = into.getOpposite();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int k : new int[]{0, 3}) {
				d.set(cells[k][0], y, cells[k][1], cheek.full());
				d.set(cells[k][0], y + 1, cells[k][1], cheek.full());
			}
			d.set(hx, y + 1, hz, stone.full());
			d.set(fx, y + 1, fz, stone.full());
			for (int[] c : cells) {
				d.set(c[0], y + 2, c[1], stone.full());
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(hx, y, hz, Blocks.LAVA_CAULDRON.defaultBlockState());
			d.set(fx, y, fz, States.facing(Blocks.BLAST_FURNACE, into).trySetValue(BlockStateProperties.LIT, true));
		}
		// the hood: stairs over the work cells, sloping back to the stack
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells) {
				int ox = c[0] + Dirs.dx(into);
				int oz = c[1] + Dirs.dz(into);
				if (d.isAbsent(ox, y + 2, oz) || d.isEmpty(ox, y + 2, oz)) {
					d.set(ox, y + 2, oz, stone.stairsOrSlab(out, Half.TOP));
				}
			}
			d.set(cells[0][0], y + 3, cells[0][1], stone.stairsOrSlab(run, Half.BOTTOM));
			d.set(cells[3][0], y + 3, cells[3][1], stone.stairsOrSlab(run.getOpposite(), Half.BOTTOM));
		}
		int top;
		if (breast) {
			int[][] wall = new int[4][];
			int[][] bre = new int[4][];
			for (int i = 0; i < 4; i++) {
				wall[i] = new int[]{cells[i][0] + Dirs.dx(out), cells[i][1] + Dirs.dz(out)};
				bre[i] = new int[]{cells[i][0] + 2 * Dirs.dx(out), cells[i][1] + 2 * Dirs.dz(out)};
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (int i = 0; i < 4; i++) {
					// the wall behind the forge is masonry to the plate
					for (int yy = y; yy <= plateY; yy++) {
						d.set(wall[i][0], yy, wall[i][1], stone.full());
					}
					// the breast: four wide to above the hood, footed on the ground
					int g = d.site().ground(bre[i][0], bre[i][1]);
					for (int yy = Math.min(y - 1, g - 1); yy <= y + 2; yy++) {
						d.set(bre[i][0], yy, bre[i][1], yy < y ? cheek.full() : stone.full());
					}
				}
				// weathered shoulders where it narrows
				d.set(bre[0][0], y + 3, bre[0][1], stone.stairsOrSlab(run, Half.BOTTOM));
				d.set(bre[3][0], y + 3, bre[3][1], stone.stairsOrSlab(run.getOpposite(), Half.BOTTOM));
			}
			top = stack(d, new int[][]{wall[1], wall[2], bre[1], bre[2]}, y + 3, stackTop, roof);
			// the inner flue over the hearth closes the stack's foot
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(hx, y + 3, hz, stone.full());
				d.set(fx, y + 3, fz, stone.full());
			}
		} else {
			top = stack(d, new int[][]{{hx, hz}, {fx, fz}}, y + 3, stackTop, roof);
		}
		return new int[]{hx, hz, fx, fz, top};
	}

	/**
	 * A broad stack over the given cells from {@code fromY} up through the roof to {@code top} (clamped to the plot,
	 * and to at least two above the roof where it can): masonry, a corbel course, a smoking campfire on the first cell
	 * and a slab cap on the others. Returns the smoking top, or MIN_VALUE if there was no room.
	 */
	public static int stack(Draft d, int[][] cells, int fromY, int top, Roofs.Roof roof) {
		Mat stone = d.mat(Role.CHIMNEY);
		int max = d.maxHeight() - 1;
		int surface = Integer.MIN_VALUE;
		for (int[] c : cells) {
			if (roof != null && roof.covers(c[0], c[1])) {
				surface = Math.max(surface, roof.surfaceY(c[0], c[1]));
			}
		}
		int t = Math.min(max, Math.max(top, surface == Integer.MIN_VALUE ? fromY : surface + 2));
		if (t <= fromY) {
			return Integer.MIN_VALUE;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : cells) {
				for (int yy = fromY; yy < t; yy++) {
					d.set(c[0], yy, c[1], stone.full());
				}
			}
			d.set(cells[0][0], t, cells[0][1], States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			for (int i = 1; i < cells.length; i++) {
				d.set(cells[i][0], t, cells[i][1], stone.has(Form.SLAB) ? stone.slab(SlabType.BOTTOM) : stone.full());
			}
		}
		return t;
	}

	/**
	 * A baker's oven vault against a wall from (x, z) along {@code run} for {@code n} (2 or 3) cells: smokers (and a
	 * furnace) facing {@code into} the room in a brick mass two high, a brick arch course over them, the stack from the
	 * middle. Returns the oven cells (x, z pairs) so the caller registers job sites in front of them.
	 */
	public static int[][] ovens(Draft d, int x, int y, int z, Direction run, Direction into, int n, int stackTop, Roofs.Roof roof) {
		Mat brick = d.mat(Role.CHIMNEY);
		int[][] cells = new int[n][];
		for (int i = 0; i < n; i++) {
			cells[i] = new int[]{x + Dirs.dx(run) * i, z + Dirs.dz(run) * i};
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < n; i++) {
				Block oven = i == 1 && n == 3 ? Blocks.FURNACE : Blocks.SMOKER;
				d.set(cells[i][0], y, cells[i][1], States.facing(oven, into).trySetValue(BlockStateProperties.LIT, i != 1));
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells) {
				d.set(c[0], y + 1, c[1], brick.full());
				int ox = c[0] + Dirs.dx(into);
				int oz = c[1] + Dirs.dz(into);
				if (d.isAbsent(ox, y + 2, oz) || d.isEmpty(ox, y + 2, oz)) {
					d.set(ox, y + 2, oz, brick.stairsOrSlab(into.getOpposite(), Half.TOP));
				}
				d.set(c[0], y + 2, c[1], brick.full());
			}
		}
		if (stackTop != Integer.MIN_VALUE) {
			int mid = n / 2;
			stack(d, new int[][]{cells[mid]}, y + 3, stackTop, roof);
		}
		return cells;
	}

	/**
	 * The oven's back standing out of a back wall as a rounded hump of brick (the bread oven's crown seen from the yard):
	 * over columns {@code x0..x1} in row {@code z} outside the wall, two courses and a rounded top, the wall behind it
	 * brick, and the oven stack rising from its middle past the roof. Returns the smoking top.
	 */
	public static int bulge(Draft d, int x0, int x1, int z, int wallZ, int y, int stackTop, Roofs.Roof roof) {
		Mat brick = d.mat(Role.CHIMNEY);
		Mat footing = d.mat(Role.FOUNDATION);
		int mid = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				int g = d.site().ground(x, z);
				for (int yy = Math.min(y - 1, g - 1); yy < y; yy++) {
					d.set(x, yy, z, footing.full());
				}
				boolean end = x == x0 || x == x1;
				d.set(x, y, z, brick.full());
				if (end) {
					d.set(x, y + 1, z, brick.stairsOrSlab(x == x0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
				} else {
					d.set(x, y + 1, z, brick.full());
					d.set(x, y + 2, z, brick.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
				}
				for (int yy = y; yy <= y + 2; yy++) {
					d.set(x, yy, wallZ, brick.full());
				}
			}
		}
		return stack(d, new int[][]{{mid, wallZ}, {mid, z}}, y + 3, stackTop, roof);
	}

	/**
	 * A domed bread oven standing on its own in a yard (a horno): a 3 × 3 dome of brick or mud centred on (cx, cz), its
	 * corners rounded, its mouth a glowing oven toward {@code mouth}, a little flue on top, on a footing in the ground.
	 * The baker stands at the mouth. Returns the cell before the mouth {x, z}.
	 */
	public static int[] dome(Draft d, int cx, int y, int cz, Direction mouth) {
		Mat brick = d.mat(Role.CHIMNEY);
		Mat footing = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					d.set(x, y - 1, z, footing.full());
					if (dx != 0 && dz != 0) {
						// rounded corners: a stair whose back is toward the middle
						Direction toward = Dirs.of(-dx, 0);
						d.set(x, y, z, brick.stairsOrSlab(toward, Half.BOTTOM));
					} else if (dx != 0 || dz != 0) {
						d.set(x, y, z, brick.full());
						d.set(x, y + 1, z, brick.stairsOrSlab(Dirs.of(-dx, -dz), Half.BOTTOM));
					} else {
						d.set(x, y, z, brick.full());
						d.set(x, y + 1, z, brick.full());
					}
				}
			}
			d.set(cx, y + 2, cz, brick.has(Form.WALL) ? brick.wall() : brick.slab(SlabType.BOTTOM));
		}
		int mx = cx + Dirs.dx(mouth);
		int mz = cz + Dirs.dz(mouth);
		d.inStage(StageKind.INTERIOR, () -> d.set(mx, y, mz, States.facing(Blocks.SMOKER, mouth).trySetValue(BlockStateProperties.LIT, true)));
		return new int[]{mx + Dirs.dx(mouth), mz + Dirs.dz(mouth)};
	}

	/** A bin of fuel or ore: a block of it behind a slab lip (coal, charcoal, raw iron). */
	public static void bin(Draft d, int x, int y, int z, BlockState content) {
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, content));
	}
}
