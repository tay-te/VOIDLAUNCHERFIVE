package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
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
 * The castle of the south, a rocca (Sirmione, Fénis, the Este castles; the Spanish alcázares and the Provençal castra
 * build alike): one square court behind high walls on a battered scarp, a square tower on every corner barely higher
 * than the walls, all of them crowned with machicolated parapets of the culture's merlons (swallowtails in Italy); the
 * mastio, the tall square keep, rising at one back corner, the city's landmark; the gate tower on the front's axis. In
 * the court the palazzo: a range across the back and one down a side, two storeys under low tile hips behind loggias
 * on the court (the great hall upstairs at the back, the lord's rooms in the side), the chapel in the front corner, a
 * well in the middle. Compact, cubic, flat-topped: the opposite of a castle of spires.
 *
 * <p>Raised in seven campaigns: the walls in two; the corner towers and the gate; the mastio; the back range; the side
 * range and the chapel; the court.
 */
final class Rocca {
	private Rocca() {
	}

	/** Where everything stands (local coordinates; walls by their field faces). */
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
		/** The back range from bz0; the side range on the side away from the mastio, sx0..sx1. */
		int bz0;
		int sx0;
		int sx1;
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
			scarp(d, m, p, true);
		}
		try (var ph = d.campaign(1)) {
			Curtain.run(d, m, man, p.rx, p.fz, p.rx, p.bz, Dirs.RIGHT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.thick, p.walk, o);
			scarp(d, m, p, false);
		}
		try (var ph = d.campaign(2)) {
			corners(d, m, man, p);
			gate(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			mastio(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			backRange(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			sideRange(d, m, man, p);
			chapel(d, m, man, p);
		}
		try (var ph = d.campaign(6)) {
			int cx = (p.lx + p.rx) / 2 + (p.towerLeft ? 2 : -2);
			int cz = (p.fz + p.thick + p.bz0) / 2;
			if (Castle.open(d, cx - 2, cz - 2, cx + 2, cz + 2, p.y, p.y + 3)) {
				Court.well(d, m, cx, cz, p.y);
			}
			Court.lamps(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz0 - 1, p.y);
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
		p.lx = 4;
		p.rx = p.w - 5;
		p.fz = 6;
		p.bz = p.dp - 5;
		p.y = Court.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, -1, 1);
		p.walk = p.y + 8;
		p.bz0 = p.bz - 7;
		p.sx0 = p.towerLeft ? p.rx - 7 : p.lx;
		p.sx1 = p.towerLeft ? p.rx : p.lx + 7;
		return p;
	}

	/**
	 * The scarp: the walls' feet battered out two blocks all round, a course of the footing stone and a sloping course
	 * over it, so the rocca stands on a plinth that sheds what is dropped from the machicolations.
	 */
	private static void scarp(Draft d, Dress m, Plan p, boolean frontLeft) {
		Direction[] sides = frontLeft ? new Direction[]{Dirs.FRONT, Dirs.LEFT} : new Direction[]{Dirs.RIGHT, Dirs.BACK};
		for (Direction out : sides) {
			boolean alongX = out == Dirs.FRONT || out == Dirs.BACK;
			int a0 = alongX ? p.lx : p.fz;
			int a1 = alongX ? p.rx : p.bz;
			int face = out == Dirs.FRONT ? p.fz : out == Dirs.BACK ? p.bz : out == Dirs.LEFT ? p.lx : p.rx;
			for (int a = a0; a <= a1; a++) {
				if (out == Dirs.FRONT && Math.abs(a - p.c) <= 5) {
					continue;
				}
				for (int k = 1; k <= 2; k++) {
					int x = alongX ? a : face + Dirs.dx(out) * k;
					int z = alongX ? face + Dirs.dz(out) * k : a;
					if (!Mason.inPlot(d, x, z) || d.site().isWater(x, z)) {
						continue;
					}
					int g = d.site().ground(x, z);
					int topY = k == 1 ? Math.max(g + 1, p.y) : g;
					try (var s = d.stage(StageKind.FOUNDATION)) {
						for (int yy = g - 1; yy < topY; yy++) {
							if (d.isAbsent(x, yy, z)) {
								d.set(x, yy, z, m.footing.full());
							}
						}
						if (d.isAbsent(x, topY, z)) {
							d.set(x, topY, z, m.footing.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ towers and gate

	/** A square tower on each corner but the mastio's, a little over the walls, open behind machicolated battlements. */
	private static void corners(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 3, p.maxTop - 4);
		int[][] cs = {{p.lx, p.fz, -1, -1}, {p.rx, p.fz, 1, -1}, {p.lx, p.bz, -1, 1}, {p.rx, p.bz, 1, 1}};
		for (int[] c : cs) {
			boolean mastio = c[3] > 0 && (c[2] < 0) == p.towerLeft;
			if (mastio) {
				continue;
			}
			Direction sx = c[2] < 0 ? Dirs.LEFT : Dirs.RIGHT;
			Direction sz = c[3] < 0 ? Dirs.FRONT : Dirs.BACK;
			Castle.wallTower(d, m, man, c[0], c[1], 7, p.y, p.walk, top, false, Manner.Cap.CRENEL, Set.of(sx.getOpposite(), sz.getOpposite()),
				Set.of(sx, sz));
		}
	}

	/** The gate tower on the front's axis, a step higher than the corners. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 5, p.maxTop - 4);
		Gate.tower(d, m, man, p.c - 4, p.fz - 3, p.c + 4, p.fz + 3, 3, p.y, p.walk, top, p.maxTop);
		d.entrance(p.c, p.y, p.fz - 4);
		Groundwork.approach(d, p.c, p.y, p.fz - 4);
		Lighting.doorLamp(d, p.c - 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Lighting.doorLamp(d, p.c + 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Flag.banner(d, m, p.c, p.walk + 1, p.fz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.fz - 4, Dirs.FRONT);
		Curtain.posts(d, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, 7);
	}

	/**
	 * The mastio at the back corner: a tall square keep, its foot the guards' room, the steward's room and the stores
	 * up its storeys, machicolated battlements at its head with the lord's flag.
	 */
	private static void mastio(Draft d, Dress m, Manner man, Plan p) {
		int x = p.towerLeft ? p.lx : p.rx;
		int top = Math.max(p.walk + 8, Math.min(p.y + 26, p.maxTop - 4));
		Tower t = Tower.square(x - 4, p.bz - 4, x + 4, p.bz + 4, p.y, top, Manner.Cap.CRENEL);
		int[] lv = new int[]{p.y + 4, p.walk, p.walk + 5, p.walk + 10, p.walk + 15};
		int n = 0;
		while (n < lv.length && lv[n] < top - 3) {
			n++;
		}
		lv = java.util.Arrays.copyOf(lv, n);
		t.footings(d, m);
		t.shell(d, m, top - 1, lv);
		t.stair(d, m, false, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT);
		t.floors(d, m, top, lv);
		Direction court = p.towerLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (t.doorToward(d, m, Dirs.FRONT, p.y, true) == null) {
			t.doorToward(d, m, court, p.y, true);
		}
		t.doorToward(d, m, court, p.walk, false);
		t.doorToward(d, m, Dirs.FRONT, p.walk, false);
		Direction field = court.getOpposite();
		t.loops(d, Set.of(Dirs.BACK, field), p.y + 2, top);
		for (int k = 1; k < lv.length; k++) {
			t.window(d, m, Dirs.FRONT, lv[k] + 1, 2);
			t.window(d, m, court, lv[k] + 1, 2);
		}
		t.head(d, m, man, p.maxTop);
		for (int k = 0; k <= lv.length; k++) {
			int y = k == 0 ? p.y : lv[k - 1];
			Rooms.Room room = t.room(y);
			switch (k) {
				case 0 -> Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
				case 1 -> Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
				case 2 -> {
					Chambers.desk(d, m, room, room.x0, room.z0, Dirs.FRONT);
					Chambers.furnish(d, room, Rooms.Type.BEDROOM, 1);
				}
				default -> Chambers.furnish(d, room, Rooms.Type.BEDROOM, 2);
			}
		}
		if (lv.length < 2) {
			Rooms.Room room = t.room(p.y);
			Chambers.desk(d, m, room, room.x0, room.z0, Dirs.FRONT);
		}
		t.light(d, top - 1);
		// the flag on the open head
		int[] st = t.stairTop();
		int fx = t.cx + (p.towerLeft ? -1 : 1);
		int fz = t.cz + 1;
		if (st != null && Math.abs(st[0] - fx) <= 1 && Math.abs(st[1] - fz) <= 1) {
			fx = t.cx + (p.towerLeft ? 2 : -2);
			fz = t.cz - 2;
		}
		Flag.pole(d, m, fx, top, fz, 5, court, 3, p.maxTop);
		t.watch(d, Dirs.FRONT);
	}

	// ------------------------------------------------------------------------------------------------ the palazzo

	/**
	 * The range across the back: the kitchen and the stores below, the great hall over them with the throne, a flight
	 * inside at the end; low hips; a loggia on the court.
	 */
	private static void backRange(Draft d, Dress m, Manner man, Plan p) {
		int x0 = p.towerLeft ? p.lx + 5 : p.lx + 4;
		int x1 = p.towerLeft ? p.rx - 4 : p.rx - 5;
		Range r = new Range(x0, p.bz0, x1, p.bz, p.y, new int[]{5, 6}, Dirs.FRONT);
		r.blind(Dirs.BACK);
		int fx = p.towerLeft ? x1 - 1 : x0 + 1;
		Direction up = p.towerLeft ? Dirs.LEFT : Dirs.RIGHT;
		r.flight(fx, p.bz - 1, 0, up);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int xm = (x0 + x1) / 2;
		int d1 = (x0 + xm) / 2;
		int d2 = (xm + x1) / 2;
		r.door(d, m, Dirs.FRONT, d1, 0, 1, false);
		r.door(d, m, Dirs.FRONT, d2, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, Dirs.BACK);
		r.windows(d, m, man, 1, 3, 3, null);
		int[] hearth = r.hearth(d, p.towerLeft ? x0 + 2 : x1 - 2, p.bz, 1, Dirs.FRONT);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop, 0, List.of(new int[]{hearth[0], hearth[1]}));
		Palace.arcade(d, m, man, x0 + 1, p.bz0, x1 - 1, p.bz0, p.y, Dirs.FRONT, d1, d2);
		int[] g = r.room(0);
		r.partition(d, m, 0, true, xm, g[1] + 1);
		int land = fx + Dirs.dx(up) * 5;
		Rooms.Room kitchen = Chambers.room(p.towerLeft ? g[0] : xm + 1, g[1], p.towerLeft ? xm - 1 : g[2], g[3], g[4], new int[]{d1, g[1]},
			new int[]{p.towerLeft ? xm - 1 : xm + 1, g[1] + 1});
		Rooms.Room stores = Chambers.room(p.towerLeft ? xm + 1 : g[0], g[1], p.towerLeft ? g[2] : xm - 1, g[3], g[4], new int[]{d2, g[1]},
			new int[]{p.towerLeft ? xm + 1 : xm - 1, g[1] + 1}, new int[]{fx, p.bz - 2});
		stores.keepArea(Math.min(fx, land), p.bz - 1, Math.max(fx, land), p.bz - 1);
		kitchen.keepArea(Math.min(fx, land), p.bz - 1, Math.max(fx, land), p.bz - 1);
		Chambers.furnish(d, kitchen, Rooms.Type.KITCHEN, 0);
		Chambers.furnish(d, stores, Rooms.Type.STORAGE, 0);
		int[] h = r.room(1);
		Rooms.Room hall = Chambers.room(h[0], h[1], h[2], h[3], h[4], new int[]{land, p.bz - 1});
		hall.keepArea(Math.min(fx, land), p.bz - 1, Math.max(fx, land), p.bz - 1);
		Chambers.hall(d, m, hall, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, true);
		Range.hang(d, h, 4);
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	/** The range down the side away from the mastio: the guards and the household below, the lord's rooms above. */
	private static void sideRange(Draft d, Dress m, Manner man, Plan p) {
		int z0 = p.fz + 4;
		int z1 = p.bz0 - 2;
		if (z1 - z0 < 9) {
			return;
		}
		boolean left = !p.towerLeft;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction wall = court.getOpposite();
		Range r = new Range(p.sx0, z0, p.sx1, z1, p.y, new int[]{5, 5}, court);
		r.blind(wall);
		int fx = left ? p.sx1 - 2 : p.sx0 + 2;
		Direction up = left ? Dirs.LEFT : Dirs.RIGHT;
		r.flight(fx, z1 - 1, 0, up);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.flights(d, m);
		int mid = (z0 + z1) / 2;
		int dz1 = z0 + 3;
		int dz2 = z1 - 3;
		r.door(d, m, court, dz1, 0, 1, false);
		r.door(d, m, court, dz2, 0, 1, false);
		r.windows(d, m, man, 0, 2, 3, wall);
		r.windows(d, m, man, 1, 3, 3, null);
		r.roof(d, m, man, RoofForm.HIP, Direction.Axis.Z, p.maxTop - 4, 0, List.of());
		int ix = left ? p.sx1 : p.sx0;
		Palace.arcade(d, m, man, ix, z0 + 1, ix, z1 - 1, p.y, court, dz1, dz2);
		int[] g = r.room(0);
		r.partition(d, m, 0, false, mid, left ? g[0] + 1 : g[2] - 1);
		int[] u = r.room(1);
		r.partition(d, m, 1, false, mid, left ? u[0] + 1 : u[2] - 1);
		int land = fx + Dirs.dx(up) * 4;
		int doorIn = left ? g[2] : g[0];
		Rooms.Room front = Chambers.room(g[0], g[1], g[2], mid - 1, g[4], new int[]{doorIn, dz1}, new int[]{left ? g[0] + 1 : g[2] - 1, mid - 1});
		Rooms.Room back = Chambers.room(g[0], mid + 1, g[2], g[3], g[4], new int[]{doorIn, dz2}, new int[]{left ? g[0] + 1 : g[2] - 1, mid + 1});
		back.keepArea(Math.min(fx, land) - 1, z1 - 1, Math.max(fx, land) + 1, z1 - 1);
		Rooms.Room upFront = Chambers.room(u[0], u[1], u[2], mid - 1, u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid - 1});
		Rooms.Room upBack = Chambers.room(u[0], mid + 1, u[2], u[3], u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid + 1}, new int[]{land, z1 - 2});
		upBack.keepArea(Math.min(fx, land) - 1, z1 - 1, Math.max(fx, land) + 1, z1 - 1);
		upBack.keepArea(land, z1 - 2, left ? u[0] + 1 : u[2] - 1, z1 - 2);
		Chambers.furnish(d, front, Rooms.Type.BEDROOM, 3);
		Chambers.furnish(d, back, Rooms.Type.STORAGE, 0);
		Chambers.niche(d, m, upFront, 0, 0, wall);
		Chambers.furnish(d, upFront, Rooms.Type.BEDROOM, 2);
		Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 2);
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	/** The chapel in the front corner on the mastio's side: a gabled cell with a bell gable, the altar at its back. */
	private static void chapel(Draft d, Dress m, Manner man, Plan p) {
		int x0 = p.towerLeft ? p.lx + p.thick : p.rx - p.thick - 6;
		int x1 = x0 + 6;
		int z0 = p.fz + p.thick + 3;
		int z1 = Math.min(z0 + 9, p.bz0 - 3);
		Direction court = p.towerLeft ? Dirs.RIGHT : Dirs.LEFT;
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{6}, court);
		r.blind(court.getOpposite());
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		r.door(d, m, court, z0 + 1, 0, 1, false);
		var cs = r.side(court);
		for (int i = 4; i < cs.length() - 1; i += 3) {
			Range.window(d, m, man.window(), cs, i, p.y + 2, 3);
		}
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop - 6, 0, List.of());
		int cx = (x0 + x1) / 2;
		if (r.roof != null && r.roof.covers(cx, z0 + 1)) {
			Castle.belfry(d, m, man, cx, z0 + 1, r.roof.surfaceY(cx, z0 + 1) + 1, p.maxTop);
		}
		int[] in = r.room(0);
		Sacred.altar(d, cx, p.y, in[3], Dirs.FRONT, in[2] - in[0] >= 4, cx + 2);
		Sacred.pews(d, in[0], in[2], in[3] - 3, in[1] + 2, p.y, cx, Dirs.BACK, false);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}
}
