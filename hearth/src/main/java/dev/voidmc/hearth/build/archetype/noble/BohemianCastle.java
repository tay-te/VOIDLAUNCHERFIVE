package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
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
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.kit.noble.Turret;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * The castle of Bohemia as Kingdom Come II builds it (Talmberg, Rattay, Trosky, Pirkštejn): few towers, and broad ones.
 * From the road a square gate tower under a tile hip in the front curtain of the outer bailey, whose front corners are cut
 * back along the brow of the hill (more where the ground falls away under them), a round shell tower under a low cone on
 * the far cut; in the bailey the stables and the smithy in lean-tos, a well; a ramp up to the inner gate tower in the
 * cross wall; the inner castle on its raised terrace, the bergfried astride the cross wall on one side (a tile helm or
 * a hoarded head), the palace across the whole back, three storeys of white render under one great red hip with dormers,
 * its wing of two storeys down the far side with the chapel upstairs behind a front gable carrying the bell turret, a
 * round stair turret in the angle; covered walks on the inner curtain. The palace and wing stand on the curtain's line
 * and are the curtain there.
 *
 * <p>Raised in eight campaigns, the curtains each in two on a hillside: the terraces and the bergfried; the inner
 * curtain; the palace; the wing; the inner gate and the stair turret; the outer curtain; the gate tower and the shell
 * tower; the wards.
 */
final class BohemianCastle {
	private BohemianCastle() {
	}

	/** Where everything stands (local coordinates; walls by their field faces). */
	static final class Plan {
		int w;
		int dp;
		int c;
		int maxTop;
		boolean keepLeft;
		int lx;
		int rx;
		int fz;
		int cz;
		int bz;
		int ot = 2;
		int it = 3;
		int lo;
		int li;
		int walkO;
		int walkI;
		/** How far the outer bailey's front corners are cut back, on the keep's side and the far side. */
		int chK;
		int chF;
		Outline outer;
		Outline inner;
		/** The gate tower's middle column in the front curtain; the inner gate's in the cross wall. */
		int gc;
		int igc;
		/** The bergfried. */
		int kcx;
		int kcz;
		int kd;
		boolean keepRound;
		/** The palace's main range from pz0 to the back; its wing down the far side, wx0..wx1, from the cross wall. */
		int pz0;
		int wx0;
		int wx1;
	}

	static void draw(Draft d, Manner man, Dress m) {
		Plan p = plan(d, man);
		d.body(p.lx, p.fz, p.rx, p.bz);
		d.eave(p.walkI + 6);
		try (var ph = d.campaign(0)) {
			Court.fill(d, m, p.outer, p.ot, p.lo);
			Court.fill(d, m, p.inner, p.it, p.li);
		}
		// the walls first, so the towers and ranges standing on them are drawn over them; each curtain a stretch at a
		// time where it is footed far down a hillside
		outerCurtain(d, m, man, p, 6);
		innerCurtain(d, m, man, p, 1);
		try (var ph = d.campaign(0)) {
			bergfried(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			innerGate(d, m, man, p);
		}
		try (var ph = d.campaign(4)) {
			wing(d, m, man, p);
		}
		try (var ph = d.campaign(3)) {
			palace(d, m, man, p);
		}
		try (var ph = d.campaign(8)) {
			gate(d, m, man, p);
			shellTower(d, m, man, p);
		}
		try (var ph = d.campaign(9)) {
			outerWard(d, m, man, p);
			innerWard(d, m, p);
		}
		try (var ph = d.campaign(0)) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, p.maxTop);
		}
	}

	static Plan plan(Draft d, Manner man) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.c = (p.w - 1) / 2;
		p.maxTop = d.maxHeight() - 1;
		p.keepLeft = d.param("keep_side", d.chance(0.5) ? "left" : "right").equals("left");
		p.lx = 2;
		p.rx = p.w - 3;
		p.fz = 7;
		p.bz = p.dp - 3;
		int span = p.bz - p.fz;
		int outer = Math.clamp(Math.round(span * 0.36f), 11, 15);
		p.cz = p.fz + outer;
		p.lo = Court.level(d, p.lx + 2, p.fz + 2, p.rx - 2, p.cz - 1, -1, 2);
		int ground = Court.level(d, p.lx + 3, p.cz + 3, p.rx - 3, p.bz - 3, -99, 99);
		p.li = Math.clamp(ground, p.lo + 2, p.lo + 6);
		p.walkO = p.lo + 6;
		p.walkI = p.li + 8;
		// the outer bailey's front corners cut back along the brow: further where the ground falls away under them, and a
		// little anyway (a curtain laid out on the hill, not with a square)
		int most = Math.max(4, Math.min(outer - 4, (p.rx - p.lx - 18) / 2));
		int chL = chamfer(d, p, p.lx, 1, most, 11);
		int chR = chamfer(d, p, p.rx, -1, most, 12);
		p.chK = p.keepLeft ? chL : chR;
		p.chF = p.keepLeft ? chR : chL;
		p.outer = Outline.of(p.lx, p.cz, p.lx, p.fz + chL, p.lx + chL, p.fz, p.rx - chR, p.fz, p.rx, p.fz + chR, p.rx, p.cz);
		p.inner = Outline.box(p.lx, p.cz, p.rx, p.bz);
		p.gc = Math.clamp(p.c + (p.keepLeft ? 2 : -2), p.lx + chL + 5, p.rx - chR - 5);
		// the bergfried astride the cross wall on the keep's side
		p.keepRound = d.chance(Math.max(0.35, man.round()));
		p.kd = p.keepRound ? 11 : 9;
		int r = p.kd / 2;
		p.kcx = p.keepLeft ? p.lx + r - 1 : p.rx - r + 1;
		p.kcz = p.cz + 1;
		// the palace across the back, the wing down the far side
		int pd = p.dp >= 44 ? 8 : 7;
		p.pz0 = p.bz - pd + 1;
		int ww = 8;
		p.wx0 = p.keepLeft ? p.rx - ww + 1 : p.lx;
		p.wx1 = p.keepLeft ? p.rx : p.lx + ww - 1;
		// the inner gate in the cross wall between the bergfried and the wing
		int a = p.keepLeft ? p.kcx + r + 5 : p.wx1 + 5;
		int b = p.keepLeft ? p.wx0 - 5 : p.kcx - r - 5;
		p.igc = Math.clamp(p.c, Math.min(a, b), Math.max(a, b));
		return p;
	}

	/** How far a front corner of the outer bailey is cut back: with the fall of the ground under it, seeded a little. */
	private static int chamfer(Draft d, Plan p, int x, int in, int most, int salt) {
		int g = Math.min(d.site().ground(x, p.fz), d.site().ground(x + in * 3, p.fz + 3));
		int drop = Math.max(0, p.lo - g);
		int ch = 4 + 2 * drop + (int) (Noise.unit(d.seed(), salt, 5, 9) * 3);
		return Math.clamp(ch, 4, most);
	}

	/** Whether a cell is under the palace or its wing (which stand on the curtain's line and are the curtain there). */
	private static boolean palace(Plan p, int x, int z) {
		return z >= p.pz0 || z >= p.cz && x >= p.wx0 && x <= p.wx1;
	}

	// ------------------------------------------------------------------------------------------------ walls

	/** The outer curtain in campaign {@code first} (and the next, on a hillside), the steps up to its walk in the last. */
	private static void outerCurtain(Draft d, Dress m, Manner man, Plan p, int first) {
		int n = Curtain.encloseInCampaigns(d, m, man, p.outer, p.ot, p.walkO, Curtain.Opts.of(man), null, first, 2);
		try (var ph = d.campaign(first + n - 1)) {
			outerSteps(d, m, p);
		}
	}

	private static void outerSteps(Draft d, Dress m, Plan p) {
		// steps up to the walk along the front curtain, beside the gate where the straight runs longer
		int left = p.gc - 4 - (p.lx + (p.keepLeft ? p.chK : p.chF)) - 1;
		int right = p.rx - (p.keepLeft ? p.chF : p.chK) - (p.gc + 4) - 1;
		int rise = p.walkO - p.lo;
		if (Math.max(left, right) >= rise + 1) {
			boolean toRight = right >= left;
			Curtain.flight(d, m, toRight ? p.gc + 6 : p.gc - 6, p.fz + p.ot, toRight ? Dirs.RIGHT : Dirs.LEFT, p.lo, p.walkO);
		}
		Curtain.posts(d, p.lx + p.chK, p.fz, p.rx - p.chF, p.fz, Dirs.FRONT, p.ot, p.walkO, 7);
	}

	/** The inner curtain: the cross wall and the keep's side (the palace and the wing are the rest), its walks covered. */
	private static void innerCurtain(Draft d, Dress m, Manner man, Plan p, int first) {
		int n = Curtain.encloseInCampaigns(d, m, man, p.inner, p.it, p.walkI, Curtain.Opts.of(man), (x, z) -> palace(p, x, z), first, 2);
		try (var ph = d.campaign(first + n - 1)) {
			innerWalks(d, m, man, p);
		}
	}

	private static void innerWalks(Draft d, Dress m, Manner man, Plan p) {
		int kx = p.keepLeft ? p.lx : p.rx;
		Curtain.covered(d, m, man, kx, p.cz, kx, p.pz0 - 1, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT, p.it, p.walkI);
		if (p.keepLeft) {
			Curtain.covered(d, m, man, p.lx, p.cz, p.wx0 - 1, p.cz, Dirs.FRONT, p.it, p.walkI);
		} else {
			Curtain.covered(d, m, man, p.wx1 + 1, p.cz, p.rx, p.cz, Dirs.FRONT, p.it, p.walkI);
		}
	}

	// ------------------------------------------------------------------------------------------------ towers

	/**
	 * The bergfried astride the cross wall: the stores at the foot, a floor at the walks' level opening onto both of
	 * them (the guards' room), chambers over it, and a head that is either a helm of the roofing straight off the wall
	 * (Talmberg's tile hip, a low cone) or the older hoarded head.
	 */
	private static void bergfried(Draft d, Dress m, Manner man, Plan p) {
		int r = p.kd / 2;
		Manner.Cap cap = d.chance(0.5) ? man.keepCap() : p.keepRound ? Manner.Cap.CONE : Manner.Cap.PYRAMID;
		int head = Math.min(14, Tower.headRows(cap, p.kd, man));
		int top = Math.max(p.walkI + 6, Math.min(p.maxTop - head, p.li + 28));
		List<Integer> levels = new ArrayList<>();
		levels.add(p.li + 4);
		for (int y = p.walkI; y < top - 3; y += 5) {
			levels.add(y);
		}
		int[] lv = levels.stream().mapToInt(Integer::intValue).toArray();
		Tower t = p.keepRound ? Tower.round(p.kcx, p.kcz, p.kd, p.li, top, cap)
			: Tower.square(p.kcx - r, p.kcz - r, p.kcx + r, p.kcz + r, p.li, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, p.walkI, top);
		t.stair(d, m, man.timberTops(), p.keepLeft ? Dirs.LEFT : Dirs.BACK);
		t.floors(d, m, top, lv);
		// the door on the inner court, and doorways onto the walks of the cross wall and the keep's side
		t.door(d, m, Dirs.BACK, p.li, 1, true);
		int[] f = t.face(Dirs.BACK);
		Lighting.doorLamp(d, f[0] + 1, p.li + 2, f[1] + 1, Dirs.FRONT);
		t.doorToward(d, m, p.keepLeft ? Dirs.RIGHT : Dirs.LEFT, p.walkI, false);
		t.doorToward(d, m, Dirs.BACK, p.walkI, false);
		Direction field = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		t.loops(d, Set.of(Dirs.FRONT, field), p.li + 2, top);
		for (int k = 2; k < lv.length; k++) {
			t.window(d, m, Dirs.BACK, lv[k] + 1, 2);
			t.window(d, m, field.getOpposite(), lv[k] + 1, 2);
		}
		t.flag(field.getOpposite());
		t.head(d, m, man, p.maxTop);
		for (int k = 0; k <= lv.length; k++) {
			int y = k == 0 ? p.li : lv[k - 1];
			Rooms.Room room = t.room(y);
			if (y == p.walkI) {
				Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
			} else if (k == 0 || k % 2 == 1) {
				Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			} else {
				Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);
			}
		}
		t.light(d, top - 1);
		t.watch(d, Dirs.FRONT);
	}

	/** The shell tower on the far cut of the outer bailey: round and broad, a low cone or an open battlemented head. */
	private static void shellTower(Draft d, Dress m, Manner man, Plan p) {
		int x = p.keepLeft ? p.rx - p.chF / 2 : p.lx + p.chF / 2;
		int z = p.fz + p.chF / 2;
		Manner.Cap cap = Noise.unit(d.seed(), 4, 2, 8) < 0.65 ? Manner.Cap.CONE : Manner.Cap.CRENEL;
		int top = Math.min(p.walkO + 4, p.maxTop - Math.min(9, Tower.headRows(cap, 9, man)));
		Direction toFront = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		Castle.wallTower(d, m, man, x, z, 9, p.lo, p.walkO, Math.max(p.walkO + 2, top), true, cap, Set.of(toFront, Dirs.BACK),
			Set.of(Dirs.FRONT, toFront.getOpposite()));
	}

	// ------------------------------------------------------------------------------------------------ gates

	/** The gate tower in the front curtain, standing out over the way up to it. */
	private static void gate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walkO + 6, p.maxTop - 8);
		Gate.tower(d, m, man, p.gc - 4, p.fz - 3, p.gc + 4, p.fz + 3, 3, p.lo, p.walkO, top, p.maxTop);
		d.entrance(p.gc, p.lo, p.fz - 4);
		Groundwork.approach(d, p.gc, p.lo, p.fz - 4);
		Lighting.doorLamp(d, p.gc - 2, p.lo + 2, p.fz - 4, Dirs.BACK);
		Lighting.doorLamp(d, p.gc + 2, p.lo + 2, p.fz - 4, Dirs.BACK);
		Flag.banner(d, m, p.gc, p.walkO + 1, p.fz - 4, Dirs.FRONT, 0);
		d.poi(PoiType.GUARD_POST, p.gc + 2, p.lo, p.fz - 4, Dirs.FRONT);
	}

	/** The inner gate tower in the cross wall, a ramp up to it from the bailey. */
	private static void innerGate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walkI + 5, p.maxTop - 8);
		Gate.tower(d, m, man, p.igc - 3, p.cz - 1, p.igc + 3, p.cz + p.it + 1, 3, p.li, p.walkI, top, p.maxTop);
		Castle.ramp(d, m, p.igc, p.cz - 2, p.lo, p.li);
	}

	// ------------------------------------------------------------------------------------------------ the palace

	/**
	 * The palace across the whole back, its back and end walls the curtain: three storeys under one great hip with
	 * dormers and chimneys: the kitchen and the stores below, the great hall over them with the throne at the end toward
	 * the bergfried, the lord's chamber and the steward's room under the roof; a round stair turret on the court in the
	 * angle with the wing, its spiral turned to arrive on the palace's side at every storey.
	 */
	private static void palace(Draft d, Dress m, Manner man, Plan p) {
		int[] heights = {5, 7, 5};
		Range main = new Range(p.lx, p.pz0, p.rx, p.bz, p.li, heights, Dirs.FRONT);
		// the part of the front wall the wing stands against stays blind
		for (int x = p.wx0; x <= p.wx1; x++) {
			main.keepClear(x, p.pz0, p.li, main.plate());
		}
		main.footings(d, m, true);
		main.walls(d, m, man);
		main.floors(d, m);
		// doorways from the wing into the stores and the hall through the front wall it stands against
		int wc = (p.wx0 + p.wx1) / 2;
		for (int k = 0; k < 2; k++) {
			int y = main.floorY(k);
			try (var s = d.stage(dev.voidmc.hearth.api.StageKind.WALLS)) {
				d.set(wc, y, p.pz0, Blocks.AIR.defaultBlockState());
				d.set(wc, y + 1, p.pz0, Blocks.AIR.defaultBlockState());
				d.set(wc, y + 2, p.pz0, m.dress.full());
			}
		}
		Direction head = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		int tcx = p.keepLeft ? p.wx0 - 4 : p.wx1 + 4;
		int tcz = p.pz0 - 3;
		int dcol = p.keepLeft ? (p.lx + p.wx0) / 2 - 2 : (p.wx1 + p.rx) / 2 + 2;
		main.door(d, m, Dirs.FRONT, dcol, 0, 2, false);
		main.keepClear(tcx, p.pz0, p.li, main.floorY(2) + 3);
		main.windows(d, m, man, 0, 2, 4, Dirs.BACK);
		main.windows(d, m, man, 1, 4, 3, null);
		main.windows(d, m, man, 2, 2, 3, null);
		// the hall's hearth on the back wall toward the throne, the kitchen's in the kitchen's back wall
		int xm = (p.lx + p.rx) / 2;
		int[] hall = main.hearth(d, p.keepLeft ? p.lx + 5 : p.rx - 5, p.bz, 1, Dirs.FRONT);
		int[] kitchen = main.hearth(d, p.keepLeft ? xm - 3 : xm + 3, p.bz, 0, Dirs.FRONT);
		int dormers = Math.max(3, (p.rx - p.lx) / 7);
		main.roof(d, m, man, RoofForm.HIP, Direction.Axis.X, p.maxTop, dormers,
			List.of(new int[]{hall[0], hall[1]}, new int[]{kitchen[0], kitchen[1]}));
		// the stair turret
		int ttop = Math.max(main.floorY(2) + 3, Math.min(main.ridge - 2, p.maxTop - 6));
		Tower stair = Turret.stair(d, m, man, tcx, tcz, 7, p.li, ttop, man.timberTops(), true, Dirs.BACK, main.floorY(1), main.floorY(2));
		stair.door(d, m, Dirs.FRONT, p.li, 1, true);
		for (int k = 0; k < heights.length; k++) {
			stair.door(d, m, Dirs.BACK, main.floorY(k), 1, false);
		}
		stair.loops(d, Set.of(Dirs.LEFT, Dirs.RIGHT), p.li + 2, ttop);
		stair.head(d, m, man, p.maxTop);
		stair.light(d, ttop - 1);
		// the rooms: the ground floor parted into the kitchen (toward the keep) and the stores (toward the wing)
		int tdz = p.pz0 + 1;
		int[] g = main.room(0);
		main.partition(d, m, 0, true, xm, (g[1] + g[3]) / 2);
		Rooms.Room kitchenRoom = Chambers.room(p.keepLeft ? g[0] : xm + 1, g[1], p.keepLeft ? xm - 1 : g[2], g[3], g[4], new int[]{dcol, g[1]},
			new int[]{dcol + 1, g[1]}, new int[]{p.keepLeft ? xm - 1 : xm + 1, (g[1] + g[3]) / 2});
		Chambers.furnish(d, kitchenRoom, Rooms.Type.KITCHEN, 0);
		Rooms.Room stores = Chambers.room(p.keepLeft ? xm + 1 : g[0], g[1], p.keepLeft ? g[2] : xm - 1, g[3], g[4], new int[]{tcx, tdz},
			new int[]{wc, g[1]}, new int[]{p.keepLeft ? xm + 1 : xm - 1, (g[1] + g[3]) / 2});
		Chambers.furnish(d, stores, Rooms.Type.STORAGE, 0);
		int[] h = main.room(1);
		Rooms.Room hallRoom = Chambers.room(h[0], h[1], h[2], h[3], h[4], new int[]{tcx, tdz}, new int[]{wc, h[1]});
		Chambers.hall(d, m, hallRoom, head, true);
		Range.hang(d, h, 4);
		int[] u = main.room(2);
		int us = xm;
		main.partition(d, m, 2, true, us, u[1] + 1);
		Rooms.Room lord = Chambers.room(p.keepLeft ? u[0] : us + 1, u[1], p.keepLeft ? us - 1 : u[2], u[3], u[4],
			new int[]{p.keepLeft ? us - 1 : us + 1, u[1] + 1});
		Chambers.niche(d, m, lord, 0, 0, head);
		Chambers.furnish(d, lord, Rooms.Type.BEDROOM, 2);
		Rooms.Room steward = Chambers.room(p.keepLeft ? us + 1 : u[0], u[1], p.keepLeft ? u[2] : us - 1, u[3], u[4], new int[]{tcx, tdz},
			new int[]{p.keepLeft ? us + 1 : us - 1, u[1] + 1});
		Chambers.desk(d, m, steward, 0, 0, Dirs.BACK);
		Chambers.furnish(d, steward, Rooms.Type.BEDROOM, 1);
		for (int k = 0; k < main.storeys(); k++) {
			int[] rm = main.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	/**
	 * The wing down the far side from the cross wall to the palace, its outer and front walls the curtain: two storeys,
	 * the household below, the chapel upstairs with its altar under the front gable, which carries the bell turret over
	 * the bailey; doorways into the palace on both floors.
	 */
	private static void wing(Draft d, Dress m, Manner man, Plan p) {
		Direction court = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range wing = new Range(p.wx0, p.cz, p.wx1, p.pz0, p.li, new int[]{5, 5}, court);
		wing.blind(Dirs.BACK);
		wing.footings(d, m, true);
		wing.walls(d, m, man);
		wing.floors(d, m);
		int wc = (p.wx0 + p.wx1) / 2;
		// (the doorways into the palace through its front wall are the palace's: its walls are drawn after)
		for (int k = 0; k < 2; k++) {
			wing.keepClear(wc, p.pz0, wing.floorY(k), wing.floorY(k) + 2);
		}
		int wdoor = (p.cz + p.pz0) / 2 + 1;
		wing.door(d, m, court, wdoor, 0, 1, false);
		wing.windows(d, m, man, 0, 2, 3, court.getOpposite());
		var cs = wing.side(court);
		for (int i = 3; i < cs.length() - 1; i += 3) {
			Range.window(d, m, Manner.Window.LANCET, cs, i, wing.floorY(1) + 1, 3);
		}
		var front = wing.side(Dirs.FRONT);
		Range.window(d, m, Manner.Window.LANCET, front, front.middle(), wing.floorY(1) + 1, 3);
		int[] hearth = wing.hearth(d, court == Dirs.LEFT ? p.wx1 : p.wx0, p.cz + 3, 0, court);
		wing.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop - 2, 0, List.of(new int[]{hearth[0], hearth[1]}));
		if (wing.roof != null && wing.roof.covers(wc, p.cz + 1)) {
			Castle.belfry(d, m, man, wc, p.cz + 1, wing.roof.surfaceY(wc, p.cz + 1) + 1, p.maxTop);
		}
		// the household below; the chapel upstairs, its altar under the front gable
		int[] g = wing.room(0);
		int gx = court == Dirs.LEFT ? g[0] : g[2];
		Rooms.Room house = Chambers.room(g[0], g[1], g[2], g[3], g[4], new int[]{gx, wdoor}, new int[]{wc, g[3]});
		house.keep(hearth[0] - Dirs.dx(court.getOpposite()), p.cz + 3);
		Chambers.furnish(d, house, Rooms.Type.BEDROOM, 3);
		int[] u = wing.room(1);
		Sacred.altar(d, wc, u[4], u[1], Dirs.BACK, u[2] - u[0] >= 4, wc + 2);
		Sacred.pews(d, u[0], u[2], u[1] + 3, u[3] - 2, u[4], wc, Dirs.FRONT, false);
		Range.hang(d, u, 4);
		for (int k = 0; k < 2; k++) {
			int[] rm = wing.room(k);
			Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
		}
	}

	// ------------------------------------------------------------------------------------------------ the wards

	private static void outerWard(Draft d, Dress m, Manner man, Plan p) {
		// the stables against the cross wall on the far side of the ramp
		int sx0 = p.keepLeft ? p.igc + 4 : p.lx + p.ot;
		int sx1 = p.keepLeft ? p.rx - p.ot : p.igc - 4;
		int sz1 = p.cz - p.ot;
		for (int depth = 5; depth >= 4; depth--) {
			int sz0 = sz1 - depth;
			// short of the shell tower on the far cut
			while (sx1 - sx0 >= 6 && !Castle.open(d, sx0, sz0, sx1, sz1, p.lo, p.lo + 4)) {
				if (p.keepLeft) {
					sx1--;
				} else {
					sx0++;
				}
			}
			if (sx1 - sx0 >= 6 && Castle.open(d, sx0, sz0, sx1, sz1, p.lo, p.lo + 4)) {
				int[] door = new int[2];
				Rooms.Room room = Court.leanTo(d, m, man, sx0, sz0, sx1, sz1, p.lo, Dirs.BACK, 4, door);
				stalls(d, room);
				Lighting.room(d, room.x0, room.z0, room.x1, room.z1, p.lo);
				d.poi(PoiType.HITCHING_POST, door[0], p.lo, door[1], Dirs.BACK);
				break;
			}
		}
		// the smithy against the keep's side, between the cut and the bergfried's foot
		int mz0 = p.fz + p.chK + 2;
		int mz1 = p.kcz - p.kd / 2 - 2;
		int mx0 = p.keepLeft ? p.lx + p.ot : p.rx - p.ot - 4;
		int mx1 = mx0 + 4;
		if (mz1 - mz0 >= 4 && Castle.open(d, mx0, mz0, mx1, mz1, p.lo, p.lo + 4)) {
			Rooms.Room room = Court.leanTo(d, m, man, mx0, mz0, mx1, mz1, p.lo, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT, 4, new int[2]);
			Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.SMITH);
			Lighting.room(d, room.x0, room.z0, room.x1, room.z1, p.lo);
		}
		// a well off the way from the gate to the ramp
		int wx = (p.gc + p.igc) / 2 + (p.keepLeft ? -5 : 5);
		int wz = (p.fz + p.ot + p.cz) / 2;
		if (Castle.open(d, wx - 2, wz - 2, wx + 2, wz + 2, p.lo, p.lo + 3)) {
			Court.well(d, m, wx, wz, p.lo);
		}
		Court.lamps(d, p.outer, p.ot, p.lo);
	}

	/** Stalls along the back of a stable lean-to (its wall the cross wall): fences out from the wall, hay and troughs. */
	private static void stalls(Draft d, Rooms.Room room) {
		var fence = d.mat(dev.voidmc.hearth.build.style.Role.FENCE);
		try (var s = d.stage(dev.voidmc.hearth.api.StageKind.INTERIOR)) {
			for (int x = room.x0 + 1; x < room.x1; x++) {
				int k = x - room.x0;
				if (!d.isEmpty(x, room.y, room.z1) || !Chambers.take(room, x, room.z1)) {
					continue;
				}
				if (k % 3 == 0) {
					d.set(x, room.y, room.z1, fence.fence());
				} else {
					d.set(x, room.y, room.z1, k % 3 == 1 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.COMPOSTER.defaultBlockState());
				}
			}
		}
	}

	private static void innerWard(Draft d, Dress m, Plan p) {
		// a lime tree and a well in the inner court, lamps
		int x0 = p.keepLeft ? p.lx + p.it : p.wx1 + 1;
		int x1 = p.keepLeft ? p.wx0 - 1 : p.rx - p.it;
		int z0 = p.cz + p.it;
		int z1 = p.pz0 - 1;
		int cx = (x0 + x1) / 2 + (p.keepLeft ? 2 : -2);
		int cz = (z0 + z1) / 2;
		if (z1 - z0 >= 8 && Castle.open(d, cx - 2, cz - 2, cx + 2, cz + 2, p.li, p.li + 3)) {
			Court.well(d, m, cx, cz, p.li);
		}
		int tx = p.keepLeft ? p.kcx + p.kd / 2 + 4 : p.kcx - p.kd / 2 - 4;
		int tz = z1 - 3;
		if (Castle.open(d, tx - 2, tz - 2, tx + 2, tz + 2, p.li, p.li + 7)) {
			Garden.tree(d, tx, p.li, tz, 5);
		}
		Court.lamps(d, x0, z0, x1, z1, p.li);
		d.poi(PoiType.MEETING_POINT, cx, p.li, Math.max(z0, cz - 3), Dirs.BACK);
	}
}
