package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A player's cards (docs/CARDS.md §2.1): only a cache of the owner's view of the world ledger, rebuilt from it on join, so
 * a rolled-back or copied player file can neither lose nor duplicate a card. Synced to the owner, who draws the binder
 * and the decks from it. The derived fields ({@code active} to {@code tiers}) are worked out by the server whenever the
 * cards, skill levels or attributes change, so both sides read them without doing the sums again.
 *
 * @param version      0 until the character went through the v2 migration (§12), then 1
 * @param cards        every card the player owns, wherever it is (binder, a deck, a trade)
 * @param freeSwaps    unslots without draught, fee or scar, one per character level, held up to 3 (§7.4)
 * @param freeRebuilds skills whose first rebuild after the migration is still free (§7.4)
 * @param pins         keywords the player pinned as their three sets (empty: the three with most cards)
 * @param pinDay       the in-game day the pins last changed (they change once a day)
 * @param setOrder     keywords in the order they were first counted (ties among sets go to the one reached first)
 * @param active       drafted perks active from slotted, non-dormant cards, with their rank
 * @param dormant      ids of slotted cards that do nothing now (a Mastery without its side, a slot lost with Wits)
 * @param traits       each active trait's total (boosted by attributes, capped), in its unit
 * @param sets         each keyword's count over slotted, non-dormant cards
 * @param tiers        the active sets (at most three) and the tier each reached (2, 4 or 6)
 */
public record CardsState(
	int version,
	List<CardData> cards,
	int freeSwaps,
	List<ResourceKey<Skill>> freeRebuilds,
	List<Identifier> pins,
	long pinDay,
	List<Identifier> setOrder,
	Map<ResourceKey<Perk>, Integer> active,
	List<Long> dormant,
	Map<Identifier, Double> traits,
	Map<Identifier, Integer> sets,
	Map<Identifier, Integer> tiers
) {
	public static final CardsState EMPTY = new CardsState(0, List.of(), 0, List.of(), List.of(), -1, List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of());

	public static final Codec<CardsState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("version", 0).forGetter(CardsState::version),
		CardData.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(CardsState::cards),
		Codec.INT.optionalFieldOf("free_swaps", 0).forGetter(CardsState::freeSwaps),
		ResourceKey.codec(MettleRegistries.SKILL).listOf().optionalFieldOf("free_rebuilds", List.of()).forGetter(CardsState::freeRebuilds),
		Identifier.CODEC.listOf().optionalFieldOf("pins", List.of()).forGetter(CardsState::pins),
		Codec.LONG.optionalFieldOf("pin_day", -1L).forGetter(CardsState::pinDay),
		Identifier.CODEC.listOf().optionalFieldOf("set_order", List.of()).forGetter(CardsState::setOrder),
		Codec.unboundedMap(ResourceKey.codec(MettleRegistries.PERK), Codec.INT).optionalFieldOf("active", Map.of()).forGetter(CardsState::active),
		Codec.LONG.listOf().optionalFieldOf("dormant", List.of()).forGetter(CardsState::dormant),
		Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("traits", Map.of()).forGetter(CardsState::traits),
		Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("sets", Map.of()).forGetter(CardsState::sets),
		Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("tiers", Map.of()).forGetter(CardsState::tiers)
	).apply(i, CardsState::new));

	public CardsState {
		cards = List.copyOf(cards);
		freeRebuilds = List.copyOf(freeRebuilds);
		pins = List.copyOf(pins);
		setOrder = List.copyOf(setOrder);
		active = Map.copyOf(active);
		dormant = List.copyOf(dormant);
		traits = Map.copyOf(traits);
		sets = Map.copyOf(sets);
		tiers = Map.copyOf(tiers);
	}

	public Optional<CardData> card(long id) {
		for (CardData c : this.cards) {
			if (c.id() == id) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	/** A skill's slotted cards, by slot index. */
	public List<CardData> deck(ResourceKey<Skill> skill) {
		List<CardData> out = new ArrayList<>();
		for (CardData c : this.cards) {
			if (c.slotted() && c.place().deck().map(skill::equals).orElse(false)) {
				out.add(c);
			}
		}
		out.sort(Comparator.comparingInt(c -> c.place().index()));
		return out;
	}

	public List<CardData> slotted() {
		return this.cards.stream().filter(CardData::slotted).toList();
	}

	public boolean isDormant(long id) {
		return this.dormant.contains(id);
	}

	/** The rank of a perk active through the deck (0 when no slotted, non-dormant card gives it). */
	public int activeRank(ResourceKey<Perk> perk) {
		return this.active.getOrDefault(perk, 0);
	}

	/** A trait's active total (0 when none), in its unit. */
	public double trait(Identifier trait) {
		return this.traits.getOrDefault(trait, 0.0);
	}

	/** The tier an active set reached (0 when the keyword is not one of the active sets). */
	public int tier(Identifier keyword) {
		return this.tiers.getOrDefault(keyword, 0);
	}

	public CardsState withCards(List<CardData> c) {
		return new CardsState(this.version, c, this.freeSwaps, this.freeRebuilds, this.pins, this.pinDay, this.setOrder, this.active, this.dormant,
			this.traits, this.sets, this.tiers);
	}

	public CardsState withVersion(int v) {
		return new CardsState(v, this.cards, this.freeSwaps, this.freeRebuilds, this.pins, this.pinDay, this.setOrder, this.active, this.dormant,
			this.traits, this.sets, this.tiers);
	}

	public CardsState withFreeSwaps(int n) {
		return new CardsState(this.version, this.cards, Math.max(0, n), this.freeRebuilds, this.pins, this.pinDay, this.setOrder, this.active,
			this.dormant, this.traits, this.sets, this.tiers);
	}

	public CardsState withFreeRebuilds(List<ResourceKey<Skill>> skills) {
		return new CardsState(this.version, this.cards, this.freeSwaps, skills, this.pins, this.pinDay, this.setOrder, this.active, this.dormant,
			this.traits, this.sets, this.tiers);
	}

	public CardsState withPins(List<Identifier> p, long day) {
		return new CardsState(this.version, this.cards, this.freeSwaps, this.freeRebuilds, p, day, this.setOrder, this.active, this.dormant,
			this.traits, this.sets, this.tiers);
	}

	/** The derived fields, as {@code CardRules.derive} worked them out. */
	public CardsState withDerived(List<Identifier> order, Map<ResourceKey<Perk>, Integer> a, List<Long> d, Map<Identifier, Double> t,
		Map<Identifier, Integer> s, Map<Identifier, Integer> ti) {
		return new CardsState(this.version, this.cards, this.freeSwaps, this.freeRebuilds, this.pins, this.pinDay, order, a, d, t, s, ti);
	}
}
