package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Curtain;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Flag;
import dev.voidmc.hearth.build.kit.noble.Gate;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Mason;
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The karst's castle: not a castle of towers but a walled compound laid out on its axis, as the yamen and the citadels
 * of the south are. A low wall with a plain parapet round a long rectangle; the gate tower on the axis, a hall on its
 * solid base under a curved roof, small pavilions on the front corners; the first court with the ancestral shrine under
 * its own roof; the second gate, a gate hall across the axis between low walls; the main court with the halls of the
 * household down both sides behind timber verandas; the great hall on its stone terrace up a flight of steps, roofed in
 * two tiers (a skirt of eaves round it under the main roof), the throne inside; behind it the residence across the back;
 * at a back corner the lord's tower, each storey under its own skirt of eaves. The roofs step up from the gate to the
 * great hall: the silhouette is horizontal, layered, symmetrical.
 *
 * <p>Raised in eight campaigns: the wall in two; the gate and the towers; the second gate and the shrine; the great hall;
 * the side halls one by one; the residence and the courts.
 */
final class Compound {
	private Compound() {
	}

	/** Where everything stands (local coordinates). */
	static final class Plan {
		int w;
		int dp;
		int c;
		int maxTop;
		int y;
		int lx;
		int rx;
		int fz;
		int bz;
		int thick = 2;
		int walk;
		boolean towerLeft;
		/** The second gate's hall across the axis. */
		int g2z0;
		int g2z1;
		/** The great hall on its terrace. */
		int hx0;
		int hx1;
		int hz0;
		int hz1;
		/** The side halls down the main court. */
		int sz0;
		int sz1;
		int sd = 6;
		/** The residence across the back (none on a short plot). */
		int bhz0;
		boolean residence;
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
		try (var ph = d.campaign(1)) {
			Curtain.run(d, m, man, p.rx, p.fz, p.rx, p.bz, Dirs.RIGHT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.thick, p.walk, o);
		}
		try (var ph = d.campaign(2)) {
			gate(d, m, man, p);
			towers(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			secondGate(d, m, man, p);
			shrine(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			greatHall(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			sideHall(d, m, man, p, true);
		}
		try (var ph = d.campaign(6)) {
			sideHall(d, m, man, p, false);
		}
		try (var ph = d.campaign(7)) {
			residence(d, m, man, p);
			courts(d, m, p);
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
		int span = p.bz - p.fz;
		p.g2z0 = p.fz + 2 + Math.round(span * 0.22f);
		p.g2z1 = p.g2z0 + 4;
		p.hz0 = p.g2z1 + 6;
		p.hz1 = p.hz0 + 7;
		// as wide as the side halls and their verandas leave
		int hw = Math.clamp(p.c - p.lx - p.sd - 4, 5, 9);
		p.hx0 = p.c - hw;
		p.hx1 = p.c + hw;
		p.bhz0 = p.hz1 + 4;
		p.residence = p.bz - p.bhz0 >= 6;
		p.sz0 = p.g2z1 + 2;
		p.sz1 = p.residence ? p.bhz0 - 2 : p.bz - p.thick;
		return p;
	}

	// ------------------------------------------------------------------------------------------------ gates and towers

	/** The gate tower on the axis: a hall over the passage on its solid base under the curved roof. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 5, p.maxTop - 10);
		Gate.tower(d, m, man, p.c - 5, p.fz - 3, p.c + 5, p.fz + 3, 3, p.y, p.walk, top, p.maxTop);
		d.entrance(p.c, p.y, p.fz - 4);
		Groundwork.approach(d, p.c, p.y, p.fz - 4);
		Lighting.doorLamp(d, p.c - 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Lighting.doorLamp(d, p.c + 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Flag.banner(d, m, p.c, p.walk + 1, p.fz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.fz - 4, Dirs.FRONT);
		Curtain.posts(d, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, 7);
	}

	/** Little pavilions on the front corners; the lord's tower at a back corner, every storey under its skirt of eaves. */
	private static void towers(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 3, p.maxTop - 8);
		Castle.wallTower(d, m, man, p.lx, p.fz, 5, p.y, p.walk, top, false, Manner.Cap.PAGODA, Set.of(Dirs.RIGHT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.LEFT));
		Castle.wallTower(d, m, man, p.rx, p.fz, 5, p.y, p.walk, top, false, Manner.Cap.PAGODA, Set.of(Dirs.LEFT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.RIGHT));
		int lx = p.towerLeft ? p.lx + 1 : p.rx - 1;
		int lz = p.bz - 1;
		int head = Math.min(10, Tower.headRows(Manner.Cap.PAGODA, 7, man));
		int ltop = Math.max(p.walk + 8, Math.min(p.y + 24, p.maxTop - head));
		Tower t = Castle.wallTower(d, m, man, lx, lz, 7, p.y, p.walk, ltop, false, Manner.Cap.PAGODA,
			Set.of(p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT), Set.of(Dirs.BACK, p.towerLeft ? Dirs.LEFT : Dirs.RIGHT));
		Range box = new Range(t.x0, t.z0, t.x1, t.z1, p.y, new int[]{1}, Dirs.FRONT);
		for (int y = p.walk + 5; y < ltop - 2; y += 5) {
			t.window(d, m, Dirs.FRONT, y - 3, 2);
			t.window(d, m, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, y - 3, 2);
			box.skirt(d, m, y, 2);
		}
		t.flag(p.towerLeft ? Dirs.RIGHT : Dirs.LEFT);
	}

	/**
	 * The second gate: a hall of one storey across the axis under a curved roof, open through its middle, low walls
	 * either side of it closing the first court off from the main court.
	 */
	private static void secondGate(Draft d, Dress m, Manner man, Plan p) {
		Curtain.Opts o = new Curtain.Opts(false, false, false);
		int wall = p.y + 4;
		Curtain.run(d, m, man, p.lx + p.thick, p.g2z0 + 2, p.c - 7, p.g2z0 + 2, Dirs.FRONT, 2, wall, o);
		Curtain.run(d, m, man, p.c + 7, p.g2z0 + 2, p.rx - p.thick, p.g2z0 + 2, Dirs.FRONT, 2, wall, o);
		Range r = new Range(p.c - 6, p.g2z0, p.c + 6, p.g2z1, p.y, new int[]{6}, Dirs.FRONT);
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		// the passage through it: wide doors front and back on the axis
		r.door(d, m, Dirs.FRONT, p.c - 1, 0, 3, false);
		r.door(d, m, Dirs.BACK, p.c - 1, 0, 3, false);
		r.windows(d, m, man, 0, 3, 4, null);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop - 8, 0, List.of());
		r.skirt(d, m, r.plate() - 1, 1);
		int[] in = r.room(0);
		// the gate's guard room in its ends: benches, a lamp
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{p.c - 1, in[1]}, new int[]{p.c, in[1]}, new int[]{p.c + 1, in[1]},
			new int[]{p.c - 1, in[3]}, new int[]{p.c, in[3]}, new int[]{p.c + 1, in[3]});
		room.keepArea(p.c - 1, in[1], p.c + 1, in[3]);
		Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
		d.poi(PoiType.GATE, p.c, p.y, (p.g2z0 + p.g2z1) / 2, Dirs.FRONT);
	}

	/** The ancestral shrine in the first court, on the side away from the lord's tower: the altar, the keeper's lectern. */
	private static void shrine(Draft d, Dress m, Manner man, Plan p) {
		int z0 = p.fz + p.thick + 2;
		int z1 = p.g2z0 - 2;
		if (z1 - z0 < 5) {
			z0 = z1 - 5;
		}
		boolean left = !p.towerLeft;
		int x0 = left ? p.lx + p.thick + 1 : p.rx - p.thick - 7;
		int x1 = x0 + 6;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{5}, court);
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int dz = (z0 + z1) / 2;
		r.door(d, m, court, dz, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, court.getOpposite());
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.Z, p.maxTop - 10, 0, List.of());
		int[] in = r.room(0);
		int ax = left ? in[0] : in[2];
		Sacred.altar(d, ax, p.y, dz, court, false, Integer.MIN_VALUE);
		dev.voidmc.hearth.build.kit.Furniture.workstation(d, JobType.PRIEST, ax + Dirs.dx(court), p.y, in[1], Dirs.BACK);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	// ------------------------------------------------------------------------------------------------ the great hall

	/**
	 * The great hall on its terrace, two steps up a broad flight on the axis: one tall room under a curved roof with a
	 * skirt of eaves round it at the clerestory, a timber veranda before it, the throne against the back wall.
	 */
	private static void greatHall(Draft d, Dress m, Manner man, Plan p) {
		int lift = 2;
		int y = p.y + lift;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int x = p.hx0 - 2; x <= p.hx1 + 2; x++) {
				for (int z = p.hz0 - 3; z <= p.hz1 + 1; z++) {
					if (!Mason.inPlot(d, x, z)) {
						continue;
					}
					for (int yy = p.y - 1; yy < y - 1; yy++) {
						d.set(x, yy, z, m.footing.full());
					}
					boolean edge = x == p.hx0 - 2 || x == p.hx1 + 2 || z == p.hz0 - 3 || z == p.hz1 + 1;
					d.set(x, y - 1, z, edge ? m.dress.full() : m.paving.full());
				}
			}
			// the flight up the front of the terrace on the axis
			for (int k = 0; k < lift; k++) {
				for (int x = p.c - 2; x <= p.c + 2; x++) {
					int z = p.hz0 - 5 + k;
					for (int yy = p.y - 1; yy < p.y + k; yy++) {
						d.set(x, yy, z, m.footing.full());
					}
					d.set(x, p.y + k, z, m.paving.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				}
			}
		}
		Range r = new Range(p.hx0, p.hz0, p.hx1, p.hz1, y, new int[]{6, 4}, Dirs.FRONT);
		r.openAbove(1);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.door(d, m, Dirs.FRONT, p.c - 1, 0, 3, false);
		Side fs = r.side(Dirs.FRONT);
		for (int i = 2; i < fs.length() - 2; i += 3) {
			if (Math.abs(fs.column(i) - p.c) > 3) {
				Range.window(d, m, Manner.Window.LATTICE, fs, i, y + 1, 3);
			}
		}
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			Side s = r.side(sd);
			for (int i = 2; i < s.length() - 2; i += 3) {
				Range.window(d, m, Manner.Window.LATTICE, s, i, r.floorY(1) + 1, 2);
			}
		}
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop, 0, List.of());
		// the lower tier of eaves round the hall, over the veranda's posts
		r.skirt(d, m, r.floorY(1), 2);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = p.hx0 - 2; x <= p.hx1 + 2; x += 3) {
				for (int yy = y; yy < r.floorY(1) - 1; yy++) {
					if (Math.abs(x - p.c) > 2 && Mason.ok(d, x, yy, p.hz0 - 2) && d.isEmpty(x, yy, p.hz0 - 2)) {
						d.set(x, yy, p.hz0 - 2, Mason.post(m.frame));
					}
				}
			}
		}
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{p.c - 1, in[1]}, new int[]{p.c, in[1]}, new int[]{p.c + 1, in[1]});
		room.keepArea(p.c - 1, in[1], p.c + 1, in[1] + 1);
		Chambers.hall(d, m, room, Dirs.BACK, true);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	// ------------------------------------------------------------------------------------------------ the side halls

	/**
	 * A hall of two storeys against a side wall down the main court behind a timber veranda: on the left the kitchen and
	 * the guards' room, the household's beds over them; on the right the steward's counting room and the stores, the
	 * lord's chamber over them. A flight inside against the back end.
	 */
	private static void sideHall(Draft d, Dress m, Manner man, Plan p, boolean left) {
		int x0 = left ? p.lx : p.rx - p.sd;
		int x1 = left ? p.lx + p.sd : p.rx;
		// short of the great hall's terrace
		x1 = left ? Math.min(x1, p.hx0 - 4) : x1;
		x0 = left ? x0 : Math.max(x0, p.hx1 + 4);
		if (p.sz1 - p.sz0 < 9 || x1 - x0 < 5) {
			return;
		}
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction wall = court.getOpposite();
		Range r = new Range(x0, p.sz0, x1, p.sz1, p.y, new int[]{5, 5}, court);
		r.blind(wall);
		int fx = left ? x1 - 2 : x0 + 2;
		Direction up = left ? Dirs.LEFT : Dirs.RIGHT;
		int fz = p.sz1 - 1;
		r.flight(fx, fz, 0, up);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int mid = (p.sz0 + p.sz1) / 2;
		int dz1 = p.sz0 + 3;
		int dz2 = p.sz1 - 3;
		r.door(d, m, court, dz1, 0, 1, false);
		r.door(d, m, court, dz2, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, wall);
		r.windows(d, m, man, 1, 2, 3, null);
		int[] hearth = r.hearth(d, left ? x0 + 1 : x1 - 1, p.sz0, 0, Dirs.BACK);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.Z, p.maxTop - 6, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int ix = left ? x1 : x0;
		Palace.arcade(d, m, man, ix, p.sz0 + 1, ix, p.sz1 - 1, p.y, court, dz1, dz2);
		int[] g = r.room(0);
		r.partition(d, m, 0, false, mid, left ? g[0] + 1 : g[2] - 1);
		int[] u = r.room(1);
		r.partition(d, m, 1, false, mid, left ? u[0] + 1 : u[2] - 1);
		int land = fx + Dirs.dx(up) * 4;
		int doorIn = left ? g[2] : g[0];
		Rooms.Room front = Chambers.room(g[0], g[1], g[2], mid - 1, g[4], new int[]{doorIn, dz1}, new int[]{left ? g[0] + 1 : g[2] - 1, mid - 1});
		Rooms.Room back = Chambers.room(g[0], mid + 1, g[2], g[3], g[4], new int[]{doorIn, dz2}, new int[]{left ? g[0] + 1 : g[2] - 1, mid + 1});
		back.keepArea(Math.min(fx, land) - 1, fz, Math.max(fx, land) + 1, fz);
		front.keep(hearth[0], g[1]);
		Rooms.Room upFront = Chambers.room(u[0], u[1], u[2], mid - 1, u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid - 1});
		Rooms.Room upBack = Chambers.room(u[0], mid + 1, u[2], u[3], u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid + 1}, new int[]{land, fz - 1});
		upBack.keepArea(Math.min(fx, land) - 1, fz, Math.max(fx, land) + 1, fz);
		upBack.keepArea(land, fz - 1, left ? u[0] + 1 : u[2] - 1, fz - 1);
		if (left) {
			Chambers.furnish(d, front, Rooms.Type.KITCHEN, 0);
			Rooms.furnish(d, back, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
			Chambers.furnish(d, upFront, Rooms.Type.BEDROOM, 3);
			Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 2);
		} else {
			Chambers.desk(d, m, front, 0, 0, wall);
			Chambers.furnish(d, front, Rooms.Type.STORAGE, 0);
			Chambers.furnish(d, back, Rooms.Type.STORAGE, 0);
			Chambers.niche(d, m, upFront, 0, 0, wall);
			Chambers.furnish(d, upFront, Rooms.Type.BEDROOM, 2);
			Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 1);
		}
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	// ------------------------------------------------------------------------------------------------ the residence

	/** The residence across the back, behind the great hall: two storeys under a curved roof, the family's rooms. */
	private static void residence(Draft d, Dress m, Manner man, Plan p) {
		if (!p.residence) {
			return;
		}
		int x0 = p.towerLeft ? p.lx + 5 : p.lx;
		int x1 = p.towerLeft ? p.rx : p.rx - 5;
		Range r = new Range(x0, p.bhz0, x1, p.bz, p.y, new int[]{5, 4}, Dirs.FRONT);
		r.blind(Dirs.BACK);
		int fx = p.towerLeft ? x1 - 2 : x0 + 2;
		Direction up = p.towerLeft ? Dirs.LEFT : Dirs.RIGHT;
		r.flight(fx, p.bz - 1, 0, up);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int dcol = p.c;
		r.door(d, m, Dirs.FRONT, dcol, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, Dirs.BACK);
		r.windows(d, m, man, 1, 2, 3, null);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop - 4, 0, List.of());
		int[] g = r.room(0);
		int land = fx + Dirs.dx(up) * 5;
		Rooms.Room k0 = Chambers.room(g[0], g[1], g[2], g[3], g[4], new int[]{dcol, g[1]}, new int[]{fx, p.bz - 2});
		k0.keepArea(Math.min(fx, land), p.bz - 1, Math.max(fx, land), p.bz - 1);
		Chambers.furnish(d, k0, Rooms.Type.BEDROOM, 2);
		int[] u = r.room(1);
		Rooms.Room k1 = Chambers.room(u[0], u[1], u[2], u[3], u[4], new int[]{land, p.bz - 1});
		k1.keepArea(Math.min(fx, land), p.bz - 1, Math.max(fx, land), p.bz - 1);
		Chambers.furnish(d, k1, Rooms.Type.BEDROOM, 3);
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	private static void courts(Draft d, Dress m, Plan p) {
		// trees either side of the way through the first court, and in the main court
		for (int s : new int[]{-1, 1}) {
			int tx = p.c + s * 5;
			int tz = (p.fz + p.thick + p.g2z0) / 2 + 1;
			if (Math.abs(d.site().ground(tx, tz) - p.y) <= 2 && Castle.open(d, tx - 2, tz - 2, tx + 2, tz + 2, p.y, p.y + 6)) {
				Palace.planter(d, m, tx, tz, p.y);
				Garden.tree(d, tx, p.y, tz, 5);
			}
			int mx = p.c + s * (p.hx1 - p.c + 1);
			int mz = (p.g2z1 + p.hz0) / 2;
			if (Math.abs(d.site().ground(mx, mz) - p.y) <= 2 && Castle.open(d, mx - 2, mz - 2, mx + 2, mz + 2, p.y, p.y + 6)) {
				Palace.planter(d, m, mx, mz, p.y);
				Garden.tree(d, mx, p.y, mz, 5);
			}
		}
		d.poi(PoiType.MEETING_POINT, p.c, p.y, p.g2z1 + 2, Dirs.BACK);
		Court.lamps(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y);
	}
}
