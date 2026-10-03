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

	/** How far from the heart streets are planned ahead. */
	public static double reach(PlanContext ctx) {
		return Math.max(44, ctx.coreRadius() * 1.5 + 18);
	}

	/** Plans side lanes along the stretch {@code [from, to)} of a built street. */
	public static void planAhead(PlanContext ctx, RoadEdge e, int from, int to) {
		if (!plans(ctx.state.layout) || !e.emits()) {
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
		int spacing = e.role == RoadEdge.Role.STAIR ? 9 : layout.laneSpacing;
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
		boolean contour = layout.terraces && grade < ctx.config.roadGrade() * 0.7 && ctx.state.site != null && ctx.state.site.slope() > 0.12;
		int maxLen = stub ? parent.width / 2 + 16 : (int) Math.round(layout.laneLength * (0.8 + 0.4 * ctx.hash(sx, sz, 13)));
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
			if ((e.role != RoadEdge.Role.LANE && e.role != RoadEdge.Role.STAIR) || e.built > 0 || !parentBuilt(ctx, e)) {
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
