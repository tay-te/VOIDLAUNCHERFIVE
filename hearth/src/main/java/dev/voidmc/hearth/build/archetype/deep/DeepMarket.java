package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:deep/market}: the deep town's market under the vault, lit by lanterns (LIVING_WORLD.md §8.2). A paved
 * court open to the street, closed on its three other sides by arcaded wings of stalls: dressed piers and arches, a
 * counter in every bay with the merchant behind it and his stock against the wall, a cloth awning over the counter,
 * the wings roofed in the culture's own covering under a pent toward the court, a pavilion at each back corner rising
 * over them under a pyramid roof. At the court's heart stands a lantern tree — a stone pillar with iron arms hung with
 * lanterns over a basin and benches — and festoons of lanterns hang from chains strung across the court between the
 * wings. Wares by stall: the deep's own (glow berries, mushrooms, the realm's minerals, ore and iron) and what comes down
 * the lift (bread, wool, candles).
 *
 * <p>Params: {@code realm}, {@code ceiling_y}, {@code character}, {@code local} ({@link DeepSite}); {@code variant}.
 */
public final class DeepMarket implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/market"), ArchetypeCategory.UNDERGROUND, Set.of(JobType.MERCHANT), 0,
		11, 17, 11, 17, 8, 12, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public TerrainProbe terrain(PlotSpec spec, TerrainProbe probe) {
		return DeepSite.probeFor(spec, probe);
	}

	/** One wing: its cells across (outer wall to arcade line), and the bays along it. */
	private record Wing(Direction toCourt, int[][] bays) {
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		DeepSite ds = DeepSite.of(d);
		Glow g = Glow.of(ds.character());
		int h = Math.max(8, Math.min(d.maxHeight(), ds.top() + 1));
		int wd = w >= 15 ? 4 : 3;
		int arch = 4;
		int eave = arch + 1;
		d.body(0, 0, w - 1, dp - 1);
		d.eave(eave);

		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Groundwork.footings(d, 0, 0, w - 1, dp - 1, -1);
		Mat paving = d.mat(Role.PAVING);
		Mat accent = Rock.dressing(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
		// the three wings: left, right, back
		int cx0 = wd;
		int cx1 = w - 1 - wd;
		int cz1 = dp - 1 - wd;
		List<int[]> stalls = new ArrayList<>();
		wingX(d, 0, wd - 1, 0, cz1, Dirs.RIGHT, arch, stalls);
		wingX(d, w - 1, w - wd, 0, cz1, Dirs.LEFT, arch, stalls);
		// the merchants' doors: through the first pier of each side wing from the court's mouth into the walk behind the
		// counters, a lantern over each on a bracket
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int side = 0; side < 2; side++) {
			int line = side == 0 ? wd - 1 : w - wd;
			Direction toCourt = side == 0 ? Dirs.RIGHT : Dirs.LEFT;
			int walk = line - Dirs.dx(toCourt);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int y = 0; y <= 1; y++) {
					d.air(line, y, 0);
					d.air(walk, y, 0);
				}
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(line, 0, 0, door, toCourt, DoorHingeSide.LEFT));
			d.poi(PoiType.DOOR, line + Dirs.dx(toCourt), 0, 0, toCourt.getOpposite());
			Lighting.doorLamp(d, line + Dirs.dx(toCourt), 2, 0, toCourt.getOpposite());
		}
		wingZ(d, 0, w - 1, cz1 + 1, dp - 1, Dirs.FRONT, arch, stalls);
		roofs(d, w, dp, wd, eave, h);
		pavilions(d, w, dp, wd, eave, h, g);
		// stalls: counters, awnings, wares, the merchants and their customers
		for (int i = 0; i < stalls.size(); i++) {
			stall(d, stalls.get(i), i, g);
		}
		// the court's heart: the lantern tree over a basin and benches, festoons across the court
		int tx = w / 2;
		int tz = Math.max(2, cz1 / 2);
		ring(d, w, dp, tx, tz);
		lanternTree(d, tx, tz, Math.min(h - 2, eave + 3), cx1 - cx0 >= 6 && cz1 >= 7, g);
		festoons(d, cx0, cx1, cz1, eave, tx, tz);
		// the way in
		d.entrance(w / 2, 0, -1);
		d.poi(PoiType.MEETING_POINT, tx, 0, Math.max(0, tz - 2), Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, h));
	}

	/** A border of the dressing stone laid in the court round the lantern tree's square. */
	private static void ring(Draft d, int w, int dp, int tx, int tz) {
		Mat accent = Rock.dressing(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = tz - 3; z <= tz + 3; z++) {
				for (int x = tx - 3; x <= tx + 3; x++) {
					if (Math.max(Math.abs(x - tx), Math.abs(z - tz)) == 3 && Rock.inPlot(d, x, z)) {
						d.set(x, -1, z, accent.full());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- the wings

	/** A side wing along z from z0 to z1, its outer wall at x = {@code outer}, its arcade at x = {@code line} facing the court. */
	private static void wingX(Draft d, int outer, int line, int z0, int z1, Direction toCourt, int arch, List<int[]> stalls) {
		int lo = Math.min(outer, line);
		int hi = Math.max(outer, line);
		shell(d, lo, hi, z0, z1, outer, line, true, arch);
		for (int z = z0 + 1; z + 1 <= z1; z += 3) {
			stalls.add(new int[]{line, z, Dirs.dx(toCourt), Dirs.dz(toCourt), outer, z + 1});
		}
	}

	/** The back wing along x. */
	private static void wingZ(Draft d, int x0, int x1, int z0, int z1, Direction toCourt, int arch, List<int[]> stalls) {
		int outer = z1;
		int line = z0;
		shell(d, x0, x1, line, outer, outer, line, false, arch);
		int wd = z1 - z0 + 1;
		int first = wd + Math.floorMod(1 - wd, 3);
		for (int x = first; x + 1 <= x1 - wd; x += 3) {
			stalls.add(new int[]{x, line, 0, -1, outer, x + 1});
		}
	}

	/**
	 * A wing's masonry: the outer wall (rough low, dressed above), the arcade line of piers every third cell with arches
	 * between them over the counters, the wall over the arcade to the eaves, the floor under the stalls, the air inside.
	 */
	private static void shell(Draft d, int a0, int a1, int b0, int b1, int outer, int line, boolean alongZ, int arch) {
		Mat wall = d.mat(Role.WALL);
		Mat rough = d.mat(Role.FOUNDATION);
		Mat plinth = d.mat(Role.PLINTH);
		Mat dress = Rock.dressing(d);
		Mat floor = d.mat(Role.FLOOR);
		int eave = arch + 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int u = alongZ ? b0 : a0; u <= (alongZ ? b1 : a1); u++) {
				// the outer wall
				int ox = alongZ ? outer : u;
				int oz = alongZ ? u : outer;
				for (int y = -1; y < eave; y++) {
					d.set(ox, y, oz, y == -1 ? plinth.full() : y <= 1 ? rough.full() : wall.full());
				}
				// the arcade line: a pier every third cell, arches between
				int lx = alongZ ? line : u;
				int lz = alongZ ? u : line;
				int start = alongZ ? b0 : a0;
				int k = Math.floorMod(u - start, 3);
				if (k == 0) {
					for (int y = -1; y < eave; y++) {
						d.set(lx, y, lz, y == -1 ? plinth.full() : y == arch - 1 ? Paint.of(Rock.carved(d).defaultBlockState()) : dress.full());
					}
				} else {
					Direction toPier = alongZ ? (k == 1 ? Dirs.FRONT : Dirs.BACK) : (k == 1 ? Dirs.LEFT : Dirs.RIGHT);
					d.set(lx, arch - 1, lz, dress.stairsOrSlab(toPier, Half.TOP));
					d.set(lx, arch, lz, wall.full());
				}
				// the floor and the air of the stall behind the arcade
				for (int v = Math.min(outer, line) + 1; v < Math.max(outer, line); v++) {
					int fx = alongZ ? v : u;
					int fz = alongZ ? u : v;
					d.set(fx, -1, fz, floor.full());
				}
			}
			// the wing's end walls where it meets the street (side wings) or the side wings (back wing)
			if (alongZ) {
				for (int v = Math.min(outer, line); v <= Math.max(outer, line); v++) {
					for (int y = -1; y < eave; y++) {
						BlockState b = y == -1 ? plinth.full().at(v, y, b0) : v == line || v == outer ? dress.full().at(v, y, b0) : wall.full().at(v, y, b0);
						if (y >= 1 || v == outer || v == line) {
							d.set(v, y, b0, b);
						}
					}
				}
			}
		}
		int x0 = Math.min(alongZ ? outer : a0, alongZ ? line : a1);
		int x1 = Math.max(alongZ ? outer : a0, alongZ ? line : a1);
		int z0 = Math.min(alongZ ? b0 : outer, alongZ ? b1 : line);
		int z1 = Math.max(alongZ ? b0 : outer, alongZ ? b1 : line);
		d.carve(x0, 0, z0, x1, arch - 1, z1);
	}

	/**
	 * The wings' roofs: a lean-to of the culture's own covering rising from the outer wall to the arcade, its high edge
	 * over the court finished with a parapet of the dressing stone.
	 */
	private static void roofs(Draft d, int w, int dp, int wd, int eave, int h) {
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int cz1 = dp - 1 - wd;
		Roofs.Field left = Roofs.shed(0, 0, wd - 1, cz1, Dirs.RIGHT, Roofs.Profile.straight(0.5));
		Roofs.Field right = Roofs.shed(w - wd, 0, w - 1, cz1, Dirs.LEFT, Roofs.Profile.straight(0.5));
		Roofs.Field back = Roofs.shed(0, cz1 + 1, w - 1, dp - 1, Dirs.FRONT, Roofs.Profile.straight(0.5));
		Roofs.raster(d, left, eave, 0, 0, wd - 1, cz1, sheet, trim, Habits.Ridge.SLAB);
		Roofs.raster(d, right, eave, w - wd, 0, w - 1, cz1, sheet, trim, Habits.Ridge.SLAB);
		Roofs.raster(d, back, eave, wd, cz1 + 1, w - 1 - wd, dp - 1, sheet, trim, Habits.Ridge.SLAB);
		if (!DeepManner.thatches(d)) {
			homeCovering(d, 0, eave, 0, w - 1, eave + 3, dp - 1, sheet);
		}
	}

	/** Restates what a material drew in a box in the culture's own roof covering (same shapes). */
	static void homeCovering(Draft d, int x0, int y0, int z0, int x1, int y1, int z1, Mat from) {
		java.util.Set<Block> mine = new java.util.HashSet<>();
		for (var e : from.palette().entries()) {
			for (Form f : new Form[]{Form.FULL, Form.STAIRS, Form.SLAB}) {
				Block b = e.material().block(f);
				if (b != null) {
					mine.add(b);
				}
			}
		}
		Paint stairs = DeepManner.home(d, Role.ROOF, Form.STAIRS, s -> s);
		Paint slab = DeepManner.home(d, Role.ROOF, Form.SLAB, s -> s);
		Paint full = DeepManner.home(d, Role.ROOF, Form.FULL, s -> s);
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					BlockState s = d.get(x, y, z);
					if (s == null || !mine.contains(s.getBlock())) {
						continue;
					}
					BlockState n;
					if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
						BlockState b = stairs.at(x, y, z);
						n = b == null ? null : b.getBlock().withPropertiesOf(s);
					} else if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
						BlockState b = slab.at(x, y, z);
						n = b == null ? null : b.getBlock().withPropertiesOf(s);
					} else {
						n = full.at(x, y, z);
					}
					if (n != null) {
						d.restate(x, y, z, n);
					}
				}
			}
		}
	}

	/** A pavilion at each back corner over the wings' meeting: its walls up a storey, a pyramid roof, a lantern in it. */
	private static void pavilions(Draft d, int w, int dp, int wd, int eave, int h, Glow g) {
		Mat wall = d.mat(Role.WALL);
		Mat dress = Rock.dressing(d);
		Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		Paint home = DeepManner.home(d, Role.WALL).orElse(wall.full());
		int top = Math.min(h - 4, eave + 3);
		if (top <= eave) {
			return;
		}
		for (int side = 0; side < 2; side++) {
			int x0 = side == 0 ? 0 : w - wd;
			int x1 = x0 + wd - 1;
			int z0 = dp - wd;
			int z1 = dp - 1;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
						boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
						for (int y = eave; y <= top; y++) {
							if (edge) {
								d.set(x, y, z, corner ? dress.full() : home);
							} else {
								d.set(x, y, z, Blocks.AIR.defaultBlockState());
							}
						}
						if (!edge) {
							d.set(x, eave - 1, z, d.mat(Role.FLOOR).full());
						}
					}
				}
				// a window to the court and one outward
				int mx = (x0 + x1) / 2;
				int mz = (z0 + z1) / 2;
				d.set(mx, eave + 1, z0, d.mat(Role.GLASS).pane());
				d.set(side == 0 ? x0 : x1, eave + 1, mz, d.mat(Role.GLASS).pane());
			}
			Roofs.Field pyr = Roofs.hip(x0 - (side == 0 ? 0 : 1), z0 - 1, x1 + (side == 0 ? 1 : 0), z1, Roofs.Profile.straight(1.0));
			int rx0 = Math.max(0, x0 - 1);
			int rx1 = Math.min(w - 1, x1 + 1);
			Roofs.raster(d, pyr, top + 1, rx0, z0 - 1, rx1, z1, sheet, trim, Habits.Ridge.FULL);
			Rock.copper(d, rx0, top + 1, z0 - 1, rx1, d.maxHeight() - 1, z1, sheet, Rock.weathering(d));
			int fin = top + 1 + (wd + 1) / 2;
			int mx = (x0 + x1) / 2;
			int mz = (z0 + z1) / 2;
			if (fin < d.maxHeight() && d.isAbsent(mx, fin, mz)) {
				try (var s = d.stage(StageKind.ROOF)) {
					d.set(mx, fin, mz, g.lamp.defaultBlockState());
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- stalls

	/**
	 * A stall in a bay: {line x, z (first cell), into-court dx, dz, outer, second cell}. Two cells along the arcade line:
	 * counters (a barrel and a slab of the furniture's wood) under a cloth awning out over the court, wares on them, the
	 * merchant's stock against the outer wall, the merchant behind, the customer in front.
	 */
	private static void stall(Draft d, int[] st, int index, Glow g) {
		int dx = st[2];
		int dz = st[3];
		boolean alongZ = dx != 0;
		int[][] cells = alongZ ? new int[][]{{st[0], st[1]}, {st[0], st[5]}} : new int[][]{{st[0], st[1]}, {st[5], st[1]}};
		Direction toCourt = Dirs.of(dx, dz);
		Direction back = toCourt.getOpposite();
		Mat wood = d.mat(Role.FURNITURE);
		Mat cloth = d.mat(Role.CLOTH);
		String theme = switch (Math.floorMod(index + (int) (d.seed() & 7), 5)) {
			case 0 -> "food";
			case 1 -> "ore";
			case 2 -> "craft";
			case 3 -> "cloth";
			default -> "deep";
		};
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < 2; i++) {
				int x = cells[i][0];
				int z = cells[i][1];
				if (i == 0) {
					d.set(x, 0, z, States.facing(Blocks.BARREL, toCourt));
				} else {
					d.set(x, 0, z, wood.slab(SlabType.TOP));
				}
				wares(d, x, 1, z, theme, i, g);
			}
		}
		// the awning: cloth stairs sloping out over the customers' row, under the arch
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				int ax = c[0] + dx;
				int az = c[1] + dz;
				if (Rock.inPlot(d, ax, az) && d.isAbsent(ax, 3, az)) {
					d.set(ax, 3, az, cloth.stairsOrSlab(back, Half.BOTTOM));
				}
			}
		}
		int mx = cells[0][0] - dx;
		int mz = cells[0][1] - dz;
		int cxs = cells[1][0] + dx;
		int czs = cells[1][1] + dz;
		d.poi(PoiType.COUNTER, mx, 0, mz, toCourt);
		d.poi(PoiType.MARKET_STALL, cxs, 0, czs, back);
		// the counter's barrel holds the stall's stock: the merchant reaches it from behind
		d.poi(PoiType.STORAGE, mx, 0, mz, toCourt);
		// stock against the outer wall behind the merchant: the job site's barrel, a chest
		int ox = st[4];
		int[] stock = alongZ ? new int[]{ox + dx, cells[1][1]} : new int[]{cells[1][0], ox + dz};
		if (Math.abs((alongZ ? stock[0] : stock[1]) - (alongZ ? st[0] : st[1])) >= 2) {
			Furniture.workstation(d, JobType.MERCHANT, stock[0], 0, stock[1], toCourt);
		} else {
			d.poi(PoiType.JOB_SITE, mx, 0, mz, toCourt, JobType.MERCHANT);
		}
		// a lantern hung under the arch over the counter
		int lx = cells[0][0];
		int lz = cells[0][1];
		if (d.isEmpty(lx, 2, lz) || d.isAbsent(lx, 2, lz)) {
			if (d.get(lx, 3, lz) != null && !d.get(lx, 3, lz).isAir()) {
				Lighting.chandelier(d, lx, lz, 3, 2);
			}
		}
	}

	/** Wares on a counter by the stall's trade. */
	private static void wares(Draft d, int x, int y, int z, String theme, int i, Glow g) {
		double u = Noise.unit(d.seed() ^ 0x5A1E, x, y, z);
		BlockState b = switch (theme) {
			case "food" -> i == 0 ? Blocks.FLOWER_POT.defaultBlockState() : u < 0.5 ? Blocks.MELON.defaultBlockState() : Blocks.CAKE.defaultBlockState();
			case "ore" -> i == 0 ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : u < 0.5 ? Blocks.RAW_COPPER_BLOCK.defaultBlockState()
				: Blocks.IRON_BARS.defaultBlockState();
			case "craft" -> i == 0 ? States.lantern(Blocks.LANTERN, false) : Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)
				.setValue(BlockStateProperties.LIT, true);
			case "cloth" -> d.mat(Role.CLOTH).carpet().at(x, y, z);
			default -> i == 0 ? Glow.plantState(g.plants[0]).getBlock() instanceof net.minecraft.world.level.block.FlowerBlock
				? Blocks.FLOWER_POT.defaultBlockState() : Blocks.DECORATED_POT.defaultBlockState()
				: (u < 0.5 ? g.mineral : Blocks.DECORATED_POT).defaultBlockState();
		};
		if (b != null) {
			d.set(x, y, z, b);
		}
	}

	// ------------------------------------------------------------------------------------------------- the heart

	/**
	 * The lantern tree: a pillar of the dressing stone on a stepped base (a basin of water round it in a broad court),
	 * iron arms at its head and halfway up with lanterns hung from them, benches round the base.
	 */
	private static void lanternTree(Draft d, int x, int z, int top, boolean basin, Glow g) {
		Mat dress = Rock.dressing(d);
		Mat plinth = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.WALLS)) {
			if (basin) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dz == 0) {
							continue;
						}
						d.set(x + dx, -1, z + dz, Blocks.WATER.defaultBlockState());
						d.set(x + dx, -2, z + dz, plinth.full());
					}
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
							d.set(x + dx, 0, z + dz, dress.slab(SlabType.BOTTOM));
						}
					}
				}
			}
			for (int y = -1; y < top; y++) {
				d.set(x, y, z, y <= 0 ? plinth.full() : y == top - 1 ? Paint.of(Rock.carved(d).defaultBlockState()) : dress.full());
			}
			d.set(x, top, z, g.lamp.defaultBlockState());
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int level : new int[]{top - 1, Math.max(3, top - 4)}) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int ax = x + Dirs.dx(dir);
					int az = z + Dirs.dz(dir);
					d.set(ax, level, az, Blocks.IRON_BARS.defaultBlockState());
					int lx = x + Dirs.dx(dir) * 2;
					int lz = z + Dirs.dz(dir) * 2;
					d.set(lx, level, lz, Blocks.IRON_BARS.defaultBlockState());
					if (level - 1 >= 2) {
						d.set(lx, level - 1, lz, States.lantern(Blocks.LANTERN, true));
					}
				}
			}
		}
		if (!basin) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int bx = x + Dirs.dx(dir);
				int bz = z + Dirs.dz(dir);
				Rock.bench(d, bx, 0, bz, dir.getOpposite(), Dirs.right(dir), 1);
			}
		}
	}

	/**
	 * Festoons: chains strung across the court between the side wings' eaves every few rows, a lantern hung under every
	 * third link (clear of the lantern tree's arms).
	 */
	private static void festoons(Draft d, int cx0, int cx1, int cz1, int eave, int tx, int tz) {
		int y = eave;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z < cz1; z += 3) {
				if (Math.abs(z - tz) <= 2) {
					continue;
				}
				for (int x = cx0; x <= cx1; x++) {
					if (!d.isAbsent(x, y, z)) {
						continue;
					}
					d.set(x, y, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.X));
					if ((x - cx0) % 3 == 1 && d.isAbsent(x, y - 1, z)) {
						d.set(x, y - 1, z, States.lantern(Blocks.LANTERN, true));
					}
				}
			}
		}
	}
}
