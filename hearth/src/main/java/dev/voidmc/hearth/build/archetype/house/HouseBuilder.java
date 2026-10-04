package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import dev.voidmc.hearth.build.kit.Porches;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The shared sequence houses are drawn in: lay out the body on the plot, ground it, raise the walls storey by storey, cut
 * the openings, roof it, add the chimney, the stair and the rooms, then the garden. Archetypes call the steps they need
 * and add their own character between them.
 */
final class HouseBuilder {
	private HouseBuilder() {
	}

	/** Roof forms a pitched-roof house can take. */
	static final RoofForm[] PITCHED = {RoofForm.GABLE, RoofForm.CROSS_GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.SALTBOX,
		RoofForm.PAGODA, RoofForm.FLAT};

	// ------------------------------------------------------------------------------------------------- cottage

	static void cottage(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = roofParam(d, h.roof().pick(d.random(), PITCHED));
		boolean flat = form == RoofForm.FLAT;
		boolean lowPitch = !flat && h.roof().pitch() < 0.9;
		boolean logs = h.walls().kind() == WallKind.LOG;
		// the north roofs its log cottages in turf: a low gable of sods with flowers in them, the house one storey under it
		boolean grassy = d.mat(Role.GROUND).main(dev.voidmc.hearth.build.style.Form.FULL) == Blocks.GRASS_BLOCK;
		boolean turf = !flat && logs && grassy && d.param("turf", d.chance(0.45));
		if (turf) {
			form = RoofForm.GABLE;
		}

		// the body on the plot: a front garden, a side margin where log corners or a path need it
		int front = dp >= 10 ? 3 : dp >= 8 ? 2 : 1;
		int sideL = (w >= 9 && (logs || d.chance(0.5))) ? 1 : 0;
		int sideR = (w >= 9 && (logs || d.chance(0.5))) ? 1 : 0;
		if (w >= 11) {
			sideL = 1;
			sideR = 1;
		}
		// raised houses (stilts over water or by habit) need room in front for a deck and its steps
		int stiltH = Math.max(1, h.plan().stiltHeight());
		boolean water = d.site().anyWater(sideL, front, w - 1 - sideR, dp - 1);
		boolean stilted = h.plan().stilts() == Habits.Stilts.ALWAYS || water && h.plan().stilts() != Habits.Stilts.NEVER || water;
		if (stilted) {
			front = Math.max(front, Math.min(4, dp - 6));
		}
		int back = dp - front >= 9 ? 1 : 0;
		int sh = Math.max(3, h.walls().storeyHeight());
		// on a deep plot, sometimes a lean-to outshot behind: a scullery under a roof of its own below the back eave
		boolean outshot = !flat && !stilted && dp >= 10 && sh >= 4 && w - sideL - sideR >= 6 && form != RoofForm.PAGODA
			&& form != RoofForm.MANSARD && d.param("outshot", d.chance(0.45));
		if (outshot) {
			front = Math.min(front, dp - 9);
			back = 0;
		}
		int bx0 = sideL;
		int bx1 = w - 1 - sideR;
		int bz0 = front;
		int bz1 = outshot ? dp - 5 : dp - 1 - back;
		int bw = bx1 - bx0 + 1;
		int bd = bz1 - bz0 + 1;
		Direction.Axis ridge = bw >= bd ? Direction.Axis.X : Direction.Axis.Z;
		if (!flat && d.chance(h.roof().frontGable())) {
			ridge = Direction.Axis.Z;
		}
		if (outshot) {
			// its roof runs on from the main roof's back slope
			ridge = Direction.Axis.X;
		}
		int full = flat ? (bw * bd >= 49 ? 2 : 1) : lowPitch ? (bw * bd >= 56 ? 2 : 1) : 1;
		full = Math.clamp(d.param("storeys", full), 1, 3);
		boolean attic = !flat && !lowPitch && !turf;
		int[] heights = new int[full + (attic ? 1 : 0)];
		java.util.Arrays.fill(heights, sh);
		if (attic) {
			heights[full] = 1;
		}
		int base = 0;
		if (stilted) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					int g = d.site().ground(x, z);
					int wy = d.site().water(x, z);
					base = Math.max(base, Math.max(g + stiltH + 1, wy == Site.NONE ? 0 : wy + 2));
				}
			}
			base = Math.min(base, front + 1);
		}
		Body body = new Body(bx0, bz0, bx1, bz1, base, heights, false, false);
		boolean terraced = flat && full >= 2 && h.plan().terraces() && bd >= 7;
		if (terraced) {
			// a pueblo: the upper storey stands back, the lower roof a terrace in front of it
			body.setback(1, Dirs.FRONT, Math.min(3, bd - 5));
			if (bw >= 9 && d.chance(0.5)) {
				body.setback(1, d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, 2);
			}
		} else if (full >= 2 && d.chance(h.walls().jetty())) {
			body.jetty(1, Dirs.FRONT);
		}
		d.body(bx0, bz0, bx1, bz1);

		// openings planned once for all storeys
		int doorCol = bx0 + (bw - 1) / 2 + (bw >= 8 ? d.pick(-1, 0, 1) : 0);
		int windowWidth = h.windows().tall() && bw >= 8 && d.chance(0.35) ? 2 : 1;
		Facade facade = Facade.plan(d, body, doorCol, 1, windowWidth, 0.3);
		Integer door = doorColumn(facade);

		int plate = body.plateY();
		d.eave(plate);
		ground(d, body, front, stilted);
		shell(d, body, facade);
		openings(d, body, facade, !stilted);
		int terraceY = Integer.MIN_VALUE;
		if (terraced) {
			int ty = Roofs.terrace(d, body.rect(0), body.rect(1), body.floorY(1) - 1);
			terraceY = ty;
			// a door from the upper storey out onto the terrace, a ladder up from the yard
			Side up = body.side(1, Dirs.FRONT);
			int mid = up.middle();
			d.inStage(StageKind.OPENINGS, () -> d.door(up.x(mid), ty, up.z(mid), d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR,
				Blocks.SPRUCE_DOOR), Dirs.FRONT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			d.poi(PoiType.VIEWPOINT, up.outX(mid, 1), ty, up.outZ(mid, 1), Dirs.FRONT);
			Side low = body.side(0, Dirs.FRONT);
			int li = door != null && low.index(door) <= low.middle() ? low.last() - 1 : 1;
			Stairs.ladder(d, low.outX(li, 1), low.outZ(li, 1), base, ty, Dirs.FRONT);
		}

		// what stands against the front: a porch or gallery, a bay window, a balcony
		Side frontWall = body.side(0, Dirs.FRONT);
		boolean porch = false;
		if (stilted) {
			int depth = Math.min(2, front - 1);
			Porches.porch(d, frontWall, bx0, bx1, depth, base, 3, door == null ? bx0 : door);
			porch = true;
			if (door != null) {
				// a way down from the gallery's opening to the street
				Groundwork.approach(d, door, base, bz0 - depth);
			}
		} else if (flat && door != null && front >= 2 && d.chance(Math.max(0.5, h.porch()))
			&& dev.voidmc.hearth.build.kit.homestead.Adobe.ramada(d, Math.max(0, door - 1), Math.max(0, bz0 - 2), Math.min(w - 1, door + 1), bz0 - 1, base)) {
			// the dry lands shade their doors with poles, slats and palm fronds
			porch = true;
			Groundwork.approach(d, door, base, bz0 - 1);
		} else if (door != null && front >= 3 && d.chance(h.porch())) {
			Porches.porch(d, frontWall, Math.max(bx0, door - 1), Math.min(bx1, door + 1), 2, base, 3, door);
			porch = true;
			Groundwork.approach(d, door, base, bz0 - 2);
		} else if (door != null && front >= 1) {
			Groundwork.approach(d, door, base, bz0 - 1);
		}
		if (!stilted && front >= 2 && d.chance(h.windows().bay())) {
			for (Facade.Opening o : facade.openings(Dirs.FRONT)) {
				boolean clearOfPorch = !porch || door == null || Math.abs(o.at() - door) > 2;
				if (o.kind() == Facade.Kind.WINDOW && clearOfPorch && o.at() - 1 > bx0 && o.at() + o.width() < bx1) {
					Porches.bay(d, frontWall, o.at() - (o.width() == 1 ? 1 : 0), o.at() + o.width() - (o.width() == 1 ? 0 : 1), base);
					break;
				}
			}
		}
		if (full >= 2 && d.chance(h.balcony())) {
			Side upper = body.side(1, Dirs.FRONT);
			int y1 = body.floorY(1);
			if (ridge == Direction.Axis.Z || bw <= 7) {
				Porches.balcony(d, upper, upper.column(1), upper.column(upper.last() - 1), y1);
			} else if (door != null) {
				Porches.balcony(d, upper, Math.max(upper.column(1), door - 1), Math.min(upper.column(upper.last() - 1), door + 1), y1);
			}
			if (door != null) {
				// a door out onto it, where the window over the front door would be
				int i = upper.index(door);
				if (i > 0 && i < upper.last()) {
					d.inStage(StageKind.OPENINGS, () -> d.door(upper.x(i), y1, upper.z(i), d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR,
						Blocks.SPRUCE_DOOR), Dirs.FRONT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
				}
			}
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, true);
			roof = null;
			terraceAccess(d, body, deck);
			dryDress(d, body, facade, deck, terraceY, door, full);
		} else if (turf) {
			roof = turfRoof(d, body, ridge);
		} else {
			roof = pitchedRoof(d, body, form, ridge, facade, topK);
		}
		if (roof != null && logs && (form == RoofForm.GABLE || form == RoofForm.CROSS_GABLE || form == RoofForm.SALTBOX)) {
			// carved horns over the gables, drip caps over the windows
			dev.voidmc.hearth.build.kit.homestead.Carving.hornsAtGables(d, roof, ridge);
		}
		if (logs || h.walls().kind() == WallKind.PLANK) {
			int[] r0 = body.rect(0);
			dev.voidmc.hearth.build.kit.homestead.Carving.dripCaps(d, r0[0], r0[1], r0[2], r0[3], base, body.plateY());
		}

		// chimney and hearth on a gable end (or the back wall of a hipped house)
		Direction chimneySide = ridge == Direction.Axis.X ? (d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT) : Dirs.BACK;
		int[] hearth = hearthSpot(d, body, chimneySide, facade);
		if (hearth != null && d.chance(h.chimney())) {
			Furniture.hearth(d, hearth[0], base, hearth[1], hearth[2] == 0 ? Dirs.RIGHT : hearth[2] == 1 ? Dirs.LEFT : Dirs.FRONT, false);
			if (roof != null) {
				Chimneys.stack(d, hearth[0], hearth[1], base + 2, roof, roof.ridgeY + 1);
			} else {
				chimneyFlat(d, hearth[0], hearth[1], deck);
			}
		} else {
			hearth = null;
		}

		// which way the stair inside will run (away from the hearth), so a door at the back can keep clear of it
		boolean leftward = hearth != null ? hearth[2] == 1 : d.chance(0.5);
		// the outshot behind, its door through the back wall
		int[] through = null;
		if (outshot && roof != null) {
			through = outshot(d, body, hearth, leftward);
		}

		// stair to the floor above, rooms
		interior(d, body, facade, roof, hearth, through, leftward);
		// a loft too low for a bed leaves the household sleeping downstairs
		FarmParts.ensureBeds(d, body, door, 2, through);

		// outside
		garden(d, body, facade, front);
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, (roof != null ? roof.ridgeY : plate + 2) + 2);
	}

	/**
	 * A lean-to outshot against the back wall: walls two blocks high under a shallow roof that starts just below the main
	 * roof's back eave and falls to the back, the plot's last row its overhang; a door through the back wall, a window,
	 * barrels and a chest inside. Spans the whole back or one end of it. Returns the cell inside the main room in front of
	 * its door (to keep free), or null if there was no room for one.
	 */
	static int[] outshot(Draft d, Body b, int[] hearth, boolean stairLeftward) {
		int dp = d.depth();
		int[] r = b.rect(0);
		int bw = r[2] - r[0] + 1;
		int base = b.baseY;
		int plate = b.plateY();
		int z0 = r[3];
		int z1 = dp - 2;
		// the whole back, or one end of it
		int ox0 = r[0];
		int ox1 = r[2];
		if (bw >= 8 && d.chance(0.6)) {
			int ow = Math.max(5, bw / 2 + 1);
			if (d.chance(0.5)) {
				ox1 = ox0 + ow - 1;
			} else {
				ox0 = ox1 - ow + 1;
			}
		}
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		boolean logs = d.habits().walls().kind() == WallKind.LOG;
		Groundwork.footings(d, ox0, z0 + 1, ox1, z1, base - 1);
		Floors.boards(d, ox0 + 1, z0 + 1, ox1 - 1, z1 - 1, base - 1);
		// the roof first, so the walls know how high to go: its high edge a block under the main eave
		int rx0 = Math.max(Math.max(0, r[0] - 1), ox0 - 1);
		int rx1 = Math.min(Math.min(d.frontage() - 1, r[2] + 1), ox1 + 1);
		Roofs.Field lean = Roofs.shed(rx0, z0 + 1, rx1, dp - 1, Dirs.FRONT, Roofs.Profile.straight(0.5));
		int rows = dp - 1 - (z0 + 1);
		int tTop = (int) Math.round((1 + 0.5 * rows) * 2);
		int y0 = plate - 1 - ((tTop + 1) / 2 - 1);
		Roofs.Roof roof = Roofs.raster(d, lean, y0, rx0, z0 + 1, rx1, dp - 1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		// walls: the two ends and the back, up to the roof
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0 + 1; z <= z1; z++) {
				for (int x = ox0; x <= ox1; x++) {
					boolean edge = x == ox0 || x == ox1 || z == z1;
					if (!edge) {
						continue;
					}
					boolean corner = (x == ox0 || x == ox1) && z == z1;
					int top = roof.covers(x, z) ? roof.bottomY(x, z) - 1 : base + 1;
					for (int y = base; y <= top; y++) {
						if (corner || logs && (x == ox0 || x == ox1)) {
							d.set(x, y, z, frame.log(Direction.Axis.Y));
						} else if (y == base && d.habits().walls().plinth() > 0) {
							d.set(x, y, z, plinth.full());
						} else if (logs) {
							d.set(x, y, z, wall.log(Direction.Axis.X).orElse(wall.full()));
						} else {
							d.set(x, y, z, wall.full());
						}
					}
				}
			}
		}
		d.carve(ox0 + 1, base, z0 + 1, ox1 - 1, base + 1, z1 - 1);
		// a window in the back wall
		int mid = (ox0 + ox1) / 2;
		if (roof.covers(mid, z1) && roof.bottomY(mid, z1) - 1 >= base + 1) {
			d.inStage(StageKind.OPENINGS, () -> d.set(mid, base + 1, z1, d.mat(Role.GLASS).pane()));
		}
		// the door through the main back wall: a column clear of the hearth, the stair and the corners
		int doorX = Integer.MIN_VALUE;
		// from the ends inward, leaving the middle of the back wall to the stair inside
		for (int k = 0; k <= ox1 - ox0; k++) {
			int x = k % 2 == 0 ? ox0 + 1 + k / 2 : ox1 - 1 - k / 2;
			if (x <= Math.max(ox0, r[0]) || x >= Math.min(ox1, r[2])) {
				continue;
			}
			if (hearth != null && Math.abs(x - hearth[0]) <= 1 && Math.abs(z0 - 1 - hearth[1]) <= 1) {
				continue;
			}
			if (!Openings.isWallBlock(d, x, base, z0) || !Openings.isWallBlock(d, x, base + 1, z0)) {
				continue;
			}
			// clear of the flight inside (or the ladder in its corner)
			if (b.storeys() >= 2) {
				int ix0 = r[0] + 1;
				int ix1 = r[2] - 1;
				int rise = b.height(0);
				boolean run = ix1 - ix0 + 1 >= rise + 2;
				if (run ? runCovers(ix0, ix1, stairLeftward, rise, x) : Math.abs(x - (stairLeftward ? ix0 : ix1)) <= 1) {
					continue;
				}
			}
			doorX = x;
			break;
		}
		if (doorX == Integer.MIN_VALUE) {
			return null;
		}
		int dx = doorX;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, base, z0, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR, Blocks.SPRUCE_DOOR),
			Dirs.BACK, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		// inside: a scullery's barrels and a chest along the walls
		Rooms.Room room = new Rooms.Room(ox0 + 1, z0 + 1, ox1 - 1, z1 - 1, base, 2);
		room.keep(dx, z0 + 1);
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		return new int[]{dx, z0 - 1};
	}

	/**
	 * A turf roof over the top storey: a low gable (rising a block in two) of sods on boards with a timber eave board, the
	 * gable ends laid up in the wall's logs to the roof. Returns the roof.
	 */
	static Roofs.Roof turfRoof(Draft d, Body b, Direction.Axis ridge) {
		int[] tr = b.topRect();
		int plate = b.plateY();
		Habits.Roof hr = d.habits().roof();
		int eave = Math.max(1, hr.eaves());
		int go = Math.max(1, hr.gableOverhang());
		boolean alongX = ridge == Direction.Axis.X;
		int nx0 = tr[0] - (alongX ? go : eave);
		int nx1 = tr[2] + (alongX ? go : eave);
		int nz0 = tr[1] - (alongX ? eave : go);
		int nz1 = tr[3] + (alongX ? eave : go);
		double pitch = 0.6;
		Roofs.Field f = Roofs.gable(nx0, nz0, nx1, nz1, ridge, Roofs.Profile.straight(pitch));
		double hWall = 1 + pitch * eave;
		int tWall = Math.max(1, (int) Math.round(hWall * 2));
		int y0 = plate + 1 - ((tWall + 1) / 2 - 1);
		Roofs.Roof r = dev.voidmc.hearth.build.kit.homestead.Turf.raster(d, f, y0, Math.max(0, nx0), Math.max(-1, nz0), Math.min(d.frontage() - 1, nx1),
			Math.min(d.depth() - 1, nz1));
		// the sods held at the gables' rakes by barge boards
		if (alongX) {
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimCol(d, r, r.x0, d.mat(Role.BEAM));
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimCol(d, r, r.x1, d.mat(Role.BEAM));
		} else {
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimRow(d, r, r.z0, d.mat(Role.BEAM));
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimRow(d, r, r.z1, d.mat(Role.BEAM));
		}
		Mat wall = d.mat(Role.WALL);
		Mat gable = d.mat(Role.GABLE);
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(tr[0], tr[1], tr[2], tr[3])) {
				boolean end = side.axis() != ridge;
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (!r.covers(x, z)) {
						continue;
					}
					for (int y = plate + 1; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, end ? wall.log(side.axis()).orElse(gable.full()) : gable.full());
					}
				}
			}
		}
		return r;
	}

	/** The {@code roof} param (a roof form name) if given, else the chosen form. */
	static RoofForm roofParam(Draft d, RoofForm chosen) {
		String p = d.param("roof", "");
		if (p.isEmpty()) {
			return chosen;
		}
		try {
			return RoofForm.valueOf(p.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return chosen;
		}
	}

	// ------------------------------------------------------------------------------------------------- steps

	/**
	 * Grounds the body: footings down to the ground (or stilts for a raised house), the front garden levelled to the street
	 * (the standing level of the plot's front), and a gentle apron of earth round the walls.
	 */
	static void ground(Draft d, Body b, int front, boolean stilted) {
		int[] r = b.rect(0);
		if (stilted) {
			Groundwork.stilts(d, r[0], r[1], r[2], r[3], b.baseY - 1, 2);
			Floors.boards(d, r[0], r[1], r[2], r[3], b.baseY - 1);
		} else {
			Groundwork.footings(d, r[0], r[1], r[2], r[3], b.baseY - 1);
		}
		if (front > 0 && !d.site().anyWater(0, 0, d.frontage() - 1, r[1] - 1)) {
			Groundwork.level(d, 0, 0, d.frontage() - 1, r[1] - 1, 0);
		}
		if (!stilted) {
			Groundwork.apron(d, r[0], r[1], r[2], r[3], b.baseY, 3);
		}
	}

	/** Floors, walls storey by storey, and the rooms' air. */
	static void shell(Draft d, Body b, Facade f) {
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			Floors.boards(d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k) - 1);
			if (k > 0) {
				// the floor of an oversailing storey runs out over the wall below
				Floors.boards(d, r[0], r[1], r[2], r[3], b.floorY(k) - 1);
			}
			Walls.storey(d, b, k, f);
			if (k > 0 && d.chance(d.habits().walls().beamEnds())) {
				Walls.beamEnds(d, b, k, f, Dirs.FRONT);
			}
		}
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			d.carve(r[0] + 1, b.floorY(k), r[1] + 1, r[2] - 1, b.floorY(k + 1) - 2, r[3] - 1);
		}
	}

	/** The front door, the windows of every storey (from the facade plan), a back door when the body is deep. */
	static void openings(Draft d, Body b, Facade f, boolean lampAtDoor) {
		Habits.Windows hw = d.habits().windows();
		Openings.WindowStyle style = hw.lattice() ? Openings.WindowStyle.LATTICE
			: d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
		int winH = hw.tall() ? 2 : 1;
		for (int k = 0; k < b.storeys(); k++) {
			int sh = b.height(k);
			if (sh < 3) {
				continue;
			}
			for (Side s : b.sides(k)) {
				if (b.isParty(s.out())) {
					continue;
				}
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					int y = b.floorY(k);
					if (o.kind() == Facade.Kind.DOOR && k == 0) {
						Openings.door(d, s, o.at(), y, o.width(), new Openings.DoorOpts(lampAtDoor, false, true));
					} else if (o.kind() == Facade.Kind.WINDOW || o.kind() == Facade.Kind.DOOR) {
						int height = Math.min(winH, sh - 2);
						Openings.window(d, s, o.at(), y + 1, o.width(), height, style);
					}
				}
			}
		}
	}

	/** A pitched roof over the top storey in the given form, with a cross gable or dormers where they fit. */
	static Roofs.Roof pitchedRoof(Draft d, Body b, RoofForm form, Direction.Axis ridge, Facade f, int topK) {
		int[] tr = b.topRect();
		int plate = b.plateY();
		Roofs.Opts o = Roofs.Opts.of(d);
		RoofForm base = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, base, ridge, o, b.partyLeft, b.partyRight);
		Side front = b.side(topK, Dirs.FRONT);
		int width = tr[2] - tr[0] + 1;
		if (form == RoofForm.CROSS_GABLE && ridge == Direction.Axis.X && width >= 7) {
			// a gable at right angles over the middle of the front (over the door bay)
			int gw = width >= 10 ? 5 : width >= 8 ? 5 : 3;
			Integer door = doorColumn(f);
			int centre = door != null ? door : (tr[0] + tr[2]) / 2;
			int col = Math.clamp(centre - gw / 2, tr[0] + 1, tr[2] - gw);
			plan.crossGable(front, col, gw, d);
		} else if (ridge == Direction.Axis.X && b.storeys() >= 2 && width >= 7 && d.chance(d.habits().roof().dormers())
			&& form != RoofForm.PAGODA && form != RoofForm.GAMBREL && form != RoofForm.MANSARD) {
			// dormers over the front windows of the floor below, keeping clear of the roof's ends
			for (Facade.Opening op : f.openings(Dirs.FRONT)) {
				if (op.kind() != Facade.Kind.WINDOW) {
					continue;
				}
				int c0 = op.at() - 1;
				int c1 = op.at() + op.width();
				if (c0 <= tr[0] || c1 >= tr[2]) {
					continue;
				}
				plan.dormer(front, op.at(), op.width(), b.floorY(topK));
			}
		}
		return plan.draw(d).roof();
	}

	/** The column of the front door, or null. */
	static Integer doorColumn(Facade f) {
		return f.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse(null);
	}

	/**
	 * Where the hearth goes: inside the given wall at its middle (x, z, and which wall: 0 left, 1 right, 2 back), clear of
	 * openings; null if there is no room.
	 */
	static int[] hearthSpot(Draft d, Body b, Direction side, Facade f) {
		int[] r = b.rect(0);
		if (r[2] - r[0] < 4 || r[3] - r[1] < 4) {
			return null;
		}
		if (side == Dirs.BACK) {
			int x = (r[0] + r[2]) / 2;
			if (f.at(Dirs.BACK, x) != null) {
				x++;
			}
			return new int[]{x, r[3] - 1, 2};
		}
		int z = (r[1] + r[3] + 1) / 2;
		int x = side == Dirs.LEFT ? r[0] + 1 : r[2] - 1;
		return new int[]{x, z, side == Dirs.LEFT ? 0 : 1};
	}

	static int chimneyFlat(Draft d, int x, int z, int deck) {
		Mat stone = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = 2; y < deck + 2; y++) {
				d.set(x, y, z, stone.full());
			}
			d.set(x, deck + 2, z, Blocks.CAMPFIRE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
		}
		return deck + 2;
	}

	/**
	 * A flat-roofed house of the dry lands made more than a box: its parapets finished (rounded mud shoulders in the
	 * pueblos, stepped merlons in stone and plaster), a wind tower in a back corner of the roof (desert), a latticed
	 * wooden screen boxed out over an upper window, the door in a painted surround with a tile at its sill, jars on the
	 * terrace and by the door.
	 */
	static void dryDress(Draft d, Body b, Facade f, int deck, int terraceY, Integer door, int full) {
		var t = dev.voidmc.hearth.build.kit.townhome.Traits.of(d);
		var kind = t.pueblo() ? dev.voidmc.hearth.build.kit.homestead.Adobe.Parapet.ROUNDED : dev.voidmc.hearth.build.kit.homestead.Adobe.Parapet.MERLONS;
		int[] tr = b.topRect();
		dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(d, tr[0], tr[1], tr[2], tr[3], deck, kind);
		if (terraceY != Integer.MIN_VALUE) {
			int[] r0 = b.rect(0);
			dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(d, r0[0], r0[1], r0[2], r0[3], terraceY, kind);
		}
		// a wind tower over the back corner away from the stair hatch
		if (!t.pueblo() && d.chance(0.8)) {
			// a square tower rising from the parapet's back corner, its vents latticed on every face
			if (tr[2] - tr[0] >= 4 && tr[3] - tr[1] >= 4) {
				dev.voidmc.hearth.build.kit.homestead.Adobe.windTower(d, tr[2] - 1, tr[3] - 1, 2, deck, 4, true);
			}
		}
		// a latticed screen boxed out over the window above the door
		if (!t.pueblo() && full >= 2) {
			Side up = b.side(1, Dirs.FRONT);
			for (Facade.Opening o : f.openings(Dirs.FRONT)) {
				if (o.kind() != Facade.Kind.WINDOW || !up.contains(o.at())) {
					continue;
				}
				int i = up.index(o.at());
				if (i >= 2 && i <= up.last() - 2) {
					dev.voidmc.hearth.build.kit.townhome.Projections.oriel(d, up, i - 1, i + 1, b.floorY(1), 2, true);
					break;
				}
			}
		}
		// the door: a painted surround, a tile at its sill, jars either side
		if (door != null) {
			Side front = b.side(0, Dirs.FRONT);
			dev.voidmc.hearth.build.kit.townhome.Fronts.doorCase(d, front, door, 1, b.baseY, t);
			int z = front.outZ(front.index(door), 1);
			dev.voidmc.hearth.build.kit.homestead.Adobe.threshold(d, door, b.baseY, z);
			for (int dx : new int[]{-1, 1}) {
				if (z >= 0 && dev.voidmc.hearth.build.kit.homestead.Spots.standable(d, door + dx, b.baseY, z)) {
					dev.voidmc.hearth.build.kit.homestead.Adobe.jar(d, door + dx, b.baseY, z);
				}
			}
		}
		// a pueblo's ladder up its front to the roof, its rails over the parapet; a horno in the yard
		if (t.pueblo()) {
			Side front = b.side(0, Dirs.FRONT);
			int[] r0 = b.rect(0);
			for (int i : new int[]{1, front.last() - 1, 2, front.last() - 2}) {
				int x = front.x(i);
				if (door != null && Math.abs(x - door) <= 1) {
					continue;
				}
				int deckY = terraceY != Integer.MIN_VALUE ? terraceY : deck;
				if (dev.voidmc.hearth.build.kit.homestead.Adobe.ladder(d, x, r0[1] - 1, Dirs.FRONT, b.baseY, deckY)) {
					break;
				}
			}
			for (int x : new int[]{0, d.frontage() - 1}) {
				if (r0[1] >= 3 && dev.voidmc.hearth.build.kit.homestead.Adobe.horno(d, x == 0 ? 0 : x, b.baseY, Math.max(1, r0[1] - 2), x == 0 ? Dirs.RIGHT : Dirs.LEFT)) {
					break;
				}
			}
		}
		// the roof lived on: a rug in the shade, seats and jars
		dev.voidmc.hearth.build.kit.homestead.Adobe.terraceLife(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, tr[0] + 1, tr[3] - 1);
	}

	/** A stair up to a flat roof terrace inside the back corner, a hatch-like opening, and furniture on the terrace. */
	static void terraceAccess(Draft d, Body b, int deck) {
		int[] r = b.topRect();
		int top = b.storeys() - 1;
		int y0 = b.floorY(top);
		int rise = deck - y0;
		int x = r[0] + 1;
		int z = r[3] - 1;
		if (r[3] - r[1] - 1 >= rise + 1) {
			Stairs.straight(d, x, y0, r[3] - rise, Dirs.BACK.getOpposite() == Dirs.FRONT ? Dirs.BACK : Dirs.BACK, rise, 1);
			// opening in the deck over the top of the flight
			for (int i = 0; i < 2; i++) {
				d.set(x, deck - 1, r[3] - 1 - i, Blocks.AIR.defaultBlockState());
			}
		} else {
			Stairs.ladder(d, x, z, y0, deck - 1, Dirs.FRONT);
			d.set(x, deck - 1, z, Blocks.AIR.defaultBlockState());
		}
		// a terrace: an awning of cloth on posts, a bench, pots
		Mat fence = d.mat(Role.FENCE);
		Mat cloth = d.mat(Role.CLOTH);
		int ax = r[2] - 3;
		int az = r[1] + 1;
		if (r[2] - r[0] >= 6 && r[3] - r[1] >= 5) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int[] p : new int[][]{{ax, az}, {r[2] - 1, az}, {ax, az + 2}, {r[2] - 1, az + 2}}) {
					d.set(p[0], deck, p[1], fence.fence());
					d.set(p[0], deck + 1, p[1], fence.fence());
				}
				for (int xx = ax; xx <= r[2] - 1; xx++) {
					for (int zz = az; zz <= az + 2; zz++) {
						d.set(xx, deck + 2, zz, cloth.slab(SlabType.BOTTOM));
					}
				}
			}
			Furniture.chair(d, ax + 1, deck, az + 1, Dirs.LEFT);
			d.poi(PoiType.VIEWPOINT, ax + 1, deck, az + 1, Dirs.FRONT);
		}
		Garden.pot(d, r[0] + 1, deck, r[1] + 1);
	}

	/** Stairs to the roof storey, then each floor's rooms. */
	static void interior(Draft d, Body b, Facade f, Roofs.Roof roof, int[] hearth, int[] keep, boolean leftwardRun) {
		int[] r0 = b.rect(0);
		int ix0 = r0[0] + 1;
		int iz0 = r0[1] + 1;
		int ix1 = r0[2] - 1;
		int iz1 = r0[3] - 1;
		int doorX = f.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse((ix0 + ix1) / 2);
		Rooms.Room ground = new Rooms.Room(ix0, iz0, ix1, iz1, b.baseY, b.height(0) - 1);
		ground.keep(doorX, iz0);
		if (keep != null) {
			ground.keep(keep[0], keep[1]);
		}
		if (hearth != null) {
			ground.block(hearth[0], hearth[1]);
			int fx = hearth[2] == 0 ? hearth[0] + 1 : hearth[2] == 1 ? hearth[0] - 1 : hearth[0];
			int fz = hearth[2] == 2 ? hearth[1] - 1 : hearth[1];
			ground.keep(fx, fz);
		}
		// a stair along the back wall to the floor above, away from the hearth
		if (b.storeys() >= 2) {
			int rise = b.height(0);
			int run = rise;
			boolean leftward = leftwardRun;
			int sz = iz1;
			// a door through the back wall (to an outshot) must not open onto the flight: run the other way, or climb a
			// ladder in the corner away from it
			boolean blocked = false;
			if (keep != null && keep[1] == sz) {
				if (runCovers(ix0, ix1, leftward, rise, keep[0])) {
					leftward = !leftward;
				}
				blocked = runCovers(ix0, ix1, leftward, rise, keep[0]);
			}
			if (ix1 - ix0 + 1 >= run + 2 && !blocked) {
				int sx = leftward ? ix0 + 1 : ix1 - 1;
				Direction dir = leftward ? Dirs.RIGHT : Dirs.LEFT;
				int start = leftward ? sx : sx;
				Stairs.straight(d, start, b.baseY, sz, dir, rise, 1);
				int endX = start + Dirs.dx(dir) * (rise - 1);
				ground.keepArea(start, sz, endX, sz);
				ground.keep(start - Dirs.dx(dir), sz);
				// the stairwell through the floor above
				int fy = b.floorY(1) - 1;
				for (int i = Math.max(0, rise - 3); i < rise; i++) {
					d.set(start + Dirs.dx(dir) * i, fy, sz, Blocks.AIR.defaultBlockState());
				}
				d.set(start + Dirs.dx(dir) * (rise - 1), fy, sz, d.mat(Role.FURNITURE).stairs(dir, Half.BOTTOM));
				Rooms.Room up = upperRoom(d, b, 1, roof);
				if (up != null) {
					up.keep(start + Dirs.dx(dir) * rise, sz);
					up.keepArea(start, sz, endX, sz);
					Rooms.furnish(d, up, Rooms.Type.BEDROOM, 2, JobType.NONE);
				}
			} else {
				// too narrow for a run: a ladder in the back corner (the one away from a back door)
				int lx = leftward ? ix0 : ix1;
				if (keep != null && keep[1] == sz && Math.abs(keep[0] - lx) <= 1) {
					lx = lx == ix0 ? ix1 : ix0;
				}
				Stairs.ladder(d, lx, iz1, b.baseY, b.floorY(1) - 1, Dirs.FRONT);
				d.set(lx, b.floorY(1) - 1, iz1, Blocks.AIR.defaultBlockState());
				ground.keep(lx, iz1);
				ground.block(lx, iz1);
				Rooms.Room up = upperRoom(d, b, 1, roof);
				if (up != null) {
					up.keep(lx, iz1);
					Rooms.furnish(d, up, Rooms.Type.BEDROOM, 2, JobType.NONE);
				}
			}
		}
		boolean bedroomBelow = b.storeys() < 2;
		int[] fire = hearth == null ? null : new int[]{hearth[0], hearth[1]};
		if (bedroomBelow) {
			// no loft: the household sleeps by the fire, its beds placed first, then the trade's piece, then the kitchen
			TownHouses.bedroom(d, ground, 2);
			dev.voidmc.hearth.build.kit.homestead.Household.first(d, ground, fire);
			Rooms.furnish(d, ground, Rooms.Type.KITCHEN, 0, JobType.NONE);
		} else {
			dev.voidmc.hearth.build.kit.homestead.Household.first(d, ground, fire);
			Rooms.furnish(d, ground, Rooms.Type.KITCHEN, 0, JobType.NONE);
		}
		dev.voidmc.hearth.build.kit.homestead.Household.dress(d, ground, ground, fire);
		// exposed joists under the ground floor's ceiling
		if (b.storeys() >= 2) {
			Floors.joists(d, ix0, iz0, ix1, iz1, b.floorY(1) - 1, (ix1 - ix0) <= (iz1 - iz0) ? Direction.Axis.X : Direction.Axis.Z, 2, b.baseY);
		}
	}

	/** Whether a straight flight along the back wall (as {@link #interior} lays it) covers column x, or its foot. */
	private static boolean runCovers(int ix0, int ix1, boolean leftward, int rise, int x) {
		int start = leftward ? ix0 + 1 : ix1 - 1;
		int dir = leftward ? 1 : -1;
		int a = start - dir;
		int b = start + dir * rise;
		return x >= Math.min(a, b) && x <= Math.max(a, b);
	}

	/** The room of storey k: inside the walls, as high as the walls or the roof allow. */
	static Rooms.Room upperRoom(Draft d, Body b, int k, Roofs.Roof roof) {
		int[] r = b.rect(k);
		int y = b.floorY(k);
		int h = b.height(k) - 1;
		if (k == b.storeys() - 1 && roof != null) {
			// under the roof: carve the attic up to the roof's underside
			for (int z = r[1] + 1; z <= r[3] - 1; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					int under = roof.covers(x, z) ? roof.bottomY(x, z) : y + 2;
					for (int yy = y; yy < under; yy++) {
						if (d.isAbsent(yy == y ? x : x, yy, z)) {
							d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			h = 2;
		}
		if (r[2] - r[0] < 2 || r[3] - r[1] < 2) {
			return null;
		}
		return new Rooms.Room(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, y, Math.max(2, h));
	}

	/**
	 * The yard: a path from the door (or the foot of its steps) to the street, flower beds along the house and the fence
	 * in the style's garden manner, a fence and gate along the street, a woodpile against a side wall, barrels or a water
	 * butt by a corner, a small tree where a corner of the plot is free.
	 */
	static void garden(Draft d, Body b, Facade f, int front) {
		int[] r = b.rect(0);
		Integer door = doorColumn(f);
		int y = 0;
		Habits.FenceKind fence = d.habits().yard().fence();
		if (door != null && front >= 1) {
			// the path runs from the first open cell in front of the door to the plot's edge
			int start = r[1] - 1;
			while (start > 0 && !d.isEmpty(door, Math.max(y, b.baseY), start) && !d.isAbsent(door, y, start)) {
				start--;
			}
			List<int[]> path = new ArrayList<>();
			for (int z = start; z >= 0; z--) {
				if (d.isAbsent(door, y, z) || d.isEmpty(door, y, z)) {
					path.add(new int[]{door, z});
				}
			}
			Garden.path(d, path, y);
			if (d.param("garden", true)) {
				List<int[]> beds = new ArrayList<>();
				for (int z = fence == Habits.FenceKind.NONE ? 0 : 1; z < r[1]; z++) {
					for (int x = 0; x < d.frontage(); x++) {
						boolean byHouse = z == r[1] - 1;
						boolean byFence = z == (fence == Habits.FenceKind.NONE ? 0 : 1);
						if (x == door || !(byHouse || byFence || front <= 2) || !d.isAbsent(x, y, z) || !d.isAbsent(x, y - 1, z)) {
							continue;
						}
						if (d.site().isWater(x, z)) {
							continue;
						}
						beds.add(new int[]{x, z});
					}
				}
				for (int[] c : beds) {
					Garden.garden(d, c[0], c[1], c[0], c[1], y);
				}
			}
			if (fence != Habits.FenceKind.NONE && front >= 2) {
				List<int[]> line = new ArrayList<>();
				for (int x = 0; x < d.frontage(); x++) {
					line.add(new int[]{x, 0});
				}
				Garden.fence(d, line, door, Dirs.FRONT);
			}
		}
		// the yard: a woodpile against a side wall, barrels by the front corner
		int len = Math.min(3, r[3] - r[1] - 1);
		if (len >= 2 && d.chance(d.habits().yard().woodpile())) {
			if (r[0] >= 1) {
				Garden.woodpile(d, r[0] - 1, y, r[3] - 1, Dirs.FRONT, len);
			} else if (r[2] <= d.frontage() - 2) {
				Garden.woodpile(d, r[2] + 1, y, r[3] - 1, Dirs.FRONT, len);
			}
		}
		if (r[2] <= d.frontage() - 2 && r[1] >= 1 && d.isAbsent(r[2] + 1, y, r[1])) {
			Garden.barrels(d, r[2] + 1, y, r[1], Dirs.BACK, 2);
		} else if (r[1] >= 2 && d.isAbsent(r[2], y, r[1] - 1)) {
			Garden.waterButt(d, r[2], y, r[1] - 1);
		}
	}
}
