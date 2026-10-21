package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.Skill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Mythic shards and the forge (docs/BUILD_FEEL.md §5), free of players and worlds. Every Mythic stays one of a kind: a
 * forge claims an unclaimed drawn Mythic, never a copy; with none left anywhere, shards buy the cosmetic legend title.
 */
public final class ForgeRules {
	private ForgeRules() {
	}

	/** A drawn Mythic as the forge sees it: its id, its skill, whether someone holds it. */
	public record Drawn(Identifier id, ResourceKey<Skill> skill, boolean claimed) {
	}

	/** The shards the next forge costs: 30, 45, 60, then 60 again (the last repeats). */
	public static int cost(int forgesMade) {
		int[] costs = CardConfig.get().shard_forge_costs;
		return costs[Math.clamp(forgesMade, 0, costs.length - 1)];
	}

	/** Why this Mythic can't be forged now, or empty (§5.3): drawn, unclaimed, a skill at 18 in its skill, enough shards. */
	public static Optional<CardRules.Refusal> canForge(Optional<Drawn> mythic, int shards, int forgesMade, ToIntFunction<ResourceKey<Skill>> levels) {
		if (mythic.isEmpty()) {
			return Optional.of(CardRules.Refusal.NOT_DRAWN);
		}
		if (mythic.get().claimed()) {
			return Optional.of(CardRules.Refusal.MYTHIC_HELD);
		}
		if (levels.applyAsInt(mythic.get().skill()) < CardConfig.get().forge_skill) {
			return Optional.of(CardRules.Refusal.FORGE_SKILL);
		}
		if (shards < cost(forgesMade)) {
			return Optional.of(CardRules.Refusal.NO_SHARDS);
		}
		return Optional.empty();
	}

	/**
	 * Why the legend title can't be bought now, or empty (§5.3): only when no drawn Mythic is unclaimed anywhere, one title
	 * a player with up to {@code legend_stars_max} stars, and enough shards.
	 */
	public static Optional<CardRules.Refusal> canLegend(List<Drawn> pool, int shards, int forgesMade, int stars) {
		if (pool.stream().anyMatch(d -> !d.claimed())) {
			return Optional.of(CardRules.Refusal.POOL_LEFT);
		}
		if (stars >= CardConfig.get().legend_stars_max) {
			return Optional.of(CardRules.Refusal.LEGEND_FULL);
		}
		if (shards < cost(forgesMade)) {
			return Optional.of(CardRules.Refusal.NO_SHARDS);
		}
		return Optional.empty();
	}

	/** Whether a forge is ready (the forge-ready milestone): shards for it, and something it can buy now. */
	public static boolean ready(List<Drawn> pool, int shards, int forgesMade, int stars, ToIntFunction<ResourceKey<Skill>> levels) {
		if (shards < cost(forgesMade)) {
			return false;
		}
		for (Drawn d : pool) {
			if (canForge(Optional.of(d), shards, forgesMade, levels).isEmpty()) {
				return true;
			}
		}
		return canLegend(pool, shards, forgesMade, stars).isEmpty();
	}

	/** The skills that still hold an unclaimed drawn Mythic (the Forge tab says which, and what they need). */
	public static List<ResourceKey<Skill>> skillsWithUnclaimed(List<Drawn> pool) {
		List<ResourceKey<Skill>> out = new ArrayList<>();
		for (Drawn d : pool) {
			if (!d.claimed() && !out.contains(d.skill())) {
				out.add(d.skill());
			}
		}
		out.sort(Comparator.comparing(s -> s.identifier().toString()));
		return out;
	}

	/**
	 * The drawn Mythic a successful roll offers (§5): one of the skill's still unclaimed, chosen from the roll's own hash so
	 * it never depends on what the player does; empty when the skill's three are all held (that roll gives shards).
	 */
	public static Optional<Identifier> offered(List<Identifier> skillMythics, Predicate<Identifier> unclaimed, long pick) {
		List<Identifier> left = skillMythics.stream().filter(unclaimed).sorted(Comparator.comparing(Identifier::toString)).toList();
		return left.isEmpty() ? Optional.empty() : Optional.of(left.get((int) Math.floorMod(pick, (long) left.size())));
	}
}
