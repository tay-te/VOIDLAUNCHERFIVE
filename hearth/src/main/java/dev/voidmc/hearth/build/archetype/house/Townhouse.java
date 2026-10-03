package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/townhouse}: a tall, narrow town house of three or four storeys and a roof storey, built to stand in
 * a row. Its upper floors oversail the street on jetties in timber cultures; its gable faces the street or its ridge
 * runs along it with dormers; a stair turns in a two-lane well at the back, floor after floor. The ground floor is a
 * parlour and kitchen or a workroom with a wide window under an awning; above are a living room and bedrooms; the roof
 * storey sleeps one more. Party walls are blind and carry no eaves, so a row of these makes a continuous street front
 * whose heights, gables and colours still differ house to house.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 3–4): full storeys under the roof (default 3 or 4 by seed)</li>
 *   <li>{@code party_left}, {@code party_right} (boolean): the side shares a wall with a neighbour (no windows, no eaves)</li>
 *   <li>{@code shop} (boolean): a workroom with a shop window on the ground floor (default by seed)</li>
 * </ul>
 */
public final class Townhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/townhouse"), ArchetypeCategory.HOUSING, Set.of(), 4,
		5, 8, 8, 13, 14, 24, SettlementTier.TOWN, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE, ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.MANSARD, RoofForm.HIP, RoofForm.PAGODA,
		RoofForm.FLAT, RoofForm.CROSS_GABLE};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean partyL = d.param("party_left", false);
		boolean partyR = d.param("party_right", false);
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		if (form == RoofForm.CROSS_GABLE) {
			form = RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		WallKind kind = h.walls().kind();
		boolean timber = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLANK || kind == WallKind.LOG || h.walls().upper() != null;

		// the body: the whole frontage; a step back from the street for the jetties to come forward over
		int full = Math.clamp(d.param("storeys", d.chance(0.45) ? 4 : 3), 2, 5);
		boolean jetties = timber && d.chance(Math.max(0.5, h.walls().jetty()));
		// the door: to one side, on whichever side the street outside stands nearest the floor
		int pref = partyL && !partyR ? w - 2 : partyR && !partyL ? 1 : d.chance(0.5) ? 1 : w - 2;
		int alt = pref == 1 ? w - 2 : 1;
		int door = misfit(d, alt) < misfit(d, pref) ? alt : pref;
		// a street that is not level with the floor at the door: a row in front for steps up or down to it
		boolean awkward = misfit(d, door) > 0;
		int setback = dp >= 10 || jetties || awkward ? 1 : 0;
		int back = dp - setback >= 12 ? 2 : dp - setback >= 10 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		int bx0 = 0;
		int bx1 = w - 1;
		int sh = Math.max(4, h.walls().storeyHeight());
		boolean shop = d.param("shop", d.chance(0.5));
		int[] heights = new int[full + (flat ? 0 : 1)];
		java.util.Arrays.fill(heights, sh);
		heights[0] = shop ? sh + 1 : sh;
		if (!flat) {
			heights[full] = 1;
		}
		Body body = new Body(bx0, bz0, bx1, bz1, 0, heights, partyL, partyR);
		if (jetties) {
			body.jetty(1, Dirs.FRONT);
			if (full >= 3 && bz0 - 2 >= -1) {
				body.jetty(2, Dirs.FRONT);
			}
		}
		d.body(bx0, bz0, bx1, bz1);
		Direction.Axis ridge = d.chance(Math.max(0.55, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.MANSARD || form == RoofForm.HIP || form == RoofForm.PAGODA) {
			ridge = Direction.Axis.Z;
		}

		// the facade: the door, windows in bays; a wide shop window on the ground floor
		Facade facade = Facade.plan(d, body, door, 1, 1, 0.75);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		int plate = body.plateY();
		d.eave(plate);

		// ground and shell
		Groundwork.footings(d, bx0, bz0, bx1, bz1, -1);
		if (setback > 0 && !awkward && d.site().maxGround(0, 0, w - 1, bz0 - 1) - d.site().minGround(0, 0, w - 1, bz0 - 1) <= 1) {
			Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0);
		}
		HouseBuilder.shell(d, body, facade);
		HouseBuilder.openings(d, body, facade, true);
		if (shop) {
			shopFront(d, body, door);
		}
		// beam ends under each jetty, balconies on the upper floors
		if (full >= 3 && d.chance(h.balcony())) {
			Side s2 = body.side(2, Dirs.FRONT);
			int c = s2.column(s2.middle());
			Porches.balcony(d, s2, c - (w >= 7 ? 1 : 0), c + (w >= 7 ? 1 : 0), body.floorY(2));
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], plate, true);
			HouseBuilder.terraceAccess(d, body, deck);
		} else {
			Roofs.Opts o = Roofs.Opts.of(d);
			if (ridge == Direction.Axis.Z && o.pitch() < 1.0) {
				o = o.withPitch(1.0);
			}
			Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, form, ridge, o, partyL, partyR);
			if (ridge == Direction.Axis.X && w >= 6 && form != RoofForm.GAMBREL) {
				Side front = body.side(topK, Dirs.FRONT);
				int col = front.column(front.middle());
				plan.dormer(front, col, 1, body.floorY(topK));
			}
			roof = plan.draw(d).roof();
		}

		// chimney against the back wall, on the side away from the stair well (which is away from the door)
		boolean wellLeft = door > (bx0 + bx1) / 2;
		int cx = wellLeft ? bx1 - 1 : bx0 + 1;
		int cz = bz1 - 1;
		if (d.chance(Math.max(0.5, h.chimney()))) {
			Furniture.hearth(d, cx, 0, cz, cx == bx0 + 1 ? Dirs.RIGHT : Dirs.LEFT, false);
			if (roof != null) {
				Chimneys.stack(d, cx, cz, 2, roof, roof.ridgeY + 1);
			} else {
				HouseBuilder.chimneyFlat(d, cx, cz, deck);
			}
		} else {
			cx = Integer.MIN_VALUE;
		}

		interior(d, body, facade, roof, door, shop, cx, cz);
		// the street front: steps if the floor stands above the pavement, a bench, a lantern
		if (awkward) {
			Groundwork.approach(d, door, 0, bz0 - 1);
		} else if (setback > 0) {
			int g = d.site().ground(door, bz0 - 1);
			if (g < 0) {
				Groundwork.steps(d, door, 0, bz0 - 1, Dirs.FRONT, 1);
			} else {
				Garden.path(d, List.of(new int[]{door, 0}), 0);
				for (int x = bx0; x <= bx1; x++) {
					if (x != door && d.isAbsent(x, 0, 0) && d.chance(0.35)) {
						Garden.pot(d, x, 0, 0);
					}
				}
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, (roof != null ? roof.ridgeY : plate + 3) + 2);
	}

	/** How far the street in front of column x stands from the floor, beyond the step up a doorway allows (0 if fine). */
	private static int misfit(Draft d, int x) {
		int wy = d.site().water(x, -1);
		int s = Math.max(d.site().ground(x, -1), wy == dev.voidmc.hearth.build.terrain.Site.NONE ? Integer.MIN_VALUE : wy);
		return s > 0 ? s : s < -1 ? -1 - s : 0;
	}

	/** A shop window: the ground floor's front glazed two wide beside the door under a cloth awning. */
	private static void shopFront(Draft d, Body b, int door) {
		Side front = b.side(0, Dirs.FRONT);
		int y = b.floorY(0);
		int c0 = door <= (b.x0 + b.x1) / 2 ? door + 2 : door - 3;
		int c1 = c0 + 1;
		if (!front.contains(c0) || !front.contains(c1) || front.isCorner(front.index(c0)) || front.isCorner(front.index(c1))) {
			return;
		}
		Openings.window(d, front, c0, y + 1, 2, 2, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, true));
		// an awning of cloth stairs over the window
		var cloth = d.mat(Role.CLOTH);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int c = c0 - 1; c <= c1 + 1; c++) {
				if (!front.contains(c)) {
					continue;
				}
				int i = front.index(c);
				int x = front.outX(i, 1);
				int z = front.outZ(i, 1);
				if (z >= 0 || y + 3 >= 3) {
					if (d.isAbsent(x, y + 3, z)) {
						d.set(x, y + 3, z, cloth.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					}
				}
			}
		}
	}

	/** The stair well at the back, and every floor's rooms around it. */
	private static void interior(Draft d, Body b, Facade f, Roofs.Roof roof, int door, boolean shop, int hx, int hz) {
		int[] r0 = b.rect(0);
		int ix0 = r0[0] + 1;
		int ix1 = r0[2] - 1;
		int iz1 = r0[3] - 1;
		// the well: two lanes against the side wall away from the door, at the back
		boolean left = door > (r0[0] + r0[2]) / 2;
		int laneA = left ? ix0 : ix1;
		Direction across = left ? Dirs.RIGHT : Dirs.LEFT;
		int floors = b.storeys();
		for (int k = 0; k + 1 < floors; k++) {
			int rise = b.height(k);
			int first = rise / 2;
			int zStart = iz1 - first;
			Stairs.switchback(d, laneA, b.floorY(k), zStart, Dirs.BACK, rise, across);
		}
		int laneB = laneA + Dirs.dx(across);
		int wellZ0 = iz1 - b.height(0) / 2 - 1;
		for (int k = 0; k < floors; k++) {
			int[] r = b.rect(k);
			boolean top = k == floors - 1;
			Rooms.Room room;
			if (top && roof != null) {
				room = HouseBuilder.upperRoom(d, b, k, roof);
			} else {
				room = new Rooms.Room(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k), b.height(k) - 1);
			}
			if (room == null) {
				continue;
			}
			// the well's cells stay free; its foot and its head must be reachable
			room.keepArea(laneA, wellZ0, laneB, iz1);
			room.keep(laneB, wellZ0 - 1);
			room.keep(laneA, wellZ0 - 1);
			if (k == 0) {
				room.keep(door, r[1] + 1);
				if (hx != Integer.MIN_VALUE) {
					room.block(hx, hz);
					room.keep(hx + (hx == ix0 ? 1 : -1), hz);
				}
				Rooms.furnish(d, room, shop ? Rooms.Type.WORKSHOP : Rooms.Type.KITCHEN, 0, JobType.CARPENTER);
				if (b.storeys() >= 2) {
					Floors.joists(d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(1) - 1, Direction.Axis.X, 2, b.floorY(0));
				}
			} else if (k == 1) {
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
			} else {
				Rooms.furnish(d, room, Rooms.Type.BEDROOM, top ? 1 : 2, JobType.NONE);
			}
		}
	}
}
