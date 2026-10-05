package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.lawhouse.Courtroom;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
import dev.voidmc.hearth.build.kit.lawhouse.Turret;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The single-hall courts of {@link Courthouse}: the north's lawspeaker's hall (logs, a steep gable to the street with
 * carved crests, a gabled porch on posts over the door, a bell turret with a spire on the ridge; inside, the high seat on
 * its dais, benches along the walls and the long hearth down the floor) and the raised court house of the bayou and the
 * tropics (on stilts, a gallery on posts along the front and sides under a deep hipped roof, a stair down from the
 * gallery, a cupola with the bell; inside, benches in rows before the bench).
 */
final class CourtSingle {
	private CourtSingle() {
	}

	static void draw(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		boolean raised = t == Tradition.STILT;
		int x0 = 2;
		int x1 = w - 3;
		int cx = (x0 + x1) / 2;
		int z0 = raised ? 4 : 3;
		int z1 = dp - 2;
		int gx0 = x0 - 1;
		int gx1 = x1 + 1;
		int gz0 = z0 - 2;
		int base = raised ? Math.max(2, Grounds.stiltBase(d, gx0, gz0, gx1, z1)) : Grounds.raisedBase(d, x0, x1, z0);
		int hgt = 6;
		Body body = new Body(x0, z0, x1, z1, base, new int[]{hgt}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);
		Timber tim = Timber.frame(d);
		// --- ground and shell
		if (raised) {
			Groundwork.stilts(d, gx0, gz0, gx1, z1, base - 1, 2);
			Storeys.ground(d, body, true);
			Floors.boards(d, gx0, gz0, gx1, z1, base - 1);
		} else {
			Storeys.ground(d, body, false);
			Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		}
		Facade fac = Facade.plan(d, body, cx, 1, 1, 0.6);
		Storeys.walls(d, body, fac, 0, 0, null, false);
		Storeys.windows(d, body, fac, 0, 0, raised ? 3 : 2);
		Side front = body.side(0, Dirs.FRONT);
		Openings.door(d, front, cx, base, 1, new Openings.DoorOpts(true, false, true));
		int skyline;
		Roofs.Roof roof;
		if (raised) {
			// the gallery: posts along its edge carrying the roof's plate, a rail between them, open at the stair
			try (var st = d.stage(StageKind.FRAME)) {
				for (Side s : Side.of(gx0, gz0, gx1, z1)) {
					if (s.out() == Dirs.BACK) {
						continue;
					}
					for (int i = 0; i < s.length(); i++) {
						int x = s.x(i);
						int z = s.z(i);
						if (s.isCorner(i) || i % 2 == 0) {
							for (int y = base; y < plate; y++) {
								d.set(x, y, z, tim.post());
							}
						}
						d.set(x, plate, z, tim.beam(s.axis()));
					}
				}
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (Side s : Side.of(gx0, gz0, gx1, z1)) {
					if (s.out() == Dirs.BACK) {
						continue;
					}
					for (int i = 0; i < s.length(); i++) {
						int x = s.x(i);
						int z = s.z(i);
						if (d.isEmpty(x, base, z) && !(s.out() == Dirs.FRONT && Math.abs(x - cx) <= 1)) {
							d.set(x, base, z, tim.fence());
						}
					}
				}
			}
			Roofs.Built rb = Roofs.build(d, gx0, gz0, gx1, z1, plate, RoofForm.HIP, Direction.Axis.X,
				Roofs.Opts.of(d).withPitch(Math.max(0.5, Math.min(1.0, h.roof().pitch()))), false, false);
			roof = rb.roof();
			skyline = roof.ridgeY;
			for (int x : new int[]{gx0 + 1, gx1 - 1}) {
				Halls.lanternUnder(d, x, base, gz0 + 1, 1);
			}
			Groundwork.steps(d, cx, base, gz0 - 1, Dirs.FRONT, 3);
			// the cupola with the bell over the middle of the hip
			skyline = Math.max(skyline, Turret.onRidge(d, t, cx - 1, (z0 + z1) / 2 - 1, plate, roof.ridgeY, maxH, Towers.Cap.PYRAMID));
		} else {
			// the north's hall: a steep gable to the street, crests on its apexes, a porch over the door
			Storeys.Top top = Storeys.roof(d, body, RoofForm.GABLE, Direction.Axis.Z, Math.max(1.0, h.roof().pitch()), 8, false);
			roof = top.roof();
			skyline = top.ridge();
			if (roof != null) {
				int rz0 = Math.max(0, roof.z0);
				int rz1 = roof.z1;
				crest(d, cx, roof.surfaceY(cx, rz0) + 1, rz0, Dirs.FRONT);
				crest(d, cx, roof.surfaceY(cx, rz1) + 1, rz1, Dirs.BACK);
				skyline = Math.max(skyline, roof.ridgeY + 2);
				skyline = Math.max(skyline, Turret.onRidge(d, t, cx - 1, z0 + 2, plate, roof.ridgeY, maxH,
					d.style().culture() == Culture.TUNDRA ? Towers.Cap.ONION : Towers.Cap.SPIRE));
			}
			porch(d, cx, base, z0, tim);
			if (base >= 1) {
				Grounds.flight(d, cx - 1, cx + 1, z0 - 3, base, base);
			}
			Grounds.approach(d, cx, base, z0 - 3);
		}
		// --- the court
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		Set<Long> keep = new HashSet<>();
		keep.add(Courtroom.key(cx, iz0));
		keep.add(Courtroom.key(cx, iz0 + 1));
		Courtroom.furnish(d, ix0, iz0, ix1, iz1, base, hgt - 1, keep, new HashSet<>(), raised ? Courtroom.Seating.ROWS : Courtroom.Seating.FIRE);
		// --- the notice board by the way in
		CourtHall.noticeBoard(d, cx, raised ? Math.max(1, gz0 - 2) : z0 - 1, cx - 2, cx + 2);
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, cx);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, skyline + 2));
	}

	/** A carved crest on a gable's apex: a post rising from the ridge end and a scroll of the trim leaning out. */
	static void crest(Draft d, int x, int y, int z, Direction out) {
		if (y <= 0 || !d.allowed(x, y, z) || !d.allowed(x, y + 1, z)) {
			return;
		}
		Timber t = Timber.frame(d);
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(x, y, z, t.fence());
			d.set(x, y + 1, z, d.mat(Role.ROOF_TRIM).stairsOrSlab(out.getOpposite(), Half.BOTTOM));
		}
	}

	/**
	 * A gabled porch before the door at (cx, base) of a front wall at z0: two carved posts two rows out on stone feet,
	 * beams back to the wall, a little gable roof with its end boarded and a crest on its apex, a lantern inside.
	 */
	static void porch(Draft d, int cx, int base, int z0, Timber t) {
		int pz = z0 - 2;
		if (pz < 0) {
			return;
		}
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{cx - 1, cx + 1}) {
				for (int y = Math.min(base, d.site().ground(x, pz)) - 1; y < base; y++) {
					d.set(x, y, pz, Stone.footing(d).full());
				}
				for (int y = base; y <= base + 2; y++) {
					d.set(x, y, pz, t.post());
				}
				for (int z = pz; z <= z0 - 1; z++) {
					d.set(x, base + 3, z, t.beam(Direction.Axis.Z));
				}
			}
			d.set(cx, base + 3, pz, t.beam(Direction.Axis.X));
		}
		Roofs.Field f = Roofs.gable(cx - 2, pz - 1, cx + 2, z0 - 1, Direction.Axis.Z, Roofs.Profile.straight(1.0));
		Roofs.Roof r;
		try (var st = d.stage(StageKind.ROOF)) {
			r = Roofs.raster(d, f, base + 4, cx - 2, Math.max(-1, pz - 1), cx + 2, z0 - 1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.TRIM);
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				int under = r.bottomY(x, pz);
				for (int y = base + 4; y < under; y++) {
					d.set(x, y, pz, d.mat(Role.GABLE).full());
				}
			}
		}
		int cz = Math.max(-1, pz - 1);
		if (r.covers(cx, cz)) {
			crest(d, cx, r.surfaceY(cx, cz) + 1, cz, Dirs.FRONT);
		}
		Halls.lanternUnder(d, cx, base, z0 - 1, 0);
	}

	/** Slab helper for a louvre on a ridge. */
	static SlabType bottom() {
		return SlabType.BOTTOM;
	}
}
