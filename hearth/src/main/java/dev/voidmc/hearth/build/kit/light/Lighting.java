package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Rooms;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Light by design: what an archetype calls to light a room the way it means it to look (a chandelier over the hall
 * table, sconces flanking the hearth, candles on the altar, a lantern by every door), in the culture's fittings
 * ({@link Manner}). Whatever the archetype leaves dark the compiler's pass ({@link AutoLighter}) lights afterwards; a
 * building lit by design gives it nothing to do.
 *
 * <p>Two levels:
 * <ul>
 *   <li>{@link #room} lights a finished room to the rule (8 everywhere one stands) with the pass's own taste: candles on
 *   its furniture when it is small, lanterns hung evenly along its middle (a wheel in a tall hall), then sconces,
 *   lamps and candles for the corners. Call it after the room's walls, ceiling and furniture are drawn.</li>
 *   <li>The fittings themselves, placed exactly where the archetype says: {@link #chandelier}, {@link #wheel},
 *   {@link #sconce}, {@link #flank}, {@link #candles}, {@link #lamp}, {@link #doorLamp}, {@link #post},
 *   {@link #underRug}. They check nothing: the archetype knows its plan (keep doorways, stairs and beds clear, hang
 *   from something sturdy, stand on something).</li>
 * </ul>
 * Every fitting is drawn in stage INTERIOR, or EXTERIOR for the door lamp and the yard post; supports come before what
 * they carry (the compiler orders them).
 */
public final class Lighting {
	private Lighting() {
	}

	// ------------------------------------------------------------------------------------------- rooms

	/**
	 * Lights the room standing at {@code floorY} over the local rectangle to the rule: every cell one can stand on in it
	 * at 8 or more. Returns how many fittings it put up.
	 */
	public static int room(Draft d, int x0, int z0, int x1, int z1, int floorY) {
		Session s = Session.forRoom(d, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), floorY);
		s.lightRooms();
		return s.log.size();
	}

	/** {@link #room(Draft, int, int, int, int, int)} for a furnished {@link Rooms.Room}. */
	public static int room(Draft d, Rooms.Room r) {
		return room(d, r.x0, r.z0, r.x1, r.z1, r.y);
	}

	// ------------------------------------------------------------------------------------------- fittings

	/**
	 * A lantern hung over (x, z) from the ceiling block at {@code ceilingY}, its lantern at {@code lanternY} (a chain
	 * fills the links between). Keep the lantern at least two above the floor of anyone walking under it.
	 */
	public static void chandelier(Draft d, int x, int z, int ceilingY, int lanternY) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = ceilingY - 1; y > lanternY; y--) {
				d.set(x, y, z, States.axis(m.chain, Direction.Axis.Y));
			}
			d.set(x, lanternY, z, States.lantern(m.lantern, true));
		}
	}

	/**
	 * A wheel chandelier for a tall hall: a chain from the ceiling at {@code ceilingY} to a hub at {@code hubY}, four arms,
	 * a lantern hung under each arm (in the north: candles on the arms round a lantern hung under the hub). Needs the
	 * 3 × 3 round the hub free at hub height and the row under it.
	 */
	public static void wheel(Draft d, int x, int z, int ceilingY, int hubY) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = ceilingY - 1; y > hubY; y--) {
				d.set(x, y, z, States.axis(m.chain, Direction.Axis.Y));
			}
			d.set(x, hubY, z, fence(m, x, hubY, z));
			for (Direction dir : Dirs.HORIZONTAL) {
				int ax = x + Dirs.dx(dir);
				int az = z + Dirs.dz(dir);
				d.set(ax, hubY, az, fence(m, ax, hubY, az));
				if (m.candleWheel) {
					d.set(ax, hubY + 1, az, Session.candles(m.candle(ax, hubY + 1, az), 4));
				} else {
					d.set(ax, hubY - 1, az, States.lantern(m.lantern, true));
				}
			}
			if (m.candleWheel) {
				d.set(x, hubY - 1, z, States.lantern(m.lantern, true));
			}
		}
	}

	/**
	 * A wall light in the cell (x, y, z) against the wall toward {@code toWall}: a lantern hung from a bracket (a post
	 * against the wall at y + 1), or a torch on the wall where the culture burns torches. {@code y} is the light's own
	 * row: two above the floor is right in a room four high. Where something is already drawn at y + 1 (a floor board
	 * over a gallery, a beam) the lantern hangs from it and no bracket replaces it, so call it after what is above.
	 */
	public static void sconce(Draft d, int x, int y, int z, Direction toWall) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			drawSconce(d, m, x, y, z, toWall);
		}
	}

	/** A pair of sconces either side of (x, z) along the wall toward {@code toWall}, {@code spread} cells out (a hearth, an altar, a door). */
	public static void flank(Draft d, int x, int y, int z, Direction toWall, int spread) {
		Direction along = Dirs.right(toWall);
		sconce(d, x + Dirs.dx(along) * spread, y, z + Dirs.dz(along) * spread, toWall);
		sconce(d, x - Dirs.dx(along) * spread, y, z - Dirs.dz(along) * spread, toWall);
	}

	/** Lit candles (1–4) in the culture's colours, standing at (x, y, z) on whatever is below (a table, a sill, an altar). */
	public static void candles(Draft d, int x, int y, int z, int count) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Session.candles(m.candle(x, y, z), count));
		}
	}

	/** A lantern standing at (x, y, z) on whatever is below it (a chest, a counter, a post). */
	public static void lamp(Draft d, int x, int y, int z) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.lantern(m.lantern, false));
		}
	}

	/**
	 * The lamp by a door: a lantern in the outside cell (x, y, z) hung from a bracket on the wall toward {@code toWall}
	 * (a torch where the culture burns torches). Put it beside the door at lintel height (two above the step).
	 */
	public static void doorLamp(Draft d, int x, int y, int z, Direction toWall) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			drawSconce(d, m, x, y, z, toWall);
		}
	}

	/**
	 * The culture's yard light standing on the ground at (x, y, z) (y the standing level): a post with a lantern on top,
	 * a brazier on a stone stand, a stone lantern under its cap, a tiki torch. Two or three blocks tall.
	 */
	public static void post(Draft d, int x, int y, int z) {
		Manner m = new Manner(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			switch (m.yard) {
				case POST -> {
					d.set(x, y, z, fence(m, x, y, z));
					d.set(x, y + 1, z, fence(m, x, y + 1, z));
					d.set(x, y + 2, z, States.lantern(m.lantern, false));
				}
				case TIKI -> {
					d.set(x, y, z, fence(m, x, y, z));
					d.set(x, y + 1, z, fence(m, x, y + 1, z));
					d.set(x, y + 2, z, Blocks.TORCH.defaultBlockState());
				}
				case BRAZIER -> {
					BlockState stand = m.stone().wall().at(x, y, z);
					d.set(x, y, z, stand == null ? Blocks.COBBLESTONE_WALL.defaultBlockState() : stand);
					d.set(x, y + 1, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				}
				case STONE_LANTERN -> {
					BlockState stand = m.stone().wall().at(x, y, z);
					BlockState cap = m.stone().slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).at(x, y + 2, z);
					d.set(x, y, z, stand == null ? Blocks.STONE_BRICK_WALL.defaultBlockState() : stand);
					d.set(x, y + 1, z, States.lantern(m.lantern, false));
					d.set(x, y + 2, z, cap == null ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : cap);
				}
			}
		}
	}

	/**
	 * Light hidden under a rug: the floor block under (x, y, z) swapped for the culture's glowing block and a carpet of
	 * the house's cloth laid over it at y. Only on a ground floor (a glowing block in a ceiling shows from below).
	 */
	public static void underRug(Draft d, int x, int y, int z) {
		Manner m = new Manner(d);
		var floorCell = d.cell(x, y - 1, z);
		StageKind floorStage = floorCell == null ? StageKind.FLOORS : floorCell.stage();
		try (var s = d.stage(floorStage)) {
			d.set(x, y - 1, z, m.glow.defaultBlockState());
		}
		if (d.isEmpty(x, y, z)) {
			var carpet = d.mat(dev.voidmc.hearth.build.style.Role.CLOTH).block(dev.voidmc.hearth.build.style.Form.CARPET, x, y, z);
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(x, y, z, (carpet == null ? Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED) : carpet).defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------------------------------- helpers

	private static void drawSconce(Draft d, Manner m, int x, int y, int z, Direction toWall) {
		if (m.torches) {
			d.set(x, y, z, States.wallTorch(m.torch, toWall.getOpposite()));
		} else {
			// the bracket only where nothing is drawn over the light: under a floor or a beam (a gallery, an arcade) the lantern
			// hangs from that instead, and the board over it stays (requests/b-sociallife.md §7)
			BlockState over = d.get(x, y + 1, z);
			if (over == null || over.isAir()
				|| !over.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
				d.set(x, y + 1, z, fence(m, x, y + 1, z));
			}
			d.set(x, y, z, States.lantern(m.lantern, true));
		}
	}

	private static BlockState fence(Manner m, int x, int y, int z) {
		BlockState f = m.bracketWood().fence().at(x, y, z);
		return f == null ? Blocks.OAK_FENCE.defaultBlockState() : f;
	}
}
