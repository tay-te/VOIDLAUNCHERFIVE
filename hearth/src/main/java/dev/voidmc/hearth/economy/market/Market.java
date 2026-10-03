package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.item.EconomyItems;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.property.PropertyNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A settlement's market as one player sees it, and the trades. Server only.
 *
 * <p>The market sells what the settlement has in surplus, as its culture's own items (WOOD as the local planks and logs,
 * FOOD as its bread and dishes...), and buys what the player carries of the goods it has room and coin for, paying more
 * the shorter it is — most of all for what its builders are waiting on. Every trade moves goods into or out of the
 * stockpile and coin into or out of the treasury; a town that cannot pay does not buy.
 */
public final class Market {
	/** Rows the market buys are numbered from here, so a clicked id says which list it came from. */
	public static final int BUY_ID_BASE = 10_000;

	private final Settlement settlement;
	private final List<Row> sells = new ArrayList<>();
	private final List<Row> buys = new ArrayList<>();
	private Component status = Component.empty();

	public Market(Settlement settlement) {
		this.settlement = settlement;
	}

	public Settlement settlement() {
		return this.settlement;
	}

	/** Builds the view for this player (and remembers the rows so a click can be matched to them). */
	public MarketView view(ServerPlayer player) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		EconomyConfig.Pricing cfg = EconomyModule.config().pricing();
		int rep = this.settlement.reputation(player.getUUID());
		boolean refused = rep < cfg.minTradeReputation();
		long balance = PlayerPurse.balance(player);
		long treasury = this.settlement.treasury();
		this.sells.clear();
		this.buys.clear();

		// What the market sells: the culture's wares for each goods in surplus.
		for (Goods g : Goods.VALUES) {
			int surplus = pricer.surplus(g);
			if (surplus <= 0) {
				continue;
			}
			int shown = 0;
			for (Item item : PriceTable.wares(this.settlement.culture(), g)) {
				if (shown >= cfg.waresPerGoods()) {
					break;
				}
				ItemStack one = new ItemStack(item);
				MarketPricer.Quote q = pricer.quote(one, player.getUUID());
				int unitsPerLot = q.units() * q.lot();
				int available = surplus / Math.max(1, unitsPerLot);
				if (available <= 0) {
					continue;
				}
				int flags = (balance < q.lotBuy() ? MarketView.CANNOT_AFFORD : 0) | (refused ? MarketView.REFUSED : 0);
				this.sells.add(new Row(this.sells.size(), one.copyWithCount(q.lot()), g, q, refused ? 0 : available, 0, flags));
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
		for (int k = 0; k < kinds.size(); k++) {
			ItemStack one = kinds.get(k);
			MarketPricer.Quote q = pricer.quote(one, player.getUUID());
			if (q.lotSell() <= 0 || q.goods() == null) {
				continue;
			}
			this.buys.add(buyRow(pricer, one, q, counts.get(k), treasury, refused));
		}
		for (Goods g : Goods.VALUES) {
			if (!pricer.needed(g) || this.buys.stream().anyMatch(r -> r.goods == g)) {
				continue;
			}
			List<Item> wares = PriceTable.wares(this.settlement.culture(), g);
			if (!wares.isEmpty()) {
				ItemStack one = new ItemStack(wares.getFirst());
				this.buys.add(buyRow(pricer, one, pricer.quote(one, player.getUUID()), 0, treasury, refused));
			}
		}
		this.buys.sort(Comparator.comparing((Row r) -> !r.has(MarketView.NEEDED)).thenComparing(r -> r.owned == 0)
			.thenComparing(r -> -pricer.scarcity(r.goods)));
		for (int i = 0; i < this.buys.size(); i++) {
			this.buys.set(i, this.buys.get(i).withId(BUY_ID_BASE + i));
		}

		return new MarketView(this.settlement.name(), this.settlement.culture().getSerializedName(), rep, balance, treasury,
			note(pricer, rep, refused), this.status, this.sells.stream().map(Row::offer).toList(), this.buys.stream().map(Row::offer).toList());
	}

	private Row buyRow(MarketPricer pricer, ItemStack one, MarketPricer.Quote q, int owned, long treasury, boolean refused) {
		Goods g = q.goods();
		int unitsPerLot = q.units() * q.lot();
		int roomLots = pricer.room(g) / Math.max(1, unitsPerLot);
		long payLots = q.lotSell() <= 0 ? 0 : treasury / q.lotSell();
		int flags = (pricer.needed(g) ? MarketView.NEEDED : 0) | (roomLots <= 0 ? MarketView.NO_ROOM : 0)
			| (payLots <= 0 ? MarketView.TOWN_CANNOT_PAY : 0) | (refused ? MarketView.REFUSED : 0);
		int available = refused ? 0 : (int) Math.min(Math.min(owned / q.lot(), roomLots), payLots);
		return new Row(0, one.copyWithCount(q.lot()), g, q, Math.max(0, available), owned, flags);
	}

	/**
	 * The player clicked a row: buy or sell one lot, or with {@code bulk} as many as make a stack (buying) or everything
	 * they carry of it (selling). Returns false if nothing happened. The status line says what happened either way.
	 */
	public boolean trade(ServerPlayer player, int id, boolean bulk) {
		Row row = id >= BUY_ID_BASE ? find(this.buys, id) : find(this.sells, id);
		if (row == null) {
			return false;
		}
		EconomyConfig.Pricing cfg = EconomyModule.config().pricing();
		if (this.settlement.reputation(player.getUUID()) < cfg.minTradeReputation()) {
			this.status = Component.translatable("hearth.economy.market.refused").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		return id >= BUY_ID_BASE ? sellToTown(player, row, bulk) : buyFromTown(player, row, bulk);
	}

	private boolean buyFromTown(ServerPlayer player, Row row, boolean bulk) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		ItemStack one = row.stack.copyWithCount(1);
		MarketPricer.Quote q = pricer.quote(one, player.getUUID());
		Goods g = row.goods;
		int unitsPerLot = q.units() * q.lot();
		int available = pricer.surplus(g) / Math.max(1, unitsPerLot);
		int wanted = bulk ? Math.max(1, one.getMaxStackSize() / q.lot()) : 1;
		long balance = PlayerPurse.balance(player);
		int lots = (int) Math.min(Math.min(wanted, available), q.lotBuy() <= 0 ? 0 : balance / q.lotBuy());
		if (available <= 0) {
			this.status = Component.translatable("hearth.economy.market.sold_out").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (lots <= 0) {
			this.status = Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(q.lotBuy())).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		long total = q.lotBuy() * lots;
		if (!PlayerPurse.charge(player, total)) {
			this.status = Component.translatable("hearth.economy.market.cannot_afford", CoinFormat.format(total)).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		int units = unitsPerLot * lots;
		this.settlement.stockpile().take(g, units);
		this.settlement.deposit(total);
		MarketLedger ledger = pricer.ledger();
		ledger.traded[g.ordinal()] -= units;
		int count = q.lot() * lots;
		give(player, one, count);
		Standing.earn(player, this.settlement, ledger, total);
		this.settlement.markDirty();
		this.status = Component.translatable("hearth.economy.market.bought", count, one.getHoverName(), CoinFormat.format(total))
			.withStyle(ChatFormatting.DARK_GREEN);
		return true;
	}

	private boolean sellToTown(ServerPlayer player, Row row, boolean bulk) {
		MarketPricer pricer = new MarketPricer(this.settlement);
		ItemStack one = row.stack.copyWithCount(1);
		MarketPricer.Quote q = pricer.quote(one, player.getUUID());
		Goods g = q.goods();
		if (g == null || q.lotSell() <= 0) {
			return false;
		}
		int owned = count(player, one);
		int unitsPerLot = q.units() * q.lot();
		int roomLots = pricer.room(g) / Math.max(1, unitsPerLot);
		long payLots = this.settlement.treasury() / q.lotSell();
		int lots = (int) Math.min(Math.min(bulk ? Integer.MAX_VALUE : 1, owned / q.lot()), Math.min(roomLots, payLots));
		if (owned < q.lot()) {
			this.status = Component.translatable("hearth.economy.market.need_lot", q.lot(), one.getHoverName()).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (roomLots <= 0) {
			this.status = Component.translatable("hearth.economy.market.plenty", goodsWord(g)).withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		if (lots <= 0) {
			this.status = Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		long total = q.lotSell() * lots;
		if (!this.settlement.withdraw(total)) {
			this.status = Component.translatable("hearth.economy.market.town_cannot_pay").withStyle(ChatFormatting.DARK_RED);
			return false;
		}
		int count = q.lot() * lots;
		take(player, one, count);
		int units = unitsPerLot * lots;
		this.settlement.stockpile().add(g, units);
		MarketLedger ledger = pricer.ledger();
		ledger.traded[g.ordinal()] += units;
		boolean helpedBuild = ledger.need[g.ordinal()] > 0;
		if (helpedBuild) {
			ledger.need[g.ordinal()] = Math.max(0, ledger.need[g.ordinal()] - units);
		}
		PlayerPurse.pay(player, total);
		Standing.earn(player, this.settlement, ledger, total);
		this.settlement.markDirty();
		Component sold = Component.translatable("hearth.economy.market.sold", count, one.getHoverName(), CoinFormat.format(total));
		this.status = (helpedBuild
			? Component.translatable("hearth.economy.market.sold_builders", sold, PropertyNames.lower(ledger.needFor[g.ordinal()]))
			: sold.copy()).withStyle(ChatFormatting.DARK_GREEN);
		return true;
	}

	private Component note(MarketPricer pricer, int rep, boolean refused) {
		if (refused) {
			return Component.translatable("hearth.economy.market.note.refused");
		}
		MarketLedger ledger = pricer.ledger();
		Goods needed = ledger.mostNeeded();
		if (needed != null) {
			String building = ledger.needFor[needed.ordinal()];
			return building.isEmpty()
				? Component.translatable("hearth.economy.market.note.builders_any", goodsWord(needed))
				: Component.translatable("hearth.economy.market.note.builders", goodsWord(needed), PropertyNames.lower(building));
		}
		if (this.settlement.treasury() < 50) {
			return Component.translatable("hearth.economy.market.note.poor");
		}
		Goods plenty = null;
		int most = 0;
		for (Goods g : Goods.VALUES) {
			int s = pricer.surplus(g);
			if (s > most) {
				most = s;
				plenty = g;
			}
		}
		if (plenty != null) {
			return Component.translatable("hearth.economy.market.note.plenty", goodsWord(plenty));
		}
		return Component.translatable(rep >= 50 ? "hearth.economy.market.note.friend" : rep < 0 ? "hearth.economy.market.note.wary" : "hearth.economy.market.note.welcome");
	}

	/** The goods' name as a word in a sentence ("timber", "fine goods"). */
	static Component goodsWord(Goods g) {
		return Component.translatable("hearth.economy.goods." + g.getSerializedName());
	}

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

	private static Row find(List<Row> rows, int id) {
		for (Row r : rows) {
			if (r.id == id) {
				return r;
			}
		}
		return null;
	}

	private record Row(int id, ItemStack stack, Goods goods, MarketPricer.Quote quote, int available, int owned, int flags) {
		boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		Row withId(int newId) {
			return new Row(newId, this.stack, this.goods, this.quote, this.available, this.owned, this.flags);
		}

		MarketView.Offer offer() {
			long price = this.id >= BUY_ID_BASE ? this.quote.lotSell() : this.quote.lotBuy();
			return new MarketView.Offer(this.id, this.stack, price, this.available, this.owned, this.flags, this.goods.getSerializedName());
		}
	}
}
