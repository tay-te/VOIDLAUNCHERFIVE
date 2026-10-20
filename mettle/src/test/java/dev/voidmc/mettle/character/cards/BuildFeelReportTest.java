package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The build-feel simulator (docs/BUILD_FEEL.md §9): today's card rules beside the proposed ones (build momentum, Focus,
 * odd-level boons Temper and Inscribe, Mythic shards), prototyped here in the test so the proposal's numbers are
 * measured before any game code changes. Characters are played the way people play: three main skills, two side skills
 * at a third of the pace and three dabbled ones, plus the all-eight lockstep character of CardReportTest for comparison.
 * {@code ./gradlew test --tests '*BuildFeelReportTest' -Dmettle.balance=true} writes {@code build/reports/mettle-build-feel.md}.
 */
class BuildFeelReportTest {
	private static final int ATTRIBUTE = 10;
	/** Levers for sweeps: {@code build/bf.properties} if present (Gradle doesn't pass -D to the test JVM), else the defaults. */
	private static final java.util.Properties LEVERS = new java.util.Properties();

	static {
		Path file = Path.of("build", "bf.properties");
		if (Files.exists(file)) {
			try (var in = Files.newBufferedReader(file)) {
				LEVERS.load(in);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
	}

	static String lever(String key, String fallback) {
		return LEVERS.getProperty(key, fallback);
	}

	static int intLever(String key, int fallback) {
		return Integer.parseInt(LEVERS.getProperty(key, Integer.toString(fallback)));
	}
	private static final int[] RARITY_INDEX = new int[Rarity.values().length];
	private static final List<ResourceKey<Skill>> SKILLS = new ArrayList<>();
	private static final List<Identifier> KEYWORDS;

	static {
		RARITY_INDEX[Rarity.COMMON.ordinal()] = 0;
		RARITY_INDEX[Rarity.RARE.ordinal()] = 1;
		RARITY_INDEX[Rarity.EPIC.ordinal()] = 2;
		RARITY_INDEX[Rarity.CURSED.ordinal()] = 3;
		for (String s : TestCards.RELEASED) {
			SKILLS.add(TestCards.skill(s));
		}
		KEYWORDS = TestCards.KEYWORDS.keySet().stream().sorted(Comparator.comparing(Identifier::getPath)).toList();
	}

	// ---------------------------------------------------------------- the levers

	/**
	 * One rule set. {@code perkLean}: a perk whose native keyword you are committed to is dealt up to (1 + lean)× as often
	 * within its depth band (the band's total is kept, so depth and power odds don't move). {@code keywordLean}: a second
	 * keyword is weighted 1 + lean × commitment. {@code focus}: what the Focus keyword adds to commitment. {@code boons}:
	 * odd levels from 7 offer Temper / Inscribe / reroll instead of a reroll. {@code inscribeCap}: inscribed cards a deck
	 * may hold. {@code temperStep}: Temper raises a trait by this share of its range, to its rarity band's top.
	 */
	record Rules(String name, double perkLean, double keywordLean, int commitCap, int focus, boolean boons, int inscribeCap, double temperStep,
		double[] second) {
	}

	static final double[] SECOND_NOW = {0, 0.3, 0.7, 1.0};
	static final Rules NOW = new Rules("today", 0, 0, 4, 0, false, 0, 0, SECOND_NOW);
	static final Rules PROPOSED = new Rules("proposed", Double.parseDouble(lever("bf.perkLean", "1.0")),
		Double.parseDouble(lever("bf.keywordLean", "0.5")), 4, intLever("bf.focus", 2), true,
		intLever("bf.inscribeCap", 1), Double.parseDouble(lever("bf.temperStep", "0.25")),
		new double[] {0, Double.parseDouble(lever("bf.rareSecond", "0.3")), 0.7, 1.0});
	/** Momentum alone (no boons), to show what each part does. */
	static final Rules MOMENTUM_ONLY = new Rules("momentum only", PROPOSED.perkLean, PROPOSED.keywordLean, 4, PROPOSED.focus, false, 0, 0, PROPOSED.second);

	/** How a simulated player keeps cards and spends boons. */
	enum Policy {
		/** Keeps the card that adds most points; tempers; no Focus. Today's simulator player. */
		POINTS,
		/** As POINTS, but takes the game's suggestion to Focus its first set's keyword, and inscribes toward it. */
		GUIDED,
		/** Chases one keyword from the start: Focus on it, values its cards at +1 point, inscribes toward it. */
		COMMITTED
	}

	static final double CHASE_BONUS = 1.0;

	// ---------------------------------------------------------------- a card a character owns

	static final class Owned {
		Card card;
		boolean tempered;
		boolean inscribed;

		Owned(Card card) {
			this.card = card;
		}

		ResourceKey<Skill> skill() {
			return TestCards.def(this.card.perk()).skill();
		}
	}

	// ---------------------------------------------------------------- a character

	/** Skill XP rates: three mains at 1, two sides at 0.3, three dabbled at 0.08 (of the mains' XP). */
	static final double[] FOCUSED = {1, 1, 1, 0.3, 0.3, 0.08, 0.08, 0.08};
	static final double[] ALL_EIGHT = {1, 1, 1, 1, 1, 1, 1, 1};
	static final double[] SINGLE = {1, 0, 0, 0, 0, 0, 0, 0};

	static final class Char {
		final Rules rules;
		final Policy policy;
		Identifier focus;
		final List<ResourceKey<Skill>> order;
		final double[] rate;
		final Map<ResourceKey<Skill>, List<Owned>> owned = new HashMap<>();
		final Map<ResourceKey<Skill>, List<Owned>> decks = new HashMap<>();
		final Map<ResourceKey<Skill>, Integer> queued = new HashMap<>();
		final TestCards.Sheet sheet = new TestCards.Sheet();
		final long seed;
		final int wits;
		int dealt;
		double rerolls = DraftRules.START_REROLLS;
		int characterLevel = 1;
		// metrics
		final int[] topTier = new int[21];
		final int[] focusTier = new int[21];
		final int[] charLevelAt = new int[21];
		int firstTwo;
		int firstFour;
		int firstSix;
		final Set<Identifier> sixesReached = new HashSet<>();
		int boonsTemper;
		int boonsInscribe;
		int boonsReroll;
		double shards;
		int shardForgeLevel;
		int synergies;

		Char(Rules rules, Policy policy, Identifier focus, List<ResourceKey<Skill>> order, double[] rate, long seed, int wits) {
			this.rules = rules;
			this.policy = policy;
			this.focus = focus;
			this.order = order;
			this.rate = rate;
			this.seed = seed;
			this.wits = wits;
			this.sheet.attribute = 20;
			for (ResourceKey<Skill> s : SKILLS) {
				this.owned.put(s, new ArrayList<>());
				this.decks.put(s, List.of());
			}
		}

		int level(ResourceKey<Skill> s) {
			return this.sheet.level(s);
		}

		/** Commitment per keyword: slotted cards carrying it, plus the Focus. */
		Map<Identifier, Integer> commitment() {
			Map<Identifier, Integer> c = new HashMap<>();
			for (List<Owned> d : this.decks.values()) {
				for (Owned o : d) {
					for (Identifier k : o.card.keywords()) {
						c.merge(k, 1, Integer::sum);
					}
				}
			}
			if (this.focus != null && this.rules.focus > 0) {
				c.merge(this.focus, this.rules.focus, Integer::sum);
			}
			return c;
		}

		double score(Owned o) {
			double s = CardReportTest.points(o.card);
			if (this.policy == Policy.COMMITTED && this.focus != null && o.card.keywords().contains(this.focus)) {
				s += CHASE_BONUS;
			}
			return s;
		}

		List<Owned> bestDeck(ResourceKey<Skill> skill, int level, List<Owned> cards) {
			int slots = CardRules.slots(level, this.wits);
			if (slots == 0) {
				return List.of();
			}
			List<Owned> usable = cards.stream().filter(o -> o.card.needs() <= level).sorted(Comparator.comparingDouble(this::score).reversed()).toList();
			List<Owned> best = greedy(usable, slots, null);
			double bestScore = total(best);
			Set<ResourceKey<Perk>> masteries = new HashSet<>();
			usable.stream().filter(o -> TestCards.def(o.card.perk()).kind() == Perk.Kind.MASTERY).forEach(o -> masteries.add(o.card.perk()));
			for (ResourceKey<Perk> m : masteries) {
				List<Owned> d = greedy(usable, slots, m);
				if (total(d) > bestScore) {
					best = d;
					bestScore = total(d);
				}
			}
			return best;
		}

		double total(List<Owned> deck) {
			return deck.stream().mapToDouble(this::score).sum();
		}

		private List<Owned> greedy(List<Owned> usable, int slots, ResourceKey<Perk> mastery) {
			List<Owned> out = new ArrayList<>();
			Set<ResourceKey<Perk>> perks = new HashSet<>();
			int inscribed = 0;
			if (mastery != null) {
				Perk m = TestCards.def(mastery);
				Owned mc = usable.stream().filter(o -> o.card.perk().equals(mastery)).findFirst().orElseThrow();
				List<Owned> side = new ArrayList<>();
				Set<ResourceKey<Perk>> sidePerks = new HashSet<>();
				for (Owned o : usable) {
					Perk p = TestCards.def(o.card.perk());
					if (p.kind() == Perk.Kind.PATH && ("any".equals(m.side()) || p.path().equals(m.side())) && side.size() < 2
						&& (!o.inscribed || inscribed < this.rules.inscribeCap) && sidePerks.add(o.card.perk())) {
						side.add(o);
						inscribed += o.inscribed ? 1 : 0;
					}
				}
				if (side.size() < 2 || slots < 3) {
					return List.of();
				}
				out.add(mc);
				out.addAll(side);
				perks.add(mastery);
				side.forEach(o -> perks.add(o.card.perk()));
			}
			for (Owned o : usable) {
				if (out.size() >= slots) {
					break;
				}
				if (TestCards.def(o.card.perk()).kind() == Perk.Kind.MASTERY || perks.contains(o.card.perk())) {
					continue;
				}
				if (o.inscribed && inscribed >= this.rules.inscribeCap) {
					continue;
				}
				perks.add(o.card.perk());
				inscribed += o.inscribed ? 1 : 0;
				out.add(o);
			}
			return out;
		}

		void redeck(ResourceKey<Skill> s) {
			this.decks.put(s, bestDeck(s, level(s), this.owned.get(s)));
		}

		// ------------------------------------------------------------ dealing with momentum

		List<Card> deal(ResourceKey<Skill> skill, Set<ResourceKey<Perk>> avoid) {
			Random random = DraftRules.random(this.seed, this.dealt++);
			Map<Identifier, Integer> commit = commitment();
			List<ResourceKey<Perk>> own = CardRules.candidates(skill, this.sheet, TestCards.DECK);
			boolean wild = random.nextDouble() < DraftRules.WILDCARD_CHANCE;
			long wildPick = random.nextLong();
			long[] rollSeeds = {random.nextLong(), random.nextLong(), random.nextLong()};
			if (own.isEmpty()) {
				return List.of();
			}
			List<Card> hand = new ArrayList<>();
			Set<ResourceKey<Perk>> taken = new HashSet<>();
			Card wildcard = null;
			if (wild) {
				List<ResourceKey<Skill>> others = new ArrayList<>();
				for (int i = 0; i < this.order.size() && others.size() < 4; i++) {
					ResourceKey<Skill> s = this.order.get(i);
					if (!s.equals(skill) && this.rate[i] >= 0.3 && !CardRules.candidates(s, this.sheet, TestCards.DECK).isEmpty()) {
						others.add(s);
					}
				}
				if (!others.isEmpty()) {
					Random r = new Random(wildPick);
					ResourceKey<Skill> from = others.get(r.nextInt(others.size()));
					ResourceKey<Perk> key = draw(CardRules.candidates(from, this.sheet, TestCards.DECK), from, r, taken, avoid, commit);
					if (key != null) {
						wildcard = roll(key, level(from), new Random(rollSeeds[2]), true, commit);
						taken.add(key);
					}
				}
			}
			int slots = wildcard == null ? DraftRules.HAND : DraftRules.HAND - 1;
			for (int i = 0; i < slots; i++) {
				ResourceKey<Perk> key = draw(own, skill, random, taken, avoid, commit);
				if (key == null) {
					break;
				}
				hand.add(roll(key, level(skill), new Random(rollSeeds[i]), false, commit));
				taken.add(key);
			}
			if (wildcard != null) {
				hand.add(wildcard);
			}
			return hand;
		}

		private ResourceKey<Perk> draw(List<ResourceKey<Perk>> cards, ResourceKey<Skill> skill, Random random, Set<ResourceKey<Perk>> taken,
			Set<ResourceKey<Perk>> avoid, Map<Identifier, Integer> commit) {
			List<ResourceKey<Perk>> pool = cards.stream().filter(k -> !taken.contains(k) && !avoid.contains(k)).toList();
			if (pool.isEmpty()) {
				pool = cards.stream().filter(k -> !taken.contains(k)).toList();
			}
			if (pool.isEmpty()) {
				return null;
			}
			int level = level(skill);
			Map<Rarity, Double> odds = DraftRules.odds(level);
			Map<Rarity, Integer> count = new EnumMap<>(Rarity.class);
			for (ResourceKey<Perk> k : pool) {
				count.merge(DraftRules.rarity(TestCards.def(k)), 1, Integer::sum);
			}
			double[] base = new double[pool.size()];
			double[] lean = new double[pool.size()];
			Map<Rarity, double[]> band = new EnumMap<>(Rarity.class);
			for (int i = 0; i < pool.size(); i++) {
				Perk p = TestCards.def(pool.get(i));
				Rarity depth = DraftRules.rarity(p);
				base[i] = odds.get(depth) / count.get(depth) * DraftRules.attributeWeight(p, 20) * CardRules.duplicateWeight(this.sheet.owned(pool.get(i)));
				Identifier nat = p.kind() == Perk.Kind.CURSED ? CardRules.HEX : TestCards.DATA_SET.nativeKeyword(pool.get(i)).orElse(CardRules.HEX);
				lean[i] = 1 + this.rules.perkLean * Math.min(this.rules.commitCap, commit.getOrDefault(nat, 0)) / (double) this.rules.commitCap;
				double[] b = band.computeIfAbsent(depth, d -> new double[2]);
				b[0] += base[i];
				b[1] += base[i] * lean[i];
			}
			double[] weights = new double[pool.size()];
			double total = 0;
			for (int i = 0; i < pool.size(); i++) {
				double[] b = band.get(DraftRules.rarity(TestCards.def(pool.get(i))));
				weights[i] = base[i] * lean[i] * (b[1] <= 0 ? 1 : b[0] / b[1]);
				total += weights[i];
			}
			double roll = random.nextDouble() * total;
			for (int i = 0; i < pool.size(); i++) {
				roll -= weights[i];
				if (roll < 0) {
					return pool.get(i);
				}
			}
			return pool.getLast();
		}

		private Card roll(ResourceKey<Perk> key, int level, Random random, boolean wildcard, Map<Identifier, Integer> commit) {
			Perk perk = TestCards.def(key);
			Rarity rarity = CardRules.rollRarity(perk, level, random);
			int count = CardRules.traitCount(rarity, random);
			boolean second = random.nextDouble() < this.rules.second[RARITY_INDEX[rarity.ordinal()]];
			long traitSeed = random.nextLong();
			long keywordSeed = random.nextLong();
			List<TraitRoll> traits = CardRules.rollTraits(TestCards.DATA_SET.traits(perk.skill()), rarity, count, new Random(traitSeed));
			Identifier first = perk.kind() == Perk.Kind.CURSED ? CardRules.HEX : TestCards.DATA_SET.nativeKeyword(key).orElse(CardRules.HEX);
			List<Identifier> keywords = new ArrayList<>(List.of(first));
			if (second) {
				List<Identifier> pool = TestCards.DATA_SET.pool(perk.skill()).stream().filter(k -> !k.equals(first)).sorted(Comparator.comparing(Identifier::toString)).toList();
				if (!pool.isEmpty()) {
					double[] w = new double[pool.size()];
					double sum = 0;
					for (int i = 0; i < pool.size(); i++) {
						w[i] = 1 + this.rules.keywordLean * Math.min(this.rules.commitCap, commit.getOrDefault(pool.get(i), 0));
						sum += w[i];
					}
					double r = new Random(keywordSeed).nextDouble() * sum;
					Identifier pick = pool.getLast();
					for (int i = 0; i < pool.size(); i++) {
						r -= w[i];
						if (r < 0) {
							pick = pool.get(i);
							break;
						}
					}
					keywords.add(pick);
				}
			}
			return new Card(key, CardRules.rank(perk, rarity), rarity, wildcard, keywords, traits, CardRules.needs(perk, rarity));
		}

		// ------------------------------------------------------------ keeping

		double gain(Card h) {
			ResourceKey<Skill> s = TestCards.def(h.perk()).skill();
			int at = Math.max(level(s), h.needs());
			List<Owned> with = new ArrayList<>(this.owned.get(s));
			with.add(new Owned(h));
			return total(bestDeck(s, at, with)) - total(bestDeck(s, at, this.owned.get(s)));
		}

		void draft(ResourceKey<Skill> skill) {
			List<Card> hand = deal(skill, Set.of());
			if (hand.isEmpty()) {
				this.queued.merge(skill, 1, Integer::sum);
				return;
			}
			double best = hand.stream().mapToDouble(this::gain).max().orElse(0);
			if (this.rerolls >= 1 && best < 0.5) {
				hand = deal(skill, new HashSet<>(hand.stream().map(Card::perk).toList()));
				this.rerolls--;
			}
			Card keep = hand.getFirst();
			double bestGain = Double.NEGATIVE_INFINITY;
			for (Card h : hand) {
				double g = gain(h) + CardReportTest.points(h) * 1e-3;
				if (g > bestGain) {
					bestGain = g;
					keep = h;
				}
			}
			ResourceKey<Skill> to = TestCards.def(keep.perk()).skill();
			this.owned.get(to).add(new Owned(keep));
			this.sheet.owned.merge(keep.perk(), 1, Integer::sum);
			redeck(to);
			if (this.level(skill) >= 15) {
				this.shards += 1; // a draft from skill 15
			}
		}

		// ------------------------------------------------------------ boons (odd levels from 7)

		void boon(ResourceKey<Skill> skill) {
			if (!this.rules.boons) {
				this.rerolls = Math.min(DraftRules.MAX_REROLLS, this.rerolls + 1);
				this.boonsReroll++;
				return;
			}
			if (this.policy != Policy.POINTS && this.focus != null && inscribe(skill)) {
				this.boonsInscribe++;
				return;
			}
			if (temper(skill)) {
				this.boonsTemper++;
				return;
			}
			this.rerolls = Math.min(DraftRules.MAX_REROLLS, this.rerolls + 1);
			this.boonsReroll++;
		}

		boolean inscribe(ResourceKey<Skill> skill) {
			Identifier k = this.focus;
			if (!TestCards.DATA_SET.pool(skill).contains(k)) {
				return false;
			}
			List<Owned> deck = this.decks.get(skill);
			long inscribed = deck.stream().filter(o -> o.inscribed).count();
			if (inscribed >= this.rules.inscribeCap) {
				return false;
			}
			Owned pick = null;
			for (Owned o : deck) {
				if (!o.inscribed && !o.card.keywords().contains(k) && TestCards.def(o.card.perk()).kind() != Perk.Kind.CURSED
					&& (pick == null || CardReportTest.points(o.card) > CardReportTest.points(pick.card))) {
					pick = o;
				}
			}
			if (pick == null) {
				return false;
			}
			if (INSCRIBE_NEEDS_SEED) {
				// The keyword must already be on another card in this deck: Inscribe strengthens, it doesn't start a set.
				Owned chosen = pick;
				if (deck.stream().noneMatch(o -> o != chosen && o.card.keywords().contains(k))) {
					return false;
				}
			}
			Card c = pick.card;
			pick.card = new Card(c.perk(), c.rank(), c.rarity(), c.wildcard(), List.of(c.keywords().getFirst(), k), c.traits(), c.needs());
			pick.inscribed = true;
			redeck(skill);
			return true;
		}

		boolean temper(ResourceKey<Skill> skill) {
			Owned pick = null;
			int pickTrait = -1;
			double pickValue = 0;
			double bestGain = 1e-9;
			if (this.decks.get(skill).stream().filter(o -> o.tempered).count() >= TEMPER_DECK_CAP) {
				return false;
			}
			for (Owned o : this.decks.get(skill)) {
				if (o.tempered) {
					continue;
				}
				List<TraitRoll> ts = o.card.traits();
				for (int i = 0; i < ts.size(); i++) {
					Trait def = TestCards.TRAITS.get(ts.get(i).trait());
					double to = tempered(def, o.card.rarity(), ts.get(i).value(), this.rules.temperStep);
					double g = (CardRules.effective(def, to, a -> ATTRIBUTE) - CardRules.effective(def, ts.get(i).value(), a -> ATTRIBUTE)) / CardRules.cardCap(def);
					if (g > bestGain) {
						bestGain = g;
						pick = o;
						pickTrait = i;
						pickValue = to;
					}
				}
			}
			if (pick == null) {
				return false;
			}
			List<TraitRoll> ts = new ArrayList<>(pick.card.traits());
			ts.set(pickTrait, new TraitRoll(ts.get(pickTrait).trait(), pickValue));
			Card c = pick.card;
			pick.card = new Card(c.perk(), c.rank(), c.rarity(), c.wildcard(), c.keywords(), ts, c.needs());
			pick.tempered = true;
			redeck(skill);
			return true;
		}

		// ------------------------------------------------------------ playing a career

		void play() {
			for (int main = 1; main <= Curves.SKILL_CAP; main++) {
				double mainXp = Curves.skillXpFor(main);
				double totalXp = 0;
				for (int i = 0; i < this.order.size(); i++) {
					ResourceKey<Skill> s = this.order.get(i);
					double xp = this.rate[i] * mainXp;
					totalXp += xp;
					int target = Curves.skillLevel(xp);
					for (int l = level(s) + 1; l <= target; l++) {
						this.sheet.levels.put(s, l);
						int drafts = DraftRules.draftsFor(l - 1, l);
						int waiting = this.queued.getOrDefault(s, 0) + drafts;
						this.queued.put(s, 0);
						for (int d = 0; d < waiting; d++) {
							draft(s);
						}
						if (drafts == 0) {
							boon(s);
						}
						if (l == 20) {
							this.shards += 2;
						}
						if (l == 12 || l == 18) {
							this.shards += 1; // a feat (assumed: one at 12 and one at 18 per skill)
						}
						redeck(s);
					}
				}
				int cl = Curves.characterLevel(totalXp);
				if (cl > this.characterLevel) {
					this.rerolls = Math.min(DraftRules.MAX_REROLLS, this.rerolls + (cl - this.characterLevel));
					this.characterLevel = cl;
				}
				this.charLevelAt[main] = cl;
				measure(main, cl);
			}
		}

		void measure(int main, int cl) {
			Map<Identifier, Integer> counts = new HashMap<>();
			Map<Identifier, Set<ResourceKey<Skill>>> from = new HashMap<>();
			for (Map.Entry<ResourceKey<Skill>, List<Owned>> e : this.decks.entrySet()) {
				for (Owned o : e.getValue()) {
					for (Identifier k : o.card.keywords()) {
						counts.merge(k, 1, Integer::sum);
						from.computeIfAbsent(k, x -> new HashSet<>()).add(e.getKey());
					}
				}
			}
			int top = 0;
			for (Identifier k : counts.keySet()) {
				int t = CardRules.tier(counts.get(k), from.get(k).size());
				top = Math.max(top, t);
				if (t >= 6 && this.sixesReached.add(k)) {
					this.shards += 2; // a keyword's first 6-set
				}
			}
			this.topTier[main] = top;
			if (top >= 2 && this.firstTwo == 0) {
				this.firstTwo = cl;
				if (this.policy == Policy.GUIDED && this.focus == null) {
					// The game suggests following the first set's keyword; the guided player accepts.
					this.focus = counts.entrySet().stream().filter(e -> e.getValue() >= 2 && !e.getKey().equals(CardRules.HEX))
						.max(Comparator.comparingInt((Map.Entry<Identifier, Integer> e) -> e.getValue()).thenComparing(e -> e.getKey().getPath()))
						.map(Map.Entry::getKey).orElse(null);
				}
			}
			if (top >= 4 && this.firstFour == 0) {
				this.firstFour = cl;
			}
			if (top >= 6 && this.firstSix == 0) {
				this.firstSix = cl;
			}
			if (this.focus != null) {
				this.focusTier[main] = CardRules.tier(counts.getOrDefault(this.focus, 0), from.getOrDefault(this.focus, Set.of()).size());
			}
			// Synergies: the nine wave-1 pairs, once both reach 10 (one shard each).
			int syn = 0;
			for (String[] pair : SYNERGIES) {
				if (level(TestCards.skill(pair[0])) >= 10 && level(TestCards.skill(pair[1])) >= 10) {
					syn++;
				}
			}
			if (syn > this.synergies) {
				this.shards += syn - this.synergies;
				this.synergies = syn;
			}
			if (this.shardForgeLevel == 0 && this.shards >= SHARD_COST) {
				this.shardForgeLevel = cl;
			}
		}
	}

	static final boolean INSCRIBE_NEEDS_SEED = Boolean.parseBoolean(lever("bf.inscribeSeed", "true"));
	/** Temper never lifts a trait above this share of its range (1 = the rarity band's top only). */
	static final double TEMPER_CAP = Double.parseDouble(lever("bf.temperCap", "1.0"));
	/** Tempered cards a deck may hold (the boon is refused past it). */
	static final int TEMPER_DECK_CAP = intLever("bf.temperDeckCap", 3);
	static final boolean TEMPER_NEAREST = !"floor".equals(lever("bf.temperRound", "nearest"));
	static final String[][] SYNERGIES = {{"mining", "smithing"}, {"mining", "heavy_weapons"}, {"blades", "defense"}, {"blades", "heavy_weapons"},
		{"marksmanship", "survival"}, {"marksmanship", "athletics"}, {"defense", "athletics"}, {"survival", "athletics"}, {"smithing", "defense"}};
	static final int SHARD_COST = intLever("bf.shardCost", 30);

	// ---------------------------------------------------------------- profiles

	/** A focused character with random mains (POINTS / GUIDED). */
	static Char focused(Rules rules, Policy policy, int run) {
		List<ResourceKey<Skill>> order = new ArrayList<>(SKILLS);
		Collections.shuffle(order, new Random(run * 7919L + 3));
		return new Char(rules, policy, null, order, FOCUSED, new Random(run * 1_000_003L + 29).nextLong(), 3);
	}

	/** How well a skill carries a keyword: natives count 1, being in the second-keyword pool counts 4. */
	static double capacity(ResourceKey<Skill> skill, Identifier k) {
		double c = 0;
		for (ResourceKey<Perk> p : TestCards.DECK.of(skill)) {
			Perk def = TestCards.def(p);
			Identifier nat = def.kind() == Perk.Kind.CURSED ? CardRules.HEX : TestCards.DATA_SET.nativeKeyword(p).orElse(CardRules.HEX);
			if (nat.equals(k)) {
				c += def.tier() >= 4 ? 0.3 : 1;
			}
		}
		return c + (TestCards.DATA_SET.pool(skill).contains(k) ? 4 : 0);
	}

	/** A committed character: its three mains are the skills that carry the keyword best. */
	static Char committed(Rules rules, Identifier k, int run) {
		List<ResourceKey<Skill>> order = new ArrayList<>(SKILLS);
		Collections.shuffle(order, new Random(run * 104_729L + 1));
		order.sort(Comparator.comparingDouble((ResourceKey<Skill> s) -> -capacity(s, k)));
		return new Char(rules, Policy.COMMITTED, k, order, FOCUSED, new Random(run * 1_000_003L + 31).nextLong(), 3);
	}

	// ---------------------------------------------------------------- the report

	@Test
	void report() throws IOException {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "build-feel report: run with -Dmettle.balance=true");
		int runs = intLever("bf.runs", 800);
		StringBuilder out = new StringBuilder("# Build feel simulation (docs/BUILD_FEEL.md §9)\n\n");
		out.append(String.format(Locale.ROOT, "Levers: perk lean %.2f, keyword lean %.2f, Focus +%d, boons %s, inscribed cards a deck %d, Temper step %.2f of a range, "
			+ "Rare second keyword %.0f %%, shard cost %d. %d careers a row.%n%n", PROPOSED.perkLean, PROPOSED.keywordLean, PROPOSED.focus, PROPOSED.boons,
			PROPOSED.inscribeCap, PROPOSED.temperStep, 100 * PROPOSED.second[1], SHARD_COST, runs));
		out.append("A **focused** character plays three main skills, two side skills at 30 % of their XP and three at 8 %: when the mains reach 20 the sides "
			+ "are at about 12 and the dabbled ones at about 7, character level about 39. **Mid-game** is character level 20 (mains near 11).\n\n");

		String sections = lever("bf.sections", "12345");
		// 1. Sets for focused and all-eight characters.
		out.append("## 1. Sets: when they come (character level; share of careers that get there by the mains' skill 20)\n\n");
		out.append("| Character · rules · player | first 2-set | first 4-set | first 6-set | a 4-set by mid-game (char 20) | a 6-set by 20 | own Focus: 4-set by mid-game · 6-set by 20 | boons: temper / inscribe / reroll |\n"
			+ "|---|---|---|---|---|---|---|---|\n");
		for (Object[] cfg : !sections.contains("1") ? new Object[0][] : new Object[][] {{"focused", NOW, Policy.POINTS}, {"focused", MOMENTUM_ONLY, Policy.POINTS}, {"focused", PROPOSED, Policy.POINTS},
			{"focused", NOW, Policy.GUIDED}, {"focused", PROPOSED, Policy.GUIDED}, {"all-eight", NOW, Policy.POINTS}, {"all-eight", PROPOSED, Policy.GUIDED}}) {
			Rules rules = (Rules) cfg[1];
			Policy policy = (Policy) cfg[2];
			int[] two = new int[runs];
			int[] four = new int[runs];
			int[] six = new int[runs];
			int mid = 0;
			int focusFour = 0;
			int focusSix = 0;
			double t = 0;
			double ins = 0;
			double rr = 0;
			for (int run = 0; run < runs; run++) {
				Char c = "focused".equals(cfg[0]) ? focused(rules, policy, run)
					: new Char(rules, policy, null, new ArrayList<>(SKILLS), ALL_EIGHT, new Random(run * 1_000_003L + 5).nextLong(), 3);
				c.play();
				two[run] = c.firstTwo;
				four[run] = c.firstFour;
				six[run] = c.firstSix;
				if (c.firstFour > 0 && c.firstFour <= 20) {
					mid++;
				}
				for (int m = 1; m <= 20; m++) {
					if (c.charLevelAt[m] <= 20 && c.focusTier[m] >= 4) {
						focusFour++;
						break;
					}
				}
				if (c.focusTier[20] >= 6) {
					focusSix++;
				}
				t += c.boonsTemper;
				ins += c.boonsInscribe;
				rr += c.boonsReroll;
			}
			out.append(String.format(Locale.ROOT, "| %s · %s · %s | %s | %s | %s | %d %% | %d %% | %s | %.1f / %.1f / %.1f |%n", cfg[0], rules.name, policy.name().toLowerCase(Locale.ROOT),
				when(two), when(four), when(six), 100 * mid / runs, 100 * nonZero(six) / runs,
				policy == Policy.POINTS ? "—" : String.format(Locale.ROOT, "%d %% · %d %%", 100 * focusFour / runs, 100 * focusSix / runs), t / runs, ins / runs, rr / runs));
		}

		// 2. Every keyword, chased by a committed player.
		out.append("\n## 2. Every keyword, chased: a committed player (Focus from the start, its three mains the skills that carry it best)\n\n");
		out.append("| Keyword | mains | today: 6-set by skill 16 / 18 / 20 | proposed: by 16 / 18 / 20 | mean skill at first 6-set (today → proposed) | 4-set by skill 12 (today → proposed) |\n"
			+ "|---|---|---|---|---|---|\n");
		int perKeyword = Math.max(100, runs / 3);
		double worstAt20 = 1;
		Map<Identifier, List<Set<String>>> chaseDecks = new LinkedHashMap<>();
		for (Identifier k : !sections.contains("2") ? List.<Identifier>of() : KEYWORDS) {
			double[][] by = new double[2][3];
			double[] four12 = new double[2];
			double firstSum = 0;
			int firstN = 0;
			double nowSum = 0;
			int nowN = 0;
			String mains = "";
			for (int r = 0; r < 2; r++) {
				Rules rules = r == 0 ? NOW : PROPOSED;
				for (int run = 0; run < perKeyword; run++) {
					Char c = committed(rules, k, run);
					mains = c.order.subList(0, 3).stream().map(s -> s.identifier().getPath()).reduce((a, b) -> a + ", " + b).orElse("");
					c.play();
					for (int i = 0; i < 3; i++) {
						if (c.focusTier[new int[] {16, 18, 20}[i]] >= 6) {
							by[r][i]++;
						}
					}
					if (c.focusTier[12] >= 4) {
						four12[r]++;
					}
					if (r == 0) {
						for (int m = 1; m <= 20; m++) {
							if (c.focusTier[m] >= 6) {
								nowSum += m;
								nowN++;
								break;
							}
						}
					}
					if (r == 1) {
						for (int m = 1; m <= 20; m++) {
							if (c.focusTier[m] >= 6) {
								firstSum += m;
								firstN++;
								break;
							}
						}
						Set<String> sig = new HashSet<>();
						for (Owned o : c.decks.get(c.order.getFirst())) {
							String p = o.card.perk().identifier().getPath();
							sig.add(p);
							o.card.traits().forEach(tr -> sig.add(p + "+" + tr.trait().getPath()));
						}
						sig.add("@" + c.order.getFirst().identifier().getPath());
						chaseDecks.computeIfAbsent(k, x -> new ArrayList<>()).add(sig);
					}
				}
			}
			worstAt20 = Math.min(worstAt20, by[1][2] / perKeyword);
			out.append(String.format(Locale.ROOT, "| %s | %s | %.0f / %.0f / %.0f %% | %.0f / %.0f / %.0f %% | %s | %.0f → %.0f %% |%n", k.getPath(), mains,
				100 * by[0][0] / perKeyword, 100 * by[0][1] / perKeyword, 100 * by[0][2] / perKeyword, 100 * by[1][0] / perKeyword, 100 * by[1][1] / perKeyword,
				100 * by[1][2] / perKeyword, (nowN == 0 ? "never" : String.format(Locale.ROOT, "%.1f", nowSum / nowN)) + " → " + (firstN == 0 ? "never" : String.format(Locale.ROOT, "%.1f", firstSum / firstN)), 100 * four12[0] / perKeyword,
				100 * four12[1] / perKeyword));
		}

		// 3. Power and variety per skill (single-skill careers, as CardReportTest), with Temper on every boon.
		out.append("\n## 3. Power and variety at skill 20 (one skill, as CardReportTest; Temper taken on every odd level)\n\n");
		out.append("| Skill | today: deck · ×v1 · Wits 14 ×v1 | proposed: deck · ×v1 · Wits 14 ×v1 | Jaccard today → proposed | identical decks | tempered traits |\n"
			+ "|---|---|---|---|---|---|\n");
		int single = intLever("bf.single", 1500);
		double v1 = 27.9; // CardReportTest's v1 draft at skill 20, the same in all eight skills
		double ratioSum = 0;
		double ratioWitsSum = 0;
		double worstRatio = 0;
		double worstWits = 0;
		double worstJac = 0;
		int identicalAll = 0;
		for (ResourceKey<Skill> skill : !sections.contains("3") ? List.<ResourceKey<Skill>>of() : SKILLS) {
			double[] deck = new double[2];
			double[] wits = new double[2];
			double[] jac = new double[2];
			int same = 0;
			double tempered = 0;
			for (int r = 0; r < 2; r++) {
				Rules rules = r == 0 ? NOW : PROPOSED;
				List<Set<String>> sigs = new ArrayList<>();
				List<String> exact = new ArrayList<>();
				for (int run = 0; run < single; run++) {
					List<ResourceKey<Skill>> order = new ArrayList<>(List.of(skill));
					Char c = new Char(rules, Policy.POINTS, null, order, SINGLE, new Random(run * 1_000_003L + 11).nextLong(), 3);
					c.play();
					List<Owned> d = c.decks.get(skill);
					deck[r] += d.stream().mapToDouble(o -> CardReportTest.points(o.card)).sum();
					wits[r] += witsDeck(c, skill); // two more slots (Wits 14) from the same binder
					Set<String> sig = new HashSet<>();
					StringBuilder ex = new StringBuilder();
					for (Owned o : d) {
						String p = o.card.perk().identifier().getPath();
						sig.add(p);
						o.card.traits().forEach(tr -> sig.add(p + "+" + tr.trait().getPath()));
						ex.append(p).append(o.card.rarity()).append(o.card.keywords()).append(o.card.traits()).append(';');
						if (o.tempered && r == 1) {
							tempered++;
						}
					}
					sigs.add(sig);
					exact.add(ex.toString());
				}
				double j = 0;
				for (int i = 0; i + 1 < sigs.size(); i++) {
					int o = (i * 7 + 13) % sigs.size();
					j += jaccard(sigs.get(i), sigs.get(o));
					if (r == 1 && o != i && exact.get(i).equals(exact.get(o))) {
						same++;
					}
				}
				jac[r] = j / (sigs.size() - 1);
			}
			double ratio = deck[1] / single / v1;
			double ratioW = wits[1] / single / v1;
			ratioSum += ratio;
			ratioWitsSum += ratioW;
			worstRatio = Math.max(worstRatio, ratio);
			worstWits = Math.max(worstWits, ratioW);
			worstJac = Math.max(worstJac, jac[1]);
			identicalAll += same;
			out.append(String.format(Locale.ROOT, "| %s | %.1f · %.2f× · %.2f× | %.1f · %.2f× · %.2f× | %.3f → %.3f | %d | %.1f |%n", skill.identifier().getPath(),
				deck[0] / single, deck[0] / single / v1, wits[0] / single / v1, deck[1] / single, ratio, ratioW, jac[0], jac[1], same, tempered / single));
		}
		out.append(String.format(Locale.ROOT, "%nMean: **%.2f×** v1 with base slots (limit 1.15×), **%.2f×** with Wits 14 (limit 1.3×); worst skill %.2f× / %.2f×.%n",
			ratioSum / 8, ratioWitsSum / 8, worstRatio, worstWits));

		// 4. Overlap between committed players chasing the same keyword (the worst case for variety).
		out.append("\n## 4. Variety among players chasing the same keyword (first main's deck at 20, perk + trait pairs)\n\n| Keyword | mean Jaccard |\n|---|---|\n");
		double worstChase = 0;
		for (Map.Entry<Identifier, List<Set<String>>> e : chaseDecks.entrySet()) {
			List<Set<String>> s = e.getValue();
			double j = 0;
			int pairs = 0;
			for (int i = 0; i + 1 < s.size(); i++) {
				Set<String> a = s.get(i);
				Set<String> b = s.get((i * 7 + 13) % s.size());
				if (a == b || a.stream().filter(x -> x.startsWith("@")).noneMatch(b::contains)) {
					continue; // compare decks of the same skill only
				}
				j += jaccard(a, b);
				pairs++;
			}
			j /= Math.max(1, pairs);
			worstChase = Math.max(worstChase, j);
			out.append(String.format(Locale.ROOT, "| %s | %.3f |%n", e.getKey().getPath(), j));
		}

		// 5. Mythic shards for a focused (guided) player.
		out.append(String.format(Locale.ROOT, "%n## 5. Mythic shards (cost %d): a focused player, guided%n%n", SHARD_COST));
		out.append("Sources counted: 1 a draft from skill 15, 2 a skill reaching 20, 1 a feat (assumed one at skill 12 and one at 18 in each skill), 1 a synergy, "
			+ "2 a keyword's first 6-set. Not counted (bonus): boss kills after the world first, a drawn-Mythic roll on a claimed Mythic.\n\n");
		out.append("| Character | shards when the mains reach 12 / 14 / 16 / 18 / 20 | one forge by 18 · by 20 | two forges by 20 | character level at the first forge |\n|---|---|---|---|---|\n");
		int shardRuns = !sections.contains("5") ? 1 : Math.min(runs, 300);
		for (String kind : !sections.contains("5") ? List.<String>of() : List.of("focused", "all-eight")) {
			double[] shardAt = new double[21];
			int by18 = 0;
			int by20 = 0;
			int two = 0;
			double forgeLevel = 0;
			for (int run = 0; run < shardRuns; run++) {
				Char c = "focused".equals(kind) ? focused(PROPOSED, Policy.GUIDED, run)
					: new Char(PROPOSED, Policy.GUIDED, null, new ArrayList<>(SKILLS), ALL_EIGHT, new Random(run * 1_000_003L + 5).nextLong(), 3);
				for (int m : new int[] {12, 14, 16, 18, 20}) {
					double sh = shardsAt(c, m);
					shardAt[m] += sh;
					if (m == 18 && sh >= SHARD_COST) {
						by18++;
					}
					if (m == 20 && sh >= SHARD_COST) {
						by20++;
					}
					if (m == 20 && sh >= 2 * SHARD_COST) {
						two++;
					}
				}
				Char probe = "focused".equals(kind) ? focused(PROPOSED, Policy.GUIDED, run)
					: new Char(PROPOSED, Policy.GUIDED, null, new ArrayList<>(SKILLS), ALL_EIGHT, new Random(run * 1_000_003L + 5).nextLong(), 3);
				probe.play();
				forgeLevel += probe.shardForgeLevel;
			}
			out.append(String.format(Locale.ROOT, "| %s | %.1f / %.1f / %.1f / %.1f / %.1f | %d %% · %d %% | %d %% | %.1f |%n", kind, shardAt[12] / shardRuns, shardAt[14] / shardRuns,
				shardAt[16] / shardRuns, shardAt[18] / shardRuns, shardAt[20] / shardRuns, 100 * by18 / shardRuns, 100 * by20 / shardRuns, 100 * two / shardRuns,
				by20 == 0 ? 0 : forgeLevel / by20));
		}
		double drawnPerChar = 1 - Math.pow(1 - CardConfig.get().mythic_draw_chance, 3 * 3);
		out.append(String.format(Locale.ROOT, "Today a focused player (three skills from 15 to 20, 9 drafts at 0.5 %%) draws a Mythic with %.1f %%.%n", 100 * drawnPerChar));

		String text = out.toString();
		System.out.println(text);
		Path file = Path.of("build", "reports", "mettle-build-feel.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		assertTrue(worstRatio <= 1.15 + 1e-9, "proposed deck over 1.15× v1: " + worstRatio);
		assertTrue(worstWits <= 1.3 + 1e-9, "proposed Wits 14 deck over 1.3× v1: " + worstWits);
		assertTrue(worstJac < 0.25 && worstChase < 0.25, "decks overlap: " + worstJac + " / " + worstChase);
		assertTrue(identicalAll == 0, "identical decks: " + identicalAll);
		assertTrue(worstAt20 > 0, "a keyword can't reach 6 when chased");
	}

	static double witsDeck(Char c, ResourceKey<Skill> skill) {
		Char w = new Char(c.rules, c.policy, c.focus, c.order, c.rate, c.seed, 14);
		return w.bestDeck(skill, 20, c.owned.get(skill)).stream().mapToDouble(o -> CardReportTest.points(o.card)).sum();
	}

	/** Shards a fresh copy of this character holds when its mains reach {@code main}. */
	static double shardsAt(Char template, int main) {
		Char c = new Char(template.rules, template.policy, template.focus, template.order, template.rate, template.seed, template.wits);
		// play() runs to 20; replay with a cut-off.
		for (int m = 1; m <= main; m++) {
			double mainXp = Curves.skillXpFor(m);
			double totalXp = 0;
			for (int i = 0; i < c.order.size(); i++) {
				ResourceKey<Skill> s = c.order.get(i);
				double xp = c.rate[i] * mainXp;
				totalXp += xp;
				int target = Curves.skillLevel(xp);
				for (int l = c.level(s) + 1; l <= target; l++) {
					c.sheet.levels.put(s, l);
					int drafts = DraftRules.draftsFor(l - 1, l);
					int waiting = c.queued.getOrDefault(s, 0) + drafts;
					c.queued.put(s, 0);
					for (int d = 0; d < waiting; d++) {
						c.draft(s);
					}
					if (drafts == 0) {
						c.boon(s);
					}
					if (l == 20) {
						c.shards += 2;
					}
					if (l == 12 || l == 18) {
						c.shards += 1;
					}
					c.redeck(s);
				}
			}
			int cl = Curves.characterLevel(totalXp);
			if (cl > c.characterLevel) {
				c.rerolls = Math.min(DraftRules.MAX_REROLLS, c.rerolls + (cl - c.characterLevel));
				c.characterLevel = cl;
			}
			c.measure(m, cl);
		}
		return c.shards;
	}

	/** Today's tempered value: up by {@code step} of the range, never above the rarity band's top, on the step grid. */
	static double tempered(Trait def, Rarity rarity, double value, double step) {
		if (step <= 0) {
			return value;
		}
		double top = Math.min(CardRules.band(def, rarity)[1], def.min() + TEMPER_CAP * (def.max() - def.min()));
		double grid = def.step() <= 0 ? 1 : def.step();
		top = Math.floor(top / grid + 1e-9) * grid; // the highest value the rarity can roll (CardRules.rollValue)
		double raised = value + step * (def.max() - def.min());
		double v = Math.min(top, (TEMPER_NEAREST ? Math.round(raised / grid) : Math.floor(raised / grid + 1e-9)) * grid);
		return Math.max(value, CardRules.round(v));
	}

	static double jaccard(Set<String> a, Set<String> b) {
		Set<String> union = new HashSet<>(a);
		union.addAll(b);
		Set<String> both = new HashSet<>(a);
		both.retainAll(b);
		return union.isEmpty() ? 0 : both.size() / (double) union.size();
	}

	static String when(int[] xs) {
		int n = nonZero(xs);
		if (n == 0) {
			return "never";
		}
		double s = 0;
		for (int x : xs) {
			s += x;
		}
		return String.format(Locale.ROOT, "%.1f (%d %%)", s / n, 100 * n / xs.length);
	}

	static int nonZero(int[] xs) {
		int n = 0;
		for (int x : xs) {
			if (x > 0) {
				n++;
			}
		}
		return n;
	}
}
