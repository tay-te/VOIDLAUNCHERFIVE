package dev.voidmc.mettle.character;

import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Skill;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;

/**
 * The rules a "begin as" request must meet, kept free of players and worlds so they can be tested on their own. The
 * server runs them before recording anything; the overlay shows the reason when one fails.
 */
public final class BackgroundRules {
	public enum Reason {
		ALREADY_CHOSEN, UNKNOWN_BACKGROUND, POINTS_NOT_PLACED, POINTS_INVALID, SKILLS_NOT_PICKED, SKILL_INVALID;

		public String translationKey() {
			return "mettle.picker.rejected." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Either the cleaned-up choice or why it was refused. */
	public record Result(Optional<Reason> rejected, Map<CharacterAttribute, Integer> spent, List<ResourceKey<Skill>> favoured) {
		static Result reject(Reason reason) {
			return new Result(Optional.of(reason), Map.of(), List.of());
		}

		public boolean ok() {
			return this.rejected.isEmpty();
		}
	}

	private BackgroundRules() {
	}

	/**
	 * Checks a request against a background. Points and picks are only read for backgrounds that hand them to the player
	 * (the Wanderer); for every other background they are ignored, so a stale client cannot add points.
	 */
	public static Result validate(Background background, Map<CharacterAttribute, Integer> spent, List<ResourceKey<Skill>> favoured,
		Function<ResourceKey<Skill>, Optional<Skill>> skills) {
		Map<CharacterAttribute, Integer> cleanSpent = new EnumMap<>(CharacterAttribute.class);
		if (background.freePoints() > 0) {
			int total = 0;
			for (Map.Entry<CharacterAttribute, Integer> e : spent.entrySet()) {
				int points = e.getValue();
				int value = CharacterAttribute.BASE + background.bonus(e.getKey()) + points;
				if (points < 0 || value > CharacterAttribute.MAX) {
					return Result.reject(Reason.POINTS_INVALID);
				}
				if (points > 0) {
					cleanSpent.put(e.getKey(), points);
				}
				total += points;
			}
			if (total > background.freePoints()) {
				return Result.reject(Reason.POINTS_INVALID);
			}
			if (total < background.freePoints()) {
				return Result.reject(Reason.POINTS_NOT_PLACED);
			}
		}

		List<ResourceKey<Skill>> cleanFavoured = List.of();
		if (background.freeFavoured() > 0) {
			if (new HashSet<>(favoured).size() != favoured.size() || favoured.size() > background.freeFavoured()) {
				return Result.reject(Reason.SKILL_INVALID);
			}
			if (favoured.size() < background.freeFavoured()) {
				return Result.reject(Reason.SKILLS_NOT_PICKED);
			}
			for (ResourceKey<Skill> key : favoured) {
				Optional<Skill> skill = skills.apply(key);
				if (skill.isEmpty() || !skill.get().released()) {
					return Result.reject(Reason.SKILL_INVALID);
				}
			}
			cleanFavoured = List.copyOf(favoured);
		}
		return new Result(Optional.empty(), Map.copyOf(cleanSpent), cleanFavoured);
	}
}
