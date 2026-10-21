package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.core.BuildState.Byname;
import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * "Your build" in plain words (docs/BUILD_FEEL.md §2.3), as data free of players and worlds: the byname and tier, up to
 * five lines (each active set at its tier, then the two traits nearest their caps, then the slotted Mastery and
 * Mythics), and the nearest next step. The overlay turns the lines into words; every number is the derived, capped one.
 */
public final class BuildSummary {
	public static final int LINES = 5;

	private BuildSummary() {
	}

	public enum Kind {
		SET, TRAIT, MASTERY, MYTHIC
	}

	/** One line: a set at its tier, a trait's total against its cap, a slotted Mastery (perk id) or Mythic (its id). */
	public record Line(Kind kind, Identifier id, int tier, double value, double cap) {
	}

	public enum NextKind {
		/** {@code cards} more cards of {@code keyword} reach {@code tier}. */
		SET,
		/** A byname tier comes with the next set tier. */
		BYNAME,
		/** {@code shards} more Mythic shards make a forge. */
		FORGE
	}

	public record Next(NextKind kind, Identifier keyword, int cards, int tier, int shards) {
	}

	public record Summary(Optional<Byname> byname, int tier, List<Line> lines, Optional<Next> next) {
	}

	/**
	 * @param derived  what the deck makes active
	 * @param byname   the byname worn and the tier earned
	 * @param shards   shards held, and what the next forge costs
	 */
	public static Summary of(Bynames.Build build, CardRules.Derived derived, CardRules.Data data, Optional<Byname> byname, int tier, int shards, int forgeCost) {
		List<Line> lines = new ArrayList<>();
		for (Identifier k : Bynames.keywords(build)) {
			if (lines.size() < LINES) {
				lines.add(new Line(Kind.SET, k, derived.tiers().get(k), derived.sets().getOrDefault(k, 0), 0));
			}
		}
		derived.traits().entrySet().stream()
			.filter(e -> data.trait(e.getKey()).isPresent())
			.sorted(Comparator.comparingDouble((java.util.Map.Entry<Identifier, Double> e) -> -e.getValue() / CardRules.totalCap(data.trait(e.getKey()).get()))
				.thenComparing(e -> e.getKey().toString()))
			.limit(2)
			.forEach(e -> {
				if (lines.size() < LINES) {
					lines.add(new Line(Kind.TRAIT, e.getKey(), 0, e.getValue(), CardRules.totalCap(data.trait(e.getKey()).get())));
				}
			});
		for (CardData c : build.active()) {
			if (lines.size() >= LINES) {
				break;
			}
			if (c.mythic()) {
				lines.add(new Line(Kind.MYTHIC, c.mythicId().orElseThrow(), 0, 0, 0));
			} else if (build.perks().apply(c.perk()).map(p -> p.kind() == Perk.Kind.MASTERY).orElse(false)) {
				lines.add(new Line(Kind.MASTERY, c.perk().identifier(), 0, 0, 0));
			}
		}
		return new Summary(byname, tier, lines, next(build, derived, tier, shards, forgeCost));
	}

	/**
	 * The nearest next step: the keyword fewest cards from its next tier (one or two away; the Focus wins a tie, then the
	 * active ones), else the shards a forge still needs.
	 */
	static Optional<Next> next(Bynames.Build build, CardRules.Derived derived, int tier, int shards, int forgeCost) {
		Next best = null;
		for (var e : derived.sets().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString))).toList()) {
			Identifier k = e.getKey();
			int count = e.getValue();
			int nextTier = count < 2 ? 2 : count < 4 ? 4 : count < 6 ? 6 : 0;
			if (nextTier == 0 || k.equals(CardRules.HEX) && !build.focus().map(k::equals).orElse(false)) {
				continue;
			}
			int need = nextTier - count;
			if (need > 2) {
				continue;
			}
			boolean focus = build.focus().map(k::equals).orElse(false);
			boolean active = derived.tiers().containsKey(k);
			if (best == null || need < best.cards() || need == best.cards() && (focus || active && !build.focus().map(best.keyword()::equals).orElse(false)
				&& !derived.tiers().containsKey(best.keyword()))) {
				best = new Next(nextTier / 2 > tier && nextTier >= 2 ? NextKind.BYNAME : NextKind.SET, k, need, nextTier, 0);
			}
		}
		if (best != null) {
			return Optional.of(best);
		}
		if (shards > 0 && shards < forgeCost) {
			return Optional.of(new Next(NextKind.FORGE, CardRules.HEX, 0, 0, forgeCost - shards));
		}
		return Optional.empty();
	}
}
