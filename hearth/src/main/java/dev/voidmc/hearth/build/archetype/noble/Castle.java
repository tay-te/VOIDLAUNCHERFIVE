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
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Curtain;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Gate;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Mason;
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
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:noble/castle}: a city's castle on its hill, in the manner of Kingdom Come's Bohemia (Rattay, Talmberg,
 * Trosky). From the road: a walled barbican with its own gate before a twin-towered gatehouse in the front curtain; the
 * outer ward behind it, its curtain flanked by round towers, with the stables and the smithy in lean-tos against its
 * walls, a well and the lists; a cross wall, higher, with the inner gate tower over a ramp up to the inner ward on its
 * raised terrace; the bergfried rising over the cross wall at one end, the tallest thing for miles under its hoarded cap
 * and the lord's flag; across the back of the inner ward the palace of three storeys under a steep roof with dormers and
 * chimneys, its stair turret on the court, the great hall on its first floor with the throne on the dais, the lord's
 * chambers and the steward's counting room over it; the chapel with its ridge turret beside the court; corner towers at
 * every turn of the walls. Curtains follow the ground: footed wherever it lies, they show their full height to the field
 * and stand at their walk's height over the courts.
 *
 * <p>Every culture builds its own (the {@link Manner}): Tyrolean white towers under needle pyramids, harled Scottish
 * walls with bartizans and crow steps, granite with machicolated open tops, northern stone walls with timber hoardings,
 * swallowtail merlons in the south, Moorish pointed merlons in the desert, curved roofs in the karst, domed sentry turrets
 * in the tropics; the steppe raises an ordu, a round ward round the khan's great round hall.
 *
 * <p>Raised in nine campaigns ({@link Draft#campaign(int)}), so a castle half built looks like one and no stage asks for more
 * than a town can store: the bergfried on the levelled hill; the inner curtain in two; the inner towers; the palace; the
 * inner gate and the chapel; the outer curtain; the outer towers, the gatehouse and the barbican; the outer ward's
 * buildings and the courts.
 *
 * <p>Params: {@code station} ({@code lord}), {@code keep_side} ({@code left}/{@code right}), {@code replaces} (the keep's
 * plot, set by the planner), {@code variant}.
 */
public final class Castle implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("noble/castle"), ArchetypeCategory.NOBLE,
		Set.of(JobType.LORD, JobType.STEWARD, JobType.GUARD, JobType.PRIEST), 14, 37, 47, 39, 49, 30, 42, SettlementTier.CITY, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** Where everything stands (local coordinates; walls by their field faces). */
	static final class Plan {
		int w;
		int dp;
		int c;
		int maxTop;
		boolean keepLeft;
		/** Field faces of the side curtains, the front curtain, the cross wall (facing the outer ward), the back curtain. */
		int lx;
		int rx;
		int fz;
		int cz;
		int bz;
		int ot = 2;
		int it = 3;
		/** Court levels and walks of the outer and inner wards. */
		int lo;
		int li;
		int walkO;
		int walkI;
		/** The gate block. */
		int gx0;
		int gx1;
		int gz0;
		int gz1;
		/** The inner gate tower over the cross wall. */
		int igx0;
		int igx1;
		/** The bergfried. */
		int kcx;
		int kcz;
		int kd;
		boolean keepRound;
		/** The palace across the back of the inner ward. */
		int px0;
		int px1;
		int pz0;
		/** The chapel in the inner ward. */
		int chx0;
		int chx1;
		int chz0;
		int chz1;
	}

	@Override
	public void draw(Draft d) {
		Manner man = Manner.of(d);
		Dress m = Dress.of(d);
		Plan p = plan(d, man);
		d.body(p.lx, p.fz, p.rx, p.bz);
		d.eave(p.walkI + 6);

		// ---- campaign 0: the hill levelled into its two terraces, the bergfried
		try (var ph = d.campaign(0)) {
			terraces(d, m, p);
		}
		// the walls are drawn before the towers and the buildings that stand over them (their campaigns are later)
		try (var ph = d.campaign(1)) {
			innerCurtain(d, m, man, p, true);
		}
		try (var ph = d.campaign(2)) {
			innerCurtain(d, m, man, p, false);
		}
		try (var ph = d.campaign(6)) {
			outerCurtain(d, m, man, p);
		}
		try (var ph = d.campaign(0)) {
			bergfried(d, m, man, p);
		}
		// ---- campaigns 1 to 3: the inner curtain in two, then its towers
		try (var ph = d.campaign(3)) {
			innerTowers(d, m, man, p);
		}
		// ---- campaign 5: the chapel, the inner gate (drawn before the palace, whose stair turret may pierce its eaves)
		try (var ph = d.campaign(5)) {
			innerGate(d, m, man, p);
		}
		// ---- campaign 4: the palace
		try (var ph = d.campaign(4)) {
			palace(d, m, man, p);
		}
		try (var ph = d.campaign(5)) {
			chapel(d, m, man, p);
		}
		// ---- campaigns 6 and 7: the outer curtain, then the outer towers, the gatehouse, the barbican
		try (var ph = d.campaign(7)) {
			outerTowers(d, m, man, p);
			gatehouse(d, m, man, p);
		}
		// ---- campaign 8: the outer ward's buildings, the courts
		try (var ph = d.campaign(8)) {
			outerWard(d, m, man, p);
			innerWard(d, m, man, p);
		}
		try (var ph = d.campaign(0)) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, p.maxTop);
		}
	}

	// ------------------------------------------------------------------------------------------------ the plan

	static Plan plan(Draft d, Manner man) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.c = (p.w - 1) / 2;
		p.maxTop = d.maxHeight() - 1;
		p.keepLeft = d.param("keep_side", d.chance(0.5) ? "left" : "right").equals("left");
		p.lx = 3;
		p.rx = p.w - 4;
		p.fz = p.dp >= 43 ? 8 : 7;
		p.bz = p.dp - 4;
		int span = p.bz - p.fz;
		int outer = Math.clamp(Math.round(span * 0.36f), 10, 15);
		p.cz = p.fz + p.ot + outer;
		// levels: the outer ward near the road's level, the inner ward on its raised terrace
		p.lo = Court.level(d, p.lx + p.ot, p.fz + p.ot, p.rx - p.ot, p.cz - 1, -1, 2);
		int ground = Court.level(d, p.lx + p.it, p.cz + p.it, p.rx - p.it, p.bz - p.it, -99, 99);
		p.li = Math.clamp(ground, p.lo + 2, p.lo + 6);
		p.walkO = p.lo + 6;
		p.walkI = p.li + 8;
		// the gate block, its towers on its front corners standing out over the barbican's court
		p.gx0 = p.c - 5;
		p.gx1 = p.c + 5;
		p.gz0 = p.fz - 1;
		p.gz1 = p.fz + 4;
		// the bergfried over the cross wall at one end
		p.keepRound = man.culture() != Culture.MOORLAND && man.culture() != Culture.KARST && d.chance(Math.max(0.35, man.round()));
		p.kd = p.keepRound ? 11 : 9;
		int r = p.kd / 2;
		p.kcx = p.keepLeft ? p.lx + r - 1 : p.rx - r + 1;
		p.kcz = p.cz + 1;
		// the inner gate tower on the cross wall, away from the keep
		int ig = p.keepLeft ? Math.max(p.c + 2, p.kcx + r + 6) : Math.min(p.c - 2, p.kcx - r - 6);
		p.igx0 = ig - 3;
		p.igx1 = ig + 3;
		// the palace across the back, its back wall the back curtain
		int pd = p.dp >= 45 ? 8 : 7;
		p.pz0 = p.bz - pd + 1;
		p.px0 = p.lx + 1;
		p.px1 = p.rx - 1;
		// the chapel against the side curtain on the gate's side, between the cross wall and the palace
		int chw = 7;
		p.chz0 = p.cz + p.it + 1;
		p.chz1 = Math.min(p.pz0 - 3, p.chz0 + 9);
		if (p.keepLeft) {
			p.chx1 = p.rx - p.it;
			p.chx0 = p.chx1 - chw + 1;
		} else {
			p.chx0 = p.lx + p.it;
			p.chx1 = p.chx0 + chw - 1;
		}
		return p;
	}

	/** Whether the culture roofs its inner walks with a timber pentice (central Europe and the north). */
	static boolean covered(Manner man) {
		return switch (man.culture()) {
			case TEMPERATE, ALPINE, NORTHERN, TUNDRA, WISTERIA -> true;
			default -> false;
		};
	}

	/** The parts of a castle a tower can play. */
	enum Role {
		OUTER, FLANK, INNER, CROSS, TURRET
	}

	/**
	 * How a tower in a role is capped: the manner's cap, varied the way a castle that grew over centuries varies (the
	 * outer towers of a temperate castle left open behind their battlements, the cross-wall tower under a pyramid...).
	 */
	static Manner.Cap cap(Manner man, Role role, boolean alt) {
		Manner.Cap c = man.cap();
		return switch (c) {
			case CONE -> switch (role) {
				case OUTER -> Manner.Cap.CRENEL;
				case TURRET -> Manner.Cap.CRENEL;
				case CROSS -> man.hoardings() > 0.3 ? Manner.Cap.HOARD : Manner.Cap.PYRAMID;
				case INNER -> alt ? Manner.Cap.PYRAMID : Manner.Cap.CONE;
				default -> Manner.Cap.CONE;
			};
			case PYRAMID -> role == Role.TURRET ? Manner.Cap.CRENEL : Manner.Cap.PYRAMID;
			case HOARD -> role == Role.TURRET || role == Role.OUTER && alt ? Manner.Cap.PYRAMID : Manner.Cap.HOARD;
			case DOME -> role == Role.OUTER || role == Role.TURRET ? Manner.Cap.DOME : role == Role.CROSS ? Manner.Cap.PYRAMID : Manner.Cap.CRENEL;
			default -> c;
		};
	}

	// ------------------------------------------------------------------------------------------------ ground and walls

	private static void terraces(Draft d, Dress m, Plan p) {
		Groundwork.level(d, p.lx + p.ot, p.fz + p.ot, p.rx - p.ot, p.cz - 1, p.lo, true);
		Groundwork.level(d, p.lx + p.it, p.cz + p.it, p.rx - p.it, p.bz - p.it, p.li, true);
		// the barbican's court and the way in, at the road's level
		Groundwork.level(d, p.c - 3, 0, p.c + 3, p.gz0 - 1, Math.min(p.lo, 0), true);
		Court.pave(d, m, p.lx + p.ot, p.fz + p.ot, p.rx - p.ot, p.cz - 1, p.lo);
		Court.pave(d, m, p.lx + p.it, p.cz + p.it, p.rx - p.it, p.bz - p.it, p.li);
	}

	/** The inner curtain in two campaigns: the cross wall and the keep's side first, then the far side and the back. */
	private static void innerCurtain(Draft d, Dress m, Manner man, Plan p, boolean first) {
		Curtain.Opts o = Curtain.Opts.of(man);
		boolean cover = covered(man);
		Direction keepSide = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		int kx = p.keepLeft ? p.lx : p.rx;
		int fx = p.keepLeft ? p.rx : p.lx;
		if (first) {
			Curtain.run(d, m, man, p.lx, p.cz, p.rx, p.cz, Dirs.FRONT, p.it, p.walkI, o);
			Curtain.run(d, m, man, kx, p.cz, kx, p.bz, keepSide, p.it, p.walkI, o);
			if (cover) {
				Curtain.covered(d, m, man, p.lx, p.cz, p.rx, p.cz, Dirs.FRONT, p.it, p.walkI);
				Curtain.covered(d, m, man, kx, p.cz, kx, p.bz, keepSide, p.it, p.walkI);
			}
			// steps up to the inner walk along the cross wall's court face, on the gate's side of the keep
			int sx = p.keepLeft ? p.rx - p.it - 1 : p.lx + p.it + 1;
			Direction up = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
			if (Math.abs(sx - (p.igx0 + p.igx1) / 2) > 4 + (p.walkI - p.li)) {
				Curtain.flight(d, m, sx, p.cz + p.it, up, p.li, p.walkI);
			}
			return;
		}
		Curtain.run(d, m, man, fx, p.cz, fx, p.bz, keepSide.getOpposite(), p.it, p.walkI, o);
		Curtain.run(d, m, man, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.it, p.walkI, o);
		if (cover) {
			Curtain.covered(d, m, man, fx, p.cz, fx, p.bz, keepSide.getOpposite(), p.it, p.walkI);
		}
		Curtain.posts(d, p.lx, p.bz, p.rx, p.bz, Dirs.BACK, p.it, p.walkI, 8);
	}

	private static void outerCurtain(Draft d, Dress m, Manner man, Plan p) {
		Curtain.Opts o = Curtain.Opts.of(man);
		Curtain.run(d, m, man, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.ot, p.walkO, o);
		Curtain.run(d, m, man, p.lx, p.fz, p.lx, p.cz, Dirs.LEFT, p.ot, p.walkO, o);
		Curtain.run(d, m, man, p.rx, p.fz, p.rx, p.cz, Dirs.RIGHT, p.ot, p.walkO, o);
		// steps up to the outer walk along the front curtain's court face
		int sx = p.keepLeft ? p.gx1 + 2 : p.gx0 - 2;
		Direction up = p.keepLeft ? Dirs.RIGHT : Dirs.LEFT;
		Curtain.flight(d, m, sx, p.fz + p.ot, up, p.lo, p.walkO);
		Curtain.posts(d, p.lx, p.fz, p.rx, p.fz, Dirs.FRONT, p.ot, p.walkO, 7);
	}

	// ------------------------------------------------------------------------------------------------ towers

	/** The bergfried: the tall keep astride the cross wall, the last refuge, its door high on the inner court. */
	private static void bergfried(Draft d, Dress m, Manner man, Plan p) {
		int kd = p.kd;
		int r = kd / 2;
		Manner.Cap cap = man.keepCap();
		int minHead = cap == Manner.Cap.CRENEL || cap == Manner.Cap.DOME ? 4 : (int) Math.ceil(r + 2.5) + 5;
		int top = Math.min(p.maxTop - minHead, p.li + 30);
		int[] heights = {5, 5, 5, 5, 5, 5, 5};
		List<Integer> levels = new ArrayList<>();
		for (int y = p.li + 5; y < top - 3; y += 5) {
			levels.add(y);
		}
		int[] lv = levels.stream().mapToInt(Integer::intValue).toArray();
		Tower t = p.keepRound ? Tower.round(p.kcx, p.kcz, kd, p.li, top, cap)
			: Tower.square(p.kcx - r, p.kcz - r, p.kcx + r, p.kcz + r, p.li, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, lv.length > 0 ? lv[0] : top, top);
		t.stair(d, m, man.timberTops(), p.keepLeft ? Dirs.LEFT : Dirs.BACK);
		t.floors(d, m, top, lv);
		// the door on the inner court; a door onto the inner walk
		t.door(d, m, Dirs.BACK, p.li, 1, true);
		int[] f = t.face(Dirs.BACK);
		Lighting.doorLamp(d, f[0] + 1, p.li + 2, f[1] + 1, Dirs.FRONT);
		t.loops(d, Set.of(Dirs.FRONT, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT), p.li + 2, top);
		for (int k = 1; k < lv.length; k += 2) {
			t.window(d, m, Dirs.BACK, lv[k] + 1, 2);
			t.window(d, m, p.keepLeft ? Dirs.RIGHT : Dirs.LEFT, lv[k] + 1, 2);
		}
		t.flag(p.keepLeft ? Dirs.RIGHT : Dirs.LEFT);
		t.head(d, m, man, p.maxTop);
		if (man.bartizans() && !p.keepRound) {
			t.bartizans(d, m, man, top, p.maxTop);
		}
		// the rooms: the stores, the guards' room, the armoury, the prison under the stores is left to the imagination
		for (int k = 0; k <= lv.length; k++) {
			int y = k == 0 ? p.li : lv[k - 1];
			Rooms.Room room = t.room(y);
			switch (k % 3) {
				case 0 -> Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
				case 1 -> Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);
				default -> Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			}
		}
		t.light(d, top - 1);
		t.watch(d, Dirs.FRONT);
	}

	/**
	 * Corner towers of the inner ward: at the back corners a tall round tower on one side, a square one on the other
	 * (where the palace ends), and where the cross wall meets the far side a square tower under a hoarding or a pyramid.
	 */
	private static void innerTowers(Draft d, Dress m, Manner man, Plan p) {
		boolean round = man.round() >= 0.3;
		for (int side : new int[]{-1, 1}) {
			boolean tall = (side < 0) == p.keepLeft;
			int cx = side < 0 ? p.lx : p.rx;
			int top = Math.min(p.walkI + (tall ? 4 : 7), p.maxTop - 8);
			boolean thisRound = round && !tall || man.round() >= 0.99;
			wallTower(d, m, man, cx, p.bz, 7, p.li, p.walkI, top, thisRound, cap(man, Role.INNER, !thisRound),
				Set.of(side < 0 ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT), Set.of(Dirs.BACK, side < 0 ? Dirs.LEFT : Dirs.RIGHT));
		}
		// where the cross wall meets the side away from the keep
		int cx = p.keepLeft ? p.rx : p.lx;
		wallTower(d, m, man, cx, p.cz + 1, 7, p.li, p.walkI, Math.min(p.walkI + 6, p.maxTop - 9), man.round() >= 0.99, cap(man, Role.CROSS, false),
			Set.of(p.keepLeft ? Dirs.LEFT : Dirs.RIGHT, Dirs.BACK), Set.of(Dirs.FRONT, p.keepLeft ? Dirs.RIGHT : Dirs.LEFT));
	}

	/**
	 * A tower on the walls: round or square, diameter {@code diam} centred on (cx, cz), its ground floor at the court's
	 * level {@code court} with a door into the court, doorways onto the walk at {@code walk} toward {@code walkDoors}, loops
	 * toward {@code field}, a spiral up to its head under {@code cap}.
	 */
	static Tower wallTower(Draft d, Dress m, Manner man, int cx, int cz, int diam, int court, int walk, int top, boolean round, Manner.Cap cap,
		Set<Direction> walkDoors, Set<Direction> field) {
		int r = diam / 2;
		Tower t = round ? Tower.round(cx, cz, diam, court, top, cap) : Tower.square(cx - r, cz - r, cx + r, cz + r, court, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, walk);
		t.stair(d, m, man.timberTops(), null);
		t.floors(d, m, top, walk);
		for (Direction dir : walkDoors) {
			t.doorToward(d, m, dir, walk, false);
		}
		// the ground floor's door on the court, in the angle of the walls
		for (Direction courtSide : walkDoors) {
			if (t.doorToward(d, m, courtSide, court, true) != null) {
				break;
			}
		}
		t.loops(d, field, court + 2, top);
		t.head(d, m, man, d.maxHeight() - 1);
		Rooms.Room ground = t.room(court);
		Rooms.furnish(d, ground, Rooms.Type.STORAGE, 0, JobType.NONE);
		t.light(d, top - 1);
		t.watch(d, field.iterator().next());
		return t;
	}

	/** Round towers on the outer ward's front corners (open behind their battlements in some), turrets on its long sides. */
	private static void outerTowers(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walkO + 3, p.maxTop - 7);
		boolean round = man.round() >= 0.3;
		boolean alt = dev.voidmc.hearth.build.draft.Noise.unit(d.seed(), 3, 1, 4) < 0.5;
		wallTower(d, m, man, p.lx, p.fz, 7, p.lo, p.walkO, top, round, cap(man, Role.OUTER, alt), Set.of(Dirs.RIGHT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.LEFT));
		wallTower(d, m, man, p.rx, p.fz, 7, p.lo, p.walkO, top, round, cap(man, Role.OUTER, alt), Set.of(Dirs.LEFT, Dirs.BACK), Set.of(Dirs.FRONT, Dirs.RIGHT));
		// half-round turrets on the long sides of the outer ward
		if (p.cz - p.fz >= 14) {
			int z = (p.fz + p.cz) / 2;
			Turret.wall(d, m, man, p.lx, z, 5, p.walkO, p.maxTop, Set.of(Dirs.FRONT, Dirs.BACK), cap(man, Role.TURRET, false));
			Turret.wall(d, m, man, p.rx, z, 5, p.walkO, p.maxTop, Set.of(Dirs.FRONT, Dirs.BACK), cap(man, Role.TURRET, false));
		}
	}

	// ------------------------------------------------------------------------------------------------ gates

	private static void gatehouse(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walkO + 6, p.maxTop - 8);
		Tower[] flank = new Tower[2];
		Gate.gatehouse(d, m, man, p.gx0, p.gz0, p.gx1, p.gz1, 3, p.lo, p.walkO, top, p.maxTop, man.round() >= 0.3, 7, flank);
		// the barbican before it: a walled forecourt with its own gate at the road
		int bw = p.gx1 - p.gx0 + 1;
		int zg = p.gz0 - 2;
		if (zg >= 4) {
			Gate.barbican(d, m, man, p.c, bw, 0, zg, 3, Math.min(p.lo, 0), 4, p.maxTop, man.round() >= 0.3);
		}
		// the way in: from the road through the barbican to the gate, stepping to the outer ward's level
		int y0 = Math.min(p.lo, 0);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = 0; z < p.gz0; z++) {
				for (int x = p.c - 1; x <= p.c + 1; x++) {
					d.set(x, y0 - 1, z, m.paving.full());
				}
			}
			if (p.lo > y0) {
				for (int x = p.c - 1; x <= p.c + 1; x++) {
					d.set(x, y0, p.gz0 - 1, m.paving.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				}
			}
		}
		d.entrance(p.c, 0, -1);
		dev.voidmc.hearth.build.kit.noble.Flag.banner(d, m, p.c, p.walkO + 1, p.gz0 - 1, Dirs.FRONT, 0);
	}

	/** The inner gate tower over the cross wall, a ramp up to it from the outer ward. */
	private static void innerGate(Draft d, Dress m, Manner man, Plan p) {
		int top = Math.min(p.walkI + 5, p.maxTop - 8);
		Gate.tower(d, m, man, p.igx0, p.cz - 1, p.igx1, p.cz + p.it + 1, 3, p.li, p.walkI, top, p.maxTop);
		int c = (p.igx0 + p.igx1) / 2;
		// the ramp: steps from the outer ward up to the passage
		int rise = p.li - p.lo;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int k = 1; k <= rise; k++) {
				int z = p.cz - 1 - (rise - k) - 1;
				int y = p.lo + k - 1;
				for (int x = c - 1; x <= c + 1; x++) {
					for (int yy = p.lo - 1; yy < y; yy++) {
						d.set(x, yy, z, m.wall.full());
					}
					d.set(x, y, z, m.paving.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					for (int yy = y + 1; yy <= y + 3; yy++) {
						d.set(x, yy, z, Blocks.AIR.defaultBlockState());
					}
				}
				// parapets either side of the ramp
				for (int x : new int[]{c - 2, c + 2}) {
					for (int yy = p.lo - 1; yy <= y; yy++) {
						d.set(x, yy, z, m.wall.full());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the palace

	/**
	 * The palace across the back of the inner ward, its back wall the back curtain's face: a main block of three storeys
	 * on the keep's side under a steep hipped roof with dormers and two chimneys (the kitchen below, the great hall on the
	 * first floor, the lord's chambers and the steward's room over it), a lower wing of two storeys on the other side with
	 * its gable to the court (the household's rooms), and a round stair turret on the court face where they meet.
	 */
	private static void palace(Draft d, Dress m, Manner man, Plan p) {
		int pz1 = p.bz;
		int x0 = p.lx + 3;
		int x1 = p.rx - 3;
		int len = x1 - x0;
		int xm = p.keepLeft ? x0 + len * 3 / 5 : x1 - len * 3 / 5;
		int mx0 = p.keepLeft ? x0 : xm;
		int mx1 = p.keepLeft ? xm : x1;
		int wx0 = p.keepLeft ? xm : x0;
		int wx1 = p.keepLeft ? x1 : xm;
		Direction toWing = p.keepLeft ? Dirs.RIGHT : Dirs.LEFT;
		int[] heights = {5, 7, 5};
		Range main = new Range(mx0, p.pz0, mx1, pz1, p.li, heights, Dirs.FRONT);
		main.blind(toWing.getOpposite());
		Range wing = new Range(wx0, p.pz0, wx1, pz1, p.li, new int[]{5, 5}, Dirs.FRONT);
		wing.blind(toWing.getOpposite() == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT);
		wing.blind(toWing.getOpposite());
		// the wing first, so the main block's walls stand over the wall they share
		wing.footings(d, m, true);
		wing.walls(d, m, man);
		wing.floors(d, m);
		main.footings(d, m, true);
		main.walls(d, m, man);
		main.floors(d, m);
		// doorways through the shared wall on the two floors they share
		int dzc = (p.pz0 + pz1) / 2;
		for (int k = 0; k < 2; k++) {
			int y = main.floorY(k);
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(xm, y, dzc, Blocks.AIR.defaultBlockState());
				d.set(xm, y + 1, dzc, Blocks.AIR.defaultBlockState());
				d.set(xm, y + 2, dzc, m.dress.full());
			}
			main.keepClear(xm, dzc, y, y + 2);
			wing.keepClear(xm, dzc, y, y + 2);
		}
		int dcol = (mx0 + mx1) / 2;
		int tcx = p.keepLeft ? xm - 3 : xm + 3;
		int tcz = p.pz0 - 3;
		main.door(d, m, Dirs.FRONT, dcol, 0, 2, false);
		main.keepClear(tcx, p.pz0, p.li, main.floorY(2) + 3);
		main.windows(d, m, man, 0, 2, 4, Dirs.BACK);
		main.windows(d, m, man, 1, 4, 3, null);
		main.windows(d, m, man, 2, 2, 3, null);
		wing.windows(d, m, man, 0, 2, 3, Dirs.BACK);
		wing.windows(d, m, man, 1, 2, 3, null);
		int wdoor = (wx0 + wx1) / 2;
		wing.door(d, m, Dirs.FRONT, wdoor, 0, 1, false);
		// the chimneys: the hall's hearth on the back wall of the first floor, the kitchen's below the far end
		int hx = p.keepLeft ? mx0 + 3 : mx1 - 3;
		int[] hall = main.hearth(d, hx, pz1, 1, Dirs.FRONT);
		int[] kitchen = main.hearth(d, p.keepLeft ? mx1 - 2 : mx0 + 2, pz1, 0, Dirs.FRONT);
		int dormers = Math.max(2, (mx1 - mx0) / 6);
		main.roof(d, m, man, man.crowSteps() ? RoofForm.GABLE : RoofForm.HIP, Direction.Axis.X, p.maxTop, dormers,
			List.of(new int[]{hall[0], hall[1]}, new int[]{kitchen[0], kitchen[1]}));
		int[] wh = wing.hearth(d, (wx0 + wx1) / 2 + 1, pz1, 1, Dirs.FRONT);
		wing.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop, 0, List.of(new int[]{wh[0], wh[1]}));
		// the stair turret on the court face of the main block, doors into each storey
		int ttop = Math.min(main.ridge - 2, p.maxTop - 6);
		// (a curved karst roof sweeps down three rows inside its eaves)
		ttop = Math.max(main.floorY(2) + 3 + (man.cap() == Manner.Cap.PAGODA ? 3 : 0), ttop);
		// its spiral turned so it arrives at each storey on the side toward the palace, where the doors are
		Tower stair = Turret.stair(d, m, man, tcx, tcz, 7, p.li, ttop, man.timberTops(), true, Dirs.BACK, main.floorY(1), main.floorY(2));
		stair.door(d, m, Dirs.FRONT, p.li, 1, true);
		for (int k = 0; k < heights.length; k++) {
			stair.door(d, m, Dirs.BACK, main.floorY(k), 1, false);
		}
		stair.loops(d, Set.of(Dirs.LEFT, Dirs.RIGHT), p.li + 2, ttop);
		stair.head(d, m, man, p.maxTop);
		stair.light(d, ttop - 1);
		// the rooms (the cell inside the turret's back door kept)
		int tdz = p.pz0 + 1;
		int[] g = main.room(0);
		Rooms.Room kitchenRoom = Chambers.room(g[0], g[1], g[2], g[3], g[4], new int[]{dcol, g[1]}, new int[]{tcx, tdz},
			new int[]{p.keepLeft ? g[2] : g[0], dzc});
		Chambers.furnish(d, kitchenRoom, Rooms.Type.KITCHEN, 0);
		int[] h = main.room(1);
		Rooms.Room hallRoom = Chambers.room(h[0], h[1], h[2], h[3], h[4], new int[]{tcx, tdz}, new int[]{p.keepLeft ? h[2] : h[0], dzc});
		Chambers.hall(d, m, hallRoom, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT, true);
		Range.hang(d, h, 4);
		int[] u = main.room(2);
		int us = (u[0] + u[2]) / 2;
		main.partition(d, m, 2, true, us, u[1] + 1);
		Rooms.Room lord = Chambers.room(p.keepLeft ? u[0] : us + 1, u[1], p.keepLeft ? us - 1 : u[2], u[3], u[4],
			new int[]{p.keepLeft ? us - 1 : us + 1, u[1] + 1});
		Chambers.niche(d, m, lord, 0, 0, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT);
		Chambers.furnish(d, lord, Rooms.Type.BEDROOM, 2);
		Rooms.Room steward = Chambers.room(p.keepLeft ? us + 1 : u[0], u[1], p.keepLeft ? u[2] : us - 1, u[3], u[4], new int[]{tcx, tdz},
			new int[]{p.keepLeft ? us + 1 : us - 1, u[1] + 1});
		Chambers.desk(d, m, steward, 0, 0, Dirs.BACK);
		Chambers.furnish(d, steward, Rooms.Type.BEDROOM, 1);
		int[] w0 = wing.room(0);
		Rooms.Room wr0 = Chambers.room(w0[0], w0[1], w0[2], w0[3], w0[4], new int[]{wdoor, w0[1]}, new int[]{p.keepLeft ? w0[0] : w0[2], dzc});
		Chambers.furnish(d, wr0, Rooms.Type.BEDROOM, 3);
		int[] w1 = wing.room(1);
		Rooms.Room wr1 = Chambers.room(w1[0], w1[1], w1[2], w1[3], w1[4], new int[]{p.keepLeft ? w1[0] : w1[2], dzc});
		if (hasChapel(p)) {
			Chambers.furnish(d, wr1, Rooms.Type.BEDROOM, 3);
		} else {
			// no room in the ward for a chapel: the palace chapel on the wing's upper floor, its altar at the far end
			int ax = p.keepLeft ? w1[2] - 1 : w1[0] + 1;
			int lx = p.keepLeft ? ax - 2 : ax + 2;
			Sacred.altar(d, ax, w1[4], w1[3], Dirs.FRONT, false, lx);
			int px0 = p.keepLeft ? ax - 3 : w1[0];
			int px1 = p.keepLeft ? w1[2] : ax + 3;
			Sacred.pews(d, px0, px1, w1[3] - 3, w1[1] + 1, w1[4], -1, Dirs.BACK, false);
			Chambers.furnish(d, wr1, Rooms.Type.BEDROOM, 1);
		}
		for (Range r : new Range[]{main, wing}) {
			for (int k = 0; k < r.storeys(); k++) {
				int[] rm = r.room(k);
				Lighting.room(d, rm[0], rm[1], rm[2], rm[3], rm[4]);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the chapel

	/** Whether the inner ward has room for a chapel of its own (else the palace wing keeps one upstairs). */
	static boolean hasChapel(Plan p) {
		return p.chz1 - p.chz0 >= 6;
	}

	private static void chapel(Draft d, Dress m, Manner man, Plan p) {
		if (!hasChapel(p)) {
			return;
		}
		int x0 = p.chx0;
		int x1 = p.chx1;
		int z0 = p.chz0;
		int z1 = p.chz1;
		Range r = new Range(x0, z0, x1, z1, p.li, new int[]{7}, p.keepLeft ? Dirs.LEFT : Dirs.RIGHT);
		r.blind(p.keepLeft ? Dirs.RIGHT : Dirs.LEFT);
		r.footings(d, m);
		r.walls(d, m, man);
		r.floors(d, m);
		int doorCol = (z0 + z1) / 2;
		Direction court = p.keepLeft ? Dirs.LEFT : Dirs.RIGHT;
		// the door on the court near the front, its first row kept clear of the pews
		r.door(d, m, court, z0 + 1, 0, 1, false);
		// tall windows down the court side and the end walls
		Manner.Window wk = man.window() == Manner.Window.MULLION ? Manner.Window.LANCET : man.window();
		var cs = r.side(court);
		for (int i = 4; i < cs.length() - 1; i += 3) {
			Range.window(d, m, wk, cs, i, p.li + 3, 3);
		}
		var back = r.side(Dirs.BACK);
		Range.window(d, m, wk, back, back.middle(), p.li + 3, 3);
		r.roof(d, m, man, RoofForm.GABLE, Direction.Axis.Z, p.maxTop, 0, List.of());
		// the ridge turret over the front gable: an open timber belfry under a spike
		int rx = (x0 + x1) / 2;
		int rz = z0 + 1;
		if (r.roof != null && r.roof.covers(rx, rz)) {
			int y = r.roof.surfaceY(rx, rz) + 1;
			belfry(d, m, man, rx, rz, y, p.maxTop);
		}
		// inside: the altar at the back, pews, the chaplain's lectern
		int[] in = r.room(0);
		Sacred.altar(d, (in[0] + in[2]) / 2, p.li, in[3], Dirs.FRONT, in[2] - in[0] >= 4, (in[0] + in[2]) / 2 + 2);
		Sacred.pews(d, in[0], in[2], in[3] - 3, in[1] + 2, p.li, (in[0] + in[2]) / 2, Dirs.BACK, false);
		Range.hang(d, in, 4);
		Lighting.room(d, in[0], in[1], in[2], in[3], in[4]);
	}

	/** A little open belfry riding a ridge: four posts on a block, a bell, a steep cap and a spike. */
	private static void belfry(Draft d, Dress m, Manner man, int x, int z, int y, int maxTop) {
		if (y + 6 > maxTop) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					d.set(x + dx, y, z + dz, m.wall.full());
				}
			}
			for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
				d.set(x + o[0], y + 1, z + o[1], Mason.post(m.frame));
				d.set(x + o[0], y + 2, z + o[1], Mason.post(m.frame));
			}
			d.set(x, y + 2, z, Blocks.BELL.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
		}
		Turret.cap(d, m, man, x, z, 3, y + 3, maxTop);
	}

	// ------------------------------------------------------------------------------------------------ the wards

	private static void outerWard(Draft d, Dress m, Manner man, Plan p) {
		// the stables against the side curtain away from the keep, the smithy against the other
		int z0 = p.fz + p.ot + 3;
		int z1 = p.cz - (p.keepLeft ? 2 : 2) - 1;
		boolean stablesRight = p.keepLeft;
		if (z1 - z0 >= 5) {
			int x1 = stablesRight ? p.rx - p.ot : p.lx + p.ot + 5;
			int x0 = stablesRight ? p.rx - p.ot - 5 : p.lx + p.ot;
			int[] door = new int[2];
			Rooms.Room room = Court.leanTo(d, m, man, x0, z0, x1, z1, p.lo, stablesRight ? Dirs.RIGHT : Dirs.LEFT, 4, door);
			// stalls: fences out from the wall, hay, a trough
			stalls(d, m, room, stablesRight ? Dirs.RIGHT : Dirs.LEFT);
			Lighting.room(d, room.x0, room.z0, room.x1, room.z1, p.lo);
			d.poi(PoiType.HITCHING_POST, door[0], p.lo, door[1], stablesRight ? Dirs.RIGHT : Dirs.LEFT);
		}
		// the smithy on the keep's side, before the keep's foot
		int sz1 = Math.min(z1, p.kcz - p.kd / 2 - 2);
		if (sz1 - z0 >= 4) {
			int x0 = stablesRight ? p.lx + p.ot : p.rx - p.ot - 4;
			int x1 = x0 + 4;
			Rooms.Room room = Court.leanTo(d, m, man, x0, z0, x1, sz1, p.lo, stablesRight ? Dirs.LEFT : Dirs.RIGHT, 4, new int[2]);
			Rooms.furnish(d, room, Rooms.Type.WORKSHOP, 0, JobType.SMITH);
			Lighting.room(d, room.x0, room.z0, room.x1, room.z1, p.lo);
		}
		// a well, archery butts, lamps
		int wx = p.keepLeft ? p.c - 5 : p.c + 5;
		int wz = (p.gz1 + p.cz) / 2 + 1;
		if (p.cz - p.gz1 >= 8) {
			Court.well(d, m, wx, wz, p.lo);
		}
		Court.lamps(d, p.lx + p.ot, p.fz + p.ot, p.rx - p.ot, p.cz - 1, p.lo);
	}

	/** Nothing placed in the box but air, over paving (open court). */
	static boolean open(Draft d, int x0, int z0, int x1, int z1, int y0, int y1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!Mason.inPlot(d, x, z) || !d.isSolid(x, y0 - 1, z)) {
					return false;
				}
				for (int y = y0; y <= y1; y++) {
					if (!d.isEmpty(x, y, z)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void stalls(Draft d, Dress m, Rooms.Room room, Direction wall) {
		var fence = d.mat(dev.voidmc.hearth.build.style.Role.FENCE);
		boolean alongZ = wall.getAxis() == Direction.Axis.X;
		int wx = wall == Dirs.RIGHT ? room.x1 : room.x0;
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (alongZ) {
				for (int z = room.z0 + 2; z < room.z1; z += 3) {
					int ix = wx - Dirs.dx(wall);
					if (d.isEmpty(wx, room.y, z) && Chambers.take(room, wx, z)) {
						d.set(wx, room.y, z, fence.fence());
						if (d.isEmpty(ix, room.y, z) && Chambers.take(room, ix, z)) {
							d.set(ix, room.y, z, fence.fence());
						}
					}
				}
				for (int z = room.z0; z <= room.z1; z++) {
					if ((z - room.z0) % 3 != 2 && d.isEmpty(wx, room.y, z) && Chambers.take(room, wx, z)) {
						d.set(wx, room.y, z, (z - room.z0) % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState()
							: Blocks.COMPOSTER.defaultBlockState());
					}
				}
			}
		}
		// the tack chest against the front wall, wherever it blocks no door
		int cx = wall == Dirs.RIGHT ? room.x0 : room.x1;
		for (int z = room.z0; z <= room.z1; z++) {
			int fx = cx + Dirs.dx(wall);
			if (d.isEmpty(cx, room.y, z) && d.isEmpty(fx, room.y, z) && room.inside(fx, z) && Chambers.take(room, cx, z)) {
				room.keep(fx, z);
				Furniture.chest(d, cx, room.y, z, wall, List.of(new Object[]{"minecraft:saddle", 2}, new Object[]{"minecraft:lead", 3},
					new Object[]{"minecraft:wheat", 16}));
				break;
			}
		}
	}

	private static void innerWard(Draft d, Dress m, Manner man, Plan p) {
		// a well and a lime tree in the inner court, lamps round it
		int cx = (p.lx + p.rx) / 2 + (p.keepLeft ? 2 : -2);
		int cz = (p.cz + p.it + p.pz0) / 2;
		if (p.pz0 - (p.cz + p.it) >= 9) {
			Court.well(d, m, cx, cz - 1, p.li);
		}
		int tx = p.keepLeft ? p.kcx + p.kd / 2 + 4 : p.kcx - p.kd / 2 - 4;
		int tz = p.pz0 - 4;
		if (Mason.inPlot(d, tx, tz) && open(d, tx - 2, tz - 2, tx + 2, tz + 2, p.li, p.li + 7)) {
			Garden.tree(d, tx, p.li, tz, 5);
		}
		Court.lamps(d, p.lx + p.it, p.cz + p.it, p.rx - p.it, p.pz0 - 1, p.li);
		d.poi(PoiType.MEETING_POINT, cx, p.li, cz + 1, Dirs.FRONT);
	}
}
