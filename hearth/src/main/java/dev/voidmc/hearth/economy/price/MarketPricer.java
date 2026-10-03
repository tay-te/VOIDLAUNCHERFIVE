package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Stockpile;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.market.MarketLedger;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One settlement's prices right now: {@link PriceModel} fed with its stockpile, its use of each goods, its culture, its
 * price wander and what its builders need. Cheap to make; make one per market view or price query.
 */
public final class MarketPricer {
	/** Lots are sized so one is worth at least this many bits: rounding to whole bits then costs at most a quarter. */
	public static final double MIN_LOT_VALUE = 4.0;

	private final Settlement settlement;
	private final MarketLedger ledger;
	private final EconomyConfig.Pricing config;
	private final PriceModel model;

	public MarketPricer(Settlement settlement) {
		this.settlement = settlement;
		this.ledger = settlement.data(EconomyModule.LEDGER);
		this.config = EconomyModule.config().pricing();
		this.model = PriceModel.of(this.config);
	}

	public Settlement settlement() {
		return this.settlement;
	}

	public PriceModel model() {
		return this.model;
	}

	public MarketLedger ledger() {
		return this.ledger;
	}

	/** The stock the town is content with. */
	public double target(Goods goods) {
		Stockpile s = this.settlement.stockpile();
		return PriceModel.target(s.capacity(goods), this.ledger.use[goods.ordinal()], this.config.targetFraction(), this.config.reserveDays());
	}

	public double scarcity(Goods goods) {
		return this.model.scarcity(this.settlement.stockpile().get(goods), target(goods));
	}

	/** Units above what the town keeps, which its market may sell. */
	public int surplus(Goods goods) {
		return (int) Math.max(0, this.settlement.stockpile().get(goods) - target(goods) * this.config.surplusAbove());
	}

	/** Units the town will still buy: up to twice its target, and only what fits in its stores. */
	public int room(Goods goods) {
		Stockpile s = this.settlement.stockpile();
		int stock = s.get(goods);
		return (int) Math.max(0, Math.min(s.capacity(goods) - stock, target(goods) * 2 - stock));
	}

	public boolean needed(Goods goods) {
		return this.ledger.need(goods) > 0;
	}

	/** Mid price of one of this item here (no spread, no reputation). */
	public double mid(ItemStack one) {
		double base = PriceTable.base(one);
		Goods g = PriceTable.goods(one.getItem());
		if (g == null) {
			return base;
		}
		double drift = Math.exp(this.ledger.drift(g));
		return this.model.mid(base, scarcity(g), this.model.culture(PriceTable.speciality(this.settlement.culture(), g)), drift);
	}

	/** Everything a market row needs about one item. */
	public Quote quote(ItemStack one, @Nullable UUID player) {
		int rep = player == null ? 0 : this.settlement.reputation(player);
		Goods g = PriceTable.goods(one.getItem());
		double mid = mid(one);
		double buy = this.model.buy(mid, rep);
		double sell = this.model.sell(mid, rep, g != null && needed(g));
		int lot = PriceModel.lotSize(mid, one.getMaxStackSize(), MIN_LOT_VALUE);
		return new Quote(g, PriceTable.units(one.getItem()), mid, buy, sell, lot, PriceModel.lotPrice(buy, lot, true), PriceModel.lotPrice(sell, lot, false));
	}

	/** The price of the whole stack ({@code stack.getCount()} items), as the market would charge or pay it. */
	public long price(ItemStack stack, @Nullable UUID player, boolean playerBuys) {
		if (stack.isEmpty()) {
			return 0;
		}
		Quote q = quote(stack.copyWithCount(1), player);
		return PriceModel.lotPrice(playerBuys ? q.buyUnit : q.sellUnit, stack.getCount(), playerBuys);
	}

	/**
	 * @param goods    what kind of goods (null: no market here deals in it)
	 * @param units    stockpile units one item is worth
	 * @param mid      mid price of one item
	 * @param buyUnit  what the player pays for one
	 * @param sellUnit what the town pays for one
	 * @param lot      items per lot in the market
	 * @param lotBuy   what the player pays for a lot (whole bits)
	 * @param lotSell  what the town pays for a lot (whole bits)
	 */
	public record Quote(@Nullable Goods goods, int units, double mid, double buyUnit, double sellUnit, int lot, long lotBuy, long lotSell) {
	}
}
