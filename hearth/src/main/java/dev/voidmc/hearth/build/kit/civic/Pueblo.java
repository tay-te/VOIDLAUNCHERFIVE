package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The mesa's own manner of the dry south (the {@code ADOBE} tradition's pueblo habit): where the desert raises a
 * crenellated block under a dome, the mesa builds in terraces. Rooms are stacked back from the front so each roof is the
 * terrace of the storey over it; the parapets are low and rounded at the shoulders, never crested or raised at the
 * corners; the roof beams' ends (vigas) run out through every wall head; ladders climb through hatches to the
 * terraces; and the churches carry their bells in a pierced wall (an espadaña) rather than under a dome.
 *
 * <p>An archetype keeps its own plan and calls {@link #crown} at the end of its draw: over its flat roof (the body
 * rectangle and eave registered with the draft) it stacks a stepped room on the back of the deck, door onto the terrace,
 * and sets a ladder up through a hatch from the storey below to the terrace. {@link Adobe#dome} draws no dome here, and
 * {@link Adobe#roof} lays the mesa's parapet.
 */
public final class Pueblo {
	private Pueblo() {
	}

	/** Whether the draft builds in the pueblo habit: the mesa's styles. */
	public static boolean is(Draft d) {
		return d.style().culture() == Culture.MESA;
	}

	/**
	 * The portal of the mesa's houses of the town: a porch along the front wall (row {@code zWall}) from {@code x0} to
	 * {@code x1}, {@code depth} rows deep, on posts at its corners and every third column (never before a door), a beam
	 * along its front, vigas from the wall to the beam and a roof of slabs over them; three rows of head room under it.
	 * Cells already taken are left as they are. Returns whether it was built.
	 */
	public static boolean portal(Draft d, int x0, int x1, int zWall, int base, int depth) {
		int zo = zWall - depth;
		if (zo < 0 || x1 - x0 < 3 || base + 5 >= d.maxHeight()) {
			return false;
		}
		// (the vigas' row clear over the whole porch; a lamp or a bench under it may stay, and a course run along the
		// wall over it serves as the roof's edge there)
		for (int z = zo; z < zWall; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!d.isEmpty(x, base + 3, z)) {
					return false;
				}
			}
		}
		Mat beam = d.mat(Role.BEAM);
		Mat roof = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : d.mat(Role.ROOF);
		java.util.Set<Integer> doors = new java.util.HashSet<>();
		for (int x = x0; x <= x1; x++) {
			var st = d.get(x, base, zWall);
			if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				doors.add(x);
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = x0; x <= x1; x++) {
				boolean post = (x == x0 || x == x1 || (x - x0) % 3 == 0) && !doors.contains(x) && !doors.contains(x - 1) && !doors.contains(x + 1);
				if (post && d.isEmpty(x, base, zo) && d.isEmpty(x, base + 1, zo) && d.isEmpty(x, base + 2, zo) && d.isSolid(x, base - 1, zo)) {
					for (int y = base; y < base + 3; y++) {
						d.set(x, y, zo, beam.log(Direction.Axis.Y));
					}
				}
				d.set(x, base + 3, zo, beam.log(Direction.Axis.X));
				for (int z = zo + 1; z < zWall; z++) {
					d.set(x, base + 3, z, beam.log(Direction.Axis.Z));
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = zo; z < zWall; z++) {
				for (int x = x0; x <= x1; x++) {
					if (d.isEmpty(x, base + 4, z)) {
						d.set(x, base + 4, z, roof.slab(SlabType.BOTTOM));
					}
				}
			}
		}
		return true;
	}

	/**
	 * {@link #portal} along the front of the draft's body, at the floor of its front door (its registered entrance before
	 * the front wall), over the widest clear run round the door, two rows deep where the plot allows, one where not.
	 * Returns whether it was built.
	 */
	public static boolean portalFront(Draft d) {
		int[] r = d.bodyRect();
		for (int[] e : d.entrances()) {
			if (e[2] != r[1] - 1 || e[0] <= r[0] || e[0] >= r[2]) {
				continue;
			}
			for (int depth = 2; depth >= 1; depth--) {
				int zo = r[1] - depth;
				if (zo < 0) {
					continue;
				}
				// (over the widest run round the door where the vigas' row is clear: a sign or a lamp on the wall ends it)
				int a = e[0];
				int b = e[0];
				while (a - 1 >= r[0] && rowClear(d, a - 1, zo, r[1], e[1] + 3)) {
					a--;
				}
				while (b + 1 <= r[2] && rowClear(d, b + 1, zo, r[1], e[1] + 3)) {
					b++;
				}
				if (rowClear(d, e[0], zo, r[1], e[1] + 3) && b - a >= 3 && portal(d, a, b, r[1], e[1], depth)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The cells of column x from row zo to the wall's row (not included) are empty at height y. */
	private static boolean rowClear(Draft d, int x, int zo, int zWall, int y) {
		for (int z = zo; z < zWall; z++) {
			if (!d.isEmpty(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Stacks a stepped upper room on the back of the draft's flat roof with a ladder up to its terrace through a hatch
	 * (nothing when the roof is not flat, the deck is taken, the plot is too low or no wall under the terrace takes a ladder). The room fills the deck's back
	 * part flush with the back and side walls, its front wall set back so the front of the deck is a terrace; inside, the
	 * stores of the house (barrels, chests, a bale) and a lamp. Returns the new top, or the eave when nothing was built.
	 */
	public static int crown(Draft d) {
		return crown(d, d.bodyRect());
	}

	/** {@link #crown(Draft)} over the walls {@code r} ({x0, z0, x1, z1}) of the top storey (a set-back storey's own). */
	public static int crown(Draft d, int[] r) {
		int plate = d.eaveY();
		int top = d.maxHeight() - 1;
		if (r[2] - r[0] < 4 || r[3] - r[1] < 5 || plate + 6 > top) {
			return plate;
		}
		int deck = plate + 1;
		// the storey's own roof beams' ends run out through its wall head (where it has none yet), stepped room or none
		Adobe.vigas(d, r[0], r[1], r[2], r[3], plate - 1);
		// a flat roof: the deck solid over most of the inside, the air over it clear
		int solid = 0;
		int all = 0;
		for (int z = r[1] + 1; z < r[3]; z++) {
			for (int x = r[0] + 1; x < r[2]; x++) {
				all++;
				if (d.isSolid(x, plate, z) && d.isEmpty(x, deck, z)) {
					solid++;
				}
			}
		}
		if (solid * 10 < all * 7) {
			return plate;
		}
		// the room: the largest clear rectangle on the back of the deck that a hatch and a door serve, flush with the back
		// wall, its front wall set back at least a quarter of the depth (the terrace before it two deep at least), three by
		// two inside at the least
		int depth = r[3] - r[1] + 1;
		java.util.List<int[]> rooms = new java.util.ArrayList<>();
		for (int f = r[1] + Math.max(2, depth / 4); f <= r[3] - 3; f++) {
			for (int a = r[0]; a <= r[2] - 4; a++) {
				for (int b = r[2]; b >= a + 4; b--) {
					if (clear(d, a, f, b, r[3], deck)) {
						rooms.add(new int[]{f, a, b, (b - a - 1) * (r[3] - f - 1)});
					}
				}
			}
		}
		rooms.sort((p, q) -> Integer.compare(q[3], p[3]));
		int zf = Integer.MIN_VALUE;
		int x0 = 0;
		int x1 = -1;
		int door = Integer.MIN_VALUE;
		int[] ladder = null;
		for (int[] c : rooms) {
			// up through a hatch from the storey under the terrace (a ladder up an outside wall stands over a drop the whole way)
			int[] hatch = hatchSpot(d, r, plate, c[0]);
			// (or the house's own ladder already up through the roof before the room's line: the terrace is reached)
			if (hatch == null) {
				hatch = ownHatch(d, r, plate, c[0]);
			}
			if (hatch == null) {
				continue;
			}
			// the door onto the terrace where the deck before it is free (the middle first)
			int mid = (c[1] + c[2]) / 2;
			int dx = Integer.MIN_VALUE;
			for (int k = 0; k <= (c[2] - c[1]) / 2 && dx == Integer.MIN_VALUE; k++) {
				for (int x : new int[]{mid - k, mid + k}) {
					if (dx == Integer.MIN_VALUE && x > c[1] && x < c[2] && d.isEmpty(x, deck, c[0] - 1) && d.isEmpty(x, deck + 1, c[0] - 1)
						&& d.isSolid(x, deck - 1, c[0] - 1) && !(x == hatch[0] && c[0] - 1 == hatch[1])) {
						dx = x;
					}
				}
			}
			if (dx == Integer.MIN_VALUE) {
				continue;
			}
			zf = c[0];
			x0 = c[1];
			x1 = c[2];
			door = dx;
			ladder = hatch;
			break;
		}
		if (ladder == null) {
			return plate;
		}
		Mat wall = d.mat(Role.WALL);
		int h = 4;
		int up = deck + h - 1;
		// the room's walls rise off the deck (over the parapet where they stand on the outer walls)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, zf, x1, r[3])) {
				for (int y = deck; y <= up; y++) {
					d.set(c[0], y, c[1], wall.full());
				}
			}
			for (int z = zf + 1; z < r[3]; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					for (int y = deck; y < up; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		int roofTop = parapet(d, x0, zf, x1, r[3], up);
		Adobe.vigas(d, x0, zf, x1, r[3], up - 1);
		// the door onto the terrace, a light either side of it and in each end
		int cx = door;
		int fz = zf;
		d.inStage(StageKind.OPENINGS, () -> d.door(cx, deck, fz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		dev.voidmc.hearth.build.kit.Side front = dev.voidmc.hearth.build.kit.Side.of(x0, zf, x1, r[3], Dirs.FRONT);
		for (int x : new int[]{cx - 2, cx + 2}) {
			if (x > x0 && x < x1) {
				Openings.window(d, front, x, deck + 1, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, false, true));
			}
		}
		for (dev.voidmc.hearth.build.kit.Side s : dev.voidmc.hearth.build.kit.Side.of(x0, zf, x1, r[3])) {
			if (s.out() == Dirs.LEFT || s.out() == Dirs.RIGHT) {
				int col = s.column(s.middle());
				if (d.isEmpty(s.outX(s.middle(), 1), deck + 1, s.outZ(s.middle(), 1))) {
					Openings.window(d, s, col, deck + 1, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, false, true));
				}
			}
		}
		// the stores inside, a lamp
		Rooms.Room room = new Rooms.Room(x0 + 1, zf + 1, x1 - 1, r[3] - 1, deck, h - 1);
		room.keep(cx, zf + 1);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		Lighting.room(d, room);
		// the terrace before it: kept clear before the door, the ladder from the ground up the side wall
		d.poi(PoiType.VIEWPOINT, cx, deck, zf - 1, Dirs.FRONT);
		if (ladder[4] != 4) {
			ladder(d, ladder, deck);
		}
		return roofTop;
	}

	/** Nothing stands on the deck over the rectangle (its parapet ring aside) from the deck three rows up. */
	private static boolean clear(Draft d, int x0, int z0, int x1, int z1, int deck) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				boolean ring = x == x0 || x == x1 || z == z1;
				for (int y = deck; y < deck + 4; y++) {
					if (!d.isEmpty(x, y, z) && !(ring && y <= deck + 2 && (Openings.isWallBlock(d, x, y, z) || isCoping(d, x, y, z)))) {
						return false;
					}
				}
				// (the walls too: a wall over a stair's hatch takes the climber's head off)
				if (!d.isSolid(x, deck - 1, z)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isCoping(Draft d, int x, int y, int z) {
		var s = d.get(x, y, z);
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock || s.getBlock() instanceof net.minecraft.world.level.block.StairBlock);
	}

	/**
	 * A ladder the house already has up through its roof in front of the room's line {@code zf}: {x, z, -, -, 4} (the
	 * marker that no ladder is to be built), or null.
	 */
	private static int[] ownHatch(Draft d, int[] r, int plate, int zf) {
		for (int z = r[1] + 1; z < zf; z++) {
			for (int x = r[0] + 1; x < r[2]; x++) {
				var s = d.get(x, plate, z);
				if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
					return new int[]{x, z, 0, 0, 4, 0};
				}
			}
		}
		return null;
	}

	/**
	 * The hatch: {x, z, -, standY, 3, facing index} for a cell inside the top storey against an outer
	 * wall in front of the room's line (under the terrace), its column empty from the storey's floor to the roof, the wall
	 * behind it solid all the way, the floor before it free; the ladder climbs it through the deck. Null when none does.
	 */
	private static int[] hatchSpot(Draft d, int[] r, int plate, int zf) {
		for (int z = r[1] + 1; z < zf - 1; z++) {
			for (int x = r[0] + 1; x < r[2]; x++) {
				Direction toWall = x == r[0] + 1 ? Dirs.LEFT : x == r[2] - 1 ? Dirs.RIGHT : z == r[1] + 1 ? Dirs.FRONT : null;
				if (toWall == null) {
					continue;
				}
				// the storey's floor under the cell
				int y = plate - 1;
				while (y > plate - 7 && d.isEmpty(x, y, z)) {
					y--;
				}
				int stand = y + 1;
				if (plate - stand < 2 || !d.isSolid(x, y, z)) {
					continue;
				}
				int wx = x + Dirs.dx(toWall);
				int wz = z + Dirs.dz(toWall);
				int fx = x - Dirs.dx(toWall);
				int fz = z - Dirs.dz(toWall);
				boolean ok = d.isEmpty(fx, stand, fz) && d.isEmpty(fx, stand + 1, fz) && d.isSolid(fx, stand - 1, fz)
					&& d.isEmpty(fx, plate + 1, fz) && d.isEmpty(fx, plate + 2, fz) && d.isSolid(fx, plate, fz);
				for (int yy = stand; yy <= plate && ok; yy++) {
					ok = (yy == plate || d.isEmpty(x, yy, z)) && Openings.isWallBlock(d, wx, yy, wz) && !door(d, x, yy, z);
				}
				if (ok) {
					return new int[]{x, z, x, stand, 3, toWall.get2DDataValue()};
				}
			}
		}
		return null;
	}

	/** A door beside the cell (a ladder before a door blocks it). */
	private static boolean door(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			var s = d.get(x + Dirs.dx(dir), y, z + Dirs.dz(dir));
			if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				return true;
			}
		}
		return false;
	}

	/** The ladder against the outer wall from the storey's floor up through a hatch in the deck to the terrace. */
	private static void ladder(Draft d, int[] spot, int deck) {
		Direction toWall = Direction.from2DDataValue(spot[5]);
		Stairs.ladder(d, spot[0], spot[1], spot[3], deck - 1, toWall.getOpposite());
	}

	/**
	 * The mesa's parapet over walls {@code x0..x1 × z0..z1} whose top course is {@code plate}: the deck inside, one course of
	 * the wall's render all round under a coping, and at the four corners no coping (the shoulders rounded off, never
	 * raised). Returns the top.
	 */
	public static int parapet(Draft d, int x0, int z0, int x1, int z1, int plate) {
		Mat deck = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : d.mat(Role.ROOF_TRIM);
		Paint cap = coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, plate, z, deck.full());
				}
			}
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				d.set(c[0], plate, c[1], wall.full());
				if (!d.allowed(c[0], plate + 1, c[1])) {
					continue;
				}
				d.set(c[0], plate + 1, c[1], corner ? cap : wall.full());
				if (!corner && d.allowed(c[0], plate + 2, c[1])) {
					d.set(c[0], plate + 2, c[1], cap);
				}
			}
		}
		return plate + 2;
	}
}
