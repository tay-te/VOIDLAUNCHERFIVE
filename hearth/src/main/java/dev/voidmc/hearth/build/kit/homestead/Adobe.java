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
		/**
		 * A home's or a workshop's: the plain coping run round, its corners eased into rounded shoulders (the coping turned
		 * over outward there), no pinnacle anywhere.
		 */
		SHOULDERED,
		/**
		 * Dressed stone or plaster: a stepped pinnacle at each corner and over the middle of a long side. Only for the
		 * fort, the noble seat and the law: on a house it makes the town a town of little forts.
		 */
		MERLONS
	}

	/**
	 * Finishes the parapet round a roof deck whose walls are the rectangle {@code x0..x1 × z0..z1}, the deck's standing
	 * level {@code y} (the parapet is the course at y with its coping at y + 1).
	 */
	public static void parapet(Draft d, int x0, int z0, int x1, int z1, int y, Parapet kind) {
		parapet(d, x0, z0, x1, z1, y, kind, false);
	}

	/**
	 * As {@link #parapet(Draft, int, int, int, int, int, Parapet)}; {@code lived}: the deck is lived on (a roof terrace,
	 * beds set out on it in the heat), so the parapet stands breast high, a second course under the coping, as the dry
	 * lands build it for privacy and as a guard: nobody steps from a bed or a jar at its foot over its top.
	 */
	public static void parapet(Draft d, int x0, int z0, int x1, int z1, int y, Parapet kind, boolean lived) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat cap = accent.has(Form.SLAB) ? accent : wall;
		if (lived && y + 3 > d.maxHeight()) {
			// no room for the second course under the plot's height: the coping is a wall's rounded top instead, which
			// nobody stands on (or, without one, the plain parapet)
			Mat round = wall.has(Form.WALL) ? wall : accent.has(Form.WALL) ? accent : d.mat(Role.ROOF_TRIM).has(Form.WALL) ? d.mat(Role.ROOF_TRIM) : null;
			if (round != null && y + 2 <= d.maxHeight()) {
				try (var s = d.stage(StageKind.ROOF)) {
					for (Side side : Side.of(x0, z0, x1, z1)) {
						for (int i = 0; i < side.length(); i++) {
							if (d.isSolid(side.x(i), y, side.z(i))) {
								d.set(side.x(i), y + 1, side.z(i), round.wall());
							}
						}
					}
				}
				return;
			}
			lived = false;
		}
		if (y + 3 >= d.maxHeight() && !lived) {
			return;
		}
		// a rounded coping is a stair a full block high on the deck's side: over one course it stands two high, which
		// nobody climbs from a bed at its foot, so a lived deck under it needs no second course (with it the parapet
		// stood three high over the deck: the dry cottage's top storey read as a hollow cube, art review 2 item 7)
		Mat roundStair = wall.has(Form.STAIRS) ? wall : accent.has(Form.STAIRS) ? accent : d.mat(Role.ROOF_TRIM).has(Form.STAIRS) ? d.mat(Role.ROOF_TRIM) : null;
		boolean second = lived && !(kind == Parapet.ROUNDED && roundStair != null);
		try (var s = d.stage(StageKind.ROOF)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z0w = side.z(i);
					if (!d.isSolid(x, y, z0w)) {
						continue;
					}
					net.minecraft.world.level.block.state.BlockState laid = d.get(x, y + 1, z0w);
					boolean coping = laid != null && (laid.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
						|| laid.getBlock() instanceof net.minecraft.world.level.block.StairBlock);
					if (second && (d.isAbsent(x, y + 1, z0w) || coping) && !ladderBeside(d, x, y, z0w)) {
						// (a coping laid already by the roof is lifted a course)
						// the second course, of the wall's own stuff
						net.minecraft.world.level.block.state.BlockState under = d.get(x, y, z0w);
						d.set(x, y + 1, z0w, under != null && under.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
							? under : wall.block(Form.FULL, x, y + 1, z0w).defaultBlockState());
					}
				}
			}
			int top = second ? y + 1 : y;
			for (Side side : Side.of(x0, z0, x1, z1)) {
				int mid = side.middle();
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (lived && (!second || !d.isSolid(x, top, z)) && d.isSolid(x, y, z) && ladderBeside(d, x, y, z)) {
						// where a ladder comes up past the parapet it stays one course high, so one steps off over it
						d.set(x, y + 1, z, cap.slab(SlabType.BOTTOM));
						continue;
					}
					if (!d.isSolid(x, top, z) || second && !d.isSolid(x, y, z)) {
						continue;
					}
					boolean corner = side.isCorner(i);
					if (kind == Parapet.ROUNDED) {
						// the coping turned over toward the outside: a stair whose high side is inward
						Mat trim = d.mat(Role.ROOF_TRIM);
						Mat round = wall.has(Form.STAIRS) ? wall : accent.has(Form.STAIRS) ? accent : trim.has(Form.STAIRS) ? trim : null;
						if (round != null) {
							d.set(x, top + 1, z, round.stairs(side.out().getOpposite(), Half.BOTTOM));
						}
					} else if (kind == Parapet.SHOULDERED) {
						// at the corner the coping turned over outward, a stair of its own stone easing the corner round
						// (the shape resolver turns the pair at each corner into one rounded quarter); no pinnacle stands on it
						Mat round = cap.has(Form.STAIRS) ? cap : wall.has(Form.STAIRS) ? wall : null;
						if (corner && round != null) {
							d.set(x, top + 1, z, round.stairs(side.out().getOpposite(), Half.BOTTOM));
						} else if (d.isAbsent(x, top + 1, z)) {
							d.set(x, top + 1, z, cap.slab(SlabType.BOTTOM));
						}
					} else if (corner || side.length() >= 8 && i == mid) {
						d.set(x, top + 1, z, wall.full());
						d.set(x, top + 2, z, cap.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/** Whether a ladder stands beside (x, z) at the parapet's course or the one under it. */
	private static boolean ladderBeside(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				var s = d.get(x + Dirs.dx(dir), y + dy, z + Dirs.dz(dir));
				if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
					return true;
				}
			}
		}
		return false;
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
	 * A shade over the cells {@code x0..x1 × z0..z1} at standing level y: poles at the corners, a roof of reed mats with
	 * palm fronds laid over it two blocks up. Only over cells that are free; returns whether it was built.
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
				if (!d.isEmpty(x, y + 2, z) || !Verandas.in(d, x, z)) {
					return false;
				}
			}
		}
		Mat pole = d.mat(Role.FRAME);
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
					if (frond.has(Form.FULL) && (u < 0.3 && !edge || u < 0.1)) {
						d.set(x, y + 2, z, frond.leaves());
					} else {
						d.set(x, y + 2, z, reed(SlabType.BOTTOM));
					}
				}
			}
		}
		return true;
	}

	/** Reeds: the woven cane mat a dry-land shade is roofed with, pale as straw. */
	public static net.minecraft.world.level.block.state.BlockState reed(SlabType type) {
		return Blocks.BAMBOO_MOSAIC_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type);
	}

	/** A pole of the frame's timber: a fence where the wood has one, else an upright log. */
	private static net.minecraft.world.level.block.state.BlockState pole(Draft d, int x, int y, int z) {
		Mat pole = d.mat(Role.FRAME);
		var p = pole.fence().orElse(pole.log(Direction.Axis.Y)).at(x, y, z);
		return p == null ? Blocks.OAK_FENCE.defaultBlockState() : p;
	}

	/**
	 * A lean-to of poles and reeds over the cells {@code x0..x1 × z0..z1} at standing level y, its back against the wall
	 * on side {@code toWall} (the house's, or a yard wall's): poles at its open corners (and every third cell of a long
	 * run) where the ground is free, a roof of reed mats two up, a half-step higher against the wall so it reads as
	 * falling away from it, fronds thrown over here and there; jars, a water jar, a stack of firewood along the wall
	 * under it. Cells already holding something keep it (an oven sheltered under it, a yard wall carrying its edge).
	 * Needs at least a cell of roof clear; returns whether it was built.
	 */
	public static boolean leanTo(Draft d, int x0, int z0, int x1, int z1, int y, Direction toWall) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		if (y + 3 >= d.maxHeight()) {
			return false;
		}
		int clear = 0;
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				if (!Verandas.in(d, x, z)) {
					return false;
				}
				clear += d.isEmpty(x, y + 2, z) ? 1 : 0;
			}
		}
		if (clear == 0) {
			return false;
		}
		boolean alongX = toWall.getAxis() == Direction.Axis.Z;
		// the row against the wall, the open row away from it
		int wallRow = alongX ? (Dirs.dz(toWall) > 0 ? bz : az) : (Dirs.dx(toWall) > 0 ? bx : ax);
		int openRow = alongX ? (wallRow == bz ? az : bz) : (wallRow == bx ? ax : bx);
		int r0 = alongX ? ax : az;
		int r1 = alongX ? bx : bz;
		Mat frond = d.mat(Role.TREE_LEAVES);
		Mat poleWood = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = r0; k <= r1; k++) {
				if (k != r0 && k != r1 && (k - r0) % 3 != 0) {
					continue;
				}
				int px = alongX ? k : openRow;
				int pz = alongX ? openRow : k;
				if (d.isEmpty(px, y, pz) && d.isEmpty(px, y + 1, pz) && (d.isSolid(px, y - 1, pz) || y - 1 < d.site().ground(px, pz))) {
					d.set(px, y, pz, pole(d, px, y, pz));
					d.set(px, y + 1, pz, pole(d, px, y + 1, pz));
				}
			}
			boolean deep = wallRow != openRow;
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					if (!d.isEmpty(x, y + 2, z)) {
						// a viga's end or a shutter: the reeds lie round it
						continue;
					}
					boolean back = (alongX ? z : x) == wallRow;
					double u = Noise.unit(d.seed() ^ 0x1EA7, x, y, z);
					SlabType t = deep && back ? SlabType.TOP : SlabType.BOTTOM;
					int k = alongX ? x : z;
					if ((k - r0) % 3 == 0 || k == r1) {
						// a rafter pole over each post, from the post to the wall
						d.set(x, y + 2, z, poleWood.slab(t).orElse(poleWood.full()));
					} else if (frond.has(Form.FULL) && !back && u < 0.12) {
						d.set(x, y + 2, z, frond.leaves());
					} else {
						d.set(x, y + 2, z, reed(t));
					}
				}
			}
		}
		// along the wall under it: firewood at one end, jars
		boolean wood = false;
		for (int k = r0; k <= r1; k++) {
			int x = alongX ? k : wallRow;
			int z = alongX ? wallRow : k;
			var behind = d.get(x + Dirs.dx(toWall), y, z + Dirs.dz(toWall));
			if (!Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z) || !d.isEmpty(x, y, z)
				|| behind != null && behind.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0x1EA8, x, y, z);
			if (!wood && (k == r0 || k == r1) && r1 > r0) {
				int fx = x;
				int fz = z;
				d.inStage(StageKind.EXTERIOR, () -> {
					d.set(fx, y, fz, d.mat(Role.BEAM).log(alongX ? Direction.Axis.X : Direction.Axis.Z));
				});
				wood = true;
			} else if (u < 0.45) {
				jar(d, x, y, z);
			}
		}
		return true;
	}

	/**
	 * A shade of reeds over a doorway at column {@code door} of a front wall whose face is {@code wallZ} (the shade stands
	 * on the cells in front of it, z = wallZ - 1), standing level y: three cells of reed mat at the lintel, on two poles
	 * where the ground before the door is free, else carried on the wall alone. Returns whether it was built.
	 */
	public static boolean doorShade(Draft d, int door, int wallZ, int y) {
		int z = wallZ - 1;
		if (z < 0 || y + 3 >= d.maxHeight()) {
			return false;
		}
		for (int x = door - 1; x <= door + 1; x++) {
			if (!Verandas.in(d, x, z)) {
				return false;
			}
		}
		if (!d.isEmpty(door, y + 2, z) && !d.isEmpty(door, y + 3, z)) {
			return false;
		}
		// over a viga's end the shade lies a course higher, on it
		int sy = d.isEmpty(door - 1, y + 2, z) && d.isEmpty(door, y + 2, z) && d.isEmpty(door + 1, y + 2, z) ? y + 2 : y + 3;
		if (sy + 1 >= d.maxHeight()) {
			return false;
		}
		boolean poles = true;
		for (int x : new int[]{door - 1, door + 1}) {
			poles &= d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && Spots.standable(d, x, y, z) && !Spots.claimed(d, x, y, z);
		}
		boolean posts = poles;
		d.inStage(StageKind.EXTERIOR, () -> {
			if (posts) {
				for (int x : new int[]{door - 1, door + 1}) {
					for (int yy = y; yy < sy; yy++) {
						if (d.isEmpty(x, yy, z)) {
							d.set(x, yy, z, pole(d, x, yy, z));
						}
					}
				}
			}
			for (int x = door - 1; x <= door + 1; x++) {
				if (d.isEmpty(x, sy, z)) {
					d.set(x, sy, z, reed(SlabType.BOTTOM));
				}
			}
		});
		return true;
	}

	/**
	 * A yard wall of mud along the given cells at standing level y: a course of the wall, rounded over with the coping a
	 * course up (head-high to a child, waist-high to a man), only where the cell is free and stands on ground. A dry-land
	 * house turns its back on the street behind one: the yard inside is where the household lives.
	 */
	public static void yardWall(Draft d, Iterable<int[]> cells, int y) {
		if (y + 2 >= d.maxHeight()) {
			return;
		}
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat cap = wall.has(Form.SLAB) ? wall : accent.has(Form.SLAB) ? accent : null;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				int x = c[0];
				int z = c[1];
				if (!Verandas.in(d, x, z) || !d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z) || Spots.claimed(d, x, y, z)
					|| !d.isSolid(x, y - 1, z) && y - 1 >= d.site().ground(x, z) || d.site().isWater(x, z)) {
					continue;
				}
				d.set(x, y, z, wall.full());
				d.set(x, y + 1, z, cap == null ? wall.full() : cap.slab(SlabType.BOTTOM));
			}
		}
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
		// the gap in the parapet where it arrives, so one steps off it onto the roof (H16)
		int px = x - Dirs.dx(out);
		int pz = z - Dirs.dz(out);
		if (d.isSolid(px, deck - 1, pz)) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int yy = deck; yy <= deck + 1; yy++) {
					if (d.get(px, yy, pz) != null && !d.isEmpty(px, yy, pz)) {
						d.set(px, yy, pz, Blocks.AIR.defaultBlockState());
					}
				}
			}
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
	 * Roof-terrace living on a deck {@code x0..x1 × z0..z1} (inside the parapet) at standing level y: one muted rug laid in
	 * a corner, a low seat, jars along the parapet; keeps (kx, kz), the way up, free. Returns a seat cell or null.
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
		// one rug, in one worn colour (a roof laid in the house's every cloth reads as a fairground)
		var carpet = net.minecraft.world.level.block.Blocks.CARPET.pick(dev.voidmc.hearth.build.kit.townhome.Life.mutedCloth(d, false));
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
