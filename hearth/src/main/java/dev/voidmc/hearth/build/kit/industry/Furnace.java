package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The fires of the works built in masonry and clay: the smelter's blast furnace (a battered stone tower with its tapping
 * arch, its charging gallery and its throat glowing under a tall stack), the clay shaft furnace of the dry lands (a
 * tapering cone of mud with tuyeres round its foot), and the trestled timber ramp a charger climbs with ore and charcoal.
 */
public final class Furnace {
	private Furnace() {
	}

	/**
	 * A blast furnace drawn: its 5 x 5 base at (x0, z0), the cell before its tapping arch, the gallery's standing level,
	 * the cell on the gallery before the charging hole, the top of its stack.
	 */
	public record Tower(int x0, int z0, int cx, int cz, int tapX, int tapZ, int gallery, int chargeX, int chargeZ, int top) {
	}

	/**
	 * A blast furnace over [x0..x0+4] × [z0..z0+4], its tapping arch in the front face, its charging hole toward
	 * {@code charge} (LEFT or RIGHT) on a gallery at standing level {@code gallery}, the stack up to {@code top}: base
	 * three courses of {@code stone} with quoins of {@code trim}, a battered course, a shaft three wide banded in
	 * {@code trim}, the gallery's deck round the shaft on corner posts and brackets with a railing, the throat's fire
	 * glowing in the charging hole and smoking up a hollow stack of {@code flue} with a corbelled crown.
	 */
	public static Tower tower(Draft d, int x0, int z0, Direction charge, int gallery, int top, Fam stone, Fam trim, Fam flue, Mat deck, Mat frame,
		dev.voidmc.hearth.build.style.Habits.@org.jspecify.annotations.Nullable RoofForm house) {
		int cx = x0 + 2;
		int cz = z0 + 2;
		int x1 = x0 + 4;
		int z1 = z0 + 4;
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		int s = gallery;
		top = Math.min(top, d.maxHeight() - 1);
		try (var sc = d.stage(StageKind.WALLS)) {
			// the base: three courses, quoined at the corners
			for (int y = 0; y <= 2; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
						boolean quoin = corner || ((x == x0 || x == x1) && (z == z0 + 1 || z == z1 - 1) || (z == z0 || z == z1) && (x == x0 + 1 || x == x1 - 1))
							&& ((y & 1) == 0);
						d.set(x, y, z, quoin ? trim.full() : stone.full());
					}
				}
			}
			// the battered course: the outer ring steps in
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				Direction in = Dirs.of(c[0] == x0 ? 1 : c[0] == x1 ? -1 : 0, c[1] == z0 ? 1 : c[1] == z1 ? -1 : 0);
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				if (corner) {
					Direction a = c[0] == x0 ? Dirs.RIGHT : Dirs.LEFT;
					d.set(c[0], 3, c[1], trim.stairsOrSlab(a, Half.BOTTOM));
				} else if (in != null) {
					d.set(c[0], 3, c[1], trim.stairsOrSlab(in, Half.BOTTOM));
				}
			}
			// the shaft
			for (int y = 3; y <= s - 1; y++) {
				boolean band = y > 3 && (y - 3) % 4 == 0;
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int z = cz - 1; z <= cz + 1; z++) {
						boolean corner = x != cx && z != cz;
						if (x == cx && z == cz && y == s - 1) {
							d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
						} else {
							d.set(x, y, z, band || corner && ((y & 1) == 1) ? trim.full() : stone.full());
						}
					}
				}
			}
		}
		// the tapping arch in the front face: three wide at the floor, closing to one, the hearth glowing deep in it
		try (var sc = d.stage(StageKind.OPENINGS)) {
			for (int k = -1; k <= 1; k++) {
				d.set(cx + k, 0, z0, Blocks.AIR.defaultBlockState());
			}
			d.set(cx, 1, z0, Blocks.AIR.defaultBlockState());
			d.set(cx - 1, 1, z0, trim.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(cx + 1, 1, z0, trim.stairsOrSlab(Dirs.RIGHT, Half.TOP));
		}
		// the hearth deep in the arch, and the tap in its left leg where the iron runs out to the casting floor (a tap set
		// back behind the leg was out of everyone's reach)
		try (var sc = d.stage(StageKind.INTERIOR)) {
			d.set(cx, 0, z0 + 1, States.facing(Blocks.BLAST_FURNACE, Dirs.FRONT).trySetValue(BlockStateProperties.LIT, true));
			d.set(cx - 1, 0, z0, Blocks.LAVA_CAULDRON.defaultBlockState());
			d.set(cx - 1, 0, z0 + 1, stone.full().at(cx - 1, 0, z0 + 1));
			d.set(cx + 1, 0, z0 + 1, stone.full().at(cx + 1, 0, z0 + 1));
		}
		// the tuyere on the far side from the charging hole: an iron nozzle in a little opening
		int tx = charge == Dirs.LEFT ? x1 : x0;
		try (var sc = d.stage(StageKind.INTERIOR)) {
			d.set(tx, 1, cz, Blocks.IRON_BARS.defaultBlockState());
		}
		// the gallery: a deck round the shaft at s - 1, corner posts down to the batter, brackets under its edges
		int dy = s - 1;
		if (s > 4) {
			try (var sc = d.stage(StageKind.FRAME)) {
				for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
					for (int y = 4; y < dy; y++) {
						d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
					}
				}
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
					if (!corner && (c[0] == cx || c[1] == cz) && dy - 1 >= 4 && d.isEmpty(c[0], dy - 1, c[1])) {
						Direction in = Dirs.of(c[0] == x0 ? 1 : c[0] == x1 ? -1 : 0, c[1] == z0 ? 1 : c[1] == z1 ? -1 : 0);
						if (in != null && frame.has(Form.STAIRS)) {
							d.set(c[0], dy - 1, c[1], frame.stairs(in.getOpposite(), Half.TOP));
						}
					}
				}
			}
		}
		try (var sc = d.stage(StageKind.FLOORS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				d.set(c[0], dy, c[1], deck.full());
			}
		}
		// round the gallery: a railing, open toward the charging side where the bridge comes in; or, where the stack has
		// height to spare, the charging house: timber walls on the deck under their own roof, the stack through it
		int bridgeX = charge == Dirs.LEFT ? x0 : x1;
		dev.voidmc.hearth.build.kit.Roofs.Roof houseRoof = null;
		boolean housed = house != null && top - s >= 9;
		if (housed) {
			Mat wall = d.mat(dev.voidmc.hearth.build.style.Role.GABLE);
			if (!Works.isWood(wall.mainOr(Form.FULL, Blocks.STONE))) {
				wall = deck;
			}
			int plate = s + 2;
			try (var sc = d.stage(StageKind.WALLS)) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
					boolean door = c[0] == bridgeX && c[1] == cz;
					boolean window = !corner && !door && (c[0] == cx || c[1] == cz);
					for (int y = s; y <= plate; y++) {
						if (door && y < plate) {
							d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
						} else if (corner) {
							d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
						} else if (y == plate) {
							Direction.Axis ax = c[1] == z0 || c[1] == z1 ? Direction.Axis.X : Direction.Axis.Z;
							d.set(c[0], y, c[1], frame.log(ax));
						} else if (window && y == s + 1 && d.mat(dev.voidmc.hearth.build.style.Role.SHUTTER).has(Form.TRAPDOOR)) {
							Direction out = Dirs.of(c[0] == x0 ? -1 : c[0] == x1 ? 1 : 0, c[1] == z0 ? -1 : c[1] == z1 ? 1 : 0);
							d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
							if (out != null) {
								d.set(c[0] + Dirs.dx(out), y, c[1] + Dirs.dz(out),
									d.mat(dev.voidmc.hearth.build.style.Role.SHUTTER).trapdoor(out, Half.BOTTOM, true));
							}
						} else {
							d.set(c[0], y, c[1], wall.full());
						}
					}
				}
			}
			var opts = dev.voidmc.hearth.build.kit.Roofs.Opts.of(d).withEaves(1, 1);
			opts = opts.withPitch(Math.max(0.5, Math.min(1.0, opts.pitch())));
			Direction.Axis ridge = charge.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
			var f = house == dev.voidmc.hearth.build.style.Habits.RoofForm.FLAT ? dev.voidmc.hearth.build.style.Habits.RoofForm.HIP : house;
			houseRoof = dev.voidmc.hearth.build.kit.Roofs.plan(d, x0, z0, x1, z1, plate, f, ridge, opts, false, false).draw(d).roof();
		} else {
			Mat rail = Works.timber(d);
			try (var sc = d.stage(StageKind.EXTERIOR)) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					if (c[0] == bridgeX && c[1] == cz) {
						continue;
					}
					if (s + 1 < d.maxHeight() && rail.has(Form.FENCE)) {
						d.set(c[0], s, c[1], rail.fence());
					}
				}
			}
		}
		// the throat: the shaft's ring round a fire, its charging hole open toward the gallery's charging side
		int hx = cx + Dirs.dx(charge);
		try (var sc = d.stage(StageKind.WALLS)) {
			for (int y = s; y <= Math.min(s + 1, top - 2); y++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int z = cz - 1; z <= cz + 1; z++) {
						if (x == cx && z == cz) {
							continue;
						}
						if (x == hx && z == cz) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						} else {
							d.set(x, y, z, stone.full());
						}
					}
				}
			}
			d.set(cx, s, cz, Stack.fire());
			// a grate of bars over the throat: the smoke goes up the stack, nobody steps into it from the charging hole
			d.set(cx, s + 1, cz, Blocks.IRON_BARS.defaultBlockState());
		}
		// the stack: hollow over the fire, rounded at the corners, banded, crowned with a corbel course and a lip
		int stackTop = top;
		boolean walls = flue.has(Form.WALL);
		try (var sc = d.stage(StageKind.ROOF)) {
			for (int y = s + 2; y <= stackTop - 2; y++) {
				boolean band = (y - s) % 5 == 0;
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int z = cz - 1; z <= cz + 1; z++) {
						if (x == cx && z == cz) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
							continue;
						}
						boolean corner = x != cx && z != cz;
						if (corner && walls && y > s + 2) {
							d.set(x, y, z, band && trim.has(Form.WALL) ? trim.wall() : flue.wall());
						} else {
							d.set(x, y, z, band ? trim.full() : flue.full());
						}
					}
				}
			}
			// a lintel over the charging hole
			if (s + 2 <= stackTop - 2) {
				d.set(hx, s + 2, cz, trim.full());
			}
			if (stackTop - 1 > s + 2) {
				int y = stackTop - 1;
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int z = cz - 1; z <= cz + 1; z++) {
						if (x == cx && z == cz) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
							continue;
						}
						d.set(x, y, z, trim.full());
						if (trim.has(Form.SLAB)) {
							d.set(x, y + 1, z, trim.slab(SlabType.BOTTOM));
						}
					}
				}
				for (int[] c : Draft.ring(cx - 2, cz - 2, cx + 2, cz + 2)) {
					boolean corner = (c[0] == cx - 2 || c[0] == cx + 2) && (c[1] == cz - 2 || c[1] == cz + 2);
					Direction in = Dirs.of(c[0] < cx - 1 ? 1 : c[0] > cx + 1 ? -1 : 0, c[1] < cz - 1 ? 1 : c[1] > cz + 1 ? -1 : 0);
					if (corner || in == null || !d.allowed(c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
						continue;
					}
					d.set(c[0], y, c[1], trim.stairsOrSlab(in, Half.TOP));
				}
			}
		}
		int chx = cx + Dirs.dx(charge) * 2;
		return new Tower(x0, z0, cx, cz, cx, z0 - 1, s, chx, cz, stackTop);
	}

	/**
	 * A timber ramp climbing toward {@code up} from (x, z) to standing level {@code rise}: plank stairs on a stringer of
	 * upside-down stairs, a second stringer beside it carrying a fence for a handrail on the {@code rail} side, trestle
	 * posts down to the ground every third step, and a landing at the top. Returns the landing cell {x, z}.
	 */
	public static int[] ramp(Draft d, int x, int z, Direction up, int rise, Direction rail, Mat wood, Mat post) {
		Mat fence = Works.timber(d);
		int rx = x + Dirs.dx(rail);
		int rz = z + Dirs.dz(rail);
		try (var sc = d.stage(StageKind.FLOORS)) {
			for (int i = 0; i < rise; i++) {
				int px = x + Dirs.dx(up) * i;
				int pz = z + Dirs.dz(up) * i;
				int ox = rx + Dirs.dx(up) * i;
				int oz = rz + Dirs.dz(up) * i;
				d.set(px, i, pz, wood.stairs(up, Half.BOTTOM));
				for (int h = 1; h <= 2; h++) {
					if (d.isAbsent(px, i + h, pz)) {
						d.set(px, i + h, pz, Blocks.AIR.defaultBlockState());
					}
				}
				if (i >= 1) {
					d.set(px, i - 1, pz, wood.stairs(up.getOpposite(), Half.TOP));
					if (Works.in(d, ox, oz)) {
						d.set(ox, i - 1, oz, wood.stairs(up.getOpposite(), Half.TOP));
					}
				}
				if (Works.in(d, ox, oz) && fence.has(Form.FENCE) && (i >= 1 || d.isEmpty(ox, i, oz))) {
					if (i == 0) {
						d.set(ox, 0, oz, post.log(Direction.Axis.Y));
					}
					d.set(ox, i, oz, i == 0 ? post.log(Direction.Axis.Y) : fence.fence());
				}
			}
		}
		// trestles under every third step
		try (var sc = d.stage(StageKind.FRAME)) {
			for (int i = 3; i < rise; i += 3) {
				for (int[] c : new int[][]{{x + Dirs.dx(up) * i, z + Dirs.dz(up) * i}, {rx + Dirs.dx(up) * i, rz + Dirs.dz(up) * i}}) {
					if (!Works.in(d, c[0], c[1])) {
						continue;
					}
					int g = d.site().ground(c[0], c[1]);
					for (int y = Math.min(0, g); y <= i - 2; y++) {
						if (d.isAbsent(c[0], y, c[1]) || d.isEmpty(c[0], y, c[1])) {
							d.set(c[0], y, c[1], post.log(Direction.Axis.Y));
						}
					}
				}
			}
		}
		int lx = x + Dirs.dx(up) * rise;
		int lz = z + Dirs.dz(up) * rise;
		try (var sc = d.stage(StageKind.FLOORS)) {
			d.set(lx, rise - 1, lz, wood.full());
			if (Works.in(d, rx + Dirs.dx(up) * rise, rz + Dirs.dz(up) * rise)) {
				d.set(rx + Dirs.dx(up) * rise, rise - 1, rz + Dirs.dz(up) * rise, wood.full());
			}
		}
		// the landing on its post
		try (var sc = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{lx, lz}, {rx + Dirs.dx(up) * rise, rz + Dirs.dz(up) * rise}}) {
				if (!Works.in(d, c[0], c[1])) {
					continue;
				}
				int g = d.site().ground(c[0], c[1]);
				for (int y = Math.min(0, g); y <= rise - 2; y++) {
					if (d.isAbsent(c[0], y, c[1]) || d.isEmpty(c[0], y, c[1])) {
						d.set(c[0], y, c[1], post.log(Direction.Axis.Y));
					}
				}
			}
		}
		return new int[]{lx, lz};
	}

	/**
	 * A clay shaft furnace on whole centre (cx, cz): a cone of mud and clay tapering from three across to one, about
	 * {@code height} tall, tuyeres of fired clay standing out of its foot on three sides, the tapping arch with its glow
	 * toward {@code front}, smoke from its mouth. Returns the cell before the arch.
	 */
	public static int[] clay(Draft d, int cx, int cz, int height, Direction front) {
		Block[] mud = {Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.TERRACOTTA};
		Paint body = (x, y, z) -> Heap.pick(d, 41, x, y, z, mud);
		int h = Math.max(3, Math.min(height, d.maxHeight() - 2));
		Shape.body(d, Shape.cone(cx, cz, 1.6, h - 0.5), 0, cx - 2, cz - 2, cx + 2, cz + 2, new Shape.Skin(body, Blocks.MUD_BRICK_STAIRS,
			Blocks.MUD_BRICK_SLAB), null, StageKind.WALLS);
		// the mouth: the top course made a ring round the fire
		int top = 0;
		for (int y = h + 1; y >= 0; y--) {
			BlockState b = d.get(cx, y, cz);
			if (b != null && !b.isAir()) {
				top = y;
				break;
			}
		}
		try (var sc = d.stage(StageKind.WALLS)) {
			d.set(cx, top, cz, Stack.fire());
			d.set(cx, top - 1, cz, Blocks.HAY_BLOCK.defaultBlockState());
		}
		// tuyeres on the three other sides, the arch and its glow at the front
		try (var sc = d.stage(StageKind.INTERIOR)) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int ox = cx + Dirs.dx(dir) * 2;
				int oz = cz + Dirs.dz(dir) * 2;
				if (dir == front) {
					int fx = cx + Dirs.dx(dir);
					int fz = cz + Dirs.dz(dir);
					d.set(fx, 0, fz, States.facing(Blocks.BLAST_FURNACE, front).trySetValue(BlockStateProperties.LIT, true));
					continue;
				}
				if (Works.in(d, ox, oz) && d.isEmpty(ox, 0, oz)) {
					d.set(ox, 0, oz, Blocks.MUD_BRICK_WALL.defaultBlockState());
				}
			}
		}
		return new int[]{cx + Dirs.dx(front) * 2, cz + Dirs.dz(front) * 2};
	}

	/**
	 * A pair of bellows worked toward {@code toward} (the tuyere): two wedges of boards and leather side by side along
	 * {@code along}, their nozzles to the furnace, a rocking beam over them on a post. Cells: (x, z) and the next along.
	 */
	public static void bellows(Draft d, int x, int y, int z, Direction toward, Direction along, Mat wood) {
		Block leather = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN);
		Block leatherStairs = Blocks.WOOL_STAIRS.pick(net.minecraft.world.item.DyeColor.BROWN);
		try (var sc = d.stage(StageKind.INTERIOR)) {
			for (int k = 0; k <= 1; k++) {
				int px = x + Dirs.dx(along) * k;
				int pz = z + Dirs.dz(along) * k;
				// the board wedge: the nozzle end low toward the furnace, the leather swelling behind
				d.set(px, y, pz, wood.stairs(toward.getOpposite(), Half.BOTTOM));
				int bx = px - Dirs.dx(toward);
				int bz = pz - Dirs.dz(toward);
				if (Works.in(d, bx, bz) && d.isEmpty(bx, y, bz)) {
					d.set(bx, y, bz, Noise.unit(d.seed(), px, y, pz) < 0.5 ? leather.defaultBlockState()
						: States.stairs(leatherStairs, toward.getOpposite(), Half.BOTTOM));
				}
			}
		}
	}
}
