package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.core.DraftState;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** The Mythic rules free of players and worlds (docs/CARDS.md §9), so they can be tested: card keys, the draw, credit. */
public final class MythicRules {
	private MythicRules() {
	}

	/** A Mythic card's perk key: {@code mettle:mythic/<id>} (not in the perk registry; the rarity says it is a Mythic). */
	public static ResourceKey<Perk> key(Identifier mythic) {
		return ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath(mythic.getNamespace(), "mythic/" + mythic.getPath()));
	}

	/** A Mythic offer for a hand. */
	public static DraftState.Card offer(Identifier id) {
		return new DraftState.Card(key(id), 1, DraftState.Rarity.MYTHIC, false, List.of(), List.of(), 0);
	}

	/** Where a hand holds its Mythic offer, or -1 (a hand holds one at most). */
	public static int offerIndex(List<DraftState.Card> hand) {
		for (int i = 0; i < hand.size(); i++) {
			if (hand.get(i).rarity() == DraftState.Rarity.MYTHIC) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The draft once its Mythic offer is claimed (§3.5): the Mythic leaves the hand, which stays open with its other
	 * cards; the draft is not spent, so the player still keeps one of them. A hand that held nothing else is left empty
	 * and dealt again by the next settle, with the draft still waiting. Unchanged when the hand holds no Mythic.
	 */
	public static DraftState claimed(DraftState d, net.minecraft.resources.ResourceKey<dev.voidmc.mettle.api.Skill> skill) {
		List<DraftState.Card> hand = d.hand(skill);
		int at = offerIndex(hand);
		if (at < 0) {
			return d;
		}
		List<DraftState.Card> rest = new java.util.ArrayList<>(hand);
		rest.remove(at);
		return d.withHand(skill, rest);
	}

	/**
	 * The draft's Mythic roll (§3.5): from skill 15, once per draft (not per reroll), 0.5 %. Decided from the player's
	 * seed and the hand's place, so it never re-rolls.
	 */
	public static boolean rolls(long seed, int dealt, int level) {
		CardConfig c = CardConfig.get();
		if (level < c.mythic_draw_from) {
			return false;
		}
		return new Random(seed * 0x2545F4914F6CDD1DL + dealt * 31L + 0x6D).nextDouble() < c.mythic_draw_chance;
	}

	/** Most damage in the fight wins; a tie goes to the killing blow (§9.2). */
	public static UUID creditOf(Map<UUID, Float> fight, UUID killer) {
		UUID best = null;
		float most = -1;
		for (Map.Entry<UUID, Float> e : fight.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			boolean kill = e.getKey().equals(killer);
			if (e.getValue() > most || e.getValue() == most && kill) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}
}
