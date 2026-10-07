package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Moot;
import dev.voidmc.hearth.build.kit.deep.Rock;
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
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The moot halls of the dry south (art review 28): a court behind a walled front, the hall across its back.
 *
 * <p><b>Domed court</b> (the desert): walls of the culture's render on a course of the realm's stone, crenellated; in
 * the middle of the front a great portal standing proud and higher than the walls (a <i>pishtaq</i>) with a pointed
 * arch over a recess and the doors at its back, slim towers either side of it under little domes; inside an open court
 * with a pool, arcades of pointed arches down its sides; across the back the hall, flat-roofed behind a parapet, a copper
 * dome on a drum over its middle.
 *
 * <p><b>Earthen hall</b> (the steppe): the Sahel's mud halls. The same court, but the walls are buttressed every few
 * cells with pinnacles rising over the parapet, timber <i>torons</i> stud the faces, and the front is a tower of
 * buttresses round a door under a timber lintel; no dome.
 */
final class HallCourt {
	private HallCourt() {
	}

	static void draw(Draft d, Glow g, int h, boolean sahel) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int bx0 = 1;
		int bx1 = w - 2;
		int zf = 2;
		int zb = dp - 1;
		int c = w / 2;
		int hw = Math.min(6, top - 4);
		// the hall across the back: about half the depth, at least eight
		int hd = Math.max(8, (zb - zf) / 2);
		int zh = zb - hd + 1;
		d.body(bx0, zf, bx1, zb);
		HallParts.ground(d, zf);
		Groundwork.footings(d, bx0, zf, bx1, zb, -1);
		Mat rough = d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION) : d.mat(Role.PLINTH);
		Mat plinth = d.mat(Role.PLINTH);
		Mat dress = Rock.dressing(d);
		Mat paving = d.mat(Role.PAVING);
		Mat floor = d.mat(Role.FLOOR);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint trimP = DeepManner.home(d, Role.WALL_ACCENT).orElse(dress.full());
		// the outer walls and the hall's front wall: a course of the realm's stone, render above, a band under the top
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zf; z <= zb; z++) {
				for (int x = bx0; x <= bx1; x++) {
					boolean edge = x == bx0 || x == bx1 || z == zf || z == zb || z == zh;
					if (!edge) {
						continue;
					}
					for (int y = -1; y <= hw; y++) {
						d.set(x, y, z, y == -1 ? plinth.full() : y == 0 ? rough.full() : y == hw - 1 ? trimP : render);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = zf + 1; z < zb; z++) {
				for (int x = bx0 + 1; x < bx1; x++) {
					d.set(x, -1, z, z > zh ? floor.full() : paving.full());
				}
			}
		}
		d.carve(bx0 + 1, 0, zh + 1, bx1 - 1, hw - 1, zb - 1);
		d.carve(bx0 + 1, 0, zf + 1, bx1 - 1, hw - 1, zh - 1);
		// the hall's deck and parapet
		Paint deck = DeepManner.home(d, Role.ROOF).orElse(d.mat(Role.ROOF).full());
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = zh; z <= zb; z++) {
				for (int x = bx0; x <= bx1; x++) {
					d.set(x, hw, z, deck);
				}
			}
		}
		crown(d, bx0, zf, bx1, zb, zh, hw, sahel);
		arcades(d, bx0, zf, bx1, zh, hw, c, sahel);
		court(d, bx0 + 4, zf + 1, bx1 - 4, zh - 1, g);
		// the hall's door from the court under an arched head
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.air(c, 0, zh);
		d.air(c, 1, zh);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, 0, zh, door, Dirs.FRONT, DoorHingeSide.LEFT));
		Rock.archedDoorHead(d, c, 0, zh, Dirs.FRONT, true, g);
		// windows high in the hall's sides
		for (int z = zh + 2; z < zb - 1; z += 3) {
			Rock.window(d, bx0, 2, z, Dirs.LEFT, 1, 2, true);
			Rock.window(d, bx1, 2, z, Dirs.RIGHT, 1, 2, true);
		}
		if (sahel) {
			front(d, c, zf, top, hw, g);
			buttresses(d, bx0, zf, bx1, zb, hw, c, top);
		} else {
			portal(d, c, zf, top, hw, g);
			towers(d, bx0, bx1, zf, top, hw, g);
			dome(d, bx0, bx1, zh, zb, hw, top, g);
		}
		d.entrance(c, 0, zf - 1);
		d.poi(PoiType.DOOR, c, 0, zf - 1, Dirs.BACK);
		Moot.furnish(d, new Moot.Room(bx0 + 1, zh + 1, bx1 - 1, zb - 1, 0, hw, c, Moot.Fire.BRAZIER, 0), g);
		Lighting.room(d, bx0 + 1, zf + 1, bx1 - 1, zh - 1, 0);
		HallParts.apron(d, c, g, sahel ? 4 : 5);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/**
	 * The walls' crown: stepped merlons in the desert (a block and a slab by turns); in the steppe a rounded parapet of
	 * slabs between the pinnacles.
	 */
	private static void crown(Draft d, int x0, int z0, int x1, int z1, int zh, int hw, boolean sahel) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint slab = DeepManner.home(d, Role.WALL, Form.SLAB, st -> st).orElse(Rock.dressing(d).slab(SlabType.BOTTOM));
		int y = hw + 1;
		if (y >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] p : Draft.ring(x0, z0, x1, z1)) {
				int x = p[0];
				int z = p[1];
				boolean merlon = sahel ? false : (x + z) % 2 == 0;
				d.set(x, y, z, merlon ? render : slab);
			}
			for (int x = x0 + 1; x < x1; x++) {
				d.set(x, y, zh, sahel || x % 2 == 0 ? slab : render);
			}
		}
	}

	/**
	 * Arcades down the court's sides: a walk two wide under a flat roof, its front a row of piers with pointed arches (round
	 * in the steppe's timber-lintelled way: a beam across) between them.
	 */
	private static void arcades(Draft d, int x0, int z0, int x1, int zh, int hw, int c, boolean sahel) {
		Mat dress = Rock.dressing(d);
		Mat beam = d.mat(Role.BEAM);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat ceiling = d.mat(Role.CEILING).has(Form.FULL) ? d.mat(Role.CEILING) : d.mat(Role.ROOF);
		int ay = 4;
		for (int side = 0; side < 2; side++) {
			int px = side == 0 ? x0 + 3 : x1 - 3;
			int wx0 = side == 0 ? x0 + 1 : x1 - 2;
			int wx1 = side == 0 ? x0 + 2 : x1 - 1;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = z0 + 1; z < zh; z++) {
					boolean pier = (z - z0 - 1) % 3 == 0 || z == zh - 1;
					for (int y = 0; y <= ay; y++) {
						if (pier || y >= ay - 1) {
							d.set(px, y, z, y == ay ? render : pier ? dress.full() : render);
						}
					}
					if (!pier) {
						boolean afterPier = (z - z0 - 2) % 3 == 0;
						boolean beforePier = (z - z0) % 3 == 0 || z == zh - 2;
						if (sahel) {
							d.set(px, ay - 1, z, beam.log(Direction.Axis.Z));
						} else if (afterPier && !beforePier) {
							d.set(px, ay - 2, z, dress.stairsOrSlab(Dirs.FRONT, Half.TOP));
						} else if (beforePier && !afterPier) {
							d.set(px, ay - 2, z, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
						}
					}
				}
			}
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z = z0 + 1; z < zh; z++) {
					for (int x = wx0; x <= wx1; x++) {
						d.set(x, ay, z, ceiling.full());
					}
				}
			}
			Lighting.room(d, wx0, z0 + 1, wx1, zh - 1, 0);
		}
	}

	/** The court: paved, a pool in the middle in a dressed kerb, planters of the realm at its corners. */
	private static void court(Draft d, int x0, int z0, int x1, int z1, Glow g) {
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		if (x1 - x0 < 4 || z1 - z0 < 4) {
			return;
		}
		Mat dress = Rock.dressing(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					boolean mid = x == cx && z == cz;
					d.set(x, -1, z, mid ? Blocks.WATER.defaultBlockState() : dress.full().at(x, -1, z));
					if (!mid) {
						d.set(x, 0, z, dress.slab(SlabType.BOTTOM));
					}
				}
			}
			d.set(cx, -2, cz, dress.full());
		}
		for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			if (d.isAbsent(p[0], 0, p[1])) {
				Rock.lampPost(d, p[0], 0, p[1], true, g);
			}
		}
	}

	/**
	 * The portal: a frame seven wide standing proud of the front wall and higher than it, a pointed arch over a recess one
	 * deep, the doors at its back under a carved head with the realm's glow, a crown of merlons.
	 */
	private static void portal(Draft d, int c, int zf, int top, int hw, Glow g) {
		Mat dress = Rock.dressing(d);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint trimP = DeepManner.home(d, Role.WALL_ACCENT).orElse(dress.full());
		int pt = Math.min(top - 1, hw + 6);
		int z = zf - 1;
		int arch = Math.min(pt - 2, 5);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - 3; x <= c + 3; x++) {
				for (int zz = z; zz <= zf; zz++) {
					for (int y = -1; y <= pt; y++) {
						boolean frame = Math.abs(x - c) == 3 || y > arch;
						boolean back = zz == zf;
						if (!frame && !back) {
							continue;
						}
						d.set(x, y, zz, y == -1 ? d.mat(Role.PLINTH).full() : Math.abs(x - c) == 3 && zz == z || y == pt ? trimP : render);
					}
				}
			}
			// the pointed arch: shoulders stepping in to a point
			for (int k = 0; k < 2; k++) {
				d.set(c - 2 + k, arch - 1 + k, z, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
				d.set(c + 2 - k, arch - 1 + k, z, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			}
			d.set(c, arch + 1, z, g.accent.defaultBlockState());
			// the carved head over the doors in the recess
			for (int x = c - 2; x <= c + 2; x++) {
				for (int y = 2; y < arch; y++) {
					d.set(x, y, zf, x == c && y == 3 ? g.accent.defaultBlockState() : Rock.carved(d).defaultBlockState());
				}
			}
		}
		d.carve(c - 2, 0, z, c + 2, arch - 2, z);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int x = c - 1; x <= c + 1; x++) {
			int fx = x;
			d.air(fx, 0, zf);
			d.air(fx, 1, zf);
			DoorHingeSide hinge = x <= c ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, 0, zf, door, Dirs.FRONT, hinge));
		}
		Lighting.chandelier(d, c - 2, z, arch - 1, arch - 2);
		Lighting.chandelier(d, c + 2, z, arch - 1, arch - 2);
		// merlons on the portal's crown
		if (pt + 1 <= top) {
			try (var s = d.stage(StageKind.ROOF)) {
				for (int x = c - 3; x <= c + 3; x += 2) {
					d.set(x, pt + 1, z, render);
				}
			}
		}
	}

	/** Slim towers at the front corners: shafts of the render with a band and a balcony course, a little dome on top. */
	private static void towers(Draft d, int bx0, int bx1, int zf, int top, int hw, Glow g) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint trimP = DeepManner.home(d, Role.WALL_ACCENT).orElse(Rock.dressing(d).full());
		Mat dress = Rock.dressing(d);
		int cap = Math.min(top - 2, hw + 6);
		if (cap <= hw + 2) {
			return;
		}
		for (int tx : new int[]{bx0, bx1 - 2}) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int z = zf; z <= zf + 2; z++) {
					for (int x = tx; x <= tx + 2; x++) {
						for (int y = -1; y <= cap; y++) {
							d.set(x, y, z, y == -1 ? d.mat(Role.PLINTH).full() : y == hw - 1 || y == cap ? trimP : render);
						}
					}
				}
				// the balcony course two under the cap: a ring of upside-down stairs
				for (int[] p : Draft.ring(tx - 1, zf - 1, tx + 3, zf + 3)) {
					int x = p[0];
					int z = p[1];
					if (x < 0 || x >= d.frontage() || z < 0) {
						continue;
					}
					if (d.isAbsent(x, cap - 1, z)) {
						Direction to = x < tx ? Dirs.RIGHT : x > tx + 2 ? Dirs.LEFT : z < zf ? Dirs.BACK : Dirs.FRONT;
						d.set(x, cap - 1, z, dress.stairsOrSlab(to, Half.TOP));
					}
				}
			}
			// openings round the top stage, a lantern in the shaft's head
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(tx + 1, cap - 2, zf, Blocks.AIR.defaultBlockState());
				d.set(tx + 1, cap - 3, zf, Blocks.AIR.defaultBlockState());
			}
			Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
			Roofs.Roof dr = Roofs.raster(d, Roofs.dome(tx + 1, zf + 1, 1.0, 1.2), cap + 1, tx, zf, tx + 2, zf + 2, sheet, d.mat(Role.ROOF_TRIM),
				Habits.Ridge.FULL);
			HallParts.solid(d, dr, cap + 1, sheet.full());
			Rock.copper(d, tx, cap + 1, zf, tx + 2, top, zf + 2, sheet, Rock.weathering(d));
		}
	}

	/** The dome over the hall: a drum walled round on the deck, a cap of copper, the realm's glow at its crown. */
	private static void dome(Draft d, int bx0, int bx1, int zh, int zb, int hw, int top, Glow g) {
		int cx = (bx0 + bx1) / 2;
		int cz = (zh + zb) / 2;
		double rad = Math.min(4.5, Math.min((bx1 - bx0) / 2.0 - 1.5, (zb - zh) / 2.0 - 1));
		int drum = 1;
		int base = hw + 1 + drum;
		int rise = Math.max(2, Math.min(top - 1 - base, (int) Math.round(rad + 0.5)));
		if (base + 1 >= top) {
			return;
		}
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Roofs.Roof r = Roofs.raster(d, Roofs.dome(cx, cz, rad, rise / (rad + 0.5)), base, cx - 5, cz - 5, cx + 5, cz + 5, sheet, d.mat(Role.ROOF_TRIM),
			Habits.Ridge.FULL);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					// the drum: walled round its rim, solid inside under the cap
					for (int y = hw + 1; y < r.surfaceY(x, z); y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, render);
						}
					}
				}
			}
		}
		Rock.copper(d, cx - 5, base, cz - 5, cx + 5, top, cz + 5, sheet, Rock.weathering(d));
		if (r.ridgeY + 1 <= top) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(cx, r.ridgeY + 1, cz, g.lamp.defaultBlockState());
			}
		}
	}

	/**
	 * The steppe's front: a tower of three buttresses round the door rising over the walls to pinnacles, a timber lintel
	 * over the door, torons up its face.
	 */
	private static void front(Draft d, int c, int zf, int top, int hw, Glow g) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat beam = d.mat(Role.BEAM);
		int tt = Math.min(top - 2, hw + 7);
		int z = zf - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - 2; x <= c + 2; x++) {
				for (int zz = z; zz <= zf; zz++) {
					for (int y = -1; y <= tt; y++) {
						if (zz == z && Math.abs(x - c) <= 1 && y <= 3) {
							continue;
						}
						d.set(x, y, zz, y == -1 ? d.mat(Role.PLINTH).full() : render);
					}
				}
			}
			for (int x = c - 1; x <= c + 1; x++) {
				d.set(x, 3, z, beam.log(Direction.Axis.X));
			}
		}
		d.carve(c - 1, 0, z, c + 1, 2, z);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int x = c - 1; x <= c + 1; x++) {
			int fx = x;
			d.air(fx, 0, zf);
			d.air(fx, 1, zf);
			DoorHingeSide hinge = x <= c ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, 0, zf, door, Dirs.FRONT, hinge));
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - 1; x <= c + 1; x++) {
				d.set(x, 2, zf, render);
			}
			d.set(c, 2, zf, g.accent.defaultBlockState());
		}
		Lighting.chandelier(d, c, z, 3, 2);
		// pinnacles on the tower's crown, torons up its face
		pinnacle(d, c - 2, z, tt + 1, top);
		pinnacle(d, c, z, tt + 1, top);
		pinnacle(d, c + 2, z, tt + 1, top);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int y = 5; y < tt; y += 3) {
				for (int x = c - 1; x <= c + 1; x += 2) {
					if (d.allowed(x, y, z - 1)) {
						d.set(x, y, z - 1, beam.log(Direction.Axis.Z));
					}
				}
			}
		}
	}

	/**
	 * Buttresses down the outer walls every third cell rising to pinnacles over the parapet, timber torons between them in
	 * two rows.
	 */
	private static void buttresses(Draft d, int bx0, int zf, int bx1, int zb, int hw, int c, int top) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat beam = d.mat(Role.BEAM);
		for (int side = 0; side < 2; side++) {
			int ox = side == 0 ? bx0 - 1 : bx1 + 1;
			Direction.Axis ax = Direction.Axis.X;
			for (int z = zf; z <= zb; z++) {
				boolean but = (z - zf) % 3 == 0 || z == zb;
				if (but) {
					try (var s = d.stage(StageKind.WALLS)) {
						for (int y = -1; y <= hw; y++) {
							d.set(ox, y, z, y == -1 ? d.mat(Role.PLINTH).full() : render);
						}
					}
					pinnacle(d, ox, z, hw + 1, top);
				} else {
					try (var s = d.stage(StageKind.FRAME)) {
						for (int y : new int[]{2, hw - 1}) {
							d.set(ox, y, z, beam.log(ax));
						}
					}
				}
			}
		}
		// along the front, the same either side of the tower
		for (int x = bx0; x <= bx1; x++) {
			if (Math.abs(x - c) <= 2) {
				continue;
			}
			if ((x - bx0) % 3 == 0) {
				try (var s = d.stage(StageKind.WALLS)) {
					for (int y = -1; y <= hw; y++) {
						d.set(x, y, zf - 1, y == -1 ? d.mat(Role.PLINTH).full() : render);
					}
				}
				pinnacle(d, x, zf - 1, hw + 1, top);
			} else {
				try (var s = d.stage(StageKind.FRAME)) {
					d.set(x, hw - 1, zf - 1, beam.log(Direction.Axis.Z));
				}
			}
		}
	}

	/** A pinnacle from y: two of the render tapering to a wall post and a cap (the Sahel's rounded spike). */
	private static void pinnacle(Draft d, int x, int z, int y, int top) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint post = DeepManner.home(d, Role.WALL, Form.WALL, st -> st).orElse(render);
		try (var s = d.stage(StageKind.ROOF)) {
			if (y <= top) {
				d.set(x, y, z, render);
			}
			if (y + 1 <= top) {
				d.set(x, y + 1, z, post);
			}
		}
	}
}
