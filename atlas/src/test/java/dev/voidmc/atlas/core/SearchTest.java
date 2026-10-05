package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Searching the map: names before the lines under them, whole names before starts before words before letters. */
class SearchTest {
	@Test
	void betterMatchesScoreHigher() {
		int exact = Search.score("forest", "Forest", "REGION");
		int prefix = Search.score("for", "Forest", "REGION");
		int word = Search.score("dark", "Deep Dark", "REGION");
		int middle = Search.score("ark", "Deep Dark", "REGION");
		int letters = Search.score("dpdk", "Deep Dark", "REGION");
		assertTrue(exact > prefix && prefix > word && word > middle && middle > letters && letters > Search.NONE,
			exact + " " + prefix + " " + word + " " + middle + " " + letters);
	}

	@Test
	void theNameCountsMoreThanTheLineUnderIt() {
		assertTrue(Search.score("village", "Village", "VILLAGE  ·  MINECRAFT") > Search.score("village", "Home", "VILLAGE NEARBY"));
		assertTrue(Search.score("death", "Home", "DEATH  ·  OCT 5") > Search.NONE, "the line under a name is searched too");
	}

	@Test
	void noMatchIsNone() {
		assertEquals(Search.NONE, Search.score("zzz", "Forest", "REGION"));
		assertEquals(Search.NONE, Search.score("x", "Forest", "REGION"), "one letter must appear as it is");
		assertTrue(Search.score("", "Forest", "") > Search.NONE, "an empty search matches everything");
	}

	@Test
	void caseAndAccentsAreIgnored() {
		assertEquals(100, Search.score("foret", "Forêt", "") / 2);
		assertTrue(Search.score("LUSH", "Lush Caves", "") > Search.NONE);
	}
}
