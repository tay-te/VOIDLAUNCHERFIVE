package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
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
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.kit.noble.Turret;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * The dry lands' castle, a qasr (the desert castles of the Umayyads, the ribats of the coast, the kasbahs' walled
 * compounds): a square of plain high walls behind pointed merlons, drum towers on the corners and half-round towers
 * buttressing every stretch at even spacing, the gate between two of them; the burj, a tall square tower, rising at one
 * back corner. Inside, rooms of two storeys against the walls all round under flat roofs at the walk's height, so the
 * whole reads as one block; arcades on the court; the audience hall in the middle of the back under a dome, the throne in
 * it; a fountain and trees in planters. Low, square, regular, one tower and one dome.
 *
 * <p>Raised in seven campaigns: the walls in two; the gate and the towers of the front; the towers of the back and
 * the burj; the audience hall; the side ranges; the court.
 */
final class Qasr {
	private Qasr() {
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
		/** The ranges against the walls are sd deep; the back range from bz0. */
		int sd = 6;
		int bz0;
		/** The audience hall in the middle of the back. */
		int hx0;
		int hx1;
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d);
		d.body(p.lx, p.fz, p.rx, p.bz);
		d.eave(p.walk + 4);
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
		try (var ph = d.campaign(4)) {
			hall(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			range(d, m, man, p, true);
			range(d, m, man, p, false);
		}
		// the gate and the towers of the front half, then those of the back: drums footed down a hillside are more than a
		// town stores at once
		try (var ph = d.campaign(2)) {
			gate(d, m, man, p);
			towers(d, m, man, p, true);
		}
		try (var ph = d.campaign(3)) {
			towers(d, m, man, p, false);
			burj(d, m, man, p);
		}
		try (var ph = d.campaign(6)) {
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
		p.lx = 3;
		p.rx = p.w - 4;
		p.fz = 6;
		p.bz = p.dp - 4;
		p.y = Court.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, -1, 1);
		p.walk = p.y + 8;
		p.bz0 = p.bz - 8;
		p.hx0 = p.c - 6;
		p.hx1 = p.c + 6;
		return p;
	}

	// ------------------------------------------------------------------------------------------------ towers and gate

	/** The gate between two drum towers standing out of the front wall. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 2, p.maxTop - 4);
		Gate.gatehouse(d, m, man, p.c - 4, p.fz - 1, p.c + 4, p.fz + 4, 3, p.y, p.walk, top, p.maxTop, true, 7, null);
		d.entrance(p.c, p.y, p.fz - 2);
		Groundwork.approach(d, p.c, p.y, p.fz - 2);
		Flag.banner(d, m, p.c, p.walk + 1, p.fz - 2, Dirs.FRONT, 0);
		Curtain.posts(d, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, 7);
	}

	/**
	 * Drum towers on the corners but the burj's, half-round towers buttressing the walls between at even spacing: those
	 * of the front half or of the back.
	 */
	private static void towers(Draft d, Dress m, Manner man, Plan p, boolean front) {
		int top = Math.min(p.walk + 2, p.maxTop - 4);
		int zm = (p.fz + p.bz) / 2;
		int[][] cs = {{p.lx, p.fz, -1, -1}, {p.rx, p.fz, 1, -1}, {p.lx, p.bz, -1, 1}, {p.rx, p.bz, 1, 1}};
		for (int[] c : cs) {
			if (c[3] > 0 && (c[2] < 0) == p.towerLeft || (c[3] < 0) != front) {
				continue;
			}
			Direction sx = c[2] < 0 ? Dirs.LEFT : Dirs.RIGHT;
			Direction sz = c[3] < 0 ? Dirs.FRONT : Dirs.BACK;
			Castle.wallTower(d, m, man, c[0], c[1], 7, p.y, p.walk, top, true, Manner.Cap.CRENEL, Set.of(sx.getOpposite(), sz.getOpposite()),
				Set.of(sx, sz));
		}
		// the buttress towers: solid drums to the walk with a guard's lookout on them
		for (int z = p.fz + 9; z <= p.bz - 7; z += 9) {
			if (z <= zm != front) {
				continue;
			}
			Turret.wall(d, m, man, p.lx, z, 5, p.walk, p.maxTop, Set.of(Dirs.FRONT, Dirs.BACK), Manner.Cap.CRENEL);
			Turret.wall(d, m, man, p.rx, z, 5, p.walk, p.maxTop, Set.of(Dirs.FRONT, Dirs.BACK), Manner.Cap.CRENEL);
		}
		for (int x : new int[]{p.c - 9, p.c + 9}) {
			if (!front && x - 3 > p.lx + 4 && x + 3 < p.rx - 4) {
				Turret.wall(d, m, man, x, p.bz, 5, p.walk, p.maxTop, Set.of(Dirs.LEFT, Dirs.RIGHT), Manner.Cap.CRENEL);
			}
		}
	}

	/**
	 * The burj at a back corner: a tall square tower, slender, slot windows up it, the guards' room at its foot, stores
	 * above (its spiral leaves no wall for a desk), open battlements at the head with the lord's flag.
	 */
	private static void burj(Draft d, Dress m, Manner man, Plan p) {
		int x = p.towerLeft ? p.lx : p.rx;
		int top = Math.max(p.walk + 8, Math.min(p.y + 24, p.maxTop - 4));
		Tower t = Tower.square(x - 3, p.bz - 3, x + 3, p.bz + 3, p.y, top, Manner.Cap.CRENEL);
		int[] lv = {p.y + 4, p.walk, p.walk + 5, p.walk + 10};
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
		t.loops(d, Set.of(Dirs.BACK, court.getOpposite(), Dirs.FRONT, court), p.walk + 2, top);
		t.head(d, m, man, p.maxTop);
		for (int k = 0; k <= lv.length; k++) {
			Rooms.Room room = t.room(k == 0 ? p.y : lv[k - 1]);
			if (k == 0) {
				Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
			} else {
				Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			}
		}
		t.light(d, top - 1);
		int[] st = t.stairTop();
		int fx = t.cx + (p.towerLeft ? -1 : 1);
		int fz = t.cz + 1;
		if (st != null && Math.abs(st[0] - fx) <= 1 && Math.abs(st[1] - fz) <= 1) {
			fx = t.cx - (p.towerLeft ? -1 : 1);
			fz = t.cz - 1;
		}
		Flag.pole(d, m, fx, top, fz, 5, court, 3, p.maxTop);
		t.watch(d, Dirs.FRONT);
	}

	// ------------------------------------------------------------------------------------------------ the halls

	/** The audience hall in the middle of the back: one tall room under a flat roof with a dome over it, the throne. */
	private static void hall(Draft d, Dress m, Manner man, Plan p) {
		Range r = new Range(p.hx0, p.bz0, p.hx1, p.bz, p.y, new int[]{8}, Dirs.FRONT);
		r.blind(Dirs.BACK);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		r.door(d, m, Dirs.FRONT, p.c - 1, 0, 3, false);
		Side fs = r.side(Dirs.FRONT);
		for (int i = 2; i < fs.length() - 2; i += 3) {
			if (Math.abs(fs.column(i) - p.c) > 3) {
				Range.window(d, m, Manner.Window.ARCHED, fs, i, p.y + 2, 4);
			}
		}
		r.roof(d, m, man, RoofForm.FLAT, Direction.Axis.X, p.maxTop, 0, List.of());
		int rad = Math.min(5, Math.min((p.hx1 - p.hx0) / 2, (p.bz - p.bz0) / 2) - 1);
		Palace.dome(d, m, p.c, (p.bz0 + p.bz) / 2, r.plate() + 2, rad, p.maxTop);
		Palace.arcade(d, m, man, p.hx0 + 1, p.bz0, p.hx1 - 1, p.bz0, p.y, Dirs.FRONT, p.c);
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{p.c - 1, in[1]}, new int[]{p.c, in[1]}, new int[]{p.c + 1, in[1]});
		room.keepArea(p.c - 1, in[1], p.c + 1, in[1] + 1);
		Chambers.hall(d, m, room, Dirs.BACK, true);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/**
	 * A range of two storeys against a side wall from the front to the back, flat-roofed at the walk, an arcade on the
	 * court: on the left the kitchen and the oratory below, the household's beds above; on the right the stores (the
	 * steward's desk and strongbox in them) and the guards below, the lord's chamber above. A flight inside at the back
	 * end.
	 */
	private static void range(Draft d, Dress m, Manner man, Plan p, boolean left) {
		// inside the curtain, which the buttress towers stand on
		int x0 = left ? p.lx + p.thick : p.rx - p.thick - p.sd;
		int x1 = x0 + p.sd;
		int z0 = p.fz + 5;
		int z1 = p.bz - 4;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction wall = court.getOpposite();
		Range r = new Range(x0, z0, x1, z1, p.y, new int[]{4, 4}, court);
		r.blind(wall);
		int fx = left ? x1 - 2 : x0 + 2;
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
		r.windows(d, m, man, 1, 2, 3, null);
		int[] hearth = r.hearth(d, left ? x0 + 1 : x1 - 1, z0, 0, Dirs.BACK);
		r.roof(d, m, man, RoofForm.FLAT, Direction.Axis.Z, p.maxTop, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int ix = left ? x1 : x0;
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
		front.keep(hearth[0], g[1]);
		Rooms.Room upFront = Chambers.room(u[0], u[1], u[2], mid - 1, u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid - 1});
		Rooms.Room upBack = Chambers.room(u[0], mid + 1, u[2], u[3], u[4], new int[]{left ? u[0] + 1 : u[2] - 1, mid + 1}, new int[]{land, z1 - 2});
		upBack.keepArea(Math.min(fx, land) - 1, z1 - 1, Math.max(fx, land) + 1, z1 - 1);
		upBack.keepArea(land, z1 - 2, left ? u[0] + 1 : u[2] - 1, z1 - 2);
		if (left) {
			Chambers.furnish(d, front, Rooms.Type.KITCHEN, 0);
			// the oratory: the prayer niche in the wall toward the field, the imam's lectern beside it
			int ax = g[0] + (wall == Dirs.LEFT ? 0 : g[2] - g[0]);
			int az = (mid + 1 + g[3]) / 2 - 1;
			Sacred.altar(d, ax, p.y, az, court, false, Integer.MIN_VALUE);
			dev.voidmc.hearth.build.kit.Furniture.workstation(d, JobType.PRIEST, ax, p.y, az - 2, court);
			Chambers.furnish(d, upFront, Rooms.Type.BEDROOM, 3);
			Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 2);
		} else {
			// the stores are the steward's: his desk with the rolls and the strongbox among them
			Chambers.desk(d, m, front, front.x0, front.z0, wall);
			Chambers.furnish(d, front, Rooms.Type.STORAGE, 0);
			Rooms.furnish(d, back, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
			Chambers.niche(d, m, upFront, 0, 0, wall);
			Chambers.furnish(d, upFront, Rooms.Type.BEDROOM, 2);
			Chambers.furnish(d, upBack, Rooms.Type.BEDROOM, 2);
		}
		for (int k = 0; k < 2; k++) {
			int[] rm = r.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	private static void courts(Draft d, Dress m, Plan p) {
		int cz = (p.fz + p.thick + 5 + p.bz0) / 2;
		Palace.fountain(d, m, p.c, cz, p.y);
		d.poi(PoiType.MEETING_POINT, p.c, p.y, cz - 3, Dirs.BACK);
		for (int s : new int[]{-1, 1}) {
			int tx = p.c + s * 6;
			for (int tz : new int[]{cz - 4, cz + 4}) {
				if (Math.abs(d.site().ground(tx, tz) - p.y) <= 2 && Castle.open(d, tx - 2, tz - 2, tx + 2, tz + 2, p.y, p.y + 6)) {
					Palace.planter(d, m, tx, tz, p.y);
					Garden.tree(d, tx, p.y, tz, 5);
				}
			}
		}
		Court.lamps(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz0 - 1, p.y);
	}
}
