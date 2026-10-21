package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.BynameWords;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.BuildState;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Build feel's rules (docs/BUILD_FEEL.md §10 "Tests"): momentum, the 6 tier's skill-12 gate, the second keyword's lean,
 * boons (Temper, Inscribe, the odd-level boon), bynames, the build preview and summary, shards and the forge (one of a
 * kind, the race for the last one), the guide, and that the new records load old saves and never fail.
 */
class BuildFeelRulesTest {
	static final CardRules.Data DATA = TestCards.DATA_SET;
	static final ResourceKey<Skill> MINING = TestCards.skill("mining");
	static final ResourceKey<Skill> DEFENSE = TestCards.skill("defense");
	static final ResourceKey<Skill> SMITHING = TestCards.skill("smithing");
	static final Identifier DEEP = TestCards.id("deep");
	static final Identifier EMBER = TestCards.id("ember");
	static final Identifier BRUTAL = TestCards.id("brutal");
	static final Identifier TIDE = TestCards.id("tide");

	@BeforeAll
	static void lang() throws IOException {
		Map<String, String> lang = new TreeMap<>();
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/mettle/lang/en_us"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
				JsonParser.parseString(Files.readString(p)).getAsJsonObject().entrySet().forEach(e -> lang.put(e.getKey(), e.getValue().getAsString()));
			}
		}
		Language.inject(new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return lang.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return lang.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return FormattedCharSequence.EMPTY;
			}
		});
	}

	// ---------------------------------------------------------------- momentum (§1)

	static TestCards.Sheet sheet(int level) {
		TestCards.Sheet s = new TestCards.Sheet();
		for (String k : TestCards.RELEASED) {
			s.level(k, level);
		}
		return s;
	}

	static CardRules.Commitment commit(Object... kv) {
		Map<Identifier, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put(TestCards.id((String) kv[i]), Math.min(CardConfig.get().momentum_cap, (Integer) kv[i + 1]));
		}
		return new CardRules.Commitment(m);
	}

	@Test
	void noCommitmentDealsTodaysHand() {
		TestCards.Sheet s = sheet(14);
		for (int seed = 0; seed < 400; seed++) {
			for (String skill : TestCards.RELEASED) {
				List<Card> today = CardRules.deal(TestCards.skill(skill), s, TestCards.DECK, DATA, DraftRules.random(seed, 3), List.of(MINING, DEFENSE), Set.of());
				List<Card> none = CardRules.deal(TestCards.skill(skill), s, TestCards.DECK, DATA, DraftRules.random(seed, 3), List.of(MINING, DEFENSE), Set.of(),
					CardRules.Commitment.NONE);
				// Committed only to keywords with no momentum: nothing leans.
				List<Card> common = CardRules.deal(TestCards.skill(skill), s, TestCards.DECK, DATA, DraftRules.random(seed, 3), List.of(MINING, DEFENSE), Set.of(),
					commit("brutal", 4, "iron", 4, "hoard", 3, "hex", 4));
				assertEquals(today, none, skill + " seed " + seed);
				assertEquals(today, common, skill + " seed " + seed + " (momentum 0)");
			}
		}
	}

	@Test
	void bandTotalsNeverMoveAndNoPerkDropsBelowHalf() {
		Random r = new Random(11);
		List<Identifier> keywords = new ArrayList<>(TestCards.KEYWORDS.keySet());
		for (int run = 0; run < 100_000; run++) {
			String skill = TestCards.RELEASED[r.nextInt(TestCards.RELEASED.length)];
			TestCards.Sheet s = sheet(1 + r.nextInt(20));
			List<ResourceKey<Perk>> pool = CardRules.candidates(TestCards.skill(skill), s, TestCards.DECK);
			if (pool.isEmpty()) {
				continue;
			}
			Map<Identifier, Integer> c = new HashMap<>();
			for (int i = 0; i < 1 + r.nextInt(4); i++) {
				c.put(keywords.get(r.nextInt(keywords.size())), 1 + r.nextInt(6));
			}
			c.replaceAll((k, v) -> Math.min(4, v));
			double[] base = CardRules.weights(pool, s.level(TestCards.skill(skill)), 20, s, TestCards.DECK, CardRules.Commitment.NONE, DATA);
			double[] lean = CardRules.weights(pool, s.level(TestCards.skill(skill)), 20, s, TestCards.DECK, new CardRules.Commitment(c), DATA);
			Map<Rarity, double[]> bands = new HashMap<>();
			for (int i = 0; i < pool.size(); i++) {
				double[] b = bands.computeIfAbsent(DraftRules.rarity(TestCards.def(pool.get(i))), x -> new double[2]);
				b[0] += base[i];
				b[1] += lean[i];
				assertTrue(lean[i] >= base[i] / 2 - 1e-12, "a perk below half its old weight: " + pool.get(i));
			}
			for (double[] b : bands.values()) {
				assertEquals(b[0], b[1], 1e-9 * Math.max(1, b[0]), "a depth band's total moved");
			}
		}
	}

	@Test
	void leanGrowsWithMomentumAndCapsAtFour() {
		assertEquals(2.0, CardRules.perkLean(TIDE, commit("tide", 4), DATA), 1e-9);
		assertEquals(1.5, CardRules.perkLean(TIDE, commit("tide", 2), DATA), 1e-9);
		assertEquals(1.0, CardRules.perkLean(BRUTAL, commit("brutal", 4), DATA), 1e-9);
		assertEquals(1.0, CardRules.perkLean(CardRules.HEX, commit("hex", 4), DATA), 1e-9, "Hex never leans");
		assertEquals(1 + 0.5 * 0.5 * 4, CardRules.keywordLean(TestCards.id("kin"), commit("kin", 4), DATA), 1e-9);
		// The cap: ten Deep cards count as four.
		List<CardData> deep = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			deep.add(TestCards.plain("mining/lantern_eyes", Rarity.COMMON, i));
		}
		assertEquals(4, CardRules.Commitment.of(deep, List.of(), Optional.empty()).of(DEEP));
		assertEquals(0, TestCards.KEYWORDS.get(CardRules.HEX).momentum(), 1e-9);
		for (CardSkillCheck c : CardSkillCheck.all()) {
			assertFalse(c.pool().contains(CardRules.HEX), c.skill() + " pool holds Hex");
		}
	}

	record CardSkillCheck(String skill, List<Identifier> pool) {
		static List<CardSkillCheck> all() {
			List<CardSkillCheck> out = new ArrayList<>();
			for (String s : TestCards.RELEASED) {
				out.add(new CardSkillCheck(s, DATA.pool(TestCards.skill(s))));
			}
			return out;
		}
	}

	@Test
	void focusCountsForMomentumNeverForSets() {
		CardData a = TestCards.plain("mining/lantern_eyes", Rarity.COMMON, 0);
		CardRules.Commitment c = CardRules.Commitment.of(List.of(a), List.of(), Optional.of(DEEP));
		assertEquals(1 + CardConfig.get().focus_weight, c.of(DEEP));
		CardRules.Derived d = CardRules.derive(List.of(a), s -> 20, 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, List.of(), List.of());
		assertEquals(1, d.sets().get(DEEP));
		assertTrue(d.tiers().isEmpty(), "a Focus made a set");
	}

	@Test
	void dealsAreDeterministicFromSeedAndCommitment() {
		TestCards.Sheet s = sheet(12);
		CardRules.Commitment c = commit("deep", 3, "tide", 2);
		for (int seed = 0; seed < 200; seed++) {
			assertEquals(CardRules.deal(MINING, s, TestCards.DECK, DATA, DraftRules.random(seed, 1), List.of(), Set.of(), c),
				CardRules.deal(MINING, s, TestCards.DECK, DATA, DraftRules.random(seed, 1), List.of(), Set.of(), c));
		}
	}

	@Test
	void secondKeywordChanceIsUnchangedAndLeansAsDesigned() {
		Perk tunneler = TestCards.def(TestCards.perk("athletics/swimmer"));
		ResourceKey<Perk> key = TestCards.perk("athletics/swimmer");
		CardRules.Commitment c = commit("gale", 4);
		int n = 100_000;
		int[] second = new int[2];
		Map<Identifier, Integer> picks = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Card none = CardRules.roll(key, tunneler, Rarity.EPIC, DATA, new Random(i), false, CardRules.Commitment.NONE);
			Card lean = CardRules.roll(key, tunneler, Rarity.EPIC, DATA, new Random(i), false, c);
			second[0] += none.keywords().size() > 1 ? 1 : 0;
			second[1] += lean.keywords().size() > 1 ? 1 : 0;
			if (lean.keywords().size() > 1) {
				picks.merge(lean.keywords().get(1), 1, Integer::sum);
			}
		}
		assertEquals(second[0], second[1], "the chance of a second keyword moved");
		assertEquals(0.7, second[0] / (double) n, 0.01);
		// Athletics' pool without its native Tide: Swift, Gale, Hoard, Kin. Gale 4 at momentum 0.25: 1 + 0.5 × 0.25 × 4 = 1.5.
		double total = 1.5 + 1 + 1 + 1;
		assertEquals(1.5 / total, picks.get(TestCards.id("gale")) / (double) second[1], 0.01);
		assertEquals(1 / total, picks.get(TestCards.id("swift")) / (double) second[1], 0.01);
	}

	// ---------------------------------------------------------------- the skill-12 gate (§1.2b)

	static CardData in(String perk, String deck, int index, String... extra) {
		CardData c = TestCards.plain(perk, Rarity.COMMON, -1, extra);
		return c.withPlace(CardData.Place.slotted(TestCards.skill(deck), index));
	}

	@Test
	void sixWakesWhenTwoOfItsDecksReachTwelve() {
		// Six Deep cards: four in Mining, two in Survival.
		List<CardData> cards = List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1), in("mining/tunneler", "mining", 2, "deep"),
			in("mining/quarryman", "mining", 3, "deep"), in("survival/field_cook", "survival", 0, "deep"), in("survival/gatherer", "survival", 1, "deep"));
		Map<ResourceKey<Skill>, Integer> levels = new HashMap<>(Map.of(MINING, 20, TestCards.skill("survival"), 11));
		CardRules.Derived low = CardRules.derive(cards, s -> levels.getOrDefault(s, 0), 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA,
			List.of(), List.of());
		assertEquals(6, low.sets().get(DEEP));
		assertEquals(4, low.tiers().get(DEEP), "the 6 counted below the gate");
		assertTrue(CardRules.sixAsleep(6, Set.of(MINING, TestCards.skill("survival")), s -> levels.getOrDefault(s, 0)));
		levels.put(TestCards.skill("survival"), 12);
		CardRules.Derived high = CardRules.derive(cards, s -> levels.getOrDefault(s, 0), 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA,
			List.of(), List.of());
		assertEquals(6, high.tiers().get(DEEP), "the 6 didn't wake at 12");
		assertFalse(CardRules.sixAsleep(6, Set.of(MINING, TestCards.skill("survival")), s -> levels.getOrDefault(s, 0)));
	}

	// ---------------------------------------------------------------- boons (§3)

	static CardData deepborn(Rarity rarity, double value, int index) {
		return TestCards.withTraits(TestCards.plain("mining/tunneler", rarity, index), new TraitRoll(TestCards.id("mining/deepborn"), value));
	}

	@Test
	void temperRaisesAQuarterOfTheRangeToTheBandTop() {
		Trait deepborn = TestCards.TRAITS.get(TestCards.id("mining/deepborn"));
		assertEquals(10, CardRules.tempered(deepborn, Rarity.RARE, 8), 1e-9, "Deepborn 8 → 10 % on a Rare");
		assertEquals(9, CardRules.tempered(deepborn, Rarity.COMMON, 8), 1e-9, "a Common stops at the top of its band");
		assertEquals(12, CardRules.tempered(deepborn, Rarity.EPIC, 11), 1e-9, "never above the max");
		for (Trait t : TestCards.TRAITS.values()) {
			for (Rarity r : List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC, Rarity.CURSED)) {
				double v = CardRules.band(t, r)[0];
				double after = CardRules.tempered(t, r, v);
				assertTrue(after <= t.max() + 1e-9 && after <= CardRules.bandTop(t, r) + 1e-9, t.name().getString() + " passed its band");
				double grid = t.step() <= 0 ? 1 : t.step();
				assertEquals(0, Math.abs(after / grid - Math.round(after / grid)), 1e-6, "off the step grid");
			}
		}
	}

	@Test
	void temperOncePerCardAndThreeADeck() {
		Identifier trait = TestCards.id("mining/deepborn");
		CardData a = deepborn(Rarity.RARE, 8, 0);
		assertTrue(CardRules.canTemper(a, trait, List.of(a), DATA, 0).isEmpty());
		CardData tempered = a.withBuild(a.keywords(), CardRules.temper(a, trait, DATA), a.marks().withTempered(new CardData.Tempered(trait, 8, 10, "T", 9, 1)));
		assertEquals(10, tempered.traits().getFirst().value(), 1e-9);
		assertEquals(Optional.of(CardRules.Refusal.ALREADY_TEMPERED), CardRules.canTemper(tempered, trait, List.of(tempered), DATA, 0));
		assertEquals(Optional.of(CardRules.Refusal.NO_TRAIT), CardRules.canTemper(a, TestCards.id("mining/lucky"), List.of(a), DATA, 0));
		assertEquals(Optional.of(CardRules.Refusal.AT_TOP), CardRules.canTemper(deepborn(Rarity.COMMON, 9, -1), trait, List.of(), DATA, 0));
		// Three tempered cards in the deck: a fourth is refused, both as a Temper and as a slot.
		List<CardData> deck = new ArrayList<>();
		String[] perks = {"mining/quarryman", "mining/prospector", "mining/lantern_eyes"};
		for (int i = 0; i < 3; i++) {
			CardData c = TestCards.withTraits(TestCards.plain(perks[i], Rarity.RARE, i), new TraitRoll(trait, 6));
			deck.add(c.withMarks(c.marks().withTempered(new CardData.Tempered(trait, 5, 6, "T", 9, 1))));
		}
		CardData fourth = deepborn(Rarity.RARE, 8, 3);
		List<CardData> withFourth = new ArrayList<>(deck);
		withFourth.add(fourth);
		assertEquals(Optional.of(CardRules.Refusal.TEMPERED_FULL), CardRules.canTemper(fourth, trait, withFourth, DATA, 0));
		CardData binderTempered = tempered.withPlace(CardData.Place.BINDER);
		assertEquals(Optional.of(CardRules.Refusal.TEMPERED_FULL), CardRules.canSlot(binderTempered, -1, deck, 20, 3, 0,
			k -> Optional.ofNullable(TestCards.PERKS.get(k))));
		// A scar can take a tempered trait; the mark stays (once per card). Fusion makes a new card: no marks.
		CardData scarred = CardRules.scarred(tempered, false);
		assertTrue(scarred.tempered());
		assertTrue(scarred.traits().isEmpty());
		Card fused = CardRules.fuse(List.of(tempered, tempered, tempered), TestCards.def(tempered.perk()), DATA, new Random(1));
		assertEquals(10, fused.traits().stream().filter(t -> t.trait().equals(trait)).findFirst().orElseThrow().value(), 1e-9,
			"fusion keeps the best trait at its tempered value");
	}

	@Test
	void inscribeOnlyFromThePoolWithASeedOncePerCardOneADeck() {
		CardData common = in("mining/quarryman", "mining", 0);
		CardData seed = in("mining/lantern_eyes", "mining", 1);
		List<CardData> deck = List.of(common, seed);
		assertTrue(CardRules.canInscribe(common, DEEP, deck, DATA, 0).isEmpty(), "a Common takes a second keyword");
		assertEquals(List.of(BRUTAL, DEEP), CardRules.inscribe(common, DEEP));
		assertEquals(Optional.of(CardRules.Refusal.NEEDS_SEED), CardRules.canInscribe(common, EMBER, deck, DATA, 0), "no Ember card in the deck");
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_POOL), CardRules.canInscribe(common, TIDE, deck, DATA, 0));
		assertEquals(Optional.of(CardRules.Refusal.NOT_IN_POOL), CardRules.canInscribe(common, CardRules.HEX, deck, DATA, 0));
		assertEquals(Optional.of(CardRules.Refusal.NATIVE_KEYWORD), CardRules.canInscribe(common, BRUTAL, deck, DATA, 0));
		CardData done = common.withBuild(CardRules.inscribe(common, DEEP), common.traits(),
			common.marks().withInscribed(new CardData.Inscribed(DEEP, Optional.empty(), "T", 9, 1)));
		assertEquals(Optional.of(CardRules.Refusal.ALREADY_INSCRIBED), CardRules.canInscribe(done, EMBER, List.of(done, seed), DATA, 0));
		CardData other = in("mining/prospector", "mining", 2);
		assertEquals(Optional.of(CardRules.Refusal.INSCRIBED_FULL), CardRules.canInscribe(other, DEEP, List.of(done, seed, other), DATA, 0));
		// A cursed card keeps Hex and may have its second inscribed.
		CardData cursed = in("mining/greedy_pick", "mining", 3, "hoard");
		assertTrue(CardRules.canInscribe(cursed, DEEP, List.of(cursed, seed), DATA, 0).isEmpty());
		assertEquals(List.of(CardRules.HEX, DEEP), CardRules.inscribe(cursed, DEEP));
		// The slot rule: an inscribed card can't join a deck that holds one.
		CardData otherInscribed = other.withPlace(CardData.Place.BINDER).withMarks(done.marks());
		assertEquals(Optional.of(CardRules.Refusal.INSCRIBED_FULL), CardRules.canSlot(otherInscribed, -1,
			List.of(done.withPlace(CardData.Place.slotted(MINING, 0))), 20, 3, 0, k -> Optional.ofNullable(TestCards.PERKS.get(k))));
	}

	@Test
	void oddLevelsFromSevenGiveBoonsAndOldRerollsStay() {
		DraftState d = DraftState.fresh(1, 3);
		DraftState after = CardRules.levelled(d, MINING, 6, 11);
		assertEquals(3, after.boons(MINING), "levels 7, 9 and 11 give boons");
		assertEquals(3, after.rerolls(), "no rerolls from odd levels any more");
		assertEquals(3, CardRules.levelled(d, MINING, 5, 6).rerolls());
		CardConfig off = new CardConfig();
		off.boons_from = 0;
		CardConfig.use(off);
		try {
			DraftState old = CardRules.levelled(d, MINING, 6, 9);
			assertEquals(0, old.boons(MINING));
			assertEquals(5, old.rerolls(), "boons off: odd levels give rerolls as before");
		} finally {
			CardConfig.reset();
		}
		// An old save has no boons; its rerolls stay rerolls.
		DraftState read = DraftState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"rerolls\": 4, \"seed\": 9}")).getOrThrow();
		assertEquals(4, read.rerolls());
		assertEquals(0, read.totalBoons());
	}

	// ---------------------------------------------------------------- bynames (§2.1)

	/** The byname words from the shipped tables, through lang. */
	static Bynames.Words words() {
		Map<String, BynameWords> tables = new HashMap<>();
		for (String t : List.of("keywords", "skills", "pairs")) {
			try {
				tables.put(t, BynameWords.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(TestCards.DATA.resolve("byname/" + t + ".json"))))
					.getOrThrow());
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		Map<Identifier, dev.voidmc.mettle.api.Mythic> mythics = TestCardsMythics.load();
		return new Bynames.Words() {
			BynameWords.Word w(String table, String key) {
				BynameWords.Word x = tables.get(table).words().get(key);
				assertTrue(x != null, table + " has no " + key);
				return x;
			}

			@Override
			public Component prefix(Identifier keyword) {
				return w("keywords", keyword.toString()).prefix().orElseThrow();
			}

			@Override
			public Component word(Identifier keyword) {
				return w("keywords", keyword.toString()).word().orElseThrow();
			}

			@Override
			public Component suffix(ResourceKey<Skill> skill) {
				return w("skills", skill.identifier().toString()).suffix().orElseThrow();
			}

			@Override
			public Component noun(ResourceKey<Skill> skill) {
				return w("skills", skill.identifier().toString()).noun().orElseThrow();
			}

			@Override
			public Optional<Component> pair(ResourceKey<Skill> a, ResourceKey<Skill> b) {
				BynameWords.Word x = tables.get("pairs").words().get(Bynames.pairKey(a, b));
				if (x == null) {
					x = tables.get("pairs").words().get(Bynames.pairKey(b, a));
				}
				return x == null ? Optional.empty() : x.noun();
			}

			@Override
			public Component description(ResourceKey<Skill> a, Optional<ResourceKey<Skill>> b) {
				return Component.literal("");
			}

			@Override
			public Component epithet(Identifier mythic) {
				return mythics.get(mythic).epithet();
			}
		};
	}

	@Test
	void everyKeywordSkillAndPairHasItsWords() {
		Bynames.Words w = words();
		Language lang = Language.getInstance();
		for (Identifier k : TestCards.KEYWORDS.keySet()) {
			for (String s : TestCards.RELEASED) {
				Component name = Bynames.render(new Byname(1, k, Optional.empty(), TestCards.skill(s), Optional.empty(), Optional.empty()), w);
				assertFalse(name.getString().contains("mettle."), "untranslated: " + name.getString());
				assertTrue(name.getString().startsWith("the "), name.getString());
			}
		}
		for (String a : TestCards.RELEASED) {
			for (String b : TestCards.RELEASED) {
				if (!a.equals(b)) {
					assertTrue(w.pair(TestCards.skill(a), TestCards.skill(b)).isPresent(), "no pair noun for " + a + " + " + b);
				}
			}
		}
		for (var m : TestCardsMythics.load().entrySet()) {
			assertFalse(m.getValue().epithet().getString().isBlank(), m.getKey() + " has no epithet");
			assertTrue(lang.has("mettle.mythic." + m.getKey().getPath() + ".epithet"));
		}
		for (Keyword k : TestCards.KEYWORDS.values()) {
			for (int t : new int[] {2, 4, 6}) {
				assertTrue(k.sets().get(t).summary().isPresent(), k.name().getString() + " " + t + " has no summary line");
			}
		}
	}

	static Bynames.Build build(List<CardData> cards, int levels) {
		CardRules.Derived d = CardRules.derive(cards, s -> levels, 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, List.of(), List.of());
		return Bynames.build(cards, d, Optional.empty(), k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, x -> 10);
	}

	@Test
	void theDeepdelverGrowsIntoTheDeepEmberWardenOfTheMountain() {
		Bynames.Words w = words();
		List<CardData> cards = new ArrayList<>(List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1)));
		Bynames.Build b = build(cards, 20);
		int tier = Bynames.earned(b, 0);
		assertEquals(1, tier);
		assertEquals("the Deepdelver", Bynames.render(Bynames.of(b, tier).orElseThrow(), w).getString());
		// Deep 4 and Ember 2: the Ember Deepdelver.
		cards.add(in("mining/quarryman", "mining", 2, "deep"));
		cards.add(in("mining/tunneler", "mining", 3, "deep"));
		cards.add(in("mining/rock_hauler", "mining", 4, "ember"));
		cards.add(in("smithing/tinker", "smithing", 0));
		b = build(cards, 20);
		tier = Bynames.earned(b, tier);
		assertEquals(2, tier);
		assertEquals("the Ember Deepdelver", Bynames.render(Bynames.of(b, tier).orElseThrow(), w).getString());
		// Deep 6 across Mining and Defense (both at 20), Defense heavy enough to be skill2 (7 points against 11): the Deep Ember Warden.
		cards.add(in("defense/second_wind", "defense", 0, "deep"));
		cards.add(in("defense/mender", "defense", 1, "deep"));
		cards.add(in("defense/perfect_block", "defense", 2));
		b = build(cards, 20);
		tier = Bynames.earned(b, tier);
		assertEquals(3, tier);
		assertEquals("the Deep Ember Warden", Bynames.render(Bynames.of(b, tier).orElseThrow(), w).getString());
		// A Mythic slotted: of the Mountain.
		CardData mythic = mythic("heart_of_the_mountain", "mining", 5);
		cards.add(mythic);
		b = build(cards, 20);
		tier = Bynames.earned(b, tier);
		assertEquals(4, tier);
		assertEquals("the Deep Ember Warden of the Mountain", Bynames.render(Bynames.of(b, tier).orElseThrow(), w).getString());
		// Tiers never drop: rebuild down to one 2-set and the tier stays; the words follow the build.
		List<CardData> small = List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1));
		Bynames.Build sb = build(small, 20);
		assertEquals(4, Bynames.earned(sb, tier));
		assertEquals("the Deep Delver", Bynames.render(Bynames.of(sb, Bynames.earned(sb, tier)).orElseThrow(), w).getString());
	}

	static CardData mythic(String id, String deck, int index) {
		CardData c = TestCards.plain("mining/quarryman", Rarity.COMMON, -1);
		return new CardData(c.id(), TestCards.skill(deck), MythicRules.key(TestCards.id(id)), Rarity.MYTHIC, 1, List.of(), List.of(), 0, c.drawn(), c.owner(),
			c.history(), 0, 1, CardData.Place.slotted(TestCards.skill(deck), index), 0);
	}

	@Test
	void bynameKeywordsOrderByTierThenCardsThenFocusThenFirst() {
		List<CardData> cards = List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1), in("mining/quarryman", "mining", 2),
			in("mining/prospector", "mining", 3, "brutal"));
		CardRules.Derived d = CardRules.derive(cards, s -> 20, 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, List.of(), List.of());
		Bynames.Build plain = Bynames.build(cards, d, Optional.empty(), k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, x -> 10);
		Bynames.Build focused = Bynames.build(cards, d, Optional.of(DEEP), k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, x -> 10);
		// Brutal 2 and Deep 2 tie on tier and cards: the one counted first, unless the Focus is the other.
		assertEquals(d.order().indexOf(BRUTAL) < d.order().indexOf(DEEP) ? BRUTAL : DEEP, Bynames.keywords(plain).getFirst());
		assertEquals(DEEP, Bynames.keywords(focused).getFirst());
	}

	// ---------------------------------------------------------------- the preview and the summary (§2.3, §6.1)

	static BuildPreview.Context ctx(List<CardData> cards, int level) {
		return new BuildPreview.Context(cards, s -> level, 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, List.of(), List.of(),
			Optional.empty(), 0, 20, 1, List.of(), 0);
	}

	@Test
	void previewSaysWhereItGoesAndWhatItOpensWithDerivesNumbers() {
		List<CardData> deck = List.of(in("mining/lantern_eyes", "mining", 0), deepborn(Rarity.RARE, 8, 1));
		CardData offer = TestCards.withTraits(TestCards.plain("mining/ore_sense", Rarity.RARE, -1), new TraitRoll(TestCards.id("mining/deepborn"), 10));
		BuildPreview.Preview p = BuildPreview.of(ctx(deck, 14), offer).orElseThrow();
		assertEquals(BuildPreview.Place.FILL, p.placement().kind());
		assertEquals(2, p.placement().slot());
		BuildPreview.SetDelta deep = p.sets().stream().filter(s -> s.keyword().equals(DEEP)).findFirst().orElseThrow();
		assertEquals(1, deep.from());
		assertEquals(2, deep.to());
		assertTrue(deep.opens(), "Deep 2 opens");
		BuildPreview.TraitDelta t = p.traits().getFirst();
		List<CardData> after = new ArrayList<>(deck);
		after.add(offer.withPlace(CardData.Place.slotted(MINING, 2)));
		CardRules.Derived real = ctx(after, 14).derive(after);
		assertEquals(real.traits().get(TestCards.id("mining/deepborn")), t.to(), 1e-9, "the preview's total is what derive applies");
		// Waits below its requirement; replaces a copy of its own perk.
		CardData epic = TestCards.plain("mining/quarryman", Rarity.EPIC, -1);
		assertEquals(BuildPreview.Place.WAITS, BuildPreview.of(ctx(deck, 3), epic).orElseThrow().placement().kind());
		List<CardData> withQ = List.of(in("mining/quarryman", "mining", 0), in("mining/lantern_eyes", "mining", 1));
		BuildPreview.Preview rep = BuildPreview.of(ctx(withQ, 12), TestCards.plain("mining/quarryman", Rarity.RARE, -1)).orElseThrow();
		assertEquals(BuildPreview.Place.REPLACE, rep.placement().kind());
		assertEquals(Optional.of(CardRules.FreeWay.FREE_SWAP), rep.placement().free());
		// Breaking a set: replacing the second Deep card with a Brutal one loses Deep 2.
		List<CardData> two = List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1), in("mining/quarryman", "mining", 2));
		BuildPreview.Preview broke = BuildPreview.change(ctx(two, 12), two.get(1), two.get(1).withPlace(CardData.Place.BINDER));
		assertTrue(broke.sets().stream().anyMatch(s -> s.keyword().equals(DEEP) && s.breaks()));
	}

	@Test
	void summaryListsSetsThenTraitsThenTheMasteryWithDerivedNumbers() {
		List<CardData> cards = List.of(in("mining/lantern_eyes", "mining", 0), in("mining/ore_sense", "mining", 1), deepborn(Rarity.EPIC, 12, 2));
		CardRules.Derived d = CardRules.derive(cards, s -> 20, 3, x -> 10, k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, List.of(), List.of());
		Bynames.Build b = Bynames.build(cards, d, Optional.empty(), k -> Optional.ofNullable(TestCards.PERKS.get(k)), DATA, x -> 10);
		BuildSummary.Summary s = BuildSummary.of(b, d, DATA, Optional.empty(), 1, 3, 30);
		assertEquals(BuildSummary.Kind.SET, s.lines().getFirst().kind());
		assertEquals(DEEP, s.lines().getFirst().id());
		BuildSummary.Line trait = s.lines().stream().filter(l -> l.kind() == BuildSummary.Kind.TRAIT).findFirst().orElseThrow();
		assertEquals(d.traits().get(trait.id()), trait.value(), 1e-9);
		assertTrue(s.lines().size() <= BuildSummary.LINES);
		assertTrue(s.next().isPresent());
		assertEquals(TestCards.id("echo"), s.next().get().keyword(), "Echo is one card from Echo 2 (Deep is two from 4)");
		assertEquals(1, s.next().get().cards());
	}

	// ---------------------------------------------------------------- shards and the forge (§5)

	@Test
	void forgeLadderAndOneOfAKind() {
		assertEquals(30, ForgeRules.cost(0));
		assertEquals(45, ForgeRules.cost(1));
		assertEquals(60, ForgeRules.cost(2));
		assertEquals(60, ForgeRules.cost(7), "the last cost repeats");
		ForgeRules.Drawn stone = new ForgeRules.Drawn(TestCards.id("stonesong"), MINING, false);
		assertEquals(Optional.of(CardRules.Refusal.FORGE_SKILL), ForgeRules.canForge(Optional.of(stone), 99, 0, s -> 17));
		assertTrue(ForgeRules.canForge(Optional.of(stone), 30, 0, s -> 18).isEmpty());
		assertEquals(Optional.of(CardRules.Refusal.NO_SHARDS), ForgeRules.canForge(Optional.of(stone), 29, 0, s -> 18));
		assertEquals(Optional.of(CardRules.Refusal.NO_SHARDS), ForgeRules.canForge(Optional.of(stone), 44, 1, s -> 18));
		assertEquals(Optional.of(CardRules.Refusal.MYTHIC_HELD), ForgeRules.canForge(Optional.of(new ForgeRules.Drawn(stone.id(), MINING, true)), 99, 0, s -> 20));
		assertEquals(Optional.of(CardRules.Refusal.NOT_DRAWN), ForgeRules.canForge(Optional.empty(), 99, 0, s -> 20));
		// The legend title only when nothing is left anywhere; three stars at most.
		assertEquals(Optional.of(CardRules.Refusal.POOL_LEFT), ForgeRules.canLegend(List.of(stone), 99, 0, 0));
		List<ForgeRules.Drawn> empty = List.of(new ForgeRules.Drawn(stone.id(), MINING, true));
		assertTrue(ForgeRules.canLegend(empty, 30, 0, 0).isEmpty());
		assertEquals(Optional.of(CardRules.Refusal.LEGEND_FULL), ForgeRules.canLegend(empty, 99, 0, 3));
		// The drawn roll never offers a held Mythic, and with all three held offers none (shards instead).
		List<Identifier> three = List.of(TestCards.id("deep_lantern"), TestCards.id("heart_of_the_mountain"), TestCards.id("stonesong"));
		Set<Identifier> held = new HashSet<>(List.of(TestCards.id("heart_of_the_mountain")));
		for (long pick = 0; pick < 100; pick++) {
			Optional<Identifier> o = ForgeRules.offered(three, id -> !held.contains(id), pick);
			assertTrue(o.isPresent() && !held.contains(o.get()));
		}
		assertTrue(ForgeRules.offered(three, id -> false, 5).isEmpty());
	}

	@Test
	void twoPlayersForgingTheLastUnclaimedMythic() {
		// The server's order (BuildFeel.forge on one thread): check against the ledger, spend, claim. The second finds it held.
		CardLedger ledger = new CardLedger();
		Identifier last = TestCards.id("stonesong");
		int[] shards = {30, 30};
		List<Optional<CardRules.Refusal>> answers = new ArrayList<>();
		for (int p = 0; p < 2; p++) {
			boolean claimed = ledger.first(last.getPath()).isPresent();
			Optional<CardRules.Refusal> no = ForgeRules.canForge(Optional.of(new ForgeRules.Drawn(last, MINING, claimed)), shards[p], 0, s -> 20);
			if (no.isEmpty()) {
				shards[p] -= ForgeRules.cost(0);
				ledger.claim(last.getPath(), new CardData.Holder(new java.util.UUID(0, p), "P" + p, 1));
			}
			answers.add(no);
		}
		assertTrue(answers.get(0).isEmpty());
		assertEquals(Optional.of(CardRules.Refusal.MYTHIC_HELD), answers.get(1));
		assertEquals(0, shards[0]);
		assertEquals(30, shards[1], "the refused player keeps their shards");
		assertEquals("P0", ledger.first(last.getPath()).orElseThrow().name(), "one holder, never a copy");
	}

	@Test
	void onceOnlySourcesCountOnce() {
		BuildState b = BuildState.EMPTY.withOnce("shard/skill20/mettle:mining");
		assertTrue(b.done("shard/skill20/mettle:mining"));
		assertEquals(b, b.withOnce("shard/skill20/mettle:mining"));
	}

	// ---------------------------------------------------------------- the guide (§4)

	static GuideRules.Facts facts(boolean xp, boolean hand, int owned, int slotted, boolean set, int boons, boolean calm) {
		return new GuideRules.Facts(xp, hand, owned, slotted, false, false, set, false, false, boons, calm);
	}

	@Test
	void guideIsForNewCharactersOneHintAtATimeEachOnceAndSkippable() {
		assertFalse(GuideRules.decide(120, 4, 2).on(), "a migrated character never sees it");
		assertFalse(GuideRules.decide(0, 3, 3).on(), "three cards slotted: no guide");
		BuildState.Guide g = GuideRules.decide(0, 0, 0);
		assertTrue(g.on());
		g = GuideRules.step(g, facts(true, false, 0, 0, false, 0, false), 0);
		assertEquals(0, g.current(), "never in a fight");
		g = GuideRules.step(g, facts(true, false, 0, 0, false, 0, true), 10);
		assertEquals(1, g.current());
		// One at a time: a hand opens while hint 1 is up; hint 2 waits for it.
		g = GuideRules.step(g, facts(true, true, 0, 0, false, 0, true), 20);
		assertEquals(1, g.current());
		g = GuideRules.step(g, facts(true, true, 0, 0, false, 0, true), 10 + GuideRules.SHORT);
		assertEquals(2, g.current(), "hint 2 after hint 1's six seconds");
		g = GuideRules.step(g, facts(true, false, 1, 1, false, 0, true), 200);
		assertEquals(4, g.current(), "keeping a card puts hint 2 away (hint 3 goes with its hand); the slotted hint comes");
		assertFalse(g.seen(3));
		g = GuideRules.dismiss(g, 230);
		for (int t = 240; t < 2000; t += 20) {
			g = GuideRules.step(g, facts(true, false, 1, 1, false, 0, true), t);
			assertTrue(g.current() != 4 && g.current() != 1, "a hint came twice");
		}
		g = GuideRules.step(g, facts(true, false, 1, 1, true, 0, true), 3000);
		assertEquals(7, g.current(), "the Focus offer at the first set");
		BuildState.Guide skipped = GuideRules.skip(g);
		assertEquals(0, skipped.current());
		assertEquals(skipped, GuideRules.step(skipped, facts(true, true, 1, 1, true, 3, true), 4000), "skip turns every hint off");
	}

	// ---------------------------------------------------------------- the records never fail

	@Test
	void newRecordsLoadOldSavesAndClamp() {
		BuildState empty = BuildState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
		assertEquals(BuildState.EMPTY, empty);
		BuildState bad = BuildState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
			"{\"shards\": -5, \"legend\": 99, \"once\": [\"a\", 3, \"b\"], \"byname\": {\"tier\": 9, \"held\": [{\"tier\": 2}]}}")).getOrThrow();
		assertEquals(0, bad.shards());
		assertEquals(10, bad.legend());
		assertEquals(4, bad.byname().tier());
		assertTrue(bad.byname().held().isEmpty(), "a broken byname is skipped, not fatal");
		CardData old = TestCards.plain("mining/quarryman", Rarity.RARE, 0);
		var tag = CardData.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
		assertEquals(CardData.Marks.NONE, CardData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().marks());
		CardData marked = old.withMarks(old.marks().withTempered(new CardData.Tempered(TestCards.id("mining/deepborn"), 8, 10, "Ash", 9, 41))
			.withInscribed(new CardData.Inscribed(DEEP, Optional.of(EMBER), "Ash", 11, 50)).signedFrom("Ash", 41).signedUntil(90));
		assertEquals(CardData.CODEC.encodeStart(NbtOps.INSTANCE, marked).getOrThrow(), CardNbt.write(marked), "the ledger's fast writer and the codec agree");
		assertEquals(marked, CardData.CODEC.parse(NbtOps.INSTANCE, CardNbt.write(marked)).getOrThrow());
		assertNotEquals(old, marked);
	}
}
