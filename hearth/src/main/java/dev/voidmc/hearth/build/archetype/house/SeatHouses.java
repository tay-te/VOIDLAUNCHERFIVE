package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.homestead.Beds;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.station.Coffers;
import dev.voidmc.hearth.build.kit.station.Shrines;
import dev.voidmc.hearth.build.kit.station.Study;
import dev.voidmc.hearth.build.kit.station.Verdure;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * What the houses of the village's office-holders share (the reeve's by the square, the priest's by the chapel,
 * docs/LIVING_WORLD.md §8.1): a detached house of two storeys and a garret standing back from the street behind a
 * forecourt or a front garden, its door in the middle of a regular front; inside, the room of the office at the front
 * where folk are received (the reeve's desk and rolls, the priest's study and lectern) behind a partition from the
 * kitchen and the stair; the chambers above. Each archetype adds its own outside: the reeve's notice board and bench on
 * a paved forecourt, the priest's garden of herbs and bees with a shrine at the gate.
 */
final class SeatHouses {
	private SeatHouses() {
	}

	/** Which office the house serves. */
	enum Office {
		REEVE, RECTORY
	}

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.MANSARD, RoofForm.PAGODA,
		RoofForm.FLAT, RoofForm.CROSS_GABLE, RoofForm.SALTBOX, RoofForm.DOME};

	static void draw(Draft d, Office office, int beds) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		boolean bohemian = Bohemia.is(d);
		RoofForm form = HouseBuilder.roofParam(d, bohemian ? Bohemia.form(d) : h.roof().pick(d.random(), FORMS));
		form = switch (form) {
			case DOME -> RoofForm.FLAT;
			case SALTBOX, CROSS_GABLE -> RoofForm.GABLE;
			default -> form;
		};
		boolean flat = form == RoofForm.FLAT;

		// the body back from the street: a forecourt (the reeve's) or a front garden; the rectory's garden behind
		int side = w >= 9 ? 1 : 0;
		// (ten deep where the plot allows: the office in front of the partition, the kitchen and the stair's well behind it)
		int front = office == Office.REEVE ? (dp >= 15 ? 3 : 2) : 2;
		int bodyDepth = Math.min(10, dp - front - (office == Office.RECTORY ? 2 : 1));
		bodyDepth = Math.max(7, bodyDepth);
		int bx0 = side;
		int bx1 = w - 1 - side;
		int bz0 = front;
		int bz1 = Math.min(dp - 1, bz0 + bodyDepth - 1);
		int bw = bx1 - bx0 + 1;
		int bd = bz1 - bz0 + 1;
		int door = bx0 + (bw - 1) / 2;
		// eaves to the square (the reeve's broad front) or a gable to the lane (the Bohemian village's way, a priest's)
		Direction.Axis ridge = office == Office.REEVE ? (bohemian && d.chance(0.5) ? Direction.Axis.Z : Direction.Axis.X)
			: bohemian || d.chance(h.roof().frontGable()) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.MANSARD || form == RoofForm.GAMBREL) {
			ridge = Direction.Axis.X;
		}
		if (t.eastern()) {
			form = RoofForm.HIP;
			ridge = Direction.Axis.X;
		}
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (bohemian && !flat) {
			opts = opts.withPitch(Math.max(opts.pitch(), 1.0));
		}
		int sh = Math.max(4, h.walls().storeyHeight());
		Body body = null;
		for (int pass = 0; pass < 6; pass++) {
			body = new Body(bx0, bz0, bx1, bz1, 0, TownHouses.heights(2, sh, sh, !flat), false, false);
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (opts.pitch() > 1.0) {
				opts = opts.withPitch(1.0);
			} else if (sh > 4) {
				sh--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else {
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(bx0, bz0, bx1, bz1);
		int windowW = bw >= 9 && (t.masonryUpper() || t.timber()) ? 2 : 1;
		Facade facade = TownHouses.planWithDoor(d, body, door, 1, windowW, 0.75);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		int plate = body.plateY();
		d.eave(plate);

		// ground and shell
		Groundwork.footings(d, bx0, bz0, bx1, bz1, -1);
		if (side > 0 && !d.site().anyWater(0, bz0, w - 1, bz1)) {
			Groundwork.level(d, 0, bz0, bx0 - 1, bz1, 0);
			Groundwork.level(d, bx1 + 1, bz0, w - 1, bz1, 0);
		}
		boolean rusticate = t.walls() == WallKind.PLASTER && h.walls().kind(1) == WallKind.PLASTER && office == Office.REEVE && d.chance(0.5);
		TownHouses.shell(d, body, facade, rusticate);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		TownHouses.openings(d, body, facade, t, style, new HashSet<>(), true);
		Side front0 = body.side(0, Dirs.FRONT);
		Fronts.doorCase(d, front0, door, 1, 0, t);
		TownHouses.dress(d, body, t, flat, false, Dirs.LEFT);
		if (bohemian) {
			int[] r0 = body.rect(0);
			Bohemia.fieldstone(d, r0[0], r0[1], r0[2], r0[3], 0, Math.max(0, h.walls().plinth() - 1));
		}
		if (office == Office.REEVE && body.height(1) >= 3 && d.chance(Math.max(0.4, h.balcony()))) {
			// the reeve speaks to the square from the balcony over his door
			Side s1 = body.side(1, Dirs.FRONT);
			int ci = s1.index(door);
			if (ci >= 2 && ci <= s1.last() - 2) {
				Porches.balcony(d, s1, s1.column(ci - 1), s1.column(ci + 1), body.floorY(1));
				TownHouses.galleryDoor(d, s1, body.floorY(1), door);
			}
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			// a lived deck behind a breast-high parapet, no merlons: the reeve is the lord's man, not his castle
			deck = TownHouses.flat(d, body, false);
		} else {
			boolean dormers = d.chance(Math.max(0.4, h.roof().dormers()));
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, dormers && !bohemian, false);
			if (bohemian && form != RoofForm.HIP && form != RoofForm.PAGODA) {
				Bohemia.carryRidge(d, roof);
				Bohemia.gables(d, roof, ridge, tr, plate);
			}
		}

		// the stair up the back on the side away from the hearth; the kitchen's hearth on the other side wall
		StairCore core = TownHouses.core(d, body, door, topK, roof);
		int[] r0 = body.rect(0);
		int ix0 = r0[0] + 1;
		int ix1 = r0[2] - 1;
		int iz0 = r0[1] + 1;
		int iz1 = r0[3] - 1;
		int[] zr = core.zRange();
		boolean coreLeft = core.x0() + core.x1() < ix0 + ix1;
		// the partition between the office (in front) and the kitchen (behind), clear of the stair's foot
		int zp = Math.min(iz0 + 3, zr[0] - 1);
		boolean split = zp >= iz0 + 2 && iz1 - zp >= 3;
		int[] hearth = null;
		int hx = coreLeft ? ix1 : ix0;
		int hz = split ? zp + 1 + (iz1 - zp - 1) / 2 : (iz0 + iz1) / 2;
		if (d.chance(Math.max(0.85, h.chimney())) && !(hz >= zr[0] - 1 && hx >= core.x0() - 1 && hx <= core.x1() + 1)) {
			int topY = roof != null ? roof.ridgeY + 1 : deck + 2;
			if (topY < d.maxHeight()) {
				Furniture.hearth(d, hx, 0, hz, coreLeft ? Dirs.LEFT : Dirs.RIGHT, false);
				if (roof != null) {
					Chimneys.stack(d, hx, hz, 2, roof, Math.min(roof.ridgeY + 1, d.maxHeight() - 1));
				} else {
					HouseBuilder.chimneyFlat(d, hx, hz, deck);
				}
				hearth = new int[]{hx, hz};
			}
		}
		if (split) {
			Partitions.alongX(d, zp, ix0, ix1, 0, body.floorY(1) - 1, door, Dirs.BACK);
		}
		interior(d, office, body, roof, core, door, hearth, split ? zp : Integer.MIN_VALUE, beds);

		// outside
		if (office == Office.REEVE || d.site().anyWater(0, 0, w - 1, bz0 - 1)) {
			TownHouses.street(d, body, door, TownHouses.misfit(d, door) > 0, 0);
		} else {
			// a garden before the priest's house, not a pavement: the ground made level, a path to the door
			Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0);
			Garden.path(d, Garden.line(door, 0, door, bz0 - 1), 0);
			if (TownHouses.misfit(d, door) > 0) {
				Groundwork.approach(d, door, 0, bz0 - 1);
			}
		}
		if (office == Office.REEVE) {
			forecourt(d, body, door);
		} else {
			garden(d, body, door);
		}
		if (!t.raised()) {
			Verdure.foot(d, bx0, bz0, bx1, bz1, office == Office.RECTORY ? 0.4 : 0.2);
		}
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	// ------------------------------------------------------------------------------------------------- inside

	private static void interior(Draft d, Office office, Body b, Roofs.@Nullable Roof roof, StairCore core, int door, int @Nullable [] hearth, int zp,
		int beds) {
		int floors = b.storeys();
		int topK = floors - 1;
		boolean garret = roof != null;
		Rooms.Room[] rooms = new Rooms.Room[floors];
		for (int k = 0; k < floors; k++) {
			boolean reached = TownHouses.reached(core, k);
			if (!reached && !(garret && k == topK)) {
				continue;
			}
			int[] r = b.rect(k);
			Rooms.Room room = TownHouses.room(d, b, k, k == topK ? roof : null);
			if (room == null) {
				continue;
			}
			if (reached) {
				StationHomes.keepCore(core, room, k);
			}
			TownHouses.keepDoors(d, room, r, b.floorY(k));
			if (hearth != null && k > 0) {
				room.block(hearth[0], hearth[1]);
			}
			rooms[k] = room;
		}
		if (garret && rooms[topK] != null && !TownHouses.reached(core, topK)
			&& !MerchantHouse.loftLadder(d, b, core, rooms[topK], topK >= 1 ? rooms[topK - 1] : null)) {
			rooms[topK] = null;
		}
		Rooms.Room ground = rooms[0];
		Rooms.Room office0 = ground;
		Rooms.Room kitchen = ground;
		if (ground != null && zp != Integer.MIN_VALUE) {
			office0 = new Rooms.Room(ground.x0, ground.z0, ground.x1, zp - 1, ground.y, ground.height);
			kitchen = new Rooms.Room(ground.x0, zp + 1, ground.x1, ground.z1, ground.y, ground.height);
			StationHomes.keepCore(core, kitchen, 0);
			office0.keep(door, zp - 1);
			kitchen.keep(door, zp + 1);
		}
		if (office0 != null) {
			office0.keep(door, office0.z0);
		}
		if (kitchen != null && hearth != null) {
			kitchen.block(hearth[0], hearth[1]);
			int fx = hearth[0] + (hearth[0] == kitchen.x0 ? 1 : -1);
			if (kitchen.inside(fx, hearth[1])) {
				kitchen.keep(fx, hearth[1]);
			}
		}
		// the chambers and the garret first; any bed still short goes in the kitchen (by the fire) before it is furnished
		List<Rooms.Room> sleep = new ArrayList<>();
		int placed = 0;
		for (int k = 1; k < floors; k++) {
			if (rooms[k] == null) {
				continue;
			}
			boolean isGarret = garret && k == topK;
			int want = isGarret ? beds - placed : Math.max(1, beds - (garret ? 1 : 0));
			TownHouses.bedroom(d, rooms[k], Math.max(0, want));
			placed += TownHouses.bedsIn(d, rooms[k]);
			if (isGarret) {
				Rooms.furnish(d, rooms[k], Rooms.Type.STORAGE, 0, JobType.NONE);
			}
			sleep.add(rooms[k]);
		}
		for (Rooms.Room r : sleep) {
			if (placed < beds) {
				placed += Beds.place(d, r, beds - placed);
			}
		}
		if (kitchen != null && placed < beds) {
			placed += Beds.place(d, kitchen, beds - placed);
		}
		if (office0 != null) {
			if (office == Office.REEVE) {
				reeveOffice(d, office0, door);
			} else {
				study(d, office0, door);
			}
		}
		if (kitchen != null) {
			Rooms.furnish(d, kitchen, Rooms.Type.KITCHEN, 0, JobType.NONE);
			Household.dress(d, kitchen, sleep.isEmpty() ? kitchen : sleep.get(0), hearth);
		}
		TownHouses.joists(d, b, core);
	}

	/**
	 * The reeve's office: his desk facing the door with the rolls on shelves behind him (JOB_SITE where he sits), the
	 * strongbox of the dues and the records in a corner, chairs for those who come to him by the front wall.
	 */
	private static void reeveOffice(Draft d, Rooms.Room room, int door) {
		int y = room.y;
		// the desk across from the door, against the back of the room (the partition or the back wall), the reeve facing in
		boolean desk = false;
		for (int dx : new int[]{1, -1, 2, -2}) {
			int x = door + dx;
			if (!desk && room.inside(x, room.z1) && Study.desk(d, room, x, room.z1, Dirs.FRONT, 2)) {
				d.poi(PoiType.JOB_SITE, x, y, room.z1 - 1, Dirs.BACK, JobType.REEVE);
				desk = true;
			}
		}
		if (!desk) {
			for (int x = room.x0; x <= room.x1 && !desk; x++) {
				for (int z = room.z0 + 1; z <= room.z1 && !desk; z++) {
					for (Direction f : new Direction[]{Dirs.RIGHT, Dirs.LEFT, Dirs.FRONT}) {
						if (!desk && (x == room.x0 || x == room.x1 || z == room.z1) && Study.desk(d, room, x, z, f, 2)) {
							d.poi(PoiType.JOB_SITE, x + Dirs.dx(f), y, z + Dirs.dz(f), f.getOpposite(), JobType.REEVE);
							desk = true;
						}
					}
				}
			}
		}
		if (!desk) {
			// a narrow office: the reeve stands at his lectern of rolls by the wall
			Furniture.workstation(d, JobType.REEVE, room.x0, y, room.z1, Dirs.FRONT);
		}
		Coffers.strongbox(d, room, Coffers.RECORDS, new int[]{room.x0, room.z1});
		int shelves = 0;
		for (int x : new int[]{room.x1, room.x0}) {
			for (int z = room.z1; z >= room.z0 + 1 && shelves < 2; z--) {
				if (d.isEmpty(x, y, z) && Math.abs(x - door) >= 1 && Study.shelf(d, room, x, z, 2)) {
					shelves++;
				}
			}
		}
		Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
	}

	/**
	 * The priest's study: the lectern with the book open where the window's light falls, his desk with candles, books up
	 * the walls, the chest of the plate and the tithe.
	 */
	private static void study(Draft d, Rooms.Room room, int door) {
		int y = room.y;
		boolean lectern = false;
		for (int x : new int[]{room.x0, room.x1}) {
			Direction facing = x == room.x0 ? Dirs.RIGHT : Dirs.LEFT;
			for (int z = room.z0 + 1; z <= room.z1 && !lectern; z++) {
				int fx = x + Dirs.dx(facing);
				if (!room.inside(fx, z) || !d.isEmpty(x, y, z) || !d.isEmpty(fx, y, z)) {
					continue;
				}
				room.keep(fx, z);
				if (room.claim(x, z)) {
					Study.lectern(d, x, y, z, facing, "Breviary", List.of("Domine, labia mea aperies, et os meum annuntiabit laudem tuam.",
						"Deus, in adiutorium meum intende.", "Gloria Patri, et Filio, et Spiritui Sancto."));
					lectern = true;
				}
			}
		}
		boolean desk = false;
		for (int dx : new int[]{1, -1, 2, -2}) {
			if (!desk && room.inside(door + dx, room.z1)) {
				desk = Study.desk(d, room, door + dx, room.z1, Dirs.FRONT, 3);
			}
		}
		int shelves = 0;
		for (int x : new int[]{room.x1, room.x0}) {
			for (int z = room.z1; z >= room.z0 + 1 && shelves < 3; z--) {
				if (d.isEmpty(x, y, z) && Study.shelf(d, room, x, z, 2)) {
					shelves++;
				}
			}
		}
		Coffers.strongbox(d, room, Coffers.TITHE, new int[]{room.x1, room.z1});
		Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
	}

	// ------------------------------------------------------------------------------------------------- outside

	/** The reeve's forecourt: paved, the notice board by the way in where the square reads it, a bench under the window. */
	private static void forecourt(Draft d, Body b, int door) {
		int z0 = b.rect(0)[1];
		if (z0 < 2) {
			return;
		}
		Mat paving = d.mat(Role.PAVING);
		boolean dry = !d.site().anyWater(0, 0, d.frontage() - 1, z0 - 1);
		if (dry) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = 0; z < z0; z++) {
					for (int x = 0; x < d.frontage(); x++) {
						if (d.isAbsent(x, 0, z) && Garden.groundLike(d, x, -1, z) && Spots.level(d, x, z) == 0) {
							d.set(x, -1, z, paving.full());
						}
					}
				}
			}
		}
		// the board beside the path, facing the street, a cell off the way in
		int nx = door + (door - 2 >= 0 ? -2 : 2);
		if (nx >= 0 && nx < d.frontage() && Spots.standable(d, nx, 0, 0) && Spots.standable(d, nx, 0, 1) && !Spots.claimed(d, nx, 0, 0)) {
			Signs.noticeBoard(d, nx, 0, 1, Dirs.FRONT);
		} else {
			int ox = door + (door + 2 < d.frontage() ? 2 : -2);
			if (ox >= 0 && ox < d.frontage() && Spots.standable(d, ox, 0, 1)) {
				Signs.noticeBoard(d, ox, 0, 1, Dirs.FRONT);
			} else {
				d.poi(PoiType.NOTICE_BOARD, door, 0, Math.max(0, z0 - 1), Dirs.BACK);
			}
		}
		// a bench against the front wall for those who wait
		int bx = door + (door - 2 >= b.x0 + 1 ? 2 : -2);
		if (bx > b.x0 && bx < b.x1 && Spots.standable(d, bx, 0, z0 - 1) && !Spots.claimed(d, bx, 0, z0 - 1)) {
			Furniture.chair(d, bx, 0, z0 - 1, Dirs.BACK);
			d.poi(PoiType.SEAT, bx, 0, z0 - 1, Dirs.FRONT);
		}
		// pots by the door
		for (int x : new int[]{door - 1, door + 1}) {
			if (Spots.standable(d, x, 0, z0 - 1) && !Spots.claimed(d, x, 0, z0 - 1) && Noise.unit(d.seed(), x, 0, z0) < 0.7) {
				Garden.pot(d, x, 0, z0 - 1);
			}
		}
	}

	/**
	 * The priest's garden: flowers before the house, behind it the beds of herbs and roots, a fruit tree, the bees by the
	 * back fence, a bench in the sun, and a little shrine by the gate.
	 */
	private static void garden(Draft d, Body b, int door) {
		int w = d.frontage();
		int dp = d.depth();
		int z0 = b.rect(0)[1];
		int zb = b.rect(0)[3] + 1;
		// before the house: the path, flowers either side
		if (z0 >= 2) {
			List<int[]> beds = new ArrayList<>();
			for (int x = 0; x < w; x++) {
				if (Math.abs(x - door) >= 2) {
					beds.add(new int[]{x, z0 - 1});
				}
			}
			Garden.flowerBed(d, beds, 0, 0.7);
			int sx = door + (door + 2 < w ? 2 : -2);
			if (sx >= 0 && sx < w && Spots.standable(d, sx, 0, 0) && !Spots.claimed(d, sx, 0, 0)) {
				Shrines.wayside(d, sx, 0, 0, Dirs.FRONT);
			}
		}
		// behind: the herb garden
		if (zb <= dp - 1 && !d.site().anyWater(0, zb, w - 1, dp - 1)) {
			Groundwork.level(d, 0, zb, w - 1, dp - 1, 0);
			int gz0 = zb + (dp - zb >= 4 ? 1 : 0);
			int gz1 = dp - 1;
			if (gz1 - gz0 >= 1 && w >= 5) {
				Garden.kitchenRows(d, 1, gz0, Math.min(w - 2, w / 2), gz1, 0, Dirs.FRONT);
			}
			int tx = w - 2;
			int tz = Math.min(dp - 1, gz0 + 1);
			if (Spots.standable(d, tx, 0, tz) && !Spots.claimed(d, tx, 0, tz) && tz < dp - 1) {
				Garden.tree(d, tx, 0, tz, 4);
			}
			int hx = w - 1;
			int hz = dp - 1;
			if (Spots.standable(d, hx, 0, hz) && !Spots.claimed(d, hx, 0, hz)) {
				if (!Bohemia.hive(d, hx, 0, hz, Dirs.LEFT)) {
					Garden.beehive(d, hx, 0, hz, Dirs.LEFT);
				}
			}
			int bx = b.x0 + 1;
			if (Spots.standable(d, bx, 0, zb) && !Spots.claimed(d, bx, 0, zb)) {
				Furniture.chair(d, bx, 0, zb, Dirs.FRONT);
				d.poi(PoiType.SEAT, bx, 0, zb, Dirs.BACK);
			}
		}
	}
}
