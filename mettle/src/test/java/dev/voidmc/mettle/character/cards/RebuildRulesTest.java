package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Rebuilding (docs/CARDS.md §7): fees by requirement, honest scar previews, a master's hand, releasing for rerolls. */
class RebuildRulesTest {
	private static CardData withSeed(CardData c, long seed, int scars) {
		return new CardData(c.id(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), c.traits(), c.needs(), c.drawn(), c.owner(), c.history(),
			scars, seed, c.place(), c.attunedAt());
	}

	private static CardData threeTraits(long seed) {
		CardData c = TestCards.withTraits(TestCards.plain("mining/tunneler", Rarity.EPIC, 0), new TraitRoll(TestCards.id("mining/deepborn"), 9),
			new TraitRoll(TestCards.id("mining/echoing"), 8), new TraitRoll(TestCards.id("mining/lucky"), 5));
		return withSeed(c, seed, 0);
	}

	@Test
	void feesFollowTheRequirement() {
		int[][] table = {{2, 40, 1}, {5, 40, 1}, {6, 120, 3}, {11, 120, 3}, {12, 350, 8}, {17, 350, 8}, {18, 900, 20}, {20, 900, 20}};
		for (int[] row : table) {
			assertEquals(row[1], CardRules.feeBits(row[0]), "bits for a card needing " + row[0]);
			assertEquals(row[2], CardRules.feeEmeralds(row[0]), "emeralds for a card needing " + row[0]);
		}
	}

	@Test
	void theScarPreviewIsTheScarTaken() {
		for (long seed = 1; seed < 500; seed++) {
			CardData card = threeTraits(seed);
			CardRules.Scar preview = CardRules.scar(card, false);
			assertEquals(preview, CardRules.scar(card, false), "a preview never changes: cancelling and coming back shows the same trait");
			assertTrue(preview.loses().isPresent());
			CardData after = CardRules.scarred(card, false);
			assertEquals(2, after.traits().size());
			assertFalse(after.traits().contains(preview.loses().get()), "the scar takes the previewed trait");
			assertEquals(1, after.scars());
			// The next scar is decided by the new count, again shown before it happens.
			CardRules.Scar next = CardRules.scar(after, false);
			CardData twice = CardRules.scarred(after, false);
			assertFalse(twice.traits().contains(next.loses().orElseThrow()));
			assertEquals(2, twice.scars());
			assertEquals(card.keywords(), twice.keywords(), "keywords, rarity and the perk are never scarred");
			assertEquals(card.rarity(), twice.rarity());
			assertEquals(card.perk(), twice.perk());
		}
	}

	@Test
	void aBareCardGainsAMarkAndLosesNothing() {
		CardData bare = withSeed(TestCards.plain("mining/quarryman", Rarity.COMMON, 0), 9, 0);
		CardRules.Scar s = CardRules.scar(bare, false);
		assertEquals(Optional.empty(), s.loses());
		assertFalse(s.spared());
		CardData after = CardRules.scarred(bare, false);
		assertEquals(1, after.scars());
		assertTrue(after.traits().isEmpty());
	}

	@Test
	void aCityMasterScarsOneCardInTwo() {
		int spared = 0;
		int runs = 20_000;
		for (long seed = 0; seed < runs; seed++) {
			CardData card = threeTraits(seed * 7919 + 3);
			CardRules.Scar s = CardRules.scar(card, true);
			if (s.spared()) {
				spared++;
				assertEquals(card, CardRules.scarred(card, true), "a spared card keeps everything, mark included");
			} else {
				assertEquals(CardRules.scar(card, false).loses(), s.loses(), "the master's hand never picks a different trait");
			}
		}
		assertEquals(0.5, spared / (double) runs, 0.02);
	}

	@Test
	void releaseNeedsThreeBinderCardsOfOneSkill() {
		CardData a = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		CardData b = TestCards.plain("mining/prospector", Rarity.COMMON, -1);
		CardData c = TestCards.plain("mining/hard_hat", Rarity.RARE, -1);
		assertEquals(Optional.empty(), CardRules.canRelease(List.of(a, b, c), 2, 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_ENOUGH_CARDS), CardRules.canRelease(List.of(a, b), 2, 0));
		assertEquals(Optional.of(CardRules.Refusal.REROLLS_FULL), CardRules.canRelease(List.of(a, b, c), DraftRules.MAX_REROLLS, 0),
			"refused while you hold 5");
		assertEquals(Optional.of(CardRules.Refusal.MIXED_SKILLS), CardRules.canRelease(List.of(a, b, TestCards.plain("blades/fencer", Rarity.COMMON, -1)), 2, 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_BINDER), CardRules.canRelease(List.of(a, b, TestCards.plain("mining/tunneler", Rarity.RARE, 0)), 2, 0),
			"a slotted card can't be released: unslot it first");
		assertEquals(Optional.of(CardRules.Refusal.NOT_ENOUGH_CARDS), CardRules.canRelease(List.of(a, a, b), 2, 0), "three different cards");
	}
}
