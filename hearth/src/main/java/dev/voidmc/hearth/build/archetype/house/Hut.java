package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * {@code hearth:house/hut}: a one-room dwelling for camps and hamlets, the first house a settlement raises. In round
 * cultures (steppe, tropical) a round hut of plaster and posts under a cone roof; in flat-roofed ones an adobe cell with
 * a roof terrace; on stilts where the culture builds over water; elsewhere a small gabled hut with a lean-to log store.
 * Inside: a bed, a hearth, a chest, a work bench and a lantern.
 *
 * <p>Params: none.
 */
public final class Hut implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/hut"), ArchetypeCategory.HOUSING, Set.of(), 1,
		5, 7, 5, 7, 5, 10, SettlementTier.CAMP, Set.of(), Set.of(), List.of(Hearth.id("house/cottage")), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Habits h = d.habits();
		boolean round = d.chance(h.plan().round()) && d.frontage() >= 5 && d.depth() >= 5;
		if (round) {
			round(d);
			return;
		}
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.FLAT, RoofForm.PAGODA,
			RoofForm.SHED));
		square(d, form);
	}

	/** A square hut: one room under a gable, hip, flat or curved roof. */
	private static void square(Draft d, RoofForm form) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean flat = form == RoofForm.FLAT;
		boolean water = d.site().anyWater(0, 0, w - 1, dp - 1);
		boolean stilted = h.plan().stilts() == Habits.Stilts.ALWAYS || water;
		// a stilted hut wants a porch in front; on a shallow plot it gets a stair along its front instead
		int front = dp >= 7 ? 2 : dp >= 6 || stilted ? 1 : 0;
		int bx0 = w >= 7 ? 1 : 0;
		int bx1 = w >= 7 ? w - 2 : w - 1;
		int bz0 = front;
		int bz1 = dp - 1;
		int base = 0;
		if (stilted) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					int wy = d.site().water(x, z);
					base = Math.max(base, Math.max(d.site().ground(x, z) + Math.max(1, h.plan().stiltHeight()) + 1, wy == Site.NONE ? 0 : wy + 2));
				}
			}
			base = Math.min(base, front + 1);
		}
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{Math.max(4, h.walls().storeyHeight())}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		Facade facade = Facade.plan(d, body, (bx0 + bx1) / 2, 1, 1, 0.5);
		Integer placed = HouseBuilder.doorColumn(facade);
		int door = placed != null ? placed : (bx0 + bx1) / 2;
		int plate = body.plateY();
		d.eave(plate);
		HouseBuilder.ground(d, body, front, stilted);
		HouseBuilder.shell(d, body, facade);
		HouseBuilder.openings(d, body, facade, !stilted);
		if (stilted && front >= 1) {
			if (front >= 2) {
				Side fw = body.side(0, Dirs.FRONT);
				Porches.porch(d, fw, bx0, bx1, 1, base, 3, door);
			}
			Groundwork.approach(d, door, base, bz0 - 1);
		}
		Roofs.Roof roof = null;
		int topY;
		if (flat) {
			topY = Roofs.flat(d, bx0, bz0, bx1, bz1, plate, true);
			Mat wood = d.mat(Role.FURNITURE);
			// a ladder up the back wall to the roof
			if (bz1 + 1 < dp) {
				dev.voidmc.hearth.build.kit.Stairs.ladder(d, (bx0 + bx1) / 2, bz1 + 1, base, topY, Dirs.BACK);
			}
			Garden.pot(d, bx0 + 1, topY, bz0 + 1);
		} else {
			Direction.Axis ridge = bx1 - bx0 >= bz1 - bz0 ? Direction.Axis.X : Direction.Axis.Z;
			roof = Roofs.build(d, bx0, bz0, bx1, bz1, plate, form, ridge, Roofs.Opts.of(d), false, false).roof();
			topY = roof.ridgeY;
		}
		furnish(d, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, base, door, roof, h);
		// a woodpile or a log store against the side, a path to the door
		if (bx0 >= 1 && d.chance(h.yard().woodpile())) {
			Garden.woodpile(d, bx0 - 1, 0, bz1 - 1, Dirs.FRONT, Math.min(3, bz1 - bz0));
		}
		if (front >= 1 && !stilted) {
			Garden.path(d, Garden.line(door, bz0 - 1, door, 0), 0);
			if (front >= 2 && d.chance(0.6)) {
				Garden.garden(d, door + 1, 0, Math.min(w - 1, door + 2), bz0 - 1, 0);
			}
		}
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, topY + 2);
	}

	/** One room: a bed, a hearth, a chest, a bench, a lantern. */
	private static void furnish(Draft d, int x0, int z0, int x1, int z1, int y, int door, Roofs.Roof roof, Habits h) {
		Rooms.Room room = new Rooms.Room(x0, z0, x1, z1, y, 3);
		room.keep(door, z0);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	/** A round hut: a ring wall of plaster and posts under a cone roof, a door to the street, a hearth in the middle. */
	private static void round(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		double r = Math.min(w, dp) >= 7 ? 3.0 : 2.0;
		double cx = (w - 1) / 2.0;
		double cz = dp - 1 - r;
		int wallH = 3;
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		Mat floor = d.mat(Role.FLOOR);
		List<int[]> ring = Draft.ellipseCells(cx, cz, r, r, false);
		List<int[]> disc = Draft.ellipseCells(cx, cz, r, r, true);
		int x0 = (int) Math.floor(cx - r);
		int z0 = (int) Math.floor(cz - r);
		int x1 = (int) Math.ceil(cx + r);
		int z1 = (int) Math.ceil(cz + r);
		d.body(x0, z0, x1, z1);
		// footings and floor
		for (int[] c : disc) {
			boolean edge = contains(ring, c);
			int g = d.site().ground(c[0], c[1]);
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(-1, g - 2); y <= -1; y++) {
					d.set(c[0], y, c[1], edge ? d.mat(Role.FOUNDATION).full() : floor.full());
				}
			}
		}
		// the ring wall: a plinth course, plaster above, posts where the ring turns
		for (int[] c : ring) {
			boolean post = isTurn(ring, c);
			for (int y = 0; y < wallH; y++) {
				d.set(c[0], y, c[1], y == 0 ? plinth.full() : post ? frame.log(Direction.Axis.Y) : wall.full());
			}
		}
		for (int[] c : disc) {
			if (!contains(ring, c)) {
				d.carve(c[0], 0, c[1], c[0], wallH + 3, c[1]);
			}
		}
		// door at the front of the ring, windows to the sides
		int doorX = (int) Math.round(cx);
		int doorZ = z0;
		for (int[] c : ring) {
			if (c[0] == doorX && c[1] < doorZ + 1) {
				doorZ = c[1];
			}
		}
		int dz = doorZ;
		d.inStage(StageKind.OPENINGS, () -> d.door(doorX, 0, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(doorX, 0, dz - 1);
		d.poi(PoiType.DOOR, doorX, 0, dz - 1, Dirs.BACK);
		for (int[] c : ring) {
			boolean side = Math.abs(c[1] - cz) < 0.6 && Math.abs(c[0] - cx) > r - 0.6;
			boolean back = Math.abs(c[0] - cx) < 0.6 && c[1] > cz + r - 0.6;
			if (side || back) {
				d.set(c[0], 1, c[1], h.windows().lattice() ? d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.BOTTOM, true)
					: d.mat(Role.GLASS).pane());
			}
		}
		// the cone roof from a block outside the ring up to a point
		Roofs.Field cone = Roofs.cone(cx, cz, r + 1, 1.0);
		int rx0 = Math.max(0, x0 - 1);
		int rz0 = Math.max(-1, z0 - 1);
		int rx1 = Math.min(w - 1, x1 + 1);
		int rz1 = Math.min(dp - 1, z1 + 1);
		Roofs.Roof roof = Roofs.raster(d, cone, wallH - 1, rx0, rz0, rx1, rz1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
		// a finial at the apex
		int ax = (int) Math.round(cx);
		int az = (int) Math.round(cz);
		d.inStage(StageKind.ROOF, () -> d.set(ax, roof.ridgeY + 1, az, frame.fence()));
		// fill the gap between the ring top and the roof
		for (int[] c : ring) {
			int under = roof.bottomY(c[0], c[1]);
			for (int y = wallH; y < under; y++) {
				d.set(c[0], y, c[1], wall.full());
			}
		}
		// inside: a hearth (campfire) in the middle, a bed against the back, a chest and a bench; a small hut has no room
		// for a fire in the middle, so its fire goes wherever is left once the bed is in
		Rooms.Room room = new Rooms.Room(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 3);
		for (int z = z0 + 1; z <= z1 - 1; z++) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				if (!contains(disc, new int[]{x, z}) || contains(ring, new int[]{x, z})) {
					room.block(x, z);
				}
			}
		}
		room.keep(doorX, dz + 1);
		int[] fire = null;
		if (r >= 3 && room.claim(ax, az)) {
			fire = new int[]{ax, az};
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
		} else {
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, JobType.NONE);
			// the free cell nearest the middle, toward the back
			List<int[]> spots = new java.util.ArrayList<>();
			for (int[] c : disc) {
				if (!contains(ring, c) && d.isAbsent(c[0], 0, c[1])) {
					spots.add(c);
				}
			}
			spots.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] - cx, c[1] - cz) - 0.1 * c[1]));
			for (int[] c : spots) {
				if (room.claim(c[0], c[1])) {
					fire = c;
					break;
				}
			}
		}
		if (fire != null) {
			int fx = fire[0];
			int fz = fire[1];
			d.inStage(StageKind.INTERIOR, () -> d.set(fx, 0, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false)));
			// tended from the open side nearest the door
			Direction tend = Dirs.FRONT;
			for (Direction dir : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
				int[] n = {fx + Dirs.dx(dir), fz + Dirs.dz(dir)};
				if (contains(disc, n) && !contains(ring, n) && d.isAbsent(n[0], 0, n[1])) {
					tend = dir;
					break;
				}
			}
			d.poi(PoiType.HEARTH, fx + Dirs.dx(tend), 0, fz + Dirs.dz(tend), tend.getOpposite());
		}
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		// a path to the door, a garden bed by it
		if (dz >= 1) {
			Garden.path(d, Garden.line(doorX, dz - 1, doorX, 0), 0);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, roof.ridgeY + 3);
	}

	private static boolean contains(List<int[]> cells, int[] c) {
		for (int[] e : cells) {
			if (e[0] == c[0] && e[1] == c[1]) {
				return true;
			}
		}
		return false;
	}

	/** A ring cell where the ring changes direction (not a straight run): posts stand there. */
	private static boolean isTurn(List<int[]> ring, int[] c) {
		boolean e = contains(ring, new int[]{c[0] + 1, c[1]});
		boolean wv = contains(ring, new int[]{c[0] - 1, c[1]});
		boolean n = contains(ring, new int[]{c[0], c[1] + 1});
		boolean s = contains(ring, new int[]{c[0], c[1] - 1});
		return !(e && wv && !n && !s) && !(n && s && !e && !wv);
	}
}
