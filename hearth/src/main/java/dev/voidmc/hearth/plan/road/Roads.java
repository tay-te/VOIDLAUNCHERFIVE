package dev.voidmc.hearth.plan.road;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Operations on the road network: planning an edge from a route (graded, cut where it cannot go on), issuing its next
 * stretch as road, stair and bridge plots, walking a new lane out from a road, and routing an approach up to a site.
 * Every road is issued before anything that fronts it is queued, so the sim always builds roads first.
 */
public final class Roads {
	public static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
	public static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};

	private Roads() {
	}

	/** How a road of this role is graded. */
	public static Profile.Rules rules(PlanContext ctx, RoadEdge.Role role) {
		var cfg = ctx.config;
		boolean stairs = switch (role) {
			case TRACK, SWITCHBACK, BOARDWALK, RING, HAUL -> false;
			case ARM -> ctx.state.layout.terraces;
			default -> true;
		};
		return new Profile.Rules(cfg.roadGrade(), cfg.stairGrade(), stairs, role == RoadEdge.Role.STAIR ? cfg.maxCut() + 2 : cfg.maxCut(),
			cfg.bridgeMaxLength(), role == RoadEdge.Role.BOARDWALK ? 1 : 2, role == RoadEdge.Role.BOARDWALK);
	}

	/**
	 * Plans an edge along a dense route from an existing node: grades it, cuts it where it cannot go on, adds it to the
	 * graph and reserves its corridor. Returns null if less than {@code minLength} of it is usable.
	 */
	public static @Nullable RoadEdge plan(PlanContext ctx, int fromNode, int[] xs, int[] zs, RoadEdge.Role role, RoadKind kind, int width,
		int minLength) {
		if (xs.length < 2) {
			return null;
		}
		Profile prof = Profile.grade(xs, zs, ctx.ground, rules(ctx, role));
		int n = prof.usable;
		if (n < Math.max(2, minLength)) {
			return null;
		}
		int[] ys = prof.ys;
		Polyline line = Polyline.fromDense(Arrays.copyOf(xs, n), ys, Arrays.copyOf(zs, n), 0, n - 1);
		RoadGraph g = ctx.state.graph;
		RoadGraph.Node end = g.addNode(xs[n - 1], ys[n - 1], zs[n - 1], RoadGraph.NodeKind.END);
		RoadEdge e = g.addEdge(fromNode, end.id(), role, kind, width, line, prof.runs);
		reserve(ctx, e);
		return e;
	}

	/** Shows an edge's unbuilt corridor as planned road, so plots keep off it. */
	public static void reserve(PlanContext ctx, RoadEdge e) {
		if (!e.emits()) {
			return;
		}
		Polyline.corridor(e.line().points(), e.width, (x, z) -> ctx.occ.mark(x, z, Cell.PLANNED_ROAD));
	}

	/**
	 * Issues the edge's next stretch, up to dense index {@code upTo} (exclusive), as plots: graded runs as roads, steep
	 * runs as stairs, water runs as bridges or boardwalks, each no longer than the config's road plot length. Queues
	 * them and marks their corridors.
	 */
	public static void build(PlanContext ctx, RoadEdge e, int upTo, String reason) {
		upTo = Math.min(upTo, Math.min(e.length(), e.limit));
		// never pave over something standing: the road ends where its way is blocked (a quick look along the centreline
		// first, then the exact corridors of the plots it would issue)
		int blocked = firstBlocked(ctx, e, Math.max(0, e.built - 1), upTo);
		if (blocked >= 0) {
			e.limit = Math.max(e.built, blocked - 1);
			upTo = Math.min(upTo, e.limit);
		}
		if (!e.emits() || upTo <= e.built) {
			e.built = Math.max(e.built, upTo);
			return;
		}
		for (int attempt = 0; attempt < 4; attempt++) {
			List<PlotSpec> specs = new ArrayList<>();
			int reached = stretch(ctx, e, upTo, specs);
			if (reached <= e.built) {
				return;
			}
			int hit = firstBlocked(ctx, e, specs);
			if (hit < 0) {
				for (PlotSpec spec : specs) {
					issue(ctx, spec, reason);
				}
				e.built = Math.max(e.built, reached);
				return;
			}
			e.limit = Math.max(e.built, hit - 1);
			upTo = Math.min(upTo, e.limit);
			if (upTo <= e.built) {
				return;
			}
		}
	}

	/**
	 * The plots of the edge's next stretch, up to dense index {@code upTo} (exclusive): graded runs as roads, steep runs
	 * as stairs, water runs as bridges or boardwalks, each no longer than the config's road plot length. Returns how far
	 * the stretch really reaches (it stops short of a bridge it would cut).
	 */
	private static int stretch(PlanContext ctx, RoadEdge e, int upTo, List<PlotSpec> out) {
		Polyline.Dense d = e.dense();
		int maxLen = Math.max(8, ctx.config.roadPlotLength());
		// a bridge goes up whole or not at all: stop short of one that would be cut
		for (int r = 0; r < e.runCount(); r++) {
			int type = e.runType(r);
			if ((type == RoadEdge.BRIDGE) && e.runStart(r) < upTo && e.runEnd(r) > upTo) {
				upTo = Math.max(e.built, e.runStart(r) - 1);
			}
		}
		if (upTo <= e.built) {
			return upTo;
		}
		for (int r = 0; r < e.runCount(); r++) {
			int type = e.runType(r);
			int s = Math.max(e.runStart(r), Math.max(0, e.built - 1));
			int t = Math.min(e.runEnd(r), upTo);
			if (t <= s) {
				continue;
			}
			if (type == RoadEdge.BRIDGE || type == RoadEdge.BOARDWALK) {
				boolean boardwalk = type == RoadEdge.BOARDWALK;
				if (!boardwalk && e.runStart(r) < e.built) {
					continue;
				}
				// abutment to abutment: one column of land at each end
				int a = Math.max(0, boardwalk ? s - 1 : e.runStart(r) - 1);
				int b = Math.min(e.length() - 1, boardwalk ? t : e.runEnd(r));
				for (int c = a; c < b; c += maxLen) {
					int cEnd = Math.min(b, c + maxLen);
					int[] pts = points(d, c, cEnd, e.runDeck(r), boardwalk);
					int water = waterLevel(ctx.ground, d, c, cEnd);
					out.add(Specs.bridge(ctx, pts, e.width, e.runDeck(r), water, boardwalk, e.kind));
				}
				continue;
			}
			Identifier archetype = type == RoadEdge.STAIRS && ctx.catalogue.has(Archetypes.STAIRS) ? Archetypes.STAIRS : Archetypes.ROAD;
			int c = s;
			while (true) {
				int cEnd = Math.min(t - 1, c + maxLen);
				if (cEnd <= c) {
					// a single column between two other runs: the bridge or stair beside it already takes it in
					boolean alone = e.runCount() == 1;
					if (alone && c + 1 < d.length()) {
						cEnd = c + 1;
					} else if (alone && c > 0) {
						c--;
						cEnd = c + 1;
					} else if (c + 1 < d.length() && e.typeAt(c + 1) == type) {
						cEnd = c + 1;
					} else if (c > 0 && e.typeAt(c - 1) == type) {
						c--;
						cEnd = c + 1;
					} else {
						break;
					}
				}
				int[] pts = Polyline.fromDense(d.xs(), d.ys(), d.zs(), c, cEnd).points();
				RoadKind kind = e.kind;
				if (e.role == RoadEdge.Role.ARM && kind == RoadKind.STREET && ctx.distanceToHeart(d.xs()[c], d.zs()[c]) > ctx.coreRadius() * 1.9) {
					kind = RoadKind.HIGHWAY;
				}
				out.add(Specs.road(ctx, archetype, pts, e.width, kind));
				if (cEnd >= t - 1) {
					break;
				}
				c = cEnd;
			}
		}
		return upTo;
	}

	/** The first dense index of the edge where one of these plots' corridors takes a column something stands on, or -1. */
	private static int firstBlocked(PlanContext ctx, RoadEdge e, List<PlotSpec> specs) {
		Polyline.Dense d = e.dense();
		int from = Math.max(0, e.built - 2);
		int to = Math.min(d.length() - 1, Math.max(from, e.built) + 4096);
		int[] first = {Integer.MAX_VALUE};
		for (PlotSpec spec : specs) {
			PlotGeometry.forEachColumn(spec, (x, z) -> {
				byte c = ctx.occ.get(x, z);
				if (!Cell.isSolid(c)) {
					return;
				}
				int i = from + nearest(d, from, to, x, z);
				if (c == Cell.WALL) {
					byte centre = ctx.occ.get(d.xs()[i], d.zs()[i]);
					if (Cell.isAccess(centre) || centre == Cell.PLANNED_ROAD) {
						return;
					}
				}
				first[0] = Math.min(first[0], i);
			});
		}
		return first[0] == Integer.MAX_VALUE ? -1 : first[0];
	}


	/**
	 * The first dense index in [from, to) whose corridor takes a column something already stands on, or -1. The corridor
	 * is the polyline's own (the contract's rule), so a bend never cuts the corner of a building beside it.
	 */
	private static int firstBlocked(PlanContext ctx, RoadEdge e, int from, int to) {
		Polyline.Dense d = e.dense();
		if (to - from < 1) {
			return -1;
		}
		int a = Math.max(0, from);
		int b = Math.min(d.length() - 1, to);
		if (b <= a) {
			return -1;
		}
		int[] pts = Polyline.fromDense(d.xs(), d.ys(), d.zs(), a, b).points();
		int[] first = {Integer.MAX_VALUE};
		Polyline.corridor(pts, e.width, (x, z) -> {
			byte c = ctx.occ.get(x, z);
			if (!Cell.isSolid(c)) {
				return;
			}
			int i = a + nearest(d, a, b, x, z);
			if (c == Cell.WALL) {
				// the wall line is crossed where the road's own centreline is road already or kept for it: a gate
				byte centre = ctx.occ.get(d.xs()[i], d.zs()[i]);
				if (Cell.isAccess(centre) || centre == Cell.PLANNED_ROAD) {
					return;
				}
			}
			first[0] = Math.min(first[0], i);
		});
		return first[0] == Integer.MAX_VALUE ? -1 : first[0];
	}

	private static int nearest(Polyline.Dense d, int a, int b, int x, int z) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int i = a; i <= b; i++) {
			long dd = d.distance2(i, x, z);
			if (dd < bestD) {
				bestD = dd;
				best = i - a;
			}
		}
		return best;
	}

	private static int[] points(Polyline.Dense d, int a, int b, int deck, boolean boardwalk) {
		int[] ys = new int[b - a + 1];
		for (int i = a; i <= b; i++) {
			ys[i - a] = (i == a || i == b) && !boardwalk ? d.ys()[i] : deck;
		}
		int[] xs = Arrays.copyOfRange(d.xs(), a, b + 1);
		int[] zs = Arrays.copyOfRange(d.zs(), a, b + 1);
		return Polyline.fromDense(xs, ys, zs, 0, xs.length - 1).points();
	}

	private static int waterLevel(Ground g, Polyline.Dense d, int a, int b) {
		int w = Integer.MIN_VALUE;
		for (int i = a; i <= b; i++) {
			w = Math.max(w, g.water(d.xs()[i], d.zs()[i]));
		}
		return w == dev.voidmc.hearth.api.TerrainProbe.NO_WATER ? g.ground(d.xs()[a], d.zs()[a]) : w;
	}

	/** Queues a plot and marks it in the grid. */
	public static void issue(PlanContext ctx, PlotSpec spec, String reason) {
		issue(ctx, spec, -1, reason);
	}

	public static void issue(PlanContext ctx, PlotSpec spec, int replaces, String reason) {
		ctx.queue(spec, replaces, reason);
		PlotGeometry.mark(ctx.occ, spec, ctx.catalogue);
	}

	/** A junction node on an edge at a dense index (the edge records it so later lanes keep their distance). */
	public static int junction(PlanContext ctx, RoadEdge e, int index) {
		Polyline.Dense d = e.dense();
		RoadGraph.Node n = ctx.state.graph.addNode(d.xs()[index], d.ys()[index], d.zs()[index], RoadGraph.NodeKind.JUNCTION);
		e.addJunction(index);
		return n.id();
	}

	/** The built edge whose centreline passes nearest (x, z), within its half width plus one, as {edge, index}. */
	public static int @Nullable [] edgeAt(PlanContext ctx, int x, int z) {
		int bestEdge = -1;
		int bestIndex = -1;
		long best = Long.MAX_VALUE;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.built <= 0) {
				continue;
			}
			Polyline.Dense d = e.dense();
			long reach = (long) (e.width / 2 + 2) * (e.width / 2 + 2);
			for (int i = 0; i < Math.min(e.built, d.length()); i++) {
				long dd = d.distance2(i, x, z);
				if (dd <= reach && dd < best) {
					best = dd;
					bestEdge = e.id;
					bestIndex = i;
				}
			}
		}
		return bestEdge < 0 ? null : new int[]{bestEdge, bestIndex};
	}

	public enum Mode {
		/** Keep the heading, bending only where the slope asks. */
		STRAIGHT,
		/** Hold the starting level: a terrace street along the contour. */
		CONTOUR,
		/** Over the water: a boardwalk that runs until it meets land. */
		WATER
	}

	/**
	 * Walks a new lane from a road's centreline column out along heading {@code dir} (one of eight): through the parent
	 * road's corridor for {@code skip} steps, then over free ground until it meets another road (joining it), an
	 * obstacle, water, too steep a climb, another road running alongside within {@code clearance}, or {@code maxLen}.
	 *
	 * @param joined set to true in {@code joined[0]} if the lane ended on another road
	 */
	public static int[][] walk(PlanContext ctx, int sx, int sz, int dir, int skip, int maxLen, Mode mode, int clearance, int halfWidth,
		boolean[] joined) {
		Ground g = ctx.ground;
		int[] xs = new int[maxLen + 4];
		int[] zs = new int[maxLen + 4];
		int n = 0;
		xs[n] = sx;
		zs[n++] = sz;
		int x = sx;
		int z = sz;
		int d = dir;
		double h0 = g.smooth(sx, sz);
		int wetSteps = 0;
		joined[0] = false;
		double stairGrade = ctx.config.stairGrade();
		for (int step = 1; step <= maxLen; step++) {
			int best = -1;
			double bestCost = Double.POSITIVE_INFINITY;
			boolean bestJoin = false;
			int span = mode == Mode.CONTOUR ? 2 : 1;
			for (int turn = -span; turn <= span; turn++) {
				int nd = (d + turn + 8) & 7;
				if (angle(nd, dir) > span) {
					continue;
				}
				int nx = x + DX[nd];
				int nz = z + DZ[nd];
				byte code = ctx.occ.get(nx, nz);
				boolean access = Cell.isAccess(code);
				boolean wet = g.isWater(nx, nz);
				// a lane keeps its heading unless the slope really asks it to bend
				double cost = Math.abs(turn) * 2.5 + angle(nd, dir) * (mode == Mode.STRAIGHT ? 5.0 : 1.5);
				boolean join = false;
				if (step <= skip) {
					if (!access && !Cell.isBuildable(code) && code != Cell.PLANNED_ROAD) {
						continue;
					}
				} else if (access || code == Cell.PLANNED_ROAD && ctx.joinPlanned) {
					join = true;
					cost -= 100;
				} else if (!Cell.isBuildable(code) && code != Cell.PLANNED_ROAD) {
					continue;
				} else if (!corridorClear(ctx, nx, nz, halfWidth)) {
					// the lane's own width must fit, not just its centreline
					continue;
				}
				if (mode == Mode.WATER) {
					if (!wet && step > skip && !join) {
						cost += 4;
					}
				} else if (wet && !join && !(code == Cell.BRIDGE)) {
					continue;
				}
				double len = (nd & 1) == 1 ? 1.4142 : 1.0;
				double grade = Math.abs(g.smooth(nx, nz) - g.smooth(x, z)) / len;
				if (grade > stairGrade + 0.01 && mode != Mode.WATER) {
					continue;
				}
				cost += grade * (mode == Mode.CONTOUR ? 1 : 3);
				if (mode == Mode.CONTOUR) {
					cost += Math.abs(g.smooth(nx, nz) - h0) * 2.0;
				}
				if (cost < bestCost) {
					bestCost = cost;
					best = nd;
					bestJoin = join;
				}
			}
			if (best < 0) {
				break;
			}
			d = best;
			x += DX[d];
			z += DZ[d];
			xs[n] = x;
			zs[n++] = z;
			if (bestJoin) {
				joined[0] = true;
				// run on into the road we met so the two surfaces overlap
				for (int k = 0; k < halfWidth + 1 && n < xs.length; k++) {
					int nx = x + DX[d];
					int nz = z + DZ[d];
					if (!Cell.isAccess(ctx.occ.get(nx, nz))) {
						break;
					}
					x = nx;
					z = nz;
					xs[n] = x;
					zs[n++] = z;
				}
				break;
			}
			if (mode == Mode.WATER) {
				// over the water until it reaches land again (the walk may start ashore and run out to it)
				if (g.isWater(x, z)) {
					wetSteps++;
				} else if (wetSteps >= 4) {
					break;
				}
			}
			if (step > skip + 3 && clearance > 0 && alongside(ctx, x, z, d, halfWidth, clearance)) {
				n--;
				break;
			}
		}
		return new int[][]{Arrays.copyOf(xs, n), Arrays.copyOf(zs, n)};
	}

	private static boolean corridorClear(PlanContext ctx, int x, int z, int halfWidth) {
		for (int dz = -halfWidth; dz <= halfWidth; dz++) {
			for (int dx = -halfWidth; dx <= halfWidth; dx++) {
				byte c = ctx.occ.get(x + dx, z + dz);
				if (!Cell.isBuildable(c) && c != Cell.PLANNED_ROAD && !Cell.isAccess(c)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether another road runs within {@code clearance} to either side of a lane heading {@code d} at (x, z). */
	private static boolean alongside(PlanContext ctx, int x, int z, int d, int halfWidth, int clearance) {
		int px = -DZ[d];
		int pz = DX[d];
		for (int side = -1; side <= 1; side += 2) {
			for (int k = halfWidth + 2; k <= clearance; k++) {
				byte c = ctx.occ.get(x + side * px * k, z + side * pz * k);
				if (Cell.isAccess(c) || c == Cell.PLANNED_ROAD && ctx.joinPlanned) {
					return true;
				}
			}
		}
		return false;
	}

	/** The smaller number of 45° steps between two of the eight headings. */
	public static int angle(int a, int b) {
		int d = Math.abs(a - b) & 7;
		return Math.min(d, 8 - d);
	}

	/** The nearest of the eight headings to a direction. */
	public static int heading(double dx, double dz) {
		double a = StrictMath.atan2(dz, dx);
		int h = (int) Math.round(a / (Math.PI / 4));
		return (h + 8) & 7;
	}

	/** The nearest cardinal heading (0 east, 2 south, 4 west, 6 north). */
	public static int cardinal(double dx, double dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 0 : 4) : (dz >= 0 ? 2 : 6);
	}

	/**
	 * Routes a road from a target column back to the nearest road (stairs allowed), plans it as an edge from a junction
	 * on that road, and builds it whole. Returns the edge (its last column is next to the target) or null.
	 */
	public static @Nullable RoadEdge approach(PlanContext ctx, int tx, int tz, RoadEdge.Role role, RoadKind kind, int width, int margin,
		String reason) {
		Router.Params params = new Router.Params(1, Math.min(margin, 24), ctx.config.roadGrade(), ctx.config.stairGrade(), -1, 1.2, true, 90_000);
		return connect(ctx, tx, tz, role, kind, width, params, reason);
	}

	/**
	 * A cart road out to a far place — a quarry, a mine, a logging camp, the castle hill, a monastery — routed back from
	 * it to the nearest road on a grid coarse enough for the distance (no stairs: carts use it; water bridged where
	 * short), planned from a junction there and built whole. One such route per call, like an approach.
	 */
	public static @Nullable RoadEdge haul(PlanContext ctx, int tx, int tz, RoadEdge.Role role, RoadKind kind, int width, boolean stairs, String reason) {
		if (role == RoadEdge.Role.HAUL ? !ctx.haulMayGrow() : !ctx.roadsMayGrow()) {
			return null;
		}
		double d = ctx.distanceToHeart(tx, tz);
		int cell = d < 70 ? 1 : d < 170 ? 2 : 4;
		Router.Params params = new Router.Params(cell, cell == 1 ? 20 : 32, ctx.config.roadGrade(), stairs ? ctx.config.stairGrade() : ctx.config.roadGrade(),
			9, 2.5, true, 160_000, 0, 2);
		return connect(ctx, tx, tz, role, kind, width, params, reason);
	}

	private static @Nullable RoadEdge connect(PlanContext ctx, int tx, int tz, RoadEdge.Role role, RoadKind kind, int width, Router.Params params,
		String reason) {
		// one route per call, its search bounded: an unreachable crag must not cost more than a few milliseconds
		if (ctx.routed) {
			return null;
		}
		ctx.routed = true;
		// searched from the site toward the heart, ending at the first road it reaches
		int[][] path = Router.route(ctx.ground, ctx.occ, tx, tz, ctx.heartX(), ctx.heartZ(), params);
		if (path == null || path[0].length < 3) {
			return null;
		}
		int n = path[0].length;
		int[] xs = new int[n];
		int[] zs = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = path[0][n - 1 - i];
			zs[i] = path[1][n - 1 - i];
		}
		int[] at = edgeAt(ctx, xs[0], zs[0]);
		int from;
		if (at != null) {
			RoadEdge parent = ctx.state.graph.edge(at[0]);
			// start on the parent's centreline so the surfaces join
			Polyline.Dense pd = parent.dense();
			int[][] lead = Router.densify(new int[]{pd.xs()[at[1]], xs[0]}, new int[]{pd.zs()[at[1]], zs[0]});
			int[] nx = concat(Arrays.copyOf(lead[0], lead[0].length - 1), xs);
			int[] nz = concat(Arrays.copyOf(lead[1], lead[1].length - 1), zs);
			xs = nx;
			zs = nz;
			from = junction(ctx, parent, at[1]);
		} else {
			from = ctx.state.graph.addNode(xs[0], ctx.ground.surface(xs[0], zs[0]), zs[0], RoadGraph.NodeKind.JUNCTION).id();
		}
		RoadEdge e = plan(ctx, from, xs, zs, role, kind, width, 3);
		if (e == null) {
			return null;
		}
		build(ctx, e, e.length(), reason);
		return e;
	}

	public static int[] concat(int[] a, int[] b) {
		int[] r = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}
}
