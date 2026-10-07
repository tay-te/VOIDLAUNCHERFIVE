package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import org.jspecify.annotations.Nullable;

/**
 * Plans the streets of a town ahead of its houses (PLANNING.md, "Roads first"). Whenever a stretch of street is built
 * within reach of the core, the side lanes that will one day leave it are walked out and reserved at the layout's
 * spacing — square off a straight street, along the contour on a slope, as a stair street straight up one — so the
 * houses that line the street leave the mouths of the lanes open, and the town grows in blocks instead of as a ribbon
 * along its main roads. A planned lane that meets another road or planned lane joins it, so the blocks close. Lanes are
 * built (issued as plots) only when the town needs their frontage.
 */
public final class Grid {
	private Grid() {
	}

	/** Whether a layout lays out its streets ahead. Expansive and water layouts grow freely instead. */
	public static boolean plans(LayoutType layout) {
		return switch (layout) {
			case FARMSTEADS, STILT, RING -> false;
			default -> true;
		};
	}

	/**
	 * Whether this settlement lays out its streets ahead: as {@link #plans(LayoutType)}, and a farmsteads settlement once
	 * it has nucleated ({@link #nucleated}): its heart then grows in blocks like any town's.
	 */
	public static boolean plans(PlanContext ctx) {
		return plans(ctx.state.layout) || nucleated(ctx);
	}

	/**
	 * Whether an expansive settlement has grown a town at its heart (ART_REVIEW_2 item 1). A Bohemian village is a green
	 * (a <i>náves</i>) with farmsteads round it, and a town grows round a square (a <i>náměstí</i>): from village tier a
	 * farmsteads settlement keeps spreading its farmers' homes as farmsteads out along its tracks, but the rest of its
	 * folk build a compact village round the green, lanes planned ahead, homes filling in near the heart before they
	 * spread, town homes in rows on the village street.
	 */
	public static boolean nucleated(PlanContext ctx) {
		return ctx.state.layout.expansive && ctx.tier.ordinal() >= dev.voidmc.hearth.api.SettlementTier.VILLAGE.ordinal();
	}

	/** Whether the settlement still grows as an expansive one: an expansive layout that has not nucleated. */
	public static boolean expansive(PlanContext ctx) {
		return ctx.state.layout.expansive && !nucleated(ctx);
	}

	/** Whether the town terraces its plots and runs its lanes along the contour: the layout's habit, and a nucleated village's on its hill. */
	public static boolean terraces(PlanContext ctx) {
		return ctx.state.layout.terraces || nucleated(ctx);
	}

	/** How readily the town builds up: the layout's bias, a crossroads town's in a nucleated village. */
	public static double verticalBias(PlanContext ctx) {
		return nucleated(ctx) ? Math.max(ctx.state.layout.verticalBias, LayoutType.CROSSROADS.verticalBias) : ctx.state.layout.verticalBias;
	}

	/** How far apart the side lanes leave a street: the layout's spacing, a crossroads town's in a nucleated village. */
	public static int laneSpacing(PlanContext ctx) {
		return nucleated(ctx) ? LayoutType.CROSSROADS.laneSpacing : ctx.state.layout.laneSpacing;
	}

	/** How far a side lane runs: the layout's length, a crossroads town's in a nucleated village. */
	public static int laneLength(PlanContext ctx) {
		return nucleated(ctx) ? LayoutType.CROSSROADS.laneLength : ctx.state.layout.laneLength;
	}

	/** The side gap between detached buildings in a zone: the layout's, closer in a nucleated farmsteads village. */
	public static int gap(PlanContext ctx, int zone) {
		if (nucleated(ctx) && zone <= 1) {
			return zone == 0 ? 1 : 2;
		}
		return ctx.state.layout.gap(zone);
	}

	/** How far from the heart streets are planned ahead. */
	public static double reach(PlanContext ctx) {
		return Math.max(44, ctx.coreRadius() * 1.5 + 18);
	}

	/** Plans side lanes along the stretch {@code [from, to)} of a built street. */
	public static void planAhead(PlanContext ctx, RoadEdge e, int from, int to) {
		if (!plans(ctx) || !e.emits()) {
			return;
		}
		switch (e.role) {
			case ARM, LANE, STAIR, APPROACH -> {
			}
			default -> {
				return;
			}
		}
		LayoutType layout = ctx.state.layout;
		int spacing = e.role == RoadEdge.Role.STAIR ? stairSpacing(e) : laneSpacing(ctx);
		Polyline.Dense d = e.dense();
		int lim = Math.min(to, d.length() - 6);
		int start = Math.max(Math.max(10, spacing / 2), from);
		double reach = reach(ctx);
		for (int i = start; i < lim; i++) {
			if (e.junctionClearance(i) < spacing) {
				continue;
			}
			// beyond the planned core only the mouths are kept: short stubs, lengthened when the town gets there
			boolean stub = ctx.distanceToHeart(d.xs()[i], d.zs()[i]) > reach;
			boolean any = false;
			for (int side = -1; side <= 1; side += 2) {
				// now and then the far side's lane leaves a little further on, so not every junction is a crossing
				int at = side > 0 && ctx.hash(d.xs()[i], d.zs()[i], 61) < 0.35 ? Math.min(lim - 1, i + spacing / 3) : i;
				if (lane(ctx, e, at, side, stub) != null) {
					any = true;
				}
			}
			if (any) {
				i += spacing - 1;
			}
		}
	}

	/**
	 * Along a stair street, terrace streets leave about every seven blocks of height: that is a row of houses with a
	 * storey of retaining wall below the next street up.
	 */
	static int stairSpacing(RoadEdge e) {
		Polyline.Dense d = e.dense();
		int n = d.length();
		double rise = Math.abs(d.ys()[n - 1] - d.ys()[0]);
		double grade = rise / Math.max(1, n - 1);
		return (int) Math.max(9, Math.min(26, Math.round(7 / Math.max(0.05, grade))));
	}

	/** Walks and reserves one lane leaving a street at a dense index on one side; null if there is no room. */
	static @Nullable RoadEdge lane(PlanContext ctx, RoadEdge parent, int index, int side, boolean stub) {
		Polyline.Dense d = parent.dense();
		int lim = d.length();
		int tx = d.xs()[Math.min(lim - 1, index + 2)] - d.xs()[Math.max(0, index - 2)];
		int tz = d.zs()[Math.min(lim - 1, index + 2)] - d.zs()[Math.max(0, index - 2)];
		if (tx == 0 && tz == 0) {
			return null;
		}
		double nx = side < 0 ? tz : -tz;
		double nz = side < 0 ? -tx : tx;
		int heading = Math.abs(tx) == 0 || Math.abs(tz) == 0 || Math.abs(Math.abs(tx) - Math.abs(tz)) > 1 ? Roads.cardinal(nx, nz) : Roads.heading(nx, nz);
		int sx = d.xs()[index];
		int sz = d.zs()[index];
		if (Expand.probe(ctx, sx, sz, heading, parent.width / 2 + 1, 12) < 12) {
			return null;
		}
		LayoutType layout = ctx.state.layout;
		int mx = sx + Roads.DX[heading] * 10;
		int mz = sz + Roads.DZ[heading] * 10;
		double grade = Math.abs(ctx.ground.smooth(mx, mz) - ctx.ground.smooth(sx, sz)) / 10.0;
		boolean contour = terraces(ctx) && grade < ctx.config.roadGrade() * 0.7 && ctx.state.site != null && ctx.state.site.slope() > 0.12;
		int maxLen = stub ? parent.width / 2 + 16 : (int) Math.round(laneLength(ctx) * (0.8 + 0.4 * ctx.hash(sx, sz, 13)));
		boolean[] joined = new boolean[1];
		ctx.joinPlanned = true;
		int[][] path;
		try {
			path = Roads.walk(ctx, sx, sz, heading, parent.width / 2 + 1, maxLen, contour ? Roads.Mode.CONTOUR : Roads.Mode.STRAIGHT, 10, 1, joined);
		} finally {
			ctx.joinPlanned = false;
		}
		if (path[0].length < parent.width / 2 + 14) {
			return null;
		}
		int zone = ctx.zone(sx, sz);
		RoadKind kind = layout.dense && zone == 0 ? RoadKind.ALLEY : RoadKind.LANE;
		int from = Roads.junction(ctx, parent, index);
		RoadEdge lane = Roads.plan(ctx, from, path[0], path[1], RoadEdge.Role.LANE, kind, 3, parent.width / 2 + 12);
		if (lane == null) {
			return null;
		}
		lane.parent = parent.id;
		lane.parentIndex = index;
		if (Expand.stairShare(lane) > 0.5) {
			lane = ctx.state.graph.replaceRole(lane.id, RoadEdge.Role.STAIR);
		}
		if (joined[0]) {
			Polyline.Dense ld = lane.dense();
			int[] at = nearestEdge(ctx, ld.xs()[ld.length() - 1], ld.zs()[ld.length() - 1], lane.id);
			if (at != null) {
				ctx.state.graph.edge(at[0]).addJunction(at[1]);
			}
		}
		return lane;
	}

	/** The edge (built or planned, other than {@code except}) passing nearest a column, as {edge, index}. */
	static int @Nullable [] nearestEdge(PlanContext ctx, int x, int z, int except) {
		int bestEdge = -1;
		int bestIndex = -1;
		long best = Long.MAX_VALUE;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.id == except || !e.emits()) {
				continue;
			}
			Polyline.Dense d = e.dense();
			long reach = (long) (e.width / 2 + 2) * (e.width / 2 + 2);
			for (int i = 0; i < d.length(); i++) {
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

	/** The planned, not yet built lane whose mouth is nearest the heart, within {@code maxDistance}; null if none. */
	static @Nullable RoadEdge nearestPlanned(PlanContext ctx, double maxDistance) {
		RoadEdge best = null;
		double bestD = maxDistance;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if ((e.role != RoadEdge.Role.LANE && e.role != RoadEdge.Role.STAIR) || e.built > 0 || e.limit <= 0 || !parentBuilt(ctx, e)) {
				continue;
			}
			Polyline.Dense d = e.dense();
			double dist = ctx.distanceToHeart(d.xs()[Math.min(6, d.length() - 1)], d.zs()[Math.min(6, d.length() - 1)])
				+ ctx.hash(d.xs()[0], d.zs()[0], 67) * 4;
			if (dist < bestD) {
				bestD = dist;
				best = e;
			}
		}
		return best;
	}

	/** Whether the road a planned lane leaves from is built where the lane leaves it (so it can be reached). */
	private static boolean parentBuilt(PlanContext ctx, RoadEdge lane) {
		if (lane.parent < 0) {
			return true;
		}
		RoadEdge p = ctx.state.graph.edge(lane.parent);
		return p.built > lane.parentIndex;
	}
}
