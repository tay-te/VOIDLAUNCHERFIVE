package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** Cards v2's rules (docs/CARDS.md) against the shipped data: rolling, slots, dormancy, sets, traits, ids, migration. */
class CardRulesTest {
	private static final ResourceKey<Skill> MINING = TestCards.skill("mining");
	private static final CardRules.Attributes TEN = a -> 10;

	private static Optional<Perk> perks(ResourceKey<Perk> k) {
		return Optional.ofNullable(TestCards.PERKS.get(k));
	}

	private static Card roll(String perk, int level, long seed) {
		ResourceKey<Perk> key = TestCards.perk(perk);
		return CardRules.roll(key, TestCards.def(key), level, TestCards.DATA_SET, new Random(seed), false);
	}

	// ---------------------------------------------------------------- dealing and rolling

	@Test
	void offersAreDeterministicFromSeedAndDealCount() {
		TestCards.Sheet sheet = new TestCards.Sheet().level("mining", 14).own("mining/hard_hat", 1);
		for (int n = 0; n < 200; n++) {
			List<Card> a = CardRules.deal(MINING, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(99L, n), List.of(), Set.of());
			List<Card> b = CardRules.deal(MINING, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(99L, n), List.of(), Set.of());
			assertEquals(a, b, "the same seed and deal count deal the same offers");
			assertEquals(DraftRules.HAND, a.size());
			assertEquals(3, new HashSet<>(a.stream().map(Card::perk).toList()).size(), "a hand never holds a perk twice");
		}
		List<Card> other = CardRules.deal(MINING, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(99L, 1000), List.of(), Set.of());
		assertNotEquals(CardRules.deal(MINING, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(99L, 1001), List.of(), Set.of()), other);
	}

	@Test
	void ownedPerksAreStillDealtButLessOften() {
		TestCards.Sheet fresh = new TestCards.Sheet().level("mining", 4);
		TestCards.Sheet owner = new TestCards.Sheet().level("mining", 4).own("mining/quarryman", 2);
		int freshCount = 0;
		int ownerCount = 0;
		for (int n = 0; n < 4000; n++) {
			for (Card c : CardRules.deal(MINING, fresh, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(5L, n), List.of(), Set.of())) {
				freshCount += c.perk().equals(TestCards.perk("mining/quarryman")) ? 1 : 0;
			}
			for (Card c : CardRules.deal(MINING, owner, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(5L, n), List.of(), Set.of())) {
				ownerCount += c.perk().equals(TestCards.perk("mining/quarryman")) ? 1 : 0;
			}
		}
		// At skill 4 only the two roots can be dealt and a hand holds both, so ownership changes only the order; at 14 it
		// shifts the odds. Either way an owned perk keeps coming.
		assertTrue(ownerCount > 0, "an owned perk is still dealt");
		assertEquals(4000, freshCount, "two roots, both in every hand at skill 4");
		TestCards.Sheet deep = new TestCards.Sheet().level("mining", 14).own("mining/hard_hat", 1).own("mining/sure_footing", 1);
		TestCards.Sheet deepOwner = new TestCards.Sheet().level("mining", 14).own("mining/hard_hat", 3).own("mining/sure_footing", 1);
		int a = 0;
		int b = 0;
		for (int n = 0; n < 6000; n++) {
			a += CardRules.deal(MINING, deep, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(7L, n), List.of(), Set.of()).stream()
				.filter(c -> c.perk().equals(TestCards.perk("mining/hard_hat"))).count();
			b += CardRules.deal(MINING, deepOwner, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(7L, n), List.of(), Set.of()).stream()
				.filter(c -> c.perk().equals(TestCards.perk("mining/hard_hat"))).count();
		}
		assertTrue(b < a * 0.75, "three copies owned deal it at ¼ against ½: " + a + " vs " + b);
	}

	@Test
	void masteriesAndDeepCardsFollowWhatIsOwned() {
		TestCards.Sheet sheet = new TestCards.Sheet().level("mining", 20).own("mining/hard_hat", 1);
		for (int n = 0; n < 2000; n++) {
			for (Card c : CardRules.deal(MINING, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(3L, n), List.of(), Set.of())) {
				Perk p = TestCards.def(c.perk());
				assertNotEquals(Perk.Kind.MASTERY, p.kind(), "a Mastery needs two cards of its side owned: " + c.perk());
				if (p.kind() == Perk.Kind.PATH && p.tier() == 3) {
					assertEquals("a", p.path(), "tier III needs a tier II of its own path owned: " + c.perk());
				}
			}
		}
		// Owning all three Masteries is allowed: they keep being dealt once their sides are owned.
		TestCards.Sheet all = new TestCards.Sheet().level("mining", 20).own("mining/hard_hat", 1).own("mining/sure_footing", 1)
			.own("mining/tunneler", 1).own("mining/rock_hauler", 1).own("mining/deep_delver", 1).own("mining/motherlode", 1);
		assertTrue(CardRules.candidates(MINING, all, TestCards.DECK).contains(TestCards.perk("mining/mountain_breaker")));
		assertTrue(CardRules.candidates(MINING, all, TestCards.DECK).contains(TestCards.perk("mining/deep_delver")));
	}

	@Test
	void rarityOddsMatchTheTable() {
		Perk quarry = TestCards.def(TestCards.perk("mining/quarryman"));
		for (int level : new int[] {0, 10, 20}) {
			Map<Rarity, Integer> n = new EnumMap<>(Rarity.class);
			Random r = new Random(level);
			int runs = 100_000;
			for (int i = 0; i < runs; i++) {
				n.merge(CardRules.rollRarity(quarry, level, r), 1, Integer::sum);
			}
			Map<Rarity, Double> odds = CardRules.rarityOdds(level);
			for (Rarity rarity : CardRules.ROLLED) {
				assertEquals(odds.get(rarity), 100.0 * n.getOrDefault(rarity, 0) / runs, 1.0, rarity + " at skill " + level);
			}
		}
		assertEquals(70.0, CardRules.rarityOdds(0).get(Rarity.COMMON), 1e-9);
		assertEquals(44.0, CardRules.rarityOdds(20).get(Rarity.RARE), 1e-9);
		assertEquals(16.0, CardRules.rarityOdds(20).get(Rarity.EPIC), 1e-9);
		assertEquals(Rarity.CURSED, CardRules.rollRarity(TestCards.def(TestCards.perk("mining/greedy_pick")), 10, new Random(1)));
	}

	@Test
	void traitCountsValuesAndKeywordsFollowRarity() {
		Map<Rarity, Map<Integer, Integer>> counts = new EnumMap<>(Rarity.class);
		Map<Rarity, Integer> second = new EnumMap<>(Rarity.class);
		Map<Rarity, Integer> total = new EnumMap<>(Rarity.class);
		String[] perks = {"mining/quarryman", "mining/tunneler", "blades/fencer", "survival/gatherer", "smithing/forged", "mining/black_lung"};
		for (int i = 0; i < 100_000; i++) {
			Card c = roll(perks[i % perks.length], i % 21, i * 31L);
			Perk p = TestCards.def(c.perk());
			total.merge(c.rarity(), 1, Integer::sum);
			counts.computeIfAbsent(c.rarity(), r -> new HashMap<>()).merge(c.traits().size(), 1, Integer::sum);
			if (c.keywords().size() > 1) {
				second.merge(c.rarity(), 1, Integer::sum);
				assertNotEquals(c.keywords().get(0), c.keywords().get(1), "the second keyword is never the first");
			}
			assertEquals(c.traits().size(), new HashSet<>(c.traits().stream().map(TraitRoll::trait).toList()).size(), "no trait twice");
			for (TraitRoll t : c.traits()) {
				Trait def = TestCards.TRAITS.get(t.trait());
				assertEquals(p.skill(), def.skill(), "traits come from the card's own skill");
				double[] band = CardRules.band(def, c.rarity());
				assertTrue(t.value() >= band[0] - 1e-9 && t.value() <= band[1] + 1e-9, t + " outside " + c.rarity() + " band " + band[0] + "–" + band[1]);
				double steps = t.value() / def.step();
				assertEquals(Math.rint(steps), steps, 1e-6, t + " is not on its step " + def.step());
			}
			if (p.kind() == Perk.Kind.CURSED) {
				assertEquals(Rarity.CURSED, c.rarity());
				assertEquals(CardRules.HEX, c.keywords().getFirst(), "cursed cards carry Hex");
				assertEquals(2, c.keywords().size(), "cursed cards always carry a second keyword");
			}
			assertEquals(CardRules.needs(p, c.rarity()), c.needs());
			assertEquals(CardRules.rank(p, c.rarity()), c.rank());
		}
		assertShare(counts, total, Rarity.COMMON, 0, 0.5);
		assertShare(counts, total, Rarity.RARE, 1, 0.6);
		assertShare(counts, total, Rarity.EPIC, 2, 0.6);
		assertShare(counts, total, Rarity.CURSED, 1, 0.5);
		assertFalse(second.containsKey(Rarity.COMMON), "a Common never rolls a second keyword");
		assertEquals(0.3, second.get(Rarity.RARE) / (double) total.get(Rarity.RARE), 0.01);
		assertEquals(0.7, second.get(Rarity.EPIC) / (double) total.get(Rarity.EPIC), 0.01);
	}

	private static void assertShare(Map<Rarity, Map<Integer, Integer>> counts, Map<Rarity, Integer> total, Rarity r, int traits, double share) {
		assertEquals(share, counts.get(r).getOrDefault(traits, 0) / (double) total.get(r), 0.01, r + " cards with " + traits + " traits");
	}

	@Test
	void requirementTable() {
		int[][] want = {{2, 4, 7}, {6, 7, 9}, {12, 13, 15}, {18, 19, 20}};
		String[] perks = {"mining/quarryman", "mining/tunneler", "mining/ore_sense", "mining/motherlode"};
		for (int t = 0; t < 4; t++) {
			Perk p = TestCards.def(TestCards.perk(perks[t]));
			assertEquals(want[t][0], CardRules.needs(p, Rarity.COMMON), perks[t] + " Common");
			assertEquals(want[t][1], CardRules.needs(p, Rarity.RARE), perks[t] + " Rare");
			assertEquals(want[t][2], CardRules.needs(p, Rarity.EPIC), perks[t] + " Epic");
			assertEquals(new int[] {2, 6, 12, 18}[t], CardRules.migratedNeeds(p), "a migrated card needs only its tier");
		}
		assertEquals(6, CardRules.needs(TestCards.def(TestCards.perk("mining/greedy_pick")), Rarity.CURSED));
		assertEquals(12, CardRules.needs(TestCards.def(TestCards.perk("mining/black_lung")), Rarity.CURSED));
		Perk q = TestCards.def(TestCards.perk("mining/quarryman"));
		assertEquals(1, CardRules.rank(q, Rarity.COMMON));
		assertEquals(2, CardRules.rank(q, Rarity.RARE));
		assertEquals(3, CardRules.rank(q, Rarity.EPIC));
		assertEquals(1, CardRules.rank(TestCards.def(TestCards.perk("mining/tunneler")), Rarity.EPIC));
	}

	// ---------------------------------------------------------------- slots

	@Test
	void slotCurveAndWits() {
		int[] levels = {0, 1, 2, 4, 5, 8, 11, 14, 17, 20};
		int[] base = {0, 0, 3, 3, 4, 5, 6, 7, 8, 8};
		for (int i = 0; i < levels.length; i++) {
			assertEquals(base[i], CardRules.baseSlots(levels[i]), "base slots at " + levels[i]);
		}
		assertEquals(3, CardRules.slots(2, 7));
		assertEquals(4, CardRules.slots(2, 8));
		assertEquals(5, CardRules.slots(2, 14));
		assertEquals(10, CardRules.slots(20, 20));
		assertEquals(0, CardRules.slots(1, 20), "Wits adds nothing before a skill has slots");
		assertEquals(5, CardRules.nextSlotAt(2));
		assertEquals(0, CardRules.nextSlotAt(17));
	}

	@Test
	void slotRules() {
		CardData q1 = TestCards.plain("mining/quarryman", Rarity.COMMON, 0);
		CardData q3 = TestCards.plain("mining/quarryman", Rarity.EPIC, -1);
		List<CardData> deck = List.of(q1);
		assertEquals(Optional.of(CardRules.Refusal.NEEDS_LEVEL), CardRules.canSlot(q3, -1, deck, 6, 3, 0, CardRulesTest::perks), "Epic Quarryman III needs 7");
		assertEquals(Optional.of(CardRules.Refusal.SAME_PERK), CardRules.canSlot(q3, -1, deck, 7, 3, 0, CardRulesTest::perks), "one card of a perk per deck");
		CardData prospector = TestCards.plain("mining/prospector", Rarity.COMMON, -1);
		assertEquals(Optional.empty(), CardRules.canSlot(prospector, -1, deck, 7, 3, 0, CardRulesTest::perks));
		assertEquals(Optional.of(CardRules.Refusal.SLOT_TAKEN), CardRules.canSlot(prospector, 0, deck, 7, 3, 0, CardRulesTest::perks));
		assertEquals(Optional.of(CardRules.Refusal.NO_SLOT), CardRules.canSlot(prospector, 4, deck, 7, 3, 0, CardRulesTest::perks), "skill 7 has 4 slots");
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_BINDER), CardRules.canSlot(q1, -1, List.of(), 7, 3, 0, CardRulesTest::perks));
		CardData attuning = attuning(prospector, 500);
		assertEquals(Optional.of(CardRules.Refusal.ATTUNING), CardRules.canSlot(attuning, -1, deck, 7, 3, 100, CardRulesTest::perks));
		assertEquals(Optional.empty(), CardRules.canSlot(attuning, -1, deck, 7, 3, 500, CardRulesTest::perks));
		List<CardData> full = List.of(q1, TestCards.plain("mining/prospector", Rarity.COMMON, 1), TestCards.plain("mining/hard_hat", Rarity.RARE, 2),
			TestCards.plain("mining/tunneler", Rarity.RARE, 3));
		CardData sure = TestCards.plain("mining/sure_footing", Rarity.RARE, -1);
		assertEquals(Optional.of(CardRules.Refusal.NO_SLOT), CardRules.canSlot(sure, -1, full, 7, 3, 0, CardRulesTest::perks));
		assertEquals(Optional.empty(), CardRules.canSlot(sure, -1, full, 7, 8, 0, CardRulesTest::perks),
			"Wits 8 adds a slot");
	}

	private static CardData attuning(CardData c, long until) {
		return new CardData(c.id(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), c.traits(), c.needs(), c.drawn(), c.owner(), c.history(),
			c.scars(), c.scarSeed(), c.place(), until);
	}

	@Test
	void oneMasteryPerDeckAndOnlyWithItsSide() {
		CardData hardHat = TestCards.plain("mining/hard_hat", Rarity.RARE, 0);
		CardData sure = TestCards.plain("mining/sure_footing", Rarity.RARE, 1);
		CardData delver = TestCards.plain("mining/deep_delver", Rarity.EPIC, -1);
		assertEquals(Optional.of(CardRules.Refusal.MASTERY_SIDE), CardRules.canSlot(delver, -1, List.of(hardHat), 20, 3, 0, CardRulesTest::perks));
		assertEquals(Optional.empty(), CardRules.canSlot(delver, -1, List.of(hardHat, sure), 20, 3, 0, CardRulesTest::perks));
		CardData tunneler = TestCards.plain("mining/tunneler", Rarity.RARE, 2);
		CardData motherlode = TestCards.plain("mining/motherlode", Rarity.EPIC, 3);
		assertEquals(Optional.of(CardRules.Refusal.ONE_MASTERY), CardRules.canSlot(delver, -1, List.of(hardHat, sure, tunneler, motherlode), 20, 3, 0,
			CardRulesTest::perks));
		// Slotted Mastery whose side is gone: dormant, counts for no set and gives no perk.
		CardData slottedDelver = TestCards.plain("mining/deep_delver", Rarity.EPIC, 2);
		CardRules.Derived whole = derive(List.of(hardHat, sure, slottedDelver), 20, 3);
		assertTrue(whole.active().containsKey(TestCards.perk("mining/deep_delver")));
		CardRules.Derived broken = derive(List.of(hardHat, slottedDelver), 20, 3);
		assertTrue(broken.dormant().contains(slottedDelver.id()), "a Mastery without its side goes dormant");
		assertFalse(broken.active().containsKey(TestCards.perk("mining/deep_delver")));
		assertFalse(broken.sets().containsKey(TestCards.id("deep")), "a dormant card counts for no set");
	}

	@Test
	void slotsLostWithWitsGoDormant() {
		List<CardData> deck = new ArrayList<>();
		String[] perks = {"mining/quarryman", "mining/prospector", "mining/hard_hat", "mining/sure_footing", "mining/tunneler"};
		for (int i = 0; i < perks.length; i++) {
			deck.add(TestCards.plain(perks[i], Rarity.COMMON, i));
		}
		assertTrue(derive(deck, 6, 8).dormant().isEmpty(), "skill 6 + Wits 8 = 5 slots");
		CardRules.Derived after = derive(deck, 6, 7);
		assertEquals(List.of(deck.get(4).id()), after.dormant(), "Wits below 8: the last slot goes dormant");
		assertFalse(after.active().containsKey(TestCards.perk("mining/tunneler")));
	}

	private static CardRules.Derived derive(List<CardData> cards, int level, int wits) {
		return CardRules.derive(cards, s -> level, wits, TEN, CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
	}

	// ---------------------------------------------------------------- sets

	@Test
	void setsCountAcrossSkillsAndSixNeedsTwo() {
		// Four Swift cards in Blades alone: Swift 4, never 6.
		List<CardData> blades = new ArrayList<>(List.of(
			TestCards.plain("blades/fencer", Rarity.COMMON, 0), TestCards.plain("blades/light_feet", Rarity.COMMON, 1),
			TestCards.plain("blades/cut_and_run", Rarity.COMMON, 2), TestCards.plain("blades/quick_draw", Rarity.RARE, 3, "swift"),
			TestCards.plain("blades/measure", Rarity.RARE, 4, "swift"), TestCards.plain("blades/disarm", Rarity.RARE, 5, "swift")));
		CardRules.Derived one = CardRules.derive(blades, s -> 20, 3, TEN, CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertEquals(6, one.sets().get(TestCards.id("swift")));
		assertEquals(4, one.tiers().get(TestCards.id("swift")), "six from one deck stop at the 4 tier");
		List<CardData> two = new ArrayList<>(blades.subList(0, 4));
		two.add(TestCards.plain("athletics/runner", Rarity.COMMON, 0));
		two.add(TestCards.plain("athletics/steady_pace", Rarity.COMMON, 1));
		CardRules.Derived mixed = CardRules.derive(two, s -> 20, 3, TEN, CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertEquals(6, mixed.tiers().get(TestCards.id("swift")), "two Athletics + four Blades Swift cards make Swift 6");
	}

	@Test
	void threeActiveSetsByCountThenFirstReachedAndPins() {
		List<CardData> cards = List.of(
			TestCards.plain("mining/quarryman", Rarity.COMMON, 0), TestCards.plain("mining/mountain_breaker", Rarity.EPIC, 5, "deep"),
			TestCards.plain("mining/rock_hauler", Rarity.RARE, 1, "deep"), TestCards.plain("mining/tunneler", Rarity.RARE, 2, "iron"),
			TestCards.plain("mining/strike_the_seam", Rarity.RARE, 3, "brutal"), TestCards.plain("mining/gem_cutter", Rarity.RARE, 4, "ember"),
			TestCards.plain("blades/fencer", Rarity.COMMON, 0), TestCards.plain("blades/light_feet", Rarity.COMMON, 1, "echo"));
		// brutal 3 (quarryman, mountain breaker, strike's second), hoard 2, deep 2, echo 2, swift 2, keen 1, iron 1, ember 1
		CardRules.Derived d = CardRules.derive(cards, s -> 20, 3, TEN, CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertEquals(3, d.tiers().size(), "three sets are active");
		assertTrue(d.tiers().containsKey(TestCards.id("brutal")), "the most cards first: " + d.tiers());
		List<Identifier> order = d.order();
		List<Identifier> ties = new ArrayList<>(d.tiers().keySet()).subList(1, 3);
		for (Identifier k : ties) {
			assertEquals(2, d.sets().get(k));
		}
		assertTrue(order.indexOf(ties.get(0)) < order.indexOf(ties.get(1)), "ties go to the one counted first");
		// A remembered order wins over id order: Swift first.
		List<Identifier> remembered = new ArrayList<>(List.of(TestCards.id("swift"), TestCards.id("echo")));
		CardRules.Derived again = CardRules.derive(cards, s -> 20, 3, TEN, CardRulesTest::perks, TestCards.DATA_SET, List.of(), remembered);
		assertEquals(List.of(TestCards.id("brutal"), TestCards.id("swift"), TestCards.id("echo")), new ArrayList<>(again.tiers().keySet()));
		// Pins choose instead.
		CardRules.Derived pinned = CardRules.derive(cards, s -> 20, 3, TEN, CardRulesTest::perks, TestCards.DATA_SET,
			List.of(TestCards.id("hoard"), TestCards.id("deep")), List.of());
		assertEquals(List.of(TestCards.id("hoard"), TestCards.id("deep")), new ArrayList<>(pinned.tiers().keySet()));
	}

	// ---------------------------------------------------------------- traits

	@Test
	void traitBoostAndCaps() {
		Trait deepborn = TestCards.TRAITS.get(TestCards.id("mining/deepborn"));
		assertEquals(10.0, CardRules.boost(10, 3), 1e-9);
		assertEquals(10.0, CardRules.boost(10, 1), 1e-9, "points below 3 never shrink a trait");
		assertEquals(11.05, CardRules.boost(10, 10), 1e-9);
		assertEquals(12.55, CardRules.boost(10, 20), 1e-9);
		assertEquals(15.0, CardRules.cardCap(deepborn), 1e-9, "a broad 12 % reaches 15 %");
		assertEquals(30.0, CardRules.totalCap(deepborn), 1e-9, "Deepborn gives at most +30 %");
		assertEquals(15.0, CardRules.effective(deepborn, 12, a -> 20), 1e-9);
		List<CardData> cards = new ArrayList<>();
		String[] perks = {"mining/quarryman", "mining/prospector", "mining/hard_hat", "mining/sure_footing", "mining/tunneler", "mining/rock_hauler"};
		for (int i = 0; i < perks.length; i++) {
			cards.add(TestCards.withTraits(TestCards.plain(perks[i], Rarity.EPIC, i), new TraitRoll(TestCards.id("mining/deepborn"), 12)));
		}
		CardRules.Derived d = CardRules.derive(cards, s -> 20, 14, a -> 20, CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertEquals(30.0, d.traits().get(TestCards.id("mining/deepborn")), 1e-9, "six Deepborn cards stop at twice the card cap");
		CardRules.Derived two = CardRules.derive(cards.subList(0, 2), s -> 20, 3, a -> a == CharacterAttribute.STRENGTH ? 10 : 3,
			CardRulesTest::perks, TestCards.DATA_SET, List.of(), List.of());
		assertEquals(26.52, two.traits().get(TestCards.id("mining/deepborn")), 1e-9, "two × 12 % × 1.105");
	}

	@Test
	void shippedTraitsKeepTheirCaps() {
		for (Map.Entry<Identifier, Trait> e : TestCards.TRAITS.entrySet()) {
			Trait t = e.getValue();
			assertTrue(t.min() > 0 && t.min() < t.max(), e.getKey() + " range");
			if (t.percent()) {
				double cap = t.scope() == Trait.Scope.BROAD ? 12 : 25;
				assertTrue(t.max() <= cap, e.getKey() + ": a " + t.scope().getSerializedName() + " trait rolls at most " + cap + " %");
				assertTrue(CardRules.cardCap(t) <= (t.scope() == Trait.Scope.BROAD ? 15 : 31.25) + 1e-9);
			}
			assertTrue(t.effect().attribute().isPresent() != t.effect().behaviour().isPresent(), e.getKey() + ": an attribute or a behaviour");
		}
		for (String skill : TestCards.RELEASED) {
			ResourceKey<Skill> key = TestCards.skill(skill);
			assertTrue(TestCards.DATA_SET.traits(key).size() >= 14, skill + " has a pool of 14–16 traits");
			for (ResourceKey<Perk> perk : TestCards.DECK.of(key)) {
				Identifier k = TestCards.DATA_SET.nativeKeyword(perk).orElseThrow(() -> new AssertionError(perk + " has no native keyword"));
				assertTrue(TestCards.KEYWORDS.containsKey(k), perk + ": unknown keyword " + k);
				if (TestCards.def(perk).kind() == Perk.Kind.CURSED) {
					assertEquals(CardRules.HEX, k);
				}
			}
			for (Identifier k : TestCards.DATA_SET.pool(key)) {
				assertTrue(TestCards.KEYWORDS.containsKey(k), skill + ": unknown pool keyword " + k);
			}
		}
		assertEquals(14, TestCards.KEYWORDS.size(), "fourteen keywords");
		TestCards.KEYWORDS.forEach((k, v) -> assertEquals(Set.of(2, 4, 6), v.sets().keySet(), k + " has the 2, 4 and 6 tiers"));
	}

	// ---------------------------------------------------------------- ids and the ledger

	@Test
	void printedIdsNeverRepeat() {
		Set<String> seen = new HashSet<>();
		for (long id = 1; id <= 1_000_000; id++) {
			assertTrue(seen.add(CardRules.printId("MN", id)), "id " + id + " printed twice");
		}
		String s = CardRules.printId("MN", 12345);
		assertTrue(s.matches("MN-[0-9A-Z]{4}-[0-9A-Z]{2}"), s);
		assertFalse(s.substring(3).matches(".*[ILOU].*"), "Crockford base 32: " + s);
		assertNotEquals(CardRules.printId("MN", 1).substring(3, 6), CardRules.printId("MN", 2).substring(3, 6), "neighbours don't read as neighbours");
		assertTrue(CardRules.printId("MN", (1L << 30) + 5).length() > 10, "ids past 2^30 print longer");
	}

	@Test
	void theLedgerWinsBothWays() {
		CardData a = TestCards.plain("mining/quarryman", Rarity.COMMON, 0);
		CardData b = TestCards.plain("mining/prospector", Rarity.COMMON, -1);
		CardData c = TestCards.plain("mining/hard_hat", Rarity.RARE, -1);
		CardRules.Reconciled r = CardRules.reconcile(TestCards.PLAYER, List.of(a, b), List.of(b, c), id -> true);
		assertEquals(List.of(a.id()), r.dropped(), "a card the ledger gives to someone else is dropped");
		assertEquals(List.of(c.id()), r.restored(), "a card the cache lacks is put back");
		assertEquals(List.of(b, c), r.cards());
		assertFalse(CardRules.reconcile(TestCards.PLAYER, List.of(b, c), List.of(b, c), id -> true).changed());
	}

	@Test
	void ledgerIdsAreUniqueAndTradesComeBack() {
		CardLedger ledger = new CardLedger();
		Set<Long> ids = new HashSet<>();
		for (int i = 0; i < 1_000_000; i++) {
			assertTrue(ids.add(ledger.allocate()));
		}
		CardData base = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		long id = ledger.allocate();
		CardData traded = new CardData(id, base.skill(), base.perk(), base.rarity(), base.rank(), base.keywords(), base.traits(), base.needs(), base.drawn(),
			base.owner(), base.history(), 0, 1, new CardData.Place(CardData.PlaceKind.TRADE, Optional.empty(), 0, 77), 0);
		ledger.add(traded);
		assertEquals(1, ledger.recoverTrades(), "a card left in a trade by a crash goes back");
		assertEquals(CardData.PlaceKind.BINDER, ledger.get(id).orElseThrow().place().kind());
		ledger.update(id, x -> x.withPlace(CardData.Place.RELEASED));
		assertTrue(ledger.owned(TestCards.PLAYER).stream().noneMatch(x -> x.id() == id), "a released card leaves the binder");
		assertTrue(ledger.allocate() > id, "ids never go back");
	}

	// ---------------------------------------------------------------- migration

	@Test
	void theMiningNineExample() {
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		held.put(TestCards.perk("mining/quarryman"), 3);
		held.put(TestCards.perk("mining/prospector"), 1);
		held.put(TestCards.perk("mining/rock_hauler"), 1);
		held.put(TestCards.perk("mining/tunneler"), 1);
		held.put(TestCards.perk("mining/stonesense"), 1);
		CardRules.Migration m = CardRules.migrate(held, TestCards.DECK, TestCards.DATA_SET, 42L, s -> 9, 3);
		assertEquals(4, m.cards().size(), "four drafted perks, four cards; the earned Teaching stays a perk");
		Map<ResourceKey<Perk>, Card> by = new HashMap<>();
		m.cards().forEach(c -> by.put(c.perk(), c.card()));
		assertEquals(Rarity.EPIC, by.get(TestCards.perk("mining/quarryman")).rarity());
		assertEquals(3, by.get(TestCards.perk("mining/quarryman")).rank(), "Epic Quarryman III");
		assertEquals(Rarity.COMMON, by.get(TestCards.perk("mining/prospector")).rarity());
		assertEquals(Rarity.RARE, by.get(TestCards.perk("mining/rock_hauler")).rarity());
		assertEquals(Rarity.RARE, by.get(TestCards.perk("mining/tunneler")).rarity());
		assertEquals(2, by.get(TestCards.perk("mining/quarryman")).needs(), "migrated cards need only their tier");
		assertTrue(m.cards().stream().allMatch(CardRules.Migrated::slotted), "Mining 9 has 5 slots: all four slotted");
		assertEquals(Map.of(MINING, 2), m.drafts(), "Quarryman III gives back two drafts");
	}

	@Test
	void migrationLosesNothingAndSlotsDeepestFirst() {
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		held.put(TestCards.perk("mining/quarryman"), 2);
		held.put(TestCards.perk("mining/prospector"), 3);
		held.put(TestCards.perk("mining/hard_hat"), 1);
		held.put(TestCards.perk("mining/sure_footing"), 1);
		held.put(TestCards.perk("mining/lantern_eyes"), 1);
		held.put(TestCards.perk("mining/deep_delver"), 1);
		held.put(TestCards.perk("mining/greedy_pick"), 1);
		held.put(TestCards.perk("blades/fencer"), 1);
		int ranks = held.values().stream().mapToInt(Integer::intValue).sum();
		// Mining 2 (3 slots): the Mastery and two of its side; nothing else fits.
		CardRules.Migration small = CardRules.migrate(held, TestCards.DECK, TestCards.DATA_SET, 1L, s -> s.equals(MINING) ? 2 : 0, 3);
		int drafts = small.drafts().values().stream().mapToInt(Integer::intValue).sum();
		assertEquals(ranks, small.cards().size() + drafts, "every rank is a card or a returned draft");
		List<ResourceKey<Perk>> slotted = small.cards().stream().filter(CardRules.Migrated::slotted).map(CardRules.Migrated::perk).toList();
		assertEquals(3, slotted.size());
		assertTrue(slotted.contains(TestCards.perk("mining/deep_delver")), "the Mastery fits with two of its side: " + slotted);
		assertTrue(small.cards().stream().filter(c -> c.perk().equals(TestCards.perk("blades/fencer"))).noneMatch(CardRules.Migrated::slotted),
			"Blades 0 has no slots");
		// Mining 20 (8 slots): everything slotted, deepest first.
		CardRules.Migration big = CardRules.migrate(held, TestCards.DECK, TestCards.DATA_SET, 1L, s -> 20, 3);
		assertEquals(7, big.cards().stream().filter(c -> c.def().skill().equals(MINING) && c.slotted()).count());
		assertEquals(Rarity.CURSED, big.cards().stream().filter(c -> c.perk().equals(TestCards.perk("mining/greedy_pick"))).findFirst().orElseThrow().card().rarity());
		CardRules.Migrated first = big.cards().stream().filter(c -> c.def().skill().equals(MINING) && c.index() == 0).findFirst().orElseThrow();
		assertEquals(Perk.Kind.MASTERY, first.def().kind(), "deepest first");
		// The same save always migrates the same way.
		assertEquals(big, CardRules.migrate(held, TestCards.DECK, TestCards.DATA_SET, 1L, s -> 20, 3));
	}
}
