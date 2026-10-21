package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Cards listed at a VOID Hearth card broker (docs/CARDS.md §8.2; Hearth's request 7). Hearth runs the board, the price,
 * the fee, the tax and paying the seller; Mettle only moves the card, and the ledger stays the single truth:
 * <ul>
 *   <li><b>hold</b>: a binder card of the seller's own, attuned, not a talent, goes into escrow, ledger place
 *   {@code LISTED} with the listing's id (and the day it was listed): it can't be slotted, traded, released or listed
 *   twice while there;</li>
 *   <li><b>transfer</b>: only a card still {@code LISTED} changes hands, once: the buyer gets it attuning for a day with
 *   its history passed on (as a trade), if their binder has room (Mythics always fit);</li>
 *   <li><b>release</b>: only a card still {@code LISTED} goes back to its owner's binder (a withdrawn or lapsed listing;
 *   after a sale it is a no-op, so a stale listing can never take a card back);</li>
 *   <li>every change is committed at once (the ledger, then the players' files, as a trade), so a crash between Hearth's
 *   payment and the transfer leaves the card listed and the sale undone, and a crash after it leaves the card sold: a
 *   second buy of the same listing is refused ({@code transfer} false: Hearth calls the sale off and refunds);</li>
 *   <li>a card left {@code LISTED} longer than {@link #LAPSE_DAYS} (Hearth's term is 7 sim days; a board lost in a crash
 *   never releases it) goes back to its owner at start-up and every in-game day.</li>
 * </ul>
 * The pure rules take a {@link CardLedger} so JUnit can drive them; {@code compat.hearth.CardGoods} is Hearth's side.
 */
public final class Listings {
	/** The goods' kind at Hearth's broker. */
	public static final String KIND = "mettle:card";
	/** Days a card may stay listed before Mettle takes it back on its own (twice Hearth's term). */
	public static final long LAPSE_DAYS = 14;

	private Listings() {
	}

	// ---------------------------------------------------------------- the rules (pure, on a ledger)

	/** Why a card can't be listed (the trade refusals: not yours, bound, slotted, in a trade or listed, attuning). */
	public static Optional<TradeRules.Refusal> canList(CardData card, UUID seller, long now) {
		return TradeRules.canOffer(card, seller, now, 0);
	}

	/** The seller's cards they may list now, by id. */
	public static List<Long> listable(CardLedger ledger, UUID seller, long now) {
		List<Long> out = new ArrayList<>();
		for (CardData c : ledger.owned(seller)) {
			if (canList(c, seller, now).isEmpty()) {
				out.add(c.id());
			}
		}
		return out;
	}

	/** Whether a card is listed now (and under which listing). */
	public static boolean listed(CardData card) {
		return card.place().kind() == CardData.PlaceKind.LISTED;
	}

	/** Takes a card into escrow for listing {@code listing}; false (and nothing changes) if it can't be listed. */
	public static boolean hold(CardLedger ledger, long id, UUID seller, long listing, long day, long now) {
		Optional<CardData> card = ledger.get(id);
		if (card.isEmpty() || canList(card.get(), seller, now).isPresent()) {
			return false;
		}
		ledger.update(id, c -> c.withPlace(new CardData.Place(CardData.PlaceKind.LISTED, Optional.empty(), (int) Math.clamp(day, 0, Integer.MAX_VALUE), listing)));
		return true;
	}

	/** A listing withdrawn or lapsed: the card back to its owner's binder. Returns the owner, or null when it wasn't listed. */
	public static @Nullable UUID release(CardLedger ledger, long id) {
		Optional<CardData> card = ledger.get(id);
		if (card.isEmpty() || !listed(card.get())) {
			return null;
		}
		ledger.update(id, c -> c.withPlace(CardData.Place.BINDER));
		return card.get().owner();
	}

	/**
	 * Sold: the card to {@code buyer}, attuning, its history passed on. False (nothing changes) when it isn't listed (sold
	 * already, withdrawn, lapsed), the buyer is its owner, or the buyer's binder is full.
	 *
	 * @param buyerBinder cards in the buyer's binder now (Mythics not counted)
	 */
	public static boolean transfer(CardLedger ledger, long id, UUID buyer, String buyerName, int buyerBinder, int binderMax, long day, long now) {
		Optional<CardData> card = ledger.get(id);
		if (card.isEmpty() || !listed(card.get()) || card.get().owner().equals(buyer)) {
			return false;
		}
		if (!card.get().mythic() && buyerBinder + 1 > binderMax) {
			return false;
		}
		ledger.update(id, c -> TradeRules.received(c, buyer, buyerName, day, now));
		return true;
	}

	/** Cards listed {@link #LAPSE_DAYS} or more ago go back to their owners. Returns them. */
	public static List<CardData> lapse(CardLedger ledger, long today) {
		List<CardData> back = new ArrayList<>();
		for (CardData c : List.copyOf(ledger.all())) {
			if (listed(c) && today - c.place().index() >= LAPSE_DAYS) {
				release(ledger, c.id());
				back.add(c);
			}
		}
		return back;
	}

	/** Cards in a player's binder that count against its size (Mythics always fit), from the ledger. */
	public static int binderCount(CardLedger ledger, UUID player) {
		return CardRules.binderCount(ledger.owned(player));
	}

	// ---------------------------------------------------------------- the server's side

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static List<Long> listable(ServerPlayer seller) {
		MinecraftServer server = seller.level().getServer();
		return listable(Cards.ledger(server), seller.getUUID(), now(server));
	}

	/** Hearth's hold: escrow, the seller's cache, committed at once. */
	public static boolean hold(ServerPlayer seller, long id, long listing) {
		MinecraftServer server = seller.level().getServer();
		if (!hold(Cards.ledger(server), id, seller.getUUID(), listing, Cards.day(server), now(server))) {
			return false;
		}
		Cards.sync(seller);
		LedgerFile.commit(server, seller);
		Mettle.LOGGER.info("broker: {} listed card {} (listing {})", seller.getName().getString(), id, listing);
		return true;
	}

	/** Hearth's release: back to the owner's binder (online or not), committed at once. */
	public static void release(MinecraftServer server, long id) {
		UUID owner = release(Cards.ledger(server), id);
		if (owner == null) {
			return;
		}
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		if (p != null) {
			Cards.sync(p);
			LedgerFile.commit(server, p);
		} else {
			LedgerFile.commit(server);
		}
		Mettle.LOGGER.info("broker: card {} went back to its owner {}", id, owner);
	}

	/** Hearth's transfer: the card to the buyer (online or not), committed at once. */
	public static boolean transfer(MinecraftServer server, long id, UUID buyer) {
		CardLedger ledger = Cards.ledger(server);
		Optional<CardData> before = ledger.get(id);
		ServerPlayer b = Trades.present(server, buyer);
		String name = b != null ? b.getGameProfile().name() : before.flatMap(c -> knownName(c, buyer))
			.or(() -> server.services().nameToIdCache().get(buyer).map(net.minecraft.server.players.NameAndId::name)).orElse(buyer.toString().substring(0, 8));
		if (before.isEmpty() || !transfer(ledger, id, buyer, name, binderCount(ledger, buyer), CardConfig.get().binder_max, Cards.day(server), now(server))) {
			return false;
		}
		ServerPlayer seller = Trades.present(server, before.get().owner());
		List<ServerPlayer> online = new ArrayList<>();
		for (ServerPlayer p : new ServerPlayer[] {b, seller}) {
			if (p != null) {
				Cards.sync(p);
				online.add(p);
			}
		}
		// The ledger first, then both players' files (the buyer's carries the coin Hearth just took; a stand-in has none).
		LedgerFile.commit(server, online.toArray(ServerPlayer[]::new));
		if (b != null && seller != null) {
			CardApi.traded(seller, b, List.of(id));
		}
		Mettle.LOGGER.info("broker: card {} sold by {} to {}", id, before.get().owner(), name);
		return true;
	}

	private static Optional<String> knownName(CardData card, UUID player) {
		return card.history().last().stream().filter(h -> h.player().equals(player)).map(CardData.Holder::name).findFirst();
	}

	/** Start-up and each in-game day: listings Hearth never closed go back. */
	public static void lapse(MinecraftServer server) {
		List<CardData> back = lapse(Cards.ledger(server), Cards.day(server));
		if (back.isEmpty()) {
			return;
		}
		for (CardData c : back) {
			ServerPlayer p = server.getPlayerList().getPlayer(c.owner());
			if (p != null) {
				Cards.sync(p);
			}
		}
		LedgerFile.commit(server);
		Mettle.LOGGER.info("broker: {} cards listed for {} days or more went back to their owners", back.size(), LAPSE_DAYS);
	}
}
