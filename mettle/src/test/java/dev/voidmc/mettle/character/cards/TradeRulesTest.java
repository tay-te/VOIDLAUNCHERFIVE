package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Trading's rules (docs/CARDS.md §8): who may meet, what may be offered, room on both sides, what a received card becomes. */
class TradeRulesTest {
	// Its own bootstrap: roomForCardsAndItems makes ItemStacks, and run alone (no TestCards loaded first) it would find the
	// registries unbound ("Components not bound yet").
	static {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		// Item components are bound when a world loads its registries (WeightDerivationTest does the same); bind them here
		// unless an earlier test in this JVM already did.
		try {
			new ItemStack(Items.STONE);
		} catch (NullPointerException unbound) {
			net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createWorldLookup())
				.forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
		}
	}

	private static final UUID A = TestCards.PLAYER;
	private static final UUID B = new UUID(3, 4);

	@Test
	void rangeCalmAndLife() {
		assertEquals(Optional.empty(), TradeRules.canMeet(A, B, 5, true, 1000, 0, 0));
		assertEquals(Optional.of(TradeRules.Refusal.TOO_FAR), TradeRules.canMeet(A, B, 8.5, true, 1000, 0, 0));
		assertEquals(Optional.of(TradeRules.Refusal.HURT), TradeRules.canMeet(A, B, 3, true, 1000, 950, 0), "hurt in the last 5 s");
		assertEquals(Optional.of(TradeRules.Refusal.DEAD), TradeRules.canMeet(A, B, 3, false, 1000, 0, 0));
		assertEquals(Optional.of(TradeRules.Refusal.SELF), TradeRules.canMeet(A, A, 0, true, 1000, 0, 0));
	}

	@Test
	void onlyAttunedBinderCardsOfYourOwn() {
		CardData binder = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		assertEquals(Optional.empty(), TradeRules.canOffer(binder, A, 0, 0));
		assertEquals(Optional.of(TradeRules.Refusal.NOT_YOURS), TradeRules.canOffer(binder, B, 0, 0));
		assertEquals(Optional.of(TradeRules.Refusal.SLOTTED), TradeRules.canOffer(TestCards.plain("mining/prospector", Rarity.COMMON, 0), A, 0, 0),
			"a slotted card is rebuilt out first");
		CardData received = TradeRules.received(binder, A, "Tester", 3, 100);
		assertEquals(Optional.of(TradeRules.Refusal.ATTUNING), TradeRules.canOffer(received, A, 100 + 23_999, 0), "attuning for a day");
		assertEquals(Optional.empty(), TradeRules.canOffer(received, A, 100 + 24_000, 0));
		assertEquals(Optional.of(TradeRules.Refusal.TOO_MANY_CARDS), TradeRules.canOffer(binder, A, 0, TradeRules.MAX_CARDS));
		CardData offered = binder.withPlace(new CardData.Place(CardData.PlaceKind.TRADE, Optional.empty(), 0, 1));
		assertEquals(Optional.of(TradeRules.Refusal.IN_TRADE), TradeRules.canOffer(offered, A, 0, 0));
	}

	@Test
	void aReceivedCardKeepsItsHistory() {
		CardData card = TestCards.plain("mining/quarryman", Rarity.EPIC, 2);
		CardData got = TradeRules.received(card, B, "Bree", 58, 1000);
		assertEquals(B, got.owner());
		assertEquals(2, got.history().owners());
		assertEquals("Bree", got.history().last().getLast().name());
		assertEquals(card.drawn(), got.drawn(), "who drew it never changes");
		assertEquals(CardData.PlaceKind.BINDER, got.place().kind());
		assertEquals(1000 + 24_000, got.attunedAt());
		assertEquals(card.needs(), got.needs(), "its requirement binds the new owner too");
		CardData passed = got;
		for (int i = 0; i < 7; i++) {
			passed = TradeRules.received(passed, i % 2 == 0 ? A : B, "P" + i, 60 + i, 2000 + i);
		}
		assertEquals(9, passed.history().owners());
		assertEquals(CardData.History.KEEP, passed.history().last().size(), "the last five holders are named");
	}

	@Test
	void roomForCardsAndItems() {
		assertTrue(TradeRules.binderRoom(298, 0, 2));
		assertFalse(TradeRules.binderRoom(299, 0, 2), "a full binder refuses trades");
		assertTrue(TradeRules.binderRoom(300, 2, 2));
		List<ItemStack> inv = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			inv.add(new ItemStack(Items.STONE, 64));
		}
		assertFalse(TradeRules.fits(inv, List.of(new ItemStack(Items.DIRT))));
		inv.set(5, new ItemStack(Items.DIRT, 60));
		assertTrue(TradeRules.fits(inv, List.of(new ItemStack(Items.DIRT, 4))), "merges into a matching stack");
		assertFalse(TradeRules.fits(inv, List.of(new ItemStack(Items.DIRT, 5))));
		inv.set(7, ItemStack.EMPTY);
		assertFalse(TradeRules.fits(inv, List.of(new ItemStack(Items.DIRT, 5), new ItemStack(Items.EMERALD, 30))),
			"one empty slot holds the leftover dirt or the emeralds, not both");
		assertEquals(60, inv.get(5).getCount(), "checking room changes nothing");
	}
}
