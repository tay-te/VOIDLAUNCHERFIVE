package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Curtain;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Flag;
import dev.voidmc.hearth.build.kit.noble.Gate;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Outline;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.kit.noble.Turret;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * The steppe's seat: a royal enclosure, round as everything on the savanna is round (the Great Enclosure, the palaces of
 * the Sahel's kings). An earthen ring wall with pointed merlons, round towers under cones standing out of it; the gate
 * between two round towers; inside, the great round hall of audience under its wide cone, the throne facing the door,
 * and about it the round houses of the household under their own cones: the shrine, the steward's house with the
 * strongbox, the guards' house, the cookhouse, the sleeping houses, the granaries. A ring of cones inside a ring wall:
 * no castle elsewhere looks like it.
 *
 * <p>Raised in campaigns: the ring (in two on a hillside); the gate and the ring towers; the great hall; the houses
 * of the household; the rest of the houses and the court.
 */
final class Ordu {
	private Ordu() {
	}

	/** Where everything stands (local coordinates). */
	static final class Plan {
		int w;
		int dp;
		int c;
		int maxTop;
		int y;
		int walk;
		int thick = 2;
		Outline ring;
		double cx;
		double cz;
		double rx;
		double rz;
		int gz;
		/** The great hall's centre and diameter. */
		int hx;
		int hz;
		int hd;
		final List<int[]> taken = new ArrayList<>();
		/** The great hall's room (what found no house is kept in it). */
		Rooms.Room hallRoom;
		/** The round houses of the household, each where the ring had room for it (else a null box: kept in the hall). */
		final List<Site> sites = new ArrayList<>();
	}

	/** A round house's use and its box (null where the ring had no room for it). */
	private record Site(Use use, int @org.jspecify.annotations.Nullable [] box) {
	}

	/** What a round house is for. */
	private enum Use {
		SHRINE, STEWARD, GUARDS, SLEEPING, COOKHOUSE, GRANARY
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d);
		d.body(p.ring.x0, p.ring.z0, p.ring.x1, p.ring.z1);
		d.eave(p.walk + 6);
		// the court levelled, the ring raised a stretch at a time where it is footed far down a hillside
		try (var ph = d.campaign(0)) {
			Court.fill(d, m, p.ring, p.thick, p.y);
		}
		Curtain.encloseInCampaigns(d, m, man, p.ring, p.thick, p.walk, Curtain.Opts.of(man), null, 0, 2);
		try (var ph = d.campaign(2)) {
			gate(d, m, man, p);
			ringTowers(d, m, man, p);
		}
		// what found no house of its own is kept in the hall, which then has no long tables to make room for it
		List<Use> homeless = new ArrayList<>();
		for (Site s : p.sites) {
			if (s.box() == null) {
				homeless.add(s.use());
			}
		}
		try (var ph = d.campaign(3)) {
			hall(d, m, man, p, homeless.isEmpty());
			lodge(d, m, p, homeless);
		}
		for (Site s : p.sites) {
			if (s.box() != null) {
				// round the hall: the shrine, the steward's and the guards' houses first, then the rest
				try (var ph = d.campaign(s.use().ordinal() <= Use.GUARDS.ordinal() ? 4 : 5)) {
					house(d, m, man, p, s.use(), s.box());
				}
			}
		}
		try (var ph = d.campaign(5)) {
			Court.lamps(d, p.ring, p.thick, p.y);
			d.poi(PoiType.MEETING_POINT, p.c, p.y, p.gz + 6, Dirs.BACK);
		}
		try (var ph = d.campaign(0)) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, p.maxTop);
		}
	}

	static Plan plan(Draft d) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.c = (p.w - 1) / 2;
		p.maxTop = d.maxHeight() - 1;
		p.cx = p.c;
		p.cz = (6 + p.dp - 2) / 2.0;
		p.rx = p.c - 2.5;
		p.rz = (p.dp - 2 - 6) / 2.0;
		p.ring = Outline.oval(p.cx, p.cz, p.rx, p.rz, 20, 0.03, d.seed());
		p.y = Court.level(d, p.ring.x0 + 4, p.ring.z0 + 4, p.ring.x1 - 4, p.ring.z1 - 4, -1, 2);
		p.walk = p.y + 5;
		p.gz = p.ring.front(p.c);
		p.taken.add(new int[]{p.c - 8, p.gz - 4, p.c + 8, p.gz + 5});
		p.hd = Math.min(p.w, p.dp) >= 43 ? 15 : 13;
		int[] b = Borg.place(p.ring, p.thick, p.taken, p.hd, p.hd, p.c, (int) Math.round(p.cz) + 3);
		if (b == null) {
			b = new int[]{p.c - p.hd / 2, (int) p.cz - p.hd / 2, p.c + p.hd / 2, (int) p.cz + p.hd / 2};
			p.taken.add(b);
		}
		p.hx = (b[0] + b[2]) / 2;
		p.hz = (b[1] + b[3]) / 2;
		// round the hall: the shrine and the steward's house toward the gate, the guards by it; then the sleeping
		// houses, the cookhouse and the granaries
		site(p, Use.SHRINE, p.c - 9, p.gz + 9);
		site(p, Use.STEWARD, p.c + 9, p.gz + 9);
		site(p, Use.GUARDS, p.c - 9, p.gz + 4);
		site(p, Use.SLEEPING, p.c + 11, p.hz);
		site(p, Use.SLEEPING, p.c - 11, p.hz);
		site(p, Use.COOKHOUSE, p.c + 9, p.gz + 4);
		site(p, Use.GRANARY, p.hx - p.hd / 2 - 4, p.hz + p.hd / 2 + 1);
		site(p, Use.GRANARY, p.hx + p.hd / 2 + 4, p.hz + p.hd / 2 + 1);
		return p;
	}

	// ------------------------------------------------------------------------------------------------ the ring

	/** The gate between two round towers under cones, its hall over the passage. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 4, p.maxTop - 9);
		Gate.gatehouse(d, m, man, p.c - 4, p.gz - 1, p.c + 4, p.gz + 4, 3, p.y, p.walk, top, p.maxTop, true, 7, null);
		d.entrance(p.c, p.y, p.gz - 2);
		Groundwork.approach(d, p.c, p.y, p.gz - 2);
		Flag.banner(d, m, p.c, p.walk + 1, p.gz - 2, Dirs.FRONT, 0);
	}

	/** Round towers standing out of the ring at the sides and the back, solid to the walk, a guard's room under a cone. */
	private static void ringTowers(Draft d, Dress m, Manner man, Plan p) {
		for (double deg : new double[]{20, 90, 160}) {
			double a = Math.toRadians(deg);
			int x = (int) Math.round(p.cx + Math.cos(a) * p.rx);
			int z = (int) Math.round(p.cz + Math.sin(a) * p.rz);
			// on the wall's line, a cell in from the outline so the tower sits astride it
			int ix = x - (int) Math.signum(Math.cos(a) * 1.4);
			int iz = z - (int) Math.signum(Math.sin(a) * 1.4);
			if (ix - 3 < 0 || ix + 3 >= p.w || iz + 3 >= p.dp) {
				continue;
			}
			Turret.wall(d, m, man, ix, iz, 7, p.walk, p.maxTop, Tower.all(), Manner.Cap.CONE);
		}
	}

	// ------------------------------------------------------------------------------------------------ the hall

	/**
	 * The great round hall: one tall room under the wide cone, the door toward the gate, the throne facing it; its long
	 * tables only where it lodges no one.
	 */
	private static void hall(Draft d, Dress m, Manner man, Plan p, boolean tables) {
		int top = p.y + 7;
		Tower t = Tower.round(p.hx, p.hz, p.hd, p.y, top, Manner.Cap.CONE);
		t.footings(d, m);
		t.shell(d, m, top - 1, top);
		t.floors(d, m, top);
		t.door(d, m, Dirs.FRONT, p.y, 3, false);
		int[] f = t.face(Dirs.FRONT);
		Lighting.doorLamp(d, f[0] - 2, p.y + 2, f[1] - 1, Dirs.BACK);
		Lighting.doorLamp(d, f[0] + 2, p.y + 2, f[1] - 1, Dirs.BACK);
		for (Direction dir : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			t.window(d, m, dir, p.y + 2, 2);
		}
		t.flag(Dirs.RIGHT);
		t.head(d, m, man, p.maxTop);
		Rooms.Room room = t.room(p.y);
		Chambers.hall(d, m, room, Dirs.BACK, tables);
		p.hallRoom = room;
		t.light(d, top - 1);
	}

	/** The functions that found no house: the shrine's altar, the steward's desk, the guards' bench, beds, in the hall. */
	private static void lodge(Draft d, Dress m, Plan p, List<Use> homeless) {
		// the hall's own room, which knows its door and what the hall put in it
		Rooms.Room room = p.hallRoom;
		if (room == null || homeless.isEmpty()) {
			return;
		}
		for (Use u : homeless) {
			switch (u) {
				case SHRINE -> {
					Chambers.niche(d, m, room, room.x0, room.z1, Dirs.LEFT);
					int[] spot = Chambers.spot(d, room, Dirs.LEFT);
					if (spot != null) {
						dev.voidmc.hearth.build.kit.Furniture.workstation(d, JobType.PRIEST, spot[0], p.y, spot[1], Dirs.HORIZONTAL[spot[2]].getOpposite());
					}
				}
				case STEWARD -> Chambers.desk(d, m, room, room.x1, (room.z0 + room.z1) / 2, Dirs.RIGHT);
				case GUARDS -> guards(d, room, p.y);
				case SLEEPING -> dev.voidmc.hearth.build.kit.homestead.Beds.place(d, room, 2);
				default -> {
				}
			}
		}
	}

	/** The guards' grindstone against a wall of the room, their job site before it (else whatever the room allows). */
	private static void guards(Draft d, Rooms.Room room, int y) {
		int[] spot = Chambers.spot(d, room, Dirs.FRONT);
		if (spot != null) {
			dev.voidmc.hearth.build.kit.Furniture.workstation(d, JobType.GUARD, spot[0], y, spot[1], Dirs.HORIZONTAL[spot[2]].getOpposite());
		} else {
			Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		}
	}

	// ------------------------------------------------------------------------------------------------ the houses

	/**
	 * Sites a round house of the household near (px, pz): seven across, or a hut five across for the guards and the
	 * sleepers where the ring is tight; on the plan's list either way (no box where it found no room).
	 */
	private static void site(Plan p, Use use, int px, int pz) {
		int diam = use == Use.GRANARY ? 5 : 7;
		int[] b = Borg.place(p.ring, p.thick, p.taken, diam, diam, px, pz);
		if (b == null && (use == Use.GUARDS || use == Use.SLEEPING)) {
			b = Borg.place(p.ring, p.thick, p.taken, 5, 5, px, pz);
		}
		// the guards' hut, failing room by the gate, beside the hall where the ring is widest
		for (int side : new int[]{-1, 1}) {
			if (b == null && use == Use.GUARDS) {
				b = Borg.place(p.ring, p.thick, p.taken, 5, 5, p.c + side * 11, p.hz);
			}
		}
		p.sites.add(new Site(use, b));
	}

	/** A round house of the household in its box, its door toward the hall: earthen walls, a cone, one room for its use. */
	private static void house(Draft d, Dress m, Manner man, Plan p, Use use, int[] b) {
		int diam = b[2] - b[0] + 1;
		int cx = (b[0] + b[2]) / 2;
		int cz = (b[1] + b[3]) / 2;
		int top = p.y + (use == Use.GRANARY ? 4 : 5);
		Tower t = Tower.round(cx, cz, diam, p.y, top, Manner.Cap.CONE);
		t.footings(d, m);
		t.shell(d, m, top - 1);
		t.floors(d, m, top);
		// the door toward the hall, else toward the open court
		Direction toHall = Dirs.nearest(p.hx - cx, p.hz - cz);
		if (t.doorToward(d, m, toHall, p.y, true) == null) {
			for (Direction dir : Dirs.HORIZONTAL) {
				if (dir != toHall && t.doorToward(d, m, dir, p.y, true) != null) {
					break;
				}
			}
		}
		if (use != Use.GRANARY) {
			t.window(d, m, toHall.getOpposite(), p.y + 1, 1);
		}
		t.head(d, m, man, p.maxTop);
		Rooms.Room room = t.room(p.y);
		switch (use) {
			case SHRINE -> {
				// the altar against the back or the front wall, whichever the door is not in
				Direction face = toHall == Dirs.BACK ? Dirs.BACK : Dirs.FRONT;
				Sacred.altar(d, cx, p.y, cz - Dirs.dz(face) * (diam / 2 - 1), face, false, cx + 1);
			}
			case STEWARD -> {
				Chambers.desk(d, m, room, room.x0, cz, Dirs.LEFT);
				Chambers.furnish(d, room, Rooms.Type.BEDROOM, 1);
			}
			case GUARDS -> guards(d, room, p.y);
			case SLEEPING -> Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
			case COOKHOUSE -> Chambers.furnish(d, room, Rooms.Type.KITCHEN, 0);
			case GRANARY -> Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		}
		t.light(d, top - 1);
	}
}
