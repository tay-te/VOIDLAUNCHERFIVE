package dev.voidmc.hearth.npc.talk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class OptionIntentsTest {
	record O(String id, String label, boolean locked, String provider) {
	}

	private static List<String> ids(List<O> options) {
		return OptionIntents.dedupe(options, O::id, O::label, O::locked, O::provider).stream().map(O::id).toList();
	}

	@Test
	void townNewsIsAskedOnceTheBetterKept() {
		List<O> options = List.of(new O("npc:news", "What's new in town?", false, "npc"), new O("npc:rumours", "Heard any rumours?", false, "npc"),
			new O("social:whats_on", "Anything on in town?", false, "social"), new O("npc:bye", "Goodbye.", false, "npc"));
		assertEquals(List.of("npc:rumours", "social:whats_on", "npc:bye"), ids(options));
	}

	@Test
	void theFenceShopBeatsTheSellAllButOpenBeatsLocked() {
		assertEquals(List.of("economy:fence"), ids(List.of(new O("law:fence", "new home", false, "law"),
			new O("economy:fence", "quiet buyer", false, "economy"))));
		assertEquals(List.of("law:fence"), ids(List.of(new O("law:fence", "new home", false, "law"),
			new O("economy:fence", "quiet buyer", true, "economy"))));
	}

	@Test
	void theSameWordsFoldAcrossProvidersButNotWithinOne() {
		assertEquals(List.of("a:x"), ids(List.of(new O("a:x", "Any work?", false, "a"), new O("b:y", "any work", false, "b"))));
		assertEquals(List.of("q:1", "q:2"), ids(List.of(new O("q:1", "Bring logs", false, "q"), new O("q:2", "Bring logs", false, "q"))));
	}
}
