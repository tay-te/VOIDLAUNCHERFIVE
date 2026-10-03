package dev.voidmc.hearth.build.archetype.storage;

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
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:storage/granary}: the settlement's grain store, kept off the ground away from damp and vermin.
 * <ul>
 *   <li><b>staddle granary</b>: a boarded or framed store standing on mushroom-shaped staddle stones — a post of stone
 *   and a broad cap — so rats cannot climb into it, a heavy sill beam all round, small vents, a steep roof; a short
 *   flight of steps to its door;</li>
 *   <li><b>bin granaries</b> (steppe, tropical): round bins of mud on a raised stone platform, each under its own
 *   cone roof, the biggest with a door at the top of the steps;</li>
 *   <li><b>domed granaries</b> (desert, mesa): beehive domes of mud in a walled yard, a door in the largest.</li>
 * </ul>
 * Inside: sacks of grain, hay, a chest for the reckoning.
 *
 * <p>Storage: the chests. Params: {@code roof}, {@code kind} ({@code staddle}, {@code bins}, {@code domes}),
 * {@code variant}.
 */
public final class Granary implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("storage/granary"), ArchetypeCategory.STORAGE, Set.of(), 0,
		7, 11, 9, 13, 8, 12, SettlementTier.VILLAGE, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.SALTBOX};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Habits h = d.habits();
		String fallback = Hall.flatCulture(d) ? "domes" : h.plan().round() >= 0.25 ? "bins" : "staddle";
		String kind = d.param("kind", fallback);
		switch (kind) {
			case "bins" -> bins(d, false);
			case "domes" -> bins(d, true);
			default -> staddle(d);
		}
	}

	// --------------------------------------------------------------------------------------------- staddle granary

	private static void staddle(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = Hall.pick(d, FORMS);
		int bx0 = 1;
		int bx1 = w - 2;
		int bz0 = 3;
		int bz1 = dp - 2;
		int base = d.maxHeight() >= 11 ? 2 : 1;
		int sh = Math.max(4, h.walls().storeyHeight());
		boolean loft = d.maxHeight() >= base + sh + 7;
		Hall hall = Hall.of(d, bx0, bz0, bx1, bz1, base, loft ? new int[]{sh, 2} : new int[]{sh}, false, false);
		int[] in = hall.inner(0);
		int door = (in[0] + in[2]) / 2;
		hall.facade(door, 1, 1, 0.25);
		door = hall.door != null ? hall.door : door;
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the staddles: mushroom stones under the corners and every two along the sides, the store's sill on them
		staddles(d, bx0, bz0, bx1, bz1, base);
		Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
		hall.stilted = true;
		d.body(bx0, bz0, bx1, bz1);
		hall.shell();
		hall.openings(false);
		// a sill beam of logs round the store's foot
		Mat sill = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (Side side : Side.of(bx0, bz0, bx1, bz1)) {
				for (int i = 0; i < side.length(); i++) {
					d.set(side.x(i), base - 1, side.z(i), side.isCorner(i) ? sill.log(Direction.Axis.Y) : sill.log(side.axis()));
				}
			}
		}
		Direction.Axis ridge = bz1 - bz0 > bx1 - bx0 ? Direction.Axis.Z : Direction.Axis.X;
		if (d.chance(0.3)) {
			ridge = ridge == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		}
		hall.roof(form, ridge, 1, null);
		// the flight to the door, stopping at a landing before it
		Groundwork.approach(d, door, base, bz0 - 1);
		// vents high in the gables or side walls
		vents(d, hall);
		interior(d, hall, door);
		// round about: a cart of sheaves, a sack or two waiting, a lamp
		Yard.trodden(d, 0, 0, w - 1, bz0 - 1, 0);
		int cx = door > w / 2 ? 1 : w - 2;
		if (d.isEmpty(cx, 0, 1) && d.isEmpty(cx, 0, 0) && cx - 1 >= 0 && cx + 1 < w && d.isEmpty(cx - 1, 0, 1) && d.isEmpty(cx + 1, 0, 1)) {
			Props.cart(d, cx, 0, 1, Dirs.FRONT, Blocks.HAY_BLOCK.defaultBlockState());
		}
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, 0, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	/** Staddle stones: a stone post and a broad cap of upside-down stairs under the store's corners and sides. */
	private static void staddles(Draft d, int x0, int z0, int x1, int z1, int base) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat cap = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (Side side : Side.of(x0, z0, x1, z1)) {
				for (int i = 0; i < side.length(); i++) {
					if (!side.isCorner(i) && i % 2 != 0) {
						continue;
					}
					int x = side.x(i);
					int z = side.z(i);
					int g = d.site().ground(x, z);
					for (int y = Math.min(g - 1, 0); y < base - 2; y++) {
						d.set(x, y, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
					}
					if (base - 2 >= 0) {
						d.set(x, base - 2, z, cap.full());
					}
				}
			}
		}
	}

	/** Small square vents (open trapdoors) high in the walls. */
	private static void vents(Draft d, Hall hall) {
		Mat shutter = d.mat(Role.SHUTTER);
		int y = hall.floorY(0) + 2;
		for (Direction out : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			Side s = hall.side(0, out);
			for (int i = 2; i < s.last() - 1; i += 3) {
				if (dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
					int ii = i;
					d.inStage(StageKind.OPENINGS, () -> d.set(s.x(ii), y, s.z(ii), shutter.trapdoor(out, Half.TOP, true)));
				}
			}
		}
	}

	private static void interior(Draft d, Hall hall, int door) {
		int[] in = hall.inner(0);
		hall.openLoft(1);
		Floorplan fp = hall.floor(0);
		fp.keep(door, in[1]);
		int y = fp.y;
		boolean chest = false;
		for (int[] c : fp.along(Dirs.BACK)) {
			if (!chest && fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.store(d, c[0], y, c[1], Dirs.FRONT, true, List.of(new Object[]{"minecraft:wheat", 48}, new Object[]{"minecraft:wheat_seeds", 32},
					new Object[]{"minecraft:beetroot_seeds", 16}));
				chest = true;
			} else if (fp.take(c[0], c[1], null)) {
				Props.sacks(d, c[0], y, c[1], Dirs.RIGHT, 1, false);
			}
		}
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (c[1] > in[1] + 1 && fp.take(c[0], c[1], null)) {
					d.inStage(StageKind.INTERIOR, () -> {
						d.set(c[0], y, c[1], Blocks.HAY_BLOCK.defaultBlockState());
						if (fp.height >= 3) {
							d.set(c[0], y + 1, c[1], Blocks.HAY_BLOCK.defaultBlockState());
						}
					});
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	// --------------------------------------------------------------------------------------------- bins and domes

	/**
	 * Round bins on a raised platform (cone roofs) or domes in a walled yard: two to three, the largest at the back
	 * with its door, the platform reached by steps from the street.
	 */
	private static void bins(Draft d, boolean domes) {
		int w = d.frontage();
		int dp = d.depth();
		int gate = w / 2;
		Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int base = domes ? 0 : 1;
		Mat plat = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		// the platform (or the yard's paving)
		int px0 = 0;
		int px1 = w - 1;
		int pz0 = 2;
		int pz1 = dp - 1;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					int g = d.site().ground(x, z);
					boolean edge = x == px0 || x == px1 || z == pz0 || z == pz1;
					for (int y = Math.min(g - 1, -1); y < base - 1; y++) {
						d.set(x, y, z, plat.full());
					}
					d.set(x, base - 1, z, edge && base > 0 ? plat.full() : paving.full());
				}
			}
		}
		if (base > 0) {
			Groundwork.steps(d, gate, base, pz0 - 1, Dirs.FRONT, 1);
			d.inStage(StageKind.EXTERIOR, () -> d.set(gate, base - 1, pz0, paving.full()));
		}
		// the largest bin at the back with the door, smaller ones before it
		double big = Math.min((w - 3) / 2.0, (pz1 - pz0 - 1) / 2.0 - 0.5);
		big = Math.max(2, Math.min(3, big));
		double cxBig = (w - 1) / 2.0;
		double czBig = pz1 - 1 - big;
		int[] doorCell = bin(d, cxBig, czBig, big, base, domes, true);
		List<double[]> small = new ArrayList<>();
		double r = big >= 3 ? 2 : 1.5;
		if (w >= 9) {
			small.add(new double[]{1 + r, pz0 + 1 + r});
			small.add(new double[]{w - 2 - r, pz0 + 1 + r});
		} else {
			small.add(new double[]{d.chance(0.5) ? 1 + r : w - 2 - r, pz0 + 1 + r});
		}
		for (double[] c : small) {
			if (c[1] + r + 1 < czBig - big && Math.abs(c[0] - gate) > r + 0.6) {
				bin(d, c[0], c[1], r, base, domes, false);
			}
		}
		// a path to the big bin's door, sacks by it
		if (doorCell != null) {
			Props.sacks(d, doorCell[0] + 1, base, doorCell[1], Dirs.RIGHT, 1, false);
		}
		if (domes) {
			List<int[]> gaps = new ArrayList<>();
			gaps.add(new int[]{gate, 0});
			Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
			Props.jars(d, 1, 0, 1, Dirs.RIGHT, Math.min(2, gate - 2));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, d.maxHeight() - 1);
	}

	/**
	 * One bin: a round wall of the culture's walling, two or three high, under a cone (or a dome of the same mud),
	 * on the platform at standing level y. The main bin gets a door toward the street and a chest inside. Returns the
	 * cell before its door, or null.
	 */
	private static int[] bin(Draft d, double cx, double cz, double r, int y, boolean dome, boolean main) {
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat roofMat = d.mat(Role.ROOF);
		int wallH = main ? 3 : 2;
		List<int[]> ring = Draft.ellipseCells(cx, cz, r, r, false);
		List<int[]> disc = Draft.ellipseCells(cx, cz, r, r, true);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : ring) {
				for (int yy = y; yy < y + wallH; yy++) {
					d.set(c[0], yy, c[1], wall.full());
				}
			}
		}
		for (int[] c : disc) {
			if (!contains(ring, c)) {
				d.carve(c[0], y, c[1], c[0], y + wallH + 3, c[1]);
			}
		}
		Roofs.Roof roof;
		int rx0 = (int) Math.floor(cx - r - 1);
		int rz0 = (int) Math.floor(cz - r - 1);
		int rx1 = (int) Math.ceil(cx + r + 1);
		int rz1 = (int) Math.ceil(cz + r + 1);
		if (dome) {
			roof = Roofs.raster(d, Roofs.dome(cx, cz, r, 0.9), y + wallH - 1, Math.max(0, rx0 + 1), Math.max(0, rz0 + 1), Math.min(d.frontage() - 1, rx1 - 1),
				Math.min(d.depth() - 1, rz1 - 1), wall, wall, Habits.Ridge.FULL);
		} else {
			roof = Roofs.raster(d, Roofs.cone(cx, cz, r + 1, 1.0), y + wallH - 1, Math.max(0, rx0), Math.max(0, rz0), Math.min(d.frontage() - 1, rx1),
				Math.min(d.depth() - 1, rz1), roofMat, d.mat(Role.ROOF_TRIM), Habits.Ridge.FULL);
			int ax = (int) Math.round(cx);
			int az = (int) Math.round(cz);
			if (roof.ridgeY + 1 < d.maxHeight()) {
				d.inStage(StageKind.ROOF, () -> d.set(ax, roof.ridgeY + 1, az, frame.has(Form.FENCE) ? frame.fence() : frame.log(Direction.Axis.Y)));
			}
		}
		for (int[] c : ring) {
			int under = roof.bottomY(c[0], c[1]);
			for (int yy = y + wallH; yy < under; yy++) {
				d.set(c[0], yy, c[1], wall.full());
			}
		}
		if (!main) {
			return null;
		}
		// the door toward the street, a chest inside
		int dx = (int) Math.round(cx);
		int dz = Integer.MAX_VALUE;
		for (int[] c : ring) {
			if (c[0] == dx) {
				dz = Math.min(dz, c[1]);
			}
		}
		int fdz = dz;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, y, fdz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(dx, y, fdz - 1);
		d.poi(PoiType.DOOR, dx, y, fdz - 1, Dirs.BACK);
		int az = (int) Math.round(cz) + (r >= 2.5 ? 1 : 0);
		if (az == fdz + 1) {
			az++;
		}
		Props.store(d, dx, y, az, Dirs.FRONT, true, List.of(new Object[]{"minecraft:wheat", 48}, new Object[]{"minecraft:wheat_seeds", 24}));
		for (int[] c : disc) {
			if (!contains(ring, c) && d.isEmpty(c[0], y, c[1]) && Math.abs(c[0] - dx) >= 1 && c[1] > fdz + 1) {
				int fx = c[0];
				int fz = c[1];
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, y, fz, Blocks.HAY_BLOCK.defaultBlockState()));
				break;
			}
		}
		return new int[]{dx, fdz - 1};
	}

	private static boolean contains(List<int[]> cells, int[] c) {
		for (int[] e : cells) {
			if (e[0] == c[0] && e[1] == c[1]) {
				return true;
			}
		}
		return false;
	}
}
