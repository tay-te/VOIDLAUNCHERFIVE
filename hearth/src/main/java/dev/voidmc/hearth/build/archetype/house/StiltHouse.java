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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Beds;
import dev.voidmc.hearth.build.kit.homestead.Household;
import dev.voidmc.hearth.build.kit.homestead.RoofDress;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Verandas;
import dev.voidmc.hearth.build.kit.homestead.Waterside;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:house/stilt_house}: a little house over the water (or over the wet ground of a marsh), raised on piles,
 * its life lived on its galleries.
 *
 * <p>The house stands back from the boardwalk behind a front porch two deep under the roof; a side gallery (two wide on
 * a wide plot, a plain walk under the eave on a narrow one) runs from the porch down one side to a back porch, and from
 * there a step or a ladder goes down to the boat landing behind, where a punt lies moored by a post with a lantern on its
 * head, crab traps or lobster pots stacked on the boards. Rocking chairs and a side table on the porch, a hammock slung
 * between two posts of the side gallery, nets drying over the rail, lanterns under the beams.
 *
 * <ul>
 *   <li><b>Bayou</b> (an Acadian camp): board walls, tall shuttered windows, a steep tin roof with its gable to the
 *   boardwalk, bell-cast over the galleries so the eaves sweep low; a stovepipe; Spanish moss hanging from the eaves.</li>
 *   <li><b>Tropical</b>: palm-wood and bamboo under a deep hipped roof of thatch or bamboo that sweeps down over the
 *   verandas on every side; lattice windows.</li>
 *   <li><b>Maritime</b>: a fisher's house of boards or whitewash under a straight slate gable, lobster pots and nets.</li>
 * </ul>
 *
 * <p>Inside: a kitchen and living room round the stove by the door, the bedroom behind a partition (one room on a
 * small plot), a boarded ceiling with a lantern. On dry ground the same house stands on its posts with lattice skirting
 * between them and a yard behind instead of the landing.
 *
 * <p>Params (all optional): {@code roof} (a RoofForm name: gable, hip, half_hip), {@code side_walk} (boolean, default
 * true: the side gallery or walk), {@code station}, {@code household} (see {@link Household}).
 */
public final class StiltHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/stilt_house"), ArchetypeCategory.HOUSING, Set.of(), 2,
		9, 12, 10, 13, 10, 14, SettlementTier.CAMP, Set.of(Culture.BAYOU, Culture.TROPICAL, Culture.MARITIME), Set.of(ArchetypeInfo.SiteFlag.ON_WATER),
		List.of(), 0, 1.5);

	/** The three traditions of the house over the water. */
	enum Flavour {
		BAYOU, TROPICAL, MARITIME
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
		Flavour fl = flavour(d);
		boolean walk = d.param("side_walk", true);
		boolean wrapLeft = d.chance(0.5);
		int side = !walk ? 0 : w >= 11 ? 2 : 1;
		int bx0;
		int bx1;
		if (side == 0) {
			bx0 = 1;
			bx1 = w - 2;
		} else if (wrapLeft) {
			bx0 = side;
			bx1 = w - 2;
		} else {
			bx0 = 1;
			bx1 = w - 1 - side;
		}
		// the side the galleries wrap round (and where the landing is): its outer column
		int xo = side == 0 ? -1 : wrapLeft ? 0 : w - 1;
		int street = Math.max(0, Terraces.street(d, (bx0 + bx1) / 2));
		int base = floor(d, 0, 0, w - 1, dp - 1, street);
		// a row of steps in front where the floor stands proud of the boardwalk
		int gz0 = base - street >= 1 ? 1 : 0;
		int bz0 = gz0 + 2;
		int bd = Math.clamp(dp - bz0 - 2, 5, 8);
		int bz1 = Math.min(dp - 1, bz0 + bd - 1);
		int nb = dp - 1 - bz1;
		int sh = Math.max(4, h.walls().storeyHeight());
		Body body = new Body(bx0, bz0, bx1, bz1, base, new int[]{sh}, false, false);
		d.body(bx0, bz0, bx1, bz1);
		int plate = body.plateY();
		d.eave(plate);
		int doorGuess = (bx0 + bx1) / 2 + (bx1 - bx0 >= 7 ? (wrapLeft ? -1 : 1) : 0);
		Facade facade = Facade.plan(d, body, doorGuess, 1, 1, 0.6);
		Integer doorCol = HouseBuilder.doorColumn(facade);
		int door = doorCol != null ? doorCol : (bx0 + bx1) / 2;

		// the galleries' extent: the front porch across the body and the side gallery, the side gallery down to the back
		// porch, the back porch across the back
		int px0 = side > 0 && wrapLeft ? 0 : bx0;
		int px1 = side > 0 && !wrapLeft ? w - 1 : bx1;
		boolean backPorch = nb >= 1;
		boolean landing = nb >= 2;
		int zb = bz1 + 1;
		int zRoof1 = landing ? zb : bz1;

		// ---- ground: piles under the house and every gallery, the floors over them
		Groundwork.stilts(d, bx0, bz0, bx1, bz1, base - 1, 2);
		Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
		for (int z = gz0; z <= (backPorch ? zb : bz1); z++) {
			for (int x = px0; x <= px1; x++) {
				boolean porch = z < bz0;
				boolean sideCol = side > 0 && (wrapLeft ? x < bx0 : x > bx1);
				boolean back = backPorch && z == zb;
				if (porch || sideCol || back) {
					Verandas.deck(d, x, z, base);
				}
			}
		}
		// piles along the galleries' outer edges, every other cell and at the corners
		for (int x = px0; x <= px1; x++) {
			if ((x - px0) % 2 == 0 || x == px1) {
				Verandas.pile(d, x, gz0, base - 2);
				if (backPorch) {
					Verandas.pile(d, x, zb, base - 2);
				}
			}
		}
		if (side > 0) {
			for (int z = gz0; z <= (backPorch ? zb : bz1); z++) {
				if ((z - gz0) % 2 == 0 || z == bz1) {
					Verandas.pile(d, xo, z, base - 2);
				}
			}
		}

		// ---- the house
		Habits.WallKind kind = h.walls().kind();
		if (kind == Habits.WallKind.PLANK || kind == Habits.WallKind.BAMBOO) {
			boards(d, body);
		} else {
			HouseBuilder.shell(d, body, facade);
		}
		HouseBuilder.openings(d, body, facade, true);
		// a boarded ceiling under the roof: the attic closed, a lantern hung from it
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = bz0 + 1; z <= bz1 - 1; z++) {
				for (int x = bx0 + 1; x <= bx1 - 1; x++) {
					if (d.isEmpty(x, plate, z)) {
						d.set(x, plate, z, d.mat(Role.CEILING).full());
					}
				}
			}
		}

		// ---- the roof over house and galleries
		int ex0 = side > 0 && wrapLeft ? 0 : bx0 - 1;
		int ex1 = side > 0 && !wrapLeft ? w - 1 : bx1 + 1;
		int ez0 = gz0 - 1;
		int ez1 = Math.min(dp - 1, zRoof1 + 1);
		RoofForm form = roofForm(d, fl);
		Roofs.Roof roof = roof(d, fl, form, ex0, ez0, ex1, ez1, plate, base);
		boolean gable = form != RoofForm.HIP;
		if (gable) {
			// a barge board down the gables' rakes: the overhanging rows in the trim
			RoofDress.trimRow(d, roof, Math.max(-1, ez0), d.mat(Role.ROOF_TRIM));
			RoofDress.trimRow(d, roof, ez1, d.mat(Role.ROOF_TRIM));
		}
		// walls carried up to the roof: the house's own walls all round, and the porch's gable end over its beam
		Mat boards = d.mat(Role.GABLE);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side wall : Side.of(bx0, bz0, bx1, bz1)) {
				for (int i = 0; i < wall.length(); i++) {
					fillTo(d, roof, wall.x(i), wall.z(i), plate + 1, wallPaint(d));
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = px0; x <= px1; x++) {
				d.setIfAbsent(x, plate, gz0, beam.log(Direction.Axis.X));
				if (landing) {
					d.setIfAbsent(x, plate, zb, beam.log(Direction.Axis.X));
				}
			}
			if (side == 2) {
				for (int z = gz0; z <= zRoof1; z++) {
					d.setIfAbsent(xo, plate, z, beam.log(Direction.Axis.Z));
				}
			}
		}
		if (gable) {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = px0; x <= px1; x++) {
					fillTo(d, roof, x, gz0, plate + 1, boards.full());
					if (landing) {
						fillTo(d, roof, x, zb, plate + 1, boards.full());
					}
				}
			}
			gableVent(d, roof, (px0 + px1) / 2, gz0, plate, fl);
		}
		// posts under the beams and the eaves: the porch's front corners and either side of its middle, the side
		// gallery's outer line every third row, the back porch's corners
		post(d, roof, px0, gz0, base, true);
		post(d, roof, px1, gz0, base, true);
		for (int x : new int[]{bx0, bx1, door - 2, door + 2}) {
			if (x > px0 && x < px1 && Math.abs(x - door) >= 2) {
				post(d, roof, x, gz0, base, false);
			}
		}
		if (side == 2) {
			for (int z = gz0 + 3; z < zRoof1; z += 3) {
				post(d, roof, xo, z, base, false);
			}
			post(d, roof, xo, zRoof1, base, true);
		}
		if (landing) {
			post(d, roof, px0, zb, base, true);
			post(d, roof, px1, zb, base, true);
		}

		// ---- the stove (a stovepipe through the roof), the rooms
		int ix0 = bx0 + 1;
		int ix1 = bx1 - 1;
		int iz0 = bz0 + 1;
		int iz1 = bz1 - 1;
		int depthIn = iz1 - iz0 + 1;
		boolean partition = depthIn >= 5 && ix1 - ix0 >= 3;
		int zp = partition ? iz0 + depthIn / 2 : iz1 + 1;
		// the back door, on the landing's side, opening onto the back porch
		int backDoor = Integer.MIN_VALUE;
		if (backPorch) {
			int bdx = side > 0 ? (wrapLeft ? ix0 : ix1) : (door <= (ix0 + ix1) / 2 ? ix1 : ix0);
			Side backWall = body.side(0, Dirs.BACK);
			Openings.door(d, backWall, bdx, base, 1, Openings.DoorOpts.BACK);
			backDoor = bdx;
		}
		int sx = wrapLeft ? ix1 : ix0;
		int sz = partition ? zp - 1 : iz1;
		if (sx == backDoor && sz == iz1) {
			sz = iz1 - 1;
		}
		int[] stove = null;
		if (ix1 - ix0 >= 3 && !(sx == door && sz == iz0)) {
			Furniture.hearth(d, sx, base, sz, sx == ix0 ? Dirs.RIGHT : Dirs.LEFT, false);
			// a stovepipe where the culture keeps a chimney; under a tropical roof the smoke finds its own way out
			if (h.chimney() > 0) {
				Chimneys.stack(d, sx, sz, base + 2, roof, roof.ridgeY + 1);
			}
			stove = new int[]{sx, sz};
		}
		interior(d, body, door, backDoor, stove, partition, zp, wrapLeft, fl);

		// ---- the galleries' life
		porchLife(d, roof, px0, px1, gz0, base, plate, door, bx0, bx1, fl);
		if (side == 2) {
			sideGallery(d, roof, xo, wrapLeft, gz0, zRoof1, base, plate, fl);
		}
		if (backPorch) {
			backLife(d, roof, body, px0, px1, zb, base, backDoor, landing, xo, wrapLeft, fl);
		}
		// the way up from the boardwalk onto the porch
		if (base > street || gz0 > 0) {
			Groundwork.approach(d, door, base, gz0);
			for (int dx : new int[]{-1, 1}) {
				int x = door + dx;
				if (gz0 > 0 && Verandas.in(d, x, 0) && d.isAbsent(x, base - 1, 0) && Spots.standable(d, x, base - 1, 0)) {
					Verandas.pot(d, x, base - 1, 0);
				}
			}
		}
		// lattice skirting the piles on dry ground
		skirt(d, px0, px1, gz0, zb, backPorch, xo, base);
		if (fl == Flavour.BAYOU) {
			moss(d, roof, base);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, roof.ridgeY + 3));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Which tradition the house follows: its culture, or for others the nearest by their habits. */
	static Flavour flavour(Draft d) {
		Culture c = d.style().culture();
		if (c == Culture.BAYOU) {
			return Flavour.BAYOU;
		}
		if (c == Culture.TROPICAL) {
			return Flavour.TROPICAL;
		}
		if (c == Culture.MARITIME) {
			return Flavour.MARITIME;
		}
		Habits h = d.habits();
		if (h.roof().thatch() || h.roof().eaves() >= 2 || h.windows().lattice()) {
			return Flavour.TROPICAL;
		}
		return h.yard().fence() == Habits.FenceKind.WALL ? Flavour.MARITIME : Flavour.BAYOU;
	}

	/** The roof's form: gable (to the boardwalk) or hip, from the {@code roof} param or the tradition. */
	private static RoofForm roofForm(Draft d, Flavour fl) {
		RoofForm chosen = switch (fl) {
			case TROPICAL -> d.chance(0.8) ? RoofForm.HIP : RoofForm.HALF_HIP;
			case MARITIME -> RoofForm.GABLE;
			case BAYOU -> d.chance(0.8) ? RoofForm.GABLE : RoofForm.HALF_HIP;
		};
		RoofForm p = HouseBuilder.roofParam(d, chosen);
		return switch (p) {
			case HIP, PAGODA, MANSARD, DOME, CONE -> RoofForm.HIP;
			case HALF_HIP -> RoofForm.HALF_HIP;
			default -> RoofForm.GABLE;
		};
	}

	/**
	 * The roof over the eave rectangle: a gable with its ridge running back from the boardwalk, or a hip; bell-cast in
	 * the bayou and the tropics (shallow over the galleries, steep over the house), straight by the sea; its pitch eased
	 * where the plot is low. Thatch where the culture thatches.
	 */
	private static Roofs.Roof roof(Draft d, Flavour fl, RoofForm form, int x0, int z0, int x1, int z1, int plate, int base) {
		Habits h = d.habits();
		double pitch = Math.max(0.75, h.roof().pitch());
		int half = Math.min(x1 - x0, form == RoofForm.GABLE ? 99 : z1 - z0) / 2 + 1;
		boolean curved = fl != Flavour.MARITIME;
		while (pitch > 0.5) {
			double rise = curved ? Roofs.Profile.curved(pitch).at(half) : Roofs.Profile.straight(pitch).at(half);
			if (plate + rise + 3 < d.maxHeight()) {
				break;
			}
			pitch -= 0.125;
		}
		Roofs.Profile prof = curved ? Roofs.Profile.curved(pitch) : Roofs.Profile.straight(pitch);
		Roofs.Field f = switch (form) {
			case HIP -> Roofs.hip(x0, z0, x1, z1, prof);
			case HALF_HIP -> Roofs.halfHip(x0, z0, x1, z1, Direction.Axis.Z, prof, Math.max(1, (x1 - x0) / 2 - 2));
			default -> Roofs.gable(x0, z0, x1, z1, Direction.Axis.Z, prof);
		};
		// thatch only where it can be laid in courses (bales alone step like a ziggurat)
		boolean thatch = (h.roof().thatch() || fl == Flavour.TROPICAL && d.chance(0.5)) && d.mat(Role.THATCH).has(Form.STAIRS);
		Mat cover = thatch ? d.mat(Role.THATCH) : d.mat(Role.ROOF);
		Mat trim = thatch ? d.mat(Role.THATCH) : d.mat(Role.ROOF_TRIM);
		try (var s = d.stage(StageKind.ROOF)) {
			return Roofs.raster(d, f, plate, Math.max(0, x0), Math.max(-1, z0), Math.min(d.frontage() - 1, x1), Math.min(d.depth() - 1, z1), cover, trim,
				thatch ? Habits.Ridge.FULL : h.roof().ridge());
		}
	}

	/** The house's wall material for courses carried up under the roof. */
	private static dev.voidmc.hearth.build.draft.Paint wallPaint(Draft d) {
		Habits.WallKind k = d.habits().walls().kind();
		return k == Habits.WallKind.PLANK || k == Habits.WallKind.BAMBOO || k == Habits.WallKind.LOG || k == Habits.WallKind.TIMBER_FRAME
			? d.mat(Role.GABLE).full() : d.mat(Role.WALL).full();
	}

	/** Fills (x, from.., z) up to the roof's underside with {@code p} where nothing is drawn. */
	private static void fillTo(Draft d, Roofs.Roof roof, int x, int z, int from, dev.voidmc.hearth.build.draft.Paint p) {
		if (!roof.covers(x, z)) {
			return;
		}
		int under = roof.bottomY(x, z);
		for (int y = from; y < under; y++) {
			if (d.isAbsent(x, y, z)) {
				d.set(x, y, z, p);
			}
		}
	}

	/**
	 * A post from standing level y up to whatever is drawn above it (a beam or the roof): a log at a corner, a slender
	 * turned post (a column of fences) between.
	 */
	private static void post(Draft d, Roofs.Roof roof, int x, int z, int y, boolean corner) {
		if (!Verandas.in(d, x, z) || !d.isEmpty(x, y, z)) {
			return;
		}
		int top = y;
		while (top < y + 8 && d.isAbsent(x, top + 1, z) && (!roof.covers(x, z) || top + 1 < roof.bottomY(x, z))) {
			top++;
		}
		if (corner) {
			Verandas.post(d, x, z, y, top);
		} else {
			Mat f = d.mat(Role.FRAME).has(Form.FENCE) ? d.mat(Role.FRAME) : d.mat(Role.RAILING);
			try (var s = d.stage(StageKind.FRAME)) {
				for (int yy = y; yy <= top; yy++) {
					d.set(x, yy, z, f.fence());
				}
			}
		}
	}

	/**
	 * Board walls (plank and bamboo cultures): corner posts, a plate beam under the roof, a sill beam along the floor's
	 * edge, the boards between; the floor and the room's air. Clean board faces where the culture's framing would put a
	 * post every other column.
	 */
	private static void boards(Draft d, Body b) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat board = d.habits().walls().kind() == Habits.WallKind.PLANK ? d.mat(Role.GABLE) : d.mat(Role.WALL);
		int y0 = b.baseY;
		int plate = b.plateY();
		Floors.boards(d, b.x0 + 1, b.z0 + 1, b.x1 - 1, b.z1 - 1, y0 - 1);
		for (Side s : Side.of(b.x0, b.z0, b.x1, b.z1)) {
			try (var st = d.stage(StageKind.FRAME)) {
				for (int i = 0; i < s.length(); i++) {
					d.set(s.x(i), y0 - 1, s.z(i), beam.log(s.axis()));
				}
			}
			try (var st = d.stage(StageKind.WALLS)) {
				for (int i = 0; i < s.length(); i++) {
					for (int y = y0; y <= plate; y++) {
						d.set(s.x(i), y, s.z(i), s.isCorner(i) ? frame.log(Direction.Axis.Y) : y == plate ? beam.log(s.axis()) : board.full());
					}
				}
			}
		}
		d.carve(b.x0 + 1, y0, b.z0 + 1, b.x1 - 1, plate - 1, b.z1 - 1);
	}

	/** A little window or a carved vent high in the porch's gable. */
	private static void gableVent(Draft d, Roofs.Roof roof, int x, int z, int plate, Flavour fl) {
		if (!roof.covers(x, z)) {
			return;
		}
		int under = roof.bottomY(x, z);
		int y = plate + 2;
		if (under - y < 2 || !d.isSolid(x, y, z)) {
			return;
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			if (fl == Flavour.TROPICAL) {
				d.set(x, y, z, d.mat(Role.SHUTTER).trapdoor(Dirs.BACK, Half.BOTTOM, true));
			} else {
				d.set(x, y, z, d.mat(Role.GLASS).pane());
			}
		}
	}

	/** The rooms: kitchen and living by the door round the stove, the bedroom behind a partition (or one room). */
	private static void interior(Draft d, Body b, int door, int backDoor, int[] stove, boolean partition, int zp, boolean wrapLeft, Flavour fl) {
		int ix0 = b.x0 + 1;
		int ix1 = b.x1 - 1;
		int iz0 = b.z0 + 1;
		int iz1 = b.z1 - 1;
		int y = b.baseY;
		int h = b.height(0) - 1;
		if (partition) {
			// the partition's door on the side away from the stove
			int pdx = wrapLeft ? ix0 + 1 : ix1 - 1;
			if (stove != null && pdx == stove[0]) {
				pdx = wrapLeft ? ix0 : ix1;
			}
			Partitions.alongX(d, zp, ix0, ix1, y, h, pdx, Dirs.BACK);
			Rooms.Room front = new Rooms.Room(ix0, iz0, ix1, zp - 1, y, h);
			front.keep(door, iz0);
			front.keep(pdx, zp - 1);
			if (stove != null) {
				front.block(stove[0], stove[1]);
				front.keep(stove[0] + (stove[0] == ix0 ? 1 : -1), stove[1]);
			}
			Rooms.Room back = new Rooms.Room(ix0, zp + 1, ix1, iz1, y, h);
			back.keep(pdx, zp + 1);
			if (backDoor != Integer.MIN_VALUE) {
				back.keep(backDoor, iz1);
			}
			int beds = Beds.place(d, back, 2);
			if (beds < 2) {
				beds += Beds.place(d, front, 2 - beds);
			}
			Rooms.furnish(d, back, Rooms.Type.BEDROOM, Math.max(0, 2 - beds), JobType.NONE);
			Rooms.furnish(d, front, Rooms.Type.KITCHEN, 0, JobType.NONE);
			Household.dress(d, front, back, stove);
		} else {
			Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, y, h);
			room.keep(door, iz0);
			if (backDoor != Integer.MIN_VALUE) {
				room.keep(backDoor, iz1);
			}
			if (stove != null) {
				room.block(stove[0], stove[1]);
				room.keep(stove[0] + (stove[0] == ix0 ? 1 : -1), stove[1]);
			}
			int want = (ix1 - ix0 + 1) * (iz1 - iz0 + 1) >= 15 ? 2 : 1;
			int placed = Beds.place(d, room, want);
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, Math.max(0, want - placed), JobType.NONE);
			Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
			Household.dress(d, room, room, stove);
		}
	}

	/** The front porch: rocking chairs either side of the door with a side table, pots at the corners, lanterns. */
	private static void porchLife(Draft d, Roofs.Roof roof, int px0, int px1, int gz0, int base, int plate, int door, int bx0, int bx1, Flavour fl) {
		int lane = gz0 + 1;
		// railings along the front, open at the steps
		for (int x = px0; x <= px1; x++) {
			if (x != door) {
				Verandas.rail(d, x, gz0, base);
			}
		}
		// the porch's ends closed by a rail where it stops at the house's corner
		if (px1 == bx1) {
			Verandas.rail(d, px1, lane, base);
		}
		if (px0 == bx0) {
			Verandas.rail(d, px0, lane, base);
		}
		// chairs against the wall, a table between
		boolean any = false;
		for (int dx : new int[]{-2, 2, -3, 3}) {
			int x = door + dx;
			if (x > px0 && x < px1 && Verandas.rocker(d, x, base, lane, Dirs.FRONT)) {
				any = true;
				int tx = x + Integer.signum(dx);
				if (tx > px0 && tx < px1 && Math.abs(dx) == 2) {
					Verandas.sideTable(d, tx, base, lane);
				}
				if (Math.abs(dx) == 2) {
					break;
				}
			}
		}
		if (!any) {
			Verandas.rocker(d, door + 1 < px1 ? door + 1 : door - 1, base, lane, Dirs.FRONT);
		}
		// lanterns under the beam either side of the steps, pots on the rail line's corners
		Verandas.lantern(d, door, plate - 1, gz0);
		for (int x : new int[]{px0 + 1, px1 - 1}) {
			if (Math.abs(x - door) >= 2 && d.isEmpty(x, base, lane)) {
				Verandas.pot(d, x, base, lane);
			}
		}
		// pots of flowers on the rail's heads either side of the steps
		for (int x : new int[]{door - 1, door + 1}) {
			Verandas.railPot(d, x, base + 1, gz0);
		}
		// nets over the front rail by the sea
		if (fl == Flavour.MARITIME) {
			for (int x = px0; x <= px1; x++) {
				if (Math.abs(x - door) >= 3 && Noise.unit(d.seed() ^ 0x4E7, x, 0, gz0) < 0.25 && Waterside.net(d, x, base + 1, gz0)) {
					break;
				}
			}
		}
	}

	/** The side gallery: railing, a hammock between two posts, nets hung over the rail. */
	private static void sideGallery(Draft d, Roofs.Roof roof, int xo, boolean wrapLeft, int z0, int z1, int base, int plate, Flavour fl) {
		Direction in = wrapLeft ? Dirs.RIGHT : Dirs.LEFT;
		// a hammock in the first bay between posts that is free (posts stand every third row from the front)
		boolean slung = false;
		for (int zp = z0; zp + 3 <= z1 && !slung; zp += 3) {
			if (d.isAbsent(xo, base, zp + 1) && d.isAbsent(xo, base, zp + 2)) {
				slung = Verandas.hammock(d, xo, zp + 1, Dirs.BACK, 2, base);
			}
		}
		for (int z = z0 + 1; z <= z1; z++) {
			Verandas.rail(d, xo, z, base);
		}
		if (fl == Flavour.MARITIME) {
			int n = 0;
			for (int z = z0 + 1; z <= z1 && n < 2; z++) {
				if (Noise.unit(d.seed() ^ 0x4E8, xo, 0, z) < 0.3 && Waterside.net(d, xo, base + 1, z)) {
					n++;
				}
			}
		}
		// a lantern under the gallery beam halfway down
		Verandas.lantern(d, xo, plate - 1, (z0 + z1) / 2);
	}

	/**
	 * The back porch and what lies beyond: on the water a landing a step down (a ladder where the drop is deeper) with a
	 * punt moored by a post and lantern, traps stacked on the boards; on dry ground a yard with a woodpile.
	 */
	private static void backLife(Draft d, Roofs.Roof roof, Body b, int px0, int px1, int zb, int base, int backDoor, boolean landing, int xo,
		boolean wrapLeft, Flavour fl) {
		int dp = d.depth();
		// traps and barrels along the porch away from the door and the way down
		int far = wrapLeft ? px1 - 1 : px0 + 1;
		Waterside.traps(d, far, base, zb, fl == Flavour.TROPICAL ? 1 : 2);
		if (d.isEmpty(far + (wrapLeft ? -1 : 1), base, zb) && Math.abs(far + (wrapLeft ? -1 : 1) - backDoor) > 1) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(far + (wrapLeft ? -1 : 1), base, zb, States.facing(Blocks.BARREL, Direction.UP)));
		}
		Verandas.lantern(d, backDoor == Integer.MIN_VALUE ? (px0 + px1) / 2 : backDoor + (wrapLeft ? 1 : -1), b.plateY() - 1, zb);
		if (!landing) {
			return;
		}
		// the landing on the gallery side of the back, a step (or a ladder) down from the porch
		int lw = Math.min(5, (px1 - px0 + 1) / 2 + 1);
		int lx0 = wrapLeft ? px0 : px1 - lw + 1;
		int lx1 = lx0 + lw - 1;
		int z0 = zb + 1;
		int z1 = dp - 1;
		boolean wet = false;
		int surface = Site.NONE;
		for (int z = z0; z <= z1; z++) {
			for (int x = lx0; x <= lx1; x++) {
				if (d.site().isWater(x, z)) {
					wet = true;
					surface = Math.max(surface, d.site().water(x, z));
				}
			}
		}
		int level;
		if (wet) {
			level = Math.max(base - 1, surface);
		} else {
			level = Math.min(base - 1, d.site().meanGround(lx0, z0, lx1, z1));
		}
		if (level >= base) {
			level = base;
		}
		int lv = level;
		for (int z = z0; z <= z1; z++) {
			for (int x = lx0; x <= lx1; x++) {
				int g = d.site().ground(x, z);
				if (!wet && g >= lv) {
					continue;
				}
				Verandas.deck(d, x, z, lv);
				if ((x == lx0 || x == lx1) && (z == z0 || z == z1)) {
					Verandas.pile(d, x, z, lv - 2);
				}
			}
		}
		// the way down: a stair onto the landing, a ladder where it is deeper than a step
		int sx = wrapLeft ? lx0 + 1 : lx1 - 1;
		if (base - lv == 1) {
			if (d.isEmpty(sx, lv, z0)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(sx, lv, z0, d.mat(Role.FLOOR).stairsOrSlab(Dirs.FRONT, Half.BOTTOM)));
			}
		} else if (base - lv >= 2) {
			Verandas.pile(d, sx, zb, base - 2);
			Groundwork.ladder(d, sx, z0, lv, base - 1, Dirs.BACK);
		}
		// the mooring post at the landing's outer corner, the punt alongside
		int mx = wrapLeft ? lx0 : lx1;
		if (wet) {
			Waterside.mooring(d, mx, z1, lv);
			d.poi(PoiType.DOCK, wrapLeft ? lx0 + 1 : lx1 - 1, lv, z1, Dirs.BACK);
			boolean moored = false;
			Direction run = wrapLeft ? Dirs.RIGHT : Dirs.LEFT;
			int bx = wrapLeft ? lx1 + 1 : lx0 - 1;
			for (int len = 4; len >= 3 && !moored; len--) {
				int start = wrapLeft ? bx : bx - len + 1;
				moored = Waterside.boat(d, start, z1, Dirs.RIGHT, len);
				if (!moored && z1 - 1 > z0) {
					moored = Waterside.boat(d, start, z1 - 1, Dirs.RIGHT, len);
				}
			}
			// traps on the landing's back corner
			int tx = wrapLeft ? lx1 : lx0;
			Waterside.traps(d, tx, lv, z1, 2);
			if (lw >= 4) {
				Waterside.traps(d, tx + (wrapLeft ? -1 : 1), lv, z1, 1);
			}
		} else {
			d.poi(PoiType.VIEWPOINT, sx, lv, Math.min(z1, z0 + 1), Dirs.BACK);
			int wx = wrapLeft ? lx1 : lx0;
			if (z1 - z0 >= 1 && d.isAbsent(wx, Spots.level(d, wx, z1), z1)) {
				dev.voidmc.hearth.build.kit.Garden.woodpile(d, wx, Spots.level(d, wx, z1), z1, wrapLeft ? Dirs.LEFT : Dirs.RIGHT, 2);
			}
		}
	}

	/** Lattice between the piles along the galleries' outer edges where they stand over dry ground. */
	private static void skirt(Draft d, int px0, int px1, int gz0, int zb, boolean backPorch, int xo, int base) {
		for (int x = px0; x <= px1; x++) {
			Verandas.lattice(d, x, gz0, Dirs.FRONT, base - 2);
		}
		if (xo >= 0) {
			Direction out = xo == 0 ? Dirs.LEFT : Dirs.RIGHT;
			for (int z = gz0 + 1; z <= (backPorch ? zb : zb - 1); z++) {
				Verandas.lattice(d, xo, z, out, base - 2);
			}
		}
	}


	/** The floor level: the street's, at least a block over the water and two over the ground under the house. */
	private static int floor(Draft d, int x0, int z0, int x1, int z1, int street) {
		int base = street;
		int lift = Math.max(1, d.habits().plan().stiltHeight());
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int wy = d.site().water(x, z);
				int g = d.site().ground(x, z);
				base = Math.max(base, wy == Site.NONE ? g + 1 : Math.min(wy + lift, street + 2));
			}
		}
		// on dry ground it stands a little proud on its posts
		if (!d.site().anyWater(x0, z0, x1, z1)) {
			base = Math.max(base, d.site().meanGround(x0, z0, x1, z1) + 2);
		}
		// never more than two steps up from the street: the steps have to fit in front
		return Math.min(Math.min(base, street + 2), 4);
	}

	/** Moss hanging from the eaves (Spanish moss with Expanse, pale hanging moss without). */
	private static void moss(Draft d, Roofs.Roof roof, int base) {
		Block moss = States.block("expanse:spanish_moss", States.block("minecraft:pale_hanging_moss", Blocks.VINE));
		boolean tip = moss.defaultBlockState().hasProperty(BlockStateProperties.TIP);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					if (!roof.covers(x, z) || Noise.unit(d.seed() ^ 0x3055, x, 0, z) > 0.4) {
						continue;
					}
					// only along the eaves (the long sides), not down the gables' rakes
					boolean edge = (!roof.covers(x - 1, z) || !roof.covers(x + 1, z)) && z > roof.z0 && z < roof.z1;
					int y = roof.bottomY(x, z) - 1;
					if (!edge || y <= base + 1 || !d.isAbsent(x, y, z) || !d.isAbsent(x, y - 1, z) || z < 0 && y - 1 < 3) {
						continue;
					}
					int len = Noise.unit(d.seed() ^ 0x3056, x, 0, z) < 0.5 ? 2 : 1;
					for (int k = 0; k < len; k++) {
						var st = moss.defaultBlockState();
						if (tip) {
							st = st.setValue(BlockStateProperties.TIP, k == len - 1);
						}
						d.set(x, y - k, z, st);
					}
				}
			}
		}
	}
}
