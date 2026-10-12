package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.CardView;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Mettle's card records as the api's {@link CardView} (docs/CARDS.md §25). Pure: the caller supplies what the record
 * alone doesn't know (the printed id, dormancy, the game time), so it is checked without a world.
 */
public final class CardViews {
	/** One set whose tier moved: 0 (not active), 2, 4 or 6. */
	public record TierChange(Identifier keyword, int from, int to) {
	}

	private CardViews() {
	}

	/** A card from the ledger or a player's cache. */
	public static CardView of(CardData c, String code, boolean dormant, long gameTime) {
		List<CardView.TraitValue> traits = c.traits().stream().map(t -> new CardView.TraitValue(t.trait(), t.value())).toList();
		boolean slotted = c.slotted();
		return new CardView(c.id(), code, c.skill(), c.perk(), rarity(c.rarity()), c.rank(), false, c.keywords(), traits, c.needs(), c.mythicId(),
			Optional.of(c.owner()), c.drawn().name(), c.history().owners(), c.scars(), place(c.place().kind()),
			slotted ? c.place().deck() : Optional.empty(), slotted ? c.place().index() : -1, slotted && dormant, !c.attuned(gameTime));
	}

	/**
	 * An offer dealt in a hand: id 0, no code or owner. {@code skillOf} gives a perk's own skill (a wildcard's differs from
	 * the hand's); a Mythic offer, which names no perk, takes the hand's skill.
	 */
	public static CardView offer(DraftState.Card o, ResourceKey<Skill> hand, Function<ResourceKey<Perk>, Optional<ResourceKey<Skill>>> skillOf,
		String drawnBy) {
		List<CardView.TraitValue> traits = o.traits().stream().map(t -> new CardView.TraitValue(t.trait(), t.value())).toList();
		ResourceKey<Skill> skill = o.rarity() == DraftState.Rarity.MYTHIC ? hand : skillOf.apply(o.perk()).orElse(hand);
		Optional<Identifier> mythic = o.rarity() == DraftState.Rarity.MYTHIC ? Optional.of(MythicRules.id(o.perk())) : Optional.empty();
		return new CardView(0L, "", skill, o.perk(), rarity(o.rarity()), o.rank(), o.wildcard(), o.keywords(), traits, o.needs(), mythic,
			Optional.empty(), drawnBy, 0, 0, CardView.Place.HAND, Optional.empty(), -1, false, false);
	}

	public static CardView.Rarity rarity(DraftState.Rarity r) {
		return switch (r) {
			case COMMON -> CardView.Rarity.COMMON;
			case RARE -> CardView.Rarity.RARE;
			case EPIC -> CardView.Rarity.EPIC;
			case CURSED -> CardView.Rarity.CURSED;
			case MYTHIC -> CardView.Rarity.MYTHIC;
		};
	}

	public static CardView.Place place(CardData.PlaceKind k) {
		return switch (k) {
			case BINDER -> CardView.Place.BINDER;
			case SLOTTED -> CardView.Place.SLOTTED;
			case TRADE -> CardView.Place.TRADE;
			case LISTED -> CardView.Place.LISTED;
			case RELEASED -> CardView.Place.RELEASED;
		};
	}

	/** Every set whose tier differs between two derivations (keyword order, so events fire in a stable order). */
	public static List<TierChange> tierChanges(Map<Identifier, Integer> before, Map<Identifier, Integer> after) {
		TreeSet<Identifier> keys = new TreeSet<>(before.keySet());
		keys.addAll(after.keySet());
		List<TierChange> out = new ArrayList<>();
		for (Identifier k : keys) {
			int from = before.getOrDefault(k, 0);
			int to = after.getOrDefault(k, 0);
			if (from != to) {
				out.add(new TierChange(k, from, to));
			}
		}
		return out;
	}

	/** Whether a newly dealt hand differs from the one before (a draw), so {@code CARD_DRAWN} fires once per real deal. */
	public static boolean drawn(List<DraftState.Card> before, List<DraftState.Card> after) {
		return !after.isEmpty() && !after.equals(before);
	}
}
