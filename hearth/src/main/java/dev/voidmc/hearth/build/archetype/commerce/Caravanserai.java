package dev.voidmc.hearth.build.archetype.commerce;

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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.civic.Trade;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Ring;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:commerce/caravanserai}: the walled inn of the caravan roads, a fortress round a courtyard. High blank
 * outer walls with towers at the corners standing over them; in the front the great portal, a tall block over the gate
 * passage, its arch deep enough for a loaded camel. Round the courtyard two storeys of cells behind an arcade of piers and
 * arches: below, the stables (hay, troughs, the beasts tied at the piers) and the storerooms, the innkeeper's and the
 * trader's counters either side of the gate, the kitchen in the back range; above, round a gallery on the arcade's roof,
 * the travellers' rooms, a bed, a chest and a rug in each. In the courtyard's middle a fountain in its basin, crates and
 * bales of the caravans about it. Flat roofs behind parapets in the dry lands, tiled ring roofs where the culture roofs so.
 *
 * <p>POIs: BEDs (8 or more), the INNKEEPER's and the TRADER's JOB_SITEs with COUNTER and MARKET_STALL, HITCHING_POSTs,
 * STORAGE, the HEARTH, TABLE, SEATs, MEETING_POINT (the fountain), the DOOR. Params: {@code variant} (int).
 */
public final class Caravanserai implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/caravanserai"), ArchetypeCategory.COMMERCE,
		Set.of(JobType.INNKEEPER, JobType.TRADER), 8, 21, 31, 21, 31, 10, 14, SettlementTier.TOWN,
		Set.of(Culture.DESERT, Culture.STEPPE, Culture.MESA, Culture.MEDITERRANEAN, Culture.KARST), Set.of(), List.of(), 1, 1.0);

	/** The ring of the courtyard: the outer wall (0), three cells of rooms (1-3), the inner wall (4), the arcade (5-6, its piers at 6). */
	private static final int R = 7;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxH = d.maxHeight();
		Tradition t = Tradition.of(d);
		boolean flat = t.flatRoofed() || t == Tradition.ADOBE;
		int base = 0;
		int zF = 1;
		int zB = dp - 1;
		int tier = 4;
		int plate = base + 2 * tier - 1;
		int upY = base + tier;
		Ring ring = new Ring(0, zF, w - 1, zB, R, true);
		int cx = w / 2;

		Mat wall = d.mat(Role.WALL);
		Mat found = d.mat(Role.FOUNDATION);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat floor = d.mat(Role.FLOOR);
		Mat paving = d.mat(Role.PAVING);
		Mat path = d.mat(Role.PATH);

		// --- the ground level with the street, the courtyard paved, the cells floored
		Groundwork.level(d, 0, 0, w - 1, dp - 1, base, true);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z <= zB; z++) {
					int k = ring.k(x, z);
					d.set(x, base - 1, z, k == 0 || k == 4 ? found.full() : k < 4 ? floor.full() : Noise.unit(d.seed(), x, 5, z) < 0.75 ? paving.full()
						: path.full());
				}
			}
		}

		// --- the outer walls and the inner walls of the cells, partitions every fourth cell along the wings
		List<int[]> doors = new ArrayList<>();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z <= zB; z++) {
					int k = ring.k(x, z);
					boolean partition = k >= 1 && k <= 3 && isPartition(ring, x, z);
					if (k != 0 && k != 4 && !partition) {
						continue;
					}
					d.set(x, base - 1, z, found.full());
					for (int y = base; y < plate; y++) {
						Paint p = k == 0 && y == base ? found.full() : wall.full();
						if (k == 0 && (y == upY - 1 || y == plate - 1) && t == Tradition.STEPPE) {
							p = accent.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
		}
		// a door in the inner wall of each cell, on both storeys, and a slit window in the outer wall of the upper cells
		for (int x = 0; x < w; x++) {
			for (int z = zF; z <= zB; z++) {
				if (ring.k(x, z) != 4 || !cellMiddle(ring, x, z)) {
					continue;
				}
				Direction in = ring.inward(x, z);
				doors.add(new int[]{x, z, in.get2DDataValue()});
				try (var s = d.stage(StageKind.OPENINGS)) {
					for (int y : new int[]{base, upY}) {
						d.door(x, y, z, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), in, DoorHingeSide.LEFT);
					}
				}
				int ox = x - Dirs.dx(in) * 4;
				int oz = z - Dirs.dz(in) * 4;
				try (var s = d.stage(StageKind.OPENINGS)) {
					d.set(ox, upY + 1, oz, d.mat(Role.WINDOW).has(Form.PANE) ? d.mat(Role.WINDOW).pane() : Paint.of(Blocks.IRON_BARS.defaultBlockState()));
				}
			}
		}
		// the upper floor over the cells and the arcade (the gallery)
		for (int x = 1; x < w - 1; x++) {
			for (int z = zF + 1; z < zB; z++) {
				int k = ring.k(x, z);
				if (k >= 1 && k <= 6) {
					Floors.boards(d, x, z, x, z, upY - 1);
				}
			}
		}

		// --- the arcade: piers at the courtyard's edge with arches between, the gallery's parapet over them
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z <= zB; z++) {
					if (ring.k(x, z) != 6) {
						continue;
					}
					Direction in = ring.inward(x, z);
					int along = in.getAxis() == Direction.Axis.Z ? x : z;
					boolean corner = ring.k(x + 1, z) == 6 && ring.k(x, z + 1) == 6 || ring.k(x - 1, z) == 6 && ring.k(x, z - 1) == 6
						|| ring.k(x + 1, z) == 6 && ring.k(x, z - 1) == 6 || ring.k(x - 1, z) == 6 && ring.k(x, z + 1) == 6;
					boolean pier = corner || along % 3 == 0;
					if (pier) {
						for (int y = base; y < upY - 1; y++) {
							d.set(x, y, z, accent.full());
						}
					} else {
						// the arch's haunch: an upside-down stair either side, a slab at the crown
						Direction run = in.getAxis() == Direction.Axis.Z ? Dirs.RIGHT : Dirs.BACK;
						int m = Math.floorMod(along, 3);
						Paint a = m == 1 ? accent.stairsOrSlab(run.getOpposite(), Half.TOP) : accent.stairsOrSlab(run, Half.TOP);
						d.set(x, upY - 2, z, a);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = 0; x < w; x++) {
				for (int z = zF; z <= zB; z++) {
					if (ring.k(x, z) == 6 && d.isEmpty(x, upY, z)) {
						d.set(x, upY, z, accent.has(Form.WALL) ? accent.wall() : d.mat(Role.RAILING).fence());
					}
				}
			}
		}

		// --- the roof: the deck behind a parapet in the dry lands, else a ring of tiles round the courtyard
		ring.roof(d, plate, flat, zB);
		// corner towers standing over the walls
		int towerTop = Math.min(maxH - 1, plate + 3);
		for (int[] c : new int[][]{{0, zF}, {w - 3, zF}, {0, zB - 2}, {w - 3, zB - 2}}) {
			tower(d, c[0], c[1], plate, towerTop, flat);
		}

		// --- the gate: a passage through the front range, the portal standing over it
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int z = zF; z <= zF + R - 1; z++) {
					for (int y = base; y < base + 4; y++) {
						d.set(x, y, z, Paint.of(Blocks.AIR.defaultBlockState()));
					}
					d.set(x, base - 1, z, paving.full());
				}
			}
		}
		portal(d, cx, zF, base, plate, maxH);
		d.inStage(StageKind.OPENINGS, () -> {
			d.door(cx - 1, base, zF, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.RIGHT);
			d.door(cx + 1, base, zF, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT, DoorHingeSide.LEFT);
		});
		d.entrance(cx, base, zF - 1);
		d.poi(PoiType.DOOR, cx, base, zF - 1, Dirs.BACK);
		Lighting.doorLamp(d, cx - 2, base + 2, zF - 1, Dirs.BACK);
		Lighting.doorLamp(d, cx + 2, base + 2, zF - 1, Dirs.BACK);
		Lighting.room(d, cx - 1, zF + 1, cx + 1, zF + R - 1, base);

		// --- the cells: counters by the gate, the kitchen at the back, stables and stores below, rooms above
		int beds = 0;
		boolean inn = false;
		boolean trade = false;
		boolean kitchen = false;
		for (int[] dr : doors) {
			int x = dr[0];
			int z = dr[1];
			Direction in = Direction.from2DDataValue(dr[2]);
			Direction out = in.getOpposite();
			// the cell's middle column runs from the door's inside (k = 3) to the outer wall (k = 1)
			int mx = x + Dirs.dx(out);
			int mz = z + Dirs.dz(out);
			Direction across = Dirs.right(in);
			if (Math.abs(x - cx) <= 1 && in == Dirs.BACK) {
				continue;
			}
			// below
			boolean front = in == Dirs.BACK;
			if (front && !inn) {
				counterCell(d, mx, mz, in, across, base, JobType.INNKEEPER);
				inn = true;
			} else if (front && !trade) {
				counterCell(d, mx, mz, in, across, base, JobType.TRADER);
				trade = true;
			} else if (in == Dirs.FRONT && !kitchen) {
				kitchenCell(d, mx, mz, in, across, base);
				kitchen = true;
			} else if (Noise.unit(d.seed(), x, 11, z) < 0.7) {
				stableCell(d, mx, mz, in, across, base);
			} else {
				storeCell(d, mx, mz, in, across, base);
			}
			// above: a traveller's room
			beds += roomCell(d, mx, mz, in, across, upY);
			int[] r = cellRect(mx, mz, in, across);
			Lighting.room(d, r[0], r[1], r[2], r[3], upY);
			Lighting.room(d, r[0], r[1], r[2], r[3], base);
		}

		// --- the arcade and the gallery lit, hitching posts at the piers of the stables' wings
		for (int y : new int[]{base, upY}) {
			lightRing(d, ring, 5, y);
		}
		int hp = 0;
		for (int x = R; x < w - R && hp < 6; x += 3) {
			for (int z : new int[]{R, zB - R}) {
				if (Venue.standable(d, x, base, z) && hp < 6) {
					d.poi(PoiType.HITCHING_POST, x, base, z, z == R ? Dirs.FRONT : Dirs.BACK);
					hp++;
				}
			}
		}
		for (int z = zF + R + 1; z < zB - R && hp < 8; z += 3) {
			for (int x : new int[]{R, w - 1 - R}) {
				if (Venue.standable(d, x, base, z)) {
					d.poi(PoiType.HITCHING_POST, x, base, z, x == R ? Dirs.LEFT : Dirs.RIGHT);
					hp++;
				}
			}
		}

		// --- the stairs to the gallery from the courtyard's back corners, climbing toward the back range
		for (int sx : new int[]{R, w - 1 - R}) {
			int top = zB - R;
			Stairs.straight(d, sx, base, top - tier + 1, Dirs.BACK, tier, 1);
			try (var s = d.stage(StageKind.INTERIOR)) {
				// the gallery's parapet opened where the stair arrives
				d.set(sx, upY, zB - R + 1, Paint.of(Blocks.AIR.defaultBlockState()));
			}
		}

		// --- the courtyard: the fountain at the middle, bales and crates of the caravans about it
		int yz0 = zF + R;
		int yz1 = zB - R;
		int fz = (yz0 + yz1) / 2;
		fountain(d, cx, base, fz);
		d.poi(PoiType.MEETING_POINT, cx, base, fz - 2, Dirs.BACK);
		for (int[] c : new int[][]{{R + 1, yz0 + 1}, {w - 2 - R, yz0 + 1}, {R + 1, yz1 - tier - 1}, {w - 2 - R, yz1 - tier - 1}}) {
			if (Venue.standable(d, c[0], base, c[1]) && Noise.unit(d.seed(), c[0], 2, c[1]) < 0.8) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(c[0], base, c[1], Noise.unit(d.seed(), c[0], 9, c[1]) < 0.5 ? Blocks.HAY_BLOCK.defaultBlockState()
						: States.facing(Blocks.BARREL, Direction.UP));
				}
			}
		}
		for (int[] c : new int[][]{{cx - 3, fz - 3}, {cx + 3, fz + 3}, {cx + 3, fz - 3}, {cx - 3, fz + 3}}) {
			if (Venue.standable(d, c[0], base, c[1])) {
				Lighting.post(d, c[0], base, c[1]);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxH - 1);
	}

	/** Partitions between cells: every fourth cell along each wing, counted from the wing's middle so the gate falls between. */
	private static boolean isPartition(Ring ring, int x, int z) {
		Direction in = ring.inward(x, z);
		int along = in.getAxis() == Direction.Axis.Z ? x - (ring.x0() + ring.x1()) / 2 : z - (ring.z0() + ring.z1()) / 2;
		// the corners of the ring are solid (where two wings meet)
		int kx = Math.min(x - ring.x0(), ring.x1() - x);
		int kz = Math.min(z - ring.z0(), ring.z1() - z);
		if (kx < 4 && kz < 4) {
			return true;
		}
		return Math.floorMod(along - 2, 4) == 0;
	}

	/** The door column of a cell: the middle of the three between partitions, clear of the ring's corners. */
	private static boolean cellMiddle(Ring ring, int x, int z) {
		Direction in = ring.inward(x, z);
		int along = in.getAxis() == Direction.Axis.Z ? x - (ring.x0() + ring.x1()) / 2 : z - (ring.z0() + ring.z1()) / 2;
		int kx = Math.min(x - ring.x0(), ring.x1() - x);
		int kz = Math.min(z - ring.z0(), ring.z1() - z);
		if (kx < 6 && kz < 6) {
			return false;
		}
		return Math.floorMod(along, 4) == 0;
	}

	/** A cell's three-by-three floor {x0, z0, x1, z1}: from (mx, mz) inside its door two cells out to the outer wall. */
	private static int[] cellRect(int mx, int mz, Direction in, Direction across) {
		Direction out = in.getOpposite();
		int ax = mx + Dirs.dx(across);
		int az = mz + Dirs.dz(across);
		int bx = mx - Dirs.dx(across) + 2 * Dirs.dx(out);
		int bz = mz - Dirs.dz(across) + 2 * Dirs.dz(out);
		return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
	}

	/** The cell k from the outer wall (1: against it, 3: inside the door) in the cell's middle column, shifted across by s. */
	private static int[] at(int mx, int mz, Direction in, Direction across, int k, int s) {
		Direction out = in.getOpposite();
		return new int[]{mx + Dirs.dx(out) * (3 - k) + Dirs.dx(across) * s, mz + Dirs.dz(out) * (3 - k) + Dirs.dz(across) * s};
	}

	/** A counter across the cell's middle: the keeper behind it against the outer wall, customers coming in at the door. */
	private static void counterCell(Draft d, int mx, int mz, Direction in, Direction across, int y, JobType job) {
		Direction out = in.getOpposite();
		int[] c = at(mx, mz, in, across, 2, -1);
		Trade.counter(d, c[0], y, c[1], across, 2, in, job);
		int[] b = at(mx, mz, in, across, 1, -1);
		Furniture.barrel(d, b[0], y, b[1], across);
		int[] k = at(mx, mz, in, across, 1, 1);
		Furniture.barrel(d, k[0], y, k[1], across.getOpposite());
		int[] keeper = at(mx, mz, in, across, 1, 0);
		d.poi(PoiType.STORAGE, keeper[0], y, keeper[1], out);
	}

	/** The kitchen: a fire against the outer wall, a chest of stores beside it, a table and a stool. */
	private static void kitchenCell(Draft d, int mx, int mz, Direction in, Direction across, int y) {
		int[] h = at(mx, mz, in, across, 1, 0);
		Furniture.hearth(d, h[0], y, h[1], in, true);
		int[] c = at(mx, mz, in, across, 1, 1);
		Furniture.chest(d, c[0], y, c[1], in, List.of(new Object[]{"minecraft:bread", 12}, new Object[]{"minecraft:dried_kelp", 6},
			new Object[]{"minecraft:bowl", 6}));
		int[] t = at(mx, mz, in, across, 2, -1);
		Furniture.table(d, t[0], y, t[1]);
		d.poi(PoiType.TABLE, t[0], y, t[1], across);
		int[] ch = at(mx, mz, in, across, 3, -1);
		Furniture.chair(d, ch[0], y, ch[1], in);
	}

	/** A stable: a trough against the outer wall, a bale of hay in the corner, the beast's stand before them. */
	private static void stableCell(Draft d, int mx, int mz, Direction in, Direction across, int y) {
		int[] tr = at(mx, mz, in, across, 1, -1);
		Yard.trough(d, tr[0], y, tr[1], across, 2);
		int[] hay = at(mx, mz, in, across, 1, 1);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (d.isEmpty(hay[0], y, hay[1])) {
				d.set(hay[0], y, hay[1], Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
		int[] stand = at(mx, mz, in, across, 2, 0);
		d.poi(PoiType.HITCHING_POST, stand[0], y, stand[1], in.getOpposite());
	}

	/** A storeroom of the caravans' goods: a chest against the outer wall, barrels either side. */
	private static void storeCell(Draft d, int mx, int mz, Direction in, Direction across, int y) {
		int[] c = at(mx, mz, in, across, 1, 0);
		Furniture.chest(d, c[0], y, c[1], in, List.of(new Object[]{"minecraft:white_wool", 6}, new Object[]{"minecraft:sugar", 4}));
		for (int s : new int[]{-1, 1}) {
			int[] b = at(mx, mz, in, across, 1, s);
			Furniture.barrel(d, b[0], y, b[1], in);
		}
	}

	/** A traveller's room: the bed along the outer wall, a chest beside it, a rug. Answers the beds. */
	private static int roomCell(Draft d, int mx, int mz, Direction in, Direction across, int y) {
		int[] head = at(mx, mz, in, across, 1, 1);
		Furniture.bed(d, head[0], y, head[1], across.getOpposite());
		int[] c = at(mx, mz, in, across, 1, -1);
		Furniture.chest(d, c[0], y, c[1], in, List.<Object[]>of(new Object[]{"minecraft:white_wool", 1}));
		Mat cloth = d.mat(Role.CLOTH);
		int[] r = at(mx, mz, in, across, 2, -1);
		if (cloth.has(Form.CARPET)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				if (d.isEmpty(r[0], y, r[1])) {
					d.set(r[0], y, r[1], cloth.carpet());
				}
			}
		}
		return 1;
	}

	/** Lights the ring cells k (the arcade or the gallery) at standing level y, a lantern hung every few cells. */
	private static void lightRing(Draft d, Ring ring, int k, int y) {
		int n = 0;
		for (int x = ring.x0(); x <= ring.x1(); x++) {
			for (int z = ring.z0(); z <= ring.z1(); z++) {
				if (ring.k(x, z) != k - 1 || n++ % 4 != 0) {
					continue;
				}
				// a lantern on the inner wall's face, toward the arcade
				Direction in = ring.inward(x, z);
				int ax = x + Dirs.dx(in);
				int az = z + Dirs.dz(in);
				// hung from the boards (or the roof) over the walk, clear of the floor above
				if (d.isEmpty(ax, y + 2, az) && d.isEmpty(ax, y, az) && !d.isEmpty(ax, y + 3, az)) {
					Furniture.hangingLantern(d, ax, y + 3, az, 0);
				}
			}
		}
	}

	/** A corner tower: a three-by-three block standing over the walls to {@code top}, a parapet or a cap of the roofing. */
	private static void tower(Draft d, int x0, int z0, int plate, int top, boolean flat) {
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = x0; x < x0 + 3; x++) {
				for (int z = z0; z < z0 + 3; z++) {
					for (int y = plate; y < top; y++) {
						d.set(x, y, z, wall.full());
					}
					boolean edge = x == x0 || x == x0 + 2 || z == z0 || z == z0 + 2;
					if (flat) {
						if (edge && ((x + z) & 1) == 0 && top < d.maxHeight()) {
							d.set(x, top, z, wall.full());
						}
					} else if (top < d.maxHeight()) {
						int sx = Integer.signum(x0 + 1 - x);
						int sz = Integer.signum(z0 + 1 - z);
						d.set(x, top, z, edge ? roof.stairsOrSlab(sx != 0 ? Dirs.of(sx, 0) : Dirs.of(0, sz), Half.BOTTOM) : roof.full());
					}
				}
			}
		}
	}

	/**
	 * The portal: a tall block of the accent masonry five wide over the gate passage, standing two over the walls, its
	 * arch three wide and four high framed in a moulding, a band of the trim over it.
	 */
	private static void portal(Draft d, int cx, int zF, int base, int plate, int maxH) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat trim = d.mat(Role.ACCENT);
		int top = Math.min(maxH - 1, plate + 2);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = cx - 3; x <= cx + 3; x++) {
				for (int z = zF; z <= zF + 1; z++) {
					for (int y = base; y <= top; y++) {
						boolean opening = Math.abs(x - cx) <= 1 && y < base + 4;
						if (opening) {
							continue;
						}
						if (z == zF + 1 && y < plate) {
							continue;
						}
						d.set(x, y, z, Math.abs(x - cx) == 3 || y == top ? accent.full() : y == base + 5 ? trim.full() : d.mat(Role.WALL).full());
					}
				}
			}
			// the arch: upside-down stairs at the opening's top corners
			d.set(cx - 1, base + 3, zF, accent.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(cx + 1, base + 3, zF, accent.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(cx, base + 4, zF, accent.slab(SlabType.BOTTOM));
		}
	}

	/** A fountain: a basin of the accent stone round a pool, a spout in the middle. */
	private static void fountain(Draft d, int x, int y, int z) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						d.set(x, y - 1, z, accent.full());
						d.set(x, y, z, accent.wall());
						d.set(x, y + 1, z, States.of(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
					} else {
						d.set(x + dx, y - 1, z + dz, Blocks.WATER.defaultBlockState());
						d.set(x + dx, y - 2, z + dz, accent.full());
					}
				}
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
						d.set(x + dx, y, z + dz, accent.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}
}
