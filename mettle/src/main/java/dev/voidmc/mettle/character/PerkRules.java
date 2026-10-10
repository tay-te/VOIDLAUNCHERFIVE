package dev.voidmc.mettle.character;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.core.Curves;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * The tree's shape rules (TREES.md §1), free of players and worlds so they can be tested. Since the draft (docs/DRAFT.md)
 * nothing is bought: {@link #eligible} says whether a perk's next rank may be dealt as a card (skill gate, path order,
 * one Mastery), and the tree screen shows the same reason. {@link #check} is the old purchase rule, kept for the
 * attribute gate and points it adds.
 */
public final class PerkRules {
	public enum Reason {
		NOT_BOUGHT, MAX_RANK, SKILL_TOO_LOW, ATTRIBUTE_TOO_LOW, NO_POINTS, NEEDS_PATH_PERK, MASTERY_TAKEN, NEEDS_TWO_PATH_PERKS;

		public String translationKey() {
			return "mettle.tree.locked." + name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Everything about the character that matters for one tree.
	 *
	 * @param held   perks held in this skill, with ranks
	 * @param points unspent points in this skill
	 */
	public record TreeView(int skillLevel, int attribute, int points, Map<ResourceKey<Perk>, Integer> held, Map<ResourceKey<Perk>, Perk> perks) {
		int heldOnPath(String path, int tier) {
			int n = 0;
			for (Map.Entry<ResourceKey<Perk>, Integer> e : this.held.entrySet()) {
				Perk p = this.perks.get(e.getKey());
				if (p != null && p.kind() == Perk.Kind.PATH && p.path().equals(path) && (tier == 0 || p.tier() == tier)) {
					n++;
				}
			}
			return n;
		}

		boolean hasMastery() {
			return this.held.keySet().stream().map(this.perks::get).anyMatch(p -> p != null && p.kind() == Perk.Kind.MASTERY);
		}
	}

	private PerkRules() {
	}

	/**
	 * Empty when the next rank of {@code perk} may be dealt in a draft; otherwise the first thing in the way. Attribute gates
	 * are soft in the draft (they change a card's odds, docs/DRAFT.md §3) and nothing costs points, so neither is checked.
	 */
	public static Optional<Reason> eligible(ResourceKey<Perk> key, Perk perk, TreeView tree) {
		return rules(key, perk, tree, false);
	}

	/** The old purchase rule: {@link #eligible} plus the hard attribute gate and the points. */
	public static Optional<Reason> check(ResourceKey<Perk> key, Perk perk, TreeView tree) {
		return rules(key, perk, tree, true);
	}

	private static Optional<Reason> rules(ResourceKey<Perk> key, Perk perk, TreeView tree, boolean purchase) {
		if (!perk.kind().bought()) {
			return Optional.of(Reason.NOT_BOUGHT);
		}
		if (tree.held().getOrDefault(key, 0) >= perk.ranks()) {
			return Optional.of(Reason.MAX_RANK);
		}
		if (tree.skillLevel() < Curves.tierSkillGate(perk.tier())) {
			return Optional.of(Reason.SKILL_TOO_LOW);
		}
		if (purchase && tree.attribute() < Curves.tierAttributeGate(perk.tier())) {
			return Optional.of(Reason.ATTRIBUTE_TOO_LOW);
		}
		if (perk.kind() == Perk.Kind.PATH && perk.tier() >= 3 && tree.heldOnPath(perk.path(), 2) == 0) {
			return Optional.of(Reason.NEEDS_PATH_PERK);
		}
		if (perk.kind() == Perk.Kind.MASTERY) {
			if (tree.hasMastery()) {
				return Optional.of(Reason.MASTERY_TAKEN);
			}
			int count = "any".equals(perk.side()) ? tree.heldOnPath("a", 0) + tree.heldOnPath("b", 0) : tree.heldOnPath(perk.side(), 0);
			if (count < 2) {
				return Optional.of(Reason.NEEDS_TWO_PATH_PERKS);
			}
		}
		if (purchase && tree.points() < perk.cost()) {
			return Optional.of(Reason.NO_POINTS);
		}
		return Optional.empty();
	}
}
