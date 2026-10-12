package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.CardView;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The card api for other mods (docs/CARDS.md §25): views of cards and offers, set tier changes, the neutral stand-in. */
class CardApiTest {
	private static Optional<ResourceKey<dev.voidmc.mettle.api.Skill>> skillOf(ResourceKey<Perk> k) {
		return Optional.ofNullable(TestCards.PERKS.get(k)).map(Perk::skill);
	}

	@Test
	void aSlottedCardShowsItsDeckSlotAndRolls() {
		CardData c = TestCards.withTraits(TestCards.plain("mining/quarryman", Rarity.RARE, 2, "deep"),
			new CardData.TraitRoll(TestCards.id("mining/deepborn"), 0.09));
		CardView v = CardViews.of(c, "MN-0001-AB", true, 100);
		assertEquals(c.id(), v.id());
		assertEquals("MN-0001-AB", v.code());
		assertEquals(TestCards.skill("mining"), v.skill());
		assertEquals(c.perk(), v.perk());
		assertEquals(CardView.Rarity.RARE, v.rarity());
		assertEquals(2, v.rank());
		assertEquals(c.keywords(), v.keywords());
		assertEquals(List.of(new CardView.TraitValue(TestCards.id("mining/deepborn"), 0.09)), v.traits());
		assertTrue(v.slotted());
		assertEquals(Optional.of(TestCards.skill("mining")), v.deck());
		assertEquals(2, v.slot());
		assertTrue(v.dormant());
		assertFalse(v.attuning());
		assertEquals(Optional.of(TestCards.PLAYER), v.owner());
		assertEquals("Tester", v.drawnBy());
		assertEquals(1, v.owners());
		assertFalse(v.offer());
		assertTrue(v.mythic().isEmpty());
	}

	@Test
	void aBinderCardHasNoSlotAndIsNeverDormant() {
		CardData c = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		CardView v = CardViews.of(c, "MN-0002-AB", true, 0);
		assertEquals(CardView.Place.BINDER, v.place());
		assertTrue(v.deck().isEmpty());
		assertEquals(-1, v.slot());
		assertFalse(v.dormant(), "only a slotted card can be dormant");
	}

	@Test
	void aReceivedCardIsAttuningUntilItsTime() {
		CardData c = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		CardData received = c.withOwner(c.owner(), c.history(), 24_000L);
		assertTrue(CardViews.of(received, "", false, 23_999L).attuning());
		assertFalse(CardViews.of(received, "", false, 24_000L).attuning());
	}

	@Test
	void anOfferHasNoIdAndAWildcardKeepsItsOwnSkill() {
		DraftState.Card wild = new DraftState.Card(TestCards.perk("mining/quarryman"), 1, Rarity.COMMON, true, List.of(TestCards.id("brutal")), List.of(), 2);
		CardView v = CardViews.offer(wild, TestCards.skill("athletics"), CardApiTest::skillOf, "Tester");
		assertTrue(v.offer());
		assertEquals(0L, v.id());
		assertEquals("", v.code());
		assertTrue(v.owner().isEmpty());
		assertTrue(v.wildcard());
		assertEquals(TestCards.skill("mining"), v.skill(), "a wildcard is a card of its own skill");
		assertEquals(2, v.needs());
	}

	@Test
	void aMythicOfferNamesItsMythicAndTakesTheHandsSkill() {
		DraftState.Card m = MythicRules.offer(TestCards.id("twin_edge"));
		CardView v = CardViews.offer(m, TestCards.skill("blades"), CardApiTest::skillOf, "Tester");
		assertEquals(CardView.Rarity.MYTHIC, v.rarity());
		assertTrue(v.isMythic());
		assertEquals(Optional.of(TestCards.id("twin_edge")), v.mythic());
		assertEquals(TestCards.skill("blades"), v.skill());
		assertEquals(TestCards.id("twin_edge"), MythicRules.id(MythicRules.key(TestCards.id("twin_edge"))));
	}

	@Test
	void everyRarityAndPlaceHasAView() {
		for (Rarity r : Rarity.values()) {
			assertEquals(r.getSerializedName(), CardViews.rarity(r).id());
		}
		for (CardData.PlaceKind k : CardData.PlaceKind.values()) {
			assertEquals(k.getSerializedName(), CardViews.place(k).id());
		}
	}

	@Test
	void setTierChangesAreEveryRealChangeInKeywordOrder() {
		Identifier iron = TestCards.id("iron");
		Identifier swift = TestCards.id("swift");
		Identifier echo = TestCards.id("echo");
		List<CardViews.TierChange> changes = CardViews.tierChanges(Map.of(swift, 2, iron, 4, echo, 2), Map.of(swift, 4, iron, 4, TestCards.id("deep"), 2));
		assertEquals(List.of(
			new CardViews.TierChange(TestCards.id("deep"), 0, 2),
			new CardViews.TierChange(echo, 2, 0),
			new CardViews.TierChange(swift, 2, 4)), changes);
		assertTrue(CardViews.tierChanges(Map.of(swift, 2), Map.of(swift, 2)).isEmpty());
	}

	@Test
	void aDrawIsANewNonEmptyHand() {
		DraftState.Card a = new DraftState.Card(TestCards.perk("mining/quarryman"), 1, Rarity.COMMON, false);
		DraftState.Card b = new DraftState.Card(TestCards.perk("mining/quarryman"), 2, Rarity.RARE, false);
		assertTrue(CardViews.drawn(List.of(), List.of(a, b)));
		assertTrue(CardViews.drawn(List.of(a), List.of(a, b)), "a hand topped up is drawn again");
		assertFalse(CardViews.drawn(List.of(a, b), List.of(a, b)));
		assertFalse(CardViews.drawn(List.of(a, b), List.of()), "a kept hand going empty is no draw");
	}

	@Test
	void withoutTheCardModuleTheServiceIsNeutral() {
		var neutral = MettleServices.cards();
		assertEquals(List.of(), neutral.binder(null));
		assertEquals(Map.of(), neutral.activeSets(null));
		assertFalse(neutral.setActive(null, TestCards.id("iron"), 2));
		assertEquals(1f, neutral.cooldownScale(null));
		assertEquals(0.0, neutral.trait(null, TestCards.id("mining/deepborn")));
		assertTrue(neutral.card(null, 1).isEmpty());
	}

	@Test
	void everyCardEventIsOnMettleEvents() throws IllegalAccessException {
		Set<String> events = Arrays.stream(MettleEvents.class.getFields()).filter(f -> Event.class.isAssignableFrom(f.getType()))
			.map(Field::getName).collect(Collectors.toSet());
		for (String want : List.of("CARD_DRAWN", "CARD_KEPT", "CARD_SLOTTED", "CARD_UNSLOTTED", "CARD_FUSED", "CARD_TRADED", "MYTHIC_CLAIMED",
			"SET_TIER_CHANGED")) {
			assertTrue(events.contains(want), want + " is missing from MettleEvents");
		}
	}
}
