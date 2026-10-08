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
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.station.Coffers;
import dev.voidmc.hearth.build.kit.station.Study;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
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
 * {@code hearth:house/scribe_house} (docs/LIVING_WORLD.md §8.1): the narrow house of a scribe, a notary or a
 * bookseller, squeezed into a row by the market: three storeys and a garret on a frontage of a few blocks, its gable or
 * its eaves to the street as the culture builds. At the street, a bay of small panes (or a shop window) with the desk
 * under it where the light is best, a sign of a quill hung out over the door; inside the writing room, the lectern with
 * the book open on it, shelves of books up the walls, books in stacks and the strongbox of deeds; the kitchen and the
 * table above; the chamber over that; the garret with the apprentice's bed and the paper in store.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code party_left}, {@code party_right}, {@code corner_left}, {@code corner_right} (boolean): as for every town house</li>
 *   <li>{@code storeys} (int, 2–3): full storeys under the roof (default 3)</li>
 *   <li>{@code station}, {@code household} (shared), {@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class ScribeHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/scribe_house"), ArchetypeCategory.HOUSING, Set.of(), 3, 6, 8, 9, 12, 15,
		18, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.MANSARD, RoofForm.HIP, RoofForm.PAGODA,
		RoofForm.FLAT, RoofForm.CROSS_GABLE, RoofForm.SALTBOX, RoofForm.DOME};

	/** What lies open on the scribe's lectern. */
	private static final List<String> PAGES = List.of("Know all men by these presents that I, of this town, have granted and by this charter confirmed...",
		"Item: to the widow, the house by the well and the garden behind it, for her life.", "Witnessed and sealed on the feast of St. Martin.");

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		raise(d);
		dev.voidmc.hearth.build.kit.townhome.Settle.finish(d);
	}

	/** The house as drawn, before its last walk-round ({@link dev.voidmc.hearth.build.kit.townhome.Settle}). */
	private void raise(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean cornerL = TownHouses.cornerLocalLeft(d) && !partyL;
		boolean cornerR = TownHouses.cornerLocalRight(d) && !partyR;

		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		form = switch (form) {
			case DOME -> RoofForm.FLAT;
			case CROSS_GABLE, SALTBOX, HALF_HIP -> RoofForm.GABLE;
			default -> form;
		};
		boolean flat = form == RoofForm.FLAT;
		boolean frame = h.walls().kind(1) == WallKind.TIMBER_FRAME || h.walls().kind() == WallKind.TIMBER_FRAME;
		boolean jetties = frame && d.chance(Math.max(0.5, h.walls().jetty()));
		// low storeys, as the narrow houses of a crowded street have them (three garrets' worth of rooms on one plot)
		int sh = 4;
		int wanted = TownHouses.storeys(d, 3, 3, 3);

		int door = StationHomes.townDoor(d, 0, w - 1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		// a row in front for the bay window and the steps
		int setback = 1;
		int back = dp - setback >= 11 ? 2 : dp - setback >= 9 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		// a narrow house turns its gable to the street (a timber or brick town's way) or its eaves (the hill towns')
		Direction.Axis ridge = form == RoofForm.MANSARD ? Direction.Axis.X : d.chance(Math.max(0.85, h.roof().frontGable())) ? Direction.Axis.Z
			: Direction.Axis.X;
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (t.eastern()) {
			form = RoofForm.HIP;
			ridge = Direction.Axis.X;
			opts = opts.withPitch(0.6);
		} else if (form == RoofForm.HIP || form == RoofForm.PAGODA) {
			form = RoofForm.GABLE;
		}
		if (ridge == Direction.Axis.Z && opts.pitch() < 1.0 && !t.eastern()) {
			opts = opts.withPitch(1.0);
		}

		Body body = null;
		int full = wanted;
		for (int pass = 0; pass < 10; pass++) {
			// two storeys only: the garret stands up on knee walls two high where it can, the household's beds under it
			boolean fits = false;
			for (int knee = full <= 2 && !flat ? 2 : 1; knee >= 1 && !fits; knee--) {
				int[] hs = TownHouses.heights(full, sh, sh, !flat);
				if (!flat) {
					hs[full] = knee;
				}
				body = new Body(0, bz0, w - 1, bz1, 0, hs, partyL, partyR);
				if (jetties) {
					body.jetty(1, Dirs.FRONT);
				}
				fits = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2) < d.maxHeight();
			}
			if (fits) {
				break;
			}
			if (opts.pitch() > 1.0) {
				opts = opts.withPitch(1.0);
			} else if (full > 2 && pass >= 2) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else if (!flat && (t.flatSome() || pass >= 8)) {
				// a flat roof only where the culture builds them (or nothing else fits)
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(0, bz0, w - 1, bz1);

		Facade facade = TownHouses.planWithDoor(d, body, door, 1, 1, 0.7);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		int plate = body.plateY();
		d.eave(plate);

		Groundwork.footings(d, 0, bz0, w - 1, bz1, -1);
		TownHouses.shell(d, body, facade, false);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		TownHouses.openings(d, body, facade, t, style, skip, true);
		Fronts.doorCase(d, body.side(0, Dirs.FRONT), door, 1, 0, t);
		// the bay of small panes where the scribe writes, beside the door
		Side front0 = body.side(0, Dirs.FRONT);
		int bayAt = Integer.MIN_VALUE;
		int c = door <= (w - 1) / 2 ? door + 2 : door - 2;
		if (front0.contains(c) && !front0.isCorner(front0.index(c)) && !awkward && !t.raised() && Math.abs(c - door) >= 2) {
			Porches.bay(d, front0, c, c, 0);
			bayAt = c;
		}
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);
		MerchantHouse.character(d, body, facade, t, full, jetties, cornerL, cornerR, door);
		// the quill hung out over the door
		Side s1 = body.side(1, Dirs.FRONT);
		int signAt = s1.index(door);
		if (signAt > 0 && signAt < s1.last()) {
			Fronts.hangingSign(d, s1, signAt, body.floorY(1), Fronts.tradeName(d, d.pick("Scribe", "Letters", "Books", "Notary")));
		}

		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = TownHouses.flat(d, body, false);
		} else {
			boolean dormers = d.chance(Math.max(0.5, h.roof().dormers()));
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, dormers, false);
		}
		boolean wellLeft = door > (w - 1) / 2;
		int[] hearth = TownHouses.chimney(d, body, roof, deck, wellLeft, Math.max(0.8, h.chimney()), 1);
		StairCore core = TownHouses.core(d, body, door, topK, roof);
		if (flat && core.toK() == topK) {
			int[] exit = core.bulkhead(d, deck, d.maxHeight());
			if (exit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, exit[0], exit[2], false);
			}
		}
		interior(d, body, roof, core, door, hearth, bayAt);

		TownHouses.street(d, body, door, awkward, 0);
		TownHouses.yard(d, body, 0);
		if (h.snow()) {
			dev.voidmc.hearth.build.kit.Garden.snowBanks(d, 0, bz0, w - 1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	/**
	 * The writing room, the garret, the chambers and last the kitchen (which takes the bed the narrow house could not
	 * fit elsewhere before it is furnished).
	 */
	private static void interior(Draft d, Body b, Roofs.Roof roof, StairCore core, int door, int[] hearth, int bayAt) {
		int floors = b.storeys();
		int topK = floors - 1;
		boolean garret = roof != null;
		Rooms.Room[] rooms = new Rooms.Room[floors];
		for (int k = 0; k < floors; k++) {
			boolean reached = TownHouses.reached(core, k);
			if (!reached && !(garret && k == topK)) {
				continue;
			}
			int[] r = b.rect(k);
			Rooms.Room room = TownHouses.room(d, b, k, k == topK ? roof : null);
			if (room == null) {
				continue;
			}
			if (reached) {
				StationHomes.keepCore(core, room, k);
			}
			TownHouses.keepDoors(d, room, r, b.floorY(k));
			rooms[k] = room;
		}
		if (garret && rooms[topK] != null && !TownHouses.reached(core, topK)
			&& !MerchantHouse.loftLadder(d, b, core, rooms[topK], topK >= 1 ? rooms[topK - 1] : null)) {
			rooms[topK] = null;
		}
		List<Rooms.Room> sleep = new ArrayList<>();
		if (rooms[0] != null) {
			writingRoom(d, rooms[0], core, door, bayAt);
		}
		int beds = 0;
		int chamberTop = garret ? topK - 1 : topK;
		if (garret && topK >= 2 && rooms[topK] != null) {
			// the apprentice's bed, the paper and the parchment in store (the whole household up there over a house of
			// two storeys)
			TownHouses.bedroom(d, rooms[topK], chamberTop >= 2 ? 1 : INFO.beds());
			beds += TownHouses.bedsIn(d, rooms[topK]);
			Rooms.furnish(d, rooms[topK], Rooms.Type.STORAGE, 0, JobType.NONE);
			sleep.add(rooms[topK]);
		}
		for (int k = chamberTop; k >= 2; k--) {
			if (rooms[k] == null) {
				continue;
			}
			int want = Math.max(1, INFO.beds() - beds - (k > 2 ? 1 : 0));
			TownHouses.bedroom(d, rooms[k], want);
			beds += TownHouses.bedsIn(d, rooms[k]);
			sleep.add(0, rooms[k]);
		}
		for (Rooms.Room r : sleep) {
			if (beds < INFO.beds()) {
				beds += dev.voidmc.hearth.build.kit.homestead.Beds.place(d, r, INFO.beds() - beds);
			}
		}
		Rooms.Room kitchen = floors > 1 ? rooms[1] : null;
		if (kitchen != null) {
			if (hearth != null) {
				int[] r = b.rect(1);
				kitchen.block(hearth[0], hearth[1]);
				kitchen.keep(hearth[0] + (hearth[0] == r[0] + 1 ? 1 : -1), hearth[1]);
			}
			if (beds < INFO.beds()) {
				// the bed in the kitchen by the fire, as narrow houses have it
				beds += dev.voidmc.hearth.build.kit.homestead.Beds.place(d, kitchen, INFO.beds() - beds);
				if (beds < INFO.beds()) {
					int before = TownHouses.bedsIn(d, kitchen);
					TownHouses.bedroom(d, kitchen, INFO.beds() - beds);
					beds += TownHouses.bedsIn(d, kitchen) - before;
				}
			}
			Rooms.furnish(d, kitchen, Rooms.Type.KITCHEN, 0, JobType.NONE);
			Household.dress(d, kitchen, sleep.isEmpty() ? kitchen : sleep.get(0), hearth);
		}
		if (beds < INFO.beds() && rooms[0] != null) {
			dev.voidmc.hearth.build.kit.homestead.Beds.place(d, rooms[0], INFO.beds() - beds);
		}
		TownHouses.joists(d, b, core);
	}

	/**
	 * The writing room: the desk in the bay (or under the front window) with candles and the inkwell, the lectern in the
	 * light with the book open on it, shelves of books along the wall away from the stair, a stack on a shelf, the
	 * strongbox of deeds in a back corner.
	 */
	private static void writingRoom(Draft d, Rooms.Room room, StairCore core, int door, int bayAt) {
		room.keep(door, room.z0);
		int y = room.y;
		boolean coreLeft = core.x0() + core.x1() < room.x0 + room.x1;
		// the desk: in the bay (its floor is the front wall's cell, the scribe's stool inside), else under a front window
		boolean desk = false;
		if (bayAt != Integer.MIN_VALUE && room.inside(bayAt, room.z0)) {
			desk = Study.desk(d, room, bayAt, room.z0, Dirs.BACK, 2);
		}
		for (int x = room.x0; x <= room.x1 && !desk; x++) {
			if (Math.abs(x - door) >= 1) {
				desk = Study.desk(d, room, x, room.z0, Dirs.BACK, 2);
			}
		}
		// the lectern facing the window light, on the side away from the stair
		int[] zr = core.zRange();
		int lx = coreLeft ? room.x1 : room.x0;
		boolean lectern = false;
		for (int z = room.z0 + 2; z <= room.z1 && !lectern; z++) {
			if (z >= zr[0] - 1 && lx >= core.x0() - 1 && lx <= core.x1() + 1) {
				continue;
			}
			Direction facing = coreLeft ? Dirs.LEFT : Dirs.RIGHT;
			int fx = lx + Dirs.dx(facing);
			if (room.inside(fx, z) && d.isEmpty(lx, y, z) && d.isEmpty(fx, y, z)) {
				room.keep(fx, z);
				if (room.claim(lx, z)) {
					Study.lectern(d, lx, y, z, facing, "Charters", PAGES);
					lectern = true;
				}
			}
		}
		if (!lectern) {
			// anywhere along a wall the room can spare
			for (int z = room.z1; z >= room.z0 + 1 && !lectern; z--) {
				for (int x : new int[]{room.x0, room.x1}) {
					Direction facing = x == room.x0 ? Dirs.RIGHT : Dirs.LEFT;
					int fx = x + Dirs.dx(facing);
					if (lectern || !room.inside(fx, z) || !d.isEmpty(x, y, z) || !d.isEmpty(fx, y, z)) {
						continue;
					}
					room.keep(fx, z);
					if (room.claim(x, z)) {
						Study.lectern(d, x, y, z, facing, "Charters", PAGES);
						lectern = true;
					}
				}
			}
		}
		// shelves of books along the free wall, two high
		int sx = coreLeft ? room.x1 : room.x0;
		int shelves = 0;
		for (int z = room.z0 + 1; z <= room.z1 && shelves < 3; z++) {
			if (d.isEmpty(sx, y, z) && !(z >= zr[0] - 1 && sx >= core.x0() - 1 && sx <= core.x1() + 1) && Study.shelf(d, room, sx, z, 2)) {
				shelves++;
			}
		}
		Coffers.strongbox(d, room, Coffers.RECORDS, new int[]{coreLeft ? room.x1 : room.x0, room.z1});
		Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
	}
}
