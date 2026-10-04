package dev.voidmc.hearth.economy.market;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who the player deals with across the counter: a shop with its own shelves and till ({@link ShopCounter}), or the
 * town's own market, its stores and treasury ({@link TownCounter}). Either way the counter sells only what it holds and
 * buys only what it has room and coin for.
 */
public interface TradeCounter {
	/** {@link ShopBook#TOWN} or the shop's key (for sourness and the like). */
	String key();

	boolean deals(Goods goods);

	/** Units of a goods it can sell. */
	int stock(Goods goods);

	/** Units of a goods it will buy. */
	int room(Goods goods);

	/** Coin it can pay out. */
	long cash();

	/** The player bought {@code units} for {@code bits}. */
	void sold(Goods goods, int units, long bits);

	/** The player sold {@code units} for {@code bits}: pays out and shelves them; false (nothing changes) if it cannot pay. */
	boolean bought(Goods goods, int units, long bits);

	boolean fence();

	/** The resident behind the counter, or null for the town's market. */
	@Nullable UUID keeper();

	/** The town's market: sells its surplus from the stores, buys into them, pays and is paid by the treasury. */
	record TownCounter(Settlement settlement, MarketPricer pricer) implements TradeCounter {
		@Override
		public String key() {
			return ShopBook.TOWN;
		}

		@Override
		public boolean deals(Goods goods) {
			return true;
		}

		@Override
		public int stock(Goods goods) {
			return this.pricer.surplus(goods);
		}

		@Override
		public int room(Goods goods) {
			return this.pricer.room(goods);
		}

		@Override
		public long cash() {
			return this.settlement.treasury();
		}

		@Override
		public void sold(Goods goods, int units, long bits) {
			int taken = this.settlement.stockpile().take(goods, units);
			this.settlement.deposit(bits);
			this.pricer.ledger().traded[goods.ordinal()] -= taken;
		}

		@Override
		public boolean bought(Goods goods, int units, long bits) {
			if (!this.settlement.withdraw(bits)) {
				return false;
			}
			int added = this.settlement.stockpile().add(goods, units);
			this.pricer.ledger().traded[goods.ordinal()] += added;
			return true;
		}

		@Override
		public boolean fence() {
			return false;
		}

		@Override
		public @Nullable UUID keeper() {
			return null;
		}
	}

	/** A shop: its own shelves and till. */
	record ShopCounter(Shop shop) implements TradeCounter {
		@Override
		public String key() {
			return this.shop.key;
		}

		@Override
		public boolean deals(Goods goods) {
			return this.shop.deals(goods) || this.shop.fence;
		}

		@Override
		public int stock(Goods goods) {
			return this.shop.stock(goods);
		}

		@Override
		public int room(Goods goods) {
			return deals(goods) ? Math.max(0, EconomyModule.config().shops().maxStock() - this.shop.stock(goods)) : 0;
		}

		@Override
		public long cash() {
			return this.shop.till;
		}

		@Override
		public void sold(Goods goods, int units, long bits) {
			this.shop.sold(goods, units, bits);
		}

		@Override
		public boolean bought(Goods goods, int units, long bits) {
			if (bits > this.shop.till) {
				return false;
			}
			this.shop.till -= bits;
			this.shop.stock[goods.ordinal()] += units;
			return true;
		}

		@Override
		public boolean fence() {
			return this.shop.fence;
		}

		@Override
		public @Nullable UUID keeper() {
			return this.shop.keeper;
		}
	}
}
