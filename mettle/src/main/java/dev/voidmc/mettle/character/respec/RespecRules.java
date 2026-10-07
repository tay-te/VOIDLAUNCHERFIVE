package dev.voidmc.mettle.character.respec;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;

/**
 * What a respec gives back (CHARACTER.md §6), free of players and worlds so it can be tested. The Draught of Forgetting
 * refunds one skill's bought perks; the Draught of Unbinding returns the placed attribute points.
 */
public final class RespecRules {
	private RespecRules() {
	}

	/**
	 * One tree forgotten.
	 *
	 * @param kept    every perk the character still holds (other skills, earned perks, perks no longer in the data)
	 * @param points  perk points returned to the forgotten skill
	 * @param perks   how many perks were forgotten (a ranked root counts once)
	 * @param mastery whether the Mastery was among them
	 */
	public record Forgetting(Map<ResourceKey<Perk>, Integer> kept, int points, int perks, boolean mastery) {
		/** Nothing bought in that tree: the draught is refused and kept. */
		public boolean nothing() {
			return this.perks == 0;
		}
	}

	/** Refunds every bought perk (roots, paths, the Mastery) of {@code skill}. Feats, synergies and teachings stay. */
	public static Forgetting forget(Map<ResourceKey<Perk>, Integer> held, Function<ResourceKey<Perk>, Optional<Perk>> lookup, ResourceKey<Skill> skill) {
		Map<ResourceKey<Perk>, Integer> kept = new HashMap<>();
		int points = 0;
		int perks = 0;
		boolean mastery = false;
		for (Map.Entry<ResourceKey<Perk>, Integer> e : held.entrySet()) {
			Optional<Perk> perk = lookup.apply(e.getKey());
			if (perk.isPresent() && perk.get().kind().bought() && perk.get().skill().equals(skill)) {
				points += perk.get().cost() * e.getValue();
				perks++;
				mastery |= perk.get().kind() == Perk.Kind.MASTERY;
			} else {
				kept.put(e.getKey(), e.getValue());
			}
		}
		return new Forgetting(Map.copyOf(kept), points, perks, mastery);
	}

	/**
	 * Attribute points the player placed by hand: the ones from character levels and, for a background that hands them
	 * out (the Wanderer), its free points. A background's fixed bonus is not placed and never comes back. Unbinding
	 * returns all of these; the free points come back as free points, to place again.
	 */
	public static int placed(Map<CharacterAttribute, Integer> spent) {
		return spent.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Attribute points waiting to be placed: one per character level above 1, the background's free points, and points
	 * from perks, less what is placed.
	 */
	public static int unplaced(int characterLevel, int placed, int freePoints, int bonus) {
		return Math.max(0, characterLevel - 1 + freePoints + bonus - placed);
	}
}
