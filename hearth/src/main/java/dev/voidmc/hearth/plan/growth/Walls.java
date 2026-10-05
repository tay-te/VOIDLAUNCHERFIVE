package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Router;
import dev.voidmc.hearth.plan.road.Specs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A town wall (or a village palisade): a ring round the built-up town at a margin, its line pushed out past anything
 * standing in the way and smoothed into an organic round; gatehouses where the main roads pass through, towers along
 * it every forty blocks or so and at its corners, and gaps where lanes cross. Farms stay outside, and a walled town
 * grows up inside its walls (see {@link Upgrades}) while its suburbs and fields spread out past the gates.
 */
public final class Walls {
	private static final int SECTORS = 48;

	private Walls() {
	}

	static boolean wanted(PlanContext ctx) {
		if (ctx.state.wallStage >= 2) {
			return false;
		}
		LayoutType layout = ctx.state.layout;
		if (line(ctx) != null) {
			// a walled town: a palisade on its line as a village, rebuilt in stone as a town
			ArchetypeInfo wall = ctx.catalogue.info(Archetypes.WALL);
			ArchetypeInfo pal = ctx.catalogue.info(Archetypes.PALISADE);
			boolean stone = wall != null && Catalogue.allowed(wall, ctx.culture, ctx.tier) && ctx.population >= 60;
			boolean palisade = ctx.state.wallStage == 0 && pal != null && Catalogue.allowed(pal, ctx.culture, ctx.tier) && ctx.population >= 28;
			return stone || palisade;
		}
		boolean walledCulture = Growth.isCulture(ctx, Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.DESERT, Culture.WISTERIA, Culture.MESA,
			Culture.MARITIME);
		boolean stone = ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.population >= 70
			&& (layout == LayoutType.WALLED || walledCulture && (layout == LayoutType.CROSSROADS || layout == LayoutType.HILLSIDE)
			&& ctx.hash(0, 0, 41) < 0.5);
		boolean palisade = ctx.state.wallStage == 0 && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal() && ctx.population >= 30
			&& Growth.isCulture(ctx, Culture.NORTHERN, Culture.STEPPE, Culture.TUNDRA) && (layout == LayoutType.RING || layout == LayoutType.CROSSROADS);
		if (stone) {
			ArchetypeInfo w = ctx.catalogue.info(Archetypes.WALL);
			return w != null && Catalogue.allowed(w, ctx.culture, ctx.tier);
		}
		if (palisade) {
			ArchetypeInfo w = ctx.catalogue.info(Archetypes.PALISADE);
			return w != null && Catalogue.allowed(w, ctx.culture, ctx.tier);
		}
		return false;
	}

	static boolean plan(PlanContext ctx) {
		RoadEdge line = line(ctx);
		if (line != null) {
			return planOnLine(ctx, line);
		}
		boolean stone = ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.catalogue.has(Archetypes.WALL);
		Identifier archetype = stone ? Archetypes.WALL : Archetypes.PALISADE;
		int width = stone ? 3 : 1;
		int height = stone ? 8 : 5;
		int hx = ctx.heartX();
		int hz = ctx.heartZ();
		// how far the town reaches in each direction (farms and the far outskirts stay outside)
		double[] reach = new double[SECTORS];
		double core = ctx.coreRadius();
		List<PlotSpec> specs = new ArrayList<>();
		for (PlotView p : ctx.settlement.plots()) {
			specs.add(p.spec());
		}
		for (QueuedProject q : ctx.state.queue) {
			specs.add(q.spec());
		}
		for (PlotSpec s : specs) {
			ArchetypeInfo info = ctx.info(s.archetype());
			if (info == null || info.category() == ArchetypeCategory.INFRASTRUCTURE || info.category() == ArchetypeCategory.FARM
				|| info.category() == ArchetypeCategory.DEFENSE || Archetypes.isLinear(s.archetype())) {
				continue;
			}
			for (int c = 0; c < 4; c++) {
				int x = s.origin().getX() + (c & 1) * (s.sizeX() - 1);
				int z = s.origin().getZ() + (c >> 1) * (s.sizeZ() - 1);
				double d = Math.hypot(x - hx, z - hz);
				if (d > core * 2.2) {
					continue;
				}
				int sec = sector(x - hx, z - hz);
				reach[sec] = Math.max(reach[sec], d);
			}
		}
		double[] r = new double[SECTORS];
		for (int i = 0; i < SECTORS; i++) {
			double m = 0;
			for (int k = -2; k <= 2; k++) {
				m = Math.max(m, reach[(i + k + SECTORS) % SECTORS]);
			}
			r[i] = Math.max(core * 0.9, m + 6);
		}
		// smooth into a round, then push each vertex out past anything solid on the line
		for (int pass = 0; pass < 2; pass++) {
			double[] s = new double[SECTORS];
			for (int i = 0; i < SECTORS; i++) {
				s[i] = Math.max(r[i], (r[(i + SECTORS - 1) % SECTORS] + 2 * r[i] + r[(i + 1) % SECTORS]) / 4);
			}
			r = s;
		}
		int[] vx = new int[SECTORS + 1];
		int[] vz = new int[SECTORS + 1];
		for (int i = 0; i < SECTORS; i++) {
			double a = (i + 0.5) * 2 * Math.PI / SECTORS;
			for (int tries = 0; tries < 12; tries++) {
				int x = hx + (int) Math.round(StrictMath.cos(a) * r[i]);
				int z = hz + (int) Math.round(StrictMath.sin(a) * r[i]);
				if (lineClear(ctx, x, z, width)) {
					break;
				}
				r[i] += 3;
			}
			vx[i] = hx + (int) Math.round(StrictMath.cos(a) * r[i]);
			vz[i] = hz + (int) Math.round(StrictMath.sin(a) * r[i]);
		}
		vx[SECTORS] = vx[0];
		vz[SECTORS] = vz[0];
		int[][] dense = Router.densify(vx, vz);
		int n = dense[0].length;
		// walk the ring: runs of clear line become wall plots; roads become gates or gaps
		boolean[] wallOk = new boolean[n];
		boolean[] road = new boolean[n];
		for (int i = 0; i < n; i++) {
			byte c = ctx.occ.get(dense[0][i], dense[1][i]);
			road[i] = Cell.isAccess(c) || c == Cell.PLANNED_ROAD;
			wallOk[i] = !road[i] && lineClear(ctx, dense[0][i], dense[1][i], width) && !ctx.ground.isWater(dense[0][i], dense[1][i]);
		}
		ArchetypeInfo tower = ctx.catalogue.resolve(Archetypes.WATCHTOWER, ctx.culture, ctx.tier);
		ArchetypeInfo gate = stone ? ctx.catalogue.resolve(Archetypes.GATEHOUSE, ctx.culture, ctx.tier) : null;
		// first the stretches, gates and towers as positions; then the gates and towers; then the stretches, which leave
		// the towers' and gatehouses' footprints to them (keep) and continue into each other (join_*)
		List<int[]> runs = new ArrayList<>();
		List<Integer> gateAt = new ArrayList<>();
		int i = 0;
		int sinceTower = 0;
		List<int[]> towers = new ArrayList<>();
		while (i < n) {
			if (!wallOk[i]) {
				if (road[i] && gate != null) {
					gateAt.add(i);
				}
				i++;
				continue;
			}
			int j = i;
			while (j < n && wallOk[j] && j - i < ctx.config.roadPlotLength()) {
				j++;
			}
			if (j - i >= 4) {
				runs.add(new int[]{i, j});
				sinceTower += j - i;
				if (tower != null && stone && sinceTower >= 36) {
					towers.add(new int[]{dense[0][j - 1], dense[1][j - 1]});
					sinceTower = 0;
				}
			}
			i = j;
		}
		List<int[]> keep = new ArrayList<>();
		for (int g : gateAt) {
			int[] rect = placeGate(ctx, gate, dense, g, height);
			if (rect != null) {
				keep.add(rect);
			}
		}
		for (int[] t : towers) {
			int[] rect = placeTower(ctx, tower, t[0], t[1], height);
			if (rect != null) {
				keep.add(rect);
			}
		}
		int segments = 0;
		for (int k = 0; k < runs.size(); k++) {
			int[] run = runs.get(k);
			int[] xs = new int[run[1] - run[0]];
			int[] ys = new int[xs.length];
			int[] zs = new int[xs.length];
			for (int m = run[0]; m < run[1]; m++) {
				xs[m - run[0]] = dense[0][m];
				zs[m - run[0]] = dense[1][m];
				ys[m - run[0]] = ctx.ground.ground(dense[0][m], dense[1][m]);
			}
			int[] pts = Polyline.fromDense(xs, ys, zs, 0, xs.length - 1).points();
			boolean joinStart = k > 0 && runs.get(k - 1)[1] == run[0];
			boolean joinEnd = k + 1 < runs.size() && runs.get(k + 1)[0] == run[1];
			PlotSpec spec = dressed(Specs.wall(ctx, archetype, pts, width, height), keep, joinStart, joinEnd, hx, hz);
			Roads.issue(ctx, spec, stone ? "defence: the town wall" : "defence: a palisade round the village");
			segments++;
		}
		if (segments == 0) {
			return false;
		}
		ctx.state.wallStage = stone ? 2 : 1;
		double mean = 0;
		for (double v : r) {
			mean += v;
		}
		ctx.state.wallRadius = (int) Math.round(mean / SECTORS);
		return true;
	}

	/** The wall line a walled town kept from its founding, or null. */
	static @Nullable RoadEdge line(PlanContext ctx) {
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role == RoadEdge.Role.WALL_LINE) {
				return e;
			}
		}
		return null;
	}

	/**
	 * Keeps a walled town's wall line from the founding: a closed ring round the heart at a radius a town of a hundred
	 * and fifty folk fills, wavering a little, drawn in where water or a drop makes a natural edge. The line shows as
	 * wall in the grid at once, so streets end at it and houses keep inside it; the main roads pass through where the
	 * gates will be.
	 */
	public static void reserveLine(PlanContext ctx, int heart) {
		var n = ctx.state.graph.node(heart);
		int points = 36;
		double base = 34 + 10 * ctx.random.nextDouble();
		int[] vx = new int[points + 1];
		int[] vz = new int[points + 1];
		double phase = ctx.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < points; i++) {
			double a = i * 2 * Math.PI / points;
			double r = base * (1 + 0.1 * StrictMath.sin(2 * a + phase) + 0.06 * StrictMath.sin(3 * a + phase * 1.7));
			int x = n.x() + (int) Math.round(StrictMath.cos(a) * r);
			int z = n.z() + (int) Math.round(StrictMath.sin(a) * r);
			for (int k = 0; k < 8 && (ctx.ground.isWater(x, z) || ctx.ground.slope(x, z, 2) > 1.2); k++) {
				r -= 3;
				x = n.x() + (int) Math.round(StrictMath.cos(a) * r);
				z = n.z() + (int) Math.round(StrictMath.sin(a) * r);
			}
			vx[i] = x;
			vz[i] = z;
		}
		vx[points] = vx[0];
		vz[points] = vz[0];
		int[][] dense = Router.densify(vx, vz);
		int[] ys = new int[dense[0].length];
		for (int i = 0; i < ys.length; i++) {
			ys[i] = ctx.ground.ground(dense[0][i], dense[1][i]);
		}
		Polyline poly = Polyline.fromDense(dense[0], ys, dense[1], 0, ys.length - 1);
		RoadEdge e = ctx.state.graph.addEdge(heart, heart, RoadEdge.Role.WALL_LINE, RoadKind.PATH, 3, poly, new int[0]);
		markLine(ctx.occ, e);
	}

	/** Shows a wall line in the grid, except where roads cross it (the gates). */
	public static void markLine(Occupancy occ, RoadEdge line) {
		Polyline.corridor(line.line().points(), line.width, (x, z) -> {
			byte c = occ.get(x, z);
			if (c == Cell.FREE || c == Cell.SETBACK) {
				occ.set(x, z, Cell.WALL);
			}
		});
	}

	/** Builds the wall on its line: a palisade first, then stone in its place, with gatehouses and towers. */
	private static boolean planOnLine(PlanContext ctx, RoadEdge line) {
		ArchetypeInfo wallInfo = ctx.catalogue.info(Archetypes.WALL);
		boolean stone = ctx.population >= 60 && wallInfo != null && Catalogue.allowed(wallInfo, ctx.culture, ctx.tier);
		if (stone && ctx.state.wallStage == 1) {
			// the palisade is rebuilt in stone, segment by segment, the gatehouses and towers planned first
			List<int[]> keep = gatesAndTowers(ctx, line, 8);
			int n = 0;
			for (PlotView p : ctx.settlement.plots()) {
				PlotSpec s = p.spec();
				if (!s.archetype().equals(Archetypes.PALISADE)) {
					continue;
				}
				int[] pts = s.params().getIntArray("points").orElse(null);
				if (pts == null) {
					continue;
				}
				PlotSpec wall = dressed(Specs.wall(ctx, Archetypes.WALL, pts, 3, 8), keep, false, false, ctx.heartX(), ctx.heartZ());
				CompoundTag params = wall.params();
				params.putInt("replaces", p.id());
				wall = new PlotSpec(wall.archetype(), wall.style(), wall.seed(), wall.origin(), wall.sizeX(), wall.sizeZ(), wall.facing(),
					wall.maxHeight(), wall.tier(), params);
				ctx.queue(wall, p.id(), "defence: the palisade is rebuilt as a stone wall");
				n++;
			}
			ctx.state.wallStage = 2;
			return n > 0;
		}
		Polyline.Dense d = line.dense();
		int n = d.length();
		Identifier archetype = stone ? Archetypes.WALL : Archetypes.PALISADE;
		int width = stone ? 3 : 1;
		int height = stone ? 8 : 5;
		// gatehouses and towers first, so the stretches can leave their footprints to them
		List<int[]> keep = stone ? gatesAndTowers(ctx, line, height) : List.of();
		List<int[]> runs = new ArrayList<>();
		int i = 0;
		while (i < n) {
			if (ctx.occ.get(d.xs()[i], d.zs()[i]) != Cell.WALL) {
				i++;
				continue;
			}
			int j = i;
			while (j < n && ctx.occ.get(d.xs()[j], d.zs()[j]) == Cell.WALL && j - i < ctx.config.roadPlotLength()) {
				j++;
			}
			if (j - i >= 3) {
				runs.add(new int[]{i, j});
			}
			i = j;
		}
		int segments = 0;
		for (int k = 0; k < runs.size(); k++) {
			int[] run = runs.get(k);
			int[] pts = Polyline.fromDense(d.xs(), d.ys(), d.zs(), run[0], run[1] - 1).points();
			boolean joinStart = k > 0 && runs.get(k - 1)[1] == run[0];
			boolean joinEnd = k + 1 < runs.size() && runs.get(k + 1)[0] == run[1];
			ctx.queue(dressed(Specs.wall(ctx, archetype, pts, width, height), keep, joinStart, joinEnd, ctx.heartX(), ctx.heartZ()), -1,
				stone ? "defence: the town wall" : "defence: a palisade on the town's wall line");
			segments++;
		}
		ctx.state.wallStage = stone ? 2 : 1;
		double sum = 0;
		for (int k = 0; k < n; k++) {
			sum += ctx.distanceToHeart(d.xs()[k], d.zs()[k]);
		}
		ctx.state.wallRadius = (int) Math.round(sum / Math.max(1, n));
		return segments > 0;
	}

	/**
	 * Gatehouses where main roads cross the wall line, towers along it about every forty blocks. Returns their
	 * footprints ({@code x0, z0, x1, z1}, inclusive) for the stretches' {@code keep}.
	 */
	private static List<int[]> gatesAndTowers(PlanContext ctx, RoadEdge line, int wallHeight) {
		List<int[]> keep = new ArrayList<>();
		ArchetypeInfo tower = ctx.catalogue.resolve(Archetypes.WATCHTOWER, ctx.culture, ctx.tier);
		ArchetypeInfo gate = ctx.catalogue.resolve(Archetypes.GATEHOUSE, ctx.culture, ctx.tier);
		Polyline.Dense d = line.dense();
		int[][] dense = {d.xs(), d.zs()};
		int since = 0;
		boolean wasRoad = false;
		for (int i = 0; i < d.length(); i++) {
			byte c = ctx.occ.get(d.xs()[i], d.zs()[i]);
			boolean road = Cell.isAccess(c) || c == Cell.PLANNED_ROAD;
			if (road && !wasRoad && gate != null) {
				int[] rect = placeGate(ctx, gate, dense, i, wallHeight);
				if (rect != null) {
					keep.add(rect);
				}
				since = 0;
			}
			wasRoad = road;
			since++;
			if (tower != null && since >= 40 && c == Cell.WALL) {
				int[] rect = placeTower(ctx, tower, d.xs()[i], d.zs()[i], wallHeight);
				if (rect != null) {
					keep.add(rect);
				}
				since = 0;
			}
		}
		return keep;
	}

	/**
	 * A wall stretch with what the landscape architect's wall reads (requests/arch-landscape.md §2-4): {@code keep}, the
	 * footprints of the towers and gatehouses on it (it builds nothing there; its walk runs into their flank doors),
	 * {@code join_start}/{@code join_end} where it runs straight on into the next stretch (no pier and stair at the cut),
	 * and {@code heart}, the town side.
	 */
	static PlotSpec dressed(PlotSpec wall, List<int[]> keep, boolean joinStart, boolean joinEnd, int hx, int hz) {
		CompoundTag params = wall.params();
		int x0 = wall.origin().getX();
		int z0 = wall.origin().getZ();
		int x1 = x0 + wall.sizeX() - 1;
		int z1 = z0 + wall.sizeZ() - 1;
		List<Integer> mine = new ArrayList<>();
		for (int[] r : keep) {
			if (r[0] <= x1 && r[2] >= x0 && r[1] <= z1 && r[3] >= z0) {
				for (int v : r) {
					mine.add(v);
				}
			}
		}
		if (!mine.isEmpty()) {
			params.putIntArray("keep", mine.stream().mapToInt(Integer::intValue).toArray());
		}
		if (joinStart) {
			params.putBoolean("join_start", true);
		}
		if (joinEnd) {
			params.putBoolean("join_end", true);
		}
		params.putIntArray("heart", new int[]{hx, hz});
		return new PlotSpec(wall.archetype(), wall.style(), wall.seed(), wall.origin(), wall.sizeX(), wall.sizeZ(), wall.facing(), wall.maxHeight(),
			wall.tier(), params);
	}

	private static int sector(int dx, int dz) {
		double a = StrictMath.atan2(dz, dx);
		if (a < 0) {
			a += 2 * Math.PI;
		}
		return Math.min(SECTORS - 1, (int) (a / (2 * Math.PI) * SECTORS));
	}

	private static boolean lineClear(PlanContext ctx, int x, int z, int width) {
		int h = width / 2 + 1;
		for (int dz = -h; dz <= h; dz++) {
			for (int dx = -h; dx <= h; dx++) {
				byte c = ctx.occ.get(x + dx, z + dz);
				if (Cell.isSolid(c) || c == Cell.WALL) {
					return false;
				}
			}
		}
		return true;
	}

	/** A gatehouse across a main road where the ring crosses it (the road runs through its passage). */
	private static int @Nullable [] placeGate(PlanContext ctx, ArchetypeInfo gate, int[][] dense, int i, int wallHeight) {
		int x = dense[0][i];
		int z = dense[1][i];
		int[] at = Roads.edgeAt(ctx, x, z);
		if (at == null) {
			return null;
		}
		var e = ctx.state.graph.edge(at[0]);
		if (e.role != RoadEdge.Role.ARM) {
			return null;
		}
		Polyline.Dense d = e.dense();
		int a = Math.max(0, at[1] - 2);
		int b = Math.min(d.length() - 1, at[1] + 2);
		int tx = d.xs()[b] - d.xs()[a];
		int tz = d.zs()[b] - d.zs()[a];
		// facing outward along the road, away from the heart
		int ox = d.xs()[at[1]] - ctx.heartX();
		int oz = d.zs()[at[1]] - ctx.heartZ();
		Direction facing = Math.abs(tx) >= Math.abs(tz) ? (ox >= 0 ? Direction.EAST : Direction.WEST) : (oz >= 0 ? Direction.SOUTH : Direction.NORTH);
		int front = Math.max(gate.minFrontage(), Math.min(gate.maxFrontage(), e.width + 4));
		int depth = gate.minDepth();
		int cx = d.xs()[at[1]];
		int cz = d.zs()[at[1]];
		int sizeX = facing.getAxis() == Direction.Axis.Z ? front : depth;
		int sizeZ = facing.getAxis() == Direction.Axis.Z ? depth : front;
		int x0 = cx - sizeX / 2;
		int z0 = cz - sizeZ / 2;
		for (int zz = z0; zz < z0 + sizeZ; zz++) {
			for (int xx = x0; xx < x0 + sizeX; xx++) {
				byte c = ctx.occ.get(xx, zz);
				// a gatehouse straddles the wall line beside the road: WALL cells are its intended overlap (PLANNING.md §4;
				// requests/arch-landscape.md §1: without this no gate was ever placed in a walled layout)
				if (!(Cell.isAccess(c) || Cell.isBuildable(c) || c == Cell.PLANNED_ROAD || c == Cell.WALL)) {
					return null;
				}
			}
		}
		CompoundTag params = new CompoundTag();
		params.putInt("wall_height", wallHeight);
		params.putInt("passage_width", e.width);
		params.putBoolean("over_road", true);
		params.putInt("street_y", d.ys()[at[1]]);
		PlotSpec spec = new PlotSpec(gate.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, d.ys()[at[1]], z0), sizeX, sizeZ, facing,
			gate.maxHeight(), Build.finish(ctx, gate, 1, null), params);
		ctx.queue(spec, -1, "defence: a gatehouse where the " + Expand.describe(e) + " passes the wall");
		// mark only the parts beside the road: the road keeps running through
		for (int zz = z0; zz < z0 + sizeZ; zz++) {
			for (int xx = x0; xx < x0 + sizeX; xx++) {
				if (!Cell.isAccess(ctx.occ.get(xx, zz))) {
					ctx.occ.mark(xx, zz, Cell.BUILDING);
				}
			}
		}
		return new int[]{x0, z0, x0 + sizeX - 1, z0 + sizeZ - 1};
	}

	private static int @Nullable [] placeTower(PlanContext ctx, ArchetypeInfo tower, int x, int z, int wallHeight) {
		int s = tower.minFrontage();
		int x0 = x - s / 2;
		int z0 = z - s / 2;
		for (int zz = z0; zz < z0 + s; zz++) {
			for (int xx = x0; xx < x0 + s; xx++) {
				byte c = ctx.occ.get(xx, zz);
				if (!(c == Cell.WALL || Cell.isBuildable(c))) {
					return null;
				}
			}
		}
		CompoundTag params = new CompoundTag();
		params.putBoolean("on_wall", true);
		params.putInt("wall_height", wallHeight);
		int y = ctx.ground.ground(x, z);
		Direction facing = Math.abs(x - ctx.heartX()) >= Math.abs(z - ctx.heartZ()) ? (x > ctx.heartX() ? Direction.WEST : Direction.EAST)
			: (z > ctx.heartZ() ? Direction.NORTH : Direction.SOUTH);
		PlotSpec spec = new PlotSpec(tower.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, y, z0), s, s, facing, tower.maxHeight(),
			Build.finish(ctx, tower, 2, null), params);
		Roads.issue(ctx, spec, "defence: a tower on the wall");
		return new int[]{x0, z0, x0 + s - 1, z0 + s - 1};
	}
}
