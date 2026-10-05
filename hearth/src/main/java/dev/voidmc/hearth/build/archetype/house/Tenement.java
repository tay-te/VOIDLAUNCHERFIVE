package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:house/tenement}: a city block of flats, three or four storeys over a rusticated ground floor, the stair
 * every family shares climbing a well at the back of the house with a landing on every floor and a door off it to each
 * flat (two flats a floor on a wide plot, one on a narrow one), the top storey a roof storey of attic flats. Where the
 * culture pitches its roofs that roof is a steep one along the street with a row of dormers over the front windows
 * and, where it rises high enough, a second row of small dormers above them between the first (the Bohemian and
 * northern city's tall roofs); only the cultures that build flat roofs give it a flat roof with gardens, washing
 * lines and a water tank round the stair house. The street front is banded floor by floor with a cornice, balconies on alternate floors, sheets hung from the
 * windows; the back has galleries on brackets at every floor strung with washing. The ground floor is a hall through to
 * the stair with a flat or a workshop either side, a double door under a fanlight in the middle of the front.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 3–4): suggested full storeys (a storey up or down by seed so a city street steps); five
 *   under a flat roof</li>
 *   <li>{@code party_left}, {@code party_right}, {@code corner_left}, {@code corner_right} (boolean): as for the townhouse</li>
 *   <li>{@code shops} (boolean): workshops behind shop windows either side of the hall (default by seed)</li>
 *   <li>{@code walkway_y} (int, world y): a walkway meets the front at this level: a door there</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class Tenement implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/tenement"), ArchetypeCategory.HOUSING, Set.of(), 8,
		8, 12, 10, 14, 16, 22, SettlementTier.CITY, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE, ArchetypeInfo.SiteFlag.VERTICAL), List.of(),
		0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HIP, RoofForm.MANSARD, RoofForm.GAMBREL, RoofForm.HALF_HIP, RoofForm.PAGODA,
		RoofForm.FLAT, RoofForm.CROSS_GABLE, RoofForm.DOME, RoofForm.CONE, RoofForm.SALTBOX};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean cornerL = TownHouses.cornerLocalLeft(d) && !partyL;
		boolean cornerR = TownHouses.cornerLocalRight(d) && !partyR;
		int walkY = d.hasParam("walkway_y") ? d.param("walkway_y", 0) - d.spec().floorY() : Integer.MIN_VALUE;

		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		if (form == RoofForm.DOME || form == RoofForm.CONE || form == RoofForm.SALTBOX) {
			form = form == RoofForm.DOME ? RoofForm.FLAT : RoofForm.HIP;
		}
		// a city's tenements take to flat roofs and mansards more than the villages' houses do, but a flat roof only
		// where the culture builds them at all: in the pitched-roof towns a tall house wears a tall roof (art review 7)
		if (form != RoofForm.FLAT && t.flatSome() && d.chance(0.4)) {
			form = RoofForm.FLAT;
		} else if (form == RoofForm.GABLE && !t.eastern() && t.masonry() && t.culture() != Culture.TEMPERATE && d.chance(0.35)) {
			// (the Bohemian city keeps its steep tile roofs: no mansards on its squares)
			form = RoofForm.MANSARD;
		}
		if (form == RoofForm.FLAT && !t.flatSome()) {
			form = RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		int sh = Math.max(4, h.walls().storeyHeight());
		// three or four storeys and the roof storey (a burgher street's eaves, not a block of flats' parapet)
		int full = flat ? TownHouses.storeys(d, 3, 5, 4) : TownHouses.storeys(d, 3, 4, 4);

		boolean twin = w >= 10;
		int door = twin ? (w - 1) / 2 : TownHouses.doorColumn(d, 0, w - 1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		int setback = dp >= 12 || awkward ? 1 : 0;
		int back = dp - setback >= 13 ? 2 : dp - setback >= 11 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		Direction.Axis ridge = d.chance(h.roof().frontGable() * 0.5) && !twin ? Direction.Axis.Z : Direction.Axis.X;
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (t.eastern() && (partyL || partyR) && (form == RoofForm.PAGODA || form == RoofForm.HIP)) {
			form = RoofForm.HIP;
			opts = opts.withPitch(0.6);
		} else if (!flat && t.steep() && !t.eastern() && form != RoofForm.MANSARD && form != RoofForm.GAMBREL) {
			// the steep roof of a tall town house, room under it for the attic flats and two rows of dormers
			opts = opts.withPitch(Math.max(opts.pitch(), 1.25));
		}

		// fit the storeys and the roof under the plot's height: a storey goes before the roof's pitch, and a pitched
		// roof falls flat only where the culture has flat roofs (or nothing else will fit)
		Body body = null;
		for (int pass = 0; pass < 10; pass++) {
			int[] hs = TownHouses.heights(full, sh, sh, !flat);
			if (!flat) {
				// the roof storey's walls two high: the attic flats stand up under the slope, the stair climbs to them
				hs[full] = 2;
			}
			body = new Body(0, bz0, w - 1, bz1, 0, hs, partyL, partyR);
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 4 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 3) {
				full--;
			} else if (opts.pitch() > 1.0) {
				opts = opts.withPitch(1.0);
			} else if (full > 2 && pass >= 3) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else if (!flat && (t.flatSome() || pass >= 8)) {
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(0, bz0, w - 1, bz1);
		int[] r0 = body.rect(0);
		int ix0 = r0[0] + 1;
		int ix1 = r0[2] - 1;
		int iz0 = r0[1] + 1;
		int iz1 = r0[3] - 1;
		boolean shops = d.param("shops", d.chance("market".equals(d.param("district", "")) ? 0.8 : 0.35));

		// the facade: many windows in a regular grid, the hall's double door in the middle
		Facade facade = TownHouses.planWithDoor(d, body, door, twin && w % 2 == 0 ? 2 : 1, 1, 0.9);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		int doorW = 1;
		for (Facade.Opening o : facade.openings(Dirs.FRONT)) {
			if (o.kind() == Facade.Kind.DOOR) {
				doorW = o.width();
			}
		}
		d.eave(body.plateY());
		Groundwork.footings(d, 0, bz0, w - 1, bz1, -1);
		boolean rusticate = t.masonry() && !t.eastern() && d.chance(0.7);
		TownHouses.shell(d, body, facade, rusticate);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		TownHouses.openings(d, body, facade, t, style, skip, true);
		Side front0 = body.side(0, Dirs.FRONT);
		Fronts.doorCase(d, front0, door, doorW, 0, t);
		fanlight(d, front0, door, doorW);
		if (walkY != Integer.MIN_VALUE) {
			TownHouses.walkwayDoor(d, body, walkY);
		}
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);
		if (shops && twin) {
			shopWindows(d, body, door, doorW, t);
		}

		// balconies on alternate floors, sheets from the top windows, galleries at the back
		balconies(d, body, facade, t, full, walkY);
		if (back >= 1) {
			backGalleries(d, body, t, full);
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = TownHouses.flat(d, body, t.walls() == WallKind.ADOBE && !t.pueblo() && d.chance(0.5));
		} else {
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, true, false, true);
		}

		// chimneys on both party walls (or the side walls) where the culture has fires
		int[] hearth = TownHouses.chimney(d, body, roof, deck, !twin && door <= (w - 1) / 2, h.chimney());

		// the shared stair: in the middle at the back on a wide plot, against a side wall on a narrow one
		StairCore core;
		int c0 = (w - 2) / 2;
		if (twin) {
			core = TownHouses.fit(new StairCore(body, c0, Dirs.RIGHT, iz1 - 2, Dirs.BACK, 0, topK), body, roof).build(d);
		} else {
			core = TownHouses.core(d, body, door, topK, roof);
		}
		int[] roofExit = null;
		if (flat) {
			roofExit = core.bulkhead(d, deck, d.maxHeight());
			if (roofExit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, roofExit[0], roofExit[2], true);
				if (deck + 2 < d.maxHeight() && tr[2] - tr[0] >= 6) {
					Life.line(d, tr[0], tr[1] + 2, tr[2], tr[1] + 2, deck + 2);
				}
			}
		}

		// the flats
		if (twin) {
			twinFlats(d, body, core, door, doorW, shops, hearth, roof);
		} else {
			singleFlats(d, body, core, door, hearth, roof);
		}

		TownHouses.street(d, body, door, awkward, 0);
		yard(d, body);
		if (h.snow()) {
			Garden.snowBanks(d, 0, bz0, w - 1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** A pane of glass over the hall door where the lintel was: the fanlight. */
	private static void fanlight(Draft d, Side s, int door, int width) {
		for (int c = 0; c < width; c++) {
			int i = s.index(door) + c;
			if (Openings.isWallBlock(d, s.x(i), 2, s.z(i)) && d.get(s.x(i), 3, s.z(i)) != null) {
				int ii = i;
				d.inStage(StageKind.OPENINGS, () -> d.set(s.x(ii), 2, s.z(ii), d.mat(Role.GLASS).pane()));
			}
		}
	}

	/** Shop windows under awnings either side of the hall door. */
	private static void shopWindows(Draft d, Body b, int door, int doorW, Traits t) {
		Side front = b.side(0, Dirs.FRONT);
		int di0 = front.index(door);
		int di1 = di0 + doorW - 1;
		Fronts.ShopFront kind = Fronts.shopFront(t) == Fronts.ShopFront.OPEN ? Fronts.ShopFront.GLAZED : Fronts.shopFront(t);
		int y = b.floorY(0);
		int h = b.height(0) - 1;
		if (di0 - 2 >= 1) {
			Fronts.shopFront(d, front, front.column(1), front.column(di0 - 2), y, h, Integer.MIN_VALUE, kind, t);
			Fronts.awning(d, front, 0, di0 - 2, y + h, d.chance(0.6));
		}
		if (di1 + 2 <= front.last() - 1) {
			Fronts.shopFront(d, front, front.column(di1 + 2), front.column(front.last() - 1), y, h, Integer.MIN_VALUE, kind, t);
			Fronts.awning(d, front, di1 + 2, front.last(), y + h, d.chance(0.6));
		}
	}

	/** Balconies on alternate floors: a long one across the front on the first floor of a grand house, pairs above. */
	private static void balconies(Draft d, Body b, Facade f, Traits t, int full, int walkY) {
		Habits h = d.habits();
		boolean lattice = t.lattice();
		for (int k = 1; k < full; k++) {
			Side s = b.side(k, Dirs.FRONT);
			int y = b.floorY(k);
			if (walkY != Integer.MIN_VALUE && Math.abs(y - walkY) < 4) {
				continue;
			}
			if (k == 1 && d.chance(0.5 + h.balcony() * 0.5)) {
				Projections.gallery(d, s, 1, s.last() - 1, y, lattice, true);
			} else if ((k & 1) == 1 && d.chance(0.4 + h.balcony() * 0.5)) {
				for (Facade.Opening o : f.openings(Dirs.FRONT)) {
					int i = s.index(o.at());
					if (o.kind() != Facade.Kind.BLANK && i >= 1 && i + o.width() <= s.last() && d.chance(0.6)) {
						Porches.balcony(d, s, o.at(), o.at() + o.width() - 1, y);
						if (d.chance(0.4)) {
							Life.airing(d, s.outX(i, 1), y + 1, s.outZ(i, 1));
						}
					}
				}
			}
			// doors onto whatever was built in front of the windows
			for (Facade.Opening o : f.openings(Dirs.FRONT)) {
				int i = s.index(o.at());
				if (o.kind() == Facade.Kind.BLANK || i < 1 || i > s.last() - 1) {
					continue;
				}
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (d.get(ox, y - 1, oz) != null && !d.isEmpty(ox, y - 1, oz) && d.get(ox, y, oz) != null
					&& !d.isSolid(ox, y - 1, oz) && d.chance(0.5)) {
					Fronts.plainDoor(d, s.x(i), y, s.z(i), s.out());
				}
			}
		}
		// sheets hung out of the upper windows
		Side top = b.side(full - 1, Dirs.FRONT);
		for (Facade.Opening o : f.openings(Dirs.FRONT)) {
			if (o.kind() == Facade.Kind.WINDOW && d.chance(0.3)) {
				Life.sheet(d, top, top.index(o.at()), b.floorY(full - 1));
			}
		}
	}

	/** Galleries on brackets along the back at every upper floor, washing strung along them between posts. */
	private static void backGalleries(Draft d, Body b, Traits t, int full) {
		Mat fence = d.mat(Role.FENCE);
		for (int k = 1; k < full; k++) {
			Side s = b.side(k, Dirs.BACK);
			int y = b.floorY(k);
			if (!d.chance(0.75)) {
				continue;
			}
			Projections.gallery(d, s, 1, s.last() - 1, y, t.lattice(), false);
			// posts at the gallery's ends and a line between them, the washing over the deck
			int i0 = 1;
			int i1 = s.last() - 1;
			int ax = s.outX(i0, 1);
			int az = s.outZ(i0, 1);
			int bx = s.outX(i1, 1);
			int bz = s.outZ(i1, 1);
			if (y + 2 >= d.maxHeight()) {
				continue;
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int[] p : new int[][]{{ax, az}, {bx, bz}}) {
					d.set(p[0], y, p[1], fence.fence());
					d.set(p[0], y + 1, p[1], fence.fence());
					d.set(p[0], y + 2, p[1], fence.fence());
				}
			}
			Life.line(d, ax, az, bx, bz, y + 2);
			// a door out onto the gallery through a back window
			for (int i = 2; i < s.last() - 1; i++) {
				if (d.get(s.x(i), y + 1, s.z(i)) != null && !Openings.isWallBlock(d, s.x(i), y + 1, s.z(i)) && Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
					Fronts.plainDoor(d, s.x(i), y, s.z(i), s.out());
					break;
				}
			}
		}
	}

	/**
	 * Two flats a floor round a stair in the middle at the back: the stair hall walled on its sides with a door off the
	 * landing into each flat; the flats run the full depth either side, joined in front of the hall; a wall down the
	 * middle of the front divides them. On the ground floor the hall runs through from the street door to the stair.
	 */
	private static void twinFlats(Draft d, Body b, StairCore core, int door, int doorW, boolean shops, int[] hearth, Roofs.Roof roof) {
		int[] zr = core.zRange();
		int hx0 = core.x0() - 1;
		int hx1 = core.x1() + 1;
		int zl = core.zFoot;
		for (int k = 0; k < b.storeys() && TownHouses.reached(core, k); k++) {
			int[] r = b.rect(k);
			int y = b.floorY(k);
			boolean top = k == b.storeys() - 1;
			int air = b.height(k) - 1;
			if (top && roof != null) {
				air = 2;
				TownHouses.room(d, b, k, roof);
			}
			if (b.height(k) < 3) {
				continue;
			}
			int ix0 = r[0] + 1;
			int ix1 = r[2] - 1;
			int iz0 = r[1] + 1;
			int iz1 = r[3] - 1;
			// the hall's side walls from its front row to the back wall, doors off the landing row
			int hallFront = k == 0 ? iz0 : zl - 1;
			Partitions.alongZ(d, hx0, hallFront, iz1, y, air, zl, Dirs.LEFT);
			Partitions.alongZ(d, hx1, hallFront, iz1, y, air, zl, Dirs.RIGHT);
			if (k > 0) {
				// the hall's front wall, and the wall down the middle of the front between the two flats
				Partitions.alongX(d, zl - 1, hx0, hx1, y, air, Integer.MIN_VALUE, Dirs.FRONT);
				Partitions.alongZ(d, core.laneA, iz0, zl - 2, y, air, Integer.MIN_VALUE, Dirs.FRONT);
			}
			List<int[]> keepL = new ArrayList<>();
			List<int[]> keepR = new ArrayList<>();
			keepL.add(new int[]{hx0 - 1, zl});
			keepR.add(new int[]{hx1 + 1, zl});
			if (k == 0) {
				// the hall: the street door through to the stair, a lantern, a notice of the house on the wall
				Rooms.Room hall = new Rooms.Room(core.x0(), iz0, core.x1(), iz1, y, air);
				core.keep(hall, 0);
				hall.keepArea(core.x0(), iz0, core.x1(), zl);
				for (int c = 0; c < doorW; c++) {
					hall.keep(door + c, iz0);
				}
				Rooms.furnish(d, hall, Rooms.Type.HALL, 0, JobType.NONE);
			}
			// the left flat: beside the hall at the back, the front half before it
			Rooms.Room leftBack = room(d, ix0, Math.max(iz0, zl - 1), hx0 - 1, iz1, y, air);
			Rooms.Room leftFront = room(d, ix0, iz0, (k == 0 ? hx0 : core.laneA) - 1, zl - 2, y, air);
			Rooms.Room rightBack = room(d, hx1 + 1, Math.max(iz0, zl - 1), ix1, iz1, y, air);
			Rooms.Room rightFront = room(d, (k == 0 ? hx1 : core.laneA) + 1, iz0, ix1, zl - 2, y, air);
			boolean ground = k == 0;
			furnishFlat(d, leftFront, leftBack, keepL, ground, shops, hearth, r, y, k, b);
			furnishFlat(d, rightFront, rightBack, keepR, ground, shops, hearth, r, y, k, b);
			if (k > 0) {
				Rooms.Room landing = new Rooms.Room(core.x0(), zl, core.x1(), iz1, y, air);
				core.keep(landing, k);
				Rooms.furnish(d, landing, Rooms.Type.HALL, 0, JobType.NONE);
			}
		}
		TownHouses.joists(d, b, core);
	}

	private static Rooms.Room room(Draft d, int x0, int z0, int x1, int z1, int y, int air) {
		if (x1 < x0 || z1 < z0) {
			return null;
		}
		return new Rooms.Room(x0, z0, x1, z1, y, air);
	}

	/** One flat of two rooms: beds and a chest in one, a kitchen and a table in the other (or a workshop on the ground). */
	private static void furnishFlat(Draft d, Rooms.Room front, Rooms.Room back, List<int[]> keep, boolean ground, boolean shops, int[] hearth, int[] r,
		int y, int k, Body b) {
		for (Rooms.Room room : new Rooms.Room[]{front, back}) {
			if (room == null) {
				continue;
			}
			for (int[] c : keep) {
				room.keep(c[0], c[1]);
			}
			TownHouses.keepDoors(d, room, r, y);
			keepBackDoors(d, room, r, y);
			if (hearth != null && k == 0) {
				room.block(hearth[0], hearth[1]);
			}
		}
		// the two rooms meet: the cell where they join stays open
		if (front != null && back != null) {
			int jx = Math.max(front.x0, back.x0);
			for (int x = Math.max(front.x0, back.x0); x <= Math.min(front.x1, back.x1); x++) {
				boolean hot = hearth != null && k == 0 && hearth[0] == x && (hearth[1] == front.z1 || hearth[1] == back.z0);
				if (!hot) {
					jx = x;
					break;
				}
			}
			front.keep(jx, front.z1);
			back.keep(jx, back.z0);
		}
		Rooms.Room big = front != null && (back == null || front.width() * front.length() >= back.width() * back.length()) ? front : back;
		Rooms.Room small = big == front ? back : front;
		if (big == null) {
			return;
		}
		if (ground && shops) {
			Rooms.furnish(d, big, Rooms.Type.WORKSHOP, 0, JobType.CARPENTER);
			if (small != null) {
				Rooms.furnish(d, small, Rooms.Type.BEDROOM, 1, JobType.NONE);
			}
			return;
		}
		Rooms.furnish(d, big, Rooms.Type.BEDROOM, 2, JobType.NONE);
		if (small != null) {
			Rooms.furnish(d, small, Rooms.Type.KITCHEN, 0, JobType.NONE);
			if (small.width() * small.length() >= 6) {
				Rooms.furnish(d, small, Rooms.Type.BEDROOM, 1, JobType.NONE);
			}
		} else {
			Rooms.furnish(d, big, Rooms.Type.KITCHEN, 0, JobType.NONE);
		}
	}

	/** Cells inside doors in the back wall (onto galleries) stay free. */
	private static void keepBackDoors(Draft d, Rooms.Room room, int[] r, int y) {
		for (int x = r[0] + 1; x <= r[2] - 1; x++) {
			if (d.get(x, y, r[3]) != null && d.get(x, y, r[3]).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				room.keep(x, r[3] - 1);
			}
		}
	}

	/**
	 * One flat a floor beside a stair against the side wall: the stair walled off from the flat with a door on each
	 * landing; on the ground floor the street door opens into a hall before it.
	 */
	private static void singleFlats(Draft d, Body b, StairCore core, int door, int[] hearth, Roofs.Roof roof) {
		boolean wellLeft = core.x0() <= (b.x0 + b.x1) / 2;
		int wallX = wellLeft ? core.x1() + 1 : core.x0() - 1;
		Direction into = wellLeft ? Dirs.RIGHT : Dirs.LEFT;
		int zl = core.zFoot;
		for (int k = 0; k < b.storeys() && TownHouses.reached(core, k); k++) {
			int[] r = b.rect(k);
			int y = b.floorY(k);
			boolean top = k == b.storeys() - 1;
			int air = b.height(k) - 1;
			if (top && roof != null) {
				air = 2;
				TownHouses.room(d, b, k, roof);
			}
			int ix0 = r[0] + 1;
			int ix1 = r[2] - 1;
			int iz0 = r[1] + 1;
			int iz1 = r[3] - 1;
			// the stair hall: a wall alongside the well and across in front of its landing, a door into the flat
			Partitions.alongZ(d, wallX, zl - 1, iz1, y, air, zl, into);
			Partitions.alongX(d, zl - 1, wellLeft ? ix0 : wallX, wellLeft ? wallX : ix1, y, air, k == 0 ? core.laneA : Integer.MIN_VALUE, Dirs.FRONT);
			Rooms.Room flatFront = room(d, ix0, iz0, ix1, zl - 2, y, air);
			Rooms.Room flatBack = room(d, wellLeft ? wallX + 1 : ix0, zl - 1, wellLeft ? ix1 : wallX - 1, iz1, y, air);
			List<int[]> keep = new ArrayList<>();
			keep.add(new int[]{wallX + Dirs.dx(into), zl});
			if (k == 0) {
				keep.add(new int[]{door, iz0});
				keep.add(new int[]{core.laneA, zl - 2});
			}
			furnishFlat(d, flatFront, flatBack, keep, k == 0, false, hearth, r, y, k, b);
			Rooms.Room landing = room(d, core.x0(), zl, core.x1(), iz1, y, air);
			if (landing != null) {
				core.keep(landing, k);
				Rooms.furnish(d, landing, Rooms.Type.HALL, 0, JobType.NONE);
			}
		}
		TownHouses.joists(d, b, core);
	}

	/** The back court: paved, a washing line, a water butt, barrels by the wall. */
	private static void yard(Draft d, Body b) {
		int[] r = b.rect(0);
		int z0 = r[3] + 1;
		int z1 = d.depth() - 1;
		if (z1 < z0 || d.site().anyWater(0, z0, d.frontage() - 1, z1)) {
			return;
		}
		Groundwork.level(d, 0, z0, d.frontage() - 1, z1, 0);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = 0; x < d.frontage(); x++) {
					if (d.isAbsent(x, 0, z)) {
						d.set(x, -1, z, paving.full());
					}
				}
			}
		}
		if (d.isAbsent(0, 0, z1) && !b.partyLeft) {
			Garden.waterButt(d, 0, 0, z1);
		}
		if (d.isAbsent(d.frontage() - 1, 0, z1)) {
			Garden.barrels(d, d.frontage() - 1, 0, z1, Dirs.LEFT, 2);
		}
	}
}
