package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Router;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Grows the road network when no frontage is left where a building wants to go (PLANNING.md, "Roads first"):
 * builds the next stretch of a main road outward, branches a lane off a street at the next good spacing (straight on
 * the level, along the contour on a slope, as a stair street straight up it), runs a track out into the farmland, or
 * routes an approach up to a rise. Each layout prefers its own moves: a linear town lengthens its main street, a
 * walled town fills its blocks with lanes and alleys, a hill town steps up in contour streets joined by stairs.
 */
public final class Expand {
	private Expand() {
	}

	/** Grows the network toward where a placement wants to go; true if a road was queued. */
	public static boolean toward(PlanContext ctx, Parcels.Placement placement) {
		if (!ctx.roadsMayGrow()) {
			return false;
		}
		Site site = ctx.state.site;
		switch (placement) {
			case RISE -> {
				if (site != null && approachTo(ctx, site.riseX(), site.riseZ(), "approach: the way up to the rise")) {
					return true;
				}
			}
			case SUMMIT -> {
				if (site != null && ctx.distanceToHeart(site.summitX(), site.summitZ()) < ctx.coreRadius() * 2.5 + 40
					&& approachTo(ctx, site.summitX(), site.summitZ(), "approach: the way up to the summit")) {
					return true;
				}
			}
			case WATER -> {
				if (site != null && site.hasWater() && waterLane(ctx)) {
					return true;
				}
			}
			case ON_WATER -> {
				return boardwalk(ctx);
			}
			case EDGE, FARMSTEAD -> {
				return outward(ctx, placement == Parcels.Placement.FARMSTEAD);
			}
			case GATEWAY, HOVELS, CROFTS -> {
				// out along the main roads to where the town meets the country
				return outward(ctx, false) || town(ctx);
			}
			case QUIET -> {
				return quiet(ctx);
			}
			default -> {
			}
		}
		return town(ctx);
	}

	/**
	 * Grows toward a building's siting: a cart track out to its anchor when no road comes near it (a sawmill by a logging
	 * camp across the valley), else the town's own streets.
	 */
	public static boolean toward(PlanContext ctx, dev.voidmc.hearth.plan.growth.Siting siting) {
		if (nearestAccess(ctx, siting.x(), siting.z(), 24) > 20 && ctx.distanceToHeart(siting.x(), siting.z()) > ctx.coreRadius()) {
			int[] at = freeNear(ctx, siting.x(), siting.z());
			if (at != null && Roads.haul(ctx, at[0], at[1], RoadEdge.Role.HAUL, RoadKind.PATH, 3, false, "road: a cart track out to the works") != null) {
				return true;
			}
		}
		return ctx.distanceToHeart(siting.x(), siting.z()) > Parcels.edgeRing(ctx) ? outward(ctx, false) || town(ctx) : town(ctx);
	}

	/** Blocks to the nearest road, bridge or plaza column from (x, z), searched in rings out to {@code max} ({@code max + 1} if none). */
	static int nearestAccess(PlanContext ctx, int x, int z, int max) {
		for (int r = 0; r <= max; r += 2) {
			for (int k = -r; k <= r; k += 2) {
				if (Cell.isAccess(ctx.occ.get(x + k, z - r)) || Cell.isAccess(ctx.occ.get(x + k, z + r)) || Cell.isAccess(ctx.occ.get(x - r, z + k))
					|| Cell.isAccess(ctx.occ.get(x + r, z + k))) {
					return r;
				}
			}
		}
		return max + 1;
	}

	/** A free, dry column at or near (x, z) a road can end on, or null. */
	static int @Nullable [] freeNear(PlanContext ctx, int x, int z) {
		for (int r = 0; r <= 12; r += 3) {
			for (int k = 0; k < 8; k++) {
				int px = x + Roads.DX[k] * r;
				int pz = z + Roads.DZ[k] * r;
				if (Cell.isBuildable(ctx.occ.get(px, pz)) && !ctx.ground.isWater(px, pz)) {
					return new int[]{px, pz};
				}
				if (r == 0) {
					break;
				}
			}
		}
		return null;
	}

	/**
	 * Quiet ground outside the town for a monastery or convent: the highest open spot a little way beyond the edge,
	 * apart from everything else, reached by a lane of its own.
	 */
	static boolean quiet(PlanContext ctx) {
		if (!ctx.roadsMayGrow()) {
			return false;
		}
		double ring = Parcels.edgeRing(ctx);
		int[] best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int r = (int) ring + 36; r <= ring + 84; r += 12) {
			for (int k = 0; k < 24; k++) {
				double a = k * Math.PI / 12 + r * 0.01;
				int x = ctx.heartX() + (int) Math.round(StrictMath.cos(a) * r);
				int z = ctx.heartZ() + (int) Math.round(StrictMath.sin(a) * r);
				if (ctx.ground.isWater(x, z) || !Cell.isBuildable(ctx.occ.get(x, z))
					|| dev.voidmc.hearth.plan.resource.Extraction.accessNear(ctx, x, z, 18)) {
					// served already (a lane runs there): the frontage search finds it; a new lane would only run beside it
					continue;
				}
				int[] range = ctx.ground.range(x - 10, z - 10, x + 10, z + 10, 4);
				if (range[1] - range[0] > 6) {
					continue;
				}
				double score = (ctx.ground.ground(x, z) - ctx.state.heartY) * 1.2 - r * 0.05 - (range[1] - range[0]) + ctx.hash(x, z, 211) * 4;
				if (score > bestScore) {
					bestScore = score;
					best = new int[]{x, z};
				}
			}
		}
		return best != null && Roads.haul(ctx, best[0], best[1], RoadEdge.Role.APPROACH, RoadKind.LANE, 3, false,
			"road: a lane out to quiet ground beyond the town") != null;
	}

	/**
	 * Grows the town itself. A compact town fills in before it spreads: a lane branching off a street near the heart
	 * comes first while there is room for one within the core, the main roads are lengthened while their ends are still
	 * near, and only then does the town reach out along them. A linear town lengthens its main street first.
	 */
	public static boolean town(PlanContext ctx) {
		if (!ctx.roadsMayGrow()) {
			return false;
		}
		LayoutType layout = ctx.state.layout;
		double core = ctx.coreRadius();
		// the best few places for a lane, not only the best: a mouth whose lane will not walk (it runs into a plot a few
		// blocks on) must not stop every other from being tried, call after call (requests/w5-tour.md §1)
		List<Branch> fresh = new ArrayList<>();
		List<Branch> branches = branches(ctx, false, 4, fresh);
		Branch branch = branches.isEmpty() ? null : branches.get(0);
		RoadEdge arm = shortestArm(ctx);
		double armDist = arm == null ? Double.MAX_VALUE : armFrontier(ctx, arm);
		double laneReach = switch (layout) {
			case LINEAR -> 0.6;
			case WALLED, HILLSIDE, CLIFF -> 1.5;
			// a farmsteads settlement that has nucleated fills its village in like a crossroads town
			case FARMSTEADS -> Grid.nucleated(ctx) ? 1.2 : 0.8;
			default -> 1.2;
		};
		double armReach = switch (layout) {
			case LINEAR -> 2.6;
			case WALLED -> 1.0;
			default -> 1.4;
		};
		// a lane planned ahead (its mouth kept open) is built first, nearest the heart first
		RoadEdge planned = Grid.nearestPlanned(ctx, core * laneReach + 12);
		if (planned != null && buildLane(ctx, planned)) {
			return true;
		}
		for (Branch b : branches) {
			if (ctx.pastDeadline()) {
				// a live call out of time: the walks wait for the next call (nothing is remembered of a walk not tried)
				return false;
			}
			if (b.distance < core * laneReach && walkBranch(ctx, b)) {
				return true;
			}
		}
		// a dead-end lane near the heart runs on into open ground
		if (extendLane(ctx, core * laneReach + 16)) {
			return true;
		}
		if (arm != null && armDist < core * armReach && extendArm(ctx, arm, "road: the main street grows outward")) {
			return true;
		}
		for (Branch b : branches) {
			if (ctx.pastDeadline()) {
				return false;
			}
			if ((arm == null || b.distance <= armDist) && walkBranch(ctx, b)) {
				return true;
			}
		}
		planned = Grid.nearestPlanned(ctx, Double.MAX_VALUE);
		if (planned != null && (arm == null || ctx.distanceToHeart(planned.dense().xs()[0], planned.dense().zs()[0]) < armDist)
			&& buildLane(ctx, planned)) {
			return true;
		}
		if (arm != null && extendArm(ctx, arm, "road: the main street grows outward")) {
			return true;
		}
		if (extendLane(ctx, Double.MAX_VALUE)) {
			return true;
		}
		// the town has filled the streets it was given: it grows a new quarter beyond them — a lane off a street out past
		// the built-up town, else the main road runs on into the country and the houses follow it
		for (Branch b : branches(ctx, true, 4)) {
			if (ctx.pastDeadline()) {
				return false;
			}
			if (walkBranch(ctx, b)) {
				return true;
			}
		}
		if (ctx.pastDeadline()) {
			// (an arm's end that will not run on is closed for good: not for want of time)
			return false;
		}
		if (continueArm(ctx)) {
			return true;
		}
		// still nothing: the mouths no call has tried yet, beyond each street's best (a street whose best mouth runs into a
		// plot a few blocks on has others; a walled steppe town stalled at 312 souls with open land all round it)
		// (every third call: a town with nothing else to grow does not pay for these walks on every call)
		int tries = ctx.state.calls % 3 == 0 ? 0 : 3;
		for (Branch b : fresh) {
			if (tries >= 3 || ctx.pastDeadline()) {
				break;
			}
			if (branches.contains(b)) {
				// tried above
				continue;
			}
			if (walkBranch(ctx, b)) {
				return true;
			}
			tries++;
		}
		Growth.why("no road could grow (" + (branch == null ? "no lane mouth" : "a lane mouth at " + (int) branch.distance + " would not walk") + ", "
			+ (arm == null ? "the main streets built out" : "the main street would not run on") + ", no dead end to run on)");
		return false;
	}

	/**
	 * Lengthens the dead-end lane (or stub) nearest the heart: walks on from its end, the way it was heading, into open
	 * ground, and builds the new stretch as a lane continuing it.
	 */
	static boolean extendLane(PlanContext ctx, double maxDistance) {
		RoadEdge best = null;
		// lanes run on within the planned town; beyond it the main roads and tracks reach out instead
		double bestD = Math.min(maxDistance, Math.max(Grid.reach(ctx) * 1.5, Parcels.edgeRing(ctx)));
		for (RoadEdge e : ctx.state.graph.edges()) {
			if ((e.role != RoadEdge.Role.LANE && e.role != RoadEdge.Role.STAIR) || !e.fullyBuilt() || e.length() < 6 || e.built < e.length()) {
				// (a lane given up at its mouth or cut short has no open end to run on from)
				continue;
			}
			if (ctx.state.graph.node(e.to).kind() != RoadGraph.NodeKind.END) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int n = d.length();
			double dist = ctx.distanceToHeart(d.xs()[n - 1], d.zs()[n - 1]) + ctx.hash(d.xs()[n - 1], d.zs()[n - 1], 71) * 3;
			if (dist >= bestD) {
				continue;
			}
			int heading = Roads.heading(d.xs()[n - 1] - d.xs()[Math.max(0, n - 5)], d.zs()[n - 1] - d.zs()[Math.max(0, n - 5)]);
			if (probe(ctx, d.xs()[n - 1], d.zs()[n - 1], heading, 1, 10) < 10) {
				continue;
			}
			bestD = dist;
			best = e;
		}
		if (best == null) {
			return false;
		}
		Polyline.Dense d = best.dense();
		int n = d.length();
		int heading = Roads.heading(d.xs()[n - 1] - d.xs()[Math.max(0, n - 5)], d.zs()[n - 1] - d.zs()[Math.max(0, n - 5)]);
		boolean[] joined = new boolean[1];
		int maxLen = (int) Math.round(Grid.laneLength(ctx) * (0.7 + 0.4 * ctx.hash(d.xs()[n - 1], d.zs()[n - 1], 73)));
		int[][] path = Roads.walk(ctx, d.xs()[n - 1], d.zs()[n - 1], heading, 1, maxLen, Roads.Mode.STRAIGHT, 10, 1, joined);
		RoadEdge next = path[0].length < 10 ? null : Roads.plan(ctx, best.to, path[0], path[1], best.role, best.kind, best.width, 8);
		// either way the end is no longer a dead end to try again
		markClosed(ctx, best);
		if (next == null) {
			return false;
		}
		next.parent = best.id;
		next.parentIndex = n - 1;
		Roads.build(ctx, next, next.length(), "road: the " + best.kind.getSerializedName() + " runs on");
		Grid.planAhead(ctx, next, 0, next.length());
		return true;
	}

	private static void markClosed(PlanContext ctx, RoadEdge e) {
		RoadGraph g = ctx.state.graph;
		RoadGraph.Node end = g.node(e.to);
		e.to = g.addNode(end.x(), end.y(), end.z(), RoadGraph.NodeKind.JUNCTION).id();
	}

	/**
	 * When every main road is built to its planned end, the one whose end is nearest the heart runs on: a new arm
	 * routed onward the way it was heading, so the town's roads keep reaching out to new farmland.
	 */
	static boolean continueArm(PlanContext ctx) {
		if (ctx.state.layout.expansive) {
			// an expansive settlement reaches out along its tracks, not by lengthening its village street
			return false;
		}
		RoadEdge best = null;
		// only a road that ends about where the town's edge is runs on, and not far: farmland within reach, not the world
		double bestD = Parcels.edgeRing(ctx) + 40;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if ((e.role != RoadEdge.Role.ARM && e.role != RoadEdge.Role.TRACK) || !e.fullyBuilt()
				|| ctx.state.graph.node(e.to).kind() != RoadGraph.NodeKind.END) {
				continue;
			}
			Polyline.Dense d = e.dense();
			double dist = ctx.distanceToHeart(d.xs()[d.length() - 1], d.zs()[d.length() - 1]);
			if (dist < bestD) {
				bestD = dist;
				best = e;
			}
		}
		if (best == null) {
			return false;
		}
		Polyline.Dense d = best.dense();
		int n = d.length();
		double angle = StrictMath.atan2(d.zs()[n - 1] - d.zs()[Math.max(0, n - 12)], d.xs()[n - 1] - d.xs()[Math.max(0, n - 12)]);
		int tx = d.xs()[n - 1] + (int) Math.round(StrictMath.cos(angle) * 64);
		int tz = d.zs()[n - 1] + (int) Math.round(StrictMath.sin(angle) * 64);
		Router.Params params = new Router.Params(4, 40, ctx.config.roadGrade() * 0.85, ctx.config.roadGrade() * 1.1, 9, 4, false, 400_000, 0.5);
		int[][] path = Router.route(ctx.ground, ctx.occ, d.xs()[n - 1], d.zs()[n - 1], tx, tz, params);
		markClosed(ctx, best);
		if (path == null) {
			return false;
		}
		RoadEdge next = Roads.plan(ctx, best.to, path[0], path[1], best.role, best.kind, best.width, 16);
		if (next == null) {
			return false;
		}
		next.parent = best.id;
		next.parentIndex = n - 1;
		return extendArm(ctx, next, "road: the road runs on beyond the town");
	}

	/** Builds a planned lane whole, then plans the lanes that will leave it in turn. */
	static boolean buildLane(PlanContext ctx, RoadEdge lane) {
		RoadEdge parent = lane.parent >= 0 ? ctx.state.graph.edge(lane.parent) : null;
		Roads.build(ctx, lane, lane.length(), "road: a new " + lane.kind.getSerializedName() + (parent == null ? "" : " off the " + describe(parent)));
		if (lane.built <= 0) {
			// blocked at its mouth since it was planned (a plot stands across it now): it is given up, or it would be "built"
			// on every call with nothing to show and the town would plan nothing else (a city of 447, a hillside town of 200)
			lane.limit = 0;
			return false;
		}
		Grid.planAhead(ctx, lane, 0, lane.length());
		Beauty.cornerLamp(ctx, lane);
		return true;
	}

	/** Builds the next stretch of an arm (or ring) planned at founding. */
	public static boolean extendArm(PlanContext ctx, RoadEdge e, String reason) {
		int step = e.role == RoadEdge.Role.TRACK ? 48 : e.role == RoadEdge.Role.RING ? 40 : 28;
		int before = e.built;
		Roads.build(ctx, e, e.built + step, reason);
		Grid.planAhead(ctx, e, before, e.built);
		return e.built > before;
	}

	/** The unfinished arm (or ring) whose built end is nearest the heart. */
	static @Nullable RoadEdge shortestArm(PlanContext ctx) {
		RoadEdge best = null;
		double bestD = Double.MAX_VALUE;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if ((e.role != RoadEdge.Role.ARM && e.role != RoadEdge.Role.RING && e.role != RoadEdge.Role.SWITCHBACK) || e.fullyBuilt()) {
				continue;
			}
			double d = armFrontier(ctx, e);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static double armFrontier(PlanContext ctx, RoadEdge e) {
		Polyline.Dense d = e.dense();
		int i = Math.min(d.length() - 1, Math.max(0, e.built));
		double dist = ctx.distanceToHeart(d.xs()[i], d.zs()[i]);
		return e.role == RoadEdge.Role.RING ? dist * 0.9 : dist;
	}

	/** A place to branch a lane: an edge, an index on it and a heading. */
	record Branch(RoadEdge edge, int index, int heading, double distance, double score, boolean contour) {
	}

	/** The best place to branch a new lane, near the heart (or, {@code outward}, beyond the built-up town). */
	static @Nullable Branch bestBranch(PlanContext ctx, boolean outward) {
		List<Branch> b = branches(ctx, outward, 1);
		return b.isEmpty() ? null : b.get(0);
	}

	/** The best {@code max} places to branch a new lane, best first, at most one per street (see {@link #bestBranch}). */
	static List<Branch> branches(PlanContext ctx, boolean outward, int max) {
		return branches(ctx, outward, max, null);
	}

	/**
	 * As {@link #branches(PlanContext, boolean, int)}; {@code fresh}, if given, also gets each street's best mouth among
	 * those no call has tried and seen fail (best first, at most {@code max}), from the same pass.
	 */
	static List<Branch> branches(PlanContext ctx, boolean outward, int max, @Nullable List<Branch> fresh) {
		java.util.Map<Integer, Branch> perEdge = new java.util.HashMap<>();
		java.util.Map<Integer, Branch> untried = new java.util.HashMap<>();
		Probes probes = Probes.of(ctx);
		double core = ctx.coreRadius();
		Ground g = ctx.ground;
		LayoutType layout = ctx.state.layout;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (!e.emits() || e.built < 8) {
				continue;
			}
			switch (e.role) {
				case PATH, APPROACH -> {
					continue;
				}
				case BOARDWALK -> {
					continue;
				}
				case TRACK, HAUL -> {
					if (!outward) {
						continue;
					}
				}
				default -> {
				}
			}
			int spacing = e.role == RoadEdge.Role.STAIR ? Grid.stairSpacing(e) : Grid.laneSpacing(ctx);
			if (outward) {
				spacing = Math.max(spacing, 30);
			}
			double outwardFrom = Math.max(core * 1.4, ctx.extent() + 16);
			EdgeProbes memo = probes.of(ctx, e);
			Polyline.Dense d = e.dense();
			int lim = Math.min(e.built, d.length());
			int fromStart = Math.max(10, spacing / 2);
			for (int i = fromStart; i < lim - 4; i += 3) {
				if (e.junctionClearance(i) < spacing || e.length() - 1 - i < 4) {
					continue;
				}
				int tx = d.xs()[Math.min(lim - 1, i + 2)] - d.xs()[Math.max(0, i - 2)];
				int tz = d.zs()[Math.min(lim - 1, i + 2)] - d.zs()[Math.max(0, i - 2)];
				if (tx == 0 && tz == 0) {
					continue;
				}
				for (int side = -1; side <= 1; side += 2) {
					double nx = side < 0 ? tz : -tz;
					double nz = side < 0 ? -tx : tx;
					// square off: a lane leaves a near-straight street at right angles
					int heading = Math.abs(tx) == 0 || Math.abs(tz) == 0 || Math.abs(Math.abs(tx) - Math.abs(tz)) > 1
						? Roads.cardinal(nx, nz) : Roads.heading(nx, nz);
					int probe = memo.probe(ctx, i, side, d.xs()[i], d.zs()[i], heading, e.width / 2 + 1);
					if (probe < 10) {
						continue;
					}
					int mx = d.xs()[i] + Roads.DX[heading] * 8;
					int mz = d.zs()[i] + Roads.DZ[heading] * 8;
					double dist = ctx.distanceToHeart(mx, mz);
					if (outward && dist < outwardFrom) {
						continue;
					}
					double grade = Math.abs(g.surface(mx, mz) - g.surface(d.xs()[i], d.zs()[i])) / 8.0;
					boolean contour = Grid.terraces(ctx) && grade < ctx.config.roadGrade() && ctx.state.site != null && ctx.state.site.slope() > 0.12;
					double score = outward ? -Math.abs(dist - outwardFrom) : -dist;
					score -= grade > ctx.config.stairGrade() ? 1000 : grade > ctx.config.roadGrade() ? (Grid.terraces(ctx) ? 4 : 25) : 0;
					if (Grid.terraces(ctx) && e.role == RoadEdge.Role.STAIR && grade < ctx.config.roadGrade()) {
						score += 12;
					}
					if (layout == LayoutType.WALLED && ctx.state.wallRadius > 0 && dist > ctx.state.wallRadius - 6) {
						score -= 40;
					}
					score += ctx.hash(d.xs()[i], d.zs()[i], 11) * 6;
					Branch best = perEdge.get(e.id);
					if (best == null || score > best.score) {
						perEdge.put(e.id, new Branch(e, i, heading, dist, score, contour));
					}
					if (fresh != null && !hasFailed(ctx, e, i, heading)) {
						Branch u = untried.get(e.id);
						if (u == null || score > u.score) {
							untried.put(e.id, new Branch(e, i, heading, dist, score, contour));
						}
					}
				}
			}
		}
		if (fresh != null) {
			fresh.addAll(best(untried, max));
		}
		return best(perEdge, max);
	}

	private static List<Branch> best(java.util.Map<Integer, Branch> perEdge, int max) {
		List<Branch> out = new ArrayList<>(perEdge.values());
		out.sort(java.util.Comparator.comparingDouble((Branch b) -> -b.score).thenComparingInt(b -> b.edge.id));
		return out.size() > max ? out.subList(0, max) : out;
	}

	/**
	 * The lane mouths' probes ({@link #probe} of twelve, at each index and side of each street), kept in the plan state
	 * between calls: a street's are probed again only once the grid changed in a tile one of them read, so a city's
	 * search for a lane mouth reads only the streets where it grew.
	 */
	public static final class Probes {
		final dev.voidmc.hearth.plan.grid.Occupancy occ;
		final java.util.IdentityHashMap<RoadEdge, EdgeProbes> edges = new java.util.IdentityHashMap<>();

		private Probes(dev.voidmc.hearth.plan.grid.Occupancy occ) {
			this.occ = occ;
		}

		static Probes of(PlanContext ctx) {
			Probes p = ctx.state.laneProbes;
			if (p == null || p.occ != ctx.occ) {
				p = new Probes(ctx.occ);
				ctx.state.laneProbes = p;
			}
			return p;
		}

		EdgeProbes of(PlanContext ctx, RoadEdge e) {
			EdgeProbes m = this.edges.get(e);
			if (m == null || !m.valid(ctx)) {
				m = new EdgeProbes(ctx, e);
				this.edges.put(e, m);
			}
			return m;
		}
	}

	/** One street's lane-mouth probes (see {@link Probes}). */
	static final class EdgeProbes {
		/** The grid's version when the first was probed: a tile read that changed since makes them all stale. */
		private final long version;
		private final it.unimi.dsi.fastutil.longs.LongOpenHashSet tiles = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		private long[] checked = new long[0];
		/** Per index, the left and the right side's probe, or -1 before it is probed. */
		private final int[] values;

		EdgeProbes(PlanContext ctx, RoadEdge e) {
			this.version = ctx.occ.version();
			this.values = new int[e.length() * 2];
			java.util.Arrays.fill(this.values, -1);
		}

		boolean valid(PlanContext ctx) {
			if (this.checked.length != this.tiles.size()) {
				this.checked = this.tiles.toLongArray();
			}
			for (long t : this.checked) {
				if (ctx.occ.changedSince(t, this.version)) {
					return false;
				}
			}
			return true;
		}

		int probe(PlanContext ctx, int i, int side, int x, int z, int heading, int skip) {
			int slot = i * 2 + (side < 0 ? 0 : 1);
			int v = this.values[slot];
			if (v < 0) {
				v = Expand.probe(ctx, x, z, heading, skip, 12, this.tiles);
				this.values[slot] = v;
			}
			return v;
		}
	}

	/** Free columns ahead of a road column along a heading, after skipping the road's own corridor. */
	static int probe(PlanContext ctx, int x, int z, int heading, int skip, int max) {
		return probe(ctx, x, z, heading, skip, max, null);
	}

	/** {@link #probe(PlanContext, int, int, int, int, int)}, noting the grid's tiles it reads in {@code read}. */
	private static int probe(PlanContext ctx, int x, int z, int heading, int skip, int max, it.unimi.dsi.fastutil.longs.@Nullable LongSet read) {
		int n = 0;
		for (int k = skip + 1; k <= skip + max; k++) {
			int px = x + Roads.DX[heading] * k;
			int pz = z + Roads.DZ[heading] * k;
			if (read != null) {
				// the lane's corridor is a column either side: their tiles too
				int ox = -Roads.DZ[heading];
				int oz = Roads.DX[heading];
				read.add(dev.voidmc.hearth.plan.grid.Occupancy.tileKey(px, pz));
				read.add(dev.voidmc.hearth.plan.grid.Occupancy.tileKey(px + ox, pz + oz));
				read.add(dev.voidmc.hearth.plan.grid.Occupancy.tileKey(px - ox, pz - oz));
			}
			byte c = ctx.occ.get(px, pz);
			if (!(Cell.isBuildable(c) || c == Cell.PLANNED_ROAD) || ctx.ground.isWater(px, pz)) {
				break;
			}
			// keep the lane's own corridor clear too
			int ox = -Roads.DZ[heading];
			int oz = Roads.DX[heading];
			if (!passable(ctx.occ.get(px + ox, pz + oz)) || !passable(ctx.occ.get(px - ox, pz - oz))) {
				break;
			}
			n++;
		}
		return n;
	}

	private static boolean passable(byte c) {
		return Cell.isBuildable(c) || c == Cell.PLANNED_ROAD || Cell.isAccess(c);
	}

	/** Walks and builds a lane from a branch place. */
	static boolean walkBranch(PlanContext ctx, Branch b) {
		RoadEdge parent = b.edge;
		Polyline.Dense d = parent.dense();
		int sx = d.xs()[b.index];
		int sz = d.zs()[b.index];
		int zone = ctx.zone(sx, sz);
		LayoutType layout = ctx.state.layout;
		boolean[] joined = new boolean[1];
		int maxLen = (int) Math.round(Grid.laneLength(ctx) * (0.75 + 0.5 * ctx.hash(sx, sz, 13)));
		Roads.Mode mode = b.contour ? Roads.Mode.CONTOUR : Roads.Mode.STRAIGHT;
		int[][] path = Roads.walk(ctx, sx, sz, b.heading, parent.width / 2 + 1, maxLen, mode, 11, 1, joined);
		if (path[0].length < parent.width / 2 + 10) {
			failed(ctx, b);
			return false;
		}
		RoadKind kind = layout.dense && zone == 0 && ctx.tier.ordinal() >= 2 ? RoadKind.ALLEY : zone >= 3 ? RoadKind.PATH : RoadKind.LANE;
		int from = Roads.junction(ctx, parent, b.index);
		RoadEdge lane = Roads.plan(ctx, from, path[0], path[1], RoadEdge.Role.LANE, kind, 3, parent.width / 2 + 8);
		if (lane == null) {
			failed(ctx, b);
			return false;
		}
		lane.parent = parent.id;
		lane.parentIndex = b.index;
		if (stairShare(lane) > 0.5) {
			// mostly steps: it is a stair street; later lanes branch off it along the contour
			lane = ctx.state.graph.replaceRole(lane.id, RoadEdge.Role.STAIR);
		}
		if (joined[0]) {
			Polyline.Dense ld = lane.dense();
			int[] at = Roads.edgeAt(ctx, ld.xs()[ld.length() - 1], ld.zs()[ld.length() - 1]);
			if (at != null && at[0] != parent.id) {
				ctx.state.graph.edge(at[0]).addJunction(at[1]);
			}
		}
		Roads.build(ctx, lane, lane.length(), "road: a new " + kind.getSerializedName() + " off the " + describe(parent));
		Grid.planAhead(ctx, lane, 0, lane.length());
		Beauty.cornerLamp(ctx, lane);
		return true;
	}

	/**
	 * Lane mouths that would not walk, per settlement (not saved): when every other way to grow has failed, a street's
	 * mouths that ran into a plot a few blocks on are passed over and its next best are offered, instead of the same
	 * failures call after call (a walled steppe town stalled at 312 souls with open land all round it).
	 */
	private static final java.util.Map<dev.voidmc.hearth.plan.model.PlanState, java.util.Set<Long>> FAILED =
		java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private static long mouth(RoadEdge e, int index, int heading) {
		return ((long) e.id << 24) ^ ((long) index << 4) ^ heading;
	}

	private static void failed(PlanContext ctx, Branch b) {
		java.util.Set<Long> set = FAILED.computeIfAbsent(ctx.state, k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>()));
		if (set.size() > 4096) {
			// a town that has tried this many has changed since the first: let them be tried again
			set.clear();
		}
		set.add(mouth(b.edge, b.index, b.heading));
	}

	private static boolean hasFailed(PlanContext ctx, RoadEdge e, int index, int heading) {
		java.util.Set<Long> set = FAILED.get(ctx.state);
		return set != null && set.contains(mouth(e, index, heading));
	}

	static double stairShare(RoadEdge e) {
		int stairs = 0;
		for (int r = 0; r < e.runCount(); r++) {
			if (e.runType(r) == RoadEdge.STAIRS) {
				stairs += e.runEnd(r) - e.runStart(r);
			}
		}
		return stairs / (double) Math.max(1, e.length());
	}

	static String describe(RoadEdge e) {
		return switch (e.role) {
			case ARM -> "main street";
			case RING -> "ring road";
			case STAIR -> "stair street";
			case SWITCHBACK -> "switchback road";
			case TRACK -> "track";
			case HAUL -> "cart track";
			case SQUARE -> "square";
			default -> e.kind.getSerializedName();
		};
	}

	/** Out into the farmland: the next stretch of a track or arm, or a new track off one beyond the town. */
	static boolean outward(PlanContext ctx, boolean far) {
		double core = ctx.coreRadius();
		double target = far ? core * 2.2 * ctx.config.sprawlBias() : Parcels.edgeRing(ctx);
		RoadEdge best = null;
		double bestD = Double.MAX_VALUE;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if ((e.role != RoadEdge.Role.ARM && e.role != RoadEdge.Role.TRACK) || e.fullyBuilt()) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int i = Math.min(d.length() - 1, e.built);
			double dist = ctx.distanceToHeart(d.xs()[i], d.zs()[i]);
			double score = Math.abs(dist - target) + (dist > target ? 30 : 0) + (e.role == RoadEdge.Role.TRACK ? -10 : 0);
			if (score < bestD) {
				bestD = score;
				best = e;
			}
		}
		Branch branch = bestBranch(ctx, true);
		String reason = far ? "road: the track runs out to the farmsteads" : "road: the road runs out past the fields";
		boolean preferTrack = branch != null && (best == null || bestD >= 40);
		if (preferTrack && track(ctx, branch, far)) {
			return true;
		}
		if (best != null && extendArm(ctx, best, reason)) {
			return true;
		}
		if (!preferTrack && branch != null && track(ctx, branch, far)) {
			return true;
		}
		return continueArm(ctx);
	}

	private static boolean track(PlanContext ctx, Branch b, boolean far) {
		RoadEdge parent = b.edge;
		Polyline.Dense d = parent.dense();
		int sx = d.xs()[b.index];
		int sz = d.zs()[b.index];
		boolean[] joined = new boolean[1];
		int maxLen = far ? 120 : 64;
		int[][] path = Roads.walk(ctx, sx, sz, b.heading, parent.width / 2 + 1, maxLen, Roads.Mode.STRAIGHT, 16, 1, joined);
		if (path[0].length < parent.width / 2 + 16) {
			return false;
		}
		int from = Roads.junction(ctx, parent, b.index);
		RoadEdge track = Roads.plan(ctx, from, path[0], path[1], RoadEdge.Role.TRACK, RoadKind.PATH, 3, 14);
		if (track == null) {
			return false;
		}
		track.parent = parent.id;
		track.parentIndex = b.index;
		Roads.build(ctx, track, Math.min(track.length(), far ? 64 : track.length()), "road: a farm track off the " + describe(parent));
		return true;
	}

	/** Routes and builds an approach road up to a site from the nearest road. */
	public static boolean approachTo(PlanContext ctx, int tx, int tz, String reason) {
		// stand a little off the very top so the building has room in front of it
		if (!Cell.isBuildable(ctx.occ.get(tx, tz))) {
			return false;
		}
		RoadEdge e = Roads.approach(ctx, tx, tz, RoadEdge.Role.APPROACH, RoadKind.LANE, 3, 48, reason);
		return e != null;
	}

	/** A lane down to the water's edge. */
	static boolean waterLane(PlanContext ctx) {
		Site site = ctx.state.site;
		if (site == null) {
			return false;
		}
		// the nearest dry column to the water near the town
		int wx = site.waterX();
		int wz = site.waterZ();
		double ang = StrictMath.atan2(ctx.heartZ() - wz, ctx.heartX() - wx);
		for (int k = 2; k < 40; k++) {
			int x = wx + (int) Math.round(StrictMath.cos(ang) * k);
			int z = wz + (int) Math.round(StrictMath.sin(ang) * k);
			if (!ctx.ground.isWater(x, z) && Cell.isBuildable(ctx.occ.get(x, z))) {
				return approachTo(ctx, x, z, "road: a lane down to the water");
			}
		}
		return false;
	}

	/** A new boardwalk over the water, off an existing one or out from the shore. */
	static boolean boardwalk(PlanContext ctx) {
		Branch best = null;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.built < 6 || (e.role != RoadEdge.Role.BOARDWALK && e.role != RoadEdge.Role.ARM)) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int lim = Math.min(e.built, d.length());
			for (int i = 3; i < lim - 3; i += 3) {
				if (e.junctionClearance(i) < 18) {
					continue;
				}
				int tx = d.xs()[Math.min(lim - 1, i + 2)] - d.xs()[Math.max(0, i - 2)];
				int tz = d.zs()[Math.min(lim - 1, i + 2)] - d.zs()[Math.max(0, i - 2)];
				for (int side = -1; side <= 1; side += 2) {
					double nx = side < 0 ? tz : -tz;
					double nz = side < 0 ? -tx : tx;
					int heading = Roads.cardinal(nx, nz);
					// open, shallow water ahead (a shore road may be a few blocks from it)
					int wet = 0;
					int firstWet = -1;
					boolean blocked = false;
					for (int k = 3; k <= 22 && !blocked; k++) {
						int px = d.xs()[i] + Roads.DX[heading] * k;
						int pz = d.zs()[i] + Roads.DZ[heading] * k;
						byte c = ctx.occ.get(px, pz);
						if (!Cell.isBuildable(c) && c != Cell.PLANNED_ROAD) {
							blocked = true;
						} else if (ctx.ground.isWater(px, pz) && ctx.ground.depth(px, pz) <= 7) {
							wet++;
							firstWet = firstWet < 0 ? k : firstWet;
						}
					}
					if (blocked || wet < 10 || firstWet > 10) {
						continue;
					}
					double dist = ctx.distanceToHeart(d.xs()[i], d.zs()[i]);
					double score = -dist + (e.role == RoadEdge.Role.BOARDWALK ? 10 : 0) + ctx.hash(d.xs()[i], d.zs()[i], 17) * 5;
					if (best == null || score > best.score) {
						best = new Branch(e, i, heading, dist, score, false);
					}
				}
			}
		}
		if (best == null) {
			return false;
		}
		Polyline.Dense d = best.edge.dense();
		int sx = d.xs()[best.index];
		int sz = d.zs()[best.index];
		boolean[] joined = new boolean[1];
		int[][] path = Roads.walk(ctx, sx, sz, best.heading, best.edge.width / 2 + 1, 44, Roads.Mode.WATER, 12, 1, joined);
		if (path[0].length < 10) {
			return false;
		}
		int from = Roads.junction(ctx, best.edge, best.index);
		RoadEdge walk = Roads.plan(ctx, from, path[0], path[1], RoadEdge.Role.BOARDWALK, RoadKind.LANE, 3, 8);
		if (walk == null) {
			return false;
		}
		Roads.build(ctx, walk, walk.length(), "road: a new boardwalk over the water");
		return true;
	}
}
