package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Deed Mythics, one of a kind (docs/BUILD_FEEL.md §12), free of players and worlds so they can be tested: what a deed
 * done means for whoever does it, and the one-time migration of the numbered copies the old rules made.
 */
public final class DeedRules {
	private DeedRules() {
	}

	/** What doing a deed brings. */
	public enum Outcome {
		/** The first in the world: the card (and the title). */
		CARD,
		/** Someone holds it already: shards and the title, never a card. */
		LATER,
		/** This player did it before: nothing again. */
		DONE
	}

	/**
	 * @param claimed  whether the deed's Mythic has a holder in the Hall of Firsts
	 * @param achieved whether this player is already on the deed's list
	 */
	public static Outcome outcome(boolean claimed, boolean achieved) {
		if (achieved) {
			return Outcome.DONE;
		}
		return claimed ? Outcome.LATER : Outcome.CARD;
	}

	/** The migration's plan: the copy each deed keeps, and the copies to return (in the order they are returned). */
	public record Plan(Map<String, CardData> keep, List<CardData> returned) {
		public boolean empty() {
			return this.returned.isEmpty();
		}
	}

	/**
	 * Which copy of each deed Mythic stays: the earliest, i.e. the lowest copy number (its earner's place in the deed's
	 * list), ties by the day it was drawn, then by card id. Every other live copy is returned. Released cards are not
	 * copies. Running it on its own result changes nothing (one copy each stays one copy).
	 *
	 * @param cards every card in the ledger
	 * @param deeds the deed Mythics' ids (paths: {@code long_walker})
	 * @param order each deed's list of who did it, in order (the old copy numbers)
	 */
	public static Plan migrate(Collection<CardData> cards, Set<String> deeds, Function<String, List<UUID>> order) {
		Map<String, List<CardData>> copies = new LinkedHashMap<>();
		for (CardData c : cards) {
			if (c.place().kind() == CardData.PlaceKind.RELEASED || c.mythicId().isEmpty()) {
				continue;
			}
			String id = c.mythicId().get().getPath();
			if (deeds.contains(id)) {
				copies.computeIfAbsent(id, k -> new ArrayList<>()).add(c);
			}
		}
		Map<String, CardData> keep = new LinkedHashMap<>();
		List<CardData> returned = new ArrayList<>();
		copies.keySet().stream().sorted().forEach(id -> {
			List<UUID> who = order.apply(id);
			List<CardData> list = new ArrayList<>(copies.get(id));
			list.sort(Comparator.comparingInt((CardData c) -> number(who, c)).thenComparingLong(c -> c.drawn().day()).thenComparingLong(CardData::id));
			keep.put(id, list.getFirst());
			returned.addAll(list.subList(1, list.size()));
		});
		return new Plan(keep, returned);
	}

	/** A copy's number: its earner's place in the deed's list (1 = first), or past the end when the list lost them. */
	static int number(List<UUID> who, CardData c) {
		int at = who.indexOf(c.drawn().player());
		return at < 0 ? Integer.MAX_VALUE : at + 1;
	}

	/** The Hall's holder for a kept copy: who earned it, by name, on the day they did. */
	public static CardData.Holder holder(CardData kept) {
		return new CardData.Holder(kept.drawn().player(), kept.drawn().name(), kept.drawn().day());
	}
}
