package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Deed Mythics, one of a kind (docs/BUILD_FEEL.md §12): who gets what, and the one-time migration of numbered copies. */
class DeedRulesTest {
	static {
		TestCards.PERKS.size();
	}

	private static final UUID BREE = new UUID(1, 1);
	private static final UUID ASH = new UUID(2, 2);
	private static final UUID CORA = new UUID(3, 3);
	private static final Set<String> DEEDS = Set.of("long_walker", "unclad", "hundredth_dawn", "lone_banner", "polymath");

	private static long next = 50_000;

	/** A deed Mythic card earned by {@code earner} on {@code day}, now owned by {@code owner}, in {@code place}. */
	static CardData copy(String deed, UUID earner, long day, UUID owner, CardData.Place place) {
		Identifier id = Identifier.fromNamespaceAndPath("mettle", deed);
		CardData.Holder h = new CardData.Holder(owner, "P" + owner.getLeastSignificantBits(), day);
		return new CardData(next++, net.minecraft.resources.ResourceKey.create(dev.voidmc.mettle.api.MettleRegistries.SKILL,
			Identifier.fromNamespaceAndPath("mettle", "mythic")), MythicRules.key(id), DraftState.Rarity.MYTHIC, 1,
			List.of(), List.of(), 0, new CardData.Drawn(earner, "P" + earner.getLeastSignificantBits(), 0, day, 0, CardData.How.MYTHIC), owner,
			CardData.History.first(h), 0, 1L, place, 0L);
	}

	static CardData copy(String deed, UUID earner, long day) {
		return copy(deed, earner, day, earner, CardData.Place.BINDER);
	}

	@Test
	void theFirstGetsTheCardLaterOnesShardsAndTheTitleNothingTwice() {
		assertEquals(DeedRules.Outcome.CARD, DeedRules.outcome(false, false));
		assertEquals(DeedRules.Outcome.LATER, DeedRules.outcome(true, false));
		assertEquals(DeedRules.Outcome.DONE, DeedRules.outcome(true, true));
		assertEquals(DeedRules.Outcome.DONE, DeedRules.outcome(false, true));
		assertEquals(10, new CardConfig().deed_later_shards);
		assertEquals(30, new CardConfig().deed_migration_shards);
	}

	@Test
	void migrationKeepsTheEarliestCopyAndReturnsTheRest() {
		CardData bree = copy("long_walker", BREE, 112);
		CardData ash = copy("long_walker", ASH, 90);
		CardData cora = copy("long_walker", CORA, 200, CORA, CardData.Place.slotted(TestCards.skill("mining"), 2));
		CardData unclad = copy("unclad", ASH, 50);
		CardData world = copy("dragonfall", BREE, 10);
		// The deed list's order is the copy number: Bree did it first (copy 1), though Ash's card says an earlier day.
		Map<String, List<UUID>> order = Map.of("long_walker", List.of(BREE, ASH, CORA), "unclad", List.of(ASH));
		DeedRules.Plan plan = DeedRules.migrate(List.of(ash, bree, cora, unclad, world), DEEDS, k -> order.getOrDefault(k, List.of()));
		assertEquals(bree, plan.keep().get("long_walker"));
		assertEquals(unclad, plan.keep().get("unclad"));
		assertEquals(List.of(ash, cora), plan.returned(), "copies 2 and 3 return, the slotted one too");
		assertFalse(plan.keep().containsKey("dragonfall"), "a world first is not a deed");
		assertEquals(new CardData.Holder(BREE, "P1", 112), DeedRules.holder(bree));
	}

	@Test
	void tiesGoByDayThenCardWhenTheListLostThem() {
		CardData a = copy("polymath", ASH, 40);
		CardData b = copy("polymath", BREE, 30);
		CardData c = copy("polymath", CORA, 30);
		DeedRules.Plan plan = DeedRules.migrate(List.of(a, c, b), DEEDS, k -> List.of());
		assertEquals(b, plan.keep().get("polymath"), "no list: the earliest day, then the lowest card id");
		assertEquals(List.of(c, a), plan.returned());
	}

	@Test
	void migrationIsIdempotentAndIgnoresReleasedCards() {
		CardData bree = copy("hundredth_dawn", BREE, 112);
		CardData ash = copy("hundredth_dawn", ASH, 120);
		Map<String, List<UUID>> order = Map.of("hundredth_dawn", List.of(BREE, ASH));
		DeedRules.Plan first = DeedRules.migrate(List.of(bree, ash), DEEDS, order::get);
		assertEquals(List.of(ash), first.returned());
		List<CardData> after = new ArrayList<>();
		after.add(bree);
		first.returned().forEach(r -> after.add(r.withPlace(CardData.Place.RELEASED)));
		DeedRules.Plan second = DeedRules.migrate(after, DEEDS, order::get);
		assertTrue(second.empty(), "a second run changes nothing");
		assertEquals(bree, second.keep().get("hundredth_dawn"));
	}

	@Test
	void theLedgerRecordsDeedsOnceAndKeepsTheReturnLog() {
		CardLedger ledger = new CardLedger();
		assertEquals(1, ledger.deed("unclad", BREE));
		assertEquals(2, ledger.deed("unclad", ASH));
		assertEquals(1, ledger.deed("unclad", BREE), "a deed is recorded once a player");
		assertEquals(List.of(BREE, ASH), ledger.deeds("unclad"));
		ledger.addReturn(new CardLedger.Returned(77, "unclad", ASH, "Ash", 5, false));
		ledger.settleReturn(77);
		assertTrue(ledger.returns().getFirst().settled());
		var json = CardLedger.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
		CardLedger back = CardLedger.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(ledger.returns(), back.returns(), "the return log survives a save");
		assertEquals(List.of(BREE, ASH), back.deeds("unclad"));
	}

	@Test
	void titlesAreKeptOnceAndTheFirstIsWorn() {
		Identifier walker = Identifier.fromNamespaceAndPath("mettle", "long_walker");
		Identifier unclad = Identifier.fromNamespaceAndPath("mettle", "unclad");
		BuildState.Titles t = BuildState.Titles.NONE.with(walker);
		assertEquals(Optional.of(walker), t.worn());
		t = t.with(unclad).with(walker);
		assertEquals(List.of(walker, unclad), t.held());
		assertEquals(Optional.of(walker), t.worn(), "a second title does not replace the worn one");
		t = t.wearing(Optional.of(unclad), true);
		assertEquals(Optional.of(unclad), t.worn());
		assertTrue(t.instead());
		assertEquals(Optional.empty(), t.wearing(Optional.of(Identifier.fromNamespaceAndPath("mettle", "polymath")), false).worn(), "only a held title");
		assertFalse(t.wearing(Optional.empty(), true).instead());
		var json = BuildState.CODEC.encodeStart(JsonOps.INSTANCE, BuildState.EMPTY.withTitles(t)).getOrThrow();
		assertEquals(t, BuildState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().titles());
		assertEquals(BuildState.Titles.NONE, BuildState.CODEC.parse(JsonOps.INSTANCE, BuildState.CODEC.encodeStart(JsonOps.INSTANCE, BuildState.EMPTY).getOrThrow())
			.getOrThrow().titles(), "an old record reads with no titles");
	}
}
