package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.SettlementTier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What has happened in a town lately, as its folk would talk about it: a building finished, building stopped for want of
 * stone, the town grown into a village, a birth, a death. Filled from {@link dev.voidmc.hearth.api.SettlementEvents}
 * (cheaply: the events fire during abstract simulation too) and kept in memory, a handful per town.
 */
public final class TownNews {
	private static final int KEEP = 8;

	public enum Kind {
		FOUNDED, BUILT, STALLED, TIER, ARRIVED, DIED,
		/** A party of settlers left to found a hamlet ({@code subject} is its name). */
		LEFT
	}

	/**
	 * @param subject a building's name, a person's name, the tier reached
	 * @param goods   for STALLED: what is short
	 */
	public record Item(Kind kind, long day, String subject, @Nullable Goods goods, int amount, @Nullable SettlementTier tier) {
	}

	private final Deque<Item> items = new ArrayDeque<>();

	public synchronized void add(Item item) {
		// The same stall reported every step is one piece of news, refreshed.
		if (item.kind == Kind.STALLED) {
			this.items.removeIf(i -> i.kind == Kind.STALLED && i.goods == item.goods);
		}
		// a party of settlers is one piece of news, not one per person
		if (item.kind == Kind.LEFT) {
			this.items.removeIf(i -> i.kind == Kind.LEFT && i.subject.equals(item.subject));
		}
		this.items.addFirst(item);
		while (this.items.size() > KEEP) {
			this.items.removeLast();
		}
	}

	/** News from the last {@code days} days, newest first. */
	public synchronized List<Item> recent(long today, int days) {
		List<Item> out = new ArrayList<>();
		for (Item i : this.items) {
			if (today - i.day <= days) {
				out.add(i);
			}
		}
		return out;
	}

	public synchronized @Nullable Item latest(long today, int days, Kind kind) {
		for (Item i : this.items) {
			if (i.kind == kind && today - i.day <= days) {
				return i;
			}
		}
		return null;
	}
}
