package dev.voidmc.hearth.build.archetype.house;

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
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.Rooflines;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/tower_house}: a family's tower, narrow and tall and built to be held: a vaulted store at the
 * bottom lit only by slits, the door a storey up at the head of a stone stair along the front, the hall over the store
 * with its fire, chambers over the hall, and a top that tells the tradition from a long way off. Stone towns crown it
 * with a parapet walk and corner bartizans round a crow-stepped cap house; alpine towers carry a tall hipped roof on
 * deep eaves; hill towns open the top storey into a loggia under a low roof; karst towers end in a latticed gallery
 * under a curved, flared roof; desert towers in merlons round a roof terrace; timber towns oversail the top storey and
 * gable it. A switchback stair climbs one side of the tower floor after floor to the roof.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 3–5): full storeys (default by the plot's height)</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class TowerHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/tower_house"), ArchetypeCategory.HOUSING, Set.of(), 2,
		6, 7, 7, 8, 14, 22, SettlementTier.HAMLET, Set.of(Culture.ALPINE, Culture.MOORLAND, Culture.MEDITERRANEAN, Culture.KARST),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 0, 0.7);

	/** How the tower is crowned. */
	enum Crown {
		/** A parapet walk with merlons and corner bartizans round a gabled cap house over the stair. */
		CAPHOUSE,
		/** A tall roof straight on the walls (hipped or gabled), the top storey timbered or jettied in framed styles. */
		ROOF,
		/** The top storey opened into a loggia on piers under a low roof. */
		LOGGIA,
		/** A latticed gallery round the top storey's front under a curved roof. */
		GALLERY,
		/** Merlons round a flat roof terrace. */
		TERRACE
	}

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
		Crown crown = crown(d, t);
		int sh = Math.max(4, h.walls().storeyHeight());
		int wanted = Math.clamp(d.param("storeys", d.maxHeight() >= 20 ? 4 : 3), 3, 5);

		// the external stair to a raised door needs a row in front of the tower and a street that meets its foot
		int door = d.chance(0.5) ? w - 2 : 1;
		boolean raisedDoor = dp >= 7 && w >= 6 && stairMeetsStreet(d, door, sh);
		int bz0 = 1;
		int bz1 = dp - 1;
		Body body = null;
		RoofForm form = roofForm(d, t, crown);
		Direction.Axis ridge = d.chance(0.5) ? Direction.Axis.X : Direction.Axis.Z;
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (crown == Crown.ROOF && opts.pitch() < 1.0 && !t.eastern()) {
			opts = opts.withPitch(form == RoofForm.HIP ? 1.0 : 1.2);
		}
		if (crown == Crown.LOGGIA) {
			opts = opts.withPitch(0.5);
		}
		int full = wanted;
		boolean flatTop = crown == Crown.CAPHOUSE || crown == Crown.TERRACE;
		for (int pass = 0; pass < 6; pass++) {
			body = new Body(0, bz0, w - 1, bz1, 0, TownHouses.heights(full, sh, sh, !flatTop), false, false);
			int top = flatTop ? body.plateY() + (crown == Crown.CAPHOUSE ? 8 : 4) : TownHouses.ridge(d, body, form, ridge, opts) + 2;
			if (top < d.maxHeight()) {
				break;
			}
			if (full > 3) {
				full--;
			} else if (!flatTop && opts.pitch() > 0.6) {
				opts = opts.withPitch(0.6);
			} else {
				crown = Crown.TERRACE;
				flatTop = true;
			}
		}
		d.body(0, bz0, w - 1, bz1);
		int doorK = raisedDoor ? 1 : 0;

		// walls: few openings, slits below, proper windows above
		Facade facade = Facade.plan(d, body, raisedDoor ? -1 : door, 1, 1, 0.45);
		if (raisedDoor) {
			facade.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, door, 1));
		} else {
			Integer placed = HouseBuilder.doorColumn(facade);
			if (placed != null) {
				door = placed;
			}
		}
		d.eave(body.plateY());
		Groundwork.footings(d, 0, bz0, w - 1, bz1, -1);
		battered(d, body, raisedDoor ? Integer.MIN_VALUE : door);
		TownHouses.shell(d, body, facade, false);
		int loggiaK = crown == Crown.LOGGIA ? body.storeys() - 2 : -1;
		openings(d, body, facade, t, doorK, door, loggiaK);

		// the raised door's stair along the front, from the far side up to a landing before the door
		if (raisedDoor) {
			outsideStair(d, body, door);
		}

		// the crown
		int topK = body.storeys() - 1;
		// the storey the crown opens: the last full one (under a pitched roof the top storey is the roof's knee)
		int crownK = flatTop || topK == 0 ? topK : topK - 1;
		int[] tr = body.topRect();
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		switch (crown) {
			case CAPHOUSE, TERRACE -> {
				deck = TownHouses.flat(d, body, true);
				machicolations(d, body);
				if (crown == Crown.CAPHOUSE) {
					bartizans(d, body, deck);
				}
			}
			case LOGGIA -> {
				Side s = body.side(crownK, Dirs.FRONT);
				int ah = Math.min(3, body.height(crownK) - 1);
				Projections.arcade(d, s, 0, s.last(), body.floorY(crownK), ah, 2, true);
				Side l = body.side(crownK, Dirs.LEFT);
				Projections.arcade(d, l, 0, l.last(), body.floorY(crownK), ah, 2, true);
				roof = Rooflines.plan(d, tr[0], tr[1], tr[2], tr[3], body.plateY(), RoofForm.HIP, Direction.Axis.X, opts, false, false).draw(d).roof();
			}
			case GALLERY -> {
				Side s = body.side(crownK, Dirs.FRONT);
				Projections.gallery(d, s, 0, s.last(), body.floorY(crownK), true, false);
				TownHouses.galleryDoor(d, s, body.floorY(crownK), s.column(s.middle()));
				for (int k = 1; k < crownK; k++) {
					Side below = body.side(k - 1, Dirs.FRONT);
					if (body.floorY(k) - 1 >= 3) {
						Fronts.pent(d, below, 0, below.last(), body.floorY(k) - 1, 1);
					}
				}
				roof = Rooflines.plan(d, tr[0], tr[1], tr[2], tr[3], body.plateY(), form, ridge, opts, false, false).draw(d).roof();
			}
			case ROOF -> {
				roof = TownHouses.pitched(d, body, form, ridge, opts, t, facade, false, t.stoneGables() && d.chance(0.6));
			}
		}

		// the hall's fire on the first floor with its chimney
		int[] r1 = body.rect(Math.min(1, topK));
		boolean wellLeft = door > (w - 1) / 2;
		int hx = wellLeft ? r1[2] - 1 : r1[0] + 1;
		int hz = r1[3] - 1;
		int[] hearth = null;
		if (d.chance(Math.max(0.4, h.chimney())) && topK >= 1) {
			int hy = body.floorY(1);
			int surface = roof != null && roof.covers(hx, hz) ? roof.surfaceY(hx, hz) : deck;
			if (surface != Integer.MIN_VALUE && surface + 3 < d.maxHeight()) {
				Furniture.hearth(d, hx, hy, hz, wellLeft ? Dirs.LEFT : Dirs.RIGHT, false);
				hearth = new int[]{hx, hz};
				if (roof != null) {
					Chimneys.stack(d, hx, hz, hy + 2, roof, Math.min(roof.ridgeY + 1, d.maxHeight() - 1));
				} else {
					Mat stone = d.mat(Role.CHIMNEY);
					try (var s = d.stage(StageKind.ROOF)) {
						for (int y = hy + 2; y <= deck + 1; y++) {
							d.set(hx, y, hz, stone.full());
						}
						d.set(hx, deck + 2, hz, dev.voidmc.hearth.build.draft.States.lit(Blocks.CAMPFIRE, true)
							.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
					}
				}
			}
		}

		// the stair: up one side from the store to the top storey (and on to the roof walk)
		int[] r0 = body.rect(0);
		int laneA = wellLeft ? r0[0] + 1 : r0[2] - 1;
		StairCore core = TownHouses.fit(new StairCore(body, laneA, wellLeft ? Dirs.RIGHT : Dirs.LEFT, r0[3] - 3, Dirs.BACK, 0, topK), body, roof).build(d);
		int[] exit = null;
		if (flatTop) {
			exit = core.bulkhead(d, deck, d.maxHeight());
			if (exit != null && crown == Crown.CAPHOUSE) {
				capRoof(d, core, deck, t);
			}
			if (exit != null) {
				d.poi(PoiType.VIEWPOINT, exit[0], deck, exit[2], Dirs.FRONT);
				d.poi(PoiType.GUARD_POST, exit[0], deck, exit[2], Dirs.FRONT);
			}
		}

		// floors: the store, the hall, chambers
		for (int k = 0; k < body.storeys() && TownHouses.reached(core, k); k++) {
			int[] r = body.rect(k);
			boolean top = k == topK;
			Rooms.Room room = TownHouses.room(d, body, k, top ? roof : null);
			if (room == null) {
				continue;
			}
			core.keep(room, k);
			TownHouses.keepDoors(d, room, r, body.floorY(k));
			if (k == doorK) {
				room.keep(door, r[1] + 1);
			}
			if (hearth != null && k == 1) {
				room.block(hearth[0], hearth[1]);
				room.keep(hearth[0] + (wellLeft ? -1 : 1), hearth[1]);
			}
			boolean loggia = k == crownK && crown == Crown.LOGGIA;
			int reachK = core.toK();
			boolean chambers = reachK >= 3 || reachK == 2 && !(crown == Crown.LOGGIA && crownK == 2);
			if (k == 0) {
				Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
			} else if (k == 1) {
				Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
				dev.voidmc.hearth.build.kit.homestead.Household.dress(d, room, room, null);
				if (!chambers) {
					TownHouses.bedroom(d, room, 2);
				}
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
			} else if (loggia) {
				Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
			} else {
				TownHouses.bedroom(d, room, top && roof != null ? 1 : 2);
			}
			if (k == (crown == Crown.LOGGIA || crown == Crown.GALLERY ? crownK : core.toK())) {
				// the lookout: the loggia, the gallery door, or the top room's front window
				d.poi(PoiType.VIEWPOINT, (r[0] + r[2]) / 2, body.floorY(k), r[1] + 1, Dirs.FRONT);
			}
		}
		TownHouses.joists(d, body, core);

		// the foot of the tower: a kerb of stones, a lamp by the stair
		if (!raisedDoor) {
			TownHouses.street(d, body, door, TownHouses.misfit(d, door) > 0, 0);
		}
		if (h.snow()) {
			Garden.snowBanks(d, 0, bz0, w - 1, bz1, 0, door);
		}
		int top = roof != null ? roof.ridgeY : deck + 5;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Whether a flight from a landing at the first floor along the front row, away from the door, ends where the street can be stepped onto. */
	private static boolean stairMeetsStreet(Draft d, int door, int rise) {
		boolean fromLeft = door > (d.frontage() - 1) / 2;
		int dir = fromLeft ? -1 : 1;
		int footX = door + dir * (rise - 1);
		if (footX < 0 || footX >= d.frontage()) {
			return false;
		}
		int wy = d.site().water(footX, -1);
		int street = wy == dev.voidmc.hearth.build.terrain.Site.NONE ? d.site().ground(footX, -1) : Math.max(d.site().ground(footX, -1), wy);
		for (int k = 1; k < rise; k++) {
			if (d.site().ground(door + dir * k, 0) > rise - k) {
				return false;
			}
		}
		return street >= 0 && street <= 1;
	}

	private static Crown crown(Draft d, Traits t) {
		if (t.eastern()) {
			return Crown.GALLERY;
		}
		if (t.flat()) {
			return Crown.TERRACE;
		}
		Habits h = d.habits();
		if (t.masonry() && t.flatSome()) {
			return d.chance(0.55) ? Crown.LOGGIA : Crown.TERRACE;
		}
		if (t.stoneGables() || t.walls() == WallKind.STONE) {
			return d.chance(0.6) ? Crown.CAPHOUSE : Crown.ROOF;
		}
		if (h.roof().eaves() >= 2) {
			return Crown.ROOF;
		}
		return d.chance(0.25) && t.masonry() ? Crown.CAPHOUSE : Crown.ROOF;
	}

	private static RoofForm roofForm(Draft d, Traits t, Crown crown) {
		Habits h = d.habits();
		RoofForm f = HouseBuilder.roofParam(d, h.roof().pick(d.random(), RoofForm.HIP, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.PAGODA,
			RoofForm.CONE));
		if (f == RoofForm.CONE) {
			f = RoofForm.HIP;
		}
		if (crown == Crown.GALLERY) {
			return RoofForm.PAGODA;
		}
		if (h.roof().eaves() >= 2 && !t.eastern() && d.chance(0.6)) {
			// a tall pyramid on deep eaves (alpine towers)
			return RoofForm.HIP;
		}
		return f;
	}

	/** A battered plinth: the ground storey's foot thickened with a sloped course of stairs where the plot allows. */
	private static void battered(Draft d, Body b, int door) {
		Mat found = d.mat(Role.FOUNDATION);
		int[] r = b.rect(0);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (Side side : Side.of(r[0], r[1], r[2], r[3])) {
				for (int i = 0; i < side.length(); i++) {
					int ox = side.outX(i, 1);
					int oz = side.outZ(i, 1);
					if (!Fronts.allowed(d, ox, 0, oz) || !d.isAbsent(ox, 0, oz) || side.out() == Dirs.FRONT && Math.abs(ox - door) <= 1) {
						continue;
					}
					int g = d.site().ground(ox, oz);
					if (g > 0 || g < -2) {
						continue;
					}
					for (int y = g - 1; y < 0; y++) {
						d.set(ox, y, oz, found.full());
					}
					d.set(ox, 0, oz, found.stairsOrSlab(side.out().getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/** Slits low down, proper windows from the hall up, the door. */
	private static void openings(Draft d, Body b, Facade f, Traits t, int doorK, int door, int loggiaK) {
		Habits.Windows hw = d.habits().windows();
		for (int k = 0; k < b.storeys(); k++) {
			if (b.height(k) < 3) {
				continue;
			}
			for (Side s : b.sides(k)) {
				if (k == loggiaK && (s.out() == Dirs.FRONT || s.out() == Dirs.LEFT)) {
					// the loggia's arches open these walls: no windows, shutters or sills to stand in front of them
					continue;
				}
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at())) {
						continue;
					}
					int y = b.floorY(k);
					if (o.kind() == Facade.Kind.DOOR) {
						if (k == doorK) {
							Openings.door(d, s, o.at(), y, 1, new Openings.DoorOpts(doorK == 0, false, true));
						} else if (k > doorK) {
							Openings.window(d, s, o.at(), y + 1, 1, 1, hw.lattice() ? Openings.WindowStyle.LATTICE : Openings.WindowStyle.DRESSED);
						}
						continue;
					}
					Openings.WindowStyle style = k <= 1 ? Openings.WindowStyle.SLIT : hw.lattice() ? Openings.WindowStyle.LATTICE
						: d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
					int height = k <= 1 ? 2 : hw.tall() && k == b.storeys() - 1 ? 2 : 1;
					if (k == 1 && s.out() == Dirs.FRONT) {
						style = hw.lattice() ? Openings.WindowStyle.LATTICE : Openings.WindowStyle.DRESSED;
					}
					Openings.window(d, s, o.at(), y + 1, 1, Math.min(height, b.height(k) - 2), style);
				}
			}
		}
	}

	/**
	 * A straight stone stair along the front of the tower from the street up to a landing before the first-floor
	 * door, built solid on the ground (it never floats), the cell under the landing an arch where it is tall.
	 */
	private static void outsideStair(Draft d, Body b, int door) {
		Mat found = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		int z = b.rect(0)[1] - 1;
		int y1 = b.floorY(1);
		boolean fromLeft = door > (b.x0 + b.x1) / 2;
		Direction up = fromLeft ? Dirs.RIGHT : Dirs.LEFT;
		int lastX;
		int lastY;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// the landing before the door
			int lg = d.site().ground(door, z);
			for (int y = Math.min(lg - 1, y1 - 1); y < y1 - 1; y++) {
				d.set(door, y, z, found.full());
			}
			d.set(door, y1 - 1, z, paving.full());
			d.set(door, y1, z, Blocks.AIR.defaultBlockState());
			d.set(door, y1 + 1, z, Blocks.AIR.defaultBlockState());
			d.set(door, y1 + 2, z, Blocks.AIR.defaultBlockState());
			// the flight down from the landing toward the far side, on down to the ground where it lies lower
			int x = door - Dirs.dx(up);
			int y = y1 - 1;
			while (x >= 0 && x < d.frontage()) {
				int g = d.site().ground(x, z);
				int wy = d.site().water(x, z);
				int stand = wy == dev.voidmc.hearth.build.terrain.Site.NONE ? g : Math.max(g, wy);
				if (stand >= y) {
					break;
				}
				d.set(x, y - 1, z, paving.has(Form.STAIRS) ? paving.stairs(up, Half.BOTTOM) : found.stairsOrSlab(up, Half.BOTTOM));
				for (int yy = g - 1; yy < y - 1; yy++) {
					d.set(x, yy, z, found.full());
				}
				for (int yy = y; yy <= Math.max(y + 2, g - 1); yy++) {
					d.set(x, yy, z, Blocks.AIR.defaultBlockState());
				}
				x -= Dirs.dx(up);
				y--;
			}
			lastX = x >= 0 && x < d.frontage() ? Integer.MIN_VALUE : x + Dirs.dx(up);
			lastY = y + 1;
			// a low wall along the stair's open side where the plot has a row for it is the street: none; a lamp
		}
		Openings.wallLantern(d, b.side(1, Dirs.FRONT), b.side(1, Dirs.FRONT).index(door) + (fromLeft ? 1 : -1), y1 + 1);
		// where the flight ran out of plot above the street, the rest of the way along the front row
		if (lastX >= 0 && lastX < d.frontage() && lastY > d.site().ground(lastX, -1) + 1) {
			Groundwork.approach(d, lastX, lastY, z);
		}
	}

	/** Corbels along the front under the parapet: a machicolated look where the parapet can stand over the street. */
	private static void machicolations(Draft d, Body b) {
		Side s = b.side(b.storeys() - 1, Dirs.FRONT);
		int y = b.plateY();
		Mat accent = d.mat(Role.WALL_ACCENT);
		Fronts.band(d, s, 0, s.last(), y, accent);
		for (int i = 0; i <= s.last(); i += 2) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			if (Fronts.allowed(d, x, y - 1, z) && d.isAbsent(x, y - 1, z)) {
				d.inStage(StageKind.WALLS, () -> d.set(x, y - 1, z, accent.stairsOrSlab(s.out().getOpposite(), Half.TOP)));
			}
		}
	}

	/** Bartizans: the parapet's two front corners raised into little turrets with pointed caps. */
	private static void bartizans(Draft d, Body b, int deck) {
		int[] tr = b.topRect();
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		Mat accent = d.mat(Role.WALL_ACCENT);
		if (deck + 4 >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] c : new int[][]{{tr[0], tr[1]}, {tr[2], tr[1]}}) {
				for (int y = deck; y <= deck + 2; y++) {
					d.set(c[0], y, c[1], wall.full());
				}
				d.set(c[0], deck + 2, c[1], accent.full());
				d.set(c[0], deck + 3, c[1], roof.stairsOrSlab(c[0] == tr[0] ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
			}
		}
	}

	/** The stair house on the roof walk crowned as a cap house: a gabled roof over it, its gables crow-stepped. */
	private static void capRoof(Draft d, StairCore core, int deck, Traits t) {
		int[] zr = core.zRange();
		int x0 = core.x0() - 1;
		int x1 = core.x1() + 1;
		int z0 = zr[0] - (core.climb == Dirs.BACK ? 0 : 1);
		int z1 = zr[1] + (core.climb == Dirs.BACK ? 1 : 0);
		int plate = deck + 2;
		if (plate + 4 >= d.maxHeight()) {
			return;
		}
		// replace the slab lid with a steep gable along the stair house's length
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				d.remove(x, deck + 3, z);
			}
		}
		Roofs.Opts o = new Roofs.Opts(1.0, 0, 0, d.habits().roof().ridge(), false, false);
		Roofs.Roof r = Rooflines.plan(d, x0, z0, x1, z1, plate, RoofForm.GABLE, Direction.Axis.Z, o, false, false).draw(d).roof();
		if (t.stoneGables() || t.masonry()) {
			Rooflines.crowSteps(d, r, Side.of(x0, z0, x1, z1, Dirs.FRONT), d.maxHeight());
			Rooflines.crowSteps(d, r, Side.of(x0, z0, x1, z1, Dirs.BACK), d.maxHeight());
		}
	}
}
