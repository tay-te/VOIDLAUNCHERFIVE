package dev.voidmc.hearth.build.archetype.work;

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
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.craft.Hangs;
import dev.voidmc.hearth.build.kit.craft.Lofts;
import dev.voidmc.hearth.build.kit.craft.Relief;
import dev.voidmc.hearth.build.kit.craft.Vats;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/dye_works}: the dyers' house by the water at the edge of town, the most colourful place in it. Its
 * gable to the street, the dye house stands down one side of the plot: below, the coppers steaming over their fires
 * and the great vats of woad, madder and weld, opening to the yard through a wide bay; above, the drying loft, open to
 * the yard between its posts and hung with lengths of cloth in madder, woad and weld, and its street gable carrying a gallery hung
 * the same. In the yard, a stream led along a stone channel for the rinsing, tall tenter frames strung with dyed cloth
 * that show over the roofs, tubs of dye, bales of white wool waiting, a bed of dye plants by the fence. Where roofs are
 * flat the cloth dries on rails on the terrace and the vats are round and sunk in the yard, as in the dyers' quarters
 * of the south.
 *
 * <p>Job sites (DYER): the coppers, the vats, the rinsing channel, the frames. Storage: the chest of dyestuffs, the wool.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class DyeWorks implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/dye_works"), ArchetypeCategory.WORKPLACE, Set.of(JobType.DYER), 0,
		9, 13, 9, 13, 10, 15, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

	// woad, madder and weld: the three dyes a town dyer works (greens and purples are dipped twice, not vatted apart)
	private static final Vats.Liquor[] DYES = {Vats.Liquor.BLUE, Vats.Liquor.RED, Vats.Liquor.YELLOW};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;
		boolean south = Craft.southern(d);
		// the dye house down one side, its gable to the street; the yard beside it
		boolean houseLeft = d.chance(0.5);
		int hw = w >= 12 ? 7 : w >= 10 ? 6 : 5;
		int hx0 = houseLeft ? 0 : w - hw;
		int hx1 = houseLeft ? hw - 1 : w - 1;
		int hz0 = 1;
		int hz1 = dp - 1;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int sh = Math.max(4, h.walls().storeyHeight());
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, new int[]{sh + 1, 4}, false, form, 1, Direction.Axis.Z);
		boolean loft = heights.length >= 2;
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int doorAt = (in[0] + in[2]) / 2;
		hall.facade(doorAt, 1, 1, 0.5);
		int door = hall.door != null ? hall.door : doorAt;
		hall.ground(false).shell().openings(true);
		Direction toYard = houseLeft ? Dirs.RIGHT : Dirs.LEFT;
		Direction outer = toYard.getOpposite();

		// the wide bay from the dye house into the yard, midway along the side
		Side yardWall = hall.side(0, toYard);
		int bayW = Math.min(3, Math.max(2, yardWall.length() - 6));
		int bayC = yardWall.column(Math.max(2, yardWall.length() / 2 - bayW / 2));
		hall.bay(toYard, bayC, bayW, 3);
		// the stair to the loft along the outer wall from the back
		int[] flight = loft ? hall.flight(0, outer, Dirs.BACK) : null;
		if (loft && flight == null) {
			flight = hall.flight(0, Dirs.BACK, outer);
		}
		// the loft: open to the yard between its posts and hung with cloth; a gallery of cloth on the street gable
		if (loft) {
			Lofts.gallery(d, hall.body, 1, toYard, 3, Hangs.DYED);
		}
		Craft.roof(d, hall, form, Direction.Axis.Z, 1, null);
		if (flat) {
			hall.crown();
		} else if (hall.roof != null && Bohemia.is(d)) {
			Bohemia.gables(d, hall.roof, Direction.Axis.Z, hall.rect(hall.storeys() - 1), hall.plateY());
		}
		Relief.wall(d, hall.body, hall.facade, Dirs.FRONT, 0, 0, hall.roof, null, Relief.Opts.ALL);
		if (loft && hall.roof != null) {
			streetGallery(d, hall);
		}

		dyeHouse(d, hall, door, flight, toYard, yardWall, bayC, bayW);
		if (loft) {
			loftFloor(d, hall, flight, toYard);
		}
		// the terrace and its stair before the yard, so the yard is laid round the stair
		if (hall.deck != Integer.MIN_VALUE) {
			if (hall.terrace(outer, false)) {
				terraceCloth(d, hall);
			}
		}
		yard(d, hall, w, dp, houseLeft, ex, door, bayC, bayW, south);
		Side front = hall.side(0, Dirs.FRONT);
		int si = front.index(door + (houseLeft ? 1 : -1));
		if (!Signs.bracket(d, front, si, 3, "dye_works", "Dyer")) {
			Signs.bracket(d, front, front.index(door + (houseLeft ? -1 : 1)), 3, "dye_works", "Dyer");
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, 8) + 2);
	}

	/**
	 * A gallery of cloth across the street gable at the loft's floor: a rail of the beam on brackets a block out from
	 * the gable, hung with lengths of cloth to the street, where the plot's front row is free.
	 */
	private static void streetGallery(Draft d, Hall hall) {
		Side s = hall.side(1, Dirs.FRONT);
		int y = hall.floorY(1) + 2;
		Mat beam = d.mat(Role.BEAM);
		List<int[]> rail = new ArrayList<>();
		for (int i = 1; i < s.last(); i++) {
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (!d.allowed(ox, y, oz) || !d.isAbsent(ox, y, oz) || !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
				continue;
			}
			rail.add(new int[]{ox, oz});
		}
		if (rail.size() < 2) {
			return;
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : rail) {
				d.set(c[0], y, c[1], beam.log(s.axis()));
			}
		}
		for (int[] c : rail) {
			int hx = c[0] + Dirs.dx(s.out());
			int hz = c[1] + Dirs.dz(s.out());
			if (hz >= -1 && d.allowed(hx, y, hz) && (hz >= 0 || y - 1 >= 3)) {
				Hangs.hang(d, hx, y, hz, s.out(), Hangs.DYED);
			}
		}
	}

	/** Below: the coppers along the outer wall, a great vat where there is room, the dyestuff chest, bales of wool. */
	private static void dyeHouse(Draft d, Hall hall, int door, int[] flight, Direction toYard, Side yardWall, int bayC, int bayW) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		// what stands first (the stair), then the cells that must stay reachable among it
		if (flight != null) {
			for (int x = Math.min(flight[4], flight[6]); x <= Math.max(flight[4], flight[6]); x++) {
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(x, z);
				}
			}
			fp.keep(flight[0], flight[1]);
		}
		fp.keep(door, in[1]);
		for (int c = bayC; c < bayC + bayW; c++) {
			int i = yardWall.index(c);
			fp.keep(yardWall.x(i) - Dirs.dx(toYard), yardWall.z(i) - Dirs.dz(toYard));
		}
		// the coppers' chimney against the back wall, as near its middle as the floor allows; the coppers gather by it
		int mid = (in[0] + in[2]) / 2;
		for (int off = 0; off <= in[2] - in[0]; off++) {
			int cx = mid + ((off & 1) == 0 ? off / 2 : -(off + 1) / 2);
			if (cx >= in[0] && cx <= in[2] && fp.take(cx, in[3], null)) {
				hall.chimney(cx, in[3], 2);
				break;
			}
		}
		Direction outer = toYard.getOpposite();
		// the coppers: kettles of dye over their fires along the outer wall (where the stair leaves room) and the back wall
		int coppers = 0;
		for (Direction wall : new Direction[]{Dirs.BACK, outer}) {
			for (int[] c : fp.along(wall)) {
				if (coppers >= 3 || c[1] == in[1]) {
					continue;
				}
				Direction user = wall.getOpposite();
				if (fp.take(c[0], c[1], user)) {
					Vats.kettle(d, c[0], 0, c[1], false);
					if (coppers < 2) {
						d.poi(PoiType.JOB_SITE, c[0] + Dirs.dx(user), 0, c[1] + Dirs.dz(user), wall, JobType.DYER);
					}
					coppers++;
				}
			}
		}
		// a great vat of woad in the middle where the floor is five wide
		if (in[2] - in[0] >= 4 && in[3] - in[1] >= 5) {
			int cx = (in[0] + in[2]) / 2;
			int cz = in[1] + 2;
			List<int[]> cells = new ArrayList<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cells.add(new int[]{cx + dx, cz + dz});
				}
			}
			if (fp.take(cells.toArray(int[][]::new), new int[]{cx, cz + 2})) {
				Vats.tub(d, cx, 0, cz, Vats.Liquor.BLUE, d.mat(Role.FURNITURE));
				d.poi(PoiType.JOB_SITE, cx, 0, cz + 2, Dirs.FRONT, JobType.DYER);
			}
		}
		// the dyestuff chest and the bales of white wool by the street wall
		List<Object[]> stuffs = List.of(new Object[]{"minecraft:blue_dye", 16}, new Object[]{"minecraft:red_dye", 16},
			new Object[]{"minecraft:yellow_dye", 16}, new Object[]{"minecraft:white_wool", 32}, new Object[]{"minecraft:lapis_lazuli", 4});
		boolean chest = false;
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (Math.abs(c[0] - door) <= 1) {
				continue;
			}
			if (!chest && fp.take(c[0], c[1], Dirs.BACK)) {
				Props.store(d, c[0], 0, c[1], Dirs.BACK, true, stuffs);
				chest = true;
			} else if (fp.take(c[0], c[1], null)) {
				d.inStage(StageKind.INTERIOR, () -> {
					d.set(c[0], 0, c[1], Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
					d.set(c[0], 1, c[1], Blocks.WOOL_SLAB.pick(DyeColor.WHITE).defaultBlockState());
				});
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, stuffs);
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.WORKSHOP, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}

	/** The loft: rails of cloth down its length, light, a dyer turning the cloth. */
	private static void loftFloor(Draft d, Hall hall, int[] flight, Direction toYard) {
		Floorplan up = hall.floor(1).blockHoles(d);
		if (flight != null) {
			up.keep(flight[2], flight[3]);
			up.keepArea(flight[4], flight[5], flight[6], flight[7]);
		}
		int[] in = hall.inner(1);
		int y = hall.floorY(1);
		int top = y + 2;
		int rx = (in[0] + in[2]) / 2 + (toYard == Dirs.RIGHT ? -1 : 1);
		if (in[3] - in[1] >= 3 && rx > in[0] && rx < in[2]) {
			// the rail runs the loft's length, its ends let into the gables
			Hangs.rail(d, rx, top, in[1], Dirs.BACK, in[3] - in[1] + 1, Hangs.DYED, true, true);
		}
		for (int[] c : flight == null ? List.<int[]>of() : up.along(Dirs.FRONT)) {
			int fz = c[1] + 1;
			if (up.free(c[0], fz) && d.isEmpty(c[0], y, fz) && d.isEmpty(c[0], y + 1, fz)) {
				d.poi(PoiType.JOB_SITE, c[0], y, fz, Dirs.FRONT, JobType.DYER);
				break;
			}
		}
		Lighting.room(d, in[0], in[1], in[2], in[3], y);
	}

	/**
	 * The yard: the gate on the street and a path to the dye house door and its bay, the rinsing channel along the yard's
	 * outer edge, tall tenter frames hung with cloth, tubs of dye (round sunk vats in the south), a bed of dye plants by
	 * the fence, bales and barrels. Laid as a walkable plan so every place of work stays reachable.
	 */
	private static void yard(Draft d, Hall hall, int w, int dp, boolean houseLeft, int ex, int door, int bayC, int bayW, boolean south) {
		int[] r = hall.rect(0);
		int ya = houseLeft ? r[2] + 1 : 0;
		int yb = houseLeft ? w - 1 : r[0] - 1;
		Mat kerb = Craft.kerb(d);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		Yard.pave(d, r[0], 0, r[2], 0, 0);
		// the street fence and the yard's outer side and back, the gate where the street meets the yard
		int gate = Math.clamp(ex, houseLeft ? ya : ya + 1, houseLeft ? yb - 1 : yb);
		List<int[]> line = new ArrayList<>();
		for (int x = ya; x <= yb; x++) {
			if (x != gate) {
				line.add(new int[]{x, 0});
			}
		}
		int ox = houseLeft ? w - 1 : 0;
		for (int z = 1; z < dp; z++) {
			line.add(new int[]{ox, z});
		}
		for (int x = ya; x <= yb; x++) {
			line.add(new int[]{x, dp - 1});
		}
		if (yb - ya < 3) {
			Garden.fence(d, line, -1, Dirs.FRONT);
			return;
		}
		// inside the fence: the outer column is the fence's
		ya = houseLeft ? ya : ya + 1;
		yb = houseLeft ? yb - 1 : yb;
		Floorplan yp = Craft.yardPlan(d, ya, 1, yb, dp - 2);
		int inner = houseLeft ? ya : yb;
		yp.keep(gate, 1);
		for (int c = bayC; c < bayC + bayW; c++) {
			yp.keep(inner, c);
		}
		// the channel along the outer edge: a stream led through for the rinsing, worked from its inner side
		int chx = houseLeft ? yb : ya;
		int chz0 = 2;
		int chz1 = dp - 3;
		List<int[]> chCells = new ArrayList<>();
		for (int z = chz0; z <= chz1; z++) {
			chCells.add(new int[]{chx, z});
		}
		int rinseX = chx + (houseLeft ? -1 : 1);
		if (rinseX == (houseLeft ? ya + 1 : yb - 1)) {
			// a narrow yard: the rinsing place is the walk along the house, the channel two over (a plank across to it)
			rinseX = houseLeft ? ya : yb;
		}
		int rinseZ = (chz0 + chz1) / 2;
		boolean channel = chz1 - chz0 >= 2 && yp.take(chCells.toArray(int[][]::new), new int[]{rinseX, rinseZ});
		if (channel) {
			Vats.pit(d, chx, chz0, chx, chz1, 0, Vats.Liquor.WATER, kerb);
			d.poi(PoiType.JOB_SITE, rinseX, 0, rinseZ, houseLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.DYER);
		}
		// the tenter frames: tall frames of cloth, across the yard where it is broad, down its length where it is narrow
		int fa = houseLeft ? ya + 1 : ya + (channel ? 2 : 0);
		int fb = houseLeft ? yb - (channel ? 2 : 0) : yb - 1;
		int frames = 0;
		int top = Math.min(6, d.maxHeight() - 3);
		if (fb - fa >= 3) {
			for (int fz : new int[]{dp - 3, dp - 7}) {
				if (fz < 3) {
					continue;
				}
				List<int[]> cells = new ArrayList<>();
				for (int x = fa; x <= fb; x++) {
					cells.add(new int[]{x, fz});
				}
				if (yp.take(cells.toArray(int[][]::new), new int[]{fa, fz - 1})) {
					tenter(d, fa, fz, Dirs.RIGHT, fb - fa + 1, top);
					if (frames == 0) {
						d.poi(PoiType.JOB_SITE, fa, 0, fz - 1, Dirs.BACK, JobType.DYER);
					}
					frames++;
				}
			}
		}
		if (frames == 0) {
			int fx = houseLeft ? ya + 1 : yb - 1;
			int fz0 = 3;
			int fz1 = dp - 3;
			List<int[]> cells = new ArrayList<>();
			for (int z = fz0; z <= fz1; z++) {
				cells.add(new int[]{fx, z});
			}
			int wx = houseLeft ? ya : yb;
			for (int wz = fz0; wz <= fz1 && frames == 0; wz++) {
				if (fz1 - fz0 >= 2 && fx != chx && yp.open(wx, wz) && yp.take(cells.toArray(int[][]::new), new int[]{wx, wz})) {
					tenter(d, fx, fz0, Dirs.BACK, fz1 - fz0 + 1, top);
					d.poi(PoiType.JOB_SITE, wx, 0, wz, houseLeft ? Dirs.RIGHT : Dirs.LEFT, JobType.DYER);
					frames++;
				}
			}
		}
		// vats of dye in what is left of the yard: round tubs of staves, or sunk in the paving in the south
		int vats = 0;
		for (int cz = dp - 3; cz >= 3 && vats < 2; cz--) {
			for (int cx = ya + 1; cx <= yb - 1 && vats < 2; cx++) {
				int[][] cells = new int[9][];
				int k = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						cells[k++] = new int[]{cx + dx, cz + dz};
					}
				}
				int[] stand = new int[]{cx, cz - 2};
				if (yp.take(cells, stand)) {
					Vats.Liquor dye = DYES[Math.floorMod((int) (d.seed() >>> 3) + vats, DYES.length)];
					if (south) {
						Vats.sunkRound(d, cx, 0, cz, dye, kerb);
					} else {
						Vats.tub(d, cx, 0, cz, dye, d.mat(Role.FURNITURE));
					}
					d.poi(PoiType.JOB_SITE, stand[0], 0, stand[1], Dirs.BACK, JobType.DYER);
					vats++;
				}
			}
		}
		// the dye-plant bed along the street fence, bales of wool and barrels by the dye house
		List<int[]> bed = new ArrayList<>();
		for (int x = ya; x <= yb; x++) {
			if (x != gate && Math.abs(x - gate) > 1 && yp.take(x, 1, null)) {
				bed.add(new int[]{x, 1});
			}
		}
		dyePlants(d, bed);
		Garden.fence(d, line, -1, Dirs.FRONT);
		int lampX = gate + (houseLeft ? 1 : -1);
		if (lampX >= ya && lampX <= yb && d.isEmpty(lampX, 0, 1) && d.isEmpty(lampX, 1, 1)) {
			Lighting.post(d, lampX, 0, 1);
		}
	}

	/**
	 * A tall tenter frame along {@code run} over {@code length} cells from (x0, z0): log posts at its ends up
	 * {@code height}, a beam across their heads and a rail at three between them, cloth hung on both faces of both.
	 */
	private static void tenter(Draft d, int x0, int z0, Direction run, int length, int height) {
		int top = Math.max(4, height);
		Hangs.frame(d, x0, 0, z0, run, length, top, Hangs.DYED, true);
		if (top >= 6 && length >= 3) {
			Hangs.rail(d, x0 + Dirs.dx(run), 3, z0 + Dirs.dz(run), run, length - 2, Hangs.DYED, false, true);
		}
	}

	/** Woad, madder, weld and cornflower in a bed along the fence: the dyers' own plants. */
	private static void dyePlants(Draft d, List<int[]> cells) {
		net.minecraft.world.level.block.Block[] plants = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.DANDELION, Blocks.RED_TULIP,
			Blocks.ALLIUM};
		try (var s = d.stage(StageKind.EXTERIOR)) {
			int k = 0;
			for (int[] c : cells) {
				if (!d.isEmpty(c[0], 0, c[1]) || !d.isSolid(c[0], -1, c[1]) && d.get(c[0], -1, c[1]) != null) {
					continue;
				}
				d.set(c[0], -1, c[1], Blocks.GRASS_BLOCK.defaultBlockState());
				d.set(c[0], 0, c[1], plants[Math.floorMod((int) (d.seed() >>> 5) + k, plants.length)].defaultBlockState());
				k++;
			}
		}
	}

	/** On a flat roof: rails of cloth drying across the terrace. */
	private static void terraceCloth(Draft d, Hall hall) {
		int[] r = hall.rect(hall.storeys() - 1);
		int y = hall.deck;
		if (y + 3 >= d.maxHeight()) {
			return;
		}
		int x = (r[0] + r[2]) / 2;
		int z0 = r[1] + 2;
		int z1 = r[3] - 2;
		if (z1 - z0 >= 2) {
			Hangs.frame(d, x, y, z0, Dirs.BACK, z1 - z0 + 1, 2, Hangs.DYED, false);
		}
	}
}
