package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.lawhouse.Courtroom;
import dev.voidmc.hearth.build.kit.lawhouse.Masonry;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The magistrate's yamen of {@link Courthouse} (karst): a walled compound on the street, its wall plastered under a
 * coping of tiles; the gate hall in the middle of the front, a pavilion of red columns under a curved roof with lifted
 * corners, the doors in its middle line; the petitioners' drum on its stand beside the gate, stone lanterns either side;
 * a courtyard of stone with a path to the main hall and, on wide plots, the clerks' halls down both sides with their
 * lattice fronts; at the back the main hall on a terrace of limestone two steps up, a veranda of red columns across its
 * front under the double sweep of its roof, lattice doors and windows. Inside, the magistrate's table on its dais with the
 * high seat, the arms of the town on the screen behind, the clerks' stools along the walls, the dock before the bench.
 */
final class Yamen {
	private Yamen() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		int cx = (w - 1) / 2;
		Stone wall = Masonry.walling(d);
		Stone dress = Stone.dressing(d);
		Stone plinth = Masonry.plinth(d);
		Timber tim = Timber.frame(d);
		int fz = 2;
		int hd = Math.clamp(dp / 2, 7, 9);
		int zb = dp - hd;
		boolean wings = w >= 17 && zb - fz >= 8;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Groundwork.footings(d, 0, fz, w - 1, dp - 1, -1);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), cx, (fz + zb) / 2.0, 3, false));
		int skyline = 6;
		int gx0 = cx - 3;
		int gx1 = cx + 3;

		// --- the gate hall: four columns at its corners, walls in its side bays, the doors in its middle line
		gateHall(d, gx0, fz - 1, gx1, fz + 1, cx, tim, wall, plinth);
		skyline = Math.max(skyline, 9);
		// the drum on its stand beside the gate, stone lanterns before the gate
		drum(d, gx0 - 3, 0, 0, tim);
		for (int x : new int[]{gx0 - 1, gx1 + 1}) {
			if (x >= 0 && x < w && d.isEmpty(x, 0, 0)) {
				Details.stoneLantern(d, x, 0, 0, dress);
			}
		}

		// --- the main hall on its terrace
		int base = 2;
		terrace(d, 1, zb, w - 2, dp - 1, base, plinth, dress);
		Body hall = new Body(1, zb + 2, w - 2, dp - 1, base, new int[]{6}, false, false);
		d.body(1, zb, w - 2, dp - 1);
		Masonry.storey(d, hall, 0, wall, dress, plinth, false);
		int plate = hall.plateY();
		d.eave(plate);
		// columns across the veranda's front, a beam over them, the roof sweeping over the veranda
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x = 1; x <= w - 2; x++) {
				boolean col = x == 1 || x == w - 2 || (x - cx) % 3 == 0;
				if (col) {
					for (int y = base; y < plate; y++) {
						d.set(x, y, zb, tim.post());
					}
				}
				d.set(x, plate, zb, tim.beam(Direction.Axis.X));
				d.set(x, plate, zb + 1, tim.beam(Direction.Axis.Z).orElse(tim.planks()));
			}
		}
		// lattice doors and windows across the hall's front
		Side hf = hall.side(0, Dirs.FRONT);
		for (int i = 1; i < hf.last(); i++) {
			int x = hf.x(i);
			if (Math.abs(x - cx) == 0) {
				continue;
			}
			if ((x - cx) % 3 == 0 || Math.abs(x - cx) == 1) {
				continue;
			}
			Openings.window(d, hf, x, base + 1, 1, 3, Openings.WindowStyle.LATTICE);
		}
		Openings.door(d, hf, cx, base, 1, new Openings.DoorOpts(false, false, false));
		for (Side s : new Side[]{hall.side(0, Dirs.LEFT), hall.side(0, Dirs.RIGHT), hall.side(0, Dirs.BACK)}) {
			for (int i : Masonry.bays(s.length(), 4)) {
				Openings.window(d, s, s.column(i), base + 2, 1, 2, Openings.WindowStyle.LATTICE);
			}
		}
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(1.0).withEaves(1, 1);
		Roofs.Built rb = Roofs.build(d, 1, zb, w - 2, dp - 1, plate, RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		skyline = Math.max(skyline, rb.roof().ridgeY + 1);
		Grounds.flight(d, cx - 1, cx + 1, zb - 1, base, base);
		// lanterns hung under the veranda's roof
		for (int x : new int[]{cx - 3, cx + 3}) {
			if (x > 1 && x < w - 2) {
				Halls.lanternUnder(d, x, base, zb + 1, 0);
			}
		}

		// --- the compound: the front wall either side of the gate hall, the side walls, tiled copings
		compoundWall(d, 0, fz, gx0 - 1, fz, wall, plinth);
		compoundWall(d, gx1 + 1, fz, w - 1, fz, wall, plinth);
		// (with the clerks' halls down the sides, the wall closes the gap between their ends and the terrace)
		compoundWall(d, 0, wings ? zb - 2 : fz + 1, 0, zb - 1, wall, plinth);
		compoundWall(d, w - 1, wings ? zb - 2 : fz + 1, w - 1, zb - 1, wall, plinth);

		// --- the courtyard: the path, stone lanterns along it, a pine in a corner
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int z = fz + 2; z < zb - 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.set(x, -1, z, dress.full());
				}
			}
		}
		for (int z = fz + 3; z < zb - 2; z += 3) {
			for (int x : new int[]{cx - 3, cx + 3}) {
				if (d.isEmpty(x, 0, z)) {
					Details.stoneLantern(d, x, 0, z, dress);
				}
			}
		}
		// --- the clerks' halls down both sides on wide plots
		if (wings) {
			for (boolean left : new boolean[]{true, false}) {
				int x0 = left ? 0 : w - 4;
				int x1 = left ? 3 : w - 1;
				// (stopped two short of the terrace: an eave over its edge is a step up onto the roofs)
				skyline = Math.max(skyline, wing(d, x0, fz + 1, x1, zb - 3, left, wall, dress, plinth, tim));
			}
		}
		// --- the court room in the main hall
		Set<Long> keep = new HashSet<>();
		keep.add(Courtroom.key(cx, zb + 3));
		Courtroom.furnish(d, 2, zb + 3, w - 3, dp - 2, base, plate - base, keep, new HashSet<>(), Courtroom.Seating.SIDES);
		// the notice board on the street beside the gate
		for (int x : new int[]{gx1 + 2, gx1 + 3}) {
			if (x < w - 1 && d.isEmpty(x, 0, 1) && d.isEmpty(x, 1, 1)) {
				Signs.noticeBoard(d, x, 0, 1, Dirs.FRONT);
				break;
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 2));
	}

	/** A terrace of limestone over the rectangle, its top at standing level h: a body of the plinth stone, coped in the dressing. */
	static void terrace(Draft d, int x0, int z0, int x1, int z1, int h, Stone plinth, Stone dress) {
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					for (int y = Math.min(-1, d.site().ground(x, z) - 1); y < h; y++) {
						d.set(x, y, z, y == h - 1 ? (edge ? dress.full() : d.mat(Role.PAVING).full()) : plinth.full());
					}
				}
			}
		}
	}

	/**
	 * A compound wall over the rectangle (one cell thick): three courses of the walling on a plinth course and a coping
	 * of the roof's tiles like a little roof (a stair either side, a ridge of slabs).
	 */
	static void compoundWall(Draft d, int x0, int z0, int x1, int z1, Stone wall, Stone plinth) {
		if (x1 < x0 || z1 < z0) {
			return;
		}
		boolean alongX = z0 == z1;
		var roof = d.mat(Role.ROOF);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					for (int y = 0; y <= 3; y++) {
						d.set(x, y, z, y == 0 ? plinth.full() : wall.full());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.ROOF)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, 4, z, roof.slab(SlabType.BOTTOM));
					if (alongX) {
						for (int dz : new int[]{-1, 1}) {
							int oz = z + dz;
							if (oz >= 0 && oz < d.depth() && d.isEmpty(x, 3, oz)) {
								d.set(x, 3, oz, roof.stairsOrSlab(dz < 0 ? Dirs.BACK : Dirs.FRONT, Half.TOP));
							}
						}
					} else {
						for (int dx : new int[]{-1, 1}) {
							int ox = x + dx;
							if (ox >= 0 && ox < d.frontage() && d.isEmpty(ox, 3, z)) {
								d.set(ox, 3, z, roof.stairsOrSlab(dx < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.TOP));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * The gate hall over x0..x1 × z0..z1 (three deep): columns at its corners, walls in its side bays, the double doors
	 * in its middle line with the main entrance before them, a curved roof with lifted corners and a lantern hung inside.
	 */
	static void gateHall(Draft d, int x0, int z0, int x1, int z1, int cx, Timber tim, Stone wall, Stone plinth) {
		int top = 4;
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, -1, z, plinth.full());
				}
			}
		}
		try (var st = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {cx - 2, z0}, {cx + 2, z0}}) {
				for (int y = 0; y < top; y++) {
					d.set(c[0], y, c[1], tim.post());
				}
			}
			for (int x = x0; x <= x1; x++) {
				d.set(x, top, z0, tim.beam(Direction.Axis.X));
				d.set(x, top, z1, tim.beam(Direction.Axis.X));
			}
			for (int z = z0 + 1; z < z1; z++) {
				d.set(x0, top, z, tim.beam(Direction.Axis.Z));
				d.set(x1, top, z, tim.beam(Direction.Axis.Z));
			}
		}
		// the side bays walled in the middle line, the doors in the middle
		int mz = (z0 + z1) / 2;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				if (x == cx) {
					continue;
				}
				for (int y = 0; y < top; y++) {
					d.set(x, y, mz, y == 0 ? plinth.full() : wall.full());
				}
			}
			d.set(cx, 3, mz, wall.full());
		}
		Side s = new Side(Dirs.FRONT, x0, mz, x1 - x0 + 1);
		Openings.door(d, s, cx, 0, 1, new Openings.DoorOpts(false, true, true));
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(1.0).withEaves(1, 1);
		Roofs.build(d, x0, z0, x1, z1, top, RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		Halls.lanternUnder(d, cx, 0, z0, 0);
		Halls.lanternUnder(d, cx, 0, z1, 0);
	}

	/** The petitioners' drum: a barrel lying on a post between two posts under a little roof, at (x, y, z). */
	static void drum(Draft d, int x, int y, int z, Timber tim) {
		if (x - 1 < 0 || !d.isEmpty(x, y, z) || !d.isEmpty(x - 1, y, z) || !d.isEmpty(x + 1, y, z)) {
			return;
		}
		// (struck from the street: none where the street falls away below the gate's terrace)
		if (z == 0 && d.site().ground(x, -1) < y - 1) {
			return;
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int dx : new int[]{-1, 1}) {
				d.set(x + dx, y, z, tim.post());
				d.set(x + dx, y + 1, z, tim.post());
				d.set(x + dx, y + 2, z, tim.stairs(dx < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
			}
			d.set(x, y, z, tim.fence());
			d.set(x, y + 1, z, States.facing(Blocks.BARREL, Dirs.FRONT));
			d.set(x, y + 2, z, tim.slab(SlabType.BOTTOM));
		}
	}

	/**
	 * A clerks' hall down one side of the courtyard over x0..x1 × z0..z1, its lattice front on the court, a curved roof;
	 * inside, the clerks' tables, lecterns of the registers and chests. Returns its top.
	 */
	static int wing(Draft d, int x0, int z0, int x1, int z1, boolean left, Stone wall, Stone dress, Stone plinth, Timber tim) {
		Body b = new Body(x0, z0, x1, z1, 0, new int[]{4}, false, false);
		Masonry.storey(d, b, 0, wall, dress, plinth, false);
		Side court = b.side(0, left ? Dirs.RIGHT : Dirs.LEFT);
		int mid = court.middle();
		for (int i = 1; i < court.last(); i++) {
			if (i == mid) {
				continue;
			}
			Openings.window(d, court, court.column(i), 1, 1, 2, Openings.WindowStyle.LATTICE);
		}
		Openings.door(d, court, court.column(mid), 0, 1, new Openings.DoorOpts(false, false, false));
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(1.0).withEaves(1, 1);
		Roofs.Built r = Roofs.build(d, x0, z0, x1, z1, b.plateY(), RoofForm.PAGODA, Direction.Axis.Z, o, false, false);
		Rooms.Room room = new Rooms.Room(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 3);
		room.keep(court.x(mid) + (left ? -1 : 1), court.z(mid));
		int lx = left ? x0 + 1 : x1 - 1;
		if (room.claim(lx, z1 - 1)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, 0, z1 - 1, States.facing(Blocks.LECTERN, left ? Dirs.RIGHT : Dirs.LEFT),
				BlockEntities.lectern("The Register", "The Clerks", List.of("Petitions received, by the day."))));
			d.poi(PoiType.LECTERN, lx + (left ? 1 : -1), 0, z1 - 1, left ? Dirs.LEFT : Dirs.RIGHT);
			room.keep(lx + (left ? 1 : -1), z1 - 1);
		}
		Rooms.furnish(d, dev.voidmc.hearth.build.kit.civic.Halls.keepOffWells(d, room), Rooms.Type.STORAGE, 0, JobType.NONE);
		Lighting.room(d, room);
		return r.roof().ridgeY + 1;
	}

	/** Garden helper: a pine in a corner of the court. */
	static void pine(Draft d, int x, int z) {
		Garden.tree(d, x, 0, z, 4);
	}
}
