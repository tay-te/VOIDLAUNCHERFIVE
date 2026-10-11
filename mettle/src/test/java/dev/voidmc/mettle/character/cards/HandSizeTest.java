package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * How many cards a hand holds (docs/DRAFT.md §2), for a Blades player as the Hub's test makes one: Blades 5, then 17.
 * Below the tier II gate (skill 6) a deck can deal only its two roots, so a hand is two; from 6 it is always three, and
 * a hand dealt short is filled up when a level opens more of the deck.
 */
class HandSizeTest {
	private static final net.minecraft.resources.ResourceKey<dev.voidmc.mettle.api.Skill> BLADES = TestCards.skill("blades");

	private static List<Card> deal(TestCards.Sheet sheet, int n) {
		return CardRules.deal(BLADES, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(42L, n), List.of(), Set.of());
	}

	@Test
	void bladesFiveHasOnlyItsTwoRoots() {
		TestCards.Sheet five = new TestCards.Sheet().level("blades", 5);
		assertEquals(2, CardRules.candidates(BLADES, five, TestCards.DECK).size(), "fencer and quick draw; tier II opens at 6");
		for (int n = 0; n < 300; n++) {
			assertEquals(2, deal(five, n).size());
		}
	}

	@Test
	void bladesSeventeenAlwaysDealsThree() {
		TestCards.Sheet seventeen = new TestCards.Sheet().level("blades", 17).own("blades/fencer", 2).own("blades/quick_draw", 1);
		for (int n = 0; n < 500; n++) {
			List<Card> hand = deal(seventeen, n);
			assertEquals(3, hand.size(), "hand " + n);
			assertEquals(3, new HashSet<>(hand.stream().map(Card::perk).toList()).size(), "three different perks");
		}
	}

	@Test
	void aShortHandFillsUpWhenTheDeckOpens() {
		TestCards.Sheet sheet = new TestCards.Sheet().level("blades", 5);
		for (int n = 0; n < 100; n++) {
			List<Card> atFive = deal(sheet, n);
			assertSame(atFive, CardRules.topUp(atFive, BLADES, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(42L, 1000 + n)),
				"nothing new to deal at 5");
			TestCards.Sheet later = new TestCards.Sheet().level("blades", 17);
			List<Card> full = CardRules.topUp(atFive, BLADES, later, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(42L, 1000 + n));
			assertEquals(3, full.size());
			assertEquals(atFive, full.subList(0, 2), "the cards already shown stay where they were");
			assertEquals(3, new HashSet<>(full.stream().map(Card::perk).toList()).size());
		}
	}

	@Test
	void aMythicStaysLastWhenAHandFillsUp() {
		TestCards.Sheet sheet = new TestCards.Sheet().level("blades", 17);
		Card one = deal(new TestCards.Sheet().level("blades", 5), 0).getFirst();
		Card twin = MythicRules.offer(TestCards.id("twin_edge"));
		List<Card> full = CardRules.topUp(List.of(one, twin), BLADES, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(1L, 2));
		assertEquals(3, full.size());
		assertEquals(one, full.getFirst());
		assertEquals(Rarity.MYTHIC, full.getLast().rarity());
		assertTrue(full.get(1).rarity() != Rarity.MYTHIC);
	}
}
