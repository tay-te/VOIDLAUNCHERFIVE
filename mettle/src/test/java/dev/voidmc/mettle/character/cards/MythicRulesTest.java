package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Mythics (docs/CARDS.md §9): the twenty, their limits in a deck, claims once per world, numbered deeds, the draw, credit. */
class MythicRulesTest {
	private static final Map<Identifier, Mythic> MYTHICS = TestCardsMythics.load();

	private static Optional<Perk> perks(net.minecraft.resources.ResourceKey<Perk> k) {
		return Optional.ofNullable(TestCards.PERKS.get(k));
	}

	private static CardData mythic(String id, int index, String deck) {
		DraftState.Card offer = new DraftState.Card(MythicRules.key(TestCards.id(id)), 1, Rarity.MYTHIC, false, List.of(), List.of(), 0);
		CardData c = TestCards.card(new DraftState.Card(TestCards.perk("mining/quarryman"), 1, Rarity.COMMON, false, List.of(TestCards.id("brutal")), List.of(), 2), -1);
		CardData.Place place = index >= 0 ? CardData.Place.slotted(TestCards.skill(deck), index) : CardData.Place.BINDER;
		return new CardData(c.id(), TestCards.skill(deck), offer.perk(), Rarity.MYTHIC, 1, List.of(), List.of(), 0, c.drawn(), c.owner(), c.history(), 0, 1,
			place, 0);
	}

	@Test
	void theTwentyAreThere() {
		assertEquals(20, MYTHICS.size());
		assertEquals(7, MYTHICS.values().stream().filter(m -> m.source() == Mythic.Source.WORLD_FIRST).count());
		assertEquals(5, MYTHICS.values().stream().filter(m -> m.source() == Mythic.Source.DEED).count());
		assertEquals(8, MYTHICS.values().stream().filter(m -> m.source() == Mythic.Source.DRAWN).count());
		for (String skill : TestCards.RELEASED) {
			assertEquals(1, MYTHICS.values().stream().filter(m -> m.skill().map(TestCards.skill(skill)::equals).orElse(false)).count(),
				skill + " has one drawn Mythic");
		}
	}

	@Test
	void twoSlottedNeverTwoInADeck() {
		CardData hush = mythic("wardens_hush", -1, "mining");
		CardData twin = mythic("twin_edge", 0, "blades");
		CardData aegis = mythic("aegis", 0, "defense");
		assertEquals(Optional.of(CardRules.Refusal.ONE_MYTHIC_A_DECK),
			CardRules.canSlot(hush, -1, List.of(twin), 10, 3, 0, MythicRulesTest::perks, List.of(twin), 0), "never two in one deck");
		assertEquals(Optional.of(CardRules.Refusal.TWO_MYTHICS),
			CardRules.canSlot(hush, -1, List.of(), 10, 3, 0, MythicRulesTest::perks, List.of(twin, aegis), 0), "at most two slotted");
		assertEquals(Optional.empty(), CardRules.canSlot(hush, -1, List.of(), 10, 3, 0, MythicRulesTest::perks, List.of(twin), 0),
			"a Mythic fits any deck");
		assertTrue(hush.mythic());
		assertEquals(TestCards.id("wardens_hush"), hush.mythicId().orElseThrow());
	}

	@Test
	void mythicsAreNeverScarredOrReleasedAndCarryNoSet() {
		CardData twin = mythic("twin_edge", 0, "blades");
		assertTrue(CardRules.scar(twin, false).spared());
		assertEquals(twin, CardRules.scarred(twin, false));
		CardData a = TestCards.plain("blades/fencer", Rarity.COMMON, -1);
		CardData b = TestCards.plain("blades/measure", Rarity.COMMON, -1);
		assertEquals(Optional.of(CardRules.Refusal.MYTHIC), CardRules.canRelease(List.of(a, b, mythic("aegis", -1, "blades")), 0, 0));
		CardRules.Derived d = CardRules.derive(List.of(twin), s -> 20, 3, x -> 10, MythicRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertTrue(d.sets().isEmpty(), "a Mythic carries no keywords");
		assertTrue(d.active().containsKey(twin.perk()), "a slotted Mythic is active");
	}

	@Test
	void polymathPaysForItsOwnSlot() {
		CardData poly = mythic("polymath", 0, "mining");
		List<CardData> deck = new java.util.ArrayList<>(List.of(poly));
		String[] perks = {"mining/quarryman", "mining/prospector", "mining/hard_hat", "mining/tunneler"};
		for (int i = 0; i < perks.length; i++) {
			deck.add(TestCards.plain(perks[i], Rarity.COMMON, i + 1));
		}
		assertEquals(1, CardRules.extraSlots(deck));
		assertTrue(CardRules.derive(deck, s -> 6, 3, x -> 10, MythicRulesTest::perks, TestCards.DATA_SET, List.of(), List.of()).dormant().isEmpty(),
			"skill 6 has 4 slots, Polymath adds the one it takes");
		assertEquals(Optional.of(CardRules.Refusal.NO_SLOT), CardRules.canSlot(TestCards.plain("mining/sure_footing", Rarity.COMMON, -1), -1, deck, 6, 3, 0,
			MythicRulesTest::perks, deck, 1), "and no more");
	}

	@Test
	void theDrawIsHalfAPercentFromFifteenAndNeverRerolls() {
		int hits = 0;
		int runs = 400_000;
		for (int i = 0; i < runs; i++) {
			if (MythicRules.rolls(i * 7919L, i % 50, 15)) {
				hits++;
			}
			assertFalse(MythicRules.rolls(i * 7919L, i % 50, 14), "never below skill 15");
		}
		assertEquals(0.005, hits / (double) runs, 0.0006);
		assertEquals(MythicRules.rolls(42, 7, 20), MythicRules.rolls(42, 7, 20), "the same draft always rolls the same");
	}

	@Test
	void ledgerClaimsOnceAndNumbersDeeds() {
		CardLedger ledger = new CardLedger();
		CardData.Holder ash = new CardData.Holder(TestCards.PLAYER, "Ashford", 41);
		assertTrue(ledger.first("dragonfall").isEmpty());
		ledger.claim("dragonfall", ash);
		assertEquals("Ashford", ledger.first("dragonfall").orElseThrow().name());
		assertEquals(1, ledger.deed("unclad", TestCards.PLAYER));
		assertEquals(2, ledger.deed("unclad", new UUID(9, 9)), "the 2nd in this world");
		assertEquals(2, ledger.deeds("unclad").size());
		ledger.unclaim("dragonfall");
		assertTrue(ledger.first("dragonfall").isEmpty(), "an op's reset");
	}

	@Test
	void mostDamageWinsTiesGoToTheKillingBlow() {
		UUID a = new UUID(1, 1);
		UUID b = new UUID(2, 2);
		Map<UUID, Float> fight = new HashMap<>();
		fight.put(a, 120f);
		fight.put(b, 80f);
		assertEquals(a, MythicRules.creditOf(fight, b), "most damage wins even without the last hit");
		fight.put(b, 120f);
		assertEquals(b, MythicRules.creditOf(fight, b), "a tie goes to the killing blow");
		assertEquals(a, MythicRules.creditOf(fight, a));
	}
}
