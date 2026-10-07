package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
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
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:noble/palace}: the city's palace where the lords of the south and of the karst build courts, not
 * castles (it replaces the keep over the keep's close, as the castle does elsewhere). A walled precinct entered through
 * a gate tower on the street; a forecourt with trees; the great court with its fountain, arcades round it on the ranges
 * either side (the guards' room and the kitchen on one, the steward's counting room on the other, the household and the
 * lord's chamber over them, reached by stairs inside); across the back the audience hall, the throne on its dais; the
 * lord's tower rising at a back corner, the tallest thing in the city; towers on the precinct's other corners.
 *
 * <ul>
 *   <li><b>desert</b>: flat roofs behind pointed merlons, a dome over the audience hall, a slender round tower under a
 *   little dome, horseshoe arcades.</li>
 *   <li><b>mediterranean</b>: a palazzo: low tiled hips, swallowtail merlons, a tall square tower crowned by machicolated
 *   battlements (the city's torre), round-arched loggias.</li>
 *   <li><b>karst</b>: a walled compound: the audience hall raised on a stone terrace under a sweeping curved roof, timber
 *   verandas on posts, a tower of stacked eaves (each storey under its own ring of roof).</li>
 * </ul>
 *
 * <p>Raised in seven campaigns: the precinct wall in two, the gate and towers, the audience hall, the ranges one by
 * one, the courts.
 */
public final class Palace implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("noble/palace"), ArchetypeCategory.NOBLE,
		Set.of(JobType.LORD, JobType.STEWARD, JobType.GUARD), 8, 37, 47, 39, 49, 30, 42, SettlementTier.CITY,
		Set.of(Culture.DESERT, Culture.MEDITERRANEAN, Culture.KARST), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
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
		/** The audience hall across the back. */
		int hx0;
		int hx1;
		int hz0;
		int hz1;
		/** The side ranges: their front and back rows, their depth. */
		int sz0;
		int sz1;
		int sd = 7;
		/** The court between the ranges and before the hall. */
		int cx0;
		int cx1;
		int cz0;
		int cz1;
	}

	@Override
	public void draw(Draft d) {
		Manner man = Manner.of(d);
		Dress m = Dress.of(d);
		Plan p = plan(d);
		d.body(p.lx, p.fz, p.rx, p.bz);
		d.eave(p.walk + 6);
		try (var ph = d.campaign(0)) {
			Groundwork.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y, true);
			Groundwork.level(d, p.c - 3, p.fz - 4, p.c + 3, p.fz + 1, p.y, true);
			Court.pave(d, m, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y);
			Curtain.Opts o = Curtain.Opts.of(man);
			Curtain.run(d, m, man, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.fz, p.lx, p.bz, Dirs.LEFT, p.thick, p.walk, o);
		}
		try (var ph = d.campaign(1)) {
			Curtain.Opts o = Curtain.Opts.of(man);
			Curtain.run(d, m, man, p.rx, p.fz, p.rx, p.bz, Dirs.RIGHT, p.thick, p.walk, o);
			Curtain.run(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.thick, p.walk, o);
		}
		try (var ph = d.campaign(2)) {
			gate(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			hall(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			range(d, m, man, p, true);
		}
		try (var ph = d.campaign(5)) {
			range(d, m, man, p, false);
		}
		// the towers are the gate's campaign, but drawn once the hall and the ranges stand, so their doors open where
		// the court is still open
		try (var ph = d.campaign(2)) {
			towers(d, m, man, p);
		}
		try (var ph = d.campaign(6)) {
			courts(d, m, man, p);
		}
		try (var ph = d.campaign(0)) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, p.maxTop);
		}
		Mason.settle(d, "palace");
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
		p.fz = 5;
		p.bz = p.dp - 4;
		p.y = Court.level(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, -1, 1);
		p.walk = p.y + 6;
		// the hall across the back between the back towers, eleven deep
		p.hx0 = p.lx + 4;
		p.hx1 = p.rx - 4;
		p.hz1 = p.bz;
		p.hz0 = p.bz - 10;
		// the ranges along the sides, from a third of the way back to a passage short of the hall
		int span = p.bz - p.fz;
		p.sz0 = p.fz + Math.max(8, span * 3 / 10);
		p.sz1 = p.hz0 - 3;
		p.cx0 = p.lx + p.sd + 1;
		p.cx1 = p.rx - p.sd - 1;
		p.cz0 = p.sz0;
		p.cz1 = p.hz0 - 1;
		return p;
	}

	// ------------------------------------------------------------------------------------------------ gate and towers

	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walk + 6, p.maxTop - 9);
		Gate.tower(d, m, man, p.c - 4, p.fz - 3, p.c + 4, p.fz + 3, 3, p.y, p.walk, top, p.maxTop);
		// the way up from the street to the gate, stepping where the ground asks
		d.entrance(p.c, p.y, p.fz - 4);
		Groundwork.approach(d, p.c, p.y, p.fz - 4);
		Lighting.doorLamp(d, p.c - 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Lighting.doorLamp(d, p.c + 2, p.y + 2, p.fz - 4, Dirs.BACK);
		Flag.banner(d, m, p.c, p.walk + 1, p.fz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.c + 2, p.y, p.fz - 4, Dirs.FRONT);
	}

	/** The front corner towers, the back tower on the other side, the lord's tower. */
	private static void towers(Draft d, Dress m, Manner man, Plan p) {
		Culture cu = man.culture();
		boolean round = cu == Culture.DESERT;
		Manner.Cap cap = cu == Culture.DESERT ? Manner.Cap.DOME : man.cap();
		int top = Math.min(p.walk + 4, p.maxTop - 8);
		Castle.wallTower(d, m, man, p.lx, p.fz, 7, p.y, p.walk, top, round, cap, Set.of(Dirs.RIGHT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.LEFT));
		Castle.wallTower(d, m, man, p.rx, p.fz, 7, p.y, p.walk, top, round, cap, Set.of(Dirs.LEFT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.RIGHT));
		int ox = p.towerLeft ? p.rx : p.lx;
		Castle.wallTower(d, m, man, ox, p.bz, 7, p.y, p.walk, Math.min(p.walk + 6, p.maxTop - 8), round, cap,
			Set.of(p.towerLeft ? Dirs.LEFT : Dirs.RIGHT, Dirs.FRONT), Set.of(Dirs.BACK, p.towerLeft ? Dirs.RIGHT : Dirs.LEFT));
		// the lord's tower: slender, the tallest thing in the city
		int lx = p.towerLeft ? p.lx : p.rx;
		Manner.Cap lcap = switch (cu) {
			case DESERT -> Manner.Cap.DOME;
			case KARST -> Manner.Cap.PAGODA;
			default -> Manner.Cap.CRENEL;
		};
		int headRows = Math.min(10, Tower.headRows(lcap, 7, man));
		int ltop = Math.min(p.y + 26, p.maxTop - headRows);
		Tower t = Castle.wallTower(d, m, man, lx, p.bz, 7, p.y, p.walk, ltop, round, lcap,
			Set.of(p.towerLeft ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT), Set.of(Dirs.BACK, p.towerLeft ? Dirs.LEFT : Dirs.RIGHT));
		// (in the karst each storey under a skirt of eaves two deep, as the compound's tower)
		Range box = new Range(t.x0, t.z0, t.x1, t.z1, p.y, new int[]{1}, Dirs.FRONT);
		for (int y = p.walk + 5; y < ltop - 3; y += 6) {
			t.window(d, m, Dirs.FRONT, y, 2);
			t.window(d, m, p.towerLeft ? Dirs.LEFT : Dirs.RIGHT, y, 2);
			if (cu == Culture.KARST) {
				box.skirt(d, m, y + 3, 2);
			}
		}
		t.flag(p.towerLeft ? Dirs.RIGHT : Dirs.LEFT);
	}

	// ------------------------------------------------------------------------------------------------ the audience hall

	private static void hall(Draft d, Dress m, Manner man, Plan p) {
		Culture cu = man.culture();
		// the karst raises its hall on a terrace of two steps
		int lift = cu == Culture.KARST ? 2 : 0;
		int y = p.y + lift;
		if (lift > 0) {
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int x = p.hx0 - 1; x <= p.hx1 + 1; x++) {
					for (int z = p.hz0 - 2; z <= p.hz1; z++) {
						if (!Mason.inPlot(d, x, z)) {
							continue;
						}
						for (int yy = p.y - 1; yy < y - 1; yy++) {
							d.set(x, yy, z, m.footing.full());
						}
						if (z < p.hz0 || x < p.hx0 || x > p.hx1) {
							d.set(x, y - 1, z, m.paving.full());
						}
					}
				}
				// steps up the front of the terrace before the door
				int c = (p.hx0 + p.hx1) / 2;
				for (int k = 0; k < lift; k++) {
					// (as wide as the door: the veranda's posts stand either side of the flight)
					for (int x = c - 1; x <= c + 1; x++) {
						d.set(x, p.y + k, p.hz0 - 3 + k, m.paving.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
						for (int yy = p.y - 1; yy < p.y + k; yy++) {
							d.set(x, yy, p.hz0 - 3 + k, m.footing.full());
						}
					}
				}
			}
		}
		int height = cu == Culture.MEDITERRANEAN ? 9 : 8;
		Range r = new Range(p.hx0, p.hz0, p.hx1, p.hz1, y, new int[]{height}, Dirs.FRONT);
		r.blind(Dirs.BACK);
		r.footings(d, m, true);
		r.walls(d, m, man);
		r.floors(d, m);
		int c = (p.hx0 + p.hx1) / 2;
		r.door(d, m, Dirs.FRONT, c - 1, 0, 3, false);
		Manner.Window tall = cu == Culture.KARST ? Manner.Window.LATTICE : Manner.Window.ARCHED;
		Side fs = r.side(Dirs.FRONT);
		for (int i = 3; i < fs.length() - 3; i += 4) {
			int col = fs.column(i);
			if (Math.abs(col - c) > 3) {
				Range.window(d, m, tall, fs, i, y + 2, 4);
			}
		}
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = r.side(sd);
			Range.window(d, m, tall, s, s.middle(), y + 2, 4);
		}
		RoofForm form = cu == Culture.DESERT ? RoofForm.FLAT : RoofForm.HIP;
		r.roof(d, m, man, form, Direction.Axis.X, p.maxTop - (cu == Culture.DESERT ? 8 : 0), 0, List.of());
		if (cu == Culture.DESERT) {
			int rad = Math.min(5, Math.min((p.hx1 - p.hx0) / 2, (p.hz1 - p.hz0) / 2) - 1);
			dome(d, m, c, (p.hz0 + p.hz1) / 2, r.plate() + 2, rad, p.maxTop);
		}
		// arcade before the hall in the south, a veranda on posts in the karst
		arcade(d, m, man, p.hx0 + 1, p.hz0 - 1, p.hx1 - 1, p.hz0 - 1, y, Dirs.FRONT, c);
		// inside: the throne on its dais against the back wall, tables down the hall, lanterns, the lord's chapel niche
		int[] in = r.room(0);
		Rooms.Room room = Chambers.room(in[0], in[1], in[2], in[3], in[4], new int[]{c - 1, in[1]}, new int[]{c, in[1]}, new int[]{c + 1, in[1]});
		room.keepArea(c - 1, in[1], c + 1, in[1] + 1);
		Chambers.hall(d, m, room, Dirs.BACK, true);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** A dome of the dressing over a drum: a hemisphere's shell of radius {@code rad} on a ring a block high. */
	static void dome(Draft d, Dress m, int cx, int cz, int y, int rad, int maxTop) {
		if (rad < 2 || y + rad + 2 > maxTop) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					double r2 = dx * dx + dz * dz;
					if (r2 <= (rad + 0.5) * (rad + 0.5) && r2 > (rad - 0.5) * (rad - 0.5)) {
						d.set(cx + dx, y - 1, cz + dz, m.wall.full());
					}
					for (int dy = 0; dy <= rad; dy++) {
						double r3 = r2 + dy * dy;
						if (r3 <= (rad + 0.5) * (rad + 0.5) && r3 > (rad - 0.7) * (rad - 0.7)) {
							d.set(cx + dx, y + dy, cz + dz, m.dress.full());
						}
					}
				}
			}
			d.set(cx, y + rad + 1, cz, m.dress.has(Form.WALL) ? m.dress.wall() : m.dress.full());
			d.set(cx, y + rad + 2, cz, States.lantern(Blocks.LANTERN, false));
		}
	}

	/**
	 * An arcade along the line (x0, z0)-(x1, z1) at standing level y, facing {@code out} from the wall behind it:
	 * piers every other cell, a lintel course with the arches' haunches (upside-down stairs) and a roof of slabs over it;
	 * in the karst timber posts under a lean-to of the roof covering. Leaves the cell {@code door} (on the run) open.
	 */
	static void arcade(Draft d, Dress m, Manner man, int x0, int z0, int x1, int z1, int y, Direction out, int... doors) {
		boolean karst = man.culture() == Culture.KARST;
		Direction along = x0 == x1 ? Dirs.BACK : Dirs.RIGHT;
		int n = Math.abs(x1 - x0) + Math.abs(z1 - z0) + 1;
		int h = 4;
		List<int[]> placed = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			int x = Math.min(x0, x1) + (along == Dirs.RIGHT ? i : 0);
			int z = Math.min(z0, z1) + (along == Dirs.BACK ? i : 0);
			int ox = x + Dirs.dx(out);
			int oz = z + Dirs.dz(out);
			if (!Mason.inPlot(d, ox, oz) || !d.isEmpty(ox, y, oz) || !d.isEmpty(ox, y + 1, oz)) {
				continue;
			}
			int col = along == Dirs.RIGHT ? x : z;
			boolean nearDoor = false;
			for (int dc : doors) {
				nearDoor |= Math.abs(col - dc) <= 1;
			}
			boolean pier = i % 2 == 0 && !nearDoor;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int yy = y; yy < y + h - 1; yy++) {
					if (pier) {
						d.set(ox, yy, oz, karst ? Mason.post(m.frame) : m.dress.full());
					}
				}
				if (!pier && !karst) {
					d.set(ox, y + h - 2, oz, m.dress.stairsOrSlab(along, Half.TOP));
				}
				d.set(ox, y + h - 1, oz, karst ? Mason.beam(m.frame, along.getAxis()) : m.dress.full());
			}
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(ox, y + h, oz, karst ? m.roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM)
					: m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
				d.set(x, y + h, z, karst ? m.roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM) : m.wall.full());
			}
			if (i % 4 == 1 && !pier) {
				Lighting.chandelier(d, ox, oz, y + h - 1, y + h - 2);
			}
			placed.add(new int[]{x, z, ox, oz});
		}
		// a balustrade along the roof's outer edge, and across its ends where they stand free: the arcade's roof is a
		// terrace before the upper rooms' windows, not a ledge to fall from
		Paint rail = karst ? m.frame.fence() : m.dress.has(Form.WALL) ? m.dress.wall() : m.frame.fence();
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k = 0; k < placed.size(); k++) {
				int[] c = placed.get(k);
				if (d.isEmpty(c[2], y + h + 1, c[3])) {
					d.set(c[2], y + h + 1, c[3], rail);
				}
				if ((k == 0 || k == placed.size() - 1) && d.isEmpty(c[0], y + h + 1, c[1]) && d.isEmpty(c[0], y + h + 2, c[1])) {
					d.set(c[0], y + h + 1, c[1], rail);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the ranges

	/**
	 * A side range of two storeys against a side wall: the left has the guards' room and the kitchen, the household's
	 * beds over them; the right the steward's counting room and the stores, the lord's chamber over them. A stair inside
	 * against the wall, an arcade on the court.
	 */
	private static void range(Draft d, Dress m, Manner man, Plan p, boolean left) {
		int x0 = left ? p.lx : p.rx - p.sd;
		int x1 = left ? p.lx + p.sd : p.rx;
		if (p.sz1 - p.sz0 < 8) {
			return;
		}
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction wall = court.getOpposite();
		Range r = new Range(x0, p.sz0, x1, p.sz1, p.y, new int[]{5, 5}, court);
		r.blind(wall);
		// the stair along the back end wall's inside, climbing toward the wall side from a cell in from the court wall
		// (the way onto its foot), stepped off sideways at its head
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
		// hold the roof under the curtain's walk where it meets it; flat in the desert
		RoofForm form = man.flat() ? RoofForm.FLAT : RoofForm.HIP;
		r.roof(d, m, man, form, Direction.Axis.Z, p.maxTop - 6, 0, List.of(new int[]{hearth[0], hearth[1]}));
		int ix = left ? x1 + 0 : x0;
		arcade(d, m, man, ix, p.sz0 + 1, ix, p.sz1 - 1, p.y, court, dz1, dz2);
		// the rooms: a partition at the middle of each storey
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

	// ------------------------------------------------------------------------------------------------ the courts

	private static void courts(Draft d, Dress m, Manner man, Plan p) {
		int cx = (p.cx0 + p.cx1) / 2;
		int cz = (p.cz0 + p.cz1) / 2;
		fountain(d, m, cx, cz, p.y);
		d.poi(PoiType.MEETING_POINT, cx, p.y, cz - 3, Dirs.BACK);
		// trees in the forecourt, either side of the way from the gate
		for (int s : new int[]{-1, 1}) {
			for (int k = 0; k < 2; k++) {
				int tx = p.c + s * (5 + k * 6);
				int tz = p.fz + p.thick + 3 + (k == 0 ? 0 : 1);
				// (on made ground only where the fill is shallow: a tree wants earth under it)
				if (tz + 2 < p.sz0 && Math.abs(d.site().ground(tx, tz) - p.y) <= 2 && Castle.open(d, tx - 2, tz - 2, tx + 2, tz + 2, p.y, p.y + 6)) {
					planter(d, m, tx, tz, p.y);
					Garden.tree(d, tx, p.y, tz, 5);
				}
			}
		}
		Court.lamps(d, p.lx + p.thick, p.fz + p.thick, p.rx - p.thick, p.bz - p.thick, p.y);
		// a parados on the court edge of the walks, where nothing stands against the curtain
		Curtain.parados(d, m, man, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.thick, p.walk);
		Curtain.parados(d, m, man, p.lx, p.fz, p.lx, p.bz, Dirs.LEFT, p.thick, p.walk);
		Curtain.parados(d, m, man, p.rx, p.fz, p.rx, p.bz, Dirs.RIGHT, p.thick, p.walk);
		Curtain.parados(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.thick, p.walk);
	}

	/** A fountain: a basin five across with a rim of the dressing, water in it, a column with a bowl in the middle. */
	static void fountain(Draft d, Dress m, int x, int z, int y) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					if (rim) {
						d.set(x + dx, y, z + dz, m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
					} else {
						d.set(x + dx, y - 1, z + dz, Blocks.WATER.defaultBlockState());
						d.set(x + dx, y - 2, z + dz, m.footing.full());
					}
				}
			}
			d.set(x, y - 1, z, m.dress.full());
			d.set(x, y, z, m.dress.has(Form.WALL) ? m.dress.wall() : m.dress.full());
			d.set(x, y + 1, z, m.dress.full());
			d.set(x, y + 2, z, States.lantern(Blocks.LANTERN, false));
		}
	}

	/** A raised square planter round a tree. */
	static void planter(Draft d, Dress m, int x, int z, int y) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						d.set(x + dx, y, z + dz, m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
					}
				}
			}
			d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
		}
	}
}
