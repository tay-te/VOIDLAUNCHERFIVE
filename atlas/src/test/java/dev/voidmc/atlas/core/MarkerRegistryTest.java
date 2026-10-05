package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.AtlasMarkers;
import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.MarkerQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The marker-provider API: registration, ordering, replacement, and a provider that throws never breaking the map. */
class MarkerRegistryTest {
	private static final MarkerQuery Q = new MarkerQuery("minecraft:overworld", -100, -100, 100, 100, 0, 0, MarkerQuery.Purpose.MAP);

	private static Marker m(String id) {
		return Marker.of(id, MapLayer.STRUCTURES, id, 0, 64, 0, "minecraft:overworld", MarkerGlyph.TOWER);
	}

	private static List<String> ids(List<Marker> markers) {
		return markers.stream().map(Marker::id).toList();
	}

	@Test
	void providersAreAskedInPriorityThenIdOrder() {
		MarkerRegistry r = new MarkerRegistry(s -> {
		});
		r.registerProvider("b:second", 5, (q, out) -> out.accept(m("b")));
		r.registerProvider("a:first", 5, (q, out) -> out.accept(m("a")));
		r.registerProvider("z:early", -10, (q, out) -> out.accept(m("z")));
		r.registerProvider("c:late", 100, (q, out) -> {
			out.accept(m("c1"));
			out.accept(m("c2"));
		});
		assertEquals(List.of("z", "a", "b", "c1", "c2"), ids(r.collect(Q)));
	}

	@Test
	void registeringTheSameIdReplacesAndUnregisterRemoves() {
		MarkerRegistry r = new MarkerRegistry(s -> {
		});
		r.registerProvider("hearth:settlements", 0, (q, out) -> out.accept(m("old")));
		r.registerProvider("hearth:settlements", 0, (q, out) -> out.accept(m("new")));
		assertEquals(List.of("new"), ids(r.collect(Q)));
		r.unregisterProvider("hearth:settlements");
		assertTrue(r.collect(Q).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> r.registerProvider(null, 0, (q, out) -> {
		}));
	}

	@Test
	void aThrowingProviderLosesOnlyItsOwnMarkersAndIsSwitchedOffAfterThreeFailures() {
		List<String> warnings = new ArrayList<>();
		MarkerRegistry r = new MarkerRegistry(warnings::add);
		AtomicInteger calls = new AtomicInteger();
		r.registerProvider("ok:before", 0, (q, out) -> out.accept(m("before")));
		r.registerProvider("broken:mod", 1, (q, out) -> {
			calls.incrementAndGet();
			out.accept(m("half"));
			throw new IllegalStateException("boom");
		});
		r.registerProvider("ok:after", 2, (q, out) -> out.accept(m("after")));
		for (int i = 0; i < AtlasMarkers.MAX_FAILURES; i++) {
			assertEquals(List.of("before", "after"), ids(r.collect(Q)), "the broken provider's half-made markers are dropped");
		}
		assertEquals(1, warnings.size(), "reported once, when switched off");
		assertTrue(warnings.get(0).contains("broken:mod"));
		r.collect(Q);
		r.collect(Q);
		assertEquals(AtlasMarkers.MAX_FAILURES, calls.get(), "not called again once switched off");
		assertTrue(r.providers().contains("broken:mod (off)"));
	}

	@Test
	void aProviderThatRecoversIsNotSwitchedOff() {
		List<String> warnings = new ArrayList<>();
		MarkerRegistry r = new MarkerRegistry(warnings::add);
		AtomicInteger n = new AtomicInteger();
		r.registerProvider("flaky:mod", 0, (q, out) -> {
			if (n.incrementAndGet() % 2 == 0) {
				throw new RuntimeException("every other frame");
			}
			out.accept(m("ok"));
		});
		for (int i = 0; i < 20; i++) {
			r.collect(Q);
		}
		assertTrue(warnings.isEmpty(), "failures must be consecutive to switch a provider off");
		assertEquals(20, n.get());
	}

	@Test
	void errorsAreFencedOffToo() {
		MarkerRegistry r = new MarkerRegistry(s -> {
		});
		r.registerProvider("oom:mod", 0, (q, out) -> {
			throw new StackOverflowError();
		});
		r.registerProvider("ok:mod", 1, (q, out) -> out.accept(m("fine")));
		assertEquals(List.of("fine"), ids(r.collect(Q)));
	}

	@Test
	void nullMarkersAreIgnored() {
		MarkerRegistry r = new MarkerRegistry(s -> {
		});
		r.registerProvider("sloppy:mod", 0, (q, out) -> {
			out.accept(null);
			out.accept(m("real"));
		});
		assertEquals(List.of("real"), ids(r.collect(Q)));
	}

	@Test
	void layersAreOrderedAndReplacedById() {
		MarkerRegistry r = new MarkerRegistry(s -> {
		});
		r.registerLayer(new MapLayer(MapLayer.WAYPOINTS, "Waypoints", true, 20));
		r.registerLayer(new MapLayer(MapLayer.TERRAIN, "Terrain", true, 0));
		r.registerLayer(new MapLayer("hearth:settlements", "Settlements", true, 15));
		r.registerLayer(new MapLayer("hearth:settlements", "Towns", false, 15));
		assertEquals(List.of("Terrain", "Towns", "Waypoints"), r.layers().stream().map(MapLayer::label).toList());
	}

	@Test
	void markersFillInDefaults() {
		Marker bare = new Marker("x", null, null, null, null, 1, 2, 3, null, null, null, 0, null);
		assertEquals(MapLayer.STRUCTURES, bare.layer());
		assertEquals("minecraft:overworld", bare.dimension());
		assertEquals(MarkerGlyph.DOT, bare.glyph());
		assertEquals(Marker.Style.STRUCTURE, bare.style());
		assertEquals("", bare.name());
		assertEquals(5.0, Marker.of("y", null, "", 3, 0, 4, null, null).distanceTo(0, 0), 1e-9);
	}

	@Test
	void glyphsAreFiveByFive() {
		for (MarkerGlyph g : MarkerGlyph.values()) {
			assertTrue(g.mask != 0 && (g.mask >>> 25) == 0, g + " fits 5 x 5");
			assertEquals(0, g.accent & ~g.mask, g + " accents only lit cells");
		}
		assertTrue(MarkerGlyph.PIN.cell(2, 4));
		assertTrue(!MarkerGlyph.PIN.cell(0, 0));
	}
}
