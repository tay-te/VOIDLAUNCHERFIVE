package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;

/**
 * A camp or hamlet whose streets will not grow lays a lane straight out from its square (requests/w7-tour.md §4). Its main
 * roads may never have been routed (a desert oasis hemmed in by its wadi, a moor too broken for the founder's arms), or
 * were short stubs built out at once, and the ordinary moves all start from a street: a lane off a street, a dead end
 * running on, a main road lengthened. With none to start from, every need read "no road could grow (no lane mouth, the
 * main streets built out)" and Safia stayed four souls round its well for 155 days. Here the square itself is the street:
 * a lane is walked out from the middle of each of its sides and from its corners, along the heading and along the
 * contour, stepping where it must, and the longest that will carry houses is built, as walkBranch builds a lane off a
 * street. A town (or anything larger) has streets of its own and is left to {@link Expand}.
 */
final class FirstLane {
	/** The shortest lane worth laying (a house on either side of it). */
	private static final int MIN_LENGTH = 12;
	/** How far the first lane runs out. */
	private static final int MAX_LENGTH = 40;

	private FirstLane() {
	}

	/** Whether a settlement this small may lay its first lane off the square when its roads will not grow. */
	static boolean wanted(PlanContext ctx, String why) {
		return ctx.tier.ordinal() <= SettlementTier.HAMLET.ordinal() && ctx.state.squareEdge >= 0 && why != null && why.contains("no road could grow")
			&& ctx.roadsMayGrow();
	}

	/** Lays the longest lane that will walk out from the square; true if one was queued. */
	static boolean lay(PlanContext ctx) {
		RoadEdge square = ctx.state.graph.edge(ctx.state.squareEdge);
		Polyline.Dense d = square.dense();
		int half = ctx.state.plazaHalf;
		// every way out, best first: {score, index on the square, joined} with its path
		java.util.List<double[]> ways = new java.util.ArrayList<>();
		java.util.List<int[][]> paths = new java.util.ArrayList<>();
		boolean[] joined = new boolean[1];
		for (int dir = 0; dir < 8; dir++) {
			// from just outside the rim of the square this way (its edge line runs a block inside, over the well's kerb on a small
			// green: a lane begun there would be blocked at its first column)
			int sx = ctx.heartX() + Roads.DX[dir] * (half + 1);
			int sz = ctx.heartZ() + Roads.DZ[dir] * (half + 1);
			int index = nearest(d, sx, sz);
			for (Roads.Mode mode : new Roads.Mode[]{Roads.Mode.STRAIGHT, Roads.Mode.CONTOUR}) {
				// (no keeping clear of other roads: the square beside it, a main road's stub, would stop it at its fourth block)
				int[][] path = Roads.walk(ctx, sx, sz, dir, 0, MAX_LENGTH, mode, 0, 1, joined);
				int n = path[0].length;
				if (Growth.trace) {
					dev.voidmc.hearth.Hearth.LOG.info("[plan]   first lane from {},{} heading {} {}: {} blocks{}", sx, sz, dir, mode, n, joined[0] ? ", joined" : "");
				}
				if (n < MIN_LENGTH) {
					continue;
				}
				// long and level: a lane houses can stand along; a little chance so camps on even ground differ
				double climb = Math.abs(ctx.ground.smooth(path[0][n - 1], path[1][n - 1]) - ctx.ground.smooth(path[0][0], path[1][0]));
				ways.add(new double[]{n - climb * 0.8 + ctx.hash(dir, mode.ordinal(), 331) * 3, index, joined[0] ? 1 : 0, paths.size()});
				paths.add(path);
			}
		}
		ways.sort((a, b) -> Double.compare(b[0], a[0]));
		for (double[] w : ways) {
			int index = (int) w[1];
			int[][] path = paths.get((int) w[3]);
			// its mouth on the rim of the square, at the square's level
			int from = ctx.state.graph.addNode(path[0][0], ctx.state.heartY, path[1][0], dev.voidmc.hearth.plan.model.RoadGraph.NodeKind.JUNCTION).id();
			// (graded, a lane may lose its last blocks where it would step down too far: what is left must still carry a house)
			RoadEdge lane = Roads.plan(ctx, from, path[0], path[1], RoadEdge.Role.LANE, RoadKind.LANE, 3, MIN_LENGTH - 4);
			if (lane == null) {
				if (Growth.trace) {
					var prof = dev.voidmc.hearth.plan.road.Profile.grade(path[0], path[1], ctx.ground, Roads.rules(ctx, RoadEdge.Role.LANE));
					StringBuilder b = new StringBuilder();
					for (int i = 0; i < path[0].length; i++) {
						b.append(' ').append(ctx.ground.ground(path[0][i], path[1][i])).append(ctx.ground.isWater(path[0][i], path[1][i]) ? "~" : "");
					}
					dev.voidmc.hearth.Hearth.LOG.info("[plan]   first lane would not grade: usable {} of {}:{}", prof.usable, path[0].length, b);
				}
				continue;
			}
			lane.parent = square.id;
			lane.parentIndex = index;
			if (w[2] > 0) {
				// it ran into a road already there (a main road's stub): a junction on it
				Polyline.Dense ld = lane.dense();
				int[] at = Roads.edgeAt(ctx, ld.xs()[ld.length() - 1], ld.zs()[ld.length() - 1]);
				if (at != null && at[0] != square.id) {
					ctx.state.graph.edge(at[0]).addJunction(at[1]);
				}
			}
			Roads.build(ctx, lane, lane.length(), "road: the first lane out from the square");
			if (lane.built < MIN_LENGTH - 4) {
				// blocked a few blocks out (something standing in its way): it stays a planned lane, and the next way is tried
				continue;
			}
			Grid.planAhead(ctx, lane, 0, lane.length());
			return true;
		}
		Growth.why(ways.isEmpty() ? "no lane would walk out from the square" : "no lane out from the square would grade");
		return false;
	}

	private static int nearest(Polyline.Dense d, int x, int z) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < d.length(); i++) {
			long dx = d.xs()[i] - x;
			long dz = d.zs()[i] - z;
			if (dx * dx + dz * dz < bestD) {
				bestD = dx * dx + dz * dz;
				best = i;
			}
		}
		return best;
	}
}
