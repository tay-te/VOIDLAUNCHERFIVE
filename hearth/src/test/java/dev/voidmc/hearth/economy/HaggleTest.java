package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.haggle.Haggle;
import dev.voidmc.hearth.economy.haggle.Temper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HaggleTest {
	private static final EconomyConfig.Haggle CFG = EconomyConfig.Haggle.DEFAULTS;

	@Test
	void takingTheMerchantsWordClosesAtTheirPrice() {
		Haggle h = Haggle.start(true, 100, 0.15, 100, 0, CFG);
		assertEquals(Haggle.Event.DEAL, h.accept());
		assertEquals(100, h.agreed());
	}

	@Test
	void aFairOfferIsMetPartWayAndThenAccepted() {
		Haggle h = Haggle.start(true, 100, 0.15, 100, 0, CFG);
		assertEquals(Haggle.Event.COUNTER, h.offer(86), "within their room but far from their word: they counter");
		long counter = h.counter();
		assertTrue(counter < 100 && counter > 86, "met part way: " + counter);
		assertEquals(Haggle.Event.DEAL, h.offer(counter - 1), "close enough: done");
		assertTrue(h.agreed() >= 85 && h.agreed() < 100, "a bargain: " + h.agreed());
	}

	@Test
	void anOfferAboveTheirWordTakesTheirWord() {
		Haggle h = Haggle.start(true, 100, 0.15, 100, 0, CFG);
		h.offer(90);
		long counter = h.counter();
		assertEquals(Haggle.Event.DEAL, h.offer(counter + 5));
		assertEquals(counter, h.agreed(), "they never charge more than they asked");
	}

	@Test
	void theMerchantNeverGoesPastTheirLimit() {
		Haggle h = Haggle.start(true, 1000, 0.15, 1000, 0, CFG);
		for (int i = 0; i < 11 && h.state() == Haggle.State.OPEN; i++) {
			h.offer(600);
			assertTrue(h.counter() >= 850, "never below the limit: " + h.counter());
		}
	}

	@Test
	void insultsWearPatienceOutAndTheMerchantWalks() {
		Haggle h = Haggle.start(true, 100, 0.15, 100, 0, CFG);
		int before = h.patience();
		assertEquals(Haggle.Event.OFFENDED, h.offer(20));
		assertTrue(h.patience() < before - 50, "an insult costs far more than a counter: " + h.patience());
		Haggle.Event last = h.last();
		for (int i = 0; i < 5 && h.state() == Haggle.State.OPEN; i++) {
			last = h.offer(20);
		}
		assertEquals(Haggle.State.WALKED, h.state());
		assertEquals(Haggle.Event.WALKED, last);
		assertEquals(Haggle.Event.WALKED, h.accept(), "a walked merchant does not deal");
	}

	@Test
	void politeHagglingStillEndsAfterEnoughRounds() {
		Haggle h = Haggle.start(true, 10_000, 0.3, 10_000, 0, CFG);
		for (int i = 0; i < 30 && h.state() == Haggle.State.OPEN; i++) {
			h.offer(h.counter() - 400);
		}
		assertTrue(h.state() != Haggle.State.OPEN, "it ends one way or another");
	}

	@Test
	void sellingMirrorsBuyingAndIsCappedByTheirCash() {
		Haggle h = Haggle.start(false, 100, 0.15, 100, 105, CFG);
		h.offer(160);
		assertTrue(h.counter() <= 105, "a merchant cannot offer more than they hold: " + h.counter());
		assertTrue(h.counter() >= 100, "and never less than their first offer");
		Haggle h2 = Haggle.start(false, 100, 0.15, 100, 10_000, CFG);
		assertEquals(Haggle.Event.COUNTER, h2.offer(113));
		assertTrue(h2.counter() > 100 && h2.counter() < 113, "met part way upward: " + h2.counter());
	}

	@Test
	void sameOffersSameOutcome() {
		Haggle a = Haggle.start(true, 777, 0.2, 90, 0, CFG);
		Haggle b = Haggle.start(true, 777, 0.2, 90, 0, CFG);
		for (long offer : new long[]{500, 600, 650, 700}) {
			assertEquals(a.offer(offer), b.offer(offer));
			assertEquals(a.counter(), b.counter());
		}
	}

	@Test
	void temperFollowsTraitsCharismaAndStanding() {
		double plain = Temper.room(CFG, 0, 0, Set.of());
		assertEquals(CFG.room(), plain, 1e-9);
		assertTrue(Temper.room(CFG, 0, 0, Set.of("greedy")) < plain);
		assertTrue(Temper.room(CFG, 0, 0, Set.of("generous")) > plain);
		assertTrue(Temper.room(CFG, 1, 0, Set.of()) > plain, "charm widens the room");
		assertTrue(Temper.room(CFG, 0, -100, Set.of()) < plain, "distrust narrows it");
		assertTrue(Temper.room(CFG, 1, 100, Set.of("generous", "cheerful")) <= 0.6);
		assertTrue(Temper.patience(CFG, 0, Set.of("grumpy")) < Temper.patience(CFG, 0, Set.of()));
		assertTrue(Temper.patience(CFG, 80, Set.of()) > Temper.patience(CFG, -80, Set.of()), "friends are more patient");
	}
}
