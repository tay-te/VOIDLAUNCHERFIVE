package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Bohemian;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.lawhouse.CourtTower;
import dev.voidmc.hearth.build.kit.lawhouse.Courtroom;
import dev.voidmc.hearth.build.kit.lawhouse.Fenestration;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** The hall courts of {@link Courthouse}: the towered two-storey court and the single hall. */
final class CourtHall {
	private CourtHall() {
	}

	/**
	 * The court on its perron (no tower: that is the town hall's): a long two-storey hall, the court room on the upper
	 * floor under a tall roof, reached from the square by a double flight of stone steps along the front that meet on a
	 * landing before its portal, roofed on two posts (where sentences are read out); under the landing the low door into
	 * the undercroft, where the clerk keeps the records and the lock-up is barred off; the court's bell in a turret on the
	 * ridge and the town's stone pillory by the steps.
	 */
	static void towered(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		boolean boh = t == Tradition.TIMBER;
		boolean med = t == Tradition.MEDITERRANEAN;

		// --- the plan: the forecourt with the perron, the hall
		int x0 = 1;
		int x1 = w - 2;
		int cx = (x0 + x1) / 2;
		int z0 = 3;
		int z1 = dp - 2;
		int base = Grounds.raisedBase(d, x0, x1, z0);
		// a low undercroft, the court room tall over it (the flights must reach its floor within the front)
		int h0 = 4;
		int h1 = maxH >= 32 ? 6 : 5;
		if (cx - 2 - (base + h0) < 0) {
			base = 0;
		}
		Body body = new Body(x0, z0, x1, z1, base, new int[]{h0, h1}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		int fy = body.floorY(1);
		int run = fy;
		int doorX = cx;

		// --- ground and shell: render (or the culture's walling) quoined in the dressing over a plinth course,
		// a rusticated undercroft
		Storeys.ground(d, body, false);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		Stone dress = Stone.dressing(d);
		Stone wall = Masonry.walling(d);
		Stone plinth = Masonry.plinth(d);
		Masonry.storey(d, body, 0, med ? plinth : wall, dress, plinth, true);
		Masonry.storey(d, body, 1, wall, dress, plinth, false);
		// windows of two lights in bays four apart: small and barred in the undercroft (none behind the steps), tall over
		// the court (none over the portal)
		for (Side s : body.sides(0)) {
			for (int i0 : Masonry.bays(s.length() - 1, 4)) {
				if (i0 + 1 > s.last() - 2 || i0 < 1) {
					continue;
				}
				boolean steps = s.out() == Dirs.FRONT;
				if (!steps) {
					Fenestration.framed(d, s, i0, 2, base + 1, 2, dress, false, true, false);
				}
				if (steps && (Math.abs(s.x(i0) - cx) <= 2 || Math.abs(s.x(i0 + 1) - cx) <= 2)) {
					continue;
				}
				Side up = body.side(1, s.out());
				Fenestration.framed(d, up, i0, 2, fy + 1, h1 >= 6 ? 4 : 3, dress, false, false, false);
			}
		}

		// --- the roof: hipped or half-hipped tile over the Bohemian court, the culture's form elsewhere, dormers
		RoofForm form = switch (t) {
			case TIMBER -> d.chance(0.6) ? RoofForm.HIP : RoofForm.HALF_HIP;
			case MEDITERRANEAN, STEPPE -> RoofForm.HIP;
			default -> h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
		};
		double pitch = med ? 0.5 : Math.max(1.0, h.roof().pitch());
		Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.X, pitch, 6, true);
		if (boh || med) {
			Halls.cornice(d, body.topRect(), plate - 1);
		}
		int skyline = top.ridge();
		// the court's bell in a turret on the ridge
		if (top.roof() != null && top.ridge() + 9 < maxH) {
			skyline = Math.max(skyline, Bohemian.turret(d, cx, (z0 + z1) / 2 - 1, plate, top.ridge(), maxH));
		}

		// --- the perron: two flights up along the front wall meeting on a landing before the court's portal
		perron(d, cx, z0, base, fy, run, dress, plinth);
		Side front = body.side(1, Dirs.FRONT);
		if (boh) {
			Bohemian.portal(d, front, front.index(cx), fy);
		} else {
			dev.voidmc.hearth.build.kit.landmark.Grounds.door(d, front, front.index(cx), fy, dress);
		}
		// the undercroft's door under the landing
		Side low = body.side(0, Dirs.FRONT);
		Openings.door(d, low, cx, base, 1, new Openings.DoorOpts(false, false, false));
		Grounds.approach(d, cx, base, z0 - 1);

		// --- the undercroft: the clerk's hall, the lock-up, the stair up to the court
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		boolean stairLeft = d.chance(0.5);
		int sx = stairLeft ? ix0 : ix1;
		Storeys.flight(d, body, 0, sx, iz0 + h0, Dirs.FRONT);
		int cellX = stairLeft ? ix1 - 1 : ix0;
		boolean cell = lockUp(d, cellX, iz0, base, stairLeft ? Dirs.LEFT : Dirs.RIGHT);
		int[] flue = offices(d, top, ix0, iz0, ix1, iz1, base, h0, cx, doorX, iz0, cx + 1, cx - 1, z0, sx, stairLeft, wall, dress,
			cell ? new int[]{stairLeft ? cellX - 1 : cellX, iz0, stairLeft ? cellX + 1 : cellX + 2, iz0 + 3, stairLeft ? cellX - 2 : cellX + 3, iz0 + 1}
				: null);
		// the court room upstairs, entered from the landing and by the stair from below
		Set<Long> keep = new HashSet<>();
		Set<Long> blocked = new HashSet<>();
		keep.add(Courtroom.key(sx, iz0));
		keep.add(Courtroom.key(doorX, iz0));
		keep.add(Courtroom.key(doorX, iz0 + 1));
		for (int z = iz0 + 1; z <= iz0 + 3; z++) {
			blocked.add(Courtroom.key(sx, z));
			blocked.add(Courtroom.key(sx + (stairLeft ? 1 : -1), z));
		}
		// the court's tiled stove in the front corner away from the stair, its flue up through the roof
		int stx = stairLeft ? ix1 : ix0;
		if (top.roof() != null && !blocked.contains(Courtroom.key(stx, iz0))) {
			Furniture.hearth(d, stx, fy, iz0, Dirs.BACK, false);
			skyline = Math.max(skyline, dev.voidmc.hearth.build.kit.Chimneys.stack(d, stx, iz0, fy + 2, top.roof(), top.roof().ridgeY - 2));
			blocked.add(Courtroom.key(stx, iz0));
			keep.add(Courtroom.key(stx, iz0 + 1));
		}
		if (flue != null) {
			blocked.add(Courtroom.key(flue[0], flue[1]));
		}
		Courtroom.furnish(d, ix0, iz0, ix1, iz1, fy, h1 - 1, keep, blocked);

		// --- outside: the town's stone pillory by the steps, the notice board, lamps at the forecourt's corners
		int px = cx - run - 3;
		if (px >= 1 && Groundwork.surface(d, px, 1) == 0 && Groundwork.surface(d, px, 2) == 0 && d.isEmpty(px, 0, 1) && d.isEmpty(px, 0, 2)
			&& d.isEmpty(px, 1, 2)) {
			dev.voidmc.hearth.build.kit.lawhouse.Justice.column(d, px, 0, 1, Dirs.FRONT);
		}
		noticeBoard(d, cx, 1, cx - run - 3, cx + run + 1);
		Halls.lamp(d, 0, 0, 0, 2);
		Halls.lamp(d, w - 1, 0, 0, 2);
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, doorX);
		}
		Grounds.edgeRails(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 2));
	}

	/**
	 * The perron before the front wall (row {@code z0}): from the forecourt (standing 0) two flights of {@code run} steps
	 * in the plinth's stone climb along the wall toward column cx, each walled on its open side by a parapet stepping up
	 * with it (the plinth's wall on top), and meet on a landing three wide at the court's floor {@code fy}; the landing's
	 * front is a balustrade between two posts carried down to the forecourt, which bear a little roof over it; under the
	 * landing a passage stays open to the undercroft's door.
	 */
	static void perron(Draft d, int cx, int z0, int base, int fy, int run, Stone dress, Stone plinth) {
		int zs = z0 - 1;
		int zp = z0 - 2;
		Paint rail = Halls.rail(d, plinth);
		Paint solid = plinth.full();
		Paint deck = dress.has(Form.SLAB) ? dress.slab(SlabType.TOP) : solid;
		Mat roof = d.mat(Role.ROOF);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int side : new int[]{-1, 1}) {
				Direction up = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
				for (int k = 1; k <= run; k++) {
					int x = cx + side * (1 + k);
					int y = fy - k;
					if (x < 0 || x >= d.frontage()) {
						continue;
					}
					for (int yy = 0; yy < y; yy++) {
						d.set(x, yy, zs, solid);
					}
					d.set(x, y, zs, plinth.stairsOrSlab(up, Half.BOTTOM));
					d.clearBox(x, y + 1, zs, x, y + 3, zs);
					// the parapet on the open side, its rail where the drop beside the step would be a fall
					for (int yy = 0; yy <= y; yy++) {
						d.set(x, yy, zp, solid);
					}
					if (y >= 1) {
						d.set(x, y + 1, zp, rail);
					}
				}
			}
			// the landing: a floor three wide before the portal over the passage, its balustrade on the row before it
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, fy - 1, zs, deck);
				d.set(x, fy - 1, zp, deck);
				d.set(x, fy, zp, rail);
				d.clearBox(x, fy, zs, x, fy + 2, zs);
				d.clearBox(x, base, zs, x, fy - 2, zs);
				if (x == cx) {
					d.clearBox(x, base, zp, x, fy - 2, zp);
				}
			}
			// two posts from the forecourt through the landing's corners to its roof, the passage between them
			for (int x : new int[]{cx - 1, cx + 1}) {
				for (int y = 0; y <= fy + 2; y++) {
					if (y != fy - 1) {
						d.set(x, y, zp, dress.wallOrFull());
					}
				}
			}
		}
		// the landing's little roof
		try (var st = d.stage(StageKind.ROOF)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, fy + 3, zp, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.set(x, fy + 3, zs, roof.slab(SlabType.BOTTOM));
			}
		}
		Halls.lanternUnder(d, cx, fy - 1, zs, 0);
		d.poi(PoiType.VIEWPOINT, cx, fy, zs, Dirs.FRONT);
	}

	/**
	 * The lock-up: a cell two wide and three deep in the undercroft's front corner at (x, z), barred off on its inner
	 * side and its back with an iron door toward {@code open} (prisoners pick its lock, nobody opens it by hand), a bale of
	 * straw and a bucket inside. Returns whether it was built.
	 */
	static boolean lockUp(Draft d, int x, int z, int y, Direction open) {
		int bx = open == Dirs.LEFT ? x - 1 : x + 2;
		for (int zz = z; zz <= z + 3; zz++) {
			for (int xx = Math.min(x, bx); xx <= Math.max(x + 1, bx); xx++) {
				for (int yy = y; yy <= y + 1; yy++) {
					if (!d.isEmpty(xx, yy, zz)) {
						return false;
					}
				}
			}
		}
		Paint bars = Paint.of(Blocks.IRON_BARS.defaultBlockState());
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int zz = z; zz <= z + 3; zz++) {
				for (int yy = y; yy <= y + 2; yy++) {
					d.set(bx, yy, zz, bars);
				}
			}
			for (int xx = x; xx <= x + 1; xx++) {
				for (int yy = y; yy <= y + 2; yy++) {
					d.set(xx, yy, z + 3, bars);
				}
			}
			d.set(open == Dirs.LEFT ? x + 1 : x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
			d.set(open == Dirs.LEFT ? x + 1 : x, y, z + 2, Blocks.CAULDRON.defaultBlockState());
		}
		d.inStage(StageKind.OPENINGS, () -> d.door(bx, y, z + 1, Blocks.IRON_DOOR, open.getOpposite(), DoorHingeSide.LEFT));
		d.poi(PoiType.CELL, open == Dirs.LEFT ? x : x + 1, y, z + 1, open.getOpposite());
		return true;
	}

	/**
	 * The ground floor of a towered court: the hall where folk wait on the court, entered through the arch behind the
	 * vestibule, the stair up along one side; the clerk's desk across the back of the hall (the town's law on a lectern,
	 * the clerk's chair, the chests of records against the wall behind); benches along the other side; and, in a deep
	 * hall, the judge's chamber behind a partition (a desk, shelves of the law, a chest, a hearth whose flue rises through
	 * the court room above). Returns the flue's cell {x, z} on the court's floor, or null.
	 */
	static int[] offices(Draft d, Storeys.Top top, int ix0, int iz0, int ix1, int iz1, int base, int h0, int cx, int doorX, int entryZ,
		int tx0, int tx1, int tz1, int sx, boolean stairLeft, Stone wall, Stone dress, int[] cell) {
		boolean chamber = iz1 - iz0 + 1 >= 11;
		int zp = chamber ? iz1 - 3 : iz1 + 1;
		int hallZ1 = zp - 1;
		int[] flue = null;
		// --- the judge's chamber behind its partition
		if (chamber) {
			int dx = stairLeft ? Math.min(ix1 - 1, cx + 2) : Math.max(ix0 + 1, cx - 2);
			try (var st = d.stage(StageKind.WALLS)) {
				for (int x = ix0; x <= ix1; x++) {
					for (int y = base; y <= base + h0 - 2; y++) {
						if (!(x == dx && y <= base + 1)) {
							d.set(x, y, zp, wall.full());
						}
					}
				}
				d.set(dx, base + 2, zp, dress.full());
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(dx, base, zp, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR,
				net.minecraft.world.level.block.Blocks.SPRUCE_DOOR), Dirs.FRONT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			Rooms.Room ch = new Rooms.Room(ix0, zp + 1, ix1, iz1, base, h0 - 1);
			ch.keep(dx, zp + 1);
			// shelves of the law along the back wall, the desk before them, a chair facing the door
			for (int x = ix0 + 1; x <= ix1 - 1; x++) {
				if (Math.abs(x - cx) <= 1 || !ch.claim(x, iz1)) {
					continue;
				}
				if (((x - ix0) & 1) == 0) {
					Furniture.bookshelf(d, x, base, iz1);
				} else {
					Furniture.shelf(d, x, base, iz1, Dirs.FRONT, "study");
				}
			}
			if (ch.claim(cx, iz1 - 1) && ch.claim(cx - 1, iz1 - 1)) {
				Furniture.longTable(d, cx - 1, base, iz1 - 1, Dirs.RIGHT, 2);
				Lighting.candles(d, cx, base + 1, iz1 - 1, 2);
			}
			if (ch.claim(cx, iz1)) {
				Furniture.chair(d, cx, base, iz1, Dirs.BACK);
			}
			// the hearth on the side away from the door, its flue rising inside through the court room
			int hx = stairLeft ? ix0 : ix1;
			int hz = zp + 2;
			if (top.roof() != null && ch.claim(hx, hz)) {
				Furniture.hearth(d, hx, base, hz, stairLeft ? Dirs.RIGHT : Dirs.LEFT, false);
				dev.voidmc.hearth.build.kit.Chimneys.stack(d, hx, hz, base + 2, top.roof(), top.roof().ridgeY);
				flue = new int[]{hx, hz};
			}
			int chx = stairLeft ? ix1 : ix0;
			if (ch.claim(chx, iz1)) {
				Furniture.chest(d, chx, base, iz1, stairLeft ? Dirs.LEFT : Dirs.RIGHT, List.of(new Object[]{"minecraft:writable_book", 4},
					new Object[]{"minecraft:paper", 16}, new Object[]{"minecraft:gold_nugget", 9}));
			}
			Furniture.rug(d, cx - 1, zp + 1, cx + 1, zp + 1, base);
			Lighting.room(d, ch);
		}
		// --- the hall
		Rooms.Room lobby = new Rooms.Room(ix0, iz0, ix1, hallZ1, base, h0 - 1);
		lobby.keep(doorX, entryZ);
		lobby.keepArea(doorX - 1, entryZ, doorX + 1, entryZ);
		for (int x = tx0; x <= tx1; x++) {
			for (int z = iz0; z <= tz1; z++) {
				lobby.block(x, z);
			}
		}
		lobby.keepArea(sx, iz0, sx, iz0 + h0);
		lobby.keep(sx, iz0 + h0 + 1);
		// the lock-up and its bars are taken, the floor before its door kept
		if (cell != null) {
			for (int z = cell[1]; z <= cell[3]; z++) {
				for (int x = cell[0]; x <= cell[2]; x++) {
					lobby.block(x, z);
				}
			}
			lobby.keep(cell[4], cell[5]);
		}
		if (chamber) {
			int dx = stairLeft ? Math.min(ix1 - 1, cx + 2) : Math.max(ix0 + 1, cx - 2);
			lobby.keep(dx, hallZ1);
		}
		// the clerk's desk: a counter of three across the middle, the lectern with the town's law at its end, the clerk's
		// chair behind, the chests of records against the wall behind him
		int zc = hallZ1 - 1;
		int lx = cx + (stairLeft ? 2 : -2);
		boolean desk = zc > entryZ + 1;
		if (desk) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (lobby.claim(x, zc)) {
					int fx = x;
					d.inStage(StageKind.INTERIOR, () -> d.set(fx, base, zc, d.mat(Role.FURNITURE).slab(net.minecraft.world.level.block.state.properties.SlabType.TOP)));
				}
			}
			if (lobby.claim(lx, zc)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, zc, States.facing(Blocks.LECTERN, Dirs.FRONT),
					BlockEntities.lectern("The Town's Law", "The Clerk", List.of("The customs and the law of the town, set down."))));
				d.poi(PoiType.LECTERN, lx, base, zc - 1, Dirs.BACK);
				lobby.keep(lx, zc - 1);
			}
			if (lobby.claim(cx, hallZ1)) {
				Furniture.chair(d, cx, base, hallZ1, Dirs.BACK);
			}
			Lighting.candles(d, cx - 1, base + 1, zc, 2);
			d.poi(PoiType.TABLE, cx, base, zc, Dirs.FRONT);
			for (int x : new int[]{cx - 2, cx + 2}) {
				if (x != lx && lobby.inside(x, hallZ1) && lobby.claim(x, hallZ1)) {
					Furniture.chest(d, x, base, hallZ1, Dirs.FRONT, List.of(new Object[]{"minecraft:paper", 24}, new Object[]{"minecraft:writable_book", 2}));
				}
			}
		} else {
			if (lobby.claim(lx, hallZ1)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, hallZ1, States.facing(Blocks.LECTERN, Dirs.FRONT),
					BlockEntities.lectern("The Town's Law", "The Clerk", List.of("The customs and the law of the town, set down."))));
				d.poi(PoiType.LECTERN, lx, base, hallZ1 - 1, Dirs.BACK);
				lobby.keep(lx, hallZ1 - 1);
			}
			if (lobby.claim(cx, hallZ1)) {
				Furniture.chest(d, cx, base, hallZ1, Dirs.FRONT, List.of(new Object[]{"minecraft:paper", 24}, new Object[]{"minecraft:writable_book", 2}));
			}
		}
		// benches along the wall away from the stair for those waiting on the court
		int bx = stairLeft ? ix1 : ix0;
		for (int z = zc - 2; z > entryZ; z--) {
			if (d.isEmpty(bx, base, z) && lobby.claim(bx, z)) {
				Furniture.chair(d, bx, base, z, stairLeft ? Dirs.RIGHT : Dirs.LEFT);
			}
		}
		// the runner from the arch to the desk
		var cloth = d.mat(Role.CLOTH);
		if (cloth.has(dev.voidmc.hearth.build.style.Form.CARPET)) {
			try (var st = d.stage(StageKind.INTERIOR)) {
				for (int z = entryZ; z < zc; z++) {
					if (d.isEmpty(doorX, base, z) && d.isSolid(doorX, base - 1, z)) {
						d.set(doorX, base, z, cloth.carpet());
					}
				}
			}
		}
		Rooms.furnish(d, lobby, Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, lobby);
		return flue;
	}

	/**
	 * The notice board where the court's sentences and the wanted are posted: on the forecourt by the way in, a few blocks
	 * to either side of column {@code cx} in row {@code z} (clear of {@code avoid0..avoid1}), facing the street; failing
	 * that anywhere along the front rows where it stands on the ground and leaves the way free.
	 */
	static boolean noticeBoard(Draft d, int cx, int z, int avoid0, int avoid1) {
		int w = d.frontage();
		for (int zz = Math.max(1, z); zz >= 1; zz--) {
			for (int off : new int[]{-3, 3, -4, 4, -5, 5, -2, 2, -6, 6}) {
				int nx = cx + off;
				if (nx >= avoid0 && nx <= avoid1 || nx <= 0 || nx >= w - 1) {
					continue;
				}
				if (boardFits(d, nx, zz)) {
					Signs.noticeBoard(d, nx, Groundwork.surface(d, nx, zz), zz, Dirs.FRONT);
					return true;
				}
			}
		}
		for (int zz = 1; zz <= Math.max(1, z); zz++) {
			for (int nx = 1; nx < w - 1; nx++) {
				if ((nx < avoid0 || nx > avoid1) && boardFits(d, nx, zz)) {
					Signs.noticeBoard(d, nx, Groundwork.surface(d, nx, zz), zz, Dirs.FRONT);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean boardFits(Draft d, int x, int z) {
		int g = Groundwork.surface(d, x, z);
		int gf = Groundwork.surface(d, x, z - 1);
		return Math.abs(g) <= 1 && gf == g && !d.site().isWater(x, z) && d.isEmpty(x, g, z) && d.isEmpty(x, g + 1, z) && d.isEmpty(x, g, z - 1)
			&& d.isEmpty(x, g + 1, z - 1) && (d.isSolid(x, g - 1, z) || d.isAbsent(x, g - 1, z) && d.site().ground(x, z) == g);
	}

	/** Whether cells {@code i0 .. i0 + width - 1} of wall s lie against (or within a block of) the tower. */
	static boolean nearTower(Side s, int i0, int width, int tx0, int tz0, int tx1, int tz1) {
		for (int i = i0 - 1; i <= i0 + width; i++) {
			int x = s.x(i);
			int z = s.z(i);
			if (x >= tx0 - 1 && x <= tx1 + 1 && z >= tz0 - 1 && z <= tz1 + 1) {
				return true;
			}
		}
		return false;
	}
}
