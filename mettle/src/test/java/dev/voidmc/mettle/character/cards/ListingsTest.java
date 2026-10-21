package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Hearth card broker's card moves (docs/CARDS.md §8.2, Hearth's request 7; {@link Listings}): what may be listed,
 * escrow, one sale per listing with attunement for the buyer, release only of a listed card, the lapse, and the ledger on
 * disk through a crash between Hearth's payment and the transfer (and one after it). Two players: Ashford sells, Bree buys.
 */
class ListingsTest {
	private static final UUID ASHFORD = TestCards.PLAYER;
	private static final UUID BREE = new UUID(3, 4);
	private static final int MAX = 300;
	private static final long DAY = 10;
	private static final long NOW = 240_000;

	private static CardLedger ledgerWith(CardData... cards) {
		CardLedger l = new CardLedger();
		for (CardData c : cards) {
			l.add(c);
		}
		return l;
	}

	@Test
	void onlyAttunedBinderCardsOfYourOwnAreListable() {
		CardData binder = TestCards.plain("mining/quarryman", Rarity.RARE, -1);
		CardData slotted = TestCards.plain("mining/prospector", Rarity.COMMON, 0);
		CardData attuning = TradeRules.received(TestCards.plain("mining/tunneler", Rarity.COMMON, -1), ASHFORD, "Ashford", DAY, NOW);
		CardData theirs = TradeRules.received(TestCards.plain("blades/fencer", Rarity.COMMON, -1), BREE, "Bree", 0, 0);
		CardLedger l = ledgerWith(binder, slotted, attuning, theirs);
		assertEquals(List.of(binder.id()), Listings.listable(l, ASHFORD, NOW), "not slotted, not attuning, not someone else's");
		assertEquals(Optional.of(TradeRules.Refusal.ATTUNING), Listings.canList(attuning, ASHFORD, NOW));
		assertEquals(Optional.of(TradeRules.Refusal.NOT_YOURS), Listings.canList(theirs, ASHFORD, NOW));
		assertFalse(Listings.hold(l, slotted.id(), ASHFORD, 1, DAY, NOW), "a slotted card is rebuilt out first");
		assertFalse(Listings.hold(l, theirs.id(), ASHFORD, 1, DAY, NOW), "nobody lists another's card");
		assertFalse(Listings.hold(l, 9_999_999, ASHFORD, 1, DAY, NOW), "an unknown id");
	}

	@Test
	void escrowThenOneSaleWithAttunement() {
		CardData card = TestCards.plain("mining/quarryman", Rarity.EPIC, -1);
		CardLedger l = ledgerWith(card);
		assertTrue(Listings.hold(l, card.id(), ASHFORD, 41, DAY, NOW));
		CardData listed = l.get(card.id()).orElseThrow();
		assertEquals(CardData.PlaceKind.LISTED, listed.place().kind());
		assertEquals(41, listed.place().ref(), "the listing's id");
		assertEquals(DAY, listed.place().index(), "the day it went up");
		assertFalse(Listings.hold(l, card.id(), ASHFORD, 42, DAY, NOW), "never listed twice");
		assertEquals(Optional.of(TradeRules.Refusal.IN_TRADE), TradeRules.canOffer(listed, ASHFORD, NOW, 0), "a listed card can't be traded");
		assertTrue(CardRules.canSlot(listed, -1, List.of(), 20, 3, NOW, k -> Optional.ofNullable(TestCards.PERKS.get(k)), List.of(), 0).isPresent(),
			"a listed card can't be slotted");
		assertEquals(List.of(), Listings.listable(l, ASHFORD, NOW));

		assertFalse(Listings.transfer(l, card.id(), ASHFORD, "Ashford", 0, MAX, DAY, NOW), "the seller can't buy their own card");
		assertTrue(Listings.transfer(l, card.id(), BREE, "Bree", 12, MAX, DAY + 1, NOW + 100));
		CardData sold = l.get(card.id()).orElseThrow();
		assertEquals(BREE, sold.owner());
		assertEquals(CardData.PlaceKind.BINDER, sold.place().kind());
		assertFalse(sold.attuned(NOW + 100 + 23_999), "attuning for a day after the purchase");
		assertTrue(sold.attuned(NOW + 100 + 24_000));
		assertEquals("Bree", sold.history().last().getLast().name());
		assertEquals(2, sold.history().owners());
		assertEquals(card.drawn(), sold.drawn(), "who drew it never changes");
		assertEquals(List.of(), l.owned(ASHFORD), "gone from the seller");
		assertEquals(List.of(sold), l.owned(BREE));

		assertFalse(Listings.transfer(l, card.id(), new UUID(5, 6), "Cade", 0, MAX, DAY + 1, NOW + 100), "sold once only");
		assertNull(Listings.release(l, card.id()), "a stale listing never takes a sold card back");
		assertEquals(BREE, l.get(card.id()).orElseThrow().owner());
	}

	@Test
	void withdrawnAndRefusedSales() {
		CardData card = TestCards.plain("blades/fencer", Rarity.COMMON, -1);
		CardData mythic = mythic();
		CardLedger l = ledgerWith(card, mythic);
		assertTrue(Listings.hold(l, card.id(), ASHFORD, 1, DAY, NOW));
		assertFalse(Listings.transfer(l, card.id(), BREE, "Bree", MAX, MAX, DAY, NOW), "the buyer's binder is full: the sale is off");
		assertEquals(ASHFORD, l.get(card.id()).orElseThrow().owner(), "still the seller's, still listed");
		assertEquals(ASHFORD, Listings.release(l, card.id()), "withdrawn");
		assertEquals(CardData.PlaceKind.BINDER, l.get(card.id()).orElseThrow().place().kind());
		assertNull(Listings.release(l, card.id()), "released once");
		assertTrue(l.get(card.id()).orElseThrow().attuned(NOW), "a withdrawn card isn't attuning: it never changed hands");
		// A Mythic always fits, even a full binder.
		assertTrue(Listings.hold(l, mythic.id(), ASHFORD, 2, DAY, NOW));
		assertTrue(Listings.transfer(l, mythic.id(), BREE, "Bree", MAX, MAX, DAY, NOW));
	}

	@Test
	void listingsHearthNeverClosedLapse() {
		CardData a = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		CardData b = TestCards.plain("mining/prospector", Rarity.COMMON, -1);
		CardLedger l = ledgerWith(a, b);
		assertTrue(Listings.hold(l, a.id(), ASHFORD, 1, DAY, NOW));
		assertTrue(Listings.hold(l, b.id(), ASHFORD, 2, DAY + 10, NOW));
		assertEquals(List.of(), Listings.lapse(l, DAY + Listings.LAPSE_DAYS - 1));
		List<CardData> back = Listings.lapse(l, DAY + Listings.LAPSE_DAYS);
		assertEquals(List.of(a.id()), back.stream().map(CardData::id).toList());
		assertEquals(CardData.PlaceKind.BINDER, l.get(a.id()).orElseThrow().place().kind());
		assertEquals(CardData.PlaceKind.LISTED, l.get(b.id()).orElseThrow().place().kind(), "listed later: still on the board");
	}

	/**
	 * Hearth charges the buyer, then asks Mettle to transfer; each side saves on its own. A crash between the two leaves
	 * the ledger on disk as before (listed, the seller's): the sale simply didn't happen and can happen once after the
	 * restart. A crash after the transfer (committed at once) leaves it sold even if Hearth's board still shows the
	 * listing: a second buy is refused, so Hearth refunds, and its release changes nothing.
	 */
	@Test
	void aCrashBetweenPaymentAndTransfer(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("cards.dat");
		CardData card = TestCards.plain("mining/quarryman", Rarity.RARE, -1);
		CardData other = TestCards.plain("blades/fencer", Rarity.RARE, -1);
		CardLedger l = ledgerWith(card, other);
		assertTrue(Listings.hold(l, card.id(), ASHFORD, 7, DAY, NOW));
		assertTrue(Listings.hold(l, other.id(), ASHFORD, 8, DAY, NOW));
		LedgerFile.write(l, file); // hold commits at once

		// Bree pays for listing 7 ... and the server dies before the transfer.
		CardLedger restarted = LedgerFile.load(file);
		CardData after = restarted.get(card.id()).orElseThrow();
		assertEquals(ASHFORD, after.owner(), "not sold: the sale never happened");
		assertEquals(CardData.PlaceKind.LISTED, after.place().kind(), "still on the board");
		assertTrue(Listings.transfer(restarted, card.id(), BREE, "Bree", 0, MAX, DAY, NOW), "bought again after the restart: once");
		assertFalse(Listings.transfer(restarted, card.id(), BREE, "Bree", 0, MAX, DAY, NOW));

		// Listing 8 is sold and committed, then the server dies before Hearth saves its board.
		assertTrue(Listings.transfer(restarted, other.id(), BREE, "Bree", 1, MAX, DAY, NOW));
		LedgerFile.write(restarted, file);
		CardLedger again = LedgerFile.load(file);
		assertEquals(BREE, again.get(other.id()).orElseThrow().owner(), "the sale survived the crash");
		assertFalse(Listings.transfer(again, other.id(), new UUID(5, 6), "Cade", 0, MAX, DAY, NOW), "the stale listing can't sell it twice");
		assertNull(Listings.release(again, other.id()), "and its lapse or withdrawal can't take it back");
		assertEquals(BREE, again.get(other.id()).orElseThrow().owner());
		assertEquals(2, again.owned(BREE).size());
		assertEquals(0, again.owned(ASHFORD).size(), "no card in two places, none lost");
	}

	/** A Mythic card (from the shipped Mythics: the first drawn one of Mining), Ashford's. */
	private static CardData mythic() {
		CardData plain = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		return new CardData(plain.id(), plain.skill(), plain.perk(), Rarity.MYTHIC, 1, List.of(), List.of(), 0, plain.drawn(), plain.owner(), plain.history(),
			0, 0, CardData.Place.BINDER, 0L);
	}
}
