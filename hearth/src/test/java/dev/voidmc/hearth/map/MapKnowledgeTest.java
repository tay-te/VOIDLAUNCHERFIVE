package dev.voidmc.hearth.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class MapKnowledgeTest {
	@Test
	void sightedThenDiscoveredNeverBack() {
		MapKnowledge k = MapKnowledge.EMPTY;
		assertSame(k, k.seen(1, 500, 24, 160), "too far to see");
		k = k.seen(1, 100, 24, 160);
		assertTrue(k.knows(1));
		assertFalse(k.discovered(1));
		MapKnowledge same = k.seen(1, 120, 24, 160);
		assertSame(k, same, "seen again: nothing changes");
		k = k.seen(1, 10, 24, 160);
		assertTrue(k.discovered(1));
		assertFalse(k.sighted().contains(1L), "discovered replaces sighted");
		assertSame(k, k.seen(1, 900, 24, 160), "walking away forgets nothing");
		assertSame(k, k.seen(1, 100, 24, 160), "never back to sighted");
	}

	@Test
	void roundTrips() {
		MapKnowledge k = MapKnowledge.EMPTY.seen(1, 100, 24, 160).seen(2, 0, 24, 160);
		var json = MapKnowledge.CODEC.encodeStart(JsonOps.INSTANCE, k).getOrThrow();
		MapKnowledge back = MapKnowledge.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(back.equals(k));
	}
}
