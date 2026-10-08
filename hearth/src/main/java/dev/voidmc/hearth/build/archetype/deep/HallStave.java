package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Eaves;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Moot;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The northern moot halls (art review 28), of timber the north brings below rather than the realm's stone.
 *
 * <p><b>Stave hall</b> (northern): the hall of the stave churches. A core of upright staves between dark posts on a
 * stone sill rises out of a narrow gallery (the <i>svalgang</i>) that runs round its front and sides on posts with
 * arched heads, under a skirt roof of shingles; over the core a steep gable roof with crossed boards at its ends, a ridge
 * turret with an open belfry and a spire where the vault leaves room, a carved portal at the core's door. Inside, a long
 * fire down the middle of the floor between the benches.
 *
 * <p><b>Longhouse</b> (the tundra): one long low hall, a rough stone footing under timber walls barely a man high and a
 * great half-hipped roof sweeping down to them along the depth of the plot; a long fire down its middle, smoke under the
 * ridge, lanterns from the tie beams.
 */
final class HallStave {
	private HallStave() {
	}

	static void draw(Draft d, Glow g, DeepManner manner, int h) {
		if (manner.hallForm().equals("longhouse")) {
			longhouse(d, g, h);
		} else {
			stave(d, g, h);
		}
	}

	// ------------------------------------------------------------------------------------------------- stave hall

	private static void stave(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int gx0 = 1;
		int gx1 = w - 2;
		int gz0 = 2;
		int cx0 = 3;
		int cx1 = w - 4;
		int cz0 = 4;
		int cz1 = dp - 1;
		int c = (cx0 + cx1) / 2;
		// the tiers: the core's walls to hn1 over the gallery's skirt; where the vault leaves room an upper core on masts
		// rises out of a second skirt to hn2; the top roof as steep as the vault allows, a turret on it if it fits
		int hn1 = 6;
		int ux0 = cx0 + 2;
		int ux1 = cx1 - 2;
		int uz0 = cz0 + 2;
		int dU = (ux1 - ux0 + 2) / 2;
		int hn2 = hn1 + 3;
		boolean tiered = hn2 + (int) Math.ceil(0.75 * dU) + 1 <= top;
		int rx0 = tiered ? ux0 - 1 : cx0 - 1;
		int rx1 = tiered ? ux1 + 1 : cx1 + 1;
		int rz0 = tiered ? uz0 - 1 : cz0 - 1;
		int hn = tiered ? hn2 : hn1;
		int dmax = (rx1 - rx0) / 2;
		double pitch = 0.5;
		boolean found = false;
		for (boolean turret : new boolean[]{true, false}) {
			for (double p : new double[]{1.5, 1.25, 1.0, 0.75, 0.5}) {
				if (!found && (!turret || p >= 1.0) && hn + (int) Math.ceil(p * dmax) + 1 + (turret ? 6 : 0) <= top) {
					pitch = p;
					found = true;
				}
			}
		}
		if (tiered) {
			// a course more under the upper roof where the vault leaves it
			hn2 = Math.min(hn1 + 4, top - 1 - (int) Math.ceil(pitch * dmax) - 6 >= hn1 + 4 ? hn1 + 4 : hn2);
			hn = hn2;
		}
		d.body(cx0, cz0, cx1, cz1);

		HallParts.ground(d, gz0);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinth = d.mat(Role.PLINTH);
		Mat floor = d.mat(Role.FLOOR);
		Groundwork.footings(d, gx0, gz0, gx1, cz1, -1);
		// the floor: boards in the gallery and the hall, a stone sill under the gallery's posts
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = gz0; z <= cz1; z++) {
				for (int x = gx0; x <= gx1; x++) {
					boolean sill = x == gx0 || x == gx1 || z == gz0;
					d.set(x, -1, z, sill ? plinth.full() : floor.full());
				}
			}
		}
		staves(d, cx0, cz0, cx1, cz1, 0, hn1);
		d.carve(cx0 + 1, 0, cz0 + 1, cx1 - 1, hn1 - 1, cz1 - 1);
		gallery(d, gx0, gz0, gx1, cz1, cx0, cx1, cz0, c);
		// the skirt roof over the gallery, leaning on the core
		// (flush with the gallery where the hillside beside the plot stands near its eaves: an eave out to the plot's edge
		// is a step off the bank onto the roof)
		Roofs.Roof skirt = Eaves.skirt(d, gx0, gz0, gx1, cz1, cx0, cz0, cx1, cz1, 3, HallParts.banked(d, 2) ? 0 : 1, 0.6, false, false);
		DeepManner.rehome(d, 0, 0, 0, w - 1, hn1, dp - 1, d.mat(Role.ROOF), Role.ROOF);
		if (tiered) {
			// the masts down the hall carrying the upper core, beams along their heads, its staves over them, the second
			// skirt round it
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x : new int[]{ux0, ux1}) {
					for (int z = uz0; z < cz1; z++) {
						// masts down the hall, none on the dais (the beam rests on the back wall there)
						if ((z - uz0) % 3 == 0 && z < cz1 - 5) {
							for (int y = 0; y < hn1; y++) {
								d.set(x, y, z, frame.log(Direction.Axis.Y));
							}
						}
						d.set(x, hn1, z, beam.log(Direction.Axis.Z));
					}
				}
				for (int x = ux0; x <= ux1; x++) {
					d.set(x, hn1, uz0, beam.log(Direction.Axis.X));
				}
			}
			staves(d, ux0, uz0, ux1, cz1, hn1 + 1, hn2);
			Eaves.skirt(d, cx0, cz0, cx1, cz1, ux0, uz0, ux1, cz1, hn1, 1, 0.6, false, false);
			DeepManner.rehome(d, 0, hn1, 0, w - 1, hn2, dp - 1, d.mat(Role.ROOF), Role.ROOF);
		}
		// the top roof, its ends filled with staves, crossed boards at the ridge's ends
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int ex0 = rx0 + 1;
		int ex1 = rx1 - 1;
		int ez0 = rz0 + 1;
		Roofs.Roof r = Roofs.raster(d, Roofs.gable(rx0, rz0, rx1, cz1, Direction.Axis.Z, Roofs.Profile.straight(pitch)), hn, rx0, rz0, rx1, cz1,
			roofM, trim, Habits.Ridge.TRIM);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{ez0, cz1}) {
				for (int x = ex0; x <= ex1; x++) {
					for (int y = hn + 1; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, frame.full());
					}
				}
			}
		}
		DeepManner.rehome(d, 0, hn, 0, w - 1, top, dp - 1, roofM, Role.ROOF);
		boards(d, r, c, rz0, Dirs.FRONT);
		boards(d, r, c, cz1, Dirs.BACK);
		// a little window high in the front gable
		int wy = hn + 2;
		if (wy + 1 < r.bottomY(c, ez0)) {
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(c, wy, ez0, d.mat(Role.GLASS).pane());
			}
		}
		// slits high in the top walls' sides, between the posts
		for (int z = ez0 + 2; z < cz1 - 1; z += 3) {
			for (int x : new int[]{ex0, ex1}) {
				int ox = x + (x == ex0 ? -1 : 1);
				int under = tiered ? hn1 + 2 : skirt.covers(ox, z) ? skirt.surfaceY(ox, z) + 1 : 4;
				if (under < hn - 1) {
					try (var s = d.stage(StageKind.OPENINGS)) {
						d.set(x, hn - 1, z, d.mat(Role.GLASS).pane());
					}
				}
			}
		}
		turret(d, r, c, (ez0 + cz1) / 2, top, g);
		portal(d, c, cz0, hn1);
		d.entrance(c, 0, cz0 - 1);
		d.poi(PoiType.DOOR, c, 0, cz0 - 1, Dirs.BACK);
		Moot.furnish(d, new Moot.Room(cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, 0, hn1, c, Moot.Fire.LONG, 0), g);
		// the gallery lit along its length
		Lighting.room(d, gx0 + 1, gz0 + 1, cx0 - 1, cz1, 0);
		Lighting.room(d, cx1 + 1, gz0 + 1, gx1 - 1, cz1, 0);
		Lighting.room(d, gx0 + 1, gz0 + 1, gx1 - 1, cz0 - 1, 0);
		HallParts.apron(d, c, g, Math.min(5, (w - 1) / 2 - 1));
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/**
	 * Walls of staves round {@code x0..x1 × z0..z1} from {@code y0} to the plate at {@code y1}: upright boards (the
	 * frame's) between posts at the corners and every third, a sill beam at the foot, the plate along the top.
	 */
	private static void staves(Draft d, int x0, int z0, int x1, int z1, int y0, int y1) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean side = x == x0 || x == x1;
					if (!side && z != z0 && z != z1) {
						continue;
					}
					boolean corner = side && (z == z0 || z == z1);
					boolean post = corner || (side ? (z - z0) % 3 == 0 : (x - x0) % 3 == 0);
					for (int y = y0; y <= y1; y++) {
						Paint p;
						if (post) {
							p = frame.log(Direction.Axis.Y);
						} else if (y == y0 || y == y1) {
							p = beam.log(side ? Direction.Axis.Z : Direction.Axis.X);
						} else {
							p = frame.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
		}
	}

	/**
	 * The gallery's outer line: posts every other cell on the sill with arched heads between them (upside-down stairs
	 * turned to the posts) and a rail low between, a beam along their heads; open at the front door's bay.
	 */
	private static void gallery(Draft d, int gx0, int gz0, int gx1, int gz1, int cx0, int cx1, int cz0, int c) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		java.util.List<int[]> line = new java.util.ArrayList<>();
		for (int z = gz1; z > gz0; z--) {
			line.add(new int[]{gx0, z});
		}
		for (int x = gx0; x <= gx1; x++) {
			line.add(new int[]{x, gz0});
		}
		for (int z = gz0 + 1; z <= gz1; z++) {
			line.add(new int[]{gx1, z});
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : line) {
				int x = p[0];
				int z = p[1];
				boolean corner = (x == gx0 || x == gx1) && (z == gz0 || z == gz1);
				boolean post = corner || (z == gz0 ? (x - gx0) % 2 == 0 : (z - gz0) % 2 == 0);
				boolean door = z == gz0 && Math.abs(x - c) <= 1;
				Direction.Axis along = z == gz0 ? Direction.Axis.X : Direction.Axis.Z;
				if (post && !(door && x == c)) {
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				} else {
					if (!door) {
						d.set(x, 0, z, rail.fence());
					}
					// the arched head: a stair turned to the post it springs from
					Direction toPost = z == gz0 ? ((x - gx0) % 2 == 1 ? Dirs.LEFT : Dirs.RIGHT) : ((z - gz0) % 2 == 1 ? Dirs.FRONT : Dirs.BACK);
					d.set(x, 2, z, beam.stairsOrSlab(toPost, Half.TOP));
				}
				d.set(x, 3, z, beam.log(along));
			}
		}
		d.carve(gx0 + 1, 0, gz0 + 1, gx1 - 1, 2, gz1);
		// the gallery's front bays open over the sill: where the ground stood higher the footings raised the sill line
		// as a retaining wall, so the door's bay is cleared to the floor and the others down to their rail
		for (int x = gx0 + 1; x < gx1; x++) {
			boolean door = Math.abs(x - c) <= 1;
			boolean post = (x - gx0) % 2 == 0 && x != c;
			if (post) {
				continue;
			}
			if (door) {
				d.air(x, 0, gz0);
			}
			d.air(x, 1, gz0);
		}
	}

	/** The crossed boards at a gable's apex: the barge boards carried on past the ridge, a carved beak between them. */
	private static void boards(Draft d, Roofs.Roof r, int c, int z, Direction out) {
		int y = r.ridgeY;
		Mat beam = d.mat(Role.BEAM);
		if (y == Integer.MIN_VALUE || !d.allowed(c, y + 2, z)) {
			return;
		}
		if (!d.isSolid(c, y, z)) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x : new int[]{c - 1, c + 1}) {
				if (d.isSolid(x, y, z) || d.isSolid(x, y - 1, z) && d.isSolid(c, y + 1, z)) {
					d.set(x, y + 1, z, beam.stairsOrSlab(x < c ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM));
				}
			}
			d.set(c, y + 1, z, beam.log(Direction.Axis.Y));
			d.set(c, y + 2, z, beam.stairsOrSlab(out.getOpposite(), Half.TOP));
		}
	}

	/**
	 * The ridge turret: a deck on the ridge, posts at its corners round an open belfry with a lantern hung in it, a
	 * spire of shingles (five wide where the vault leaves room, else three) with the realm's glow at its point.
	 */
	private static void turret(Draft d, Roofs.Roof r, int c, int tz, int top, Glow g) {
		int rb = r.ridgeY + 1;
		boolean broad = rb + 8 <= top;
		if (rb + 6 > top) {
			return;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = tz - 1; z <= tz + 1; z++) {
				for (int x = c - 1; x <= c + 1; x++) {
					boolean corner = x != c && z != tz;
					int from = r.covers(x, z) ? r.surfaceY(x, z) + 1 : rb;
					for (int y = from; y < rb; y++) {
						d.set(x, y, z, corner ? frame.log(Direction.Axis.Y) : frame.full());
					}
					d.set(x, rb, z, floor.full());
					if (corner) {
						d.set(x, rb + 1, z, frame.log(Direction.Axis.Y));
						d.set(x, rb + 2, z, frame.log(Direction.Axis.Y));
					} else if (x != c || z != tz) {
						d.set(x, rb + 1, z, rail.fence());
					}
					d.set(x, rb + 3, z, floor.full());
				}
			}
		}
		Lighting.chandelier(d, c, tz, rb + 3, rb + 2);
		int span = broad ? 2 : 1;
		Mat roofM = d.mat(Role.ROOF);
		Roofs.Roof spire = Roofs.raster(d, Roofs.hip(c - span, tz - span, c + span, tz + span, Roofs.Profile.straight(broad ? 1.75 : 2.0)), rb + 3,
			c - span, tz - span, c + span, tz + span, roofM, d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
		HallParts.solid(d, spire, rb + 3, roofM.full());
		DeepManner.rehome(d, c - span, rb + 3, tz - span, c + span, top, tz + span, roofM, Role.ROOF);
		int tip = spire.ridgeY + 1;
		if (tip <= top && d.isSolid(c, tip - 1, tz)) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(c, tip, tz, g.lamp.defaultBlockState());
			}
		}
	}

	/** The core's door in a carved portal: posts either side, a lintel, a beak of carving over it, lamps either side. */
	private static void portal(Draft d, int c, int z, int hn) {
		Mat beam = d.mat(Role.BEAM);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= 2; y++) {
				d.set(c - 1, y, z, beam.log(Direction.Axis.Y));
				d.set(c + 1, y, z, beam.log(Direction.Axis.Y));
			}
			d.set(c, 2, z, beam.log(Direction.Axis.X));
			d.set(c - 1, 3, z, beam.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(c + 1, 3, z, beam.stairsOrSlab(Dirs.LEFT, Half.TOP));
		}
		d.air(c, 0, z);
		d.air(c, 1, z);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, 0, z, door, Dirs.FRONT, DoorHingeSide.LEFT));
	}

	// ------------------------------------------------------------------------------------------------- longhouse

	private static void longhouse(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int bx0 = 1;
		int bx1 = w - 2;
		int bz0 = 2;
		int hn = 3;
		// a row clear behind the hall where the bank there stands near its eaves (the back hip a step off the bank)
		int bz1 = dp - 1 - (d.site().maxGround(0, dp, w - 1, dp) >= hn - 1 ? 1 : 0);
		int c = w / 2;
		int dmax = (w - 1) / 2;
		double pitch = 0.5;
		for (double p : new double[]{1.0, 0.85, 0.75, 0.6, 0.5}) {
			if (hn + (int) Math.ceil(p * dmax) + 1 <= top) {
				pitch = p;
				break;
			}
		}
		d.body(bx0, bz0, bx1, bz1);
		HallParts.ground(d, bz0);
		Groundwork.footings(d, bx0, bz0, bx1, bz1, -1);
		Mat rough = d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION) : d.mat(Role.PLINTH);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat floor = d.mat(Role.FLOOR);
		Paint timber = DeepManner.home(d, Role.WALL).orElse(frame.full());
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					boolean edge = x == bx0 || x == bx1 || z == bz0 || z == bz1;
					if (!edge) {
						continue;
					}
					boolean post = x == bx0 || x == bx1 ? (z - bz0) % 4 == 0 : (x - bx0) % 4 == 0;
					for (int y = -1; y <= hn; y++) {
						Paint p = y <= 0 ? rough.full() : post ? frame.log(Direction.Axis.Y) : y == hn ? beam.log(x == bx0 || x == bx1 ? Direction.Axis.Z
							: Direction.Axis.X) : timber;
						d.set(x, y, z, p);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = bz0 + 1; z < bz1; z++) {
				for (int x = bx0 + 1; x < bx1; x++) {
					d.set(x, -1, z, floor.full());
				}
			}
		}
		d.carve(bx0 + 1, 0, bz0 + 1, bx1 - 1, hn - 1, bz1 - 1);
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		// the eaves a block out over the walls, or flush with them where the hillside beside the plot stands near them (a
		// bank level with the eaves is a step onto the roof)
		int ex = HallParts.banked(d, hn - 1) ? 1 : 0;
		Roofs.Roof r = Roofs.raster(d, Roofs.halfHip(ex, bz0 - 1, w - 1 - ex, bz1, Direction.Axis.Z, Roofs.Profile.straight(pitch), 2), hn, ex, bz0 - 1,
			w - 1 - ex, bz1, roofM, trim, Habits.Ridge.FULL);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{bz0, bz1}) {
				for (int x = bx0; x <= bx1; x++) {
					for (int y = hn + 1; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, frame.full());
					}
				}
			}
		}
		DeepManner.rehome(d, 0, hn, 0, w - 1, top, dp - 1, roofM, Role.ROOF);
		// the door in the gable end under a porch beam, a small window either side
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 0; y <= 2; y++) {
				d.set(c - 1, y, bz0, beam.log(Direction.Axis.Y));
				d.set(c + 1, y, bz0, beam.log(Direction.Axis.Y));
			}
			d.set(c, 2, bz0, beam.log(Direction.Axis.X));
		}
		d.air(c, 0, bz0);
		d.air(c, 1, bz0);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, 0, bz0, door, Dirs.FRONT, DoorHingeSide.LEFT));
		for (int x : new int[]{c - 3, c + 3}) {
			if (x > bx0 && x < bx1) {
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(x, 2, bz0, d.mat(Role.GLASS).pane());
				}
			}
		}
		d.entrance(c, 0, bz0 - 1);
		d.poi(PoiType.DOOR, c, 0, bz0 - 1, Dirs.BACK);
		// the tie beams the lanterns hang from, two over the walls' plate
		int ceil = Math.min(hn + 3, r.ridgeY - 2);
		Moot.furnish(d, new Moot.Room(bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, 0, ceil, c, Moot.Fire.LONG, 0), g);
		// the roof's slopes are low over the side benches: posts down the hall carry the purlins
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = bz0 + 3; z < bz1 - 1; z += 4) {
				for (int x : new int[]{bx0 + 2, bx1 - 2}) {
					if (d.isAbsent(x, 0, z) && d.isAbsent(x, 1, z)) {
						for (int y = 0; y < Math.min(ceil, r.bottomY(x, z)); y++) {
							if (d.isAbsent(x, y, z) || d.isEmpty(x, y, z)) {
								d.set(x, y, z, frame.log(Direction.Axis.Y));
							}
						}
					}
				}
			}
		}
		HallParts.apron(d, c, g, Math.min(5, (w - 1) / 2 - 1));
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}
}
