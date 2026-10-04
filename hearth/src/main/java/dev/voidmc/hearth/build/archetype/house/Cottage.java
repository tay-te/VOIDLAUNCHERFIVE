package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Logs;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:house/cottage}: a one-and-a-half storey cottage with its garden, the house most settlements are made of.
 * A ground floor of living room and kitchen round a hearth (a bedroom off it when there is room), a bedroom in the roof
 * lit by dormers or gable windows and reached by a stair, a chimney, a front garden with a path to the door, a fence and
 * gate, and a yard with a woodpile and barrels. Roof form, door position, porch, bay window, dormers, chimney side,
 * window rhythm and garden all vary with the seed within the culture's habits; flat-roofed cultures get a roof terrace.
 * Log walls are peeled (the bark left on the corner posts), so northern cottages read warm rather than dark.
 *
 * <p><b>Split level.</b> On a hillside plot (the ground three or more blocks higher or lower at the back than at the
 * street) the cottage uses the slope instead of flattening it: where the land falls away behind, the front door opens
 * from the street into the main floor and a lower storey opens through a back door onto a terrace at the lower ground;
 * where the land rises behind, the street door opens into the lower storey, cut into the bank, and the floor above has a
 * side door out at grade onto the garden up the hill.
 *
 * <p>Params (all optional):
 * <ul>
 *   <li>{@code storeys} (int, 1–2): full storeys under the roof (default: 1, or 2 for flat roofs on big plots)</li>
 *   <li>{@code garden} (boolean, default true): lay out the front garden</li>
 *   <li>{@code outshot} (boolean): a lean-to outshot behind, on plots 10+ deep (default by seed)</li>
 *   <li>{@code split} (boolean, default true): build split level where the plot falls or rises three or more</li>
 * </ul>
 */
public final class Cottage implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/cottage"), ArchetypeCategory.HOUSING, Set.of(), 2,
		7, 11, 7, 11, 6, 16, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.HILLSIDE),
		List.of(Hearth.id("house/rowhouse"), Hearth.id("house/shophouse"), Hearth.id("house/townhouse")), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int rise = d.site().riseToBack();
		boolean hillside = Math.abs(rise) >= 3 && d.depth() >= 8 && !d.param("terraced", false) && d.param("split", true)
			&& !d.site().anyWater(0, 0, d.frontage() - 1, d.depth() - 1);
		if (!hillside || !splitLevel(d, rise)) {
			HouseBuilder.cottage(d);
			Logs.sillAll(d, 0);
		}
		Logs.peelAll(d, d.maxHeight() - 1);
	}

	/**
	 * The split-level cottage. Returns false (drawing nothing) where the plot cannot take it (too little height for two
	 * storeys and a roof, or a flat-roofed culture), so the caller draws the ordinary cottage.
	 */
	private static boolean splitLevel(Draft d, int rise) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean bohemian = dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d);
		RoofForm form = HouseBuilder.roofParam(d, bohemian ? dev.voidmc.hearth.build.kit.homestead.Bohemia.form(d)
			: h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.CROSS_GABLE, RoofForm.PAGODA, RoofForm.SALTBOX));
		if (form == RoofForm.FLAT) {
			return false;
		}
		boolean falls = rise < 0;
		int sh = Math.max(4, h.walls().storeyHeight());
		int sideL = w >= 9 ? 1 : 0;
		int sideR = w >= 9 ? 1 : 0;
		int front = dp >= 10 ? 2 : 1;
		int back = falls ? Math.min(2, dp - front - 6) : Math.min(2, dp - front - 6);
		int x0 = sideL;
		int x1 = w - 1 - sideR;
		int z0 = front;
		int z1 = dp - 1 - Math.max(0, back);
		int bw = x1 - x0 + 1;
		int bd = z1 - z0 + 1;
		if (bw < 7 || bd < 6) {
			return false;
		}
		int base = falls ? -sh : 0;
		Body body = new Body(x0, z0, x1, z1, base, new int[]{sh, sh}, false, false);
		Direction.Axis ridge = bw >= bd ? Direction.Axis.X : Direction.Axis.Z;
		if (d.chance(h.roof().frontGable())) {
			ridge = Direction.Axis.Z;
		}
		// the roof must clear the plot's height
		double pitch = Math.max(0.5, h.roof().pitch());
		int span = ridge == Direction.Axis.X ? bd : bw;
		int top = body.plateY() + (int) Math.ceil(pitch * (span / 2.0 + 1)) + 3;
		if (top >= d.maxHeight()) {
			return false;
		}
		d.body(x0, z0, x1, z1);
		d.eave(body.plateY());
		int doorCol = x0 + (bw - 1) / 2 + (bw >= 8 ? d.pick(-1, 0, 1) : 0);
		Facade facade = Facade.plan(d, body, doorCol, 1, h.windows().tall() && bw >= 8 && d.chance(0.35) ? 2 : 1, 0.35);
		Integer door = HouseBuilder.doorColumn(facade);
		int entry = falls ? 1 : 0;
		int dx = door != null ? door : doorCol;

		// ground: footings into the slope, the street garden level with the street
		Groundwork.footings(d, x0, z0, x1, z1, base - 1);
		if (!d.site().anyWater(0, 0, w - 1, z0 - 1)) {
			Groundwork.level(d, 0, 0, w - 1, z0 - 1, 0);
		}
		HouseBuilder.shell(d, body, facade);
		// the lower storey's way out: a back door onto a terrace (falling), a side door at grade up the hill (rising)
		int[] keep = null;
		int bdx = Integer.MIN_VALUE;
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		if (falls) {
			bdx = d.chance(0.5) ? ix0 + 1 : ix1 - 1;
			keep = new int[]{bdx, z1 - 1};
		}
		openings(d, body, facade, entry, dx, falls, bdx);
		// the roof
		Roofs.Roof roof = HouseBuilder.pitchedRoof(d, body, form, ridge, facade, 1);
		if (bohemian) {
			if (form != RoofForm.HIP) {
				dev.voidmc.hearth.build.kit.homestead.Bohemia.gables(d, roof, ridge, body.topRect(), body.plateY());
			}
			// the lower storey's walls stand on fieldstone where they meet the ground
			dev.voidmc.hearth.build.kit.homestead.Bohemia.fieldstone(d, x0, z0, x1, z1, base, base + Math.max(0, h.walls().plinth() - 1));
		}
		// the hearth in the lower storey's gable end (or back wall), its stack up through the roof
		Direction chimneySide = ridge == Direction.Axis.X ? (d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT) : Dirs.BACK;
		int[] hearth = HouseBuilder.hearthSpot(d, body, chimneySide, facade);
		if (hearth != null && keep != null && Math.abs(hearth[0] - keep[0]) <= 1 && Math.abs(hearth[1] - keep[1]) <= 1) {
			hearth = null;
		}
		if (hearth != null && d.chance(Math.max(0.6, h.chimney()))) {
			Furniture.hearth(d, hearth[0], base, hearth[1], hearth[2] == 0 ? Dirs.RIGHT : hearth[2] == 1 ? Dirs.LEFT : Dirs.FRONT, false);
			Chimneys.stack(d, hearth[0], hearth[1], base + 2, roof, roof.ridgeY + 1);
		} else {
			hearth = null;
		}
		boolean runsRight = hearth != null ? hearth[2] == 1 : d.chance(0.5);
		HouseBuilder.interior(d, body, facade, roof, hearth, keep, runsRight);
		FarmParts.ensureBeds(d, body, falls ? null : door, 2, keep);
		if (!falls) {
			sideDoor(d, body, sh);
		} else {
			backTerrace(d, body, bdx, base);
		}
		// the street side: path, garden, fence
		HouseBuilder.garden(d, upperFrontBody(body, falls), facade, front);
		if (bohemian) {
			HouseBuilder.bohemianYard(d, upperFrontBody(body, falls), door);
		}
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, 0, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 2));
		return true;
	}

	/** For the garden: a body whose ground storey is the one the street door is in (so the path meets the door's level). */
	private static Body upperFrontBody(Body b, boolean falls) {
		if (!falls) {
			return b;
		}
		return new Body(b.x0, b.z0, b.x1, b.z1, b.floorY(1), new int[]{b.height(1)}, false, false);
	}

	/**
	 * Doors and windows: the street door in storey {@code entry}'s front wall, windows wherever the wall stands clear of
	 * the ground outside (a wall cut into the slope keeps its blind face), the back door of a lower storey.
	 */
	private static void openings(Draft d, Body b, Facade f, int entry, int door, boolean falls, int backDoor) {
		Habits.Windows hw = d.habits().windows();
		Openings.WindowStyle style = hw.lattice() ? Openings.WindowStyle.LATTICE
			: d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
		int winH = hw.tall() ? 2 : 1;
		for (int k = 0; k < b.storeys(); k++) {
			int y = b.floorY(k);
			for (Side s : b.sides(k)) {
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					boolean isDoor = o.kind() == Facade.Kind.DOOR && s.out() == Dirs.FRONT && k == entry;
					if (isDoor) {
						Openings.door(d, s, o.at(), y, 1, Openings.DoorOpts.MAIN);
						continue;
					}
					// a window only where the ground outside lies below its sill
					int i = s.index(o.at());
					int gx = s.outX(i, 1);
					int gz = s.outZ(i, 1);
					if (d.site().ground(gx, gz) > y) {
						continue;
					}
					if (k == 0 && s.out() == Dirs.BACK && backDoor != Integer.MIN_VALUE && Math.abs(o.at() - backDoor) <= 1) {
						continue;
					}
					Openings.window(d, s, o.at(), y + 1, o.width(), Math.min(winH, b.height(k) - 2), style);
				}
			}
		}
		if (backDoor != Integer.MIN_VALUE) {
			Side back = b.side(0, Dirs.BACK);
			Openings.door(d, back, backDoor, b.floorY(0), 1, new Openings.DoorOpts(true, true, false));
		}
	}

	/**
	 * Rising ground: a door out of the upper storey through the side wall nearest the hill, where the ground outside
	 * stands at the upper floor, a landing made good outside it.
	 */
	private static void sideDoor(Draft d, Body b, int sh) {
		int y = b.floorY(1);
		int[] r = b.rect(1);
		for (int z = r[3] - 1; z > r[1]; z--) {
			for (Side s : new Side[]{b.side(1, Dirs.LEFT), b.side(1, Dirs.RIGHT)}) {
				int i = s.index(z);
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (ox < 0 || ox >= d.frontage() || !Openings.isWallBlock(d, s.x(i), y, s.z(i)) || !Openings.isWallBlock(d, s.x(i), y + 1, s.z(i))) {
					continue;
				}
				int g = d.site().ground(ox, oz);
				if (Math.abs(g - y) > 1) {
					continue;
				}
				// the inside cell must be free floor
				int ix = s.x(i) - Dirs.dx(s.out());
				if (!Spots.standable(d, ix, y, z)) {
					continue;
				}
				Openings.door(d, s, z, y, 1, new Openings.DoorOpts(true, false, false));
				Groundwork.level(d, ox, oz, ox, oz, y);
				d.inStage(StageKind.EXTERIOR, () -> d.set(ox, y - 1, oz, d.mat(Role.PATH).full()));
				return;
			}
		}
	}

	/** Falling ground: a little terrace behind at the lower storey's floor, its door, a bench and pots, steps to the garden. */
	private static void backTerrace(Draft d, Body b, int door, int base) {
		int[] r = b.rect(0);
		int z0 = r[3] + 1;
		int z1 = d.depth() - 1;
		if (z1 < z0) {
			return;
		}
		Groundwork.level(d, r[0], z0, r[2], z1, base);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = r[0]; x <= r[2]; x++) {
					if (Spots.standable(d, x, base, z)) {
						d.set(x, base - 1, z, Noise.unit(d.seed() ^ 0xBA7, x, 0, z) < 0.6 ? d.mat(Role.PAVING).full() : d.mat(Role.PATH).full());
					}
				}
			}
		}
		for (int x = r[0]; x <= r[2]; x++) {
			if (Math.abs(x - door) <= 1 || x == r[0] || x == r[2]) {
				continue;
			}
			if (Spots.standable(d, x, base, z0) && d.isAbsent(x, base, z0) && Noise.unit(d.seed() ^ 0xBA8, x, 0, z0) < 0.35) {
				Garden.pot(d, x, base, z0);
			}
		}
		int bx = door <= (r[0] + r[2]) / 2 ? door + 2 : door - 2;
		if (Spots.standable(d, bx, base, z0) && d.isAbsent(bx, base, z0)) {
			Furniture.chair(d, bx, base, z0, Dirs.BACK);
			d.poi(PoiType.VIEWPOINT, bx, base, z0, Dirs.BACK);
		}
	}
}
