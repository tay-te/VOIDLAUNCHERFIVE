package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.core.CardData;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * Face-to-face trading (docs/CARDS.md §8.1), free of players and worlds so it can be tested: who may trade, which cards
 * may be offered, and whether both sides have room for what they would receive.
 */
public final class TradeRules {
	/** Cards and item stacks each side may offer. */
	public static final int MAX_CARDS = 9;
	public static final int MAX_ITEMS = 9;
	/** Ticks since a player was last hurt before they may start or finish a trade (5 s). */
	public static final int CALM_TICKS = 100;
	/** How long a trade request waits for an answer. */
	public static final int REQUEST_TICKS = 20 * 60;

	private TradeRules() {
	}

	/** Why a trade can't start, finish, or a card can't go in (lang {@code mettle.trade.refused.<name>}). */
	public enum Refusal {
		SELF, TOO_FAR, HURT, DEAD, BUSY, NO_REQUEST, NOT_TRADING, NOT_YOURS, SLOTTED, ATTUNING, IN_TRADE, TOO_MANY_CARDS, TOO_MANY_ITEMS,
		NO_ROOM, BINDER_FULL, CANT_PAY, NOT_LOCKED, NO_COIN, EMPTY_HAND, BOUND, COIN_ROOM;

		public String translationKey() {
			return "mettle.trade.refused." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Both alive, within range, neither hurt in the last 5 s, and not the same player. */
	public static Optional<Refusal> canMeet(UUID a, UUID b, double distance, boolean bothAlive, long now, long lastHurtA, long lastHurtB) {
		if (a.equals(b)) {
			return Optional.of(Refusal.SELF);
		}
		if (!bothAlive) {
			return Optional.of(Refusal.DEAD);
		}
		if (distance > CardConfig.get().trade_range) {
			return Optional.of(Refusal.TOO_FAR);
		}
		if (now - lastHurtA < CALM_TICKS || now - lastHurtB < CALM_TICKS) {
			return Optional.of(Refusal.HURT);
		}
		return Optional.empty();
	}

	/** A card may go in: it is the player's, not a talent (bound), in the binder (slotted cards are rebuilt out first) and attuned. */
	public static Optional<Refusal> canOffer(CardData card, UUID player, long now, int offered) {
		if (!card.owner().equals(player)) {
			return Optional.of(Refusal.NOT_YOURS);
		}
		// Talents are you (docs/BUILD_FEEL.md §13.1): bound, never traded or listed.
		if (card.talent()) {
			return Optional.of(Refusal.BOUND);
		}
		if (card.place().kind() == CardData.PlaceKind.SLOTTED) {
			return Optional.of(Refusal.SLOTTED);
		}
		if (card.place().kind() != CardData.PlaceKind.BINDER) {
			return Optional.of(Refusal.IN_TRADE);
		}
		if (!card.attuned(now)) {
			return Optional.of(Refusal.ATTUNING);
		}
		if (offered >= MAX_CARDS) {
			return Optional.of(Refusal.TOO_MANY_CARDS);
		}
		return Optional.empty();
	}

	/** Whether a binder of {@code owned} cards has room after giving {@code giving} and receiving {@code receiving}. */
	public static boolean binderRoom(int owned, int giving, int receiving) {
		return owned - giving + receiving <= CardConfig.get().binder_max;
	}

	/**
	 * Whether {@code incoming} fits into an inventory of {@code slots} (copies are used; nothing changes): stacks merge
	 * into matching ones first, then into empty slots.
	 */
	public static boolean fits(List<ItemStack> slots, List<ItemStack> incoming) {
		ItemStack[] inv = new ItemStack[slots.size()];
		for (int i = 0; i < inv.length; i++) {
			inv[i] = slots.get(i).copy();
		}
		for (ItemStack in : incoming) {
			int left = in.getCount();
			for (int i = 0; i < inv.length && left > 0; i++) {
				if (!inv[i].isEmpty() && ItemStack.isSameItemSameComponents(inv[i], in)) {
					int room = inv[i].getMaxStackSize() - inv[i].getCount();
					int put = Math.min(room, left);
					inv[i].grow(put);
					left -= put;
				}
			}
			for (int i = 0; i < inv.length && left > 0; i++) {
				if (inv[i].isEmpty()) {
					int put = Math.min(in.getMaxStackSize(), left);
					inv[i] = in.copyWithCount(put);
					left -= put;
				}
			}
			if (left > 0) {
				return false;
			}
		}
		return true;
	}

	/** A traded card: the new owner, the history passed on, attuning for one in-game day (§8.3). */
	public static CardData received(CardData card, UUID to, String name, long day, long now) {
		return card.withOwner(to, card.history().passedTo(new CardData.Holder(to, name, day)), now + CardConfig.get().attune_ticks)
			.withPlace(CardData.Place.BINDER);
	}
}
