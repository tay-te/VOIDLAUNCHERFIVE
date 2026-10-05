package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * {@code hearth:house/townhouse}: a tall, narrow town house of three to five storeys under a roof storey, built to stand
 * in a row and to read on its own in one. Each culture raises it its own way: timber houses oversail the street on
 * jetties, an oriel on corbels, a gable to the street or the ridge along it with dormers or a cross gable; hill-town
 * houses are rendered over a stone base with banded floors, balconies with doors onto them, a loggia under the eaves
 * or a roof terrace under a pergola; karst houses climb in tiers of pent roofs and latticed galleries under a curved
 * roof; desert houses lift a latticed mashrabiya over the street and crenellate their roof terrace; pueblo houses step
 * back storey by storey into terraces; stilt cultures stack posted galleries on the front; stone towns crow-step their
 * gables and raise their party walls over the roof with the chimneys on them. A switchback stair climbs the back of the
 * house floor after floor; the ground floor is a kitchen or a workroom with a shop window, a living room above,
 * bedrooms over that, one more under the roof where the roof is high enough over the stair to reach it (else a loft).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 3–4): suggested full storeys under the roof (a storey up or down by seed now and then so a
 *   row steps its eaves; default 3 or 4)</li>
 *   <li>{@code party_left}, {@code party_right} (boolean): the building's own sides shared with neighbours (blind, no
 *   eaves; parapets and chimneys on them in masonry towns); read through {@code Draft.partyLocalLeft/Right}</li>
 *   <li>{@code corner_left}, {@code corner_right} (boolean): a street runs along that own side too: windows on it, the
 *   roof hipped toward it where the culture hips, an oriel on the corner</li>
 *   <li>{@code shop} (boolean): a workroom with a shop window on the ground floor (default by seed)</li>
 *   <li>{@code terrace} (boolean): a flat-roofed house steps its top storey back behind a roof terrace (default by seed)</li>
 *   <li>{@code walkway_y} (int, world y): a walkway from across the street meets the front here: a door at that level</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int): as for every house</li>
 * </ul>
 */
public final class Townhouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/townhouse"), ArchetypeCategory.HOUSING, Set.of(), 4,
		5, 8, 8, 13, 13, 20, SettlementTier.TOWN, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE, ArchetypeInfo.SiteFlag.VERTICAL), List.of(Hearth.id("house/tenement")), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.MANSARD, RoofForm.HIP, RoofForm.PAGODA,
		RoofForm.FLAT, RoofForm.CROSS_GABLE, RoofForm.SALTBOX, RoofForm.DOME};

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
		if (form == RoofForm.DOME || form == RoofForm.SALTBOX && (partyL || partyR)) {
			form = form == RoofForm.DOME ? RoofForm.FLAT : RoofForm.GABLE;
		}
		boolean frame = h.walls().kind(1) == WallKind.TIMBER_FRAME || h.walls().kind() == WallKind.TIMBER_FRAME;
		boolean jetties = frame && walkY == Integer.MIN_VALUE && d.chance(Math.max(0.55, h.walls().jetty()));
		boolean pueblo = t.pueblo() && form == RoofForm.FLAT && w >= 7;
		boolean shop = d.param("shop", d.chance(0.4));
		int sh = Math.max(4, h.walls().storeyHeight());
		// three storeys and the roof, the burgher house's measure; a fourth now and then where a city street steps up
		int wanted = TownHouses.storeys(d, 3, 4, 3);

		// the door, where the street meets the floor; a row in front for steps when it does not
		int bx0 = cornerL && jetties ? 1 : 0;
		int bx1 = cornerR && jetties ? w - 2 : w - 1;
		int door = TownHouses.doorColumn(d, bx0, bx1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		// galleries on posts across the front where the street meets the floor (railed decks cannot step down to it)
		boolean galleries = t.raised() && dp >= 10 && walkY == Integer.MIN_VALUE && !awkward;
		int setback = galleries ? 2 : dp >= 10 || jetties || awkward || pueblo ? 1 : 0;
		if (walkY != Integer.MIN_VALUE) {
			setback = awkward ? 1 : 0;
		}
		int back = dp - setback >= 12 ? 2 : dp - setback >= 10 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		boolean flat = form == RoofForm.FLAT;
		Direction.Axis ridge = d.chance(Math.max(0.5, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.MANSARD || form == RoofForm.CROSS_GABLE) {
			ridge = Direction.Axis.X;
		}
		if (form == RoofForm.HIP || form == RoofForm.PAGODA) {
			ridge = Direction.Axis.Z;
		}
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (ridge == Direction.Axis.Z && opts.pitch() < 1.0 && t.timber() && !t.eastern() && h.roof().eaves() < 2) {
			// a timber town's gable to the street is steep
			opts = opts.withPitch(1.0);
		}
		if (t.eastern() && (partyL || partyR) && (form == RoofForm.PAGODA || form == RoofForm.HIP)) {
			// in a row the curved roof runs along the street, low, its corners lifted only where the house is free
			form = RoofForm.HIP;
			ridge = Direction.Axis.X;
			opts = opts.withPitch(0.6);
		}
		boolean crow = !flat && ridge == Direction.Axis.Z && t.stoneGables() && (form == RoofForm.GABLE || form == RoofForm.CROSS_GABLE)
			&& d.chance(0.7);
		if (crow) {
			opts = opts.withEaves(opts.eaves(), 0);
		}

		// fit the storeys and the roof under the plot's height (a pueblo has as many storeys as it can step back)
		Body body = null;
		int full = pueblo ? Math.max(2, Math.min(wanted, 1 + (bz1 - bz0 + 1 - 5) / 2)) : wanted;
		for (int pass = 0; pass < 8; pass++) {
			int ground = shop && walkY == Integer.MIN_VALUE ? sh + 1 : sh;
			body = new Body(bx0, bz0, bx1, bz1, 0, TownHouses.heights(full, sh, ground, !flat), partyL, partyR);
			shape(d, body, full, jetties, pueblo, flat, cornerL, cornerR, t);
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 3 || full > 2 && pass >= 2) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else if (!flat && (t.flatSome() || pass >= 6)) {
				// a flat roof only where the culture builds them (or nothing else fits)
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(bx0, bz0, bx1, bz1);
		boolean terraced = flat && !pueblo && full >= 3 && bz1 - bz0 + 1 >= 9 && d.param("terrace", d.chance(0.6));
		if (terraced) {
			body = new Body(bx0, bz0, bx1, bz1, 0, TownHouses.heights(full, sh, body.height(0), false), partyL, partyR);
			shape(d, body, full, jetties, false, true, cornerL, cornerR, t);
			body.setback(full - 1, Dirs.FRONT, bz1 - bz0 + 1 >= 11 ? 3 : 2);
		}

		// the facade: the door in its bay, windows over windows
		int windowW = (frame || t.walls() == WallKind.PLANK) && w >= 7 ? 2 : w >= 8 && t.masonryUpper() && d.chance(0.3) ? 2 : 1;
		Facade facade = TownHouses.planWithDoor(d, body, door, 1, windowW, frame ? 0.6 : 0.7);
		if ((frame || t.walls() == WallKind.BAMBOO || t.walls() == WallKind.PLANK) && d.chance(0.75)) {
			facade = TownHouses.pairedPanels(facade);
		}
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		int plate = body.plateY();
		d.eave(plate);

		// ground and shell
		Groundwork.footings(d, bx0, bz0, bx1, bz1, -1);
		boolean rusticate = t.walls() == WallKind.PLASTER && h.walls().kind(1) == WallKind.PLASTER && d.chance(0.45);
		TownHouses.shell(d, body, facade, rusticate);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		TownHouses.openings(d, body, facade, t, style, skip, true);
		Side front0 = body.side(0, Dirs.FRONT);
		Fronts.doorCase(d, front0, door, 1, 0, t);
		if (shop) {
			shopWindow(d, body, door, t);
		}
		if (walkY != Integer.MIN_VALUE) {
			TownHouses.walkwayDoor(d, body, walkY);
		}
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);

		// what stands out from the upper storeys
		List<int[]> upperKeep = new ArrayList<>();
		character(d, body, facade, t, full, jetties, galleries, cornerL, cornerR, door, walkY);
		// a hill town's house often opens its top storey into a loggia under the eaves
		int loggiaK = -1;
		if (t.masonry() && t.flatSome() && !flat && full >= 3 && !galleries && d.chance(0.5)) {
			loggiaK = full - 1;
			Side top = body.side(loggiaK, Dirs.FRONT);
			Projections.arcade(d, top, 0, top.last(), body.floorY(loggiaK), body.height(loggiaK) - 1, top.length() >= 7 ? 2 : 1, true);
			if (top.length() >= 5) {
				Life.airing(d, top.x(1), body.floorY(loggiaK) + 1, top.z(1));
			}
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		int terraceDoor = Integer.MIN_VALUE;
		if (flat) {
			if (pueblo) {
				TownHouses.terraces(d, body, full);
			}
			if (terraced) {
				terraceDoor = TownHouses.setbackTerrace(d, body, full);
			}
			deck = TownHouses.flat(d, body, t.walls() == WallKind.ADOBE && !t.pueblo() && d.chance(0.6));
		} else {
			boolean dormers = d.chance(Math.max(0.5, h.roof().dormers()));
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, dormers, crow);
			if (ridge == Direction.Axis.Z && !t.eastern() && d.chance(t.masonryUpper() || frame ? 0.45 : 0.2)) {
				TownHouses.hoist(d, body, roof);
			}
		}

		// the stair climbs the back; a chimney on the door's side (on a party wall when there is one)
		boolean wellLeft = door > (bx0 + bx1) / 2;
		int[] hearth = TownHouses.chimney(d, body, roof, deck, wellLeft, h.chimney());
		int coreTop = topK;
		StairCore core = pueblo ? null : TownHouses.core(d, body, door, coreTop, roof);
		int[] roofExit = null;
		if (pueblo) {
			TownHouses.puebloLadders(d, body, full, deck, door);
		} else if (flat && !terraced) {
			roofExit = core.bulkhead(d, deck, d.maxHeight());
			if (roofExit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, roofExit[0], roofExit[2], false);
			}
		}
		interior(d, body, roof, core, door, shop, hearth, terraceDoor, upperKeep, loggiaK);

		// the street in front and the yard behind
		TownHouses.street(d, body, door, awkward, 0);
		TownHouses.yard(d, body, 0);
		if (h.snow()) {
			dev.voidmc.hearth.build.kit.Garden.snowBanks(d, bx0, bz0, bx1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Jetties, the pueblo's setbacks, a corner's oversailing side. */
	private static void shape(Draft d, Body body, int full, boolean jetties, boolean pueblo, boolean flat, boolean cornerL, boolean cornerR, Traits t) {
		if (jetties) {
			body.jetty(1, Dirs.FRONT);
			if (full >= 3 && body.z0 - 2 >= -1) {
				body.jetty(2, Dirs.FRONT);
			}
			if (cornerL && body.x0 >= 1) {
				body.jetty(1, Dirs.LEFT);
			}
			if (cornerR && body.x1 <= d.frontage() - 2) {
				body.jetty(1, Dirs.RIGHT);
			}
		}
		if (pueblo) {
			// each storey steps back from the street: a stair of roof terraces
			int depth = body.depth();
			for (int k = 1; k < full; k++) {
				if (depth - 2 * k >= 5) {
					body.setback(k, Dirs.FRONT, 2);
				}
			}
		}
	}

	/** A shop window two wide beside the door under a striped awning, a hanging sign over the street. */
	private static void shopWindow(Draft d, Body b, int door, Traits t) {
		Side front = b.side(0, Dirs.FRONT);
		int y = b.floorY(0);
		int c0 = door <= (b.x0 + b.x1) / 2 ? door + 2 : door - 3;
		int c1 = c0 + 1;
		if (!front.contains(c0) || !front.contains(c1) || front.isCorner(front.index(c0)) || front.isCorner(front.index(c1))) {
			return;
		}
		Fronts.ShopFront kind = Fronts.shopFront(t);
		Fronts.shopFront(d, front, c0, c1, y, b.height(0) - 1, Integer.MIN_VALUE, kind == Fronts.ShopFront.OPEN ? Fronts.ShopFront.GLAZED : kind, t);
		int ia = Math.min(front.index(c0), front.index(c1));
		int ib = Math.max(front.index(c0), front.index(c1));
		if (y + b.height(0) - 1 >= 3) {
			Fronts.awning(d, front, ia - 1, ib + 1, y + b.height(0) - 1, d.chance(0.6));
		}
		Side s1 = b.side(1, Dirs.FRONT);
		Fronts.hangingSign(d, s1, s1.index(door), b.floorY(1),
			Fronts.tradeName(d, d.pick("Wares", "Goods", "Cloth", "Spices", "Books", "Tools")));
	}

	/** The culture's way of making a tall narrow house read from the street. */
	private static void character(Draft d, Body b, Facade f, Traits t, int full, boolean jetties, boolean galleries, boolean cornerL, boolean cornerR,
		int door, int walkY) {
		Habits h = d.habits();
		int w = d.frontage();
		if (galleries) {
			// stacked galleries on posts across the whole front, a door onto each
			int[] floors = new int[full];
			for (int k = 0; k < full; k++) {
				floors[k] = b.floorY(k);
			}
			Side front = b.side(0, Dirs.FRONT);
			Projections.postedGalleries(d, front, 0, front.last(), floors, 2, b.floorY(full) - 1, t.lattice(), front.index(door));
			for (int k = 1; k < full; k++) {
				TownHouses.galleryDoor(d, b.side(k, Dirs.FRONT), b.floorY(k), door);
			}
			return;
		}
		if (t.eastern()) {
			// tiers: a latticed gallery on the first floor, pent roofs at every floor line above
			for (int k = 1; k < full; k++) {
				Side s = b.side(k, Dirs.FRONT);
				Side below = b.side(k - 1, Dirs.FRONT);
				if (k == 1 && walkY == Integer.MIN_VALUE && d.chance(Math.max(0.6, h.balcony()))) {
					Projections.gallery(d, s, 0, s.last(), b.floorY(k), true, false);
					TownHouses.galleryDoor(d, s, b.floorY(k), door);
				} else if (b.floorY(k) - 1 >= 3) {
					Fronts.pent(d, below, 0, below.last(), b.floorY(k) - 1, 1);
				}
			}
			if (full >= 2) {
				Side s = b.side(full - 1, Dirs.FRONT);
				// a pent over the first-floor gallery
				Side g = b.side(1, Dirs.FRONT);
				if (full >= 3) {
					Fronts.pent(d, g, 0, g.last(), b.floorY(2) - 1, 1);
				}
				if (s.length() > 0 && full >= 4 && d.chance(0.5)) {
					Projections.gallery(d, s, 1, s.last() - 1, b.floorY(full - 1), true, false);
				}
			}
			return;
		}
		if (t.walls() == WallKind.ADOBE && !t.pueblo()) {
			// a mashrabiya: a latticed oriel over the street on the first floor (and the second in a tall house)
			Side s = b.side(1, Dirs.FRONT);
			int c = s.middle() - (s.length() >= 7 ? 1 : 0);
			Projections.oriel(d, s, c, c + (s.length() >= 6 ? 1 : 0), b.floorY(1), 2, true);
			if (full >= 4 && d.chance(0.5)) {
				Side s3 = b.side(3, Dirs.FRONT);
				Projections.oriel(d, s3, c, c + (s3.length() >= 6 ? 1 : 0), b.floorY(3), 2, true);
			}
			return;
		}
		if (t.pueblo()) {
			return;
		}
		if (jetties) {
			// an oriel on the first floor in some houses, flower boxes do the rest
			if (d.chance(Math.max(0.35, h.windows().bay())) && full >= 3) {
				Side s = b.side(1, Dirs.FRONT);
				for (Facade.Opening o : f.openings(Dirs.FRONT)) {
					int i = s.index(o.at());
					if (o.kind() == Facade.Kind.WINDOW && i >= 1 && i + o.width() <= s.last()) {
						Projections.oriel(d, s, i - (o.width() == 1 ? 0 : 0), i + o.width() - 1, b.floorY(1), 2, false);
						break;
					}
				}
			}
			return;
		}
		// rendered and stone houses: balconies with doors onto them, a loggia under the eaves
		boolean alpine = h.roof().eaves() >= 2 && h.roof().frontGable() >= 0.5;
		for (int k = 1; k < full; k++) {
			Side s = b.side(k, Dirs.FRONT);
			if (walkY != Integer.MIN_VALUE && b.floorY(k) == walkY) {
				continue;
			}
			if (alpine && k >= 1 && d.chance(0.8)) {
				Projections.gallery(d, s, 0, s.last(), b.floorY(k), false, true);
				TownHouses.galleryDoor(d, s, b.floorY(k), door);
			} else if (d.chance(h.balcony() * (k == 1 ? 1.2 : 0.8))) {
				int c = s.index(door);
				int i0 = Math.max(1, c - (w >= 7 ? 1 : 0));
				int i1 = Math.min(s.last() - 1, c + (w >= 7 ? 1 : 0));
				Porches.balcony(d, s, s.column(i0), s.column(i1), b.floorY(k));
				TownHouses.galleryDoor(d, s, b.floorY(k), door);
				if (d.chance(0.5)) {
					Life.airing(d, s.outX(i0, 1), b.floorY(k) + 1, s.outZ(i0, 1));
				}
			}
		}
		// sheets out of an upper window here and there in the hill towns
		if (t.flatSome() && full >= 3) {
			Side s = b.side(full - 1, Dirs.FRONT);
			for (Facade.Opening o : f.openings(Dirs.FRONT)) {
				if (o.kind() == Facade.Kind.WINDOW && d.chance(0.35)) {
					Life.sheet(d, s, s.index(o.at()), b.floorY(full - 1));
				}
			}
		}
		// a corner house: an oriel on the side street at the front corner
		if (cornerL || cornerR) {
			Side s = b.side(1, cornerL ? Dirs.LEFT : Dirs.RIGHT);
			int i = cornerL ? s.last() - 2 : 1;
			Projections.oriel(d, s, i, i + 1, b.floorY(1), 2, t.lattice());
		}
	}

	/** The stair kept free on every floor, each floor's rooms. */
	private static void interior(Draft d, Body b, Roofs.Roof roof, StairCore core, int door, boolean shop, int[] hearth, int terraceDoor,
		List<int[]> keep, int loggiaK) {
		int floors = b.storeys();
		// the attic a loft the stair does not reach while the loggia takes the floor under it: the first floor sleeps
		boolean atticLost = core != null && core.toK() < floors - 1;
		boolean loft = atticLost && loggiaK >= 0;
		for (int k = 0; k < floors && TownHouses.reached(core, k); k++) {
			int[] r = b.rect(k);
			boolean top = k == floors - 1;
			Rooms.Room room = TownHouses.room(d, b, k, top ? roof : null);
			if (room == null) {
				continue;
			}
			if (core != null) {
				core.keep(room, k);
			}
			TownHouses.keepDoors(d, room, r, b.floorY(k));
			for (int[] c : keep) {
				room.keep(c[0], c[1]);
			}
			if (top && terraceDoor != Integer.MIN_VALUE) {
				room.keep(terraceDoor, r[1] + 1);
			}
			if (k == 0) {
				room.keep(door, r[1] + 1);
				if (hearth != null) {
					room.block(hearth[0], hearth[1]);
					room.keep(hearth[0] + (hearth[0] == r[0] + 1 ? 1 : -1), hearth[1]);
				}
				Rooms.furnish(d, room, shop ? Rooms.Type.WORKSHOP : Rooms.Type.KITCHEN, 0, JobType.CARPENTER);
				dev.voidmc.hearth.build.kit.homestead.Household.dress(d, room, room, hearth);
			} else if (k == loggiaK) {
				// in a house of few floors the loggia storey sleeps too, the beds back from the arches
				if (loft || floors <= 4) {
					TownHouses.bedroom(d, room, floors <= 4 ? 2 : 1);
				}
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
			} else if (k == 1 && floors >= 4 && !loft) {
				// a door out onto a balcony or gallery stays reachable
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					if (d.get(x, b.floorY(k), r[1]) != null && d.get(x, b.floorY(k), r[1]).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
						room.keep(x, r[1] + 1);
					}
				}
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
				if (r[2] - r[0] - 1 >= 5 && floors <= 4) {
					// a wide house with few floors: a chamber behind the parlour
					TownHouses.bedroom(d, room, atticLost ? 2 : 1);
				}
			} else {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					if (d.get(x, b.floorY(k), r[1]) != null && d.get(x, b.floorY(k), r[1]).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
						room.keep(x, r[1] + 1);
					}
				}
				TownHouses.bedroom(d, room, top && roof != null ? 1 : 2);
				if (core == null) {
					Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				}
			}
		}
		TownHouses.joists(d, b, core);
	}
}
