package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
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
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/shophouse}: a merchant's house on the high street, the shop on the ground floor and the family
 * over it. The ground storey is taller and opens to the street in the culture's way (mullioned display windows over a
 * stall riser, round-headed glazed arches, or the whole front open between posts with its folding lattice leaves pushed
 * back) under a striped awning, a hanging sign with the trade's name over the door; inside, a counter runs along one
 * wall with the merchant behind it and the customers in front, goods on shelves and in barrels, the merchant's barrel
 * and the stock chest. In the karst, stilt and hill-town traditions the upper storeys often come forward over the
 * pavement on piers, so a row of shophouses makes a covered arcade (the five-foot way, the portico) along the street.
 * A stair at the back climbs to the home: a living room, bedrooms, one under the roof.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 2–3): full storeys including the shop (default 2)</li>
 *   <li>{@code party_left}, {@code party_right}, {@code corner_left}, {@code corner_right} (boolean): as for the
 *   townhouse; a corner shop turns its shop front round the corner</li>
 *   <li>{@code arcade} (boolean): the upper storeys over the pavement on piers (default by culture and seed, deep plots)</li>
 *   <li>{@code trade} (String): the word on the sign (default by seed)</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class Shophouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/shophouse"), ArchetypeCategory.HOUSING, Set.of(JobType.MERCHANT), 2,
		5, 8, 8, 12, 9, 15, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE),
		List.of(Hearth.id("house/townhouse"), Hearth.id("house/tenement")), 0, 0.8);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT,
		RoofForm.CROSS_GABLE, RoofForm.MANSARD, RoofForm.DOME, RoofForm.CONE, RoofForm.SALTBOX};
	private static final String[] TRADES = {"Wares", "Grocer", "Cloth", "Spices", "Books", "Potter", "Lamps", "Tea", "Candles", "Rugs", "Fruit",
		"Ironmonger"};

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

		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		if (form == RoofForm.DOME || form == RoofForm.CONE || form == RoofForm.SALTBOX) {
			form = form == RoofForm.DOME ? RoofForm.FLAT : RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		int sh = Math.max(4, h.walls().storeyHeight());
		int full = Math.clamp(d.param("storeys", 2) + (d.chance(0.25) ? 1 : 0), 2, 3);
		String trade = d.param("trade", TRADES[(int) Math.floorMod(Noise.hash(d.seed(), 0x7EADE), (long) TRADES.length)]);

		// the arcade: upper storeys over the pavement on piers (a covered walk along the row)
		double arcadeChance = t.eastern() || t.raised() ? 0.75 : t.masonry() && t.flatSome() ? 0.5 : t.masonry() ? 0.25 : 0.2;
		boolean arcade = dp >= 10 && d.param("arcade", d.chance(arcadeChance));
		int door = TownHouses.doorColumn(d, 0, w - 1, partyL, partyR);
		boolean awkward = TownHouses.misfit(d, door) > 0;
		int setback = arcade ? 2 : dp >= 10 || awkward ? 1 : 0;
		int back = dp - setback >= 11 ? 2 : dp - setback >= 9 ? 1 : 0;
		int bz0 = setback;
		int bz1 = dp - 1 - back;
		Direction.Axis ridge = d.chance(Math.max(0.35, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.HIP || form == RoofForm.PAGODA || form == RoofForm.MANSARD) {
			ridge = partyL || partyR ? Direction.Axis.X : Direction.Axis.Z;
		}
		if (form == RoofForm.CROSS_GABLE || form == RoofForm.GAMBREL) {
			ridge = Direction.Axis.X;
		}
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (t.eastern() && (partyL || partyR)) {
			form = RoofForm.HIP;
			opts = opts.withPitch(0.6);
		}
		if (ridge == Direction.Axis.Z && opts.pitch() < 1.0 && t.timber() && !t.eastern() && h.roof().eaves() < 2) {
			opts = opts.withPitch(1.0);
		}

		Body body = null;
		for (int pass = 0; pass < 6; pass++) {
			body = new Body(0, bz0, w - 1, bz1, 0, TownHouses.heights(full, sh, sh + 1, !flat), partyL, partyR);
			if (arcade) {
				body.jetty(1, Dirs.FRONT).jetty(1, Dirs.FRONT);
			} else if (h.walls().kind(1) == WallKind.TIMBER_FRAME && bz0 >= 1 && d.chance(Math.max(0.4, h.walls().jetty()))) {
				body.jetty(1, Dirs.FRONT);
			}
			int top = TownHouses.ridge(d, body, form, ridge, opts) + (flat ? 1 : 2);
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 2) {
				full--;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else {
				form = RoofForm.FLAT;
				flat = true;
			}
		}
		d.body(0, bz0, w - 1, bz1);

		Facade facade = Facade.plan(d, body, door, 1, t.timber() && !t.eastern() ? 2 : 1, 0.75);
		Integer placed = HouseBuilder.doorColumn(facade);
		if (placed != null) {
			door = placed;
		}
		d.eave(body.plateY());
		Groundwork.footings(d, 0, bz0, w - 1, bz1, -1);
		TownHouses.shell(d, body, facade, false);
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		Set<Integer> skip = new HashSet<>();
		skip.add(0);
		TownHouses.openings(d, body, facade, t, style, skip, false);
		TownHouses.dress(d, body, t, flat, cornerL || cornerR, cornerL ? Dirs.LEFT : Dirs.RIGHT);

		// the shop front: everything but the door and the corner posts, the door in it, the awning over it
		Side front = body.side(0, Dirs.FRONT);
		int gh = body.height(0);
		Fronts.ShopFront kind = Fronts.shopFront(t);
		int di = front.index(door);
		Fronts.shopFront(d, front, front.column(1), front.column(front.last() - 1), 0, gh - 1, door, kind, t);
		Openings.door(d, front, door, 0, 1, new Openings.DoorOpts(true, false, true));
		if (kind == Fronts.ShopFront.OPEN) {
			// the open front: the door leaf stands open against its post, the counter is the threshold
			d.remove(front.x(di), 0, front.z(di));
			d.remove(front.x(di), 1, front.z(di));
			d.air(front.x(di), 0, front.z(di));
			d.air(front.x(di), 1, front.z(di));
		}
		if (cornerL || cornerR) {
			Side side0 = body.side(0, cornerL ? Dirs.LEFT : Dirs.RIGHT);
			Fronts.shopFront(d, side0, side0.column(1), side0.column(Math.min(side0.last() - 1, 3)), 0, gh - 1, Integer.MIN_VALUE,
				kind == Fronts.ShopFront.OPEN ? Fronts.ShopFront.GLAZED : kind, t);
		}
		if (arcade) {
			arcade(d, body, t, door);
		} else {
			if (gh - 1 >= 3) {
				Fronts.awning(d, front, 0, front.last(), gh - 1, d.chance(0.65));
			}
		}
		Side s1 = body.side(1, Dirs.FRONT);
		Fronts.hangingSign(d, s1, s1.index(door), body.floorY(1), Fronts.tradeName(d, trade));
		if (d.chance(h.balcony())) {
			int i = s1.middle();
			Porches.balcony(d, s1, s1.column(Math.max(1, i - 1)), s1.column(Math.min(s1.last() - 1, i + 1)), body.floorY(1));
			TownHouses.galleryDoor(d, s1, body.floorY(1), s1.column(i));
		} else if (t.eastern() && full >= 2) {
			Fronts.pent(d, s1, 0, s1.last(), body.floorY(1) + body.height(1) - 1, 1);
		}

		// roof
		int topK = body.storeys() - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = TownHouses.flat(d, body, t.walls() == WallKind.ADOBE && !t.pueblo() && d.chance(0.5));
		} else {
			roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, d.chance(Math.max(0.4, h.roof().dormers())), false);
			if (ridge == Direction.Axis.Z && !t.eastern() && d.chance(0.4)) {
				TownHouses.hoist(d, body, roof);
			}
		}
		boolean wellLeft = door > (w - 1) / 2;
		int[] hearth = TownHouses.chimney(d, body, roof, deck, wellLeft, h.chimney(), 1);
		StairCore core = TownHouses.core(d, body, door, topK);
		if (flat) {
			int[] exit = core.bulkhead(d, deck, d.maxHeight());
			if (exit != null) {
				Life.roofGarden(d, tr[0] + 1, tr[1] + 1, tr[2] - 1, tr[3] - 1, deck, exit[0], exit[2], false);
			}
		}

		// the shop inside, the home above
		shop(d, body, core, door);
		for (int k = 1; k < body.storeys(); k++) {
			int[] r = body.rect(k);
			boolean top = k == topK;
			Rooms.Room room = TownHouses.room(d, body, k, top ? roof : null);
			if (room == null) {
				continue;
			}
			core.keep(room, k);
			TownHouses.keepDoors(d, room, r, body.floorY(k));
			if (k == 1 && hearth != null) {
				room.block(hearth[0], hearth[1]);
				room.keep(hearth[0] + (hearth[0] == r[0] + 1 ? 1 : -1), hearth[1]);
			}
			if (k == 1 && body.storeys() >= 3 && !(top && roof != null)) {
				Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
			}
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, top && roof != null ? 1 : 2, JobType.NONE);
		}
		TownHouses.joists(d, body, core);

		TownHouses.street(d, body, door, awkward, 0);
		display(d, body, door, arcade);
		TownHouses.yard(d, body, 0);
		if (h.snow()) {
			Garden.snowBanks(d, 0, bz0, w - 1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 4;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
	}

	/**
	 * The covered walk: the storeys above stand forward over the pavement on piers at the street line, round arches (or
	 * beams) between them, a paved floor and a lantern hanging in each bay.
	 */
	private static void arcade(Draft d, Body b, Traits t, int door) {
		int[] r0 = b.rect(0);
		int[] r1 = b.rect(1);
		int z = r1[1];
		int top = b.floorY(1) - 2;
		Mat pier = t.timber() && !t.masonry() ? d.mat(Role.FRAME) : d.mat(Role.WALL_ACCENT);
		Mat paving = d.mat(Role.PAVING);
		Mat beam = d.mat(Role.BEAM);
		int w = d.frontage();
		// piers at both ends (the party lines) and every three cells between
		java.util.List<Integer> piers = new java.util.ArrayList<>();
		piers.add(r1[0]);
		int bays = Math.max(1, Math.round((r1[2] - r1[0]) / 3.0f));
		for (int k = 1; k < bays; k++) {
			piers.add(r1[0] + Math.round(k * (r1[2] - r1[0]) / (float) bays));
		}
		piers.add(r1[2]);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : piers) {
				int g = d.site().ground(x, z);
				for (int y = Math.min(-1, g - 1); y <= top; y++) {
					d.set(x, y, z, t.timber() && !t.masonry() ? pier.log(Direction.Axis.Y) : pier.full());
				}
				// the pier's base and capital
				if (!t.timber() || t.masonry()) {
					d.set(x, top, z, pier.full());
				}
			}
		}
		// between the piers: arch shoulders (masonry) or a beam with brackets (timber), the floor paved
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = 0; i + 1 < piers.size(); i++) {
				int a = piers.get(i) + 1;
				int c = piers.get(i + 1) - 1;
				for (int x = a; x <= c; x++) {
					if (t.timber() && !t.masonry()) {
						d.set(x, top, z, beam.log(Direction.Axis.X));
					} else if (c - a >= 1 && (x == a || x == c)) {
						d.set(x, top, z, pier.stairsOrSlab(x == a ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
					} else if (c - a >= 1) {
						d.set(x, top, z, Blocks.AIR.defaultBlockState());
					} else {
						d.set(x, top, z, pier.slab(SlabType.TOP));
					}
					for (int y = 0; y < top; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				// a lantern hanging in the bay, from the floor above
				int lx = (a + c) / 2;
				int lz = (z + r0[1]) / 2;
				if (d.isSolid(lx, b.floorY(1) - 1, lz) && d.isEmpty(lx, top, lz)) {
					Furniture.hangingLantern(d, lx, b.floorY(1) - 1, lz, 0);
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int zz = z; zz < r0[1]; zz++) {
				for (int x = r1[0]; x <= r1[2]; x++) {
					if (piers.contains(x) && zz == z) {
						continue;
					}
					int g = d.site().ground(x, zz);
					if (g <= 1 && g >= -2) {
						d.set(x, -1, zz, paving.full());
						for (int y = g - 1; y < -1; y++) {
							d.set(x, y, zz, d.mat(Role.FOUNDATION).full());
						}
						for (int y = 0; y < Math.max(0, g); y++) {
							d.set(x, y, zz, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		if (w >= 7) {
			d.poi(PoiType.VIEWPOINT, door, 0, z, Dirs.FRONT);
		}
	}

	/**
	 * The shop: a counter along the side wall away from the door, the merchant between it and the wall, the customers
	 * in front of it; the merchant's barrel at the front end of his side, shelves of goods on the customers' wall.
	 */
	private static void shop(Draft d, Body b, StairCore core, int door) {
		int[] r = b.rect(0);
		int ix0 = r[0] + 1;
		int ix1 = r[2] - 1;
		int iz0 = r[1] + 1;
		int[] zr = core.zRange();
		int iz1 = zr[0] - 1;
		boolean doorLeft = door <= (r[0] + r[2]) / 2;
		int mx = doorLeft ? ix1 : ix0;
		int cx = doorLeft ? ix1 - 1 : ix0 + 1;
		Direction toCustomer = doorLeft ? Dirs.LEFT : Dirs.RIGHT;
		int y = b.baseY;
		Mat wood = d.mat(Role.FURNITURE);
		int cz0 = iz0 + 1;
		int cz1 = Math.min(iz1 - 1, cz0 + 2);
		Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, r[3] - 1, y, b.height(0) - 1);
		core.keep(room, 0);
		room.keep(door, iz0);
		if (cz1 < cz0 || ix1 - ix0 < 2) {
			// no room for a counter: the merchant's barrel by the door, shelves round the walls
			Furniture.workstation(d, JobType.MERCHANT, mx, y, iz0, Dirs.BACK);
			d.poi(PoiType.COUNTER, mx, y, iz0 + 1, toCustomer);
			room.block(mx, iz0);
			room.keep(mx, iz0 + 1);
			Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = cz0; z <= cz1; z++) {
				double u = Noise.unit(d.seed() ^ 0xC0C0, cx, y, z);
				if (u < 0.3) {
					d.set(cx, y, z, States.facing(Blocks.BARREL, toCustomer));
				} else {
					d.set(cx, y, z, wood.slab(SlabType.TOP));
					Fronts.wares(d, cx, y + 1, z, u * 0.8);
				}
				room.block(cx, z);
			}
		}
		int mid = (cz0 + cz1) / 2;
		int custX = cx + Dirs.dx(toCustomer);
		d.poi(PoiType.COUNTER, mx, y, mid, toCustomer);
		d.poi(PoiType.MARKET_STALL, custX, y, mid, toCustomer.getOpposite());
		room.keep(mx, mid);
		room.keep(custX, mid);
		// the merchant's barrel at the front end of his side, the stock chest at the far end
		Furniture.workstation(d, JobType.MERCHANT, mx, y, iz0, Dirs.BACK);
		room.block(mx, iz0);
		room.keep(mx, iz0 + 1);
		if (cz1 + 1 <= iz1 && d.isEmpty(mx, y, cz1 + 1)) {
			Furniture.chest(d, mx, y, cz1 + 1, toCustomer, List.of(new Object[]{"minecraft:paper", 12}, new Object[]{"minecraft:candle", 6},
				new Object[]{"minecraft:bread", 4}));
			room.block(mx, cz1 + 1);
			room.keep(cx, cz1 + 1);
		}
		// shelves of goods on the customers' wall
		int wx = doorLeft ? ix0 : ix1;
		Block shelf = d.mat(Role.FURNITURE).main(dev.voidmc.hearth.build.style.Form.SHELF);
		for (int z = iz0 + 1; z <= iz1; z++) {
			if (wx == door || !room.inside(wx, z) || Math.abs(wx - custX) == 0 && z == mid) {
				continue;
			}
			if ((z & 1) == 0) {
				int zz = z;
				Furniture.barrel(d, wx, y, zz, Direction.UP);
				room.block(wx, zz);
				if (shelf != null) {
					Furniture.shelf(d, wx, y + 1, zz, toCustomer.getOpposite(), "kitchen");
				}
			}
		}
		Rooms.furnish(d, room, Rooms.Type.HALL, 0, JobType.NONE);
	}

	/** Goods set out in front of the shop: barrels, crates of produce, pots, a bench by the door. */
	private static void display(Draft d, Body b, int door, boolean arcade) {
		int z0 = b.rect(0)[1];
		if (z0 < 1) {
			return;
		}
		int z = z0 - 1;
		for (int x = 0; x < d.frontage(); x++) {
			if (Math.abs(x - door) <= 1 || !d.isAbsent(x, 0, z) || !d.isSolid(x, -1, z)) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0xD15, x, 0, z);
			Block crate = u < 0.25 ? Blocks.BARREL : u < 0.4 ? Blocks.MELON : u < 0.5 ? Blocks.PUMPKIN : u < 0.62 ? Blocks.HAY_BLOCK : null;
			if (crate != null) {
				int cx = x;
				d.inStage(StageKind.EXTERIOR, () -> d.set(cx, 0, z, crate == Blocks.BARREL ? States.facing(crate, Direction.UP) : crate.defaultBlockState()));
				if (u < 0.12 && d.isAbsent(cx, 1, z)) {
					Garden.pot(d, cx, 1, z);
				}
			} else if (u < 0.75) {
				Garden.pot(d, x, 0, z);
			}
		}
	}
}
