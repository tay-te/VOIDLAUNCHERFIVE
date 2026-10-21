package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Which places are joined by a road (docs/LIVING_WORLD_2.md §1.2). Pure and local: whether two places are joined depends
 * only on the places within a road's length of them, so a world generation worker can work out the roads near one
 * chunk from the sites round about (cheap and seed-only, never a history) and get the same answer as any other thread.
 *
 * <p>The rule: two places are joined when nobody else lies in the lune between them (a relative neighbourhood graph:
 * no needless triangles, no crossings, and every place keeps its nearest neighbour), they are at most {@code maxLength}
 * apart, and each keeps the other among its {@code maxDegree} nearest such neighbours. A place left with no neighbour
 * that way is joined to its nearest place within {@code 1.5 × maxLength}, if any.
 */
public final class RoadGraph {
	private RoadGraph() {
	}

	/**
	 * A place a road can end at.
	 *
	 * @param age  sim days it had lived at day 0 (live settlements founded after day 0: negative)
	 * @param town a town or a city now (for the class of a live road)
	 */
	public record Node(long id, int x, int z, int age, boolean town) {
		public double distance(Node o) {
			double dx = this.x - o.x;
			double dz = this.z - o.z;
			return Math.sqrt(dx * dx + dz * dz);
		}

		double distance2(Node o) {
			double dx = this.x - o.x;
			double dz = this.z - o.z;
			return dx * dx + dz * dz;
		}
	}

	/** A road between two places; {@code a < b}. */
	public record Edge(long id, long a, long b, RoadClass kind, boolean historic, double length) {
		public long other(long end) {
			return end == this.a ? this.b : this.a;
		}
	}

	/** The places within a radius of a point (any order; may include the point's own place). */
	@FunctionalInterface
	public interface Nodes {
		List<Node> within(int x, int z, int radius);
	}

	/**
	 * @param maxLength  the longest road between neighbours
	 * @param maxDegree  roads a place keeps at most
	 * @param kingsAge   both ends at least this old at day 0: a king's road
	 * @param builtAge   both ends at least this old at day 0: built in history (written by world generation)
	 */
	public record Rules(int maxLength, int maxDegree, int kingsAge, int builtAge) {
		public static final Rules DEFAULTS = new Rules(2400, 4, 200, 30);

		int fallbackLength() {
			return this.maxLength * 3 / 2;
		}
	}

	/** An edge id from its two ends: the same whichever end asks, never 0, below {@code 2^61}. */
	public static long edgeId(long a, long b) {
		long lo = Math.min(a, b);
		long hi = Math.max(a, b);
		long h = lo * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(hi * 0xC2B2AE3D27D4EB4FL, 31);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h &= (1L << 61) - 1;
		return h == 0 ? 1 : h;
	}

	/**
	 * The places {@code n} would join (before its neighbours have their say): those with an empty lune within
	 * {@code maxLength}, nearest first, at most {@code maxDegree}.
	 */
	public static List<Node> candidates(Node n, List<Node> nearby, Rules rules) {
		double max2 = (double) rules.maxLength() * rules.maxLength();
		List<Node> close = new ArrayList<>();
		for (Node m : nearby) {
			if (m.id != n.id && n.distance2(m) <= max2) {
				close.add(m);
			}
		}
		close.sort(Comparator.comparingDouble((Node m) -> n.distance2(m)).thenComparingLong(Node::id));
		List<Node> out = new ArrayList<>();
		for (Node m : close) {
			double d2 = n.distance2(m);
			boolean empty = true;
			for (Node c : close) {
				// only places nearer n than m can be in the lune
				if (c == m || n.distance2(c) >= d2) {
					if (n.distance2(c) >= d2) {
						break;
					}
					continue;
				}
				if (m.distance2(c) < d2) {
					empty = false;
					break;
				}
			}
			if (empty) {
				out.add(m);
				if (out.size() >= rules.maxDegree()) {
					break;
				}
			}
		}
		return out;
	}

	/** The nearest place to {@code n} within {@code radius}, or null. */
	static @Nullable Node nearest(Node n, List<Node> nearby, double radius) {
		Node best = null;
		double bestD = radius * radius;
		for (Node m : nearby) {
			if (m.id == n.id) {
				continue;
			}
			double d = n.distance2(m);
			if (d <= bestD && (best == null || d < bestD || m.id < best.id)) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	/** Every road of {@code n}: its kept neighbours that keep it, and fallbacks to or from places with none. */
	public static List<Edge> edgesOf(Node n, Nodes nodes, Rules rules) {
		List<Node> around = nodes.within(n.x, n.z, rules.fallbackLength());
		List<Node> mine = candidates(n, around, rules);
		List<Edge> out = new ArrayList<>();
		java.util.Set<Long> joined = new java.util.HashSet<>();
		for (Node m : mine) {
			List<Node> theirs = candidates(m, nodes.within(m.x, m.z, rules.maxLength()), rules);
			for (Node k : theirs) {
				if (k.id == n.id) {
					out.add(edge(n, m, rules));
					joined.add(m.id);
					break;
				}
			}
		}
		if (mine.isEmpty()) {
			Node m = nearest(n, around, rules.fallbackLength());
			if (m != null && joined.add(m.id)) {
				out.add(edge(n, m, rules));
			}
		}
		// places round about with no neighbour of their own that fall back on n
		for (Node k : around) {
			if (k.id == n.id || joined.contains(k.id)) {
				continue;
			}
			List<Node> near = nodes.within(k.x, k.z, rules.fallbackLength());
			if (candidates(k, near, rules).isEmpty()) {
				Node m = nearest(k, near, rules.fallbackLength());
				if (m != null && m.id == n.id) {
					joined.add(k.id);
					out.add(edge(n, k, rules));
				}
			}
		}
		out.sort(Comparator.comparingDouble(Edge::length).thenComparingLong(Edge::id));
		return out;
	}

	static Edge edge(Node p, Node q, Rules rules) {
		Node a = p.id < q.id ? p : q;
		Node b = a == p ? q : p;
		boolean kings = a.age >= rules.kingsAge() && b.age >= rules.kingsAge() || a.age < 0 && b.age < 0 && a.town && b.town;
		boolean historic = a.age >= rules.builtAge() && b.age >= rules.builtAge();
		return new Edge(edgeId(a.id, b.id), a.id, b.id, kings ? RoadClass.KINGS_ROAD : RoadClass.TRACK, historic, a.distance(b));
	}

	/** Every road among a set of places (tests, the dev harness, small worlds): brute force over {@link #edgesOf}. */
	public static List<Edge> all(List<Node> nodes, Rules rules) {
		Nodes all = (x, z, r) -> {
			List<Node> out = new ArrayList<>();
			double r2 = (double) r * r;
			for (Node m : nodes) {
				double dx = m.x - x;
				double dz = m.z - z;
				if (dx * dx + dz * dz <= r2) {
					out.add(m);
				}
			}
			return out;
		};
		java.util.Map<Long, Edge> edges = new java.util.TreeMap<>();
		for (Node n : nodes) {
			for (Edge e : edgesOf(n, all, rules)) {
				edges.putIfAbsent(e.id, e);
			}
		}
		return new ArrayList<>(edges.values());
	}
}
