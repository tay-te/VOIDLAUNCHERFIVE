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
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.kit.noble.Turret;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * The moor's castle: a laird's tower house in its barmkin (Craigievar, Crathes, Fyvie's older towers). The tower is
 * an L: a tall harled block of five storeys under a steep gable roof with crow-stepped gables and corbelled bartizans
 * under little cones on its corners, and the jamb standing out from its front, the stair tower, a storey higher under
 * its own cap; the barmkin wall round the court with its walk, a round turret under a cone on each front corner, the
 * gate tower in the front; a range of two storeys against the far side (the guards and the kitchen below, the chapel
 * and the household above). One tall vertical mass in a low wall: nothing like a castle of wards.
 *
 * <p>Raised in seven campaigns: the barmkin and its wall in two; the tower's block; the jamb and the bartizans; the gate
 * and the turrets; the range; the court.
 */
final class TowerHouse {
	private TowerHouse() {
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
		int lx;
		int rx;
		int fz;
		int bz;
		boolean towerLeft;
		/** The tower's main block, its back and end walls the barmkin's field faces. */
		int tx0;
		int tx1;
		int tz0;
		/** The jamb before the block's front at its inner end. */
		int jx0;
		int jx1;
		int jz0;
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d);
		d.body(p.lx, p.fz, p.rx, p.bz);
		d.eave(p.walk + 6);
		Curtain.Opts o = Curtain.Opts.of(man);
		try (var ph = d.campaign(0)) {
			Groundwork.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y, true);
			Court.pave(d, m, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y);
			Curtain.run(d, m, man, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.fz, p.lx, p.bz, Dirs.LEFT, p.thick, p.walk, o);
		}
		// the barmkin wall in two campaigns: footed down a hillside it is more than a town stores at once
		try (var ph = d.campaign(1)) {
			Curtain.run(d, m, man, p.rx, p.fz, p.rx, p.bz, Dirs.RIGHT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.thick, p.walk, o);
		}
		Range block;
		try (var ph = d.campaign(2)) {
			block = block(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			jamb(d, m, man, p, block);
		}
		try (var ph = d.campaign(4)) {
			gate(d, m, man, p);
			Turret.wall(d, m, man, p.lx + 1, p.fz + 1, 5, p.walk, p.maxTop, Set.of(Dirs.RIGHT, Dirs.BACK), Manner.Cap.CONE);
			Turret.wall(d, m, man, p.rx - 1, p.fz + 1, 5, p.walk, p.maxTop, Set.of(Dirs.LEFT, Dirs.BACK), Manner.Cap.CONE);
		}
		try (var ph = d.campaign(5)) {
			range(d, m, man, p);
		}
		try (var ph = d.campaign(6)) {
			int wx = (p.towerLeft ? p.jx1 + p.rx - 8 : p.jx0 + p.lx + 8) / 2;
			int wz = (p.fz + p.jz0) / 2 + 1;
			if (Castle.open(d, wx - 2, wz - 2, wx + 2, wz + 2, p.y, p.y + 3)) {
				Court.well(d, m, wx, wz, p.y);
			}
			Court.lamps(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y);
			d.poi(PoiType.MEETING_POINT, p.c, p.y, p.fz + p.thick + 4, Dirs.BACK);
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
		p.towerLeft = d.param("tower_side", d.chance(0.5) ? "left" : "right").equals("left");
		p.lx = 2;
		p.rx = p.w - 3;
		p.fz = 6;
		p.bz = p.dp - 3;
		p.y = Court.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, -1, 1);
		p.walk = p.y + 5;
		int len = p.w >= 43 ? 15 : 13;
		p.tx0 = p.towerLeft ? p.lx : p.rx - len + 1;
		p.tx1 = p.tx0 + len - 1;
		p.tz0 = p.bz - 9;
		p.jx0 = p.towerLeft ? p.tx1 - 6 : p.tx0;
		p.jx1 = p.jx0 + 6;
		p.jz0 = p.tz0 - 6;
		return p;
	}

	/** The block's storeys, floor to floor: the vault, the hall, the laird's chamber, the steward's, the garret; as the height allows. */
	private static int[] storeys(Plan p) {
		int[] want = {5, 6, 4, 4, 4};
		int room = p.maxTop - p.y - 9;
		List<Integer> hs = new ArrayList<>();
		int sum = 0;
		for (int h : want) {
			if (sum + h > room && hs.size() >= 3) {
				break;
			}
			hs.add(h);
			sum += h;
		}
		return hs.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * The tower's main block: harled walls, slits in the vault, windows above, a steep gable roof with crow-stepped
	 * gables and chimneys; the rooms: the vaulted stores, the hall with the throne, the laird's chamber with its chapel
	 * niche, the steward's room with the strongbox, the garret's beds.
	 */
	private static Range block(Draft d, Dress m, Manner man, Plan p) {
		int[] hs = storeys(p);
		Range r = new Range(p.tx0, p.tz0, p.tx1, p.bz, p.y, hs, Dirs.FRONT);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		Direction field = p.towerLeft ? Dirs.LEFT : Dirs.RIGHT;
		// the jamb's doorways keep their cells clear of windows
		int jc = (p.jx0 + p.jx1) / 2;
		for (int k = 0; k < hs.length; k++) {
			r.keepClear(jc, p.tz0, r.floorY(k), r.floorY(k) + 2);
		}
		r.windows(d, m, man, 0, 1, 4, Dirs.BACK);
		for (int k = 1; k < hs.length; k++) {
			r.windows(d, m, man, k, k == 1 ? 3 : 2, 4, null);
		}
		int hx = p.towerLeft ? p.tx0 + 3 : p.tx1 - 3;
		int[] hall = r.hearth(d, hx, p.bz, 1, Dirs.FRONT);
		int[] chamber = r.hearth(d, p.towerLeft ? p.tx0 : p.tx1, (p.tz0 + p.bz) / 2, 2, field.getOpposite());
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.X, p.maxTop - 2, 0, List.of(new int[]{hall[0], hall[1]}, new int[]{chamber[0], chamber[1]}));
		// bartizans on the four corners at the wall head
		int y = r.plate() + 1;
		Turret.corner(d, m, man, p.tx0, p.tz0, -1, -1, y, p.maxTop);
		Turret.corner(d, m, man, p.tx1, p.tz0, 1, -1, y, p.maxTop);
		Turret.corner(d, m, man, p.tx0, p.bz, -1, 1, y, p.maxTop);
		Turret.corner(d, m, man, p.tx1, p.bz, 1, 1, y, p.maxTop);
		// the rooms (the jamb's doorway into each kept)
		for (int k = 0; k < hs.length; k++) {
			int[] rm = r.room(k);
			Rooms.Room room = Chambers.room(rm[0], rm[1], rm[2], rm[3], rm[4], new int[]{jc, rm[1]});
			switch (k) {
				case 0 -> Chambers.furnish(d, room, Rooms.Type.STORAGE, 0);
				case 1 -> {
					room.keep(hall[0], rm[3]);
					Chambers.hall(d, m, room, field, true);
					Range.hang(d, rm, 4);
				}
				case 2 -> {
					room.keep(field == Dirs.LEFT ? rm[0] : rm[2], (p.tz0 + p.bz) / 2);
					Chambers.niche(d, m, room, field == Dirs.LEFT ? rm[0] : rm[2], rm[3], Dirs.BACK);
					Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
				}
				case 3 -> {
					Chambers.desk(d, m, room, field == Dirs.LEFT ? rm[0] : rm[2], rm[3], Dirs.BACK);
					Chambers.furnish(d, room, Rooms.Type.BEDROOM, 1);
				}
				default -> Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
			}
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
		if (hs.length < 4) {
			int[] rm = r.room(0);
			Rooms.Room room = Chambers.room(rm[0], rm[1], rm[2], rm[3], rm[4], new int[]{jc, rm[1]});
			Chambers.desk(d, m, room, field == Dirs.LEFT ? rm[0] : rm[2], rm[3], Dirs.BACK);
		}
		return r;
	}

	/**
	 * The jamb: the stair tower before the block's front, a spiral from the court door to the garret and on to its own
	 * top room a storey over the block, a doorway into every storey of the block, its cap over it; the lord's flag.
	 */
	private static void jamb(Draft d, Dress m, Manner man, Plan p, Range block) {
		int n = block.storeys();
		int top = Math.min(p.maxTop - 8, block.floorY(n) + 2);
		int[] levels = new int[n - 1];
		for (int k = 1; k < n; k++) {
			levels[k - 1] = block.floorY(k);
		}
		Tower t = Tower.square(p.jx0, p.jz0, p.jx1, p.tz0, p.y, top, Manner.Cap.PYRAMID);
		t.footings(d, m);
		t.shell(d, m, top - 1, levels);
		t.stair(d, m, false, Dirs.FRONT);
		t.floors(d, m, top, levels);
		t.door(d, m, Dirs.FRONT, p.y, 1, true);
		int[] f = t.face(Dirs.FRONT);
		Lighting.doorLamp(d, f[0] + 1, p.y + 2, f[1] - 1, Dirs.BACK);
		for (int k = 0; k < n; k++) {
			t.door(d, m, Dirs.BACK, block.floorY(k), 1, false);
		}
		Direction court = p.towerLeft ? Dirs.RIGHT : Dirs.LEFT;
		for (int k = 1; k < n; k++) {
			t.window(d, m, court, block.floorY(k) + 1, 2);
		}
		t.flag(court);
		t.head(d, m, man, p.maxTop);
		t.light(d, top - 1);
		t.watch(d, Dirs.FRONT);
	}

	/** The gate tower in the front wall. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 5, p.maxTop - 8);
		Gate.tower(d, m, man, p.c - 3, p.fz - 3, p.c + 3, p.fz + 3, 3, p.y, p.walk, top, p.maxTop);
		d.entrance(p.c, p.y, p.fz - 4);
		Groundwork.approach(d, p.c, p.y, p.fz - 4);
		Flag.banner(d, m, p.c, p.walk + 1, p.fz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.fz - 4, Dirs.FRONT);
		Curtain.posts(d, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, 7);
	}

	/**
	 * The range against the far side of the barmkin: the guards' room and the kitchen below, the chapel and the
	 * household's beds above, a flight inside at the back end; crow-stepped gables.
	 */
	private static void range(Draft d, Dress m, Manner man, Plan p) {
		boolean left = !p.towerLeft;
		int x0 = left ? p.lx : p.rx - 6;
		int x1 = x0 + 6;
		int z0 = p.fz + 5;
		int z1 = p.bz;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction wall = court.getOpposite();
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{5, 4}, court);
		r.blind(wall, Dirs.BACK);
		int fx = left ? x1 - 2 : x0 + 2;
		Direction up = left ? Dirs.LEFT : Dirs.RIGHT;
		r.flight(fx, z1 - 1, 0, up);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int mid = (z0 + z1) / 2;
		int dz1 = z0 + 2;
		int dz2 = z1 - 3;
		r.door(d, m, court, dz1, 0, 1, false);
		r.door(d, m, court, dz2, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, wall);
		r.windows(d, m, man, 1, 2, 3, null);
		int[] hearth = r.hearth(d, left ? x0 : x1, mid + 2, 0, court);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop - 6, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int[] g = r.room(0);
		r.partition(d, m, 0, false, mid, left ? g[0] + 1 : g[2] - 1);
		int[] u = r.room(1);
		r.partition(d, m, 1, false, mid, left ? u[0] + 1 : u[2] - 1);
		int land = fx + Dirs.dx(up) * 4;
		int doorIn = left ? g[2] : g[0];
		Rooms.Room front = Chambers.room(g[0], g[1], g[2], mid - 1, g[4], new int[]{doorIn, dz1}, new int[]{left ? g[0] + 1 : g[2] - 1, mid - 1});
		Rooms.Room back = Chambers.room(g[0], mid + 1, g[2], g[3], g[4], new int[]{doorIn, dz2}, new int[]{left ? g[0] + 1 : g[2] - 1, mid + 1});
		back.keepArea(Math.min(fx, land) - 1, z1 - 1, Math.max(fx, land) + 1, z1 - 1);
		back.keep(hearth[0] + Dirs.dx(court), mid + 2);
		Rooms.furnish(d, front, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		Chambers.furnish(d, back, Rooms.Type.KITCHEN, 0);
		// upstairs the chapel at the front (its altar against the front gable), the household's beds behind
		int ax = (u[0] + u[2]) / 2;
		Sacred.altar(d, ax, u[4], u[1], Dirs.BACK, false, ax + 1);
		Rooms.Room upBack = Chambers.room(u[0], mid + 1, u[2], u[3], u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid + 1}, new int[]{land, z1 - 2});
		upBack.keepArea(Math.min(fx, land) - 1, z1 - 1, Math.max(fx, land) + 1, z1 - 1);
		upBack.keepArea(land, z1 - 2, left ? u[0] + 1 : u[2] - 1, z1 - 2);
		Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 3);
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}
}
