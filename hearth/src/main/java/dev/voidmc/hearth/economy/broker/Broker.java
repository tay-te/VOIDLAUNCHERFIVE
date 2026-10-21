package dev.voidmc.hearth.economy.broker;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.VirtualGoods;
import dev.voidmc.hearth.economy.bank.DepositStore;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The card broker (docs/requests/mettle-cards.md §7): a merchant in a town or city's market keeps a board where players
 * list goods that live outside inventories ({@link VirtualGoods}: VOID Mettle's cards) at a price they name.
 * <ul>
 *   <li>listing costs a fee ({@link #fee}: 2 %, at least a bit) paid to the town, and the goods go into the owning
 *       mod's escrow ({@link VirtualGoods#hold});</li>
 *   <li>a sale pays the town's tax ({@link #tax}: 5 %) and the rest into the seller's bank deposits, whether they are
 *       about or not; the goods go to the buyer ({@link VirtualGoods#transfer});</li>
 *   <li>a listing not sold in {@link #TERM_DAYS} sim days lapses and goes back ({@link VirtualGoods#release}); the seller
 *       may withdraw it sooner (the fee is not returned).</li>
 * </ul>
 * NPCs never list goods of their own. Server thread only.
 */
public final class Broker {
	public static final HearthDataKey<BrokerBoard> KEY = HearthDataKey.register(Hearth.id("economy/broker"), HearthDataKey.Scope.SETTLEMENT,
		BrokerBoard.CODEC, BrokerBoard::new);
	/** Sim days a listing stays on the board. */
	public static final int TERM_DAYS = 7;
	/** Listings one seller may have on one board. */
	public static final int PER_SELLER = 8;
	/** Listings one board holds. */
	public static final int PER_BOARD = 96;
	static final double FEE_SHARE = 0.02;
	static final double TAX_SHARE = 0.05;
	/** The most a seller may ask (a guard against typos and overflow): 100 000 crowns. */
	public static final long MAX_PRICE = 10_000_000L;

	public enum Result {
		LISTED, SOLD, WITHDRAWN, NOT_LISTABLE, FULL, CANNOT_PAY_FEE, REFUSED, GONE, OWN, CANNOT_AFFORD, NO_ECONOMY, BAD_PRICE;

		public String translationKey() {
			return "hearth.economy.broker.result." + name().toLowerCase(Locale.ROOT);
		}

		public boolean ok() {
			return this == LISTED || this == SOLD || this == WITHDRAWN;
		}
	}

	private Broker() {
	}

	public static void init() {
		// class load registers the key
	}

	/** Whether this settlement has a broker at all: towns and cities. */
	public static boolean keeps(Settlement s) {
		return s.tier().ordinal() >= SettlementTier.TOWN.ordinal();
	}

	public static BrokerBoard board(Settlement s) {
		return s.data(KEY);
	}

	/** The listing fee, paid to the town when listing: 2 % of the price, at least one bit. */
	public static long fee(long price) {
		return Math.max(1, Math.round(Math.max(0, price) * FEE_SHARE));
	}

	/** The town's tax on a sale: 5 % of the price. */
	public static long tax(long price) {
		return Math.max(0, Math.round(Math.max(0, price) * TAX_SHARE));
	}

	/** Lists goods: the fee taken, the goods held in escrow, the listing on the board until {@link #TERM_DAYS} from today. */
	public static Result list(ServerPlayer seller, Settlement s, String kind, long goodsId, long price, long today) {
		EconomyService economy = HearthServices.economy();
		VirtualGoods goods = HearthServices.virtualGoods(kind);
		if (economy == null) {
			return Result.NO_ECONOMY;
		}
		if (price < 1 || price > MAX_PRICE) {
			return Result.BAD_PRICE;
		}
		BrokerBoard board = board(s);
		if (goods == null || board.listed(kind, goodsId) || !goods.listable(seller).contains(goodsId)) {
			return Result.NOT_LISTABLE;
		}
		if (board.size() >= PER_BOARD || board.bySeller(seller.getUUID()).size() >= PER_SELLER) {
			return Result.FULL;
		}
		long fee = fee(price);
		if (!economy.charge(seller, fee)) {
			return Result.CANNOT_PAY_FEE;
		}
		long id = board.nextId(s.id());
		boolean held;
		try {
			held = goods.hold(goodsId, seller, id);
		} catch (RuntimeException e) {
			held = false;
		}
		if (!held) {
			economy.pay(seller, fee);
			return Result.REFUSED;
		}
		s.deposit(fee);
		board.add(new BrokerBoard.Listing(id, kind, goodsId, seller.getUUID(), seller.getName().getString(), price, today, today + TERM_DAYS));
		s.markDirty();
		return Result.LISTED;
	}

	/** The seller takes a listing back (the fee is not returned). */
	public static Result withdraw(ServerPlayer seller, Settlement s, long listing) {
		BrokerBoard board = board(s);
		BrokerBoard.Listing l = board.get(listing);
		if (l == null || !l.seller().equals(seller.getUUID())) {
			return Result.GONE;
		}
		VirtualGoods goods = HearthServices.virtualGoods(l.kind());
		if (goods == null) {
			return Result.GONE; // the owning mod is not loaded: the listing waits for it
		}
		board.remove(listing);
		goods.release(l.goods());
		s.markDirty();
		return Result.WITHDRAWN;
	}

	/** Buys a listing: the price from the buyer, the goods to them, the tax to the town and the rest to the seller's bank. */
	public static Result buy(ServerPlayer buyer, Settlement s, long listing, long today) {
		EconomyService economy = HearthServices.economy();
		if (economy == null) {
			return Result.NO_ECONOMY;
		}
		BrokerBoard board = board(s);
		BrokerBoard.Listing l = board.get(listing);
		VirtualGoods goods = l == null ? null : HearthServices.virtualGoods(l.kind());
		if (l == null || goods == null || l.expires() <= today) {
			return Result.GONE;
		}
		if (l.seller().equals(buyer.getUUID())) {
			return Result.OWN;
		}
		if (!economy.charge(buyer, l.price())) {
			return Result.CANNOT_AFFORD;
		}
		boolean moved;
		try {
			moved = goods.transfer(l.goods(), buyer.getUUID());
		} catch (RuntimeException e) {
			moved = false;
		}
		board.remove(listing);
		s.markDirty();
		if (!moved) {
			// the owning mod could not hand it over: the sale is off, the coin back, the goods to the seller
			economy.pay(buyer, l.price());
			goods.release(l.goods());
			return Result.GONE;
		}
		long tax = tax(l.price());
		s.deposit(tax);
		paySeller(buyer.level().getServer(), l, l.price() - tax);
		return Result.SOLD;
	}

	/** Takes lapsed listings off the board and sends their goods back (from the economy's day pass and every look at the board). */
	public static int expire(Settlement s, long today) {
		BrokerBoard board = board(s);
		int n = 0;
		for (BrokerBoard.Listing l : board.all()) {
			if (l.expires() > today) {
				continue;
			}
			VirtualGoods goods = HearthServices.virtualGoods(l.kind());
			if (goods == null) {
				continue; // kept until the owning mod is back
			}
			board.remove(l.id());
			goods.release(l.goods());
			n++;
		}
		if (n > 0) {
			s.markDirty();
		}
		return n;
	}

	/** The seller's share into their bank deposits; told if they are about. */
	private static void paySeller(@Nullable MinecraftServer server, BrokerBoard.Listing l, long bits) {
		if (server == null || bits <= 0) {
			return;
		}
		DepositStore.add(server, l.seller(), bits);
		ServerPlayer seller = server.getPlayerList().getPlayer(l.seller());
		if (seller != null) {
			seller.sendSystemMessage(Component.translatable("hearth.economy.broker.sold_note", name(l), CoinFormat.format(bits)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The listing's goods by name, or its kind while the owning mod is away. */
	public static Component name(BrokerBoard.Listing l) {
		VirtualGoods goods = HearthServices.virtualGoods(l.kind());
		return goods == null ? Component.literal(l.kind()) : goods.name(l.goods());
	}

	/** Listings matching a search by name, cheapest first. */
	public static List<BrokerBoard.Listing> search(Settlement s, String query, long today) {
		return board(s).search(query, l -> name(l).getString(), today);
	}
}
