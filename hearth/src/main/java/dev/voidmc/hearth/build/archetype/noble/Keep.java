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
import dev.voidmc.hearth.build.kit.Rooms;
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
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:noble/keep}: the lord's seat in a town. A tower keep at a back corner of a walled bailey, the tallest
 * thing in town: its ground storey the stores, over it the hall with the dais and the throne under the lord's banners,
 * then his bedchamber with its chapel niche, the steward's counting room with the strongbox above that, the guards' head
 * on top under the culture's cap, the lord's flag flying from it. The bailey's curtain carries a walk behind its
 * parapet, round turrets on its free corners; a gate tower stands out over the way in at the front, its guards' hall over
 * the passage, the lord's arms over the arch; a range of two storeys runs along the back wall from the keep (the kitchen,
 * the household's beds); a stable shed and a well in the court.
 *
 * <p>The keep's form follows the culture ({@link Manner}): a rendered Bohemian tower under a hoarded cap, a Tyrolean one
 * under an onion, a harled Scottish tower house with bartizans and crow steps, granite under machicolations, a northern
 * stone tower with its timber head, a kasbah's tower with its pointed merlons, a karst diaolou under a curved roof...
 *
 * <p>Raised in three campaigns ({@link Draft#campaign(int)}): the keep, then the curtain and gate, then the range and the court.
 *
 * <p>Params: {@code station} (the planner's {@code lord}), {@code keep_side} ({@code left} or {@code right}, seeded),
 * {@code round} (boolean: a round keep, seeded by the manner), {@code variant}.
 */
public final class Keep implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("noble/keep"), ArchetypeCategory.NOBLE,
		Set.of(JobType.LORD, JobType.STEWARD, JobType.GUARD), 6, 17, 25, 17, 25, 24, 34, SettlementTier.TOWN, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(Hearth.id("noble/castle"), Hearth.id("noble/palace")), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxTop = d.maxHeight() - 1;
		Manner man = Manner.of(d);
		Dress m = Dress.of(d);
		boolean left = d.param("keep_side", d.chance(0.5) ? "left" : "right").equals("left");
		boolean round = d.param("round", man.culture() == Culture.STEPPE || man.culture() != Culture.MOORLAND && d.chance(man.round()));
		int lx = 1;
		int rx = w - 2;
		int fz = 1;
		int bz = dp - 2;
		int thick = 2;
		int court = Court.level(d, lx + thick, fz + thick, rx - thick, bz - thick, -1, 1);
		int walk = court + 5;
		int c = (w - 1) / 2;
		d.body(lx, fz, rx, bz);
		d.eave(walk + 8);
		// the gate stands out over the front of the plot; behind it at least two rows of court run the whole width
		int gz1 = fz + 3;
		int kd = Math.min(round ? (Math.min(w, dp) >= 21 ? 11 : 9) : 9, bz - (gz1 + 3) + 1);
		kd = Math.max(7, kd - (kd % 2 == 0 ? 1 : 0));
		int kx0 = left ? lx : rx - kd + 1;
		int kx1 = kx0 + kd - 1;
		int kz1 = bz;
		int kz0 = bz - kd + 1;
		Manner.Cap cap = man.keepCap();
		// storeys: the stores, the hall (tall), the bedchamber, the counting room, the guards; as many as the height allows
		int minHead = (int) Math.ceil(kd / 2.0 + 2) + 4 + (cap == Manner.Cap.HOARD ? 2 : 0);
		if (cap == Manner.Cap.CRENEL || cap == Manner.Cap.DOME) {
			minHead = 4;
		}
		int[] want = {5, 6, 4, 4, 4, 4};
		int room = maxTop - court - minHead;
		int n = 0;
		int sum = 0;
		while (n < want.length && sum + want[n] <= room) {
			sum += want[n];
			n++;
		}
		n = Math.max(3, n);
		int[] heights = java.util.Arrays.copyOf(want, n);
		int top = court;
		for (int h : heights) {
			top += h;
		}
		int storeys = n;
		int[] levels = new int[storeys - 1];
		int acc = court;
		for (int k = 1; k < storeys; k++) {
			acc += heights[k - 1];
			levels[k - 1] = acc;
		}

		// ---- campaign 0: the site, and the keep
		try (var ph = d.campaign(0)) {
			Groundwork.level(d, lx + thick, fz + thick, rx - thick, bz - thick, court, true);
			Court.pave(d, m, lx + thick, fz + thick, rx - thick, bz - thick, court);
			// the curtain is the second campaign's, but drawn first so the keep and the turrets stand over it
			try (var ph1 = d.campaign(1)) {
				Curtain.Opts o = new Curtain.Opts(false, true, true);
				Curtain.run(d, m, man, lx, fz, rx, fz, Dirs.FRONT, thick, walk, o);
				Curtain.run(d, m, man, rx, fz, rx, bz, Dirs.RIGHT, thick, walk, o);
				Curtain.run(d, m, man, lx, bz, rx, bz, Dirs.BACK, thick, walk, o);
				Curtain.run(d, m, man, lx, fz, lx, bz, Dirs.LEFT, thick, walk, o);
			}
			Tower keep = round ? Tower.round(kx0 + kd / 2, kz0 + kd / 2, kd, court, top, cap) : Tower.square(kx0, kz0, kx1, kz1, court, top, cap);
			keep.footings(d, m);
			keep.shell(d, m, top - 1, levels[0], top);
			keep.stair(d, m, man.timberTops(), left ? Dirs.FRONT : Dirs.RIGHT);
			keep.floors(d, m, top, levels);
			// the door from the court, facing the gate
			keep.door(d, m, Dirs.FRONT, court, 1, true);
			int[] fc = keep.face(Dirs.FRONT);
			Lighting.doorLamp(d, fc[0] + 1, court + 2, fc[1] - 1, Dirs.BACK);
			// slits in the stores, tall windows in the hall, glass in the chambers on the court and the field
			Direction field = left ? Dirs.LEFT : Dirs.RIGHT;
			Direction inner = field.getOpposite();
			keep.loops(d, Set.of(field, Dirs.BACK), court + 1, court + 3);
			keep.window(d, m, Dirs.FRONT, levels[0] + 1, 3);
			keep.window(d, m, inner, levels[0] + 1, 3);
			for (int k = 2; k < storeys; k++) {
				int y = levels[k - 1] + 1;
				keep.window(d, m, k % 2 == 0 ? Dirs.FRONT : inner, y, 2);
				keep.window(d, m, k % 2 == 0 ? field : Dirs.BACK, y, 2);
			}
			keep.loops(d, Set.of(field, Dirs.BACK), levels[0] + 1, levels[0] + 2);
			// the head, the lord's flag flying from it
			keep.flag(left ? Dirs.RIGHT : Dirs.LEFT);
			int hi = keep.head(d, m, man, maxTop);
			if (man.bartizans() && !round) {
				hi = Math.max(hi, keep.bartizans(d, m, man, top, maxTop));
			}
			furnishKeep(d, m, keep, levels, court, left);
			keep.light(d, top - 1);
			// the lord's flag on an open head
			int[] st = keep.stairTop();
			if (cap == Manner.Cap.CRENEL || cap == Manner.Cap.DOME) {
				int px = keep.cx + (left ? -1 : 1);
				int pz = keep.cz - 1;
				if (st != null && px == st[0] && pz == st[1]) {
					pz = keep.cz + 1;
				}
				Flag.pole(d, m, px, top, pz, 5, left ? Dirs.RIGHT : Dirs.LEFT, 3, maxTop);
			}
			keep.watch(d, Dirs.FRONT);
		}

		// ---- campaign 1: the turrets and the gate (the curtain was drawn before the keep, so the keep stands over it)
		try (var ph = d.campaign(1)) {
			// round turrets on the corners the keep leaves
			if (w >= 19) {
				Turret.wall(d, m, man, lx + 1, fz + 1, 5, walk, maxTop, Set.of(Dirs.RIGHT, Dirs.BACK));
				Turret.wall(d, m, man, rx - 1, fz + 1, 5, walk, maxTop, Set.of(Dirs.LEFT, Dirs.BACK));
				if (dp >= 19) {
					Turret.wall(d, m, man, left ? rx - 1 : lx + 1, bz - 1, 5, walk, maxTop, Set.of(left ? Dirs.LEFT : Dirs.RIGHT, Dirs.FRONT));
				}
			}
			// the gate tower over the front wall, standing out over the plot's front row
			int gx0 = c - 3;
			int gx1 = c + 3;
			int gTop = Math.min(walk + 6, maxTop - Math.min(8, Tower.headRows(man.cap(), 7, man)));
			gTop = Math.max(walk + 3, gTop);
			Gate.tower(d, m, man, gx0, 0, gx1, gz1, 3, court, walk, gTop, maxTop);
			if (court > 0) {
				// a step up into the passage from the street
				try (var s = d.stage(StageKind.FLOORS)) {
					for (int x = c - 1; x <= c + 1; x++) {
						d.set(x, court - 1, 0, m.paving.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					}
				}
			}
			d.entrance(c, 0, -1);
			Flag.banner(d, m, c, walk + 2, -1, Dirs.FRONT, 0);
			// steps up from the court to the walk, along the inner face of the front wall
			int sx = w >= 19 ? (left ? rx - thick - 2 : lx + thick + 2) : (left ? rx - thick : lx + thick);
			Direction up = left ? Dirs.LEFT : Dirs.RIGHT;
			Curtain.flight(d, m, sx, fz + thick, up, court, walk);
			Curtain.posts(d, lx, fz, rx, fz, Dirs.FRONT, thick, walk, 6);
		}

		// ---- campaign 2: the range along the back wall, the court
		int rz0 = Math.max(gz1 + 3, bz - 6);
		try (var ph = d.campaign(2)) {
			int rx0 = left ? kx1 : lx;
			int rx1 = left ? rx : kx0;
			if (rx1 - rx0 >= 8 && bz - rz0 >= 4) {
				Range r = new Range(rx0, rz0, rx1, bz, court, new int[]{5, 4}, Dirs.FRONT);
				r.blind(Dirs.BACK);
				r.blind(left ? Dirs.LEFT : Dirs.RIGHT);
				// the flight along the back wall, climbing away from the keep
				int f0 = left ? rx1 - 1 : rx0 + 1;
				Direction fdir = left ? Dirs.LEFT : Dirs.RIGHT;
				r.flight(f0, bz - 1, 0, fdir);
				r.footings(d, m);
				r.walls(d, m, man);
				r.floors(d, m);
				r.flights(d, m);
				int dcol = (rx0 + rx1) / 2;
				r.door(d, m, Dirs.FRONT, dcol, 0, 1, false);
				r.windows(d, m, man, 0, 2, 3, null);
				r.windows(d, m, man, 1, 2, 3, null);
				int hx = left ? rx0 + 2 : rx1 - 2;
				int[] hearth = r.hearth(d, hx, bz, 0, Dirs.FRONT);
				r.roof(d, m, man, man.crowSteps() ? RoofForm.GABLE : RoofForm.HIP, Direction.Axis.X, maxTop, 2, List.of(new int[]{hearth[0], hearth[1]}));
				int[] room0 = r.room(0);
				int land = f0 + Dirs.dx(fdir) * 5;
				Rooms.Room k0 = Chambers.room(room0[0], room0[1], room0[2], room0[3], room0[4], new int[]{dcol, rz0 + 1}, new int[]{f0, bz - 2});
				k0.keepArea(f0, bz - 1, land, bz - 1);
				k0.keep(hx, bz - 1);
				Chambers.furnish(d, k0, Rooms.Type.KITCHEN, 0);
				int[] room1 = r.room(1);
				Rooms.Room k1 = Chambers.room(room1[0], room1[1], room1[2], room1[3], room1[4], new int[]{land, bz - 1});
				k1.keepArea(f0, bz - 1, land, bz - 1);
				Chambers.furnish(d, k1, Rooms.Type.BEDROOM, 3);
				Range.hang(d, room0, 4);
				Lighting.room(d, room0[0], room0[1], room0[2], room0[3], room0[4]);
				Lighting.room(d, room1[0], room1[1], room1[2], room1[3], room1[4]);
			}
			// the court: a stable shed against a side wall, a well, lamps
			int sx0 = left ? rx - thick - 3 : lx + thick;
			int sx1 = sx0 + 3;
			int sz0 = gz1 + 1 + (w >= 19 ? 2 : 0);
			int sz1 = Math.min(rz0 - 2, sz0 + 6);
			if (sz1 - sz0 >= 3) {
				Court.stable(d, m, man, sx0, sz0, sx1, sz1, court, left ? Dirs.RIGHT : Dirs.LEFT);
			}
			if (rz0 - gz1 >= 6) {
				Court.well(d, m, c + (left ? 2 : -2), (gz1 + rz0) / 2, court);
			}
			Court.lamps(d, lx + thick, fz + thick, rx - thick, rz0 - 1, court);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxTop);
	}

	/** The keep's storeys: stores, the hall with the throne, the bedchamber and chapel, the counting room, the guards. */
	private static void furnishKeep(Draft d, Dress m, Tower keep, int[] levels, int court, boolean left) {
		int storeys = levels.length + 1;
		boolean deskPlaced = false;
		for (int k = 0; k < storeys; k++) {
			int y = k == 0 ? court : levels[k - 1];
			Rooms.Room r = keep.room(y);
			switch (k) {
				case 0 -> Rooms.furnish(d, r, Rooms.Type.WORKSHOP, 0, JobType.GUARD);
				case 1 -> {
					Chambers.hall(d, m, r, Dirs.BACK, r.width() >= 9);
					if (storeys < 4) {
						Chambers.desk(d, m, r, r.x0, (r.z0 + r.z1) / 2, Dirs.LEFT);
						deskPlaced = true;
					}
				}
				case 2 -> {
					Chambers.niche(d, m, r, left ? r.x0 : r.x1, r.z0 + 1, left ? Dirs.LEFT : Dirs.RIGHT);
					Chambers.furnish(d, r, Rooms.Type.BEDROOM, 2);
				}
				case 3 -> {
					Chambers.desk(d, m, r, (r.x0 + r.x1) / 2, r.z0, Dirs.FRONT);
					deskPlaced = true;
					Chambers.furnish(d, r, Rooms.Type.BEDROOM, 1);
				}
				default -> Chambers.furnish(d, r, Rooms.Type.BEDROOM, 2);
			}
		}
		if (!deskPlaced) {
			Rooms.Room r = keep.room(court);
			Chambers.desk(d, m, r, r.x0, (r.z0 + r.z1) / 2, Dirs.LEFT);
		}
		d.poi(PoiType.GUARD_POST, (keep.x0 + keep.x1) / 2, court, keep.z0 - 1, Dirs.FRONT);
	}
}
