package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Which places the roads join (docs/LIVING_WORLD_2.md §1.8): reach, planarity, degree, classes, locality. */
class RoadNetworkTest {
	/** Places as the atlas sites them: at most one a 1024 region, in its middle 424 blocks, two in five settled. */
	static List<RoadGraph.Node> sites(long seed, int regions) {
		Random r = new Random(seed);
		List<RoadGraph.Node> out = new ArrayList<>();
		for (int rx = 0; rx < regions; rx++) {
			for (int rz = 0; rz < regions; rz++) {
				if (r.nextDouble() >= 0.4) {
					continue;
				}
				int x = rx * 1024 + 300 + r.nextInt(424);
				int z = rz * 1024 + 300 + r.nextInt(424);
				int age = (int) Math.round(400 * Math.pow(r.nextDouble(), 2.2));
				out.add(new RoadGraph.Node(1000L * rx + rz + 1, x, z, age, age >= 200));
			}
		}
		return out;
	}

	@Test
	void everyPlaceWithANeighbourInReachIsJoined() {
		RoadGraph.Rules rules = RoadGraph.Rules.DEFAULTS;
		for (long seed = 1; seed <= 12; seed++) {
			List<RoadGraph.Node> nodes = sites(seed, 12);
			List<RoadGraph.Edge> edges = RoadGraph.all(nodes, rules);
			Map<Long, Integer> degree = new HashMap<>();
			for (RoadGraph.Edge e : edges) {
				degree.merge(e.a(), 1, Integer::sum);
				degree.merge(e.b(), 1, Integer::sum);
			}
			for (RoadGraph.Node n : nodes) {
				boolean anyone = false;
				for (RoadGraph.Node m : nodes) {
					anyone |= m != n && n.distance(m) <= rules.maxLength() * 1.5;
				}
				if (anyone) {
					assertTrue(degree.getOrDefault(n.id(), 0) >= 1, "seed " + seed + ": place " + n + " has no road");
				}
				assertTrue(degree.getOrDefault(n.id(), 0) <= 4, "seed " + seed + ": place " + n + " has " + degree.get(n.id()) + " roads");
			}
		}
	}

	@Test
	void roadsNeverCross() {
		for (long seed = 1; seed <= 12; seed++) {
			List<RoadGraph.Node> nodes = sites(seed, 12);
			Map<Long, RoadGraph.Node> byId = new HashMap<>();
			for (RoadGraph.Node n : nodes) {
				byId.put(n.id(), n);
			}
			List<RoadGraph.Edge> edges = RoadGraph.all(nodes, RoadGraph.Rules.DEFAULTS);
			for (int i = 0; i < edges.size(); i++) {
				for (int j = i + 1; j < edges.size(); j++) {
					RoadGraph.Edge e = edges.get(i);
					RoadGraph.Edge f = edges.get(j);
					if (e.a() == f.a() || e.a() == f.b() || e.b() == f.a() || e.b() == f.b()) {
						continue;
					}
					assertFalse(cross(byId.get(e.a()), byId.get(e.b()), byId.get(f.a()), byId.get(f.b())), "seed " + seed + ": " + e + " crosses " + f);
				}
			}
		}
	}

	@Test
	void classesFollowTheAgeOfTheirEnds() {
		RoadGraph.Rules rules = RoadGraph.Rules.DEFAULTS;
		RoadGraph.Node old1 = new RoadGraph.Node(1, 0, 0, 300, true);
		RoadGraph.Node old2 = new RoadGraph.Node(2, 1500, 0, 250, true);
		RoadGraph.Node young = new RoadGraph.Node(3, 0, 1500, 12, false);
		RoadGraph.Node middling = new RoadGraph.Node(4, 1500, 1500, 60, false);
		RoadGraph.Edge kings = RoadGraph.edge(old1, old2, rules);
		assertEquals(RoadClass.KINGS_ROAD, kings.kind());
		assertTrue(kings.historic(), "both ends older than 30 days at day 0: built in history");
		RoadGraph.Edge track = RoadGraph.edge(old1, middling, rules);
		assertEquals(RoadClass.TRACK, track.kind());
		assertTrue(track.historic());
		RoadGraph.Edge planned = RoadGraph.edge(old1, young, rules);
		assertFalse(planned.historic(), "a place 12 days old at day 0 had no road yet: it is built live");
		// two live towns get a king's road
		RoadGraph.Edge live = RoadGraph.edge(new RoadGraph.Node(5, 0, 0, -10, true), new RoadGraph.Node(6, 900, 0, -40, true), rules);
		assertEquals(RoadClass.KINGS_ROAD, live.kind());
		assertEquals(RoadGraph.edgeId(1, 2), RoadGraph.edgeId(2, 1), "the same id from either end");
		assertTrue(RoadGraph.edgeId(1, 2) > 0 && RoadGraph.edgeId(1, 2) < 1L << 61);
	}

	@Test
	void aPlacesRoadsDependOnlyOnItsNeighbourhood() {
		// the roads of the middle places are the same whether the far corners exist or not
		List<RoadGraph.Node> all = sites(77, 14);
		List<RoadGraph.Node> middle = new ArrayList<>();
		for (RoadGraph.Node n : all) {
			if (Math.abs(n.x() - 7 * 1024) < 3500 + 5000 && Math.abs(n.z() - 7 * 1024) < 3500 + 5000) {
				middle.add(n);
			}
		}
		RoadGraph.Rules rules = RoadGraph.Rules.DEFAULTS;
		RoadGraph.Nodes allNodes = within(all);
		RoadGraph.Nodes fewer = within(middle);
		for (RoadGraph.Node n : all) {
			if (Math.abs(n.x() - 7 * 1024) < 1500 && Math.abs(n.z() - 7 * 1024) < 1500) {
				assertEquals(RoadGraph.edgesOf(n, allNodes, rules), RoadGraph.edgesOf(n, fewer, rules), "the roads of " + n);
			}
		}
	}

	static RoadGraph.Nodes within(List<RoadGraph.Node> nodes) {
		return (x, z, r) -> {
			List<RoadGraph.Node> out = new ArrayList<>();
			for (RoadGraph.Node m : nodes) {
				if (Math.hypot(m.x() - x, m.z() - z) <= r) {
					out.add(m);
				}
			}
			return out;
		};
	}

	private static boolean cross(RoadGraph.Node a, RoadGraph.Node b, RoadGraph.Node c, RoadGraph.Node d) {
		long d1 = orient(a, b, c);
		long d2 = orient(a, b, d);
		long d3 = orient(c, d, a);
		long d4 = orient(c, d, b);
		return Long.signum(d1) * Long.signum(d2) < 0 && Long.signum(d3) * Long.signum(d4) < 0;
	}

	private static long orient(RoadGraph.Node a, RoadGraph.Node b, RoadGraph.Node c) {
		return (long) (b.x() - a.x()) * (c.z() - a.z()) - (long) (b.z() - a.z()) * (c.x() - a.x());
	}
}
