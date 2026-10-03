package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
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
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.Rooflines;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/manor}: the grand house of a town's rich, standing back in its grounds behind a wall and a
 * carriage gate. A drive runs from the gate past formal parterres (low hedges round beds of flowers, a fountain on the
 * axis) to the steps of a raised main block of two storeys and an attic, its centre marked by a pediment over a
 * columned porch; on wider grounds two wings come forward to frame the court. The roof is one surface over the whole
 * house, hipped or gabled by the culture, its valleys where the wings meet, dormers along it and tall chimneys; hill
 * towns give it low tiles and a loggia, the desert flat roofs with merlons and a dome over the hall, the karst curved
 * roofs with lifted corners. Inside: an entrance hall with a wide stair, a library of shelves and lecterns, a dining
 * room with a long table, a drawing room; bedrooms above; the kitchen and the servants in a wing. Behind, a garden of
 * fruit trees and beds.
 *
 * <p>Params: {@code roof} (RoofForm name), {@code variant} (int). Sizes: 15–25 square; height 10–16.
 */
public final class Manor implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/manor"), ArchetypeCategory.HOUSING, Set.of(), 6,
		15, 25, 15, 25, 10, 16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 4, 0.5);

	private static final RoofForm[] FORMS = {RoofForm.HIP, RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.MANSARD, RoofForm.PAGODA, RoofForm.FLAT,
		RoofForm.CROSS_GABLE, RoofForm.DOME, RoofForm.GAMBREL};

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
		int maxH = d.maxHeight();
		RoofForm form = HouseBuilder.roofParam(d, h.roof().pick(d.random(), FORMS));
		boolean dome = form == RoofForm.DOME;
		if (dome || form == RoofForm.CROSS_GABLE || form == RoofForm.GAMBREL) {
			form = dome ? RoofForm.FLAT : form == RoofForm.GAMBREL ? RoofForm.GABLE : RoofForm.HIP;
		}
		if (form == RoofForm.PAGODA && !t.eastern()) {
			form = RoofForm.HIP;
		}
		boolean flat = form == RoofForm.FLAT;
		int sh = Math.max(4, h.walls().storeyHeight());

		// the grounds: side margins, the main block toward the back, wings forward, the court and the garden
		int mx = w >= 19 ? 2 : 1;
		int md = Math.clamp(Math.round(dp * 0.38f), 7, 9);
		int backGarden = dp >= 20 ? 4 : dp >= 17 ? 3 : 2;
		int hz1 = dp - 1 - backGarden;
		int hz0 = hz1 - md + 1;
		int ww = Math.clamp(Math.round(w * 0.24f), 5, 7);
		int wl = Math.min(7, hz0 - 5);
		boolean wings = wl >= 3 && w - 2 * mx - 2 * ww >= 5;
		int fz0 = hz0 - (wings ? wl : 0);
		int x0 = mx;
		int x1 = w - 1 - mx;
		int base = 1;

		// fit the height: a raised ground storey, the first floor, an attic under the roof
		Roofs.Opts opts = Roofs.Opts.of(d);
		if (opts.pitch() > 1.0) {
			opts = opts.withPitch(1.0);
		}
		int ground = sh + 1;
		boolean attic = !flat;
		Body main = null;
		for (int pass = 0; pass < 6; pass++) {
			int[] heights = attic ? new int[]{ground, sh, 1} : new int[]{ground, sh};
			main = new Body(x0, hz0, x1, hz1, base, heights, false, false);
			int top = flat ? main.plateY() + (dome ? 6 : 2) : TownHouses.ridge(d, main, form, Direction.Axis.X, opts) + 2;
			if (top < maxH) {
				break;
			}
			if (ground > sh) {
				ground = sh;
			} else if (opts.pitch() > 0.5) {
				opts = opts.withPitch(0.5);
			} else if (attic) {
				attic = false;
			} else {
				form = RoofForm.FLAT;
				flat = true;
				dome = false;
			}
		}
		d.body(x0, fz0, x1, hz1);
		int[] heights = attic ? new int[]{ground, sh, 1} : new int[]{ground, sh};
		Body left = wings ? new Body(x0, fz0, x0 + ww - 1, hz0, base, java.util.Arrays.copyOf(heights, 2), false, false) : null;
		Body right = wings ? new Body(x1 - ww + 1, fz0, x1, hz0, base, java.util.Arrays.copyOf(heights, 2), false, false) : null;
		int door = (x0 + x1) / 2;

		// ground: the house on a plinth a step above the grounds, the grounds levelled
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Groundwork.footings(d, x0, hz0, x1, hz1, base - 1);
		if (wings) {
			Groundwork.footings(d, left.x0, left.z0, left.x1, left.z1, base - 1);
			Groundwork.footings(d, right.x0, right.z0, right.x1, right.z1, base - 1);
		}

		// walls and openings (none in the walls the wings and the main block share)
		// the door on the axis, whatever the bays: the plan is symmetrical about it
		int doorW = w >= 19 ? 2 : 1;
		int dc = door;
		Facade fm = filtered(Facade.plan(d, main, -1, 1, 1, 0.85),
			(side, o) -> !(side == Dirs.FRONT && o.at() + o.width() - 1 >= dc - 1 && o.at() <= dc + doorW));
		fm.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, door, doorW));
		if (wings) {
			int lx1 = left.x1;
			int rx0 = right.x0;
			fm = filtered(fm, (side, o) -> !(side == Dirs.FRONT && (o.at() <= lx1 + 1 || o.at() + o.width() - 1 >= rx0 - 1)));
		}
		d.eave(main.plateY());
		Openings.WindowStyle style = TownHouses.windowStyle(d, t);
		TownHouses.shell(d, main, fm, t.masonry() && d.chance(0.5));
		TownHouses.openings(d, main, fm, t, style, new java.util.HashSet<>(), true);
		Facade fl = null;
		Facade fr = null;
		if (wings) {
			fl = filtered(Facade.plan(d, left, -1, 1, 1, 0.7), (side, o) -> side != Dirs.BACK);
			fr = filtered(Facade.plan(d, right, -1, 1, 1, 0.7), (side, o) -> side != Dirs.BACK);
			TownHouses.shell(d, left, fl, false);
			TownHouses.shell(d, right, fr, false);
			TownHouses.openings(d, left, fl, t, style, new java.util.HashSet<>(), false);
			TownHouses.openings(d, right, fr, t, style, new java.util.HashSet<>(), false);
			// the wings open into the main block through its front wall, on both floors
			for (Body wing : new Body[]{left, right}) {
				int cx = (wing.x0 + wing.x1) / 2;
				for (int k = 0; k < 2; k++) {
					Partitions.door(d, cx, wing.floorY(k), hz0, Dirs.BACK);
				}
			}
		}
		TownHouses.dress(d, main, t, flat, false, Dirs.LEFT);

		// the porch at the door: columns carrying a pediment, steps down to the drive
		Side front = main.side(0, Dirs.FRONT);
		Fronts.doorCase(d, front, door, doorW, base, t);
		int porchW = doorW;
		portico(d, main, door, porchW, t);

		// the roof, one surface over the whole house
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = TownHouses.flat(d, main, t.walls() == WallKind.ADOBE && d.chance(0.7));
			if (wings) {
				Roofs.flat(d, left.x0, left.z0, left.x1, left.z1, left.plateY(), true);
				Roofs.flat(d, right.x0, right.z0, right.x1, right.z1, right.plateY(), true);
			}
			if (dome) {
				dome(d, main, deck);
			}
		} else {
			roof = roof(d, main, left, right, form, opts, t, fm);
		}

		// chimneys at the ends of the main block, by the hearths of the library and the dining room
		int[] r0 = main.rect(0);
		int hzc = (r0[1] + r0[3]) / 2;
		List<int[]> hearths = new ArrayList<>();
		for (int hx : new int[]{r0[0] + 1, r0[2] - 1}) {
			if (!d.chance(Math.max(0.5, h.chimney()))) {
				continue;
			}
			int surface = roof != null && roof.covers(hx, hzc) ? roof.surfaceY(hx, hzc) : deck;
			if (surface == Integer.MIN_VALUE || surface + 3 >= maxH) {
				continue;
			}
			Furniture.hearth(d, hx, base, hzc, hx == r0[0] + 1 ? Dirs.RIGHT : Dirs.LEFT, false);
			if (roof != null) {
				Chimneys.stack(d, hx, hzc, base + 2, roof, Math.min(roof.ridgeY + 2, maxH - 1));
			} else {
				Mat stone = d.mat(Role.CHIMNEY);
				int dk = deck;
				d.inStage(StageKind.ROOF, () -> {
					for (int y = base + 2; y <= dk; y++) {
						d.set(hx, y, hzc, stone.full());
					}
					d.set(hx, dk + 1, hzc, States.lit(Blocks.CAMPFIRE, true).trySetValue(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
				});
			}
			hearths.add(new int[]{hx, hzc});
		}

		// inside: hall and stair, library, dining room; bedrooms above; kitchen and servants in the wings
		interior(d, main, left, right, door, porchW, hearths, roof);

		// the grounds: wall and carriage gate, drive, parterres, fountain, lamps; the garden behind
		grounds(d, main, fz0, door, porchW, t);
		garden(d, main, t);
		if (h.snow()) {
			Garden.snowBanks(d, x0, hz0, x1, hz1, base, door);
		}
		int crest = roof != null ? roof.ridgeY : deck + (dome ? 6 : 3);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, crest + 2));
	}

	/** A copy of a facade plan keeping only the openings a test accepts (posts and all kept). */
	private static Facade filtered(Facade f, java.util.function.BiPredicate<Direction, Facade.Opening> keep) {
		Facade out = new Facade();
		for (Direction side : Dirs.HORIZONTAL) {
			for (Facade.Opening o : f.openings(side)) {
				if (keep.test(side, o)) {
					out.add(side, o);
				}
			}
			for (int p : f.posts(side)) {
				out.post(side, p);
			}
		}
		return out;
	}

	/**
	 * The portico: two columns standing out before the door carrying a flat roof at the first floor, which is a balcony
	 * with a railing and a door out onto it; a step down to the drive.
	 */
	private static void portico(Draft d, Body main, int door, int porchW, Traits t) {
		int z0 = main.z0;
		int xa = door - 1;
		int xb = door + porchW;
		int y1 = main.floorY(1);
		int base = main.baseY;
		Mat floor = d.mat(Role.PAVING);
		Mat col = d.mat(Role.WALL_ACCENT);
		Mat frame = d.mat(Role.FRAME);
		Mat deck = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING);
		boolean timber = t.timber() && !t.masonry();
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0 - 2; z < z0; z++) {
				for (int x = xa; x <= xb; x++) {
					d.set(x, base - 1, z, floor.full());
					for (int y = base; y < y1 - 1; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{xa, xb}) {
				for (int y = base; y < y1 - 1; y++) {
					d.set(x, y, z0 - 2, timber ? frame.log(Direction.Axis.Y) : y == y1 - 2 ? col.full() : col.has(Form.WALL) ? col.wall() : col.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0 - 2; z < z0; z++) {
				for (int x = xa; x <= xb; x++) {
					d.set(x, y1 - 1, z, deck.full());
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = xa; x <= xb; x++) {
				d.set(x, y1, z0 - 2, rail.fence());
			}
			d.set(xa, y1, z0 - 1, rail.fence());
			d.set(xb, y1, z0 - 1, rail.fence());
			// the step down from the portico to the drive
			for (int x = door; x < door + porchW; x++) {
				d.set(x, base - 1, z0 - 3, floor.has(Form.STAIRS) ? floor.stairs(Dirs.BACK, Half.BOTTOM) : floor.slab(SlabType.BOTTOM));
			}
		}
		Side upper = main.side(1, Dirs.FRONT);
		TownHouses.galleryDoor(d, upper, y1, door);
		d.poi(PoiType.VIEWPOINT, door, y1, z0 - 1, Dirs.FRONT);
		Furniture.hangingLantern(d, door, y1 - 1, z0 - 1, 0);
	}

	/**
	 * The roof over the main block and the wings as one field (their valleys come out where they meet), a pediment over
	 * the centre of the front, dormers along the main roof, the wing walls filled up to their roof.
	 */
	private static Roofs.Roof roof(Draft d, Body main, Body left, Body right, RoofForm form, Roofs.Opts o, Traits t, Facade f) {
		int[] tr = main.topRect();
		Roofs.Plan plan = Rooflines.plan(d, tr[0], tr[1], tr[2], tr[3], main.plateY(), form, Direction.Axis.X, o, false, false);
		Side front = main.side(main.storeys() - 1, Dirs.FRONT);
		int width = tr[2] - tr[0] + 1;
		int gw = width >= 15 ? 7 : 5;
		if (form != RoofForm.PAGODA && form != RoofForm.MANSARD) {
			plan.crossGable(front, (tr[0] + tr[2]) / 2 - gw / 2, gw, d);
		}
		if (left != null) {
			for (Body wing : new Body[]{left, right}) {
				int[] wr = wing.topRect();
				int e = o.eaves();
				Roofs.Profile p = form == RoofForm.PAGODA ? Roofs.Profile.curved(Math.max(1, o.pitch())) : Roofs.Profile.straight(o.pitch());
				int nx0 = wr[0] - e;
				int nx1 = wr[2] + e;
				int nz0 = wr[1] - e;
				int nz1 = tr[3];
				// the wing's roof is laid from its own eave line: its field shifted to the main roof's base
				int lift = wing.plateY() - main.plateY();
				Roofs.Field wf = (x, z) -> {
					if (x < nx0 || x > nx1 || z < nz0 || z > nz1) {
						return Double.NaN;
					}
					double dd = form == RoofForm.GABLE || form == RoofForm.HALF_HIP ? Math.min(x - nx0, nx1 - x)
						: Math.min(Math.min(x - nx0, nx1 - x), z - nz0);
					return p.at(dd) + lift;
				};
				plan.add(wf, Math.max(0, nx0), Math.max(-1, nz0), Math.min(d.frontage() - 1, nx1), Math.min(d.depth() - 1, nz1));
			}
		}
		// dormers over the windows of the main block between the pediment and the wings
		for (Facade.Opening op : f.openings(Dirs.FRONT)) {
			if (op.kind() == Facade.Kind.WINDOW && Math.abs(op.at() - (tr[0] + tr[2]) / 2) > gw / 2 + 1 && op.at() - 2 > tr[0] && op.at() + 2 < tr[2]
				&& (left == null || op.at() > left.x1 + 1 && op.at() < right.x0 - 1)) {
				plan.dormer(front, op.at(), 1, main.floorY(main.storeys() - 1));
			}
		}
		Roofs.Roof r = plan.draw(d).roof();
		if (left != null) {
			Mat wall = d.mat(Role.WALL);
			Mat gable = d.mat(Role.GABLE);
			for (Body wing : new Body[]{left, right}) {
				int[] wr = wing.topRect();
				int plate = wing.plateY();
				try (var s = d.stage(StageKind.WALLS)) {
					for (Side side : Side.of(wr[0], wr[1], wr[2], wr[3])) {
						for (int i = 0; i < side.length(); i++) {
							int x = side.x(i);
							int z = side.z(i);
							if (!r.covers(x, z) || z >= main.z0) {
								continue;
							}
							for (int y = plate + 1; y < r.bottomY(x, z); y++) {
								d.set(x, y, z, side.out() == Dirs.FRONT ? gable.full() : wall.full());
							}
						}
					}
				}
				// the attic over the wing stays open into the roof
				d.carve(wr[0] + 1, plate + 1, wr[1] + 1, wr[2] - 1, plate + 2, main.z0 - 1);
			}
		}
		return r;
	}

	/** A dome over the hall in the middle of a flat roof (the desert's palaces), on a drum, a finial on top. */
	private static void dome(Draft d, Body main, int deck) {
		int[] tr = main.topRect();
		int cx = (tr[0] + tr[2]) / 2;
		int cz = (tr[1] + tr[3]) / 2;
		int rad = 2;
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		if (deck + 6 >= d.maxHeight()) {
			return;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ellipseCells(cx, cz, rad, rad, false)) {
				d.set(c[0], deck, c[1], wall.full());
			}
		}
		Roofs.Field f = Roofs.dome(cx, cz, rad, 0.9);
		Roofs.raster(d, f, deck + 1, cx - rad - 1, cz - rad - 1, cx + rad + 1, cz + rad + 1, roof, d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		int top = deck + 1;
		for (int y = deck + 1; y < d.maxHeight() - 1; y++) {
			if (d.get(cx, y, cz) != null && !d.isEmpty(cx, y, cz)) {
				top = y;
			}
		}
		if (top + 1 < d.maxHeight()) {
			Mat acc = d.mat(Role.WALL_ACCENT);
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(cx, top + 1, cz, acc.has(Form.WALL) ? acc.wall() : acc.slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * The rooms. Ground floor: the entrance hall across the middle bays with a wide stair up its back wall, the library
	 * on one side (shelves round the walls, lecterns, a reading table), the dining room on the other (a long table).
	 * First floor: a landing at the stair head, bedrooms either side. Wings: the kitchen and stores on one side, a
	 * drawing room on the other, bedrooms for the household above.
	 */
	private static void interior(Draft d, Body main, Body left, Body right, int door, int porchW, List<int[]> hearths, Roofs.Roof roof) {
		int[] r = main.rect(0);
		int ix0 = r[0] + 1;
		int ix1 = r[2] - 1;
		int iz0 = r[1] + 1;
		int iz1 = r[3] - 1;
		int y0 = main.floorY(0);
		int y1 = main.floorY(1);
		int air0 = main.height(0) - 1;
		int air1 = main.height(1) - 1;
		// the hall: the middle bays, walls either side with doors to the library and the dining room
		int hx0 = door - 2;
		int hx1 = door + porchW + 1;
		Partitions.alongZ(d, hx0 - 1, iz0, iz1, y0, air0, iz0 + 1, Dirs.LEFT);
		Partitions.alongZ(d, hx1 + 1, iz0, iz1, y0, air0, iz0 + 1, Dirs.RIGHT);
		// the stair: a switchback in the back of the hall, up to the landing over it
		dev.voidmc.hearth.build.kit.townhome.StairCore core = new dev.voidmc.hearth.build.kit.townhome.StairCore(main, door,
			Dirs.RIGHT, iz1 - 2, Dirs.BACK, 0, 1).build(d);
		Rooms.Room hall = new Rooms.Room(hx0, iz0, hx1, iz1, y0, air0);
		for (int c = 0; c < porchW; c++) {
			hall.keep(door + c, iz0);
		}
		hall.keep(hx0, iz0 + 1);
		hall.keep(hx1, iz0 + 1);
		core.keep(hall, 0);
		Rooms.furnish(d, hall, Rooms.Type.HALL, 0, JobType.NONE);
		// the library
		Rooms.Room lib = new Rooms.Room(ix0, iz0, hx0 - 2, iz1, y0, air0);
		lib.keep(hx0 - 2, iz0 + 1);
		keepWingDoor(lib, left, main);
		blockHearths(lib, hearths);
		library(d, lib);
		// the dining room
		Rooms.Room dine = new Rooms.Room(hx1 + 2, iz0, ix1, iz1, y0, air0);
		dine.keep(hx1 + 2, iz0 + 1);
		keepWingDoor(dine, right, main);
		blockHearths(dine, hearths);
		dining(d, dine);

		// first floor: the landing over the hall, bedrooms either side
		Partitions.alongZ(d, hx0 - 1, iz0, iz1, y1, air1, iz1, Dirs.LEFT);
		Partitions.alongZ(d, hx1 + 1, iz0, iz1, y1, air1, iz1, Dirs.RIGHT);
		Rooms.Room landing = new Rooms.Room(hx0, iz0, hx1, iz1, y1, air1);
		core.keep(landing, 1);
		landing.keep(hx0, iz1);
		landing.keep(hx1, iz1);
		TownHouses.keepDoors(d, landing, r, y1);
		Rooms.furnish(d, landing, Rooms.Type.LIVING, 0, JobType.NONE);
		Rooms.Room bedL = new Rooms.Room(ix0, iz0, hx0 - 2, iz1, y1, air1);
		bedL.keep(hx0 - 2, iz1);
		keepWingDoor(bedL, left, main);
		Rooms.furnish(d, bedL, Rooms.Type.BEDROOM, 2, JobType.NONE);
		Rooms.Room bedR = new Rooms.Room(hx1 + 2, iz0, ix1, iz1, y1, air1);
		bedR.keep(hx1 + 2, iz1);
		keepWingDoor(bedR, right, main);
		Rooms.furnish(d, bedR, Rooms.Type.BEDROOM, 2, JobType.NONE);
		// the attic: a garret under the roof
		if (main.storeys() >= 3 && roof != null) {
			TownHouses.room(d, main, 2, roof);
		}

		// the wings
		if (left != null) {
			wing(d, left, main, Rooms.Type.KITCHEN, true);
			wing(d, right, main, Rooms.Type.LIVING, false);
		}
	}

	private static void keepWingDoor(Rooms.Room room, Body wing, Body main) {
		if (wing != null) {
			room.keep((wing.x0 + wing.x1) / 2, main.z0 + 1);
		}
	}

	private static void blockHearths(Rooms.Room room, List<int[]> hearths) {
		for (int[] h : hearths) {
			if (room.inside(h[0], h[1])) {
				room.block(h[0], h[1]);
				room.keep(h[0] + (h[0] == room.x0 ? 1 : -1), h[1]);
			}
		}
	}

	/** Bookshelves two high round the walls, a lectern with the house's chronicle, a reading table and chairs. */
	private static void library(Draft d, Rooms.Room room) {
		int y = room.y;
		// a lectern by the window side, facing into the room
		int lx = room.x0 + room.width() / 2;
		int lz = room.z0;
		if (d.isEmpty(lx, y, lz) && room.claim(lx, lz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, y, lz, States.facing(Blocks.LECTERN, Dirs.BACK),
				BlockEntities.lectern("The House Chronicle", "the family", List.of("Of the founding of this house, and of all who kept it."))));
			d.poi(PoiType.LECTERN, lx, y, lz + 1, Dirs.FRONT);
		}
		Rooms.furnish(d, room, Rooms.Type.LIVING, 0, JobType.NONE);
		// more shelves along the back wall where it is still free
		for (int x = room.x0; x <= room.x1; x++) {
			int z = room.z1;
			if (d.isEmpty(x, y, z) && d.isEmpty(x, y, z - 1) && room.claim(x, z)) {
				Furniture.bookshelf(d, x, y, z);
				if (room.height >= 3) {
					Furniture.bookshelf(d, x, y + 1, z);
				}
			}
		}
	}

	/** A long table down the room with chairs either side, a sideboard, candles. */
	private static void dining(Draft d, Rooms.Room room) {
		int len = Math.min(5, room.length() - 2);
		int cx = (room.x0 + room.x1) / 2;
		if (len >= 3 && room.width() >= 3) {
			int z0 = room.z0 + (room.length() - len) / 2;
			boolean ok = true;
			for (int z = z0; z < z0 + len; z++) {
				ok &= room.claim(cx, z);
			}
			if (ok) {
				Furniture.longTable(d, cx, room.y, z0, Dirs.BACK, len);
				for (int z = z0 + 1; z < z0 + len - 1; z++) {
					for (int x : new int[]{cx - 1, cx + 1}) {
						if (room.inside(x, z) && d.isEmpty(x, room.y, z) && room.claim(x, z)) {
							Furniture.chair(d, x, room.y, z, x < cx ? Dirs.LEFT : Dirs.RIGHT);
						}
					}
				}
			}
		}
		Rooms.furnish(d, room, Rooms.Type.KITCHEN, 0, JobType.NONE);
	}

	/** A wing's two floors: below, the kitchen or the drawing room; above, bedrooms; a stair is the main block's. */
	private static void wing(Draft d, Body wing, Body main, Rooms.Type type, boolean kitchen) {
		int[] r = wing.rect(0);
		for (int k = 0; k < 2; k++) {
			int y = wing.floorY(k);
			Rooms.Room room = new Rooms.Room(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, y, wing.height(k) - 1);
			room.keep((wing.x0 + wing.x1) / 2, r[3] - 1);
			if (k == 0) {
				Rooms.furnish(d, room, type, 0, JobType.NONE);
				if (kitchen) {
					Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
				}
			} else {
				Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);
			}
		}
	}

	/**
	 * The front of the grounds: a wall (or railing, or hedge) along the street with a carriage gate between piers
	 * capped with lanterns, a drive to the porch steps, parterres of clipped hedge round flower beds either side, a
	 * fountain on the axis where there is room, lamp posts along the drive.
	 */
	private static void grounds(Draft d, Body main, int fz0, int door, int porchW, Traits t) {
		int w = d.frontage();
		int hz0 = main.z0;
		Mat fence = d.mat(Role.FENCE);
		Mat found = d.mat(Role.FOUNDATION);
		Mat path = d.mat(Role.PATH);
		Mat paving = d.mat(Role.PAVING);
		Mat hedge = d.mat(Role.HEDGE);
		Mat accent = d.mat(Role.WALL_ACCENT);
		int g0 = door - 1;
		int g1 = door + porchW;
		// the drive
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < hz0 - 2; z++) {
				for (int x = g0; x <= g1; x++) {
					if (d.isAbsent(x, 0, z)) {
						d.set(x, -1, z, x == g0 || x == g1 ? paving.full() : path.full());
					}
				}
			}
		}
		// the wall along the street, piers at the gate with lanterns, the gate leaves standing open
		Habits.FenceKind kind = d.habits().yard().fence();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 0; x < w; x++) {
				if (x >= g0 && x <= g1) {
					continue;
				}
				boolean pier = x == g0 - 1 || x == g1 + 1 || x == 0 || x == w - 1 || x % 4 == 0;
				if (pier) {
					d.set(x, 0, 0, accent.full());
					d.set(x, 1, 0, accent.full());
					d.set(x, 2, 0, accent.slab(SlabType.BOTTOM));
				} else if (kind == Habits.FenceKind.HEDGE && hedge.has(Form.FULL)) {
					d.set(x, 0, 0, hedge.leaves());
					d.set(x, 1, 0, hedge.leaves());
				} else if (kind == Habits.FenceKind.WALL || kind == Habits.FenceKind.NONE) {
					d.set(x, 0, 0, found.full());
					d.set(x, 1, 0, fence.has(Form.WALL) ? fence.wall() : found.has(Form.WALL) ? found.wall() : found.slab(SlabType.BOTTOM));
				} else {
					d.set(x, 0, 0, found.full());
					d.set(x, 1, 0, fence.fence());
				}
			}
			for (int x : new int[]{g0 - 1, g1 + 1}) {
				if (x >= 0 && x < w) {
					d.set(x, 3, 0, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
				}
			}
		}
		d.entrance(door, 0, -1);
		d.poi(PoiType.GATE, door, 0, 0, Dirs.BACK);
		// parterres either side of the drive: hedge borders round flower beds, a topiary at each corner
		int pz0 = 2;
		int pz1 = hz0 - 3;
		if (pz1 - pz0 >= 2) {
			for (int side = 0; side < 2; side++) {
				int px0 = side == 0 ? 1 : g1 + 2;
				int px1 = side == 0 ? g0 - 2 : w - 2;
				if (px1 - px0 < 2) {
					continue;
				}
				parterre(d, px0, pz0, px1, pz1, hedge);
			}
		}
		// the fountain on the axis, the drive parting round it
		if (hz0 - 2 >= 8 && g1 - g0 >= 1) {
			int fz = (pz0 + pz1) / 2;
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(door, -1, fz, accent.full());
				d.set(door, 0, fz, accent.has(Form.WALL) ? accent.wall() : accent.full());
				d.set(door, 1, fz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
			}
			d.poi(PoiType.MEETING_POINT, door - 1, 0, fz, Dirs.RIGHT);
		}
		// lamp posts along the drive
		for (int z = 3; z < hz0 - 2; z += 4) {
			for (int x : new int[]{g0 - 1, g1 + 1}) {
				if (x > 0 && x < w - 1 && d.isAbsent(x, 0, z)) {
					Garden.lampPost(d, x, 0, z, 2, null);
				}
			}
		}
	}

	/** A parterre: a clipped hedge round a bed of flowers, a cross path through it, topiary at the corners. */
	private static void parterre(Draft d, int x0, int z0, int x1, int z1, Mat hedge) {
		boolean leaves = hedge.has(Form.FULL);
		int mxp = (x0 + x1) / 2;
		int mzp = (z0 + z1) / 2;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!d.isAbsent(x, 0, z)) {
					continue;
				}
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				boolean cross = x == mxp || z == mzp;
				if (edge && leaves && !(cross && (z == z0 || z == z1 || x == x0 || x == x1) && (x == mxp || z == mzp))) {
					try (var st = d.stage(StageKind.EXTERIOR)) {
						d.set(x, 0, z, hedge.leaves());
						if ((x == x0 || x == x1) && (z == z0 || z == z1)) {
							d.set(x, 1, z, hedge.leaves());
						}
					}
				} else if (cross) {
					Garden.path(d, List.of(new int[]{x, z}), 0);
				} else {
					Garden.flower(d, x, 0, z);
				}
			}
		}
	}

	/** The garden behind: fruit trees, beds of the culture's garden, a path, a bench. */
	private static void garden(Draft d, Body main, Traits t) {
		int w = d.frontage();
		int dp = d.depth();
		int z0 = main.z1 + 1;
		if (dp - 1 - z0 < 1) {
			return;
		}
		Garden.garden(d, 1, dp - 2, w - 2, dp - 2, 0);
		for (int x = 2; x < w - 2; x += 5) {
			if (dp - 1 - z0 >= 3 && d.isAbsent(x, 0, dp - 3)) {
				Garden.tree(d, x, 0, dp - 3, 3);
			}
		}
		int bx = w / 2;
		if (d.isAbsent(bx, 0, z0)) {
			Furniture.chair(d, bx, 0, z0, Dirs.BACK);
			d.poi(PoiType.VIEWPOINT, bx, 0, z0, Dirs.BACK);
		}
	}
}
