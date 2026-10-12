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
import java.util.Comparator;
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
 * The cards v2 simulator (docs/CARDS.md §14): thousands of careers per skill level a skill from 0 to 20 under the draft's
 * cadence, keep the card that adds most to their best deck, and slot the best legal deck; the numbers are priced in the
 * old perk points (root 1 a rank, tier II 2, tier III 3, Mastery 5, cursed 2 or 3; a trait its value ÷ its card cap; set
 * tiers 0.5 / 1.5 / 3) against v1's draft. A report, with the §14 limits checked:
 * {@code ./gradlew test --tests '*CardReportTest' -Dmettle.balance=true} writes {@code build/reports/mettle-cards.md}.
 */
class CardReportTest {
	private static final int RUNS = 3000;
	private static final int[] LEVELS = {2, 6, 8, 10, 12, 14, 16, 18, 20};
	/** A typical character's attribute in every trait kind (traits scale ×1.105 at 10). */
	private static final int ATTRIBUTE = 10;
	private static final double[] SET_POINTS = {0, 0.5, 1.5, 3};

	@Test
	void report() throws IOException {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "card report: run with -Dmettle.balance=true");
		StringBuilder out = new StringBuilder("# Cards v2 simulation (docs/CARDS.md §14)\n\n");
		out.append(String.format(Locale.ROOT, "%d careers per skill; attribute %d in every trait kind (traits ×%.3f); the governing attribute meets every "
			+ "tier gate. Points: the old perk costs; a trait = its boosted value ÷ its card cap; v1 = the draft before cards (every rank active).%n%n",
			RUNS, ATTRIBUTE, CardRules.boost(1, ATTRIBUTE)));

		Map<String, double[][]> table = new LinkedHashMap<>();
		StringBuilder limits = new StringBuilder("\n## §14 limits at skill 20\n\n| Skill | v1 points | v2 perks | v2 traits | v2 deck (base slots) | ratio | "
			+ "v2 deck (Wits 14, +2 slots) | ratio | max trait total / cap | waits for an early Epic (levels) |\n|---|---|---|---|---|---|---|---|---|---|\n");
		StringBuilder distinct = new StringBuilder("\n## Distinct builds at skill 20 (best deck, perk + trait pairs)\n\n| Skill | mean Jaccard of two careers | identical decks "
			+ "(every rarity, keyword and value) in " + RUNS + " pairs | distinct perk + trait decks |\n|---|---|---|---|\n");
		StringBuilder migration = new StringBuilder("\n## Migration: v1 careers at 20 turned into v2 decks\n\n| Skill | v1 points | migrated deck points | drafts back | "
			+ "share at or above v1 |\n|---|---|---|---|---|\n");
		double ratioSum = 0;
		double ratioWitsSum = 0;
		double worstRatio = 0;
		double worstWits = 0;
		for (String name : TestCards.RELEASED) {
			ResourceKey<Skill> skill = TestCards.skill(name);
			double[][] rows = new double[LEVELS.length][6];
			double v1At20 = 0;
			double perk20 = 0;
			double trait20 = 0;
			double deck20 = 0;
			double wits20 = 0;
			double maxShare = 0;
			double waits = 0;
			int waited = 0;
			List<Set<String>> decks = new ArrayList<>();
			List<Set<String>> exacts = new ArrayList<>();
			double migPoints = 0;
			double migDrafts = 0;
			int migOk = 0;
			for (int run = 0; run < RUNS; run++) {
				Career c = career(skill, run);
				for (int i = 0; i < LEVELS.length; i++) {
					int l = LEVELS[i];
					rows[i][0] += c.owned[l];
					rows[i][1] += CardRules.slots(l, 3);
					rows[i][2] += c.perkPoints[l];
					rows[i][3] += c.traitPoints[l];
					rows[i][4] += c.v1[l];
				}
				v1At20 += c.v1[20];
				perk20 += c.perkPoints[20];
				trait20 += c.traitPoints[20];
				deck20 += c.perkPoints[20] + c.traitPoints[20];
				wits20 += c.witsDeck;
				maxShare = Math.max(maxShare, c.maxTraitShare);
				waits += c.waits;
				waited += c.waited;
				decks.add(c.signature);
				exacts.add(c.exact);
				Migrated m = migrate(skill, c.v1Held);
				migPoints += m.points;
				migDrafts += m.drafts;
				if (m.points + 1e-9 >= c.v1[20]) {
					migOk++;
				}
			}
			table.put(name, rows);
			double ratio = deck20 / v1At20;
			double ratioW = wits20 / v1At20;
			ratioSum += ratio;
			ratioWitsSum += ratioW;
			worstRatio = Math.max(worstRatio, ratio);
			worstWits = Math.max(worstWits, ratioW);
			limits.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %.1f | %.2f× | %.1f | %.2f× | %.2f | %s |%n", name, v1At20 / RUNS, perk20 / RUNS,
				trait20 / RUNS, deck20 / RUNS, ratio, wits20 / RUNS, ratioW, maxShare, waited == 0 ? "—" : String.format(Locale.ROOT, "%.1f", waits / waited)));
			double jaccard = 0;
			int same = 0;
			for (int i = 0; i + 1 < decks.size(); i += 1) {
				Set<String> a = decks.get(i);
				Set<String> b = decks.get((i * 7 + 13) % decks.size());
				Set<String> union = new HashSet<>(a);
				union.addAll(b);
				Set<String> both = new HashSet<>(a);
				both.retainAll(b);
				jaccard += union.isEmpty() ? 0 : both.size() / (double) union.size();
				if (exacts.get(i).equals(exacts.get((i * 7 + 13) % decks.size())) && i != (i * 7 + 13) % decks.size()) {
					same++;
				}
			}
			worstJaccard = Math.max(worstJaccard, jaccard / (decks.size() - 1));
			identical += same;
			distinct.append(String.format(Locale.ROOT, "| %s | %.3f | %d | %d of %d |%n", name, jaccard / (decks.size() - 1), same, new HashSet<>(decks).size(), decks.size()));
			migration.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %d %% |%n", name, v1At20 / RUNS, migPoints / RUNS, migDrafts / RUNS, 100 * migOk / RUNS));
		}
		out.append("## By skill level (mean): cards owned · slots · slotted perk points + trait points (v1 points)\n\n| Skill |");
		for (int l : LEVELS) {
			out.append(" ").append(l).append(" |");
		}
		out.append("\n|---|").append("---|".repeat(LEVELS.length)).append("\n");
		table.forEach((name, rows) -> {
			out.append("| ").append(name).append(" |");
			for (double[] r : rows) {
				out.append(String.format(Locale.ROOT, " %.1f · %.0f · %.1f + %.1f (%.1f) |", r[0] / RUNS, r[1] / RUNS, r[2] / RUNS, r[3] / RUNS, r[4] / RUNS));
			}
			out.append("\n");
		});
		out.append(limits);
		out.append(String.format(Locale.ROOT, "%nMean over the eight skills: **%.2f×** v1 with base slots (limit 1.15×), **%.2f×** with Wits 14 (limit 1.3×); "
			+ "worst skill %.2f× / %.2f×.%n", ratioSum / 8, ratioWitsSum / 8, worstRatio, worstWits));
		out.append(distinct).append(migration);
		out.append(sets());
		out.append(mythics());
		out.append(rebuilds());
		out.append(ceilings());
		String text = out.toString();
		System.out.println(text);
		Path file = Path.of("build", "reports", "mettle-cards.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		assertTrue(maxTraitShareOk, "a trait total went over its cap");
		assertTrue(worstJaccard < 0.3, "two careers' decks overlap too much: " + worstJaccard);
		assertTrue(identical * 10_000 < 8 * RUNS || identical == 0, "identical decks: " + identical);
		assertTrue(worstRatio <= 1.15 + 1e-9, String.format(Locale.ROOT, "a skill's deck at 20 is %.2f× v1 (limit 1.15×)", worstRatio));
		assertTrue(worstWits <= 1.3 + 1e-9, String.format(Locale.ROOT, "a skill's Wits 14 deck at 20 is %.2f× v1 (limit 1.3×)", worstWits));
		assertTrue(ceilingProblems.isEmpty(), String.join(System.lineSeparator(), ceilingProblems));
	}

	// ---------------------------------------------------------------- whole-character ceilings (§14), worst case

	/**
	 * The worst-case deck search of docs/CARDS.md §14: every trait at its total cap, every cursed card held, every choice
	 * of three active sets (at 6) and two Mythics, every condition met at once (sprinting on a road with a blade drawn,
	 * ten seconds in, after a kill, a critical on a foe below 30 % health, unarmoured, alone or among allies helping). For
	 * each capped stat: what the cards would add without the ceiling, and what the game applies (the same functions the
	 * hooks call), which must never pass it. Runs on every build; with -Dmettle.balance=true its table is in the report.
	 */
	@Test
	void ceilingsHoldForEveryDeck() {
		String table = ceilings();
		System.out.println(table);
		assertTrue(ceilingProblems.isEmpty(), String.join("\n", ceilingProblems));
	}

	private static final List<String> ceilingProblems = new ArrayList<>();

	static String ceilings() {
		ceilingProblems.clear();
		Map<String, Double> traits = new HashMap<>();
		TestCards.TRAITS.forEach((id, t) -> traits.put(id.getPath(), CardRules.totalCap(t)));
		Map<String, Perk> cursed = new HashMap<>();
		TestCards.PERKS.forEach((k, p) -> {
			if (p.kind() == Perk.Kind.CURSED) {
				cursed.put(k.identifier().getPath(), p);
			}
		});
		List<String> keywords = TestCards.KEYWORDS.keySet().stream().map(Identifier::getPath).sorted().toList();
		List<String> mythics = TestCardsMythics.load().keySet().stream().map(Identifier::getPath).sorted().toList();
		Ceilings.Stat[] stats = Ceilings.Stat.values();
		double[] raw = new double[stats.length];
		double[] applied = new double[stats.length];
		String[] worst = new String[stats.length];
		double jumpRaw = 0;
		double jumpApplied = 0;
		int decks = 0;
		for (int i = 0; i < keywords.size(); i++) {
			for (int j = i + 1; j < keywords.size(); j++) {
				for (int k = j + 1; k < keywords.size(); k++) {
					Map<String, Integer> sets = Map.of(keywords.get(i), 6, keywords.get(j), 6, keywords.get(k), 6);
					for (int a = 0; a < mythics.size(); a++) {
						for (int b = a + 1; b < mythics.size(); b++) {
							Ceilings.Build build = new Ceilings.Build(traits, sets, Set.of(mythics.get(a), mythics.get(b)), cursed);
							String name = keywords.get(i) + " 6, " + keywords.get(j) + " 6, " + keywords.get(k) + " 6; " + mythics.get(a) + ", " + mythics.get(b);
							decks++;
							double[][] got = ceilingValues(build);
							for (int s = 0; s < stats.length; s++) {
								if (got[s][0] > raw[s]) {
									raw[s] = got[s][0];
									worst[s] = name;
								}
								applied[s] = Math.max(applied[s], got[s][1]);
								if (got[s][1] > stats[s].ceiling + 1e-9) {
									ceilingProblems.add(String.format(Locale.ROOT, "%s: %s applies %s (ceiling %s)", name, stats[s].key,
										Ceilings.percent(stats[s], got[s][1]), Ceilings.percent(stats[s], stats[s].ceiling)));
								}
							}
							// Jumps: Springing and Gale 4 (as heights) and the Spring perk's +0.085, held under two blocks.
							double strength = CardPhysics.JUMP * (1 + CardPhysics.jumpStrengthFor(build.pct("athletics/springing"))
								+ (build.set("gale") >= 4 ? CardPhysics.jumpStrengthFor(0.10) : 0)) + 0.085;
							jumpRaw = Math.max(jumpRaw, CardPhysics.apex(strength));
							double held = CardPhysics.apex(Ceilings.jumpPower((float) strength, strength, 0f));
							jumpApplied = Math.max(jumpApplied, held);
							if (held >= 2.0) {
								ceilingProblems.add(String.format(Locale.ROOT, "%s: a sprint-jump clears %.2f blocks", name, held));
							}
						}
					}
				}
			}
		}
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%n## Whole-character ceilings (§14): the worst of %,d decks%n%n"
			+ "Every trait at its total cap, every cursed card, three sets at 6 and two Mythics, every condition at once. \"Uncapped\" is what the "
			+ "cards would add without the ceilings; \"applied\" is what the game gives (the hooks' own functions).%n%n"
			+ "| Stat | uncapped (worst deck) | applied | ceiling | worst deck |%n|---|---|---|---|---|%n", decks));
		for (int s = 0; s < stats.length; s++) {
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s |%n", stats[s].key, Ceilings.percent(stats[s], raw[s]), Ceilings.percent(stats[s], applied[s]),
				Ceilings.percent(stats[s], stats[s].ceiling), worst[s]));
		}
		out.append(String.format(Locale.ROOT, "| jump | %.2f blocks | %.2f blocks | under 2 blocks | Springing, Gale 4 and Spring |%n", jumpRaw, jumpApplied));
		return out.toString();
	}

	/** Each stat's {uncapped, applied} share for one build, worst over the states the hooks see. */
	private static double[][] ceilingValues(Ceilings.Build b) {
		double[][] out = new double[Ceilings.Stat.values().length][2];
		double move = Ceilings.movementPotential(b, true);
		out[0] = new double[] {move, move + Ceilings.movementCorrection(move)};
		double attribute = Ceilings.meleeAttribute(b);
		double hit = Ceilings.meleeHit(b);
		boolean twin = b.mythic("twin_edge");
		double second = twin ? Ceilings.TWIN_EDGE : 1;
		out[1] = new double[] {attribute * hit * second - 1, attribute * Ceilings.melee(attribute, hit, twin) * second - 1};
		double taken = Math.min(Ceilings.takenPotential(b, true), Ceilings.takenPotential(b, false));
		out[2] = new double[] {1 - taken, 1 - Ceilings.taken(taken)};
		double digAttribute = Ceilings.digAttribute(b);
		double digHook = Ceilings.digHook(b);
		out[3] = new double[] {digAttribute * digHook - 1, digAttribute * Ceilings.dig(digAttribute, digHook) - 1};
		double drop = Ceilings.dropPotential(b);
		out[4] = new double[] {drop, drop * Ceilings.dropScale(drop)};
		return out;
	}

	private static boolean maxTraitShareOk = true;
	private static double worstJaccard;
	private static int identical;

	// ---------------------------------------------------------------- points

	static double perkPoints(Perk p, int rank) {
		return p.kind() == Perk.Kind.ROOT ? p.cost() * rank : p.cost();
	}

	static double traitPoints(Card c, int attribute) {
		double pts = 0;
		for (TraitRoll t : c.traits()) {
			Trait def = TestCards.TRAITS.get(t.trait());
			pts += CardRules.effective(def, t.value(), a -> attribute) / CardRules.cardCap(def);
		}
		return pts;
	}

	static double points(Card c) {
		return perkPoints(TestCards.def(c.perk()), c.rank()) + traitPoints(c, ATTRIBUTE);
	}

	/** The best legal deck from owned cards at a level: one per perk, needs met, one Mastery with two of its side. */
	static List<Card> bestDeck(List<Card> owned, int level, int slots) {
		List<Card> usable = owned.stream().filter(c -> c.needs() <= level).sorted(Comparator.comparingDouble(CardReportTest::points).reversed()).toList();
		List<Card> best = greedy(usable, slots, null);
		double bestPts = total(best);
		Set<ResourceKey<Perk>> masteries = new HashSet<>();
		usable.stream().filter(c -> TestCards.def(c.perk()).kind() == Perk.Kind.MASTERY).forEach(c -> masteries.add(c.perk()));
		for (ResourceKey<Perk> m : masteries) {
			List<Card> d = greedy(usable, slots, m);
			if (total(d) > bestPts) {
				best = d;
				bestPts = total(d);
			}
		}
		return best;
	}

	private static List<Card> greedy(List<Card> usable, int slots, ResourceKey<Perk> mastery) {
		List<Card> out = new ArrayList<>();
		Set<ResourceKey<Perk>> perks = new HashSet<>();
		if (mastery != null) {
			Perk m = TestCards.def(mastery);
			Card mc = usable.stream().filter(c -> c.perk().equals(mastery)).findFirst().orElseThrow();
			List<Card> side = new ArrayList<>();
			Set<ResourceKey<Perk>> sidePerks = new HashSet<>();
			for (Card c : usable) {
				Perk p = TestCards.def(c.perk());
				if (p.kind() == Perk.Kind.PATH && ("any".equals(m.side()) || p.path().equals(m.side())) && sidePerks.add(c.perk()) && side.size() < 2) {
					side.add(c);
				}
			}
			if (side.size() < 2 || slots < 3) {
				return List.of();
			}
			out.add(mc);
			out.addAll(side);
			perks.add(mastery);
			side.forEach(c -> perks.add(c.perk()));
		}
		for (Card c : usable) {
			if (out.size() >= slots) {
				break;
			}
			if (TestCards.def(c.perk()).kind() == Perk.Kind.MASTERY || !perks.add(c.perk())) {
				continue;
			}
			out.add(c);
		}
		return out;
	}

	static double total(List<Card> deck) {
		return deck.stream().mapToDouble(CardReportTest::points).sum();
	}

	// ---------------------------------------------------------------- one career

	private static final class Career {
		final double[] owned = new double[21];
		final double[] perkPoints = new double[21];
		final double[] traitPoints = new double[21];
		final double[] v1 = new double[21];
		double witsDeck;
		double maxTraitShare;
		double waits;
		int waited;
		Set<String> signature = new HashSet<>();
		Set<String> exact = new HashSet<>();
		Map<ResourceKey<Perk>, Integer> v1Held = new HashMap<>();
	}

	private static Career career(ResourceKey<Skill> skill, int run) {
		TestCards.Sheet sheet = new TestCards.Sheet();
		sheet.attribute = 20;
		// Independent seeds: DraftRules.random(seed, n) mixes seed + n × φ, so seeds a multiple of φ apart would replay each other.
		long seed = new Random(run * 1_000_003L + 11).nextLong();
		int dealt = 0;
		int queued = 0;
		double rerolls = DraftRules.START_REROLLS;
		List<Card> owned = new ArrayList<>();
		Map<Card, Integer> drawnAt = new HashMap<>();
		Career c = new Career();
		V1 v1 = new V1(skill, run);
		for (int level = 1; level <= Curves.SKILL_CAP; level++) {
			sheet.levels.put(skill, level);
			int drafts = DraftRules.draftsFor(level - 1, level);
			queued += drafts;
			rerolls = Math.min(DraftRules.MAX_REROLLS, rerolls + 1 / 1.6 + (1 - drafts));
			while (queued > 0) {
				List<Card> hand = CardRules.deal(skill, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(seed, dealt++), List.of(), Set.of());
				if (hand.isEmpty()) {
					break;
				}
				double now = total(bestDeck(owned, level, CardRules.slots(level, 3)));
				if (rerolls >= 1 && gain(owned, hand, level, now) < 0.5) {
					Set<ResourceKey<Perk>> avoid = new HashSet<>(hand.stream().map(Card::perk).toList());
					hand = CardRules.deal(skill, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(seed, dealt++), List.of(), avoid);
					rerolls--;
				}
				Card keep = choose(owned, hand, level, now);
				owned.add(keep);
				drawnAt.put(keep, level);
				sheet.owned.merge(keep.perk(), 1, Integer::sum);
				queued--;
			}
			List<Card> deck = bestDeck(owned, level, CardRules.slots(level, 3));
			c.owned[level] = owned.size();
			c.perkPoints[level] = deck.stream().mapToDouble(x -> perkPoints(TestCards.def(x.perk()), x.rank())).sum();
			c.traitPoints[level] = deck.stream().mapToDouble(x -> traitPoints(x, ATTRIBUTE)).sum();
			c.v1[level] = v1.step(level);
		}
		List<Card> deck = bestDeck(owned, 20, CardRules.slots(20, 3));
		c.witsDeck = total(bestDeck(owned, 20, CardRules.slots(20, 14)));
		// Trait totals of the best Wits 14 deck at the best attribute, against twice the card cap.
		Map<Identifier, Double> totals = new HashMap<>();
		for (Card x : bestDeck(owned, 20, CardRules.slots(20, 14))) {
			for (TraitRoll t : x.traits()) {
				totals.merge(t.trait(), CardRules.effective(TestCards.TRAITS.get(t.trait()), t.value(), a -> 20), Double::sum);
			}
		}
		totals.forEach((id, v) -> {
			Trait def = TestCards.TRAITS.get(id);
			double capped = Math.min(CardRules.totalCap(def), v);
			c.maxTraitShare = Math.max(c.maxTraitShare, capped / CardRules.totalCap(def));
			if (capped > CardRules.totalCap(def) + 1e-9) {
				maxTraitShareOk = false;
			}
		});
		for (Card x : owned) {
			if (x.rarity() == Rarity.EPIC && x.needs() > drawnAt.get(x)) {
				c.waits += x.needs() - drawnAt.get(x);
				c.waited++;
			}
		}
		for (Card x : deck) {
			c.signature.add(x.perk().identifier().getPath());
			x.traits().forEach(t -> c.signature.add(x.perk().identifier().getPath() + "+" + t.trait().getPath()));
			c.exact.add(x.perk().identifier().getPath() + ":" + x.rarity() + ":" + x.keywords() + ":" + x.traits());
		}
		c.v1Held = v1.held;
		return c;
	}

	private static double gain(List<Card> owned, List<Card> hand, int level, double now) {
		double best = -1;
		for (Card h : hand) {
			List<Card> with = new ArrayList<>(owned);
			with.add(h);
			best = Math.max(best, total(bestDeck(with, Math.max(level, h.needs()), CardRules.slots(Math.max(level, h.needs()), 3))) - now);
		}
		return best;
	}

	/** Keep the card that adds most to the best deck (at the level it needs), ties to the most points. */
	private static Card choose(List<Card> owned, List<Card> hand, int level, double now) {
		Card best = hand.getFirst();
		double bestGain = Double.NEGATIVE_INFINITY;
		for (Card h : hand) {
			int at = Math.max(level, h.needs());
			List<Card> with = new ArrayList<>(owned);
			with.add(h);
			double g = total(bestDeck(with, at, CardRules.slots(at, 3))) - total(bestDeck(owned, at, CardRules.slots(at, 3))) + points(h) * 1e-3;
			if (g > bestGain) {
				bestGain = g;
				best = h;
			}
		}
		return best;
	}

	/** The v1 draft beside it, as DraftReportTest plays it (keep the rarest), for the comparison. */
	private static final class V1 {
		final ResourceKey<Skill> skill;
		final long seed;
		final Random policy;
		final V1Sheet sheet = new V1Sheet();
		final Map<ResourceKey<Perk>, Integer> held = sheet.ranks;
		int dealt;
		int queued;
		double rerolls = DraftRules.START_REROLLS;

		V1(ResourceKey<Skill> skill, int run) {
			this.skill = skill;
			this.seed = new Random(run * 1_000_003L + 17).nextLong();
			this.policy = new Random(run * 31L + 7);
		}

		double step(int level) {
			this.sheet.levels.put(this.skill, level);
			int drafts = DraftRules.draftsFor(level - 1, level);
			this.queued += drafts;
			this.rerolls = Math.min(DraftRules.MAX_REROLLS, this.rerolls + 1 / 1.6 + (1 - drafts));
			while (this.queued > 0) {
				if (DraftRules.exhausted(this.skill, this.sheet, TestCards.DECK)) {
					this.queued = 0;
					break;
				}
				List<Card> hand = DraftRules.deal(this.skill, this.sheet, TestCards.DECK, DraftRules.random(this.seed, this.dealt++), List.of(), Set.of());
				if (hand.isEmpty()) {
					break;
				}
				if (this.rerolls >= 1 && hand.stream().noneMatch(x -> x.rarity() == Rarity.EPIC || x.rarity() == Rarity.RARE && level < 12)
					&& DraftRules.candidates(this.skill, this.sheet, TestCards.DECK).size() > hand.size()) {
					Set<ResourceKey<Perk>> avoid = new HashSet<>(hand.stream().map(Card::perk).toList());
					hand = DraftRules.deal(this.skill, this.sheet, TestCards.DECK, DraftRules.random(this.seed, this.dealt++), List.of(), avoid);
					this.rerolls--;
				}
				List<Card> sorted = new ArrayList<>(hand);
				sorted.sort(Comparator.comparingInt((Card x) -> x.rarity() == Rarity.CURSED ? 0 : x.rarity().ordinal() + 1).reversed());
				Card keep = sorted.getFirst();
				if (hand.stream().anyMatch(x -> x.rarity() == Rarity.CURSED) && keep.rarity() != Rarity.EPIC && this.policy.nextInt(3) == 0) {
					keep = hand.stream().filter(x -> x.rarity() == Rarity.CURSED).findFirst().orElseThrow();
				}
				this.held.put(keep.perk(), keep.rank());
				this.queued--;
			}
			double pts = 0;
			for (var e : this.held.entrySet()) {
				pts += TestCards.def(e.getKey()).cost() * e.getValue();
			}
			return pts;
		}
	}

	/** A v1 character: skill levels and held ranks; every attribute 20. */
	private static final class V1Sheet implements DraftRules.Sheet {
		final Map<ResourceKey<Skill>, Integer> levels = new HashMap<>();
		final Map<ResourceKey<Perk>, Integer> ranks = new HashMap<>();

		@Override
		public int level(ResourceKey<Skill> skill) {
			return this.levels.getOrDefault(skill, 0);
		}

		@Override
		public int attribute(ResourceKey<Skill> skill) {
			return 20;
		}

		@Override
		public int rank(ResourceKey<Perk> perk) {
			return this.ranks.getOrDefault(perk, 0);
		}
	}

	// ---------------------------------------------------------------- migration

	private record Migrated(double points, int drafts) {
	}

	private static Migrated migrate(ResourceKey<Skill> skill, Map<ResourceKey<Perk>, Integer> held) {
		CardRules.Migration m = CardRules.migrate(held, TestCards.DECK, TestCards.DATA_SET, held.hashCode(), s -> 20, 3);
		double pts = 0;
		for (CardRules.Migrated c : m.cards()) {
			if (c.slotted()) {
				pts += points(c.card());
			}
		}
		return new Migrated(pts, m.drafts().values().stream().mapToInt(Integer::intValue).sum());
	}

	// ---------------------------------------------------------------- sets, Mythics, rebuilds

	/** All-eight characters: each skill's best deck at 20, then the keywords they carry across decks. */
	private static String sets() {
		int runs = 600;
		Map<Identifier, Integer> six = new HashMap<>();
		Map<Identifier, Integer> four = new HashMap<>();
		double bestSetPoints = 0;
		double meanSetPoints = 0;
		int[] firstTwo = new int[runs];
		int[] firstFour = new int[runs];
		int[] firstSix = new int[runs];
		for (int run = 0; run < runs; run++) {
			Map<ResourceKey<Skill>, List<Card>> owned = new HashMap<>();
			Map<ResourceKey<Skill>, TestCards.Sheet> sheets = new HashMap<>();
			long seed = new Random(run * 1_000_003L + 5).nextLong();
			int dealt = 0;
			for (int level = 1; level <= 20; level++) {
				for (String name : TestCards.RELEASED) {
					ResourceKey<Skill> skill = TestCards.skill(name);
					TestCards.Sheet sheet = sheets.computeIfAbsent(skill, s -> new TestCards.Sheet());
					sheet.levels.put(skill, level);
					List<Card> mine = owned.computeIfAbsent(skill, s -> new ArrayList<>());
					for (int d = 0; d < DraftRules.draftsFor(level - 1, level); d++) {
						List<Card> hand = CardRules.deal(skill, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(seed, dealt++), List.of(), Set.of());
						if (hand.isEmpty()) {
							continue;
						}
						Card keep = choose(mine, hand, level, total(bestDeck(mine, level, CardRules.slots(level, 3))));
						mine.add(keep);
						sheet.owned.merge(keep.perk(), 1, Integer::sum);
					}
				}
				Map<Identifier, Integer> counts = new HashMap<>();
				Map<Identifier, Set<ResourceKey<Skill>>> decks = new HashMap<>();
				for (Map.Entry<ResourceKey<Skill>, List<Card>> e : owned.entrySet()) {
					for (Card c : bestDeck(e.getValue(), level, CardRules.slots(level, 3))) {
						for (Identifier k : c.keywords()) {
							counts.merge(k, 1, Integer::sum);
							decks.computeIfAbsent(k, x -> new HashSet<>()).add(e.getKey());
						}
					}
				}
				int top = 0;
				List<Double> setPts = new ArrayList<>();
				for (Identifier k : counts.keySet()) {
					int tier = CardRules.tier(counts.get(k), decks.get(k).size());
					top = Math.max(top, tier);
					setPts.add(tier >= 6 ? 5.0 : tier >= 4 ? 2.0 : tier >= 2 ? 0.5 : 0);
					if (level == 20 && tier >= 6) {
						six.merge(k, 1, Integer::sum);
					}
					if (level == 20 && tier >= 4) {
						four.merge(k, 1, Integer::sum);
					}
				}
				int characterLevel = Curves.characterLevel(8 * Curves.skillXpFor(level));
				if (top >= 2 && firstTwo[run] == 0) {
					firstTwo[run] = characterLevel;
				}
				if (top >= 4 && firstFour[run] == 0) {
					firstFour[run] = characterLevel;
				}
				if (top >= 6 && firstSix[run] == 0) {
					firstSix[run] = characterLevel;
				}
				if (level == 20) {
					setPts.sort(Comparator.reverseOrder());
					double p = setPts.stream().limit(CardConfig.get().active_sets).mapToDouble(Double::doubleValue).sum();
					meanSetPoints += p;
					bestSetPoints = Math.max(bestSetPoints, p);
				}
			}
		}
		StringBuilder out = new StringBuilder("\n## Sets: an all-eight character (each skill's best deck for points, sets not chased)\n\n");
		out.append(String.format(Locale.ROOT, "First reaches a 2-set at character level %.1f, a 4-set at %.1f, a 6-set at %s (mean of those that do). "
			+ "Set points of the three best sets at 20: mean %.1f, best %.1f (limit 15).%n%n",
			mean(firstTwo), mean(firstFour), firstSix.length == 0 ? "never" : String.format(Locale.ROOT, "%.1f (%d %%)", mean(firstSix), 100 * nonZero(firstSix) / runs),
			meanSetPoints / runs, bestSetPoints));
		out.append("| Keyword | careers with a 4-set at 20 | with a 6-set | cards that can carry it (native · in a second-keyword pool) |\n|---|---|---|---|\n");
		TestCards.KEYWORDS.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).forEach(k -> {
			int natives = 0;
			int pools = 0;
			for (String name : TestCards.RELEASED) {
				ResourceKey<Skill> skill = TestCards.skill(name);
				for (ResourceKey<Perk> p : TestCards.DECK.of(skill)) {
					if (TestCards.DATA_SET.nativeKeyword(p).map(k::equals).orElse(false)) {
						natives++;
					}
				}
				if (TestCards.DATA_SET.pool(skill).contains(k)) {
					pools += TestCards.DECK.of(skill).size();
				}
			}
			out.append(String.format(Locale.ROOT, "| %s | %d %% | %d %% | %d · %d |%n", k.getPath(), 100 * four.getOrDefault(k, 0) / runs,
				100 * six.getOrDefault(k, 0) / runs, natives, pools));
		});
		return out.toString();
	}

	private static double mean(int[] xs) {
		int n = nonZero(xs);
		double s = 0;
		for (int x : xs) {
			s += x;
		}
		return n == 0 ? 0 : s / n;
	}

	private static int nonZero(int[] xs) {
		int n = 0;
		for (int x : xs) {
			if (x > 0) {
				n++;
			}
		}
		return n;
	}

	private static String mythics() {
		double chance = CardConfig.get().mythic_draw_chance;
		int drafts = DraftRules.draftsFor(CardConfig.get().mythic_draw_from, 20);
		double perSkill = 1 - Math.pow(1 - chance, drafts);
		double perCharacter = 1 - Math.pow(1 - perSkill, 8);
		return String.format(Locale.ROOT, "%n## Mythic draws (§3.5)%n%nDrafts from skill %d to 20 in one skill: %d; a drawn Mythic in one skill %.1f %%; "
			+ "a maxed character (all eight skills) draws at least one with %.1f %% (while that skill's Mythic is unclaimed). A server of 20 such "
			+ "characters expects %.1f of the eight drawn Mythics.%n", CardConfig.get().mythic_draw_from, drafts, 100 * perSkill, 100 * perCharacter,
			20 * 8 * perSkill);
	}

	private static String rebuilds() {
		StringBuilder out = new StringBuilder("\n## Rebuild cost (§7.1): every slotted card out, in bits (lessons for scale: 9→10 265, 14→15 580, 19→20 1,015)\n\n");
		out.append("| Skill level | slots | fee for a full deck of typical cards |\n|---|---|---|\n");
		for (int level : new int[] {10, 15, 20}) {
			int slots = CardRules.slots(level, 3);
			// A typical deck: the deepest cards a career holds at that level (roots 2–7, tier II 6–9, tier III 12–15, Masteries 18–20).
			int[] needs = level >= 20 ? new int[] {20, 15, 13, 12, 9, 7, 7, 4} : level >= 15 ? new int[] {13, 12, 9, 7, 7, 6, 4} : new int[] {9, 7, 7, 6, 4, 2};
			int fee = 0;
			for (int i = 0; i < Math.min(slots, needs.length); i++) {
				fee += fee(needs[i]);
			}
			out.append(String.format(Locale.ROOT, "| %d | %d | %,d bits + a draught |%n", level, slots, fee));
		}
		return out.toString();
	}

	static int fee(int needs) {
		int[] f = CardConfig.get().rebuild_fees;
		return needs >= 18 ? f[3] : needs >= 12 ? f[2] : needs >= 6 ? f[1] : f[0];
	}
}
