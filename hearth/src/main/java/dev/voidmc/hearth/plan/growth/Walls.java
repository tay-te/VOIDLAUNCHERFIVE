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
import dev.voidmc.hearth.plan.model.QueuedProject;
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
		int segments = 0;
		int i = 0;
		int sinceTower = 0;
		List<int[]> towers = new ArrayList<>();
		while (i < n) {
			if (!wallOk[i]) {
				if (road[i] && gate != null) {
					placeGate(ctx, gate, dense, i);
				}
				i++;
				continue;
			}
			int j = i;
			while (j < n && wallOk[j] && j - i < ctx.config.roadPlotLength()) {
				j++;
			}
			if (j - i >= 4) {
				int[] xs = new int[j - i];
				int[] ys = new int[j - i];
				int[] zs = new int[j - i];
				for (int k = i; k < j; k++) {
					xs[k - i] = dense[0][k];
					zs[k - i] = dense[1][k];
					ys[k - i] = ctx.ground.ground(dense[0][k], dense[1][k]);
				}
				int[] pts = Polyline.fromDense(xs, ys, zs, 0, xs.length - 1).points();
				Roads.issue(ctx, Specs.wall(ctx, archetype, pts, width, height), stone ? "defence: the town wall" : "defence: a palisade round the village");
				segments++;
				sinceTower += j - i;
				if (tower != null && stone && sinceTower >= 36) {
					towers.add(new int[]{dense[0][j - 1], dense[1][j - 1]});
					sinceTower = 0;
				}
			}
			i = j;
		}
		for (int[] t : towers) {
			placeTower(ctx, tower, t[0], t[1]);
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
	private static void placeGate(PlanContext ctx, ArchetypeInfo gate, int[][] dense, int i) {
		int x = dense[0][i];
		int z = dense[1][i];
		int[] at = Roads.edgeAt(ctx, x, z);
		if (at == null) {
			return;
		}
		var e = ctx.state.graph.edge(at[0]);
		if (e.role != dev.voidmc.hearth.plan.model.RoadEdge.Role.ARM) {
			return;
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
				if (!(Cell.isAccess(c) || Cell.isBuildable(c) || c == Cell.PLANNED_ROAD)) {
					return;
				}
			}
		}
		CompoundTag params = new CompoundTag();
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
	}

	private static void placeTower(PlanContext ctx, ArchetypeInfo tower, int x, int z) {
		int s = tower.minFrontage();
		int x0 = x - s / 2;
		int z0 = z - s / 2;
		for (int zz = z0; zz < z0 + s; zz++) {
			for (int xx = x0; xx < x0 + s; xx++) {
				byte c = ctx.occ.get(xx, zz);
				if (!(c == Cell.WALL || Cell.isBuildable(c))) {
					return;
				}
			}
		}
		CompoundTag params = new CompoundTag();
		params.putBoolean("on_wall", true);
		int y = ctx.ground.ground(x, z);
		Direction facing = Math.abs(x - ctx.heartX()) >= Math.abs(z - ctx.heartZ()) ? (x > ctx.heartX() ? Direction.WEST : Direction.EAST)
			: (z > ctx.heartZ() ? Direction.NORTH : Direction.SOUTH);
		PlotSpec spec = new PlotSpec(tower.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, y, z0), s, s, facing, tower.maxHeight(),
			Build.finish(ctx, tower, 2, null), params);
		Roads.issue(ctx, spec, "defence: a tower on the wall");
	}
}
