package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.haggle.Haggle;
import dev.voidmc.hearth.economy.haggle.Temper;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceModel;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A market as one player sees it, and the trades. Server only.
 *
 * <p>The counter is either a shop ({@link Shop}: its own shelves and till, restocked from the town's stores; it sells
 * only what it holds and cannot buy what it cannot pay for, as KCD's merchants do) or the town's own market (its stores
 * and treasury). It sells its goods as the culture's own items (WOOD as the local planks, FOOD as its bread...) and buys
 * what the player carries of the goods it deals in, paying more the shorter the town is. Whatever the counter, goods the
 * builders are waiting on are bought by the town itself, at a premium, straight into the stores.
 *
 * <p>Stolen goods ({@link StolenGoods}): an honest merchant will not buy goods stolen in their own town; a fence buys
 * them anywhere at the fence's rate. A merchant the player soured by haggling is dearer and will not haggle for a day.
 *
 * <p>Haggling ({@link Haggle}): right-clicking a row opens a haggle over it (one lot, or a bulk deal); the deal, when
 * struck, trades at the agreed price.
 */
public final class Market {
	/** Rows the market buys are numbered from here, so a clicked id says which list it came from. */
	public static final int BUY_ID_BASE = 10_000;

	private final Settlement settlement;
	private final @Nullable Shop shop;
	private final List<Row> sells = new ArrayList<>();
	private final List<Row> buys = new ArrayList<>();
	private Component status = Component.empty();
	private @Nullable Session session;

	/** The town's own market. */
	public Market(Settlement settlement) {
		this(settlement, null);
	}

	/** A shop's counter (null: the town's own market). */
	public Market(Settlement settlement, @Nullable Shop shop) {
		this.settlement = settlement;
		this.shop = shop;
	}

	public Settlement settlement() {
		return this.settlement;
	}

	public @Nullable Shop shop() {
		return this.shop;
	}

	private TradeCounter counter(MarketPricer pricer) {
		return this.shop == null ? new TradeCounter.TownCounter(this.settlement, pricer) : new TradeCounter.ShopCounter(this.shop);
	}

	private @Nullable Resident keeper() {
		return this.shop == null || this.shop.keeper == null ? null : this.settlement.resident(this.shop.keeper);
	}

	private long day(ServerPlayer player) {
		return EconomyModule.day(player.level());
	}

	private boolean sour(ServerPlayer player, TradeCounter counter) {
		return Shops.book(this.settlement).sour(counter.key(), player.getUUID(), day(player));
	}

	/** Builds the view for this player (and remembers the rows so a click can be matched to them). */
	public MarketView view(ServerPlayer player) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		TradeCounter counter = counter(pricer);
		EconomyConfig.Pricing cfg = EconomyModule.config().pricing();
		int rep = Reputation.standing(this.settlement, player.getUUID());
		boolean refused = rep < cfg.minTradeReputation();
		boolean sour = sour(player, counter);
		Set<String> traits = Temper.traits(keeper());
		long balance = PlayerPurse.balance(player);
		long cash = counter.cash();
		this.sells.clear();
		this.buys.clear();

		// What it sells: the culture's wares for each goods it holds.
		for (Goods g : Goods.VALUES) {
			if (!counter.deals(g)) {
				continue;
			}
			int stock = counter.stock(g);
			if (stock <= 0) {
				continue;
			}
			int shown = 0;
			for (Item item : PriceTable.wares(this.settlement.culture(), g)) {
				if (shown >= cfg.waresPerGoods()) {
					break;
				}
				ItemStack one = new ItemStack(item);
				Price p = price(pricer, one, player, true, false, sour, traits, StolenGoods.Verdict.CLEAN);
				int available = stock / Math.max(1, p.units * p.lot);
				if (available <= 0) {
					continue;
				}
				int flags = (balance < p.lotPrice ? MarketView.CANNOT_AFFORD : 0) | (refused ? MarketView.REFUSED : 0) | (sour ? MarketView.SOUR : 0);
				this.sells.add(new Row(this.sells.size(), one.copyWithCount(p.lot), g, p, refused ? 0 : available, 0, flags, false));
				shown++;
			}
		}

		// What it buys: everything the player carries that it deals in, then what the builders want that the player lacks.
		List<ItemStack> kinds = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || EconomyItems.coinOf(s) != null || EconomyItems.isPouch(s) || s.is(EconomyItems.DEED)
				|| PriceTable.goods(s.getItem()) == null) {
				continue;
			}
			int k = indexOf(kinds, s);
			if (k < 0) {
				kinds.add(s.copyWithCount(1));
				counts.add(s.getCount());
			} else {
				counts.set(k, counts.get(k) + s.getCount());
			}
		}
		MarketLedger ledger = pricer.ledger();
		TradeCounter town = new TradeCounter.TownCounter(this.settlement, pricer);
		for (int k = 0; k < kinds.size(); k++) {
			ItemStack one = kinds.get(k);
			Goods g = PriceTable.goods(one.getItem());
			StolenGoods.Verdict verdict = StolenGoods.judge(one, this.settlement.id(), counter.fence());
			boolean townBuys = verdict != StolenGoods.Verdict.FENCED && pricer.needed(g) && (this.shop == null || this.shop.general() || this.shop.deals(g));
			if (!townBuys && !counter.deals(g)) {
				continue;
			}
			Price p = price(pricer, one, player, false, townBuys, sour, traits, verdict);
			if (p.lotPrice <= 0) {
				continue;
			}
			this.buys.add(buyRow(townBuys ? town : counter, g, one, p, counts.get(k), refused, sour, verdict, townBuys));
		}
		for (Goods g : Goods.VALUES) {
			if (!pricer.needed(g) || this.buys.stream().anyMatch(r -> r.goods == g) || this.shop != null && !this.shop.general() && !this.shop.deals(g)) {
				continue;
			}
			List<Item> wares = PriceTable.wares(this.settlement.culture(), g);
			if (!wares.isEmpty()) {
				ItemStack one = new ItemStack(wares.getFirst());
				Price p = price(pricer, one, player, false, true, sour, traits, StolenGoods.Verdict.CLEAN);
				this.buys.add(buyRow(town, g, one, p, 0, refused, sour, StolenGoods.Verdict.CLEAN, true));
			}
		}
		this.buys.sort(Comparator.comparing((Row r) -> !r.has(MarketView.NEEDED)).thenComparing(r -> r.owned == 0)
			.thenComparing(r -> -pricer.scarcity(r.goods)));
		for (int i = 0; i < this.buys.size(); i++) {
			this.buys.set(i, this.buys.get(i).withId(BUY_ID_BASE + i));
		}
		if (this.session != null) {
			// row ids move as stock changes; keep the haggle on the same goods and item
			Row again = findSame(this.session.playerBuys ? this.sells : this.buys, this.session.stack);
			if (again == null) {
				this.session = null;
			} else {
				this.session.rowId = again.id;
			}
		}

		return new MarketView(this.settlement.name(), this.settlement.culture().getSerializedName(), rep, balance, cash,
			note(pricer, counter, rep, refused, sour), this.status, this.sells.stream().map(Row::offer).toList(), this.buys.stream().map(Row::offer).toList(),
			this.shop != null, this.session == null ? null : this.session.view());
	}

	/** One item's price at this counter for this player: the town's price, moved by sourness, greed and the fence's rate. */
	private Price price(MarketPricer pricer, ItemStack one, ServerPlayer player, boolean playerBuys, boolean townBuys, boolean sour, Set<String> traits,
		StolenGoods.Verdict verdict) {
		MarketPricer.Quote q = pricer.quote(one, player.getUUID(), townBuys);
		EconomyConfig cfg = EconomyModule.config();
		double factor = 1;
		if (sour) {
			factor *= playerBuys ? 1 + cfg.haggle().sourMarkup() : 1 - cfg.haggle().sourMarkup();
		}
		if (!townBuys && traits.contains("greedy")) {
			factor *= playerBuys ? 1.05 : 0.95;
		}
		if (verdict == StolenGoods.Verdict.FENCED) {
			factor *= cfg.fence().rate();
		}
		double unit = (playerBuys ? q.buyUnit() : q.sellUnit()) * factor;
		return new Price(q.units(), q.lot(), unit, PriceModel.lotPrice(unit, q.lot(), playerBuys));
	}

	private Row buyRow(TradeCounter counter, Goods g, ItemStack one, Price p, int owned, boolean refused, boolean sour, StolenGoods.Verdict verdict,
		boolean townBuys) {
		int unitsPerLot = p.units * p.lot;
		int roomLots = counter.room(g) / Math.max(1, unitsPerLot);
		long payLots = p.lotPrice <= 0 ? 0 : counter.cash() / p.lotPrice;
		boolean stolen = verdict == StolenGoods.Verdict.REFUSED;
		int flags = (townBuys ? MarketView.NEEDED : 0) | (roomLots <= 0 ? MarketView.NO_ROOM : 0) | (payLots <= 0 ? MarketView.TOWN_CANNOT_PAY : 0)
			| (refused ? MarketView.REFUSED : 0) | (stolen ? MarketView.STOLEN : 0) | (verdict == StolenGoods.Verdict.FENCED ? MarketView.FENCED : 0)
			| (sour ? MarketView.SOUR : 0);
		int available = refused || stolen ? 0 : (int) Math.min(Math.min(owned / p.lot, roomLots), payLots);
		return new Row(0, one.copyWithCount(p.lot), g, p, Math.max(0, available), owned, flags, townBuys);
	}

	/**
	 * The player clicked a row: buy or sell one lot, or with {@code bulk} as many as make a stack (buying) or everything
	 * they carry of it (selling). Returns false if nothing happened. The status line says what happened either way.
	 */
	public boolean trade(ServerPlayer player, int id, boolean bulk) {
		Row row = row(id);
		if (row == null) {
			return false;
		}
		if (refusedNow(player)) {
			return false;
		}
		int lots = lots(player, row, bulk);
		return id >= BUY_ID_BASE ? sell(player, row, lots, -1) : buy(player, row, lots, -1);
	}

	private boolean refusedNow(ServerPlayer player) {
		if (Reputation.standing(this.settlement, player.getUUID()) < EconomyModule.config().pricing().minTradeReputation()) {
			this.status = Component.translatable("hearth.economy.market.refused").withStyle(ChatFormatting.DARK_RED);
			return true;
		}
		return false;
	}

	private @Nullable Row row(int id) {
		return id >= BUY_ID_BASE ? find(this.buys, id) : find(this.sells, id);
	}

	/** Lots in a click: one, or with bulk a stack's worth (buying) or all the player carries (selling). */
	private int lots(ServerPlayer player, Row row, boolean bulk) {
		if (!bulk) {
			return 1;
		}
		if (row.id >= BUY_ID_BASE) {
			return Math.max(1, count(player, row.stack.copyWithCount(1)) / row.price.lot);
		}
		return Math.max(1, row.stack.getMaxStackSize() / row.price.lot);
	}

	/**
	 * The player buys {@code lots} lots of a row, at {@code agreed} bits for the lot (−1: the posted price).
	 */
	private boolean buy(ServerPlayer player, Row row, int lots, long agreed) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		TradeCounter counter = counter(pricer);
		ItemStack one = row.stack.copyWithCount(1);
		Goods g = row.goods;
		int unitsPerLot = row.price.units * row.price.lot;
		int available = counter.stock(g) / Math.max(1, unitsPerLot);
		long balance = PlayerPurse.balance(player);
		if (available <= 0) {
			this.status = Component.translatable("hearth.economy.market.sold_out").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (agreed < 0) {
			lots = (int) Math.min(Math.min(lots, available), row.price.lotPrice <= 0 ? 0 : balance / row.price.lotPrice);
			if (lots <= 0) {
				this.status = Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(row.price.lotPrice)).withStyle(ChatFormatting.DARK_RED);
				return false;
			}
		} else if (lots > available) {
			this.status = Component.translatable("hearth.economy.market.sold_out").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		long total = agreed >= 0 ? agreed : row.price.lotPrice * lots;
		if (!PlayerPurse.charge(player, total)) {
			this.status = Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(total)).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		counter.sold(g, unitsPerLot * lots, total);
		int count = row.price.lot * lots;
		give(player, one, count);
		Standing.earn(player, this.settlement, pricer.ledger(), total);
		this.settlement.markDirty();
		this.status = Component.translatable("hearth.economy.market.bought", count, one.getHoverName(), CoinFormat.format(total))
			.withStyle(ChatFormatting.DARK_GREEN);
		return true;
	}

	/** The player sells {@code lots} lots of a row, for {@code agreed} bits in all (−1: the posted price). */
	private boolean sell(ServerPlayer player, Row row, int lots, long agreed) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		TradeCounter counter = row.town ? new TradeCounter.TownCounter(this.settlement, pricer) : counter(pricer);
		ItemStack one = row.stack.copyWithCount(1);
		Goods g = row.goods;
		if (row.has(MarketView.STOLEN)) {
			this.status = Component.translatable("hearth.economy.market.stolen").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		int owned = count(player, one);
		int unitsPerLot = row.price.units * row.price.lot;
		int roomLots = counter.room(g) / Math.max(1, unitsPerLot);
		if (owned < row.price.lot) {
			this.status = Component.translatable("hearth.economy.market.need_lot", row.price.lot, one.getHoverName()).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (roomLots <= 0) {
			this.status = Component.translatable("hearth.economy.market.plenty", goodsWord(g)).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (agreed < 0) {
			long payLots = row.price.lotPrice <= 0 ? 0 : counter.cash() / row.price.lotPrice;
			lots = (int) Math.min(Math.min(lots, owned / row.price.lot), Math.min(roomLots, payLots));
		} else if (lots > owned / row.price.lot || lots > roomLots) {
			this.status = Component.translatable("hearth.economy.market.need_lot", row.price.lot * lots, one.getHoverName()).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		long total = agreed >= 0 ? agreed : row.price.lotPrice * lots;
		if (lots <= 0 || !counter.bought(g, unitsPerLot * lots, total)) {
			this.status = Component.translatable(this.shop == null || row.town ? "hearth.economy.market.town_cannot_pay" : "hearth.economy.market.shop_cannot_pay")
				.withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		int count = row.price.lot * lots;
		take(player, one, count);
		MarketLedger ledger = pricer.ledger();
		int units = unitsPerLot * lots;
		boolean helpedBuild = row.town && ledger.need[g.ordinal()] > 0;
		if (helpedBuild) {
			ledger.need[g.ordinal()] = Math.max(0, ledger.need[g.ordinal()] - units);
		}
		PlayerPurse.pay(player, total);
		if (!row.has(MarketView.FENCED)) {
			Standing.earn(player, this.settlement, ledger, total);
		}
		this.settlement.markDirty();
		Component sold = Component.translatable("hearth.economy.market.sold", count, one.getHoverName(), CoinFormat.format(total));
		this.status = (helpedBuild
			? Component.translatable("hearth.economy.market.sold_builders", sold, PropertyNames.lower(ledger.needFor[g.ordinal()]))
			: sold.copy()).withStyle(ChatFormatting.DARK_GREEN);
		return true;
	}

	// ---------------------------------------------------------------- haggling

	/** Opens a haggle over a row (one lot, or with {@code bulk} the same deal a shift-click would make). */
	public void haggleStart(ServerPlayer player, int id, boolean bulk) {
		Row row = row(id);
		if (row == null || refusedNow(player)) {
			return;
		}
		MarketPricer pricer = new MarketPricer(this.settlement);
		TradeCounter counter = row.town ? new TradeCounter.TownCounter(this.settlement, pricer) : counter(pricer);
		if (row.available <= 0) {
			this.status = Component.translatable("hearth.economy.haggle.nothing").withStyle(ChatFormatting.DARK_RED);
			return;
		}
		if (sour(player, counter)) {
			this.status = Component.translatable("hearth.economy.haggle.sour").withStyle(ChatFormatting.DARK_RED);
			return;
		}
		boolean playerBuys = id < BUY_ID_BASE;
		int lots = Math.min(row.available, lots(player, row, bulk));
		long list = row.price.lotPrice * lots;
		EconomyConfig.Haggle cfg = EconomyModule.config().haggle();
		Resident keeper = row.town ? null : keeper();
		Set<String> traits = Temper.traits(keeper);
		double room = Temper.room(cfg, Reputation.charisma(player), Reputation.standing(this.settlement, player.getUUID()), traits);
		double patience = Temper.patience(cfg, Reputation.opinion(keeper == null ? null : keeper.id, player.getUUID()), traits);
		Haggle haggle = Haggle.start(playerBuys, list, room, patience, counter.cash(), cfg);
		this.session = new Session(row.id, row.stack.copyWithCount(1), row.price.lot * lots, lots, playerBuys, row.town, haggle);
		this.session.line = line(haggle, Haggle.Event.START);
		this.status = Component.empty();
	}

	/** The player names a price for the whole deal under haggle. */
	public void haggleOffer(ServerPlayer player, long bits) {
		if (this.session == null) {
			return;
		}
		Haggle h = this.session.haggle;
		Haggle.Event e = h.offer(bits);
		settle(player, e);
	}

	/** The player takes the merchant's last word. */
	public void haggleAccept(ServerPlayer player) {
		if (this.session == null) {
			return;
		}
		settle(player, this.session.haggle.accept());
	}

	/** The player walks away: no harm done. */
	public void haggleCancel() {
		this.session = null;
	}

	private void settle(ServerPlayer player, Haggle.Event e) {
		Session s = this.session;
		Haggle h = s.haggle;
		s.line = line(h, e);
		if (h.state() == Haggle.State.DEAL) {
			Row row = row(s.rowId);
			this.session = null;
			if (row == null) {
				this.status = Component.translatable("hearth.economy.market.sold_out").withStyle(ChatFormatting.DARK_RED);
				return;
			}
			boolean ok = s.playerBuys ? buy(player, row, s.lots, h.agreed()) : sell(player, row, s.lots, h.agreed());
			if (ok) {
				this.status = Component.translatable("hearth.economy.haggle.struck", this.status).withStyle(ChatFormatting.DARK_GREEN);
			}
		} else if (h.state() == Haggle.State.WALKED) {
			this.session = null;
			EconomyConfig.Haggle cfg = EconomyModule.config().haggle();
			String key = s.town ? ShopBook.TOWN : this.shop == null ? ShopBook.TOWN : this.shop.key;
			Shops.book(this.settlement).setSour(key, player.getUUID(), day(player) + Math.max(0, cfg.sourDays() - 1));
			Resident keeper = s.town ? null : keeper();
			Reputation.deed(this.settlement, player.getUUID(), Reputation.HAGGLE_SOURED, cfg.sourWeight(), keeper == null ? null : keeper.id,
				player.blockPosition(), true);
			this.settlement.markDirty();
			this.status = line(h, e).copy().withStyle(ChatFormatting.DARK_RED);
		}
	}

	/** What the merchant says: by what just happened, whether the player buys, and how worn their patience is. */
	static Component line(Haggle h, Haggle.Event e) {
		String side = h.playerBuys() ? "buy" : "sell";
		String mood = h.patience() > 60 ? "calm" : h.patience() > 30 ? "tried" : "short";
		return switch (e) {
			case START -> Component.translatable("hearth.economy.haggle.start." + side, CoinFormat.format(h.counter()));
			case COUNTER -> Component.translatable("hearth.economy.haggle.counter." + side + "." + mood, CoinFormat.format(h.counter()));
			case OFFENDED -> Component.translatable("hearth.economy.haggle.offended." + mood, CoinFormat.format(h.counter()));
			case FIRM -> Component.translatable("hearth.economy.haggle.firm", CoinFormat.format(h.counter()));
			case DEAL -> Component.translatable("hearth.economy.haggle.deal", CoinFormat.format(h.agreed()));
			case WALKED -> Component.translatable("hearth.economy.haggle.walked");
		};
	}

	// ---------------------------------------------------------------- the merchant's word

	private Component note(MarketPricer pricer, TradeCounter counter, int rep, boolean refused, boolean sour) {
		if (refused) {
			return Component.translatable("hearth.economy.market.note.refused");
		}
		if (sour) {
			return Component.translatable("hearth.economy.market.note.sour");
		}
		if (counter.fence()) {
			return Component.translatable("hearth.economy.market.note.fence");
		}
		MarketLedger ledger = pricer.ledger();
		Goods needed = ledger.mostNeeded();
		if (needed != null && (this.shop == null || this.shop.general() || this.shop.deals(needed))) {
			String building = ledger.needFor[needed.ordinal()];
			return building.isEmpty()
				? Component.translatable("hearth.economy.market.note.builders_any", goodsWord(needed))
				: Component.translatable("hearth.economy.market.note.builders", goodsWord(needed), PropertyNames.lower(building));
		}
		if (counter.cash() < 50) {
			return Component.translatable(this.shop == null ? "hearth.economy.market.note.poor" : "hearth.economy.market.note.till_low");
		}
		Goods plenty = null;
		int most = 0;
		for (Goods g : Goods.VALUES) {
			int s = counter.deals(g) ? counter.stock(g) : 0;
			if (s > most && (this.shop != null || pricer.surplus(g) > 0)) {
				most = s;
				plenty = g;
			}
		}
		if (plenty != null && this.shop == null) {
			return Component.translatable("hearth.economy.market.note.plenty", goodsWord(plenty));
		}
		return Component.translatable(rep >= 50 ? "hearth.economy.market.note.friend" : rep < 0 ? "hearth.economy.market.note.wary" : "hearth.economy.market.note.welcome");
	}

	/** The goods' name as a word in a sentence ("timber", "fine goods"). */
	static Component goodsWord(Goods g) {
		return Component.translatable("hearth.economy.goods." + g.getSerializedName());
	}

	// ---------------------------------------------------------------- inventory

	private static void give(ServerPlayer player, ItemStack one, int count) {
		int max = one.getMaxStackSize();
		while (count > 0) {
			int n = Math.min(count, max);
			ItemStack s = one.copyWithCount(n);
			if (!player.getInventory().add(s) || !s.isEmpty()) {
				player.drop(s, false, Prediction.SERVER_ONLY);
			}
			count -= n;
		}
	}

	private static int count(ServerPlayer player, ItemStack one) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (ItemStack.isSameItemSameComponents(inv.getItem(i), one)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer player, ItemStack one, int count) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE && count > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (ItemStack.isSameItemSameComponents(s, one)) {
				int t = Math.min(count, s.getCount());
				s.shrink(t);
				count -= t;
			}
		}
		inv.setChanged();
	}

	private static int indexOf(List<ItemStack> kinds, ItemStack s) {
		for (int i = 0; i < kinds.size(); i++) {
			if (ItemStack.isSameItemSameComponents(kinds.get(i), s)) {
				return i;
			}
		}
		return -1;
	}

	private static @Nullable Row find(List<Row> rows, int id) {
		for (Row r : rows) {
			if (r.id == id) {
				return r;
			}
		}
		return null;
	}

	private static @Nullable Row findSame(List<Row> rows, ItemStack one) {
		for (Row r : rows) {
			if (ItemStack.isSameItemSameComponents(r.stack, one)) {
				return r;
			}
		}
		return null;
	}

	/** One item's price here: stockpile units per item, items per lot, the unit price, and a lot's price in whole bits. */
	private record Price(int units, int lot, double unit, long lotPrice) {
	}

	private record Row(int id, ItemStack stack, Goods goods, Price price, int available, int owned, int flags, boolean town) {
		boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		Row withId(int newId) {
			return new Row(newId, this.stack, this.goods, this.price, this.available, this.owned, this.flags, this.town);
		}

		MarketView.Offer offer() {
			return new MarketView.Offer(this.id, this.stack, this.price.lotPrice, this.available, this.owned, this.flags, this.goods.getSerializedName());
		}
	}

	/** A haggle under way: over which row (and item), how many lots, and the haggle itself. */
	private static final class Session {
		int rowId;
		final ItemStack stack;
		final int count;
		final int lots;
		final boolean playerBuys;
		final boolean town;
		final Haggle haggle;
		Component line = Component.empty();

		Session(int rowId, ItemStack stack, int count, int lots, boolean playerBuys, boolean town, Haggle haggle) {
			this.rowId = rowId;
			this.stack = stack;
			this.count = count;
			this.lots = lots;
			this.playerBuys = playerBuys;
			this.town = town;
			this.haggle = haggle;
		}

		MarketView.HaggleView view() {
			return new MarketView.HaggleView(this.rowId, this.stack.copyWithCount(Math.max(1, Math.min(this.count, 99))), this.playerBuys,
				this.haggle.list(), this.haggle.counter(), this.haggle.lastOffer(), this.haggle.patience(), this.line);
		}
	}
}
