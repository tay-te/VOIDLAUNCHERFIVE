package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

	/** A kiln drawn: its footprint, its top, and the places its firers stand at its mouths. */
	public record Kiln(int x0, int z0, int x1, int z1, int top, List<Mouth> mouths) {
	}

	/** Where a firer stands to stoke a firemouth: the cell (x, y, z) and the way he faces it. */
	public record Mouth(int x, int y, int z, Direction facing) {
	}

	/**
	 * A kiln over [x0..x1] × [z0..z1] (5 or more each way), its firemouths in the front wall: walls four courses of
	 * {@code body} with buttresses at the corners and between the mouths, a barrel vault over it along z, the wicket in
	 * one side wall bricked up in {@code seal}. The chamber is set full of brick (solid). Returns the kiln.
	 */
	public static Kiln kiln(Draft d, int x0, int z0, int x1, int z1, Fam body, Fam seal, Direction wicketSide) {
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		int cx = (x0 + x1) / 2;
		List<Mouth> mouths = new ArrayList<>();
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
		// the firemouths: one every other cell in the front wall, a lit furnace under a hood of brick, and before them the
		// stoking pit, a trench a block deep where the firers stand with the fire at their chests
		pit(d, x0 + 1, x1 - 1, z0 - 1, body);
		for (int x = x0 + 1; x <= x1 - 1; x += 2) {
			int fx = x;
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(fx, 0, z0, litFurnace(Dirs.FRONT));
			}
			try (var s = d.stage(StageKind.WALLS)) {
				if (Works.in(d, fx, z0 - 1) && d.isEmpty(fx, 1, z0 - 1)) {
					d.set(fx, 1, z0 - 1, body.stairsOrSlab(Dirs.BACK, Half.TOP));
				}
			}
			mouths.add(new Mouth(fx, -1, z0 - 1, Dirs.BACK));
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
	 * A stoking pit along row z from x0 to x1: the ground dug out a block, its floor paved in {@code floor}, so a firer
	 * stands in it with a firemouth at the yard's level before him. Its ends are open to the yard (a step of one block).
	 */
	public static void pit(Draft d, int x0, int x1, int z, Fam floor) {
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				if (!Works.in(d, x, z)) {
					continue;
				}
				d.set(x, -1, z, Blocks.AIR.defaultBlockState());
				d.set(x, -2, z, floor.full());
			}
		}
	}

	/** A furnace burning, its mouth toward {@code facing}. */
	public static BlockState litFurnace(Direction facing) {
		return States.facing(Blocks.FURNACE, facing).trySetValue(BlockStateProperties.LIT, true);
	}

	/**
	 * A clamp: the green brick of a season stacked over [x0..x1] x [z0..z1] into a block {@code height} high with battered
	 * sides, cased in clay and old brick against the wind, its fire eyes (lit furnaces) in a row along the front foot, the
	 * firing smouldering through its top. How the north and the forests burn brick, without a kiln. Returns the clamp,
	 * its mouths the cells before the eyes.
	 */
	public static Kiln clamp(Draft d, int x0, int z0, int x1, int z1, int height) {
		Block[] skin = {Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.PACKED_MUD};
		Paint body = (x, y, z) -> Works.patch(d, 83, 1.7, x, y, z, skin);
		int h = Math.max(2, height);
		Roofs.Field f = (x, z) -> {
			if (x < x0 || x > x1 || z < z0 || z > z1) {
				return Double.NaN;
			}
			int e = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
			return Math.min(h, 1.0 + e);
		};
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		int top = Shape.body(d, f, 0, x0, z0, x1, z1, new Shape.Skin(body, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB), null, StageKind.WALLS);
		List<Mouth> mouths = new ArrayList<>();
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = x0 + 1; x <= x1 - 1; x += 2) {
				d.set(x, 0, z0, litFurnace(Dirs.FRONT));
				mouths.add(new Mouth(x, 0, z0 - 1, Dirs.BACK));
			}
		}
		// the firing works up through the stack: embers glowing in its top course here and there
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		if (top != Integer.MIN_VALUE) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int[] c : new int[][]{{cx, cz}, {cx - 2, cz}, {cx + 2, cz}}) {
					if (c[0] > x0 && c[0] < x1 && d.isSolid(c[0], top, c[1])) {
						d.set(c[0], top, c[1], Stack.fire());
					}
				}
			}
		}
		return new Kiln(x0, z0, x1, z1, top == Integer.MIN_VALUE ? h : top, mouths);
	}

	/**
	 * A beehive kiln of the dry lands on centre (cx, cz): a dome of {@code body} radius r and {@code height} high, its
	 * firemouth in its front foot under a hood with a stoking pit before it, a vent smoking at its crown. Returns the
	 * kiln.
	 */
	public static Kiln beehive(Draft d, double cx, double cz, double r, double height, Fam body) {
		int x0 = (int) Math.floor(cx - r);
		int x1 = (int) Math.ceil(cx + r);
		int z0 = (int) Math.floor(cz - r);
		int z1 = (int) Math.ceil(cz + r);
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		Block stair = body.has(Form.STAIRS) ? body.stairs(Dirs.FRONT, Half.BOTTOM).at(0, 0, 0).getBlock() : null;
		Block slab = body.has(Form.SLAB) ? body.slab(SlabType.BOTTOM).at(0, 0, 0).getBlock() : null;
		int top = Shape.body(d, Shape.dome(cx, cz, r, height), 0, x0, z0, x1, z1, new Shape.Skin(body.full(), stair, slab), null, StageKind.WALLS);
		int mx = (int) Math.round(cx);
		int fz = z1;
		for (int z = z0; z <= z1; z++) {
			if (!d.isEmpty(mx, 0, z)) {
				fz = z;
				break;
			}
		}
		List<Mouth> mouths = new ArrayList<>();
		pit(d, mx - 1, mx + 1, fz - 1, body);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(mx, 0, fz, litFurnace(Dirs.FRONT));
		}
		if (Works.in(d, mx, fz - 1)) {
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(mx, 1, fz - 1, body.stairsOrSlab(Dirs.BACK, Half.TOP));
			}
			mouths.add(new Mouth(mx, -1, fz - 1, Dirs.BACK));
		}
		// the vent at the crown, smoking
		int mz = (int) Math.round(cz);
		if (top != Integer.MIN_VALUE) {
			for (int y = top; y >= 1; y--) {
				if (!d.isEmpty(mx, y, mz)) {
					int vy = y;
					d.inStage(StageKind.ROOF, () -> d.set(mx, vy, mz, Stack.fire()));
					break;
				}
			}
		}
		return new Kiln(x0, z0, x1, z1, top == Integer.MIN_VALUE ? (int) Math.ceil(height) : top, mouths);
	}

	/**
	 * A climbing kiln of the east (the dragon kiln), three wide in columns x..x+2 from z0 up to z1: a vaulted tunnel of
	 * {@code body} on an earth bank stepping up a block every three cells toward the back, its firebox mouth at the low
	 * front end, a stoking hole in each step of its side toward {@code stoke} (LEFT or RIGHT) with a walk of beaten earth
	 * climbing beside it (a step at each rise), a short flue smoking at its head. Returns the kiln; its mouths are the
	 * firebox's and the walk's.
	 */
	public static Kiln dragon(Draft d, int x, int z0, int z1, Direction stoke, Fam body) {
		int walkX = stoke == Dirs.LEFT ? x - 1 : x + 3;
		int holeX = stoke == Dirs.LEFT ? x : x + 2;
		List<Mouth> mouths = new ArrayList<>();
		Block[] bank = {Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.PACKED_MUD, Blocks.DIRT};
		int top = 0;
		for (int z = z0; z <= z1; z++) {
			int b = Math.min(3, (z - z0) / 3);
			int pz = z;
			try (var s = d.stage(StageKind.EARTHWORK)) {
				for (int y = 0; y < b; y++) {
					for (int xx = x; xx <= x + 2; xx++) {
						d.set(xx, y, pz, Heap.pick(d, 87, xx, y, pz, bank));
					}
					if (Works.in(d, walkX, pz)) {
						boolean step = y == b - 1 && (z - z0) % 3 == 0;
						d.set(walkX, y, pz, step ? States.stairs(Blocks.MUD_BRICK_STAIRS, Dirs.BACK, Half.BOTTOM) : Heap.pick(d, 87, walkX, y, pz, bank));
					}
				}
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (int xx = x; xx <= x + 2; xx++) {
					d.set(xx, b, pz, body.full());
					d.set(xx, b + 1, pz, body.full());
				}
				// the vault: stairs springing off the side walls, a slab crown
				d.set(x, b + 2, pz, body.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(x + 2, b + 2, pz, body.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
				d.set(x + 1, b + 2, pz, body.full());
				d.set(x + 1, b + 3, pz, body.has(Form.SLAB) ? body.slab(SlabType.BOTTOM) : body.full());
			}
			top = Math.max(top, b + 3);
			if ((z - z0) % 3 == 1 && z > z0 && Works.in(d, walkX, pz)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(holeX, b, pz, litFurnace(stoke)));
				mouths.add(new Mouth(walkX, b, pz, stoke.getOpposite()));
			}
		}
		// the firebox at the foot: its mouth to the front
		d.inStage(StageKind.INTERIOR, () -> d.set(x + 1, 0, z0, litFurnace(Dirs.FRONT)));
		if (Works.in(d, x + 1, z0 - 1)) {
			mouths.add(new Mouth(x + 1, 0, z0 - 1, Dirs.BACK));
		}
		// the flue at the head, smoking
		int hb = Math.min(3, (z1 - z0) / 3);
		int fy = hb + 4;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = hb + 2; y < fy; y++) {
				d.set(x + 1, y, z1, body.full());
			}
			d.set(x + 1, fy, z1, Stack.fire());
		}
		return new Kiln(x, z0, x + 2, z1, Math.max(top, fy), mouths);
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
