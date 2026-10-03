package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Water for bathing: sunken pools with stone basins and steps in, rock pools edged with boulders, steam rising from fires
 * under grates in the floor, and the mosaic of a bath's floor.
 */
public final class Baths {
	private Baths() {
	}

	/**
	 * A sunken pool over the rectangle, its water {@code deep} blocks deep below the floor at standing level y: a basin of
	 * the plinth stone (walls and bottom), a rim of the accent stone at the floor, steps down at the front end. Registers
	 * nothing (folk sit on the rim's benches).
	 */
	public static void pool(Draft d, int x0, int z0, int x1, int z1, int y, int deep) {
		Mat basin = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int x = x0 - 1; x <= x1 + 1; x++) {
					boolean rim = x < x0 || x > x1 || z < z0 || z > z1;
					for (int yy = y - 1 - deep; yy < y - 1; yy++) {
						if (rim || yy == y - 1 - deep) {
							d.set(x, yy, z, basin.full());
						}
					}
					if (rim) {
						d.set(x, y - 1, z, accent.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					for (int yy = y - deep; yy <= y - 1; yy++) {
						d.set(x, yy, z, Blocks.WATER.defaultBlockState());
					}
				}
			}
			// steps down at the front end: a stair under the water
			int sx = (x0 + x1) / 2;
			if (deep >= 2) {
				d.set(sx, y - deep, z0, basin.stairsOrSlab(Dirs.FRONT, Half.BOTTOM).map(st -> st.trySetValue(BlockStateProperties.WATERLOGGED, true)));
			}
		}
	}

	/**
	 * A rock pool: an irregular basin round (cx, cz) of radii rx, rz, edged with boulders of mossy stone and andesite, one
	 * deep (two in the middle) below the standing level y.
	 */
	public static void rockPool(Draft d, double cx, double cz, double rx, double rz, int y) {
		List<int[]> disc = Draft.ellipseCells(cx, cz, rx, rz, true);
		List<int[]> rim = Draft.ellipseCells(cx, cz, rx + 1, rz + 1, false);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : disc) {
				boolean middle = Draft.insideEllipse(c[0], c[1], cx, cz, rx - 1.2, rz - 1.2);
				int bottom = middle ? y - 3 : y - 2;
				d.set(c[0], bottom, c[1], Blocks.STONE.defaultBlockState());
				for (int yy = bottom + 1; yy < y - 1; yy++) {
					d.set(c[0], yy, c[1], Blocks.AIR.defaultBlockState());
				}
			}
			for (int[] c : rim) {
				double u = Noise.unit(d.seed() ^ 0x40C, c[0], y, c[1]);
				d.set(c[0], y - 2, c[1], Blocks.STONE.defaultBlockState());
				d.set(c[0], y - 1, c[1], (u < 0.4 ? Blocks.MOSSY_COBBLESTONE : u < 0.7 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState());
				if (u < 0.22) {
					d.set(c[0], y, c[1], (u < 0.11 ? Blocks.MOSSY_COBBLESTONE : Blocks.ANDESITE).defaultBlockState());
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int[] c : disc) {
				boolean middle = Draft.insideEllipse(c[0], c[1], cx, cz, rx - 1.2, rz - 1.2);
				for (int yy = middle ? y - 2 : y - 1; yy <= y - 1; yy++) {
					d.set(c[0], yy, c[1], Blocks.WATER.defaultBlockState());
				}
			}
		}
	}

	/** A steam vent: a fire in a pit under an iron grate in the floor at (x, z), standing level y. */
	public static void steam(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y - 3, z, Blocks.STONE.defaultBlockState());
			d.set(x, y - 2, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			d.set(x, y - 1, z, Blocks.IRON_BARS.defaultBlockState());
		}
	}

	/** A mosaic over the floor at block row y: the paving with a border and a pattern of the accent and glazed tile. */
	public static void mosaic(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat paving = d.mat(Role.PAVING);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat tile = d.mat(Role.ACCENT);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!d.isEmpty(x, y + 1, z) && !d.isSolid(x, y, z)) {
						continue;
					}
					boolean border = x == x0 || x == x1 || z == z0 || z == z1;
					boolean diamond = (Math.abs(x - (x0 + x1) / 2) + Math.abs(z - (z0 + z1) / 2)) % 4 == 0;
					d.set(x, y, z, border ? accent.full() : diamond ? tile.full() : paving.full());
				}
			}
		}
	}

	/** Benches of slabs along a run, each a seat. */
	public static void bench(Draft d, int x, int y, int z, Direction run, int n, Direction facing) {
		Mat stone = d.mat(Role.WALL_ACCENT);
		for (int i = 0; i < n; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			if (!d.isEmpty(px, y, pz)) {
				continue;
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(px, y, pz, stone.has(Form.SLAB) ? stone.slab(SlabType.BOTTOM) : d.mat(Role.FURNITURE).slab(SlabType.BOTTOM));
			}
			if (i % 2 == 0) {
				d.poi(PoiType.SEAT, px, y, pz, facing);
			}
		}
	}
}
