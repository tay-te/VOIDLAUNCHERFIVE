package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Talent;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The talent layer's rules (docs/BUILD_FEEL.md §13): cadence, odds, bands, the hand, the row, rearranging, ceilings. */
class TalentRulesTest {
	static {
		TestCards.PERKS.size();
	}

	private static final UUID ME = new UUID(1, 2);
	private static long next = 10_000;

	static List<TalentRules.Entry> all() {
		List<TalentRules.Entry> out = new ArrayList<>();
		TestCards.TALENTS.forEach((id, t) -> out.add(new TalentRules.Entry(id, t)));
		return out;
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	/** A talent card, in the binder ({@code slot} -1) or the row. */
	static CardData card(String talent, Rarity rarity, double value, int slot) {
		Identifier id = id(talent);
		CardData.Place place = slot >= 0 ? CardData.Place.slotted(CardData.TALENT, slot) : CardData.Place.BINDER;
		CardData.Holder h = new CardData.Holder(ME, "Tester", 0);
		return new CardData(next++, CardData.TALENT, TalentRules.key(id), rarity, 1, List.of(), List.of(new TraitRoll(TalentRules.trait(id), value)), 0,
			new CardData.Drawn(ME, "Tester", 0, 0, 0, CardData.How.DRAFT), ME, CardData.History.first(h), 0, 7L, place, 0L);
	}

	// ---------------------------------------------------------------- data

	@Test
	void twentySixTalentsInFiveFamilies() {
		assertEquals(26, TestCards.TALENTS.size());
		Map<Talent.Family, Integer> families = new HashMap<>();
		TestCards.TALENTS.values().forEach(t -> families.merge(t.family(), 1, Integer::sum));
		assertEquals(Map.of(Talent.Family.BODY, 8, Talent.Family.MOTION, 5, Talent.Family.HANDS, 5, Talent.Family.MIND, 5, Talent.Family.FORTUNE, 3), families);
		assertEquals(Set.of("light_step", "light_sleeper", "packrat"), Set.copyOf(TestCards.TALENTS.entrySet().stream().filter(e -> e.getValue().isFixed())
			.map(e -> e.getKey().getPath()).toList()));
		assertEquals(Rarity.EPIC, TalentRules.fixedRarity(TestCards.TALENTS.get(id("packrat"))));
		assertEquals(Rarity.RARE, TalentRules.fixedRarity(TestCards.TALENTS.get(id("light_step"))));
		// The ceiling talents (§14): Fleet, Weathered Hide, Grit, Scavenger.
		Map<String, String> ceilings = new HashMap<>();
		TestCards.TALENTS.forEach((k, t) -> t.ceiling().ifPresent(c -> ceilings.put(k.getPath(), c)));
		assertEquals(Map.of("fleet", "movement", "weathered_hide", "taken", "grit", "taken", "scavenger", "extra_drop"), ceilings);
		// Each skill keeps its biggest numbers: a talent's carry tops at +6 kg (Athletics' Ox is +25).
		assertEquals(6, TestCards.TALENTS.get(id("broad_shoulders")).max());
	}

	// ---------------------------------------------------------------- cadence (§13.1)

	@Test
	void slotsByCharacterLevel() {
		assertEquals(0, TalentRules.slots(0));
		assertEquals(0, TalentRules.slots(2));
		assertEquals(2, TalentRules.slots(3));
		assertEquals(2, TalentRules.slots(14));
		assertEquals(3, TalentRules.slots(15));
		assertEquals(3, TalentRules.slots(26));
		assertEquals(4, TalentRules.slots(27));
		assertEquals(4, TalentRules.slots(38));
		assertEquals(5, TalentRules.slots(39));
		assertEquals(5, TalentRules.slots(50));
	}

	@Test
	void draftAtThreeAndEveryFour() {
		List<Integer> at = new ArrayList<>();
		for (int cl = 0; cl <= 50; cl++) {
			if (TalentRules.draftAt(cl)) {
				at.add(cl);
			}
		}
		assertEquals(List.of(3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47), at);
		assertEquals(12, TalentRules.draftsUpTo(50));
		assertEquals(0, TalentRules.draftsUpTo(2));
		assertEquals(1, TalentRules.draftsUpTo(3));
		// An old character at 30 gets all seven at once; a level-up pays only what it passed, never twice.
		assertEquals(7, TalentRules.draftsBetween(0, 30));
		assertEquals(1, TalentRules.draftsBetween(30, 31));
		assertEquals(0, TalentRules.draftsBetween(31, 32));
		assertEquals(0, TalentRules.draftsBetween(31, 31));
		assertEquals(0, TalentRules.draftsBetween(40, 20));
		assertEquals(2, TalentRules.draftsBetween(4, 11));
	}

	// ---------------------------------------------------------------- rolling

	@Test
	void rarityOddsOverAHundredThousandRolls() {
		for (int cl : new int[] {0, 25, 50}) {
			Random r = new Random(cl);
			int[] n = new int[3];
			int rolls = 100_000;
			for (int i = 0; i < rolls; i++) {
				n[TalentRules.rarity(cl, r.nextDouble()).ordinal()]++;
			}
			double[] want = {70 - 0.6 * cl, 24 + 0.4 * cl, 6 + 0.2 * cl};
			for (int k = 0; k < 3; k++) {
				assertEquals(want[k], 100.0 * n[k] / rolls, 1.0, "rarity " + k + " at CL " + cl);
			}
		}
		// At 50: 40 / 44 / 16 (§13.1).
		assertEquals(40, TalentRules.odds(50)[0], 1e-9);
		assertEquals(44, TalentRules.odds(50)[1], 1e-9);
		assertEquals(16, TalentRules.odds(50)[2], 1e-9);
	}

	@Test
	void valuesStayInTheirBandsOnTheStep() {
		Random r = new Random(7);
		for (TalentRules.Entry e : all()) {
			Talent t = e.def();
			for (Rarity rarity : new Rarity[] {Rarity.COMMON, Rarity.RARE, Rarity.EPIC}) {
				double[] band = TalentRules.band(t, rarity);
				for (int i = 0; i < 2000; i++) {
					double v = TalentRules.value(t, rarity, r);
					if (t.isFixed()) {
						assertEquals(1, v);
						continue;
					}
					assertTrue(v >= band[0] - t.step() + 1e-9 && v <= band[1] + 1e-9, e.id() + " " + rarity + " " + v);
					assertTrue(v >= t.min() - 1e-9 && v <= t.max() + 1e-9, e.id() + " out of range " + v);
					double steps = v / t.step();
					assertEquals(Math.rint(steps), steps, 1e-6, e.id() + " off the step " + v);
				}
			}
			if (!t.isFixed()) {
				// Common the low two thirds, Epic the top two thirds, Rare the whole range.
				assertEquals(t.min(), TalentRules.band(t, Rarity.COMMON)[0], 1e-9);
				assertEquals(t.min() + (t.max() - t.min()) * 2 / 3, TalentRules.band(t, Rarity.COMMON)[1], 1e-9);
				assertEquals(t.min() + (t.max() - t.min()) / 3, TalentRules.band(t, Rarity.EPIC)[0], 1e-9);
				assertEquals(t.max(), TalentRules.band(t, Rarity.EPIC)[1], 1e-9);
				assertEquals(t.max(), TalentRules.band(t, Rarity.RARE)[1], 1e-9);
			}
		}
	}

	@Test
	void bandMeansOverAHundredThousandRolls() {
		Talent fleet = TestCards.TALENTS.get(id("quick_healer"));
		Random r = new Random(11);
		for (Rarity rarity : new Rarity[] {Rarity.COMMON, Rarity.RARE, Rarity.EPIC}) {
			double sum = 0;
			int rolls = 100_000;
			for (int i = 0; i < rolls; i++) {
				sum += TalentRules.value(fleet, rarity, r);
			}
			double[] band = TalentRules.band(fleet, rarity);
			assertEquals((band[0] + band[1]) / 2, sum / rolls, (band[1] - band[0]) * 0.01 + 0.5, rarity.name());
		}
	}

	@Test
	void aHandIsThreeDifferentTalentsDealtFromTheSeed() {
		for (int seed = 0; seed < 200; seed++) {
			List<DraftState.Card> a = TalentRules.deal(all(), x -> 0, 20, new Random(seed));
			List<DraftState.Card> b = TalentRules.deal(all(), x -> 0, 20, new Random(seed));
			assertEquals(3, a.size());
			assertTrue(TalentRules.distinct(a));
			assertEquals(a, b, "the same seed deals the same hand");
			for (DraftState.Card c : a) {
				assertTrue(c.keywords().isEmpty(), "talents carry no keywords");
				assertEquals(1, c.traits().size());
				assertTrue(c.perk().identifier().getPath().startsWith("talent/"));
			}
		}
	}

	@Test
	void ownedTalentsAreDealtAtHalfWeight() {
		Identifier fleet = id("fleet");
		int with = 0;
		int without = 0;
		for (int seed = 0; seed < 20_000; seed++) {
			if (TalentRules.deal(all(), x -> 0, 20, new Random(seed)).stream().anyMatch(c -> TalentRules.id(c.perk()).equals(fleet))) {
				without++;
			}
			if (TalentRules.deal(all(), x -> x.equals(fleet) ? 1 : 0, 20, new Random(seed)).stream().anyMatch(c -> TalentRules.id(c.perk()).equals(fleet))) {
				with++;
			}
		}
		// Three of 26 is about 11.5 %; at ½ weight about 5.9 %.
		assertTrue(with < without * 0.62 && with > without * 0.4, "owned " + with + " vs not " + without);
	}

	// ---------------------------------------------------------------- the row

	@Test
	void oneCardPerTalentABetterCopyReplacesAWorseOne() {
		CardData fleet2 = card("fleet", Rarity.COMMON, 2, 0);
		List<CardData> row = List.of(fleet2);
		CardData fleet3 = card("fleet", Rarity.RARE, 3, -1);
		CardData fleet2b = card("fleet", Rarity.COMMON, 2, -1);
		assertEquals(Optional.empty(), TalentRules.canSlot(fleet3, -1, row, 2));
		assertEquals(Optional.of(fleet2), TalentRules.slotting(fleet3, -1, row, 2).orElseThrow().replaces());
		assertEquals(0, TalentRules.slotting(fleet3, -1, row, 2).orElseThrow().at());
		assertEquals(Optional.of(CardRules.Refusal.SAME_TALENT), TalentRules.canSlot(fleet2b, -1, row, 2));
		// Equal value, better rarity: still better.
		CardData fleet2rare = card("fleet", Rarity.RARE, 2, -1);
		assertTrue(TalentRules.better(fleet2rare, fleet2));
		assertFalse(TalentRules.better(fleet2, fleet2rare));
	}

	@Test
	void slotRules() {
		CardData a = card("hardy", Rarity.COMMON, 1, 0);
		CardData b = card("lucky", Rarity.COMMON, 1, 1);
		CardData c = card("grit", Rarity.COMMON, 3, -1);
		assertEquals(Optional.of(CardRules.Refusal.TALENT_SLOTS), TalentRules.canSlot(c, -1, List.of(), 0));
		assertEquals(Optional.of(CardRules.Refusal.NO_SLOT), TalentRules.canSlot(c, -1, List.of(a, b), 2));
		assertEquals(Optional.of(CardRules.Refusal.SLOT_TAKEN), TalentRules.canSlot(c, 1, List.of(a, b), 3));
		assertEquals(Optional.empty(), TalentRules.canSlot(c, -1, List.of(a, b), 3));
		assertEquals(2, TalentRules.slotting(c, -1, List.of(a, b), 3).orElseThrow().at());
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_BINDER), TalentRules.canSlot(a, -1, List.of(b), 3));
		// A skill card is never a talent: the row refuses it (and earned talents, being perks, never take a slot).
		CardData skill = TestCards.plain("mining/prospector", Rarity.COMMON, -1);
		assertEquals(Optional.of(CardRules.Refusal.NOT_A_TALENT), TalentRules.canSlot(skill, -1, List.of(), 3));
	}

	@Test
	void talentsAreBound() {
		CardData t = card("lucky", Rarity.EPIC, 1.5, -1);
		assertEquals(Optional.of(TradeRules.Refusal.BOUND), TradeRules.canOffer(t, ME, 0, 0));
		assertEquals(TalentRules.PREFIX, "TL");
		assertTrue(CardRules.printId(TalentRules.PREFIX, t.id()).startsWith("TL"));
	}

	@Test
	void theRowAddsItsValuesWithinItsSlots() {
		CardData fleet = card("fleet", Rarity.RARE, 3, 0);
		CardData hardy = card("hardy", Rarity.COMMON, 1, 1);
		CardData extra = card("lucky", Rarity.COMMON, 1, 2);
		CardData binder = card("grit", Rarity.EPIC, 6, -1);
		Map<Identifier, Double> v = TalentRules.values(List.of(fleet, hardy, extra, binder), 10);
		assertEquals(Map.of(id("talent/fleet"), 3.0, id("talent/hardy"), 1.0), v, "two slots at CL 10: the third slot does nothing");
		assertEquals(3, TalentRules.values(List.of(fleet, hardy, extra, binder), 15).size());
		assertTrue(TalentRules.values(List.of(fleet, hardy), 2).isEmpty());
		// The cache keeps talents apart from skill cards.
		CardsState s = CardsState.EMPTY.withOwned(List.of(fleet, TestCards.plain("mining/prospector", Rarity.COMMON, -1)));
		assertEquals(1, s.cards().size());
		assertEquals(List.of(fleet), s.talent().cards());
		assertEquals(2, s.everything().size());
	}

	// ---------------------------------------------------------------- rearranging (§13.1)

	@Test
	void rearrangeOnWakingOrWithAFreeSwap() {
		assertEquals(Optional.of(TalentRules.Way.LOW_LEVEL), TalentRules.rearrange(4, false, 0));
		assertEquals(Optional.of(TalentRules.Way.WAKE), TalentRules.rearrange(20, true, 0));
		assertEquals(Optional.of(TalentRules.Way.WAKE), TalentRules.rearrange(20, true, 3), "waking never spends a swap");
		assertEquals(Optional.of(TalentRules.Way.FREE_SWAP), TalentRules.rearrange(20, false, 1));
		assertEquals(Optional.empty(), TalentRules.rearrange(20, false, 0));
	}

	@Test
	void theWakeWindowOpensOnlyAfterAFullNight() {
		long now = 1_000_000;
		assertEquals(now + 1200, TalentRules.wakeUntil(5000, 24000 * 7, now), "woken by the morning after a night in bed: 60 s");
		assertEquals(now + 1200, TalentRules.wakeUntil(101, 24000 * 7 + 3, now));
		assertEquals(0, TalentRules.wakeUntil(60, 24000 * 7, now), "a nap in bed of three seconds");
		assertEquals(0, TalentRules.wakeUntil(5000, 18000, now), "got up in the night");
		assertEquals(0, TalentRules.wakeUntil(5000, 24000 * 7 + 6000, now), "a day nap");
		CardConfig c = new CardConfig();
		c.talent_swap_on_wake_s = 0;
		CardConfig.use(c);
		try {
			assertEquals(0, TalentRules.wakeUntil(5000, 0, now), "0 s: only free swaps");
		} finally {
			CardConfig.reset();
		}
		CardsState.TalentState t = new CardsState.TalentState(List.of(), now + 1200);
		assertTrue(t.awake(now + 1199));
		assertFalse(t.awake(now + 1200));
	}

	// ---------------------------------------------------------------- the ceilings (§14)

	@Test
	void ceilingTalentsCountTowardTheirCeilings() {
		assertEquals(Optional.of(Ceilings.Stat.MOVEMENT), Ceilings.statOf("talent/fleet"));
		assertEquals(Optional.of(Ceilings.Stat.TAKEN), Ceilings.statOf("talent/weathered_hide"));
		assertEquals(Optional.of(Ceilings.Stat.TAKEN), Ceilings.statOf("talent/grit"));
		assertEquals(Optional.of(Ceilings.Stat.EXTRA_DROP), Ceilings.statOf("talent/scavenger"));
		Ceilings.Build none = new Ceilings.Build(Map.of(), Map.of(), Set.of(), Map.of());
		Ceilings.Build with = new Ceilings.Build(Map.of("talent/fleet", 4.0, "talent/weathered_hide", 4.0, "talent/grit", 6.0, "talent/scavenger", 5.0),
			Map.of(), Set.of(), Map.of());
		assertEquals(Ceilings.movementPotential(none, false) + 0.04, Ceilings.movementPotential(with, false), 1e-9);
		assertEquals(Ceilings.takenPotential(none, false) * 0.96 * 0.94, Ceilings.takenPotential(with, false), 1e-9);
		assertEquals(Ceilings.dropPotential(none) + 0.05, Ceilings.dropPotential(with), 1e-9);
		// A deck already at a ceiling stays there: the clamp takes the sum, talents included.
		assertEquals(Ceilings.MOVEMENT - 0.39, Ceilings.movementCorrection(0.39), 1e-9);
		assertEquals(Ceilings.TAKEN, Ceilings.taken(0.40), 1e-9);
	}
}
