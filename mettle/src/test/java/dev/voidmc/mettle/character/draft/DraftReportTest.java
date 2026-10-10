package dev.voidmc.mettle.character.draft;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.PerkRules;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The draft simulator behind docs/DRAFT.md §6 and BALANCE.md §7.3: thousands of characters level each wave-1 skill from 0
 * to 20, keep a card from every hand, and spend rerolls, against the old trees' points bought the cheapest way. A report,
 * not a check: {@code ./gradlew test --tests '*DraftReportTest' -Dmettle.balance=true} writes
 * {@code build/reports/mettle-draft.md}.
 *
 * <p>The player's policy: keep the rarest non-cursed card (a cursed card a third of the time it is offered, when it is
 * the rarest), and reroll a hand with nothing better than common (below skill 12) or rare (from 12) while it has a reroll. A reroll comes every 1.6 skill
 * levels (the mixed session's character levels per skill level, BALANCE.md §7.3).
 */
class DraftReportTest {
	private static final int RUNS = 3000;
	private static final int[] LEVELS = {2, 4, 6, 8, 10, 12, 14, 16, 18, 20};

	@Test
	void report() throws IOException {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "draft report: run with -Dmettle.balance=true");
		DraftRules.Deck deck = TestDeck.deck();
		StringBuilder out = new StringBuilder("# Draft simulation\n\n");
		out.append(String.format(Locale.ROOT, "%d characters per skill, attribute meeting every gate (10 at tier III: the soft gate barely matters there).%n%n", RUNS));
		for (int halfRateFrom : new int[] {DraftRules.HALF_RATE_FROM, Integer.MAX_VALUE}) {
		out.append(halfRateFrom == Integer.MAX_VALUE ? "\n# Schedule: a draft every level (the lever off)\n\n"
			: "\n# Schedule: a draft every level to " + (halfRateFrom - 1) + ", then on even levels, odd ones a reroll (the default)\n\n");
		out.append("## Ranks held by skill level: draft (mean) vs the old trees bought the cheapest way\n\n");
		out.append("| Skill |");
		for (int l : LEVELS) {
			out.append(" ").append(l).append(" |");
		}
		out.append("\n|---|");
		out.append("---|".repeat(LEVELS.length)).append("\n");
		StringBuilder depth = new StringBuilder("\n## Depth held (old points the held perks cost): draft vs old (= points, at most the level)\n\n| Skill |");
		for (int l : LEVELS) {
			depth.append(" ").append(l).append(" |");
		}
		depth.append("\n|---|").append("---|".repeat(LEVELS.length)).append("\n");
		StringBuilder misc = new StringBuilder("\n## Per skill, over a whole career (0 → 20)\n\n"
			+ "| Skill | first tier II card kept at | mastery at (mean) | cursed held at 20 | rerolls spent | deck spent at (mean level, if ever) | drafts left at 20 |\n|---|---|---|---|---|---|---|\n");
		for (String name : TestDeck.RELEASED) {
			ResourceKey<Skill> skill = TestDeck.skill(name);
			double[] ranks = new double[21];
			double[] cost = new double[21];
			double tierTwoAt = 0;
			double masteryAt = 0;
			int mastered = 0;
			double cursed = 0;
			double rerolls = 0;
			double spentAt = 0;
			int spent = 0;
			double leftover = 0;
			for (int run = 0; run < RUNS; run++) {
				Career c = career(skill, deck, run, halfRateFrom);
				for (int l = 0; l <= 20; l++) {
					ranks[l] += c.ranks[l];
					cost[l] += c.cost[l];
				}
				tierTwoAt += c.tierTwoAt;
				if (c.masteryAt > 0) {
					masteryAt += c.masteryAt;
					mastered++;
				}
				cursed += c.cursed;
				rerolls += c.rerollsSpent;
				if (c.spentAt > 0) {
					spentAt += c.spentAt;
					spent++;
				}
				leftover += c.leftover;
			}
			int[] old = oldRanks(skill, deck);
			int[] oldCost = oldCost(skill, deck);
			out.append("| ").append(name).append(" |");
			depth.append("| ").append(name).append(" |");
			for (int l : LEVELS) {
				out.append(String.format(Locale.ROOT, " %.1f (%d) |", ranks[l] / RUNS, old[l]));
				depth.append(String.format(Locale.ROOT, " %.1f (%d) |", cost[l] / RUNS, oldCost[l]));
			}
			out.append("\n");
			depth.append("\n");
			misc.append(String.format(Locale.ROOT, "| %s | %.1f | %s | %.2f | %.1f | %s | %.2f |%n", name, tierTwoAt / RUNS,
				mastered == 0 ? "never" : String.format(Locale.ROOT, "%.1f (%d %%)", masteryAt / mastered, 100 * mastered / RUNS),
				cursed / RUNS, rerolls / RUNS, spent == 0 ? "never" : String.format(Locale.ROOT, "%.1f (%d %%)", spentAt / spent, 100 * spent / RUNS),
				leftover / RUNS));
		}
		out.append(depth).append(misc);
		}

		out.append("\n## Slot odds by skill level (a full deck)\n\n| Level | Common | Rare | Epic | Cursed |\n|---|---|---|---|---|\n");
		for (int l : new int[] {0, 5, 10, 15, 20}) {
			Map<Rarity, Double> o = DraftRules.odds(l);
			out.append(String.format(Locale.ROOT, "| %d | %.1f %% | %.1f %% | %.1f %% | %.1f %% |%n", l, o.get(Rarity.COMMON), o.get(Rarity.RARE), o.get(Rarity.EPIC), o.get(Rarity.CURSED)));
		}
		String text = out.toString();
		System.out.println(text);
		Path file = Path.of("build", "reports", "mettle-draft.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		assertTrue(text.contains("mining"));
	}

	/** One character's career in one skill. */
	private static final class Career {
		final int[] ranks = new int[21];
		final int[] cost = new int[21];
		int tierTwoAt;
		int masteryAt;
		int cursed;
		int rerollsSpent;
		int spentAt;
		int leftover;
	}

	private static Career career(ResourceKey<Skill> skill, DraftRules.Deck deck, int run, int halfRateFrom) {
		TestDeck.Sheet sheet = new TestDeck.Sheet();
		sheet.attribute = 20;
		Random policy = new Random(run * 31L + 7);
		long seed = run * 0x9E3779B97F4A7C15L;
		int dealt = 0;
		int queued = 0;
		double rerolls = DraftRules.START_REROLLS;
		Career c = new Career();
		for (int level = 1; level <= Curves.SKILL_CAP; level++) {
			sheet.levels.put(skill, level);
			int drafts = DraftRules.draftsFor(level - 1, level, halfRateFrom);
			queued += drafts;
			rerolls = Math.min(DraftRules.MAX_REROLLS, rerolls + 1 / 1.6 + (1 - drafts));
			while (queued > 0) {
				if (DraftRules.exhausted(skill, sheet, deck)) {
					if (c.spentAt == 0) {
						c.spentAt = level;
					}
					rerolls = Math.min(DraftRules.MAX_REROLLS, rerolls + queued);
					queued = 0;
					break;
				}
				List<Card> hand = DraftRules.deal(skill, sheet, deck, DraftRules.random(seed, dealt++), List.of(), Set.of());
				if (hand.isEmpty()) {
					break;
				}
				if (rerolls >= 1 && hand.stream().noneMatch(x -> x.rarity() == Rarity.EPIC || x.rarity() == Rarity.RARE && sheet.level(skill) < 12) && DraftRules.candidates(skill, sheet, deck).size() > hand.size()) {
					Set<ResourceKey<Perk>> avoid = new HashSet<>(hand.stream().map(Card::perk).toList());
					hand = DraftRules.deal(skill, sheet, deck, DraftRules.random(seed, dealt++), List.of(), avoid);
					rerolls--;
					c.rerollsSpent++;
				}
				Card keep = choose(hand, policy);
				Perk p = deck.get(keep.perk()).orElseThrow();
				sheet.ranks.put(keep.perk(), keep.rank());
				queued--;
				if (p.kind() == Perk.Kind.PATH && c.tierTwoAt == 0) {
					c.tierTwoAt = level;
				}
				if (p.kind() == Perk.Kind.MASTERY) {
					c.masteryAt = level;
				}
				if (p.kind() == Perk.Kind.CURSED) {
					c.cursed++;
				}
			}
			int r = 0;
			int k = 0;
			for (var e : sheet.ranks.entrySet()) {
				r += e.getValue();
				k += deck.get(e.getKey()).orElseThrow().cost() * e.getValue();
			}
			c.ranks[level] = r;
			c.cost[level] = k;
		}
		c.leftover = queued;
		return c;
	}

	private static Card choose(List<Card> hand, Random policy) {
		List<Card> sorted = new ArrayList<>(hand);
		sorted.sort(Comparator.comparingInt((Card x) -> x.rarity() == Rarity.CURSED ? 0 : x.rarity().ordinal() + 1).reversed());
		Card best = sorted.getFirst();
		boolean cursedOffered = hand.stream().anyMatch(x -> x.rarity() == Rarity.CURSED);
		if (cursedOffered && best.rarity() != Rarity.EPIC && policy.nextInt(3) == 0) {
			return hand.stream().filter(x -> x.rarity() == Rarity.CURSED).findFirst().orElseThrow();
		}
		return best;
	}

	/** The old trees: points = level, buying the cheapest perk the rules allow, as many ranks as the points buy. */
	private static int[] oldRanks(ResourceKey<Skill> skill, DraftRules.Deck deck) {
		return old(skill, deck, false);
	}

	private static int[] oldCost(ResourceKey<Skill> skill, DraftRules.Deck deck) {
		return old(skill, deck, true);
	}

	private static int[] old(ResourceKey<Skill> skill, DraftRules.Deck deck, boolean cost) {
		TestDeck.Sheet sheet = new TestDeck.Sheet();
		int[] out = new int[21];
		int spent = 0;
		for (int level = 1; level <= 20; level++) {
			sheet.levels.put(skill, level);
			boolean bought = true;
			while (bought) {
				bought = false;
				PerkRules.TreeView tree = DraftRules.tree(skill, sheet, deck);
				tree = new PerkRules.TreeView(tree.skillLevel(), 20, level - spent, tree.held(), tree.perks());
				ResourceKey<Perk> cheapest = null;
				for (ResourceKey<Perk> k : deck.of(skill)) {
					Perk p = deck.get(k).orElseThrow();
					if (p.kind() != Perk.Kind.CURSED && PerkRules.check(k, p, tree).isEmpty()
						&& (cheapest == null || p.cost() < deck.get(cheapest).orElseThrow().cost())) {
						cheapest = k;
					}
				}
				if (cheapest != null) {
					sheet.ranks.merge(cheapest, 1, Integer::sum);
					spent += deck.get(cheapest).orElseThrow().cost();
					bought = true;
				}
			}
			int r = 0;
			int k = 0;
			for (var e : sheet.ranks.entrySet()) {
				r += e.getValue();
				k += deck.get(e.getKey()).orElseThrow().cost() * e.getValue();
			}
			out[level] = cost ? k : r;
		}
		return out;
	}
}
