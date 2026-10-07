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
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The mesa's own manner of the dry south (the {@code ADOBE} tradition's pueblo habit): where the desert raises a
 * crenellated block under a dome, the mesa builds in terraces. Rooms are stacked back from the front so each roof is the
 * terrace of the storey over it; the parapets are low and rounded at the shoulders, never crested or raised at the
 * corners; the roof beams' ends (vigas) run out through every wall head; ladders lean against the walls up to the
 * terraces; and the churches carry their bells in a pierced wall (an espadaña) rather than under a dome.
 *
 * <p>An archetype keeps its own plan and calls {@link #crown} at the end of its draw: over its flat roof (the body
 * rectangle and eave registered with the draft) it stacks a stepped room on the back of the deck, door onto the terrace,
 * and sets a ladder from the ground up a side wall to the terrace. {@link Adobe#dome} draws no dome here, and
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
	 * Stacks a stepped upper room on the back of the draft's flat roof and leans a ladder up to its terrace (nothing when the
	 * roof is not flat, the deck is taken, the plot is too low or no wall takes a ladder). The room fills the deck's back
	 * part flush with the back and side walls, its front wall set back so the front of the deck is a terrace; inside, the
	 * stores of the house (barrels, chests, a bale) and a lamp. Returns the new top, or the eave when nothing was built.
	 */
	public static int crown(Draft d) {
		int[] r = d.bodyRect();
		int plate = d.eaveY();
		int top = d.maxHeight() - 1;
		if (r[2] - r[0] < 6 || r[3] - r[1] < 6 || plate + 7 > top) {
			return plate;
		}
		int deck = plate + 1;
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
		if (solid * 10 < all * 8) {
			return plate;
		}
		// the room's front wall: back from the front by a third of the depth (the terrace before it at least three deep)
		int depth = r[3] - r[1] + 1;
		int zf = r[1] + Math.max(3, depth / 3);
		if (r[3] - zf < 4) {
			return plate;
		}
		int x0 = r[0];
		int x1 = r[2];
		// narrow it where the deck carries something (a bell wall, a stair head, a dome's drum)
		while (x1 - x0 >= 5 && !clear(d, x0, zf, x1, r[3], deck)) {
			if (!clear(d, x0, zf, x0, r[3], deck)) {
				x0++;
			} else {
				x1--;
			}
		}
		if (x1 - x0 < 5 || !clear(d, x0, zf, x1, r[3], deck)) {
			return plate;
		}
		int[] ladder = ladderSpot(d, r, deck);
		if (ladder == null) {
			ladder = hatchSpot(d, r, plate, zf);
		}
		if (ladder == null) {
			return plate;
		}
		// the door onto the terrace where the deck before it is free (the middle first)
		int mid = (x0 + x1) / 2;
		int door = Integer.MIN_VALUE;
		for (int k = 0; k <= (x1 - x0) / 2 && door == Integer.MIN_VALUE; k++) {
			for (int x : new int[]{mid - k, mid + k}) {
				if (door == Integer.MIN_VALUE && x > x0 && x < x1 && d.isEmpty(x, deck, zf - 1) && d.isEmpty(x, deck + 1, zf - 1)
					&& d.isSolid(x, deck - 1, zf - 1)) {
					door = x;
				}
			}
		}
		if (door == Integer.MIN_VALUE) {
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
		d.inStage(StageKind.OPENINGS, () -> d.door(cx, deck, zf, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
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
		ladder(d, ladder, deck);
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
				if (!ring && !d.isSolid(x, deck - 1, z)) {
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
	 * A side wall that takes the ladder: {x, z, outX, groundY, facing} for a column of the left or right wall (front half
	 * first, where the terrace is) solid from the ground outside up to the deck, the cells outside it free and in the plot,
	 * the deck inside it free. Null when none does.
	 */
	private static int[] ladderSpot(Draft d, int[] r, int deck) {
		for (int z = r[1] + 1; z < r[3]; z++) {
			for (int side : new int[]{-1, 1}) {
				int x = side < 0 ? r[0] : r[2];
				int ox = x + side;
				int ix = x - side;
				if (ox < 0 || ox >= d.frontage() || d.site().isWater(ox, z)) {
					continue;
				}
				int g = Groundwork.surface(d, ox, z);
				// (the foot on the building's own level, where the way round it runs)
				if (Math.abs(g - Groundwork.surface(d, ox, r[1] - 1 >= 0 ? r[1] - 1 : 0)) > 1 || deck - g < 3 || deck - g > 12
					|| !d.isSolid(ox, g - 1, z) && !d.isAbsent(ox, g - 1, z)) {
					continue;
				}
				boolean ok = d.isEmpty(ix, deck, z) && d.isEmpty(ix, deck + 1, z) && d.isSolid(ix, deck - 1, z);
				for (int y = g; y <= deck + 1 && ok; y++) {
					ok = d.isEmpty(ox, y, z) && (y > deck - 1 || Openings.isWallBlock(d, x, y, z));
				}
				if (ok) {
					return new int[]{x, z, ox, g, side};
				}
			}
		}
		// failing the sides, the back wall
		int z = r[3];
		int oz = z + 1;
		if (oz < d.depth()) {
			for (int x = r[0] + 2; x <= r[2] - 2; x++) {
				int g = Groundwork.surface(d, x, oz);
				if (deck - g < 3 || deck - g > 12 || d.site().isWater(x, oz)) {
					continue;
				}
				boolean ok = d.isEmpty(x, deck, z - 1) && d.isEmpty(x, deck + 1, z - 1) && d.isSolid(x, deck - 1, z - 1);
				for (int y = g; y <= deck + 1 && ok; y++) {
					ok = d.isEmpty(x, y, oz) && (y > deck - 1 || Openings.isWallBlock(d, x, y, z));
				}
				if (ok) {
					return new int[]{x, z, x, g, 2, oz};
				}
			}
		}
		return null;
	}

	/**
	 * Failing a wall outside, a hatch: {x, z, -, standY, 3, facing index} for a cell inside the top storey against an outer
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

	/** The ladder from the ground to the terrace, through a gap in the parapet at its head (or up through a hatch). */
	private static void ladder(Draft d, int[] spot, int deck) {
		if (spot[4] == 3) {
			// the hatch: the ladder against the outer wall from the storey's floor up through the deck
			Direction toWall = Direction.from2DDataValue(spot[5]);
			Stairs.ladder(d, spot[0], spot[1], spot[3], deck - 1, toWall.getOpposite());
			return;
		}
		int x = spot[0];
		int z = spot[1];
		Direction facing = spot[4] < 0 ? Dirs.LEFT : spot[4] == 2 ? Dirs.BACK : Dirs.RIGHT;
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(x, deck, z, Blocks.AIR.defaultBlockState());
			d.set(x, deck + 1, z, Blocks.AIR.defaultBlockState());
			// the wall head under the gap stays as the step off the ladder
			if (!d.isSolid(x, deck - 1, z)) {
				d.set(x, deck - 1, z, d.mat(Role.WALL).full());
			}
		}
		Stairs.ladder(d, spot[2], spot[4] == 2 ? spot[5] : z, spot[3], deck, facing);
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
