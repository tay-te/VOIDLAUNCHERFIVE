package dev.voidmc.mettle.character.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.respec.RespecRules;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The perk draft's rules (docs/DRAFT.md) against the shipped decks. */
class DraftRulesTest {
	private static final DraftRules.Deck DECK = TestDeck.deck();
	private static final ResourceKey<Skill> MINING = TestDeck.skill("mining");
	private static final ResourceKey<Skill> BLADES = TestDeck.skill("blades");

	private static List<Card> deal(TestDeck.Sheet sheet, int n, List<ResourceKey<Skill>> recent) {
		return DraftRules.deal(MINING, sheet, DECK, DraftRules.random(42L, n), recent, Set.of());
	}

	private static Perk perk(ResourceKey<Perk> key) {
		return DECK.get(key).orElseThrow();
	}

	// ---------------------------------------------------------------- dealing

	@Test
	void handsNeverRepeatACardAndNeverOfferAMaxedPerk() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 20).hold("mining/quarryman", 3).hold("mining/hard_hat", 1);
		for (int n = 0; n < 2000; n++) {
			List<Card> hand = deal(sheet, n, List.of());
			assertEquals(DraftRules.HAND, hand.size(), "a full deck deals a full hand");
			assertEquals(hand.size(), new HashSet<>(hand.stream().map(Card::perk).toList()).size(), "duplicate in " + hand);
			for (Card c : hand) {
				assertNotEquals(TestDeck.perk("mining/quarryman"), c.perk(), "Quarryman is maxed");
				assertNotEquals(TestDeck.perk("mining/hard_hat"), c.perk(), "Hard Hat is held");
				assertEquals(sheet.rank(c.perk()) + 1, c.rank(), "a card gives the next rank");
				assertTrue(perk(c.perk()).kind().drafted(), "earned perks are never dealt: " + c.perk());
			}
		}
	}

	@Test
	void skillGatesDecideWhenDeepCardsAppear() {
		assertTrue(deal(new TestDeck.Sheet().level("mining", 1), 0, List.of()).isEmpty(), "nothing opens before tier I (skill 2): the draft waits");
		for (int level : new int[] {2, 5, 6, 11, 12, 17, 18}) {
			TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", level).hold("mining/sure_footing", 1).hold("mining/hard_hat", 1);
			int maxTier = 0;
			for (int n = 0; n < 600; n++) {
				for (Card c : deal(sheet, n, List.of())) {
					int tier = perk(c.perk()).tier();
					assertTrue(level >= Curves.tierSkillGate(tier), c.perk() + " (tier " + tier + ") dealt at skill " + level);
					maxTier = Math.max(maxTier, tier);
				}
			}
			int expected = level >= 18 ? 4 : level >= 12 ? 3 : level >= 6 ? 2 : 1;
			assertEquals(expected, maxTier, "deepest tier dealt at skill " + level);
		}
	}

	@Test
	void tierThreePathCardsNeedATierTwoOfTheirPath() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 12).hold("mining/sure_footing", 1);
		for (int n = 0; n < 1000; n++) {
			for (Card c : deal(sheet, n, List.of())) {
				Perk p = perk(c.perk());
				assertFalse(p.kind() == Perk.Kind.PATH && p.tier() == 3 && p.path().equals("b"), "Excavator tier III without an Excavator tier II: " + c.perk());
			}
		}
	}

	@Test
	void masteriesWaitForTheirPathAndExcludeEachOther() {
		ResourceKey<Perk> deep = TestDeck.perk("mining/deep_delver");
		ResourceKey<Perk> motherlode = TestDeck.perk("mining/motherlode");
		ResourceKey<Perk> breaker = TestDeck.perk("mining/mountain_breaker");
		// One path perk: no mastery yet.
		TestDeck.Sheet one = new TestDeck.Sheet().level("mining", 20).hold("mining/sure_footing", 1);
		assertTrue(DraftRules.candidates(MINING, one, DECK).stream().noneMatch(k -> perk(k).kind() == Perk.Kind.MASTERY));
		// Two Delver perks: Deep Delver (side a) and Motherlode (any two), not Mountain Breaker (side b).
		TestDeck.Sheet twoA = new TestDeck.Sheet().level("mining", 20).hold("mining/sure_footing", 1).hold("mining/hard_hat", 1);
		List<ResourceKey<Perk>> cards = DraftRules.candidates(MINING, twoA, DECK);
		assertTrue(cards.contains(deep) && cards.contains(motherlode) && !cards.contains(breaker), cards.toString());
		// Below skill 18 none, however many path perks.
		twoA.level("mining", 17);
		assertTrue(DraftRules.candidates(MINING, twoA, DECK).stream().noneMatch(k -> perk(k).kind() == Perk.Kind.MASTERY));
		// Holding one removes the others for good.
		twoA.level("mining", 20).hold("mining/motherlode", 1);
		for (int n = 0; n < 500; n++) {
			for (Card c : deal(twoA, n, List.of())) {
				assertNotEquals(Perk.Kind.MASTERY, perk(c.perk()).kind(), "a second mastery was dealt: " + c.perk());
			}
		}
		assertFalse(DraftRules.valid(new Card(deep, 1, Rarity.EPIC, false), twoA, DECK), "a mastery card in a hand goes bad once another is held");
	}

	@Test
	void aSpentDeckIsExhaustedButAnEmptyOneWaits() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 20);
		for (ResourceKey<Perk> k : DECK.of(MINING)) {
			Perk p = perk(k);
			if (p.kind() != Perk.Kind.MASTERY) {
				sheet.ranks.put(k, p.ranks());
			}
		}
		assertFalse(DraftRules.exhausted(MINING, sheet, DECK), "the masteries are still there");
		sheet.hold("mining/motherlode", 1);
		assertTrue(DraftRules.exhausted(MINING, sheet, DECK));
		assertTrue(DraftRules.candidates(MINING, sheet, DECK).isEmpty());
		assertFalse(DraftRules.exhausted(TestDeck.skill("speech"), sheet, DECK), "a skill with no deck yet keeps its drafts");
	}

	// ---------------------------------------------------------------- odds

	@Test
	void oddsAddUpAndClimbWithLevel() {
		for (int level = 0; level <= 20; level++) {
			Map<Rarity, Double> odds = DraftRules.odds(level);
			assertEquals(100.0, odds.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9, "level " + level);
			odds.values().forEach(v -> assertTrue(v > 0));
		}
		assertTrue(DraftRules.odds(20).get(Rarity.EPIC) > DraftRules.odds(0).get(Rarity.EPIC));
		assertTrue(DraftRules.odds(20).get(Rarity.RARE) > DraftRules.odds(0).get(Rarity.RARE));
		assertTrue(DraftRules.odds(20).get(Rarity.COMMON) < DraftRules.odds(0).get(Rarity.COMMON));
	}

	@Test
	void rarityFollowsDepth() {
		assertEquals(Rarity.COMMON, DraftRules.rarity(perk(TestDeck.perk("mining/quarryman"))));
		assertEquals(Rarity.RARE, DraftRules.rarity(perk(TestDeck.perk("mining/tunneler"))));
		assertEquals(Rarity.EPIC, DraftRules.rarity(perk(TestDeck.perk("mining/gem_cutter"))));
		assertEquals(Rarity.EPIC, DraftRules.rarity(perk(TestDeck.perk("mining/motherlode"))));
		assertEquals(Rarity.CURSED, DraftRules.rarity(perk(TestDeck.perk("mining/greedy_pick"))));
	}

	@Test
	void dealtRaritiesMatchTheAdvertisedOdds() {
		// Skill 20, both paths open, roots untouched: every rarity has cards.
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 20).hold("mining/sure_footing", 1).hold("mining/tunneler", 1);
		Map<Rarity, Double> want = DraftRules.slotOdds(MINING, sheet, DECK);
		Map<Rarity, Integer> got = new EnumMap<>(Rarity.class);
		Random random = new Random(7);
		int draws = 40_000;
		List<ResourceKey<Perk>> cards = DraftRules.candidates(MINING, sheet, DECK);
		for (int i = 0; i < draws; i++) {
			ResourceKey<Perk> k = DraftRules.draw(cards, 20, 20, DECK, random, Set.of(), Set.of());
			got.merge(DraftRules.rarity(perk(k)), 1, Integer::sum);
		}
		for (Rarity r : Rarity.values()) {
			double share = 100.0 * got.getOrDefault(r, 0) / draws;
			assertEquals(want.get(r), share, 1.0, r + " dealt " + share + "% vs " + want.get(r) + "%");
		}
		// With every rarity present the slot odds are the table's.
		DraftRules.odds(20).forEach((r, v) -> assertEquals(v, want.get(r), 1e-9, r.toString()));
	}

	@Test
	void attributeGatesAreSoft() {
		// Tier III cards (attribute gate 10) at attribute 9 are dealt 0.4 times as often as at 10, but still dealt.
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 12).hold("mining/sure_footing", 1).hold("mining/tunneler", 1);
		sheet.attribute = 10;
		double met = DraftRules.slotOdds(MINING, sheet, DECK).get(Rarity.EPIC);
		sheet.attribute = 9;
		double under = DraftRules.slotOdds(MINING, sheet, DECK).get(Rarity.EPIC);
		assertTrue(under > 0, "under the gate, still in the deck");
		assertTrue(under < met * 0.6, "under the gate, rarer: " + under + " vs " + met);
		assertEquals(DraftRules.UNDER_GATE_WEIGHT, DraftRules.attributeWeight(perk(TestDeck.perk("mining/gem_cutter")), 9));
		assertEquals(1.0, DraftRules.attributeWeight(perk(TestDeck.perk("mining/quarryman")), 1), "tier I has no attribute gate");
	}

	// ---------------------------------------------------------------- wildcards

	@Test
	void wildcardsComeFromRecentlyUsedSkills() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 8).level("blades", 8);
		int hands = 6000;
		int wild = 0;
		for (int n = 0; n < hands; n++) {
			List<Card> hand = deal(sheet, n, List.of(MINING, BLADES));
			long count = hand.stream().filter(Card::wildcard).count();
			assertTrue(count <= 1, "at most one wildcard a hand");
			if (count == 1) {
				wild++;
				Card last = hand.getLast();
				assertTrue(last.wildcard(), "the wildcard is the last card");
				assertEquals(BLADES, perk(last.perk()).skill(), "the wildcard comes from the other skill");
				hand.subList(0, hand.size() - 1).forEach(c -> assertEquals(MINING, perk(c.perk()).skill()));
			} else {
				hand.forEach(c -> assertEquals(MINING, perk(c.perk()).skill()));
			}
		}
		assertEquals(DraftRules.WILDCARD_CHANCE, (double) wild / hands, 0.015, "wildcard rate");
		// Without another recent skill there is never a wildcard.
		for (int n = 0; n < 1000; n++) {
			assertTrue(deal(sheet, n, List.of(MINING)).stream().noneMatch(Card::wildcard));
			assertTrue(deal(sheet, n, List.of()).stream().noneMatch(Card::wildcard));
		}
	}

	@Test
	void recentRemembersTheLastFourSkills() {
		DraftState d = DraftState.fresh(1, 0);
		for (String s : new String[] {"mining", "blades", "mining", "defense", "survival", "smithing"}) {
			d = d.used(TestDeck.skill(s));
		}
		assertEquals(List.of(TestDeck.skill("smithing"), TestDeck.skill("survival"), TestDeck.skill("defense"), MINING), d.recent());
		assertTrue(d.used(TestDeck.skill("smithing")) == d, "using the front skill again changes nothing (no resync)");
	}

	// ---------------------------------------------------------------- determinism and rerolls

	@Test
	void dealingIsDeterministic() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 14).hold("mining/hard_hat", 1).level("blades", 6);
		for (int n = 0; n < 50; n++) {
			assertEquals(deal(sheet, n, List.of(BLADES)), deal(sheet, n, List.of(BLADES)), "hand " + n);
		}
		// A new deck object (another load, another hash order) deals the same.
		DraftRules.Deck again = TestDeck.deck();
		assertEquals(deal(sheet, 3, List.of()), DraftRules.deal(MINING, sheet, again, DraftRules.random(42L, 3), List.of(), Set.of()));
		assertNotEquals(DraftRules.seed(java.util.UUID.randomUUID(), 7L), DraftRules.seed(java.util.UUID.randomUUID(), 7L));
	}

	@Test
	void aRerollDealsCardsTheLastHandDidNotHold() {
		TestDeck.Sheet sheet = new TestDeck.Sheet().level("mining", 12).hold("mining/hard_hat", 1);
		for (int n = 0; n < 500; n++) {
			List<Card> first = deal(sheet, n, List.of());
			Set<ResourceKey<Perk>> avoid = new HashSet<>();
			first.forEach(c -> avoid.add(c.perk()));
			List<Card> second = DraftRules.deal(MINING, sheet, DECK, DraftRules.random(42L, n + 1), List.of(), avoid);
			assertEquals(DraftRules.HAND, second.size());
			second.forEach(c -> assertFalse(avoid.contains(c.perk()), "reroll repeated " + c.perk()));
		}
		// With fewer cards left than a hand, the thrown-back ones top it up.
		TestDeck.Sheet thin = new TestDeck.Sheet().level("mining", 3);
		List<Card> first = DraftRules.deal(MINING, thin, DECK, DraftRules.random(1, 0), List.of(), Set.of());
		assertEquals(2, first.size(), "two roots open at skill 3");
		Set<ResourceKey<Perk>> avoid = new HashSet<>(first.stream().map(Card::perk).toList());
		assertEquals(2, DraftRules.deal(MINING, thin, DECK, DraftRules.random(1, 1), List.of(), avoid).size());
	}

	@Test
	void everyLevelToSixThenEveryOtherDealsADraft() {
		assertEquals(0, DraftRules.draftsFor(3, 3));
		assertEquals(1, DraftRules.draftsFor(0, 1));
		assertEquals(6, DraftRules.draftsFor(0, 6));
		assertEquals(0, DraftRules.draftsFor(6, 7), "level 7 brings a reroll instead");
		assertEquals(1, DraftRules.draftsFor(7, 8));
		assertEquals(13, DraftRules.draftsFor(0, 20), "thirteen drafts to mastery");
		assertEquals(DraftRules.draftsFor(0, 20), DraftRules.draftsFor(0, 9) + DraftRules.draftsFor(9, 20), "split level-ups add up");
		assertEquals(20, DraftRules.draftsFor(0, 20, Integer.MAX_VALUE), "with the lever off, every level");
	}

	@Test
	void rerollsAreCapped() {
		assertEquals(3, DraftRules.addRerolls(2, 1));
		assertEquals(DraftRules.MAX_REROLLS, DraftRules.addRerolls(4, 5));
		assertEquals(0, DraftRules.addRerolls(0, -3));
	}

	// ---------------------------------------------------------------- old saves and respecs

	@Test
	void unspentPointsBecomeDrafts() {
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		held.put(TestDeck.perk("mining/quarryman"), 3);   // 3 points
		held.put(TestDeck.perk("mining/tunneler"), 1);    // 2 points
		held.put(TestDeck.perk("mining/diamond_hands"), 1); // earned: free
		held.put(TestDeck.perk("blades/fencer"), 2);      // 2 points in Blades
		assertEquals(4, DraftRules.legacyPoints(9, held, DECK, MINING));
		assertEquals(0, DraftRules.legacyPoints(4, held, DECK, MINING), "never below zero");
		Map<ResourceKey<Skill>, Integer> levels = Map.of(MINING, 9, BLADES, 5, TestDeck.skill("defense"), 0);
		DraftState d = DraftRules.migrate(99L, levels.keySet(), levels, held, DECK);
		assertEquals(4, d.queued(MINING));
		assertEquals(3, d.queued(BLADES));
		assertEquals(0, d.queued(TestDeck.skill("defense")));
		assertEquals(DraftRules.START_REROLLS, d.rerolls());
		assertEquals(99L, d.seed());
		assertTrue(d.hands().isEmpty(), "hands are dealt by the server after migrating");
	}

	@Test
	void respecTurnsEveryRankBackIntoADraft() {
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		held.put(TestDeck.perk("mining/quarryman"), 3);
		held.put(TestDeck.perk("mining/tunneler"), 1);
		held.put(TestDeck.perk("mining/motherlode"), 1);
		held.put(TestDeck.perk("mining/greedy_pick"), 1);
		held.put(TestDeck.perk("mining/diamond_hands"), 1);
		held.put(TestDeck.perk("blades/fencer"), 2);
		RespecRules.Forgetting f = RespecRules.forget(held, DECK::get, MINING);
		assertEquals(6, f.drafts(), "quarryman 3 + tunneler + motherlode + greedy pick");
		assertTrue(f.mastery());
		assertEquals(Map.of(TestDeck.perk("mining/diamond_hands"), 1, TestDeck.perk("blades/fencer"), 2), f.kept());
		Map<ResourceKey<Skill>, Integer> back = DraftRules.returned(held, DECK);
		assertEquals(6, back.get(MINING));
		assertEquals(2, back.get(BLADES));
	}

	// ---------------------------------------------------------------- cursed cards

	@Test
	void everyReleasedSkillHasTwoCursedCardsWithADownside() throws java.io.IOException {
		com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
			java.nio.file.Path.of("src/main/resources/assets/mettle/lang/en_us/draft.json"))).getAsJsonObject();
		Map<ResourceKey<Skill>, Integer> count = new HashMap<>();
		TestDeck.shipped().forEach((k, p) -> {
			if (p.kind() == Perk.Kind.CURSED) {
				String base = "mettle.perk." + k.identifier().getPath().replace('/', '.');
				for (String key : new String[] {base, base + ".description", base + ".downside"}) {
					assertTrue(lang.has(key), "missing lang " + key);
				}
				count.merge(p.skill(), 1, Integer::sum);
				assertTrue(p.downside().isPresent(), k + " has no downside");
				assertEquals(1, p.ranks(), k.toString());
				assertTrue(p.tier() == 2 || p.tier() == 3, k + " tier " + p.tier());
				assertEquals(Rarity.CURSED, DraftRules.rarity(p));
			} else {
				assertTrue(p.downside().isEmpty(), k + " is not cursed but has a downside");
			}
		});
		for (String s : TestDeck.RELEASED) {
			assertEquals(2, count.getOrDefault(TestDeck.skill(s), 0), s);
		}
	}

	@Test
	void cursedNumbersCutBothWays() {
		assertEquals(1f, Cursed.drawSpeed(false, false));
		assertEquals(Cursed.HEAVY_DRAW_SPEED, Cursed.drawSpeed(true, false));
		assertTrue(Cursed.drawSpeed(true, false) < 1f && Cursed.arrowDamage(true, false) > 1f, "Heavy Draw: slower, harder");
		assertTrue(Cursed.drawSpeed(false, true) > 1f && Cursed.arrowDamage(false, true) < 1f, "Snap Shot: faster, softer");
		assertEquals(Cursed.HEAVY_DRAW_DAMAGE * Cursed.SNAP_SHOT_DAMAGE, Cursed.arrowDamage(true, true), 1e-6);
		assertTrue(Cursed.WOLFS_HUNGER_DRAIN > 1f);
		assertTrue(Cursed.SLAPDASH_XP > 1 && Cursed.SLAPDASH_DURABILITY < 0);
		assertTrue(Cursed.BRITTLE_EDGE_DAMAGE > 0 && Cursed.BRITTLE_EDGE_DURABILITY < 0);
		// Brittle Edge halves a crafted weapon's durability, Slapdash takes a quarter, together three quarters.
		var none = dev.voidmc.mettle.character.skill.smithing.Forged.Reforge.NONE;
		var brittle = dev.voidmc.mettle.character.skill.smithing.Forged.Mark.BRITTLE_EDGE;
		var slapdash = dev.voidmc.mettle.character.skill.smithing.Forged.Mark.SLAPDASH;
		assertEquals(125, dev.voidmc.mettle.character.skill.smithing.SmithingRules.maxDamage(250, new dev.voidmc.mettle.character.skill.smithing.Forged("x", List.of(brittle), none)));
		assertEquals(188, dev.voidmc.mettle.character.skill.smithing.SmithingRules.maxDamage(250, new dev.voidmc.mettle.character.skill.smithing.Forged("x", List.of(slapdash), none)));
		assertEquals(63, dev.voidmc.mettle.character.skill.smithing.SmithingRules.maxDamage(250, new dev.voidmc.mettle.character.skill.smithing.Forged("x", List.of(brittle, slapdash), none)));
		// Attribute cursed cards: an upside and a downside in their data.
		for (String id : new String[] {"blades/reckless_edge", "blades/glass_dancer", "heavy_weapons/bloodrage", "heavy_weapons/immovable",
			"defense/iron_hide", "defense/bastion", "athletics/featherweight", "athletics/bulls_back", "survival/wild_luck", "mining/black_lung"}) {
			Perk p = perk(TestDeck.perk(id));
			boolean up = false;
			boolean down = false;
			for (Perk.AttributeEffect e : p.effects()) {
				double v = e.amounts().getFirst();
				boolean bad = e.attribute().getPath().equals("fall_damage_multiplier") ? v > 0 : v < 0;
				up |= !bad;
				down |= bad;
			}
			assertTrue(up && down, id + " should carry both halves in data: " + p.effects());
		}
	}
}
