package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What keeps a flat-roofed house of the dry lands from being a box: the parapet's line softened (rounded shoulders and
 * raised corners in mud, stepped merlons in dressed stone), a wind tower catching the breeze above the roof, a shade of
 * poles, slats and palm fronds over a terrace or a door, a painted door in a coloured surround with a tile at its sill,
 * jars and potted cacti. Everything is drawn in the style's roles (the wall, its accent band, the frame's timber, the
 * door's paint, the accent tile, the cloth), so each culture dresses it in its own colours.
 */
public final class Adobe {
	private Adobe() {
	}

	/** How a parapet's top is finished. */
	public enum Parapet {
		/** Mud: the coping rounded over (a bullnose), the corners eased. */
		ROUNDED,
		/** Dressed stone or plaster: a stepped pinnacle at each corner and over the middle of a long side. */
		MERLONS
	}

	/**
	 * Finishes the parapet round a roof deck whose walls are the rectangle {@code x0..x1 × z0..z1}, the deck's standing
	 * level {@code y} (the parapet is the course at y with its coping at y + 1).
	 */
	public static void parapet(Draft d, int x0, int z0, int x1, int z1, int y, Parapet kind) {
		if (y + 3 >= d.maxHeight()) {
			return;
		}
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat cap = accent.has(Form.SLAB) ? accent : wall;
		try (var s = d.stage(StageKind.ROOF)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				int mid = side.middle();
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (!d.isSolid(x, y, z)) {
						continue;
					}
					boolean corner = side.isCorner(i);
					if (kind == Parapet.ROUNDED) {
						// the coping turned over toward the outside: a stair whose high side is inward
						Mat trim = d.mat(Role.ROOF_TRIM);
						Mat round = wall.has(Form.STAIRS) ? wall : accent.has(Form.STAIRS) ? accent : trim.has(Form.STAIRS) ? trim : null;
						if (round != null) {
							d.set(x, y + 1, z, round.stairs(side.out().getOpposite(), Half.BOTTOM));
						}
					} else if (corner || side.length() >= 8 && i == mid) {
						d.set(x, y + 1, z, wall.full());
						d.set(x, y + 2, z, cap.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/**
	 * A wind tower standing on a roof deck at (x, z) (its near corner), {@code size} (1 or 2) square, rising {@code height}
	 * blocks above the deck's standing level y: plain walls, a band of the accent colour, latticed vents on every face
	 * near the top and a coping. Returns whether it was built.
	 */
	public static boolean windTower(Draft d, int x, int z, int size, int y, int height) {
		return windTower(d, x, z, size, y, height, false);
	}

	/** As {@link #windTower(Draft, int, int, int, int, int)}; {@code onWall}: rising from the wall head (a parapet's corner). */
	public static boolean windTower(Draft d, int x, int z, int size, int y, int height, boolean onWall) {
		int top = y + height;
		if (top + 1 >= d.maxHeight()) {
			return false;
		}
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				if (!Verandas.in(d, x + dx, z + dz) || !onWall && !d.isEmpty(x + dx, y, z + dz) || !d.isSolid(x + dx, y - 1, z + dz)) {
					return false;
				}
			}
		}
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat vent = d.mat(Role.SHUTTER);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dx = 0; dx < size; dx++) {
				for (int dz = 0; dz < size; dz++) {
					int cx = x + dx;
					int cz = z + dz;
					for (int yy = y; yy < top; yy++) {
						d.set(cx, yy, cz, yy == top - 3 ? accent.full() : wall.full());
					}
					d.set(cx, top, cz, (accent.has(Form.SLAB) ? accent : wall).slab(SlabType.BOTTOM));
				}
			}
			// vents: the two courses under the coping open on every face behind lattices (one cell of each face on a big
			// tower stays solid at the corner)
			if (size == 1) {
				for (int yy = top - 2; yy < top; yy++) {
					d.set(x, yy, z, Blocks.AIR.defaultBlockState());
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int ox = x + Dirs.dx(dir);
					int oz = z + Dirs.dz(dir);
					for (int yy = top - 2; yy < top; yy++) {
						if (d.isAbsent(ox, yy, oz) && Verandas.in(d, ox, oz)) {
							d.set(ox, yy, oz, vent.trapdoor(dir.getOpposite(), Half.BOTTOM, true));
						}
					}
				}
			} else {
				for (Side side : Side.of(x, z, x + size - 1, z + size - 1)) {
					for (int i = 0; i < side.length(); i++) {
						for (int yy = top - 2; yy < top; yy++) {
							d.set(side.x(i), yy, side.z(i), vent.trapdoor(side.out().getOpposite(), Half.BOTTOM, true));
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * A shade over the cells {@code x0..x1 × z0..z1} at standing level y: poles at the corners, a roof of slats with palm
	 * fronds laid over it two blocks up. Only over cells that are free; returns whether it was built.
	 */
	public static boolean ramada(Draft d, int x0, int z0, int x1, int z1, int y) {
		if (y + 3 >= d.maxHeight()) {
			return false;
		}
		int[][] posts = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
		for (int[] p : posts) {
			if (!Verandas.in(d, p[0], p[1]) || !d.isEmpty(p[0], y, p[1]) || !d.isEmpty(p[0], y + 1, p[1])) {
				return false;
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!d.isAbsent(x, y + 2, z) || !Verandas.in(d, x, z)) {
					return false;
				}
			}
		}
		Mat pole = d.mat(Role.FRAME);
		Mat slat = d.mat(Role.FURNITURE);
		Mat frond = d.mat(Role.TREE_LEAVES);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] p : posts) {
				d.set(p[0], y, p[1], pole.fence().orElse(pole.log(Direction.Axis.Y)));
				d.set(p[0], y + 1, p[1], pole.fence().orElse(pole.log(Direction.Axis.Y)));
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					double u = Noise.unit(d.seed() ^ 0x8A3A, x, y, z);
					if (frond.has(Form.FULL) && (u < 0.45 && !edge || u < 0.2)) {
						d.set(x, y + 2, z, frond.leaves());
					} else {
						d.set(x, y + 2, z, slat.trapdoor(Dirs.FRONT, Half.BOTTOM, false));
					}
				}
			}
		}
		return true;
	}

	/**
	 * A ladder up the outside of a wall to its roof (the pueblo's way up): rungs from standing level y to the deck at
	 * (x, z), its rails standing a block over the parapet. The cell must be free all the way up; returns whether drawn.
	 */
	public static boolean ladder(Draft d, int x, int z, Direction out, int y, int deck) {
		if (!Verandas.in(d, x, z) || deck + 2 >= d.maxHeight()) {
			return false;
		}
		for (int yy = y; yy <= deck + 1; yy++) {
			if (!d.isAbsent(x, yy, z)) {
				return false;
			}
		}
		if (!d.isSolid(x, y - 1, z) && y - 1 >= d.site().ground(x, z)) {
			return false;
		}
		Mat pole = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int yy = y; yy <= deck; yy++) {
				d.set(x, yy, z, States.facing(Blocks.LADDER, out));
			}
			d.set(x, deck + 1, z, pole.fence());
		}
		return true;
	}

	/**
	 * A horno, the beehive bread oven of the dry lands, standing in a yard at (x, y, z): a mouth (a smoker) facing
	 * {@code facing}, a body of the culture's clay behind it, a domed top. Returns whether it was built.
	 */
	public static boolean horno(Draft d, int x, int y, int z, Direction facing) {
		int bx = x - Dirs.dx(facing);
		int bz = z - Dirs.dz(facing);
		for (int[] c : new int[][]{{x, z}, {bx, bz}}) {
			if (!Verandas.in(d, c[0], c[1]) || !d.isEmpty(c[0], y, c[1]) || !d.isEmpty(c[0], y + 1, c[1]) || !d.isSolid(c[0], y - 1, c[1])
				&& y - 1 >= d.site().ground(c[0], c[1])) {
				return false;
			}
		}
		Mat clay = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.SMOKER, facing));
			d.set(bx, y, bz, clay.full());
			d.set(x, y + 1, z, clay.stairsOrSlab(facing.getOpposite(), Half.BOTTOM));
			d.set(bx, y + 1, bz, clay.slab(SlabType.BOTTOM).orElse(clay.full()));
		}
		return true;
	}

	/** A tile set in the threshold before a door at (x, y, z): the accent's glazed tile in the paving. */
	public static void threshold(Draft d, int x, int y, int z) {
		Mat accent = d.mat(Role.ACCENT);
		var below = d.get(x, y - 1, z);
		if (!Verandas.in(d, x, z) || accent.main(Form.FULL) == null || !d.isEmpty(x, y, z)) {
			return;
		}
		if (below == null && y - 1 < d.site().ground(x, z) || below != null && (below.is(Blocks.SAND) || below.is(Blocks.RED_SAND)
			|| Garden.groundLike(d, x, y - 1, z) || below.getBlock() == d.mat(Role.PAVING).main(Form.FULL) || below.getBlock() == d.mat(Role.PATH).main(Form.FULL))) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y - 1, z, accent.full()));
		}
	}

	/** A jar, a potted cactus or a water jar on the free, floored cell (x, y, z). */
	public static boolean jar(Draft d, int x, int y, int z) {
		if (!Verandas.in(d, x, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z) || !d.isSolid(x, y - 1, z)) {
			return false;
		}
		double u = Noise.unit(d.seed() ^ 0x1A2, x, y, z);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, u < 0.45 ? States.of(Blocks.DECORATED_POT) : u < 0.8 ? States.of(Blocks.POTTED_CACTUS)
			: Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
		return true;
	}

	/**
	 * Roof-terrace living on a deck {@code x0..x1 × z0..z1} (inside the parapet) at standing level y: a carpet laid in the
	 * shade, low seats, jars along the parapet, a lantern; keeps (kx, kz), the way up, free. Returns a seat cell or null.
	 */
	public static int[] terraceLife(Draft d, int x0, int z0, int x1, int z1, int y, int kx, int kz) {
		int w = x1 - x0 + 1;
		int dp = z1 - z0 + 1;
		if (w < 3 || dp < 3) {
			return null;
		}
		int[] seat = null;
		// a rug and two seats in the corner farthest from the way up
		int cx = Math.abs(kx - x0) > Math.abs(kx - x1) ? x0 : x1;
		int cz = Math.abs(kz - z0) > Math.abs(kz - z1) ? z0 : z1;
		int dx = cx == x0 ? 1 : -1;
		int dz = cz == z0 ? 1 : -1;
		var cloth = d.mat(Role.CLOTH).palette();
		var carpet = cloth.main().block(Form.CARPET);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < 2; i++) {
				for (int j = 0; j < 2; j++) {
					int x = cx + dx * i;
					int z = cz + dz * j;
					if (carpet != null && d.isEmpty(x, y, z) && d.isSolid(x, y - 1, z) && Math.abs(x - kx) + Math.abs(z - kz) > 1) {
						d.set(x, y, z, carpet.defaultBlockState());
					}
				}
			}
		}
		int sx = cx + dx * 2;
		if (sx >= x0 && sx <= x1 && d.isEmpty(sx, y, cz) && Math.abs(sx - kx) + Math.abs(cz - kz) > 1) {
			Furniture.chair(d, sx, y, cz, dx > 0 ? Dirs.RIGHT : Dirs.LEFT);
			seat = new int[]{sx, cz};
			d.poi(PoiType.VIEWPOINT, cx, y, cz, dz > 0 ? Dirs.FRONT : Dirs.BACK);
		}
		// jars along the parapet's foot
		for (int x = x0; x <= x1; x++) {
			for (int z : new int[]{z0, z1}) {
				if (Math.abs(x - kx) + Math.abs(z - kz) > 1 && Noise.unit(d.seed() ^ 0x7A7, x, y, z) < 0.25) {
					jar(d, x, y, z);
				}
			}
		}
		return seat;
	}
}
