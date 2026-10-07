package dev.voidmc.atlas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The name query keeps its first shape (providers built against it still link) and adds structures. */
class RegionNameQueryTest {
	@Test
	void theFirstConstructorAndAccessorsStay() throws Exception {
		RegionNameQuery q = new RegionNameQuery("minecraft:overworld", Place.LAKE, "minecraft:plains", 1, 2, 300, 7L);
		assertEquals(Place.LAKE, q.kind());
		assertNull(q.structure());
		assertFalse(q.isStructure());
		// the 7-argument constructor a provider compiled against the first API calls is still there
		RegionNameQuery.class.getConstructor(String.class, String.class, String.class, int.class, int.class, long.class, long.class);
		for (String m : new String[]{"dimension", "kind", "biome", "x", "z", "area", "seedHash"}) {
			RegionNameQuery.class.getMethod(m);
		}
	}

	@Test
	void structuresAreAskedByTheirId() {
		RegionNameQuery q = new RegionNameQuery("minecraft:overworld", Place.STRUCTURE, "minecraft:plains", 1, 2, 0, 7L, "minecraft:village");
		assertTrue(q.isStructure());
		assertEquals("minecraft:village", q.structure());
		assertEquals("atlas:structure", Place.STRUCTURE);
	}

	@Test
	void theNaturalKindsAreTheOnesProvidersSwitchOn() {
		assertEquals("atlas:region", Place.REGION);
		assertEquals("atlas:lake", Place.LAKE);
		assertEquals("atlas:river", Place.RIVER);
		assertEquals("atlas:peak", Place.PEAK);
		assertEquals("atlas:waterfall", Place.WATERFALL);
		assertEquals("atlas:cave_mouth", Place.CAVE_MOUTH);
		assertEquals("atlas:ravine", Place.RAVINE);
	}
}
