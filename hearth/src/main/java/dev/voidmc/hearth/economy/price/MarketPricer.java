package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Stockpile;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.Reputation;
import dev.voidmc.hearth.economy.market.MarketLedger;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
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
	/** Goods the settlement's own resource sites yield: cheaper here (docs/LIVING_WORLD.md §7: towns thrive on their land). */
	private final boolean[] local = new boolean[Goods.VALUES.length];

	public MarketPricer(Settlement settlement) {
		this.settlement = settlement;
		this.ledger = settlement.data(EconomyModule.LEDGER);
		this.config = EconomyModule.config().pricing();
		this.model = PriceModel.of(this.config);
		for (ResourceSite site : settlement.sites()) {
			if (site.depleted() || site.yields() == null) {
				continue;
			}
			for (int g = 0; g < Math.min(site.yields().length, this.local.length); g++) {
				if (site.yields()[g] > 0) {
					this.local[g] = true;
				}
			}
		}
	}

	/** Whether the settlement's own land yields this goods. */
	public boolean local(Goods goods) {
		return this.local[goods.ordinal()];
	}

	/** The settlement's price factor for a goods: its culture's speciality, and cheaper if its own land yields it. */
	public double placeFactor(Goods goods) {
		double f = this.model.culture(PriceTable.speciality(this.settlement.culture(), goods));
		return this.local[goods.ordinal()] ? f * (1 - this.config.siteEffect()) : f;
	}

	/** Mid price of one stockpile unit of a goods here: its unit value times scarcity, place and wander. */
	public double unitMid(Goods goods) {
		return PriceTable.data().unitValue(goods) * scarcity(goods) * placeFactor(goods) * Math.exp(this.ledger.drift(goods));
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
		return this.model.mid(base, scarcity(g), placeFactor(g), drift);
	}

	/** Everything a market row needs about one item. */
	public Quote quote(ItemStack one, @Nullable UUID player) {
		return quote(one, player, true);
	}

	/**
	 * As {@link #quote(ItemStack, UUID)}; {@code townBuys} false for a shop buying with its own till, which pays no
	 * premium for what the builders need (only the town does).
	 */
	public Quote quote(ItemStack one, @Nullable UUID player, boolean townBuys) {
		int rep = player == null ? 0 : Reputation.standing(this.settlement, player);
		Goods g = PriceTable.goods(one.getItem());
		double mid = mid(one);
		double buy = this.model.buy(mid, rep);
		double sell = this.model.sell(mid, rep, townBuys && g != null && needed(g));
		int lot = PriceModel.lotSize(mid, one.getMaxStackSize(), MIN_LOT_VALUE);
		return new Quote(g, PriceTable.units(one.getItem()), mid, buy, sell, lot, PriceModel.lotPrice(buy, lot, true), PriceModel.lotPrice(sell, lot, false));
	}

	/**
	 * As {@link #quote(ItemStack, UUID, boolean)} for a player at the counter: then every player price modifier
	 * ({@link PriceModifiers}) moves the prices too, after reputation.
	 */
	public Quote quote(ItemStack one, ServerPlayer player, boolean townBuys) {
		Quote q = quote(one, player.getUUID(), townBuys);
		if (!PriceModifiers.any()) {
			return q;
		}
		return q.adjusted(PriceModifiers.factor(player, this.settlement, one, true), PriceModifiers.factor(player, this.settlement, one, false));
	}

	/** {@link #price(ItemStack, UUID, boolean)} with the player's price modifiers. */
	public long price(ItemStack stack, ServerPlayer player, boolean playerBuys) {
		if (stack.isEmpty()) {
			return 0;
		}
		Quote q = quote(stack.copyWithCount(1), player, true);
		return PriceModel.lotPrice(playerBuys ? q.buyUnit : q.sellUnit, stack.getCount(), playerBuys);
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
		/**
		 * This quote with a player's price factors applied: {@code buyFactor} on what they pay, {@code sellFactor} on what
		 * they are paid, which still stays below 95 % of what they would pay.
		 */
		public Quote adjusted(double buyFactor, double sellFactor) {
			if (buyFactor == 1.0 && sellFactor == 1.0) {
				return this;
			}
			double buy = this.buyUnit * buyFactor;
			double sell = Math.min(this.sellUnit * sellFactor, buy * 0.95);
			return new Quote(this.goods, this.units, this.mid, buy, sell, this.lot, PriceModel.lotPrice(buy, this.lot, true),
				PriceModel.lotPrice(sell, this.lot, false));
		}
	}
}
