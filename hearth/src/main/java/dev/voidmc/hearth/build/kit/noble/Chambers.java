package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The rooms of a lord's seat, furnished for who uses them: the hall with its dais and throne under the lord's banners,
 * the high table and the long tables of the household; the lord's bedchamber; the chapel niche; the steward's counting
 * room with his desk and the strongbox; the kitchen, the guards' room, the stores. Each takes the {@link Rooms.Room} it
 * furnishes (the caller keeps its doors and stairs free in it), claims the cells of what it places so the room stays
 * walkable, and registers what folk use.
 */
public final class Chambers {
	private Chambers() {
	}

	/** Claims a cell of the room for a piece (true if it is free and taking it cuts nobody off). */
	public static boolean take(Rooms.Room r, int x, int z) {
		return r.inside(x, z) && r.claim(x, z);
	}

	/**
	 * A free cell against a wall of the room for a piece facing into it: the wall toward {@code prefer} first, from its
	 * middle outward, then the others; the cell must be empty with solid wall behind it at its own height and the one
	 * above, and its front free. Returns {x, z, wallDir index in {@link Dirs#HORIZONTAL}} or null.
	 */
	public static int[] spot(Draft d, Rooms.Room r, Direction prefer) {
		java.util.List<Direction> walls = new java.util.ArrayList<>();
		walls.add(prefer);
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dir != prefer) {
				walls.add(dir);
			}
		}
		int y = r.y;
		for (Direction wall : walls) {
			Direction across = Dirs.right(wall);
			Direction into = wall.getOpposite();
			int wx = wall == Dirs.RIGHT ? r.x1 : wall == Dirs.LEFT ? r.x0 : (r.x0 + r.x1) / 2;
			int wz = wall == Dirs.BACK ? r.z1 : wall == Dirs.FRONT ? r.z0 : (r.z0 + r.z1) / 2;
			int half = wall.getAxis() == Direction.Axis.Z ? r.width() / 2 + 1 : r.length() / 2 + 1;
			for (int k = 0; k <= half; k++) {
				for (int sign : new int[]{1, -1}) {
					if (k == 0 && sign < 0) {
						continue;
					}
					int x = wx + Dirs.dx(across) * k * sign;
					int z = wz + Dirs.dz(across) * k * sign;
					// slide to the wall (round rooms)
					while (r.inside(x + Dirs.dx(wall), z + Dirs.dz(wall)) && d.isEmpty(x + Dirs.dx(wall), y, z + Dirs.dz(wall))) {
						x += Dirs.dx(wall);
						z += Dirs.dz(wall);
					}
					int fx = x + Dirs.dx(into);
					int fz = z + Dirs.dz(into);
					if (!r.inside(x, z) || !r.inside(fx, fz) || !d.isEmpty(x, y, z) || !d.isEmpty(fx, y, fz) || !d.isSolid(x, y - 1, z)) {
						continue;
					}
					if (!d.isSolid(x + Dirs.dx(wall), y, z + Dirs.dz(wall)) || !d.isSolid(x + Dirs.dx(wall), y + 1, z + Dirs.dz(wall))) {
						continue;
					}
					if (take(r, x, z)) {
						r.keep(fx, fz);
						return new int[]{x, z, java.util.Arrays.asList(Dirs.HORIZONTAL).indexOf(wall)};
					}
				}
			}
		}
		return null;
	}

	/**
	 * The hall: a dais a step up across the end toward {@code head}, the throne in its middle with the lord's banners on
	 * the wall behind and candle stands either side, a runner of carpet down the middle, long tables with benches either
	 * side where the hall is wide enough, banners along the walls. Registers THRONE, the LORD's job site before it,
	 * TABLEs, SEATs and a MEETING_POINT.
	 */
	public static void hall(Draft d, Dress m, Rooms.Room r, Direction head, boolean tables) {
		int y = r.y;
		Direction toHall = head.getOpposite();
		Direction across = Dirs.right(head);
		boolean alongZ = head.getAxis() == Direction.Axis.Z;
		int hx = head == Dirs.RIGHT ? r.x1 : head == Dirs.LEFT ? r.x0 : (r.x0 + r.x1) / 2;
		int hz = head == Dirs.BACK ? r.z1 : head == Dirs.FRONT ? r.z0 : (r.z0 + r.z1) / 2;
		int len = alongZ ? r.length() : r.width();
		int wide = alongZ ? r.width() : r.length();
		int daisDepth = len >= 10 ? 2 : 1;
		// the throne against the end wall, facing down the hall, its arms posts
		int tx = hx;
		int tz = hz;
		boolean placed = false;
		for (int off : new int[]{0, 1, -1, 2, -2, 3, -3}) {
			tx = hx + Dirs.dx(across) * off;
			tz = hz + Dirs.dz(across) * off;
			if (r.inside(tx + Dirs.dx(toHall), tz + Dirs.dz(toHall)) && d.isEmpty(tx, y, tz) && take(r, tx, tz)) {
				placed = true;
				break;
			}
		}
		if (!placed) {
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(tx, y, tz, m.furniture.full());
			d.set(tx, y + 1, tz, m.furniture.stairsOrSlab(head, Half.BOTTOM));
			for (int a : new int[]{-1, 1}) {
				int ax = tx + Dirs.dx(across) * a;
				int az = tz + Dirs.dz(across) * a;
				if (take(r, ax, az)) {
					d.set(ax, y, az, m.furniture.full());
					d.set(ax, y + 1, az, m.furniture.fence());
				}
			}
		}
		d.poi(PoiType.THRONE, tx, y + 1, tz, toHall);
		d.poi(PoiType.SEAT, tx, y + 1, tz, toHall);
		// the dais: bottom slabs across the end in front of the throne (a step up, walked on)
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int k = 1; k <= daisDepth; k++) {
				for (int a = -(wide / 2); a <= wide / 2; a++) {
					int x = hx + Dirs.dx(toHall) * (k - 1) + Dirs.dx(across) * a;
					int z = hz + Dirs.dz(toHall) * (k - 1) + Dirs.dz(across) * a;
					if (k == 1 && Math.abs(a) <= 1 || !r.inside(x, z) || !d.isEmpty(x, y, z) || !d.isSolid(x, y - 1, z)) {
						continue;
					}
					if (k == daisDepth && daisDepth > 1) {
						continue;
					}
					d.set(x, y, z, m.furniture.slab(SlabType.BOTTOM));
				}
			}
		}
		int jx = tx + Dirs.dx(toHall);
		int jz = tz + Dirs.dz(toHall);
		if (r.inside(jx, jz)) {
			r.keep(jx, jz);
			d.poi(PoiType.JOB_SITE, jx, y, jz, head, JobType.LORD);
		}
		// the lord's banners on the wall behind and beside the throne
		for (int a : new int[]{-2, 0, 2}) {
			int bx = tx + Dirs.dx(across) * a;
			int bz = tz + Dirs.dz(across) * a;
			if (r.inside(bx, bz) && d.isEmpty(bx, y + 3, bz) && d.isSolid(bx + Dirs.dx(head), y + 3, bz + Dirs.dz(head))) {
				Details.wallBanner(d, bx, y + 3, bz, toHall, a == 0 ? 0 : 1);
			}
		}
		// candle stands either side of the dais
		for (int a : new int[]{-2, 2}) {
			int cx = tx + Dirs.dx(across) * a + Dirs.dx(toHall) * daisDepth;
			int cz = tz + Dirs.dz(across) * a + Dirs.dz(toHall) * daisDepth;
			if (d.isEmpty(cx, y, cz) && d.isSolid(cx, y - 1, cz) && take(r, cx, cz)) {
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(cx, y, cz, m.furniture.fence());
				}
				Lighting.candles(d, cx, y + 1, cz, 3);
			}
		}
		// long tables either side of a runner, benches outside them
		if (tables && wide >= 7 && len >= daisDepth + 6) {
			int start = daisDepth + 2;
			int run = len - start - 1;
			for (int a : new int[]{-2, 2}) {
				int sx = hx + Dirs.dx(toHall) * start + Dirs.dx(across) * a;
				int sz = hz + Dirs.dz(toHall) * start + Dirs.dz(across) * a;
				boolean ok = true;
				for (int k = 0; k < run; k++) {
					int x = sx + Dirs.dx(toHall) * k;
					int z = sz + Dirs.dz(toHall) * k;
					if (!d.isEmpty(x, y, z) || !take(r, x, z)) {
						ok = false;
						run = k;
						break;
					}
				}
				if (run >= 3) {
					Furniture.longTable(d, sx, y, sz, toHall, run);
					for (int k = 0; k < run; k += 2) {
						int bx = sx + Dirs.dx(toHall) * k + Dirs.dx(across) * Integer.signum(a);
						int bz = sz + Dirs.dz(toHall) * k + Dirs.dz(across) * Integer.signum(a);
						if (d.isEmpty(bx, y, bz) && take(r, bx, bz)) {
							Furniture.chair(d, bx, y, bz, a < 0 ? across.getOpposite() : across);
						}
					}
				}
			}
		}
		Block runner = m.cloth.palette().main().block(Form.CARPET);
		if (runner != null) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int k = daisDepth; k < len; k++) {
					int x = hx + Dirs.dx(toHall) * k;
					int z = hz + Dirs.dz(toHall) * k;
					if (r.inside(x, z) && d.isEmpty(x, y, z) && d.isSolid(x, y - 1, z) && d.isEmpty(x, y + 1, z)) {
						d.set(x, y, z, runner.defaultBlockState());
					}
				}
			}
		}
		// banners along the side walls
		for (int k = daisDepth + 1; k < len - 1; k += 3) {
			for (int a : new int[]{-1, 1}) {
				Direction face = a < 0 ? across : across.getOpposite();
				int ex = hx + Dirs.dx(toHall) * k;
				int ez = hz + Dirs.dz(toHall) * k;
				while (r.inside(ex - Dirs.dx(face), ez - Dirs.dz(face))) {
					ex -= Dirs.dx(face);
					ez -= Dirs.dz(face);
				}
				if (d.isEmpty(ex, y + 3, ez) && d.isSolid(ex - Dirs.dx(face), y + 3, ez - Dirs.dz(face))) {
					Details.wallBanner(d, ex, y + 3, ez, face, k);
				}
			}
		}
		int mx = hx + Dirs.dx(toHall) * (len / 2);
		int mz = hz + Dirs.dz(toHall) * (len / 2);
		d.poi(PoiType.MEETING_POINT, mx, y, mz, head);
	}

	/**
	 * A chapel niche against the wall toward {@code wall} at (x, z): an altar block with candles, flowers beside it, a
	 * banner over it, a kneeler before it. Registers the ALTAR and a SEAT.
	 */
	public static void niche(Draft d, Dress m, Rooms.Room r, int x0, int z0, Direction prefer) {
		int y = r.y;
		int[] at = spot(d, r, prefer);
		if (at == null) {
			return;
		}
		int x = at[0];
		int z = at[1];
		Direction wall = Dirs.HORIZONTAL[at[2]];
		Direction into = wall.getOpposite();
		Block chiseled = d.mat(Role.ACCENT).mainOr(Form.FULL, Blocks.CHISELED_STONE_BRICKS);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, chiseled.defaultBlockState());
		}
		Lighting.candles(d, x, y + 1, z, 3);
		Direction across = Dirs.right(wall);
		int px = x + Dirs.dx(across);
		int pz = z + Dirs.dz(across);
		if (d.isEmpty(px, y, pz) && take(r, px, pz)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(px, y, pz, m.dress.stairsOrSlab(across.getOpposite(), Half.TOP));
			}
			Garden.pot(d, px, y + 1, pz);
		}
		if (d.isEmpty(x, y + 2, z) && d.isSolid(x + Dirs.dx(wall), y + 2, z + Dirs.dz(wall))) {
			Details.wallBanner(d, x, y + 2, z, into, 3);
		}
		int ax = x + Dirs.dx(into);
		int az = z + Dirs.dz(into);
		if (r.inside(ax, az)) {
			r.keep(ax, az);
		}
		d.poi(PoiType.ALTAR, ax, y, az, wall);
	}

	/**
	 * The steward's desk at (x, z) against the wall toward {@code wall}: a lectern with the rolls, the strongbox beside it,
	 * shelves of books. Registers the STEWARD's job site, a LECTERN and the STRONGBOX.
	 */
	public static void desk(Draft d, Dress m, Rooms.Room r, int x0, int z0, Direction prefer) {
		int y = r.y;
		int[] at = spot(d, r, prefer);
		if (at == null) {
			return;
		}
		int x = at[0];
		int z = at[1];
		Direction wall = Dirs.HORIZONTAL[at[2]];
		Direction into = wall.getOpposite();
		int fx = x + Dirs.dx(into);
		int fz = z + Dirs.dz(into);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.LECTERN, into), BlockEntities.lectern("The Rolls", "the Steward",
				List.of("Rents of the manor at Michaelmas: received in full, save the miller's.", "Owed to the lord: forty days of carting and the wood for the hall.")));
		}
		d.poi(PoiType.LECTERN, fx, y, fz, wall);
		d.poi(PoiType.JOB_SITE, fx, y, fz, wall, JobType.STEWARD);
		Direction across = Dirs.right(wall);
		int sx = x + Dirs.dx(across);
		int sz = z + Dirs.dz(across);
		if (d.isEmpty(sx, y, sz) && d.isSolid(sx + Dirs.dx(wall), y, sz + Dirs.dz(wall)) && r.inside(sx + Dirs.dx(into), sz + Dirs.dz(into))
			&& d.isEmpty(sx + Dirs.dx(into), y, sz + Dirs.dz(into)) && take(r, sx, sz)) {
			strongbox(d, sx, y, sz, into);
			r.keep(sx + Dirs.dx(into), sz + Dirs.dz(into));
		} else {
			int[] sb = spot(d, r, wall);
			if (sb != null) {
				Direction sw = Dirs.HORIZONTAL[sb[2]];
				strongbox(d, sb[0], y, sb[1], sw.getOpposite());
			}
		}
		int bx = x - Dirs.dx(across);
		int bz = z - Dirs.dz(across);
		if (d.isEmpty(bx, y, bz) && take(r, bx, bz)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(bx, y, bz, Blocks.BOOKSHELF.defaultBlockState());
				d.set(bx, y + 1, bz, Blocks.BOOKSHELF.defaultBlockState());
			}
		}
	}

	/** A strongbox: a chest of coin and plate, registered as STRONGBOX (and STORAGE). */
	public static void strongbox(Draft d, int x, int y, int z, Direction facing) {
		Furniture.chest(d, x, y, z, facing, List.of(new Object[]{"minecraft:gold_nugget", 24}, new Object[]{"minecraft:emerald", 3},
			new Object[]{"minecraft:paper", 6}));
		d.poi(PoiType.STRONGBOX, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}

	/** Furnishes a room as {@link Rooms#furnish} does (bedroom, kitchen, stores...). */
	public static void furnish(Draft d, Rooms.Room r, Rooms.Type type, int beds) {
		Rooms.furnish(d, r, type, beds, JobType.NONE);
	}

	/** A stone bench (a stair of the dressing) at (x, y, z) with its back toward {@code back}; registers a seat. */
	public static void bench(Draft d, Dress m, int x, int y, int z, Direction back) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, m.dress.stairsOrSlab(back, Half.BOTTOM));
		}
		d.poi(PoiType.SEAT, x, y, z, back.getOpposite());
	}

	/** A room over a rectangle at a standing level, three rows high, with cells kept free. */
	public static Rooms.Room room(int x0, int z0, int x1, int z1, int y, int[]... keep) {
		Rooms.Room r = new Rooms.Room(x0, z0, x1, z1, y, 3);
		for (int[] k : keep) {
			r.keep(k[0], k[1]);
		}
		return r;
	}
}
