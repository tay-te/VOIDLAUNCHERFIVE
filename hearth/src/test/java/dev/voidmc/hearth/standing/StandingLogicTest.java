package dev.voidmc.hearth.standing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.logic.Curves;
import dev.voidmc.hearth.standing.logic.GossipRoutes;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.model.Memories;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandingLogicTest {
	private static final StandingConfig.Titles T = StandingConfig.Titles.DEFAULTS;
	private static final StandingConfig.Opinion O = StandingConfig.Opinion.DEFAULTS;

	@Test
	void titlesByThreshold() {
		assertEquals(Title.OUTLAW, Titles.of(-60, false, false, T));
		assertEquals(Title.DISTRUSTED, Titles.of(-20, false, false, T));
		assertEquals(Title.STRANGER, Titles.of(-19, false, false, T));
		assertEquals(Title.STRANGER, Titles.of(9, false, false, T));
		assertEquals(Title.KNOWN, Titles.of(10, false, false, T));
		assertEquals(Title.FRIEND, Titles.of(40, false, false, T));
		assertEquals(Title.HERO, Titles.of(80, true, false, T));
		assertEquals(Title.BURGHER, Titles.of(0, true, false, T), "owners are burghers");
		assertEquals(Title.DISTRUSTED, Titles.of(-30, true, false, T), "unless the town distrusts them");
		assertEquals(Title.OUTLAW, Titles.of(95, true, true, T), "the law's verdict wins");
	}

	@Test
	void softCapDampsOnlyAwayFromZero() {
		assertEquals(10, Curves.softened(0, 10, 0.5), 1e-9);
		assertEquals(5, Curves.softened(100, 10, 0.5), 1e-9);
		assertEquals(-10, Curves.softened(100, -10, 0.5), 1e-9, "falling from grace is never damped");
		assertEquals(-5, Curves.softened(-100, -10, 0.5), 1e-9);
		assertEquals(100, Curves.apply(98, 30, 0.5), 1e-9);
		// Many small kindnesses: a hero is made, not given.
		double v = 0;
		int deeds = 0;
		while (v < 80) {
			v = Curves.apply(v, 5, 0.5);
			deeds++;
		}
		assertTrue(deeds > 15, "deeds to hero: " + deeds);
	}

	@Test
	void roundingIsSymmetric() {
		assertEquals(-1, Curves.round(-0.5));
		assertEquals(1, Curves.round(0.5));
		assertEquals(0, Curves.round(-0.49));
		assertEquals(0.5, Curves.fade(20, 20), 1e-9);
		assertEquals(-4, Curves.drift(-5, 1, 0), 1e-9);
		assertEquals(0, Curves.drift(-0.5, 1, 0), 1e-9);
		assertEquals(5, Curves.drift(5, 1, 0), 1e-9);
	}

	@Test
	void memoriesMergeFadeAndForget() {
		UUID p = UUID.randomUUID();
		UUID q = UUID.randomUUID();
		Memories m = Memories.empty();
		m.add(new Memories.Memory(p, "law:theft", -30, 10), O);
		m.add(new Memories.Memory(p, "law:theft", -45, 10), O);
		assertEquals(1, m.size(), "the same deed reported twice is one memory");
		assertEquals(-45, m.all().getFirst().weight(), "the stronger kept");
		m.add(new Memories.Memory(p, "npc:gift", 20, 10), O);
		assertEquals(-25, m.opinion(p, 10, 0, O));
		assertEquals(-25 + 10, m.opinion(p, 10, 40, O), "the town's view colours it");
		// Fading: the gift (20) halves in 20 days; the theft (45 >= 40, strong) fades four times slower.
		int later = m.opinion(p, 30, 0, O);
		assertEquals(Curves.round(-45 * Math.pow(0.5, 0.25) + 10), later);
		for (int i = 0; i < 30; i++) {
			m.add(new Memories.Memory(q, "k" + i, i % 2 == 0 ? 3 : -3, 30), O);
		}
		assertTrue(m.size() <= O.memories());
		assertTrue(m.all().stream().anyMatch(x -> x.weight() == -45), "the strongest memory survives a crowd of small ones");
		assertEquals("law:theft", m.strongest(p, 30, O).kind());
	}

	@Test
	void gossipFollowsTheRoads() {
		GossipRoutes.Node a = new GossipRoutes.Node(1, 0, 0);
		List<GossipRoutes.Node> towns = List.of(new GossipRoutes.Node(2, 900, 0), new GossipRoutes.Node(3, 1800, 0), new GossipRoutes.Node(4, 0, 2900),
			new GossipRoutes.Node(5, 0, 1000));
		List<GossipRoutes.Route> routes = GossipRoutes.routes(a, towns, 3000, 1400, 4, 0.85, 1500, 1);
		GossipRoutes.Route b = find(routes, 2);
		GossipRoutes.Route c = find(routes, 3);
		assertEquals(1, b.hops());
		assertEquals(2, c.hops(), "the far town hears it through its neighbour");
		assertTrue(c.factor() < b.factor());
		assertTrue(c.delay() > b.delay());
		assertEquals(1, b.delay());
		assertTrue(routes.stream().noneMatch(r -> r.id() == 4), "beyond any road's reach: 1900 from its nearest town");
		assertTrue(routes.stream().noneMatch(r -> r.id() == 1), "the origin is not a destination");
		assertTrue(GossipRoutes.routes(a, List.of(), 3000, 1400, 4, 0.85, 1500, 1).isEmpty());
	}

	private static GossipRoutes.Route find(List<GossipRoutes.Route> routes, long id) {
		return routes.stream().filter(r -> r.id() == id).findFirst().orElseThrow(() -> new AssertionError("no route to " + id + ": " + routes));
	}
}
