package dev.voidmc.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hub.model.CardPacer;
import org.junit.jupiter.api.Test;

/** The hovered-item card: immediate when the pointer rests, rate-limited while it sweeps. */
class CardPacerTest {

	static final long MS = 1_000_000L;

	@Test
	void firstHoverAndRestingAreImmediate() {
		CardPacer p = new CardPacer();
		assertTrue(p.offer("sword", 0), "first card after opening shows at once");
		assertFalse(p.offer("sword", 5 * MS), "the same card is not sent twice");
		assertTrue(p.offer("pick", 400 * MS), "after a pause, a new item shows at once");
	}

	@Test
	void sweepingIsRateLimited() {
		CardPacer p = new CardPacer();
		int sent = 0;
		// 30 wells a second for two seconds, a different item on each.
		for (int i = 0; i < 60; i++) {
			if (p.offer("item" + i, i * 33 * MS)) {
				sent++;
			}
		}
		assertTrue(sent <= 2000 / 150 + 1, "at most one card per 150 ms while sweeping, sent " + sent);
		assertTrue(sent >= 10, "but it keeps up, sent " + sent);
	}

	@Test
	void restingOnANewItemShowsItWithinTheSettleTime() {
		CardPacer p = new CardPacer();
		assertTrue(p.offer("a", 0));
		assertFalse(p.offer("b", 20 * MS), "too soon after the last card");
		assertFalse(p.offer("b", 50 * MS), "not settled yet");
		assertTrue(p.offer("b", 80 * MS), "rested 60 ms on b");
	}

	@Test
	void resetMakesTheNextCardImmediate() {
		CardPacer p = new CardPacer();
		assertTrue(p.offer("a", 0));
		p.reset();
		assertTrue(p.offer("a", 1 * MS), "a reopened screen shows its card at once, even the same one");
		assertEquals(CardPacer.SETTLE_NS, 60 * MS);
	}
}
