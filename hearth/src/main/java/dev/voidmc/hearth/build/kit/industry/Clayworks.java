package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The brickmaker's yard: the kiln itself (a vaulted chamber of brick on buttressed walls, its firemouths glowing under
 * little hoods, the wicket bricked up for the firing), hacks of green bricks drying in open lattices under board caps,
 * stacks of fired brick waiting for the carts, and the pug mill where a horse walks the clay round its tub.
 */
public final class Clayworks {
	private Clayworks() {
	}

	/** A kiln drawn: its rectangle and the cells before its firemouths. */
	public record Kiln(int x0, int z0, int x1, int z1, int top, List<int[]> mouths) {
	}

	/**
	 * A kiln over [x0..x1] × [z0..z1] (5 or more each way), its firemouths in the front wall: walls four courses of
	 * {@code body} with buttresses at the corners and between the mouths, a barrel vault over it along z, the wicket in
	 * one side wall bricked up in {@code seal}. The chamber is set full of brick (solid). Returns the kiln.
	 */
	public static Kiln kiln(Draft d, int x0, int z0, int x1, int z1, Fam body, Fam seal, Direction wicketSide) {
		dev.voidmc.hearth.build.terrain.Groundwork.footings(d, x0, z0, x1, z1, -1);
		int cx = (x0 + x1) / 2;
		List<int[]> mouths = new ArrayList<>();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= 3; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						d.set(x, y, z, body.full());
					}
				}
			}
			// the vault: stairs springing from the long walls, the crown along the middle
			int w = x1 - x0 + 1;
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int fromEdge = Math.min(x - x0, x1 - x);
					if (fromEdge == 0) {
						d.set(x, 4, z, body.stairsOrSlab(x == x0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
					} else {
						d.set(x, 4, z, body.full());
						if (fromEdge == 1 && w >= 5) {
							d.set(x, 5, z, body.stairsOrSlab(x == x0 + 1 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
						} else if (fromEdge >= 2) {
							d.set(x, 5, z, body.has(Form.SLAB) ? body.slab(SlabType.BOTTOM) : body.full());
						}
					}
				}
			}
		}
		// buttresses at the corners, raked back to the wall
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : new int[][]{{x0, z0 - 1, 0}, {x1, z0 - 1, 0}, {x0, z1 + 1, 1}, {x1, z1 + 1, 1}}) {
				if (!Works.in(d, c[0], c[1]) || !d.isEmpty(c[0], 0, c[1])) {
					continue;
				}
				Direction toWall = c[2] == 0 ? Dirs.BACK : Dirs.FRONT;
				d.set(c[0], 0, c[1], body.full());
				d.set(c[0], 1, c[1], body.full());
				d.set(c[0], 2, c[1], body.stairsOrSlab(toWall, Half.BOTTOM));
			}
		}
		// the firemouths: one every other cell in the front wall, a lit furnace under a hood of brick
		for (int x = x0 + 1; x <= x1 - 1; x += 2) {
			int fx = x;
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(fx, 0, z0, States.facing(Blocks.FURNACE, Dirs.FRONT).trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,
					true));
			}
			try (var s = d.stage(StageKind.WALLS)) {
				if (Works.in(d, fx, z0 - 1) && d.isEmpty(fx, 1, z0 - 1)) {
					d.set(fx, 1, z0 - 1, body.stairsOrSlab(Dirs.BACK, Half.TOP));
				}
			}
			mouths.add(new int[]{fx, z0 - 1});
		}
		// the wicket in a side wall, bricked up for the firing
		int wx = wicketSide == Dirs.LEFT ? x0 : x1;
		int wz = (z0 + z1) / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = wz - 1; z <= wz + 1; z++) {
				for (int y = 0; y <= 2; y++) {
					boolean arch = y == 2 && z != wz;
					if (!arch) {
						d.set(wx, y, z, seal.full());
					}
				}
			}
		}
		return new Kiln(x0, z0, x1, z1, 5, mouths);
	}

	/**
	 * A hack of green bricks drying along {@code run} from (x, z): bricks set on edge in an open lattice two courses
	 * high (wall blocks of mud brick, so air shows between them), capped with boards against the rain where
	 * {@code capped}. {@code length} cells.
	 */
	public static void hack(Draft d, int x, int y, int z, Direction run, int length, boolean capped) {
		Mat cap = Works.timber(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz) || !d.isEmpty(px, y, pz)) {
					continue;
				}
				d.set(px, y, pz, Blocks.MUD_BRICK_WALL.defaultBlockState());
				d.set(px, y + 1, pz, Blocks.MUD_BRICK_WALL.defaultBlockState());
				if (capped && cap.has(Form.SLAB)) {
					d.set(px, y + 2, pz, cap.slab(SlabType.BOTTOM));
				} else {
					d.set(px, y + 2, pz, States.slab(Blocks.MUD_BRICK_SLAB, SlabType.BOTTOM));
				}
			}
		}
	}

	/** A stack of fired brick on a pallet, 2 across and {@code length} along {@code run}, two or three courses high. */
	public static void stack(Draft d, int x, int y, int z, Direction run, int length) {
		Mat pallet = Works.timber(d);
		Direction across = Dirs.right(run);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				for (int k = 0; k < 2; k++) {
					int px = x + Dirs.dx(run) * i + Dirs.dx(across) * k;
					int pz = z + Dirs.dz(run) * i + Dirs.dz(across) * k;
					if (!Works.in(d, px, pz) || !d.isEmpty(px, y, pz)) {
						continue;
					}
					d.set(px, y, pz, pallet.slab(SlabType.TOP));
					d.set(px, y + 1, pz, Blocks.BRICKS.defaultBlockState());
					double u = Noise.unit(d.seed() ^ 0xB41C, px, y, pz);
					if (u < 0.55) {
						d.set(px, y + 2, pz, Blocks.BRICKS.defaultBlockState());
					} else if (u < 0.85) {
						d.set(px, y + 2, pz, States.slab(Blocks.BRICK_SLAB, SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/**
	 * The pug mill: a tub of clay (a composter) with its upright shaft, the sweep arm reaching {@code arm} out toward
	 * {@code toward} at head height for the horse, the round it walks trodden into the ground. Returns the cell beside
	 * the tub where the worker stands.
	 */
	public static int[] pugMill(Draft d, int x, int z, Direction toward, int arm) {
		Mat log = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// the round trodden into a path
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					double r = Math.hypot(dx, dz);
					if (r > 1.4 && r < 2.6 && Works.in(d, x + dx, z + dz) && d.isEmpty(x + dx, 0, z + dz)
						&& dev.voidmc.hearth.build.kit.Garden.groundLike(d, x + dx, -1, z + dz)) {
						d.set(x + dx, -1, z + dz, Blocks.DIRT_PATH.defaultBlockState());
					}
				}
			}
			d.set(x, 0, z, Blocks.COMPOSTER.defaultBlockState().trySetValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 7));
			d.set(x, 1, z, log.log(Direction.Axis.Y));
			d.set(x, 2, z, log.log(Direction.Axis.Y));
			for (int i = 1; i <= arm; i++) {
				int ax = x + Dirs.dx(toward) * i;
				int az = z + Dirs.dz(toward) * i;
				if (Works.in(d, ax, az)) {
					d.set(ax, 2, az, beam.log(toward.getAxis()));
				}
			}
		}
		Direction side = Dirs.right(toward);
		return new int[]{x + Dirs.dx(side), z + Dirs.dz(side)};
	}

	/** Blocks clay is heaped in. */
	public static Block[] clay() {
		return new Block[]{Blocks.CLAY, Blocks.CLAY, Blocks.PACKED_MUD, Blocks.CLAY, Blocks.COARSE_DIRT};
	}
}
