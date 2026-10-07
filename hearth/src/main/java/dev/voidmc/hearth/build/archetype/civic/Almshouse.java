package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Pueblo;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Range;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.sociallife.Shell;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/almshouse}: the town's charity, where the old and the poor who have no one live out their days,
 * built round a little court. From the street one sees the low gable ends of the cells and between them the court's wall
 * with its roofed gate; across the back of the court stands the hall, two storeys with the ground storey of stone, a
 * bell-cote on its ridge (a bell gable over the parapet where the roofs are flat) that calls them to prayer and to meals,
 * a small cross over its door. Down one side of the court (both sides on a wide plot) runs the row of cells, a door and
 * a lamp for each, a bed and a chest inside. In the hall below, the common room: the kitchen fire on one end wall, a long
 * table where they eat together, at the other end a little altar with candles where they say their prayers, and by the
 * door the alms box; up the stair the sleeping floor for those without a cell, a bed for each with a chest at its side.
 * In the court a wayside cross, a bench in the sun, a lamp and the herbs they keep. On posts (no court can be levelled
 * under a raised house) the hall stands alone at the front with its kitchen garden behind.
 *
 * <p>Housing for the POOR (the planner counts its beds). POIs: BEDs (6 or more), STORAGE (the alms box and the chests),
 * TABLE, SEATs, the HEARTH, the ALTAR, the DOOR. Params: {@code variant} (int).
 */
public final class Almshouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/almshouse"), ArchetypeCategory.CIVIC, Set.of(), 6, 11, 15, 11, 15, 14,
		16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 2, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		if (!Tradition.of(d).raised() && streetMeets(d)) {
			court(d);
			return;
		}
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		Range.Built r = Range.build(d, dp >= 13 ? 8 : 7, true, 0.6);
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();

		// --- the common room: the fire at the end away from the stair, the altar at the stair's end against the front wall
		int hx = stairLeft ? r.ix1() : r.ix0();
		Direction face = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range.hearth(d, r, ground, hx, face, List.of(new Object[]{"minecraft:bread", 8}, new Object[]{"minecraft:potato", 12},
			new Object[]{"minecraft:bowl", 6}));
		int ax = stairLeft ? r.ix0() : r.ix1();
		int az = r.iz0() + 1;
		Direction toPeople = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (ground.free(ax, az) && ground.inside(ax + Dirs.dx(toPeople), az) && ground.take(ax, az, toPeople)) {
			Sacred.altar(d, ax, base, az, toPeople, false, Integer.MIN_VALUE);
			// a crucifix (or the culture's sign) on the wall over it
			Venue.banner(d, ax, base + 2, az, toPeople, "white", List.<String[]>of(new String[]{"minecraft:straight_cross", "black"}), StageKind.INTERIOR);
		}
		// the alms box by the door, inside
		int[] in = r.shell().inside();
		for (int k : new int[]{-1, 1}) {
			int bx = in[0] + k;
			if (ground.free(bx, r.iz0()) && ground.take(bx, r.iz0(), Dirs.BACK)) {
				Furniture.chest(d, bx, base, r.iz0(), Dirs.BACK, List.of(new Object[]{"minecraft:copper_nugget", 9}, new Object[]{"minecraft:bread", 3}));
				break;
			}
		}
		if (Range.tables(d, ground, r.ix0(), r.ix1(), w >= 13 ? 4 : 3, 1) == 0) {
			Range.tables(d, ground, r.ix0(), r.ix1(), 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, ground.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, r.ix0(), r.iz0(), r.ix1(), r.iz1(), base);

		// --- the sleeping floor: a bed for each, a chest or a candle stand at its side
		Floorplan up = r.upper();
		List<Object[]> kit = List.of(new Object[]{"minecraft:white_wool", 1}, new Object[]{"minecraft:candle", 1});
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			Wards.row(d, up, wall, 2, false, 12, kit);
		}
		Lighting.room(d, up.x0, up.z0, up.x1, up.z1, up.y);

		// --- outside: a cross over the door, a bench, the board
		Side front = r.body().side(0, Dirs.FRONT);
		int di = front.index(doorX);
		if (di >= 1 && di <= front.last() - 1) {
			Mat cross = d.mat(Role.FRAME);
			int cy = base + r.heights()[0] + 1;
			int fz = front.outZ(di, 1);
			if (cy + 1 < d.maxHeight() && d.isEmpty(doorX, cy, fz)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(doorX, cy - 1, fz, cross.has(dev.voidmc.hearth.build.style.Form.SLAB) ? cross.slab(SlabType.TOP) : cross.fence());
					d.set(doorX, cy, fz, cross.fence());
				}
			}
		}
		// the bell-cote on the ridge over the middle of the range
		Yardwork.bellcote(d, r.shell().roof(), w / 2);
		List<String[]> name = Signboard.name(d, "almshouse", Signboard.ALMSHOUSES);
		for (int k : new int[]{2, -2, 3, -3}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		Grounds.approach(d, r.shell().outside()[0], base, r.shell().outside()[1]);
		if (!raised) {
			Bohemia.bench(d, doorX, r.zF(), base);
		}

		// --- behind: the kitchen garden they keep, a well, a bench against the back wall
		int yz0 = r.zM() + 1;
		if (dp - 1 - yz0 >= 3 && !raised && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base)) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int gx0 = stairLeft ? 1 : 4;
			int gx1 = stairLeft ? w - 5 : w - 2;
			Garden.kitchenRows(d, gx0, yz0 + 1, gx1, dp - 2, base, Dirs.RIGHT);
			Yard.well(d, stairLeft ? w - 3 : 2, base, Math.min(dp - 2, yz0 + 2), Dirs.FRONT);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, r.zF(), w - 1, r.zM(), base, doorX);
		}
		// the mesa's pueblo: a stepped room over the flat roof, a ladder up to its terrace
		int pueblo = Pueblo.is(d) ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(d.maxHeight() - 1, r.shell().skyline() + 3), pueblo + 2));
	}

	/** The rows of the hall across the back of the court. */
	private static final int HALL = 7;
	/** The cell row's width: walls either side of a cell three wide. */
	private static final int WING = 5;

	/**
	 * The almshouse round its court: the court levelled from the street to the hall, the hall across the back
	 * ({@link Range} with its front wall on the court), the cell rows down the court's sides from the street to the hall,
	 * the court's wall and gate along the street between them.
	 */
	private static void court(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int zR = dp - HALL;
		Groundwork.level(d, 0, 0, w - 1, zR - 1, 0, true);
		Range.Built r = Range.build(d, HALL, true, 0.6, zR);
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();
		hall(d, r);

		// --- the cells: a row on the side away from the hall's door (both sides on a wide plot), from the street to the hall
		// (each kept clear of the hall's door by a cell and more, the corners and eaves of the row standing out from it)
		List<int[]> wings = new ArrayList<>();
		int gap = Tradition.of(d) == Tradition.KARST ? 3 : 2;
		boolean leftOk = doorX - (WING - 1) >= gap;
		boolean rightOk = w - WING - doorX >= gap;
		boolean both = w - 2 * WING >= 4 && leftOk && rightOk;
		if (leftOk && (both || doorX > w / 2 || !rightOk)) {
			wings.add(new int[]{0, WING - 1});
		}
		if (rightOk && (both || wings.isEmpty())) {
			wings.add(new int[]{w - WING, w - 1});
		}
		int roofTop = r.shell().skyline();
		for (int[] wing : wings) {
			roofTop = Math.max(roofTop, cells(d, wing[0], wing[1], zR - 1, base));
		}

		// --- the court: its wall along the street with the roofed gate, a wayside cross, a bench, a lamp, the herbs
		int cx0 = wings.stream().anyMatch(a -> a[0] == 0) ? WING : 0;
		int cx1 = wings.stream().anyMatch(a -> a[1] == w - 1) ? w - WING - 1 : w - 1;
		// the gate clear of the cells' eaves, where the street meets the court's level, as near the hall's door as that
		// allows; where the street nowhere meets it (a bank), the court is left open to the steps
		boolean roomy = cx1 - cx0 >= 4;
		int gx = Integer.MIN_VALUE;
		for (int x = roomy ? cx0 + 2 : cx0 + 1; x <= (roomy ? cx1 - 2 : cx1 - 1); x++) {
			if (Math.abs(d.site().ground(x, -1) - base) <= 1 && !d.site().isWater(x, -1) && (gx == Integer.MIN_VALUE || Math.abs(x - doorX) < Math.abs(gx
				- doorX))) {
				gx = x;
			}
		}
		if (gx != Integer.MIN_VALUE) {
			boolean gate = roomy && gx - 2 >= 0 && gx + 2 <= w - 1 && Bohemia.roofedGate(d, gx, base);
			List<int[]> wall = new ArrayList<>();
			for (int x = cx0; x <= cx1; x++) {
				if (Math.abs(x - gx) > (gate ? 1 : 0)) {
					wall.add(new int[]{x, 0});
				}
			}
			courtWall(d, wall, base);
		} else {
			gx = Math.max(cx0, Math.min(cx1, doorX));
		}
		Grounds.approach(d, r.shell().outside()[0], base, r.shell().outside()[1]);
		int crossX = gx - cx0 >= cx1 - gx ? cx0 + 1 : cx1 - 1;
		if (zR - 1 >= 3 && crossX != doorX && Venue.standable(d, crossX, base, zR - 2)) {
			Yardwork.wayside(d, crossX, base, zR - 2);
		}
		if (cx1 - cx0 >= 5 && zR >= 6) {
			// the herbs they keep, in a bed along the court's wall
			int hx0 = gx - cx0 >= cx1 - gx ? cx0 + 1 : gx + 2;
			int hx1 = gx - cx0 >= cx1 - gx ? gx - 2 : cx1 - 1;
			if (hx1 - hx0 >= 1) {
				Yardwork.herbBeds(d, hx0, 1, hx1, 1, base, Dirs.RIGHT);
			}
		}
		int lx = gx + (gx + 2 <= cx1 ? 2 : -2);
		if (lx >= cx0 && lx <= cx1 && Venue.standable(d, lx, base, zR - 2)) {
			Lighting.post(d, lx, base, zR - 2);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, cx0, 1, cx1, zR - 1, base, doorX);
		}
		// the mesa's pueblo: a stepped room over the flat roof, a ladder up to its terrace
		int pueblo = Pueblo.is(d) ? Pueblo.crown(d) : Integer.MIN_VALUE;
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(Math.min(d.maxHeight() - 1, roofTop + 3), pueblo + 2));
	}

	/**
	 * Whether the street meets the court's level (the plot's floor, within a block) somewhere along the front clear of
	 * its corners, where the court's gate will stand; on a bank or a stream the hall keeps to the front of the plot.
	 */
	private static boolean streetMeets(Draft d) {
		int w = d.frontage();
		for (int x = 2; x <= w - 3; x++) {
			if (Math.abs(d.site().ground(x, -1)) <= 1 && !d.site().isWater(x, -1)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The hall's two floors: below the common room (the kitchen fire on the end away from the stair, the altar with its
	 * cross against the court wall at the stair's end, the alms box by the door, long tables), above the sleeping floor;
	 * outside the cross over the door and the bell-cote on the ridge (the bell gable over a flat roof's parapet).
	 */
	private static void hall(Draft d, Range.Built r) {
		int w = d.frontage();
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();
		int hx = stairLeft ? r.ix1() : r.ix0();
		Direction face = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range.hearth(d, r, ground, hx, face, List.of(new Object[]{"minecraft:bread", 8}, new Object[]{"minecraft:potato", 12},
			new Object[]{"minecraft:bowl", 6}));
		int ax = stairLeft ? r.ix0() : r.ix1();
		int az = r.iz0() + 1;
		Direction toPeople = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (ground.free(ax, az) && ground.inside(ax + Dirs.dx(toPeople), az) && ground.take(ax, az, toPeople)) {
			Sacred.altar(d, ax, base, az, toPeople, false, Integer.MIN_VALUE);
			Venue.banner(d, ax, base + 2, az, toPeople, "white", List.<String[]>of(new String[]{"minecraft:straight_cross", "black"}), StageKind.INTERIOR);
		}
		int[] in = r.shell().inside();
		for (int k : new int[]{-1, 1}) {
			int bx = in[0] + k;
			if (ground.free(bx, r.iz0()) && ground.take(bx, r.iz0(), Dirs.BACK)) {
				Furniture.chest(d, bx, base, r.iz0(), Dirs.BACK, List.of(new Object[]{"minecraft:copper_nugget", 9}, new Object[]{"minecraft:bread", 3}));
				break;
			}
		}
		if (Range.tables(d, ground, r.ix0(), r.ix1(), w >= 13 ? 4 : 3, 1) == 0) {
			Range.tables(d, ground, r.ix0(), r.ix1(), 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, ground.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, r.ix0(), r.iz0(), r.ix1(), r.iz1(), base);

		Floorplan up = r.upper();
		List<Object[]> kit = List.of(new Object[]{"minecraft:white_wool", 1}, new Object[]{"minecraft:candle", 1});
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			Wards.row(d, up, wall, 2, false, 12, kit);
		}
		Lighting.room(d, up.x0, up.z0, up.x1, up.z1, up.y);

		// the cross over the door
		Side front = r.body().side(0, Dirs.FRONT);
		int di = front.index(doorX);
		if (di >= 1 && di <= front.last() - 1) {
			Mat cross = d.mat(Role.FRAME);
			int cy = base + r.heights()[0] + 1;
			int fz = front.outZ(di, 1);
			if (cy + 1 < d.maxHeight() && d.isEmpty(doorX, cy, fz)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(doorX, cy - 1, fz, cross.has(dev.voidmc.hearth.build.style.Form.SLAB) ? cross.slab(SlabType.TOP) : cross.fence());
					d.set(doorX, cy, fz, cross.fence());
				}
			}
		}
		if (!Yardwork.bellcote(d, r.shell().roof(), w / 2) && r.shell().roof() == null && r.shell().deck() != Integer.MIN_VALUE) {
			bellGable(d, w / 2, r.shell().deck(), r.zF());
		}
	}

	/**
	 * A bell gable (the espadaña of the dry south) standing on the front parapet of a flat roof at column x: a wall three
	 * wide rising over the parapet, an opening through it where the bell hangs, a stepped cap over it.
	 */
	private static void bellGable(Draft d, int x, int deck, int z) {
		int y0 = deck + 1;
		if (y0 + 4 >= d.maxHeight() || x - 1 < 0 || x + 1 >= d.frontage()) {
			return;
		}
		Mat wall = d.mat(Role.WALL);
		Mat trim = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int k = -1; k <= 1; k++) {
				for (int y = y0; y <= y0 + 3; y++) {
					boolean hole = k == 0 && (y == y0 + 1 || y == y0 + 2);
					d.set(x + k, y, z, hole ? Paint.of(Blocks.AIR.defaultBlockState()) : wall.full());
				}
			}
			d.set(x, y0 + 2, z, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Dirs.FRONT));
			d.set(x - 1, y0 + 4, z, trim.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
			d.set(x + 1, y0 + 4, z, trim.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
			d.set(x, y0 + 4, z, trim.full());
		}
	}

	/**
	 * A row of cells over columns x0..x1 from the street (row 0) to row z1: one storey under its own roof, the gable end to
	 * the street, a door and a lamp onto the court for each cell, partition walls between them; in each a bed with its
	 * head to the outer wall, a chest, a light. Returns the row's highest block.
	 */
	private static int cells(Draft d, int x0, int x1, int z1, int base) {
		boolean left = x0 == 0;
		Direction court = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction outer = court.getOpposite();
		int run = z1 - 1;
		int n = run >= 5 ? 2 : 1;
		// the cells' rows: [from, to] inside the walls, a partition between
		int[][] rows = new int[n][];
		if (n == 1) {
			rows[0] = new int[]{1, run};
		} else {
			int a = (run - 1) / 2;
			rows[0] = new int[]{1, a};
			rows[1] = new int[]{a + 2, run};
		}
		int[] doors = new int[n];
		for (int k = 0; k < n; k++) {
			doors[k] = rows[k][0] + (rows[k][1] - rows[k][0]) / 2;
		}
		Body body = new Body(x0, 0, x1, z1, base, new int[]{4}, false, false);
		d.body(x0, 0, x1, z1);
		Shell.Built sh = Shell.build(d, body, Direction.Axis.Z, court, doors, 1, 0.5, false, Tradition.of(d).masonry(), false, 0);
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		if (n == 2) {
			int pz = rows[0][1] + 1;
			Mat wall = d.mat(Role.WALL);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = ix0; x <= ix1; x++) {
					for (int y = base; y < base + 3; y++) {
						d.set(x, y, pz, wall.full());
					}
				}
			}
		}
		int ox = left ? ix0 : ix1;
		int inX = left ? ix1 : ix0;
		for (int k = 0; k < n; k++) {
			int door = sh.doors()[k];
			Floorplan cell = new Floorplan(ix0, rows[k][0], ix1, rows[k][1], base, 3);
			cell.keep(inX, door);
			Wards.row(d, cell, outer, 2, false, 1, null);
			// the chest against a wall, its front free
			boolean chest = false;
			for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT, outer}) {
				for (int[] c : cell.along(wall)) {
					if (!chest && cell.free(c[0], c[1]) && cell.inside(c[0] - Dirs.dx(wall), c[1] - Dirs.dz(wall))
						&& cell.take(c[0], c[1], wall.getOpposite())) {
						Furniture.chest(d, c[0], base, c[1], wall.getOpposite(), List.of(new Object[]{"minecraft:white_wool", 1}, new Object[]{
							"minecraft:candle", 1}));
						chest = true;
					}
				}
			}
			Lighting.room(d, ix0, rows[k][0], ix1, rows[k][1], base);
		}
		// a bench against the cells' court wall where the old sit in the sun
		Side cw = body.side(0, court);
		int bz = n == 2 ? rows[0][1] + 1 : -1;
		if (bz > 0) {
			int bx = cw.outX(cw.index(bz), 1);
			if (Venue.standable(d, bx, base, bz) && Venue.standable(d, bx, base, bz + 1) && Math.abs(bz - sh.doors()[0]) > 1
				&& Math.abs(bz + 1 - sh.doors()[1]) > 1) {
				Venue.bench(d, bx, base, bz, Dirs.BACK, 2, outer);
			}
		}
		// the name board on the street gable
		List<String[]> name = Signboard.name(d, "almshouse", Signboard.ALMSHOUSES);
		Side gable = body.side(0, Dirs.FRONT);
		for (int i : new int[]{gable.middle(), gable.middle() + (left ? 1 : -1)}) {
			if (Signboard.bracket(d, gable.x(i), base + 3, gable.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		return sh.skyline();
	}

	/** The court's wall along the street: the walls' stone a block high under a coping of slabs, footed on the ground. */
	private static void courtWall(Draft d, List<int[]> cells, int y) {
		Mat stone = d.mat(Role.PLINTH);
		Mat cap = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells) {
				if (!d.isEmpty(c[0], y, c[1]) || !d.isEmpty(c[0], y + 1, c[1])) {
					continue;
				}
				for (int yy = Math.min(y, d.site().ground(c[0], c[1])) - 1; yy <= y; yy++) {
					d.set(c[0], yy, c[1], stone.full());
				}
				d.set(c[0], y + 1, c[1], cap.has(dev.voidmc.hearth.build.style.Form.SLAB) ? cap.slab(SlabType.BOTTOM) : stone.full());
			}
		}
	}
}
