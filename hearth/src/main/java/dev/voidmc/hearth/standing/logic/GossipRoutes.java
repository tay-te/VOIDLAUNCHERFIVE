package dev.voidmc.hearth.standing.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * How news of a deed travels (docs/LIVING_WORLD.md §3): from the town where it happened along the roads to its
 * neighbours and on through theirs, arriving days later and fainter with every league and every town it passes through.
 *
 * <p>The road network is approximated from where the towns are: each town is linked to its few nearest neighbours within
 * a day or so of travel (the roads the planner and the hamlets lay between them follow the same pattern), and news takes
 * the shortest path through that network. With a few dozen towns in range this is a tiny Dijkstra, run once per deed
 * that is worth talking about. Pure: no game state.
 */
public final class GossipRoutes {
	private GossipRoutes() {
	}

	/** A town news can reach. */
	public record Node(long id, int x, int z) {
	}

	/**
	 * Where the news arrives.
	 *
	 * @param distance blocks along the roads
	 * @param hops     towns on the way, counting the destination (1 = a direct neighbour)
	 * @param factor   how much of the news is left on arrival, 0…1
	 * @param delay    days on the road
	 */
	public record Route(long id, double distance, int hops, double factor, int delay) {
	}

	/**
	 * @param radius       blocks at which news has faded to nothing
	 * @param linkDistance the longest road between neighbours
	 * @param neighbours   roads per town (to its nearest)
	 * @param hopFade      what is left after each town passed through
	 * @param blocksPerDay how far news travels a day
	 * @param minDelay     the soonest news arrives
	 */
	public static List<Route> routes(Node origin, List<Node> towns, int radius, int linkDistance, int neighbours, double hopFade, int blocksPerDay,
		int minDelay) {
		List<Node> nodes = new ArrayList<>(towns.size() + 1);
		nodes.add(origin);
		for (Node n : towns) {
			if (n.id != origin.id) {
				nodes.add(n);
			}
		}
		int n = nodes.size();
		if (n < 2) {
			return List.of();
		}
		// Undirected k-nearest links within the road length.
		boolean[][] link = new boolean[n][n];
		double[][] d = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				double dx = nodes.get(i).x - nodes.get(j).x;
				double dz = nodes.get(i).z - nodes.get(j).z;
				d[i][j] = d[j][i] = Math.sqrt(dx * dx + dz * dz);
			}
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			final int from = i;
			for (int j = 0; j < n; j++) {
				order[j] = j;
			}
			Arrays.sort(order, (a, b) -> Double.compare(d[from][a], d[from][b]));
			int taken = 0;
			for (int k = 0; k < n && taken < neighbours; k++) {
				int j = order[k];
				if (j == i) {
					continue;
				}
				if (d[i][j] > linkDistance) {
					break;
				}
				link[i][j] = link[j][i] = true;
				taken++;
			}
		}
		// Roads wind: a road between towns is longer than the crow flies.
		double winding = 1.25;
		double[] dist = new double[n];
		int[] hops = new int[n];
		Arrays.fill(dist, Double.POSITIVE_INFINITY);
		dist[0] = 0;
		PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		queue.add(new double[] {0, 0});
		while (!queue.isEmpty()) {
			double[] top = queue.poll();
			int u = (int) top[1];
			if (top[0] > dist[u]) {
				continue;
			}
			for (int v = 0; v < n; v++) {
				if (!link[u][v]) {
					continue;
				}
				double nd = dist[u] + d[u][v] * winding;
				if (nd < dist[v] && nd < radius) {
					dist[v] = nd;
					hops[v] = hops[u] + 1;
					queue.add(new double[] {nd, v});
				}
			}
		}
		List<Route> out = new ArrayList<>();
		for (int i = 1; i < n; i++) {
			if (Double.isInfinite(dist[i])) {
				continue;
			}
			double factor = (1 - dist[i] / radius) * Math.pow(hopFade, hops[i] - 1);
			if (factor <= 0) {
				continue;
			}
			int delay = Math.max(minDelay, (int) Math.ceil(dist[i] / blocksPerDay));
			out.add(new Route(nodes.get(i).id, dist[i], hops[i], factor, delay));
		}
		out.sort((a, b) -> Double.compare(a.distance, b.distance));
		return out;
	}
}
