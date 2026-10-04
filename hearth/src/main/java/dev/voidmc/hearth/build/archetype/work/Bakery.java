package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Forge;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:work/bakery}: the baker's shop on the high street. A shop of one or two storeys (built into a row with
 * blind party walls where the street is dense) with a wide shop window under a cloth awning, cakes and loaves set out
 * on its sill, the door beside it under a hanging sign. Inside, a counter across the shop, the baker behind it; behind
 * them a vault of brick ovens against the back wall whose stack smokes over the roof, sacks of flour, racks of bread,
 * the kneading board. Upstairs a flour loft. A freestanding bakery keeps a side yard with a domed bread oven (where the
 * culture bakes outdoors), a woodpile for the ovens and a back door to it.
 *
 * <p>Job sites (BAKER): each oven, and the yard oven's mouth. Storage: the flour chest. A COUNTER where the baker
 * serves.
 *
 * <p>Params: {@code party_left}, {@code party_right} (boolean, shared walls), {@code storeys} (1–2), {@code roof},
 * {@code variant}.
 */
public final class Bakery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/bakery"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BAKER), 0,
		7, 10, 7, 10, 8, 13, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.CROSS_GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA,
		RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean attached = partyL || partyR;
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;

		// the plot: the shop to the street; a side yard for the yard oven where the bakery stands free and broad
		int fz = dp >= 9 ? 2 : 1;
		int side = !attached && w >= 9 ? 3 : 0;
		boolean yardLeft = d.chance(0.5);
		int hx0 = yardLeft ? side : 0;
		int hx1 = yardLeft ? w - 1 : w - 1 - side;
		int hz0 = fz;
		// the oven's hump stands out of the back wall where the plot runs deep enough
		boolean hump = dp - fz >= 7;
		int hz1 = hump ? dp - 2 : dp - 1;
		int sh = Math.max(4, h.walls().storeyHeight());
		int want = Math.clamp(d.param("storeys", d.maxHeight() >= sh * 2 + 4 ? 2 : 1), 1, 2);
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, want == 2 ? new int[]{sh + 1, sh} : new int[]{sh + 1}, false, form, 2);
		if (heights.length == 1 && !flat) {
			// one storey: a flour loft in the roof over it
			heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, new int[]{sh + 1}, true, form, 2);
		}
		int storeys = heights.length >= 2 && heights[1] > 2 ? 2 : 1;
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, partyL && hx0 == 0, partyR && hx1 == w - 1);
		if (storeys == 2 && !attached && d.chance(h.walls().jetty())) {
			hall.body.jetty(1, Dirs.FRONT);
		}
		int[] in = hall.inner(0);
		// the door to one side, the shop window across the rest
		boolean doorLeft = attached ? partyR && !partyL || !partyL && d.chance(0.5) : yardLeft;
		int door = doorLeft ? in[0] : in[2];
		hall.facade(door, 1, 1, 0.5);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.ground(false).shell();
		// the shop window: two or three wide, beside the door
		int[] span = Shop.span(in[0], in[2], door, 3);
		int win = span != null ? span[0] : door;
		int winW = span != null ? span[1] : 0;
		hall.openings(true);
		Side front = hall.side(0, Dirs.FRONT);
		if (span != null) {
			Shop.window(d, front, win, winW, 0, i -> wares(d, i), !flat || d.chance(0.7));
		}

		// roof (two above for the oven stack)
		Direction.Axis ridge = attached || d.chance(h.roof().frontGable()) ? Direction.Axis.Z : Direction.Axis.X;
		if (attached && hall.inner(0)[2] - hall.inner(0)[0] >= 6 && d.chance(0.4)) {
			ridge = Direction.Axis.X;
		}
		hall.roof(form, ridge, 2, null);
		if (flat) {
			hall.crown();
		}

		// the ovens: a brick vault against the back wall, the stack up through the roof
		int n = in[2] - in[0] + 1 >= 5 ? 3 : 2;
		int ox = (in[0] + in[2]) / 2 - n / 2;
		if (!doorLeft && ox + n - 1 >= in[2]) {
			ox = in[2] - n;
		}
		int stackTop = hall.roof != null ? hall.roof.ridgeY + 2 : hall.top + 2;
		int[][] ovens = Forge.ovens(d, ox, 0, in[3], Dirs.RIGHT, Dirs.FRONT, n, hump ? Integer.MIN_VALUE : stackTop, hall.roof);
		if (hump) {
			int top = Forge.bulge(d, ox, ox + n - 1, hz1 + 1, hz1, 0, stackTop, hall.roof);
			hall.top = Math.max(hall.top, top);
		}
		for (int[] o : ovens) {
			if (d.get(o[0], 0, o[1]) != null && d.get(o[0], 0, o[1]).is(Blocks.SMOKER)) {
				d.poi(PoiType.JOB_SITE, o[0], 0, o[1] - 1, Dirs.BACK, JobType.BAKER);
			}
		}

		interior(d, hall, in, door, ovens, storeys);
		// the street front: a bench under the window, the sign by the door, a lamp
		int signCol = doorLeft ? door + 1 : door - 1;
		if (!Signs.bracket(d, front, front.index(signCol), 3, "bakery", "Bakery")) {
			Signs.bracket(d, front, front.index(doorLeft ? door - 1 : door + 1), 3, "bakery", "Bakery");
		}
		Yard.pave(d, 0, 0, w - 1, fz - 1, 0);
		if (fz >= 2) {
			int bx = win + winW / 2;
			if (d.isEmpty(bx, 0, hz0 - 1) && Math.abs(bx - door) > 1) {
				Furniture.chair(d, bx, 0, hz0 - 1, Dirs.BACK);
			}
		}
		if (side > 0) {
			sideYard(d, hall, yardLeft ? 0 : hx1 + 1, yardLeft ? side - 1 : w - 1, hz0, dp - 1, yardLeft);
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	private static BlockState wares(Draft d, int i) {
		return switch ((int) ((d.seed() >>> 3) + i) % 4) {
			case 0 -> Blocks.CAKE.defaultBlockState();
			case 1 -> Blocks.HAY_BLOCK.defaultBlockState();
			case 2 -> States.facing(Blocks.DECORATED_POT, Dirs.FRONT);
			default -> Blocks.CANDLE_CAKE.defaultBlockState();
		};
	}

	/** The shop and the bakehouse: counter, racks of bread, sacks of flour, the kneading board; the flour loft above. */
	private static void interior(Draft d, Hall hall, int[] in, int door, int[][] ovens, int storeys) {
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		for (int[] o : ovens) {
			fp.block(o[0], o[1]);
			fp.keep(o[0], o[1] - 1);
		}
		// the shop window's sill board
		for (int x = in[0]; x <= in[2]; x++) {
			if (!d.isEmpty(x, 0, in[1])) {
				fp.block(x, in[1]);
			}
		}
		// a stair up to the loft along the side wall away from the door, before the counter goes in
		int[] flight = null;
		if (storeys == 2) {
			Direction wall = door <= (in[0] + in[2]) / 2 ? Dirs.RIGHT : Dirs.LEFT;
			flight = hall.flight(0, wall, Dirs.BACK);
			if (flight != null) {
				fp.keep(flight[0], flight[1]);
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(flight[4], z);
				}
			}
		}
		// the counter across the shop, a pass at the door's end; the baker serves from behind it
		int cz = in[1] + 1;
		int depth = in[3] - in[1] + 1;
		if (depth >= 4) {
			int cx0 = door <= (in[0] + in[2]) / 2 ? door + 1 : in[0];
			int cx1 = door <= (in[0] + in[2]) / 2 ? in[2] : door - 1;
			// keep a pass beside the door and clear of the stair
			if (door <= (in[0] + in[2]) / 2) {
				cx0 = Math.max(cx0, door + 2);
			} else {
				cx1 = Math.min(cx1, door - 2);
			}
			int len = 0;
			int start = Integer.MIN_VALUE;
			for (int x = cx0; x <= cx1; x++) {
				if (fp.free(x, cz) && fp.free(x, cz + 1) && fp.take(new int[][]{{x, cz}}, null)) {
					if (start == Integer.MIN_VALUE) {
						start = x;
					}
					len++;
				} else if (len > 0) {
					break;
				}
			}
			if (len >= 1) {
				Shop.counter(d, start, 0, cz, Dirs.RIGHT, len, Dirs.BACK, i -> i % 2 == 0 ? Blocks.CAKE.defaultBlockState() : null);
				fp.keep(start + (len - 1) / 2, cz + 1);
			}
		}
		// the flour chest and sacks by the ovens, racks of bread on the walls
		boolean chest = false;
		for (int[] c : fp.along(door <= (in[0] + in[2]) / 2 ? Dirs.RIGHT : Dirs.LEFT)) {
			if (c[1] < in[1] + 2) {
				continue;
			}
			Direction into = door <= (in[0] + in[2]) / 2 ? Dirs.LEFT : Dirs.RIGHT;
			if (!chest && fp.take(c[0], c[1], into)) {
				Props.store(d, c[0], 0, c[1], into, true, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:bread", 16},
					new Object[]{"minecraft:sugar", 8}, new Object[]{"minecraft:egg", 8}));
				chest = true;
			} else if (fp.take(c[0], c[1], into)) {
				Props.sacks(d, c[0], 0, c[1], Dirs.BACK, 1, true);
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:bread", 16}));
		}
		Rooms.Room room = fp.room();
		Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
		// bread on shelves high on the side walls
		for (int[] c : new int[][]{{in[0], in[1] + 2}, {in[2], in[1] + 2}}) {
			Direction face = c[0] == in[0] ? Dirs.RIGHT : Dirs.LEFT;
			if (d.isEmpty(c[0], 2, c[1]) && d.isSolid(c[0] - Dirs.dx(face), 2, c[1])) {
				Props.toolRack(d, c[0], 2, c[1], face, List.of(new Object[]{"minecraft:bread", 1, 0}, new Object[]{"minecraft:bread", 1, 1},
					new Object[]{"minecraft:cookie", 1, 2}));
			}
		}
		// the loft: sacks and stores under the roof
		if (storeys == 2 && flight != null) {
			Floorplan up = hall.floor(1).blockHoles(d);
			up.keep(flight[2], flight[3]);
			up.keepArea(flight[4], flight[5], flight[6], flight[7]);
			for (int[] c : up.along(Dirs.BACK)) {
				if (up.take(c[0], c[1], null)) {
					Props.sacks(d, c[0], up.y, c[1], Dirs.RIGHT, 1, true);
				}
			}
			Rooms.furnish(d, up.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		}
	}

	/** The yard: a domed oven where the culture bakes outdoors (a woodpile otherwise), a back door, a water butt. */
	private static void sideYard(Draft d, Hall hall, int x0, int x1, int z0, int z1, boolean left) {
		Yard.trodden(d, x0, z0, x1, z1, 0);
		// a door from the bakehouse to the yard
		Direction wall = left ? Dirs.LEFT : Dirs.RIGHT;
		int[] in = hall.inner(0);
		int dz = in[3] - 1;
		hall.door(0, wall, dz, 1, false, false);
		boolean outdoors = Hall.flatCulture(d) || d.habits().roof().weight(RoofForm.FLAT) > 0 || d.habits().walls().kind() == Habits.WallKind.ADOBE
			|| d.habits().walls().kind() == Habits.WallKind.PLASTER || d.chance(0.35);
		int cx = (x0 + x1) / 2;
		int cz = z1 - 1;
		if (outdoors && x1 - x0 >= 2 && z1 - z0 >= 4) {
			int[] mouth = Forge.dome(d, cx, 0, cz, Dirs.FRONT);
			d.poi(PoiType.JOB_SITE, mouth[0], 0, mouth[1], Dirs.BACK, JobType.BAKER);
			Garden.woodpile(d, left ? x0 : x1, 0, z0 + 1, Dirs.BACK, Math.min(2, z1 - z0 - 4));
		} else {
			Garden.woodpile(d, cx, 0, z1, Dirs.FRONT, Math.min(3, z1 - z0));
			Garden.waterButt(d, left ? x0 : x1, 0, z0 + 1);
		}
		List<int[]> gaps = new java.util.ArrayList<>();
		gaps.add(new int[]{cx, z0});
		gaps.add(new int[]{left ? x0 + 1 : x1 - 1, z0});
		Yard.enclose(d, x0, z0, x1, z1, gaps, left ? Dirs.RIGHT : Dirs.LEFT);
	}
}
