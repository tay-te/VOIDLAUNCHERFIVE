package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The smaller audit findings: the binder's count (X9), the config's ceiling (N1) and the client's copy of it (M1). */
class AuditFixesTest {
	static {
		TestCards.DECK.toString();
	}

	@AfterEach
	void reset() {
		CardConfig.forgetServer();
		CardConfig.reset();
	}

	/** X9: Mythics always fit: the binder's count leaves them out, so a full binder never refuses one. */
	@Test
	void mythicsDontCountAgainstTheBinder() {
		List<CardData> cards = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			cards.add(TestCards.plain("mining/quarryman", Rarity.COMMON, -1));
		}
		CardData plain = cards.getFirst();
		cards.add(new CardData(999_999, plain.skill(), MythicRules.key(TestCards.id("twin_edge")), Rarity.MYTHIC, 1, List.of(), List.of(), 0,
			plain.drawn(), plain.owner(), plain.history(), 0, 1, CardData.Place.BINDER, 0));
		assertEquals(4, CardRules.binderCount(cards));
	}

	/** N1: binder_max can't go past the ceiling the one-attachment sync can carry; out-of-range values are set back. */
	@Test
	void binderMaxIsCapped() {
		CardConfig c = new CardConfig();
		c.binder_max = 50_000;
		c.slots_max = -2;
		c.wits_slots = new int[] {8};
		c.validated();
		assertEquals(CardConfig.BINDER_CEILING, c.binder_max);
		assertEquals(1, c.slots_max);
		assertEquals(2, c.wits_slots.length, "a broken array goes back to the default");
	}

	/** M1: the server's config, once received, is what the client thread reads; other threads keep the server's own. */
	@Test
	void theClientReadsTheServersConfig() throws Exception {
		CardConfig server = new CardConfig();
		server.binder_max = 280;
		CardConfig.use(server);
		String wire = CardConfig.wire();
		CardConfig.use(new CardConfig());
		AtomicReference<Integer> seen = new AtomicReference<>();
		Thread client = new Thread(() -> {
			CardConfig.receive(wire);
			seen.set(CardConfig.get().binder_max);
		});
		client.start();
		client.join();
		assertEquals(280, seen.get(), "the client thread sees the server's 280");
		assertEquals(300, CardConfig.get().binder_max, "this thread (a server) keeps its own");
		long before = CardConfig.clientRevision();
		CardConfig.forgetServer();
		assertEquals(before + 1, CardConfig.clientRevision());
		assertSame(CardConfig.get(), CardConfig.get());
	}
}
