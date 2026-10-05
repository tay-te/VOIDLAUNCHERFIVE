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
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.station.Coffers;
import dev.voidmc.hearth.build.kit.station.Study;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/merchant_house} (docs/LIVING_WORLD.md §8.1): the tall house of a rich merchant on the market
 * street, built to stand in a row and to outshine it. Three or four storeys and a warehouse loft under a steep roof
 * with its gable to the street; at the apex the hoist beam with its chain over the loading door, through which the
 * bales come up from the street. In the Bohemian market town (and the hill towns) the ground storey opens on the
 * street as an arcade, the walk under the house where the customers come in out of the rain, the shop's door and window
 * under the arches; elsewhere a glazed shop front beside a dressed door under an awning, the sign hung over the street.
 * Each culture raises it its own way (jetties and an oriel in timber towns, balconies on rendered ones, crow-stepped
 * brick gables in the burgher towns, a mashrabiya in the dry lands, pent roofs and a gallery in the karst).
 *
 * <p>Inside: the counting room behind the shop window, its counter across the room with the scales on it (COUNTER and
 * the merchant's place behind, MARKET_STALL before), the strongbox in the back corner by the ledger desk; the hall above
 * with the hearth, the table and the best of everything (carpets, candles); the family's chambers over that; the loft
 * stacked with barrels, bales and sacks (reached by the stair where the roof leaves room, else by a ladder).
 *
 * <p>Params:
 * <ul>
 *   <li>{@code party_left}, {@code party_right}, {@code corner_left}, {@code corner_right} (boolean): as for every town house</li>
 *   <li>{@code storeys} (int, 3–4): full storeys under the roof (default 3 or 4)</li>
 *   <li>{@code arcade} (boolean): the arcaded ground storey where the culture builds one (default by culture and seed)</li>
 *   <li>{@code station}, {@code household} (shared), {@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class MerchantHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/merchant_house"), ArchetypeCategory.HOUSING, Set.of(JobType.MERCHANT), 5,
		7, 11, 11, 15, 13, 20, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE, ArchetypeInfo.SiteFlag.VERTICAL), List.of(),
		0, 1.0);

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

		// a gable to the street wherever the culture pitches its roofs: it carries the hoist
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		form = switch (form) {
			case DOME -> RoofForm.FLAT;
			case HALF_HIP, CROSS_GABLE, SALTBOX -> RoofForm.GABLE;
			default -> form;
		};
		boolean flat = form == RoofForm.FLAT;
		boolean frame = h.walls().kind(1) == WallKind.TIMBER_FRAME || h.walls().kind() == WallKind.TIMBER_FRAME;
		boolean jetties = frame && d.chance(Math.max(0.5, h.walls().jetty()));
		int sh = Math.max(4, h.walls().storeyHeight());
		int wanted = TownHouses.storeys(d, 3, 4, flat ? 4 : d.chance(0.5) ? 4 : 3);

		int bx0 = cornerL && jetties ? 1 : 0;
		int bx1 = cornerR && jetties ? w - 2 : w - 1;
		int door = StationHomes.townDoor(d, bx0, bx1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		Culture c = t.culture();
		boolean arcadeLand = c == Culture.TEMPERATE || c == Culture.MEDITERRANEAN || c == Culture.ALPINE;
		boolean arcade = !flat && !t.raised() && !awkward && !jetties && t.masonry() && arcadeLand && w >= 7 && dp >= 12
			&& d.param("arcade", d.chance(c == Culture.TEMPERATE ? 0.7 : 0.45));
		int setback = arcade ? 0 : dp >= 10 || jetties || awkward ? 1 : 0;
		int back = dp - setback >= 13 ? 2 : dp - setback >= 11 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;

		Direction.Axis ridge = form == RoofForm.MANSARD ? Direction.Axis.X : Direction.Axis.Z;
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (t.eastern()) {
			// the curved roof runs along the street, its corners lifted where the house stands free
			form = RoofForm.HIP;
			ridge = Direction.Axis.X;
			if (partyL || partyR) {
				opts = opts.withPitch(0.6);
			}
		} else if (form == RoofForm.HIP || form == RoofForm.PAGODA) {
			form = RoofForm.GABLE;
		}
		if (ridge == Direction.Axis.Z && opts.pitch() < 1.0 && !t.eastern()) {
			opts = opts.withPitch(1.0);
		}
		boolean crow = !flat && ridge == Direction.Axis.Z && form == RoofForm.GABLE && (t.stoneGables() || c == Culture.TEMPERATE && t.masonry())
			&& d.chance(t.stoneGables() ? 0.7 : 0.4);
		if (crow) {
			opts = opts.withEaves(opts.eaves(), 0);
		}

		// fit the storeys and the roof under the plot's height
		Body body = null;
		int full = wanted;
		int ground = sh + 1;
		for (int pass = 0; pass < 10; pass++) {
			body = new Body(bx0, bz0, bx1, bz1, 0, TownHouses.heights(full, sh, ground, !flat), partyL, partyR);
			if (jetties) {
				body.jetty(1, Dirs.FRONT);
				if (cornerL && body.x0 >= 1) {
					body.jetty(1, Dirs.LEFT);
				}
				if (cornerR && body.x1 <= w - 2) {
					body.jetty(1, Dirs.RIGHT);
				}
			}
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 3) {
				full--;
			} else if (ground > sh) {
				// the shop's tall ground storey goes before a storey does
				ground = sh;
			} else if (full > 2 && pass >= 3) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else if (!flat && (t.flatSome() || pass >= 8)) {
				// a flat roof only where the culture builds them (or nothing else fits): never a box in a pitched town
				form = RoofForm.FLAT;
				flat = true;
				crow = false;
			}
		}
		d.body(bx0, bz0, bx1, bz1);

		// the facade: generous windows, the door in its bay
		int windowW = (frame || t.walls() == WallKind.PLANK) && w >= 7 ? 2 : w >= 9 && t.masonryUpper() && d.chance(0.8) ? 2 : 1;
		Facade facade = TownHouses.planWithDoor(d, body, door, 1, windowW, frame ? 0.65 : 0.75);
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
		boolean rusticate = t.walls() == WallKind.PLASTER && h.walls().kind(1) == WallKind.PLASTER && !arcade && d.chance(0.6);
		TownHouses.shell(d, body, facade, rusticate);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		if (arcade) {
			skip.add(0);
		}
		TownHouses.openings(d, body, facade, t, style, skip, true);
		int shopZ0;
		if (arcade) {
			shopZ0 = arcade(d, body, door, style);
		} else {
			Fronts.doorCase(d, body.side(0, Dirs.FRONT), door, 1, 0, t);
			shopWindow(d, body, door, t);
			shopZ0 = body.rect(0)[1] + 1;
		}
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);
		character(d, body, facade, t, full, jetties, cornerL, cornerR, door);
		Side s1 = body.side(1, Dirs.FRONT);
		int signAt = s1.index(door) + (s1.index(door) <= s1.middle() ? 1 : -1);
		if (signAt > 0 && signAt < s1.last()) {
			Fronts.hangingSign(d, s1, signAt, body.floorY(1), Fronts.tradeName(d, d.pick("Cloth", "Spices", "Wine", "Salt", "Furs", "Silver", "Wool")));
		}

		// roof, hoist and loading door
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = TownHouses.flat(d, body, t.walls() == WallKind.ADOBE && !t.pueblo() && d.chance(0.6));
			flatHoist(d, body);
		} else {
			boolean dormers = d.chance(Math.max(0.5, h.roof().dormers()));
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, dormers, crow);
			if (ridge == Direction.Axis.Z) {
				loadingDoor(d, body, roof);
				TownHouses.hoist(d, body, roof);
			}
		}

		// the hearth in the hall upstairs (the counting room keeps no fire), the stair up the back
		boolean wellLeft = door > (bx0 + bx1) / 2;
		int[] hearth = TownHouses.chimney(d, body, roof, deck, wellLeft, Math.max(0.85, h.chimney()), 1);
		StairCore core = TownHouses.core(d, body, door, topK, roof);
		if (flat && core.toK() == topK) {
			int[] roofExit = core.bulkhead(d, deck, d.maxHeight());
			if (roofExit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, roofExit[0], roofExit[2], false);
			}
		}
		interior(d, body, roof, core, door, hearth, shopZ0, arcade);

		// the street in front and the yard behind
		TownHouses.street(d, body, door, awkward, 0);
		TownHouses.yard(d, body, 0);
		if (h.snow()) {
			dev.voidmc.hearth.build.kit.Garden.snowBanks(d, bx0, bz0, bx1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	// ------------------------------------------------------------------------------------------------- front

	/**
	 * The arcade (the Bohemian {@code podloubí}): the ground storey's street wall opened into round-headed arches on
	 * piers, the walk under the house paved, and the shop's own front a row or two back under the first floor: its door
	 * and a wide window of small panes, a lantern under the vault. Returns the first row inside the shop.
	 */
	private static int arcade(Draft d, Body b, int door, Openings.WindowStyle style) {
		int[] r = b.rect(0);
		Side front = b.side(0, Dirs.FRONT);
		int walk = b.depth() >= 12 ? 2 : 1;
		int innerZ = r[1] + walk + 1;
		int ceiling = b.floorY(1) - 2;
		int bay = front.length() >= 10 ? 3 : 2;
		Projections.arcade(d, front, 0, front.last(), 0, ceiling, bay, false);
		// the inner front: wall from side wall to side wall, up to the first floor
		Mat wall = d.mat(Role.WALL);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = r[0] + 1; x <= r[2] - 1; x++) {
				for (int y = 0; y <= ceiling; y++) {
					d.set(x, y, innerZ, wall.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = r[1]; z < innerZ; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
			// the arches' sills paved too, flush with the street
			for (int i = 1; i < front.last(); i++) {
				if (d.isEmpty(front.x(i), 0, front.z(i))) {
					d.set(front.x(i), -1, front.z(i), paving.full());
				}
			}
		}
		// beams across the walk's ceiling
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = r[1] + 1; z < innerZ; z++) {
				if (((z - r[1]) & 1) == 1) {
					for (int x = r[0] + 1; x <= r[2] - 1; x++) {
						if (d.isEmpty(x, ceiling, z)) {
							d.set(x, ceiling, z, d.mat(Role.BEAM).log(Direction.Axis.X));
						}
					}
				}
			}
		}
		Side inner = Side.of(r[0], innerZ, r[2], r[3], Dirs.FRONT);
		Openings.door(d, inner, door, 0, 1, new Openings.DoorOpts(true, false, true));
		// the shop window beside the door, as wide as the bay allows
		int c0 = door <= (r[0] + r[2]) / 2 ? door + 2 : door - 3;
		int c1 = c0 + 1;
		if (c0 > r[0] && c1 < r[2] && Openings.isWallBlock(d, c0, 1, innerZ) && Openings.isWallBlock(d, c1, 1, innerZ)) {
			Openings.window(d, inner, TownHouses.firstCell(inner, c0, 2), 1, 2, Math.min(2, ceiling - 1), style);
		} else {
			int cw = door <= (r[0] + r[2]) / 2 ? door + 2 : door - 2;
			if (cw > r[0] && cw < r[2] && Openings.isWallBlock(d, cw, 1, innerZ)) {
				Openings.window(d, inner, cw, 1, 1, Math.min(2, ceiling - 1), style);
			}
		}
		// a bench for those waiting under the arches
		int bx = door <= (r[0] + r[2]) / 2 ? r[2] - 1 : r[0] + 1;
		if (Math.abs(bx - door) >= 2 && d.isEmpty(bx, 0, innerZ - 1) && walk >= 2) {
			dev.voidmc.hearth.build.kit.Furniture.chair(d, bx, 0, innerZ - 1, Dirs.FRONT);
		}
		return innerZ + 1;
	}

	/** A shop window two wide beside the door under an awning (the merchant's house shows its wares). */
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
			Fronts.awning(d, front, ia - 1, ib + 1, y + b.height(0) - 1, d.chance(0.5));
		}
	}

	/** What stands out from the upper storeys, the culture's way (a trimmed {@link Townhouse} character). */
	static void character(Draft d, Body b, Facade f, Traits t, int full, boolean jetties, boolean cornerL, boolean cornerR, int door) {
		Habits h = d.habits();
		int w = d.frontage();
		if (t.eastern()) {
			for (int k = 1; k < full; k++) {
				Side s = b.side(k, Dirs.FRONT);
				Side below = b.side(k - 1, Dirs.FRONT);
				if (k == 1) {
					Projections.gallery(d, s, 0, s.last(), b.floorY(k), true, false);
					TownHouses.galleryDoor(d, s, b.floorY(k), door);
				} else if (b.floorY(k) - 1 >= 3) {
					Fronts.pent(d, below, 0, below.last(), b.floorY(k) - 1, 1);
				}
			}
			if (full >= 3) {
				Side g = b.side(1, Dirs.FRONT);
				Fronts.pent(d, g, 0, g.last(), b.floorY(2) - 1, 1);
			}
			return;
		}
		if (t.walls() == WallKind.ADOBE && !t.pueblo()) {
			Side s = b.side(1, Dirs.FRONT);
			int c = s.middle() - (s.length() >= 7 ? 1 : 0);
			Projections.oriel(d, s, c, c + (s.length() >= 6 ? 1 : 0), b.floorY(1), 2, true);
			if (full >= 3) {
				Side s2 = b.side(2, Dirs.FRONT);
				Projections.oriel(d, s2, c, c + (s2.length() >= 6 ? 1 : 0), b.floorY(2), 2, true);
			}
			return;
		}
		if (t.pueblo()) {
			return;
		}
		if (jetties) {
			// the merchant's oriel over the street on the first floor
			if (full >= 3) {
				Side s = b.side(1, Dirs.FRONT);
				for (Facade.Opening o : f.openings(Dirs.FRONT)) {
					int i = s.index(o.at());
					if (o.kind() == Facade.Kind.WINDOW && i >= 1 && i + o.width() <= s.last()) {
						Projections.oriel(d, s, i, i + o.width() - 1, b.floorY(1), 2, false);
						break;
					}
				}
			}
			return;
		}
		// rendered and stone houses: a balcony off the hall over the door, a gallery in the alpine way
		boolean alpine = h.roof().eaves() >= 2 && h.roof().frontGable() >= 0.5;
		for (int k = 1; k < full; k++) {
			Side s = b.side(k, Dirs.FRONT);
			if (alpine && d.chance(0.8)) {
				Projections.gallery(d, s, 0, s.last(), b.floorY(k), false, true);
				TownHouses.galleryDoor(d, s, b.floorY(k), door);
			} else if (k == 1 && d.chance(Math.max(0.35, h.balcony()))) {
				int ci = s.index(door);
				int i0 = Math.max(1, ci - (w >= 7 ? 1 : 0));
				int i1 = Math.min(s.last() - 1, ci + (w >= 7 ? 1 : 0));
				Porches.balcony(d, s, s.column(i0), s.column(i1), b.floorY(k));
				TownHouses.galleryDoor(d, s, b.floorY(k), door);
			}
		}
		if (cornerL || cornerR) {
			Side s = b.side(1, cornerL ? Dirs.LEFT : Dirs.RIGHT);
			int i = cornerL ? s.last() - 2 : 1;
			Projections.oriel(d, s, i, i + 1, b.floorY(1), 2, t.lattice());
		}
	}

	/**
	 * The loading door in the front gable at the loft's floor under the hoist: the gable's window opened down to the
	 * floor, a pair of plank shutters standing open in it.
	 */
	private static void loadingDoor(Draft d, Body b, Roofs.Roof roof) {
		int topK = b.storeys() - 1;
		Side front = b.side(topK, Dirs.FRONT);
		int i = front.middle();
		int x = front.x(i);
		int z = front.z(i);
		int y0 = b.floorY(topK);
		if (!roof.covers(x, z) || roof.bottomY(x, z) - y0 < 3) {
			return;
		}
		Mat shutter = d.mat(Role.SHUTTER);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y0, z, shutter.trapdoor(Dirs.FRONT, Half.BOTTOM, true));
			d.set(x, y0 + 1, z, shutter.trapdoor(Dirs.FRONT, Half.TOP, true));
			// above the door the gable closes again (a two-high window there is walled up over the lintel)
			BlockState above = d.get(x, y0 + 2, z);
			if (above != null && !Openings.isWallBlock(d, x, y0 + 2, z) && roof.bottomY(x, z) > y0 + 2) {
				BlockState beside = d.get(x + 1, y0 + 2, z);
				d.set(x, y0 + 2, z, beside != null && Openings.isWallBlock(d, x + 1, y0 + 2, z) ? beside : d.mat(Role.BEAM).log(Direction.Axis.X).at(x, y0 + 2, z));
			}
		}
	}

	/** A flat-roofed merchant's house hoists its goods to the top storey: a loading door there, the beam over it. */
	private static void flatHoist(Draft d, Body b) {
		int topK = b.storeys() - 1;
		if (topK < 2) {
			return;
		}
		Side front = b.side(topK, Dirs.FRONT);
		int i = front.middle();
		int x = front.x(i);
		int z = front.z(i);
		int y0 = b.floorY(topK);
		int beamY = b.plateY();
		if (!Openings.isWallBlock(d, x, y0, z) && !d.isEmpty(x, y0, z) || !Fronts.allowed(d, x, beamY, z - 1) || !d.isAbsent(x, beamY, z - 1)) {
			return;
		}
		Mat shutter = d.mat(Role.SHUTTER);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y0, z, shutter.trapdoor(Dirs.FRONT, Half.BOTTOM, true));
			d.set(x, y0 + 1, z, shutter.trapdoor(Dirs.FRONT, Half.TOP, true));
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, beamY, z - 1, d.mat(Role.BEAM).log(Direction.Axis.Z));
			if (d.isAbsent(x, beamY - 1, z - 1)) {
				d.set(x, beamY - 1, z - 1, dev.voidmc.hearth.build.draft.States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- inside

	/** Every storey's room: the counting room, the hall, the chambers, the loft. */
	private static void interior(Draft d, Body b, Roofs.Roof roof, StairCore core, int door, int[] hearth, int shopZ0, boolean arcade) {
		int floors = b.storeys();
		int topK = floors - 1;
		boolean loft = roof != null;
		Rooms.Room hall = null;
		List<Rooms.Room> chambers = new ArrayList<>();
		Rooms.Room store = null;
		for (int k = 0; k < floors; k++) {
			boolean reached = TownHouses.reached(core, k);
			boolean isLoft = loft && k == topK;
			if (!reached && !isLoft) {
				continue;
			}
			int[] r = b.rect(k);
			Rooms.Room room = TownHouses.room(d, b, k, k == topK ? roof : null);
			if (room == null) {
				continue;
			}
			if (k == 0 && arcade) {
				room = new Rooms.Room(room.x0, shopZ0, room.x1, room.z1, room.y, room.height);
			}
			if (reached) {
				StationHomes.keepCore(core, room, k);
			}
			TownHouses.keepDoors(d, room, r, b.floorY(k));
			if (k == 0) {
				counting(d, room, core, door);
			} else if (k == 1) {
				if (hearth != null) {
					room.block(hearth[0], hearth[1]);
					room.keep(hearth[0] + (hearth[0] == r[0] + 1 ? 1 : -1), hearth[1]);
				}
				Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				hall = room;
			} else if (isLoft) {
				if (!reached) {
					if (!loftLadder(d, b, core, room)) {
						continue;
					}
				}
				// with one storey of chambers the household's servants and apprentices sleep up among the goods
				int chamberFloors = Math.max(0, topK - 2);
				if (chamberFloors <= 1) {
					TownHouses.bedroom(d, room, chamberFloors == 0 ? 3 : 2);
					chambers.add(room);
				}
				Coffers.goods(d, room, 0.6, 2);
				Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
				store = room;
			} else if (!loft && k == topK && k >= 3) {
				// a flat roof's top storey: the store, a servant's bed in it
				TownHouses.bedroom(d, room, 1);
				Coffers.goods(d, room, 0.45, 2);
				Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
				store = room;
				chambers.add(room);
			} else {
				TownHouses.bedroom(d, room, chambers.isEmpty() ? 3 : 2);
				chambers.add(room);
			}
		}
		// a household of five: any bed short goes in a chamber, the loft or the hall
		int beds = 0;
		List<Rooms.Room> all = new ArrayList<>(chambers);
		if (store != null && !all.contains(store)) {
			all.add(store);
		}
		if (hall != null) {
			all.add(hall);
		}
		for (Rooms.Room r : all) {
			beds += TownHouses.bedsIn(d, r);
		}
		for (Rooms.Room r : all) {
			if (beds >= INFO.beds()) {
				break;
			}
			beds += dev.voidmc.hearth.build.kit.homestead.Beds.place(d, r, INFO.beds() - beds);
		}
		if (hall != null) {
			Household.dress(d, hall, chambers.isEmpty() ? hall : chambers.get(0), hearth);
		}
		TownHouses.joists(d, b, core);
	}

	/**
	 * The counting room: the counter across it a couple of rows in from the door (the customers' side by the window, the
	 * merchant behind with his barrel), the strongbox in the back corner on the door's side with the ledger desk beside
	 * it, the rest furnished as a store.
	 */
	private static void counting(Draft d, Rooms.Room room, StairCore core, int door) {
		room.keep(door, room.z0);
		int[] zr = core.zRange();
		boolean coreLeft = core.x0() + core.x1() < room.x0 + room.x1;
		int farX = coreLeft ? room.x0 : room.x1;
		int sgn = coreLeft ? 1 : -1;
		// two rows for the customers where the stair's well leaves room behind the counter, else one
		int zc = Math.min(room.z0 + 2, zr[0] - 2);
		boolean placed = false;
		if (zc >= room.z0 + 1 && zc + 1 <= room.z1) {
			int endX = door - 2 * sgn;
			int len = (endX - farX) * sgn + 1;
			if (len >= 2) {
				int midX = farX + sgn * (len / 2);
				placed = Coffers.counter(d, room, farX, zc, endX, zc, Dirs.FRONT, midX, zc);
				if (placed) {
					Coffers.desk(d, room, farX, zc + 1, coreLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.MERCHANT);
				}
			}
		}
		if (!placed) {
			// a shallow room: the merchant's barrel by the back wall on the door's side, the counter its head
			int bx = coreLeft ? room.x1 : room.x0;
			if (!Coffers.desk(d, room, bx, room.z1, Dirs.FRONT, JobType.MERCHANT)) {
				Coffers.desk(d, room, bx, room.z0 + 1, Dirs.BACK, JobType.MERCHANT);
			}
			d.poi(dev.voidmc.hearth.api.PoiType.COUNTER, bx, room.y, Math.max(room.z0, room.z1 - 1), Dirs.FRONT);
		}
		int nearX = coreLeft ? room.x1 : room.x0;
		int[] box = Coffers.strongbox(d, room, Coffers.MERCHANT, new int[]{nearX, room.z1});
		// the ledger desk beside the strongbox, against the back wall
		if (box != null) {
			int dx = box[0] + (box[0] == room.x0 ? 1 : -1);
			if (room.inside(dx, room.z1) && !(dx >= core.x0() && dx <= core.x1() && room.z1 >= zr[0])) {
				Study.desk(d, room, dx, room.z1, Dirs.FRONT, 2);
			}
		}
		Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	/**
	 * A ladder up to a loft the stair cannot reach: against the back (else the front) wall of the storey below in the
	 * middle column, under the ridge where the loft has head room, through a hatch in the loft's floor. Answers whether
	 * the loft can be reached.
	 */
	static boolean loftLadder(Draft d, Body b, StairCore core, Rooms.Room loft) {
		return loftLadder(d, b, core, loft, null);
	}

	/** {@link #loftLadder} that also takes the ladder's cell out of the room below (not yet furnished) and keeps its foot free. */
	static boolean loftLadder(Draft d, Body b, StairCore core, Rooms.Room loft, Rooms.@org.jspecify.annotations.Nullable Room below) {
		int k = b.storeys() - 2;
		if (k < 0 || !TownHouses.reached(core, k)) {
			return false;
		}
		int[] r = b.rect(k);
		int y0 = b.floorY(k);
		int yl = b.floorY(k + 1);
		int mid = (r[0] + r[2]) / 2;
		int[] zr = core.zRange();
		for (int[] spot : new int[][]{{mid, r[3] - 1, 0}, {mid + 1, r[3] - 1, 0}, {mid, r[1] + 1, 1}, {mid - 1, r[1] + 1, 1}}) {
			int x = spot[0];
			int z = spot[1];
			Direction facing = spot[2] == 0 ? Dirs.FRONT : Dirs.BACK;
			int wz = spot[2] == 0 ? r[3] : r[1];
			boolean inCore = x >= core.x0() - 1 && x <= core.x1() + 1 && z >= zr[0] - 1 && z <= zr[1] + 1;
			if (inCore || x <= r[0] || x >= r[2]) {
				continue;
			}
			boolean ok = true;
			for (int y = y0; y <= yl && ok; y++) {
				ok = d.isSolid(x, y, wz) && (y == yl - 1 || y == yl || d.isEmpty(x, y, z));
			}
			int fx = x + Dirs.dx(facing);
			int fz = z + Dirs.dz(facing);
			ok &= d.isEmpty(x, yl, z) && d.isEmpty(x, yl + 1, z) && loft.inside(x, z) && d.isEmpty(fx, y0, fz) && d.isEmpty(fx, y0 + 1, fz);
			ok &= !(d.get(x, y0, z) != null && d.get(x, y0, z).getBlock() instanceof DoorBlock);
			if (!ok) {
				continue;
			}
			if (below != null) {
				below.keep(fx, fz);
				if (!below.claim(x, z)) {
					continue;
				}
			}
			Stairs.ladder(d, x, z, y0, yl, facing);
			loft.keep(x + Dirs.dx(facing), z + Dirs.dz(facing));
			loft.block(x, z);
			return true;
		}
		return false;
	}
}
