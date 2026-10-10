package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Fusion (docs/CARDS.md §18.1, rulings 2–4): three copies of a perk into one of the next rarity, the best trait kept. */
class FusionRulesTest {
	private static CardData copy(Rarity rarity, int index, TraitRoll... traits) {
		return TestCards.withTraits(TestCards.plain("mining/tunneler", rarity, index), traits);
	}

	@Test
	void threeCopiesOfOnePerkInTheBinder() {
		CardData a = copy(Rarity.COMMON, -1);
		CardData b = copy(Rarity.COMMON, -1);
		CardData c = copy(Rarity.COMMON, -1);
		assertEquals(Optional.empty(), CardRules.canFuse(List.of(a, b, c), 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_ENOUGH_CARDS), CardRules.canFuse(List.of(a, b), 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_ONE_PERK), CardRules.canFuse(List.of(a, b, TestCards.plain("mining/quarryman", Rarity.COMMON, -1)), 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_ONE_PERK), CardRules.canFuse(List.of(a, b, copy(Rarity.RARE, -1)), 0), "one rarity");
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_BINDER), CardRules.canFuse(List.of(a, b, copy(Rarity.COMMON, 0)), 0),
			"a slotted card is unslotted first");
		assertEquals(Optional.of(CardRules.Refusal.TOP_RARITY), CardRules.canFuse(List.of(copy(Rarity.EPIC, -1), copy(Rarity.EPIC, -1), copy(Rarity.EPIC, -1)), 0),
			"Epic triples don't fuse");
		assertEquals(Optional.of(Rarity.RARE), CardRules.fusesInto(Rarity.COMMON));
		assertEquals(Optional.of(Rarity.EPIC), CardRules.fusesInto(Rarity.RARE));
		assertEquals(Optional.empty(), CardRules.fusesInto(Rarity.CURSED));
	}

	@Test
	void theNextRarityWithTheBestTraitKept() {
		// Lucky 8 % is 8/10 of its cap; Deepborn 6 % only 6/15: Lucky is kept, at 8.
		CardData a = copy(Rarity.RARE, -1, new TraitRoll(TestCards.id("mining/deepborn"), 6));
		CardData b = copy(Rarity.RARE, -1, new TraitRoll(TestCards.id("mining/lucky"), 8));
		CardData c = copy(Rarity.RARE, -1);
		assertEquals(new TraitRoll(TestCards.id("mining/lucky"), 8), CardRules.bestTrait(List.of(a, b, c), TestCards.DATA_SET).orElseThrow());
		for (long seed = 0; seed < 300; seed++) {
			Card fused = CardRules.fuse(List.of(a, b, c), TestCards.def(TestCards.perk("mining/tunneler")), TestCards.DATA_SET, new Random(seed));
			assertEquals(Rarity.EPIC, fused.rarity());
			assertEquals(CardRules.needs(TestCards.def(TestCards.perk("mining/tunneler")), Rarity.EPIC), fused.needs());
			assertTrue(fused.traits().contains(new TraitRoll(TestCards.id("mining/lucky"), 8)), "the best trait is kept at its rolled value: " + fused.traits());
			assertTrue(fused.traits().size() >= 2 && fused.traits().size() <= 3, "an Epic's trait count");
			assertEquals(fused.traits().size(), fused.traits().stream().map(TraitRoll::trait).distinct().count(), "no trait twice");
		}
		assertEquals(CardRules.fuse(List.of(a, b, c), TestCards.def(TestCards.perk("mining/tunneler")), TestCards.DATA_SET, new Random(5)),
			CardRules.fuse(List.of(a, b, c), TestCards.def(TestCards.perk("mining/tunneler")), TestCards.DATA_SET, new Random(5)), "fusing is deterministic");
	}

	@Test
	void theFeeIsTheNewCardsRebuildFee() {
		Card fused = CardRules.fuse(List.of(copy(Rarity.COMMON, -1), copy(Rarity.COMMON, -1), copy(Rarity.COMMON, -1)),
			TestCards.def(TestCards.perk("mining/tunneler")), TestCards.DATA_SET, new Random(1));
		assertEquals(7, fused.needs(), "a Rare tier II card needs 7");
		assertEquals(120, CardRules.feeBits(fused.needs()));
	}
}
