package dev.voidmc.hearth.economy.shop;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.price.GoodsMask;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.price.PriceTable;
import dev.voidmc.hearth.economy.purse.Purses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ObjLongConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's shops, kept in step with its buildings and folk, and their day: restocking from the town's stores at
 * wholesale (only what the till can pay for and the town can spare), sending surplus back, paying market dues, and the
 * keeper (or the owner) taking a share of a full till.
 *
 * <p>A shop is any built workplace {@code hearth_prices/shops.json} names (a market stall deals in everything, a bakery in
 * food and flour, a smithy in tools and metal...), kept by whoever works there; and any trader by job (a merchant, a
 * pedlar, a fence) whose workplace is not such a building. A shop nobody works at is shut: it keeps its stock and till
 * but sells nothing until someone does. Server thread only; all of it is arithmetic on settlement data.
 */
public final class Shops {
	private Shops() {
	}

	public static ShopBook book(Settlement settlement) {
		return settlement.data(EconomyModule.SHOPS);
	}

	/** Matches the shops to the settlement's buildings and folk once a day (and on first use). */
	public static ShopBook ensure(Settlement settlement, long day) {
		ShopBook book = book(settlement);
		if (book.lastSync != day) {
			sync(settlement, day);
		}
		return book;
	}

	/**
	 * Opens shops for new shop buildings and traders, closes those whose building is gone or whose trader left (stock back
	 * to the stores, till to the treasury), and re-chooses each keeper among those who work there.
	 */
	public static void sync(Settlement settlement, long day) {
		ShopBook book = book(settlement);
		EconomyConfig.Shops cfg = EconomyModule.config().shops();
		Map<Integer, List<Resident>> workers = new HashMap<>();
		for (Resident r : settlement.residents()) {
			if (r.work != Resident.NO_PLOT) {
				workers.computeIfAbsent(r.work, k -> new ArrayList<>()).add(r);
			}
		}
		Set<String> live = new HashSet<>();
		for (PlotView plot : settlement.plots()) {
			String path = plot.spec().archetype().getPath();
			if (plot.state() != PlotState.BUILT || !PriceTable.isShop(path)) {
				continue;
			}
			String key = "p" + plot.id();
			live.add(key);
			long mask = PriceTable.shopMask(path);
			Shop shop = book.get(key);
			if (shop == null) {
				shop = new Shop(key, plot.id(), path, mask, path.equals("commerce/thieves_den"), day);
				lend(settlement, shop, cfg);
				book.shops.put(key, shop);
			}
			shop.deals = mask;
			Resident keeper = keeper(workers.getOrDefault(plot.id(), List.of()), mask);
			shop.keeper = keeper == null ? null : keeper.id;
			shop.fence = path.equals("commerce/thieves_den") || keeper != null && keeper.job == JobType.FENCE;
		}
		for (Resident r : settlement.residents()) {
			long mask = traderMask(r.job);
			if (mask == GoodsMask.NONE || r.age == AgeStage.CHILD || r.work != Resident.NO_PLOT && live.contains("p" + r.work)) {
				continue;
			}
			String key = "r" + r.id;
			live.add(key);
			Shop shop = book.get(key);
			if (shop == null) {
				shop = new Shop(key, r.work, "job/" + r.job.getSerializedName(), mask, r.job == JobType.FENCE, day);
				lend(settlement, shop, cfg);
				book.shops.put(key, shop);
			}
			shop.deals = mask;
			shop.keeper = r.id;
			shop.fence = r.job == JobType.FENCE;
		}
		for (Iterator<Shop> it = book.shops.values().iterator(); it.hasNext(); ) {
			Shop shop = it.next();
			if (!live.contains(shop.key)) {
				close(settlement, shop);
				it.remove();
			}
		}
		book.lastSync = day;
		settlement.markDirty();
	}

	/** Goods a trader deals in by job alone: merchants, traders and fences (others keep a shop only in a shop building). */
	static long traderMask(JobType job) {
		return job == JobType.MERCHANT || job == JobType.TRADER || job == JobType.FENCE ? PriceTable.jobMask(job) | fallback(job) : GoodsMask.NONE;
	}

	private static long fallback(JobType job) {
		// a datapack that forgets the trading jobs still gets traders who trade
		return PriceTable.jobMask(job) == GoodsMask.NONE ? GoodsMask.ALL : GoodsMask.NONE;
	}

	/** Who keeps a shop: a worker whose trade it sells, else a merchant, else any grown worker there. */
	static @Nullable Resident keeper(List<Resident> workers, long mask) {
		Resident any = null;
		Resident merchant = null;
		for (Resident r : workers) {
			if (r.age == AgeStage.CHILD) {
				continue;
			}
			if ((PriceTable.jobMask(r.job) & mask) != 0 && r.job != JobType.MERCHANT && r.job != JobType.TRADER) {
				return r;
			}
			if (merchant == null && (r.job == JobType.MERCHANT || r.job == JobType.TRADER || r.job == JobType.FENCE)) {
				merchant = r;
			}
			if (any == null) {
				any = r;
			}
		}
		return merchant != null ? merchant : any;
	}

	/** A new shop's till, lent by the treasury (as much as it can spare, up to the configured start). */
	private static void lend(Settlement settlement, Shop shop, EconomyConfig.Shops cfg) {
		long loan = Math.min(cfg.startingTill(), settlement.treasury() / 10);
		if (loan > 0 && settlement.withdraw(loan)) {
			shop.till += loan;
		}
	}

	/** A shop that closes for good: its stock goes back to the town's stores, its till to the treasury. */
	static void close(Settlement settlement, Shop shop) {
		for (Goods g : Goods.VALUES) {
			if (shop.stock[g.ordinal()] > 0) {
				settlement.stockpile().add(g, shop.stock[g.ordinal()]);
				shop.stock[g.ordinal()] = 0;
			}
		}
		if (shop.till > 0) {
			settlement.deposit(shop.till);
			shop.till = 0;
		}
	}

	/** The open shop a resident keeps, or null. */
	public static @Nullable Shop keptBy(Settlement settlement, UUID resident, long day) {
		for (Shop s : ensure(settlement, day).shops.values()) {
			if (resident.equals(s.keeper)) {
				return s;
			}
		}
		return null;
	}

	/** The open shops that deal in a goods, most stocked first. */
	public static List<Shop> vendors(ShopBook book, Goods goods) {
		List<Shop> out = new ArrayList<>();
		for (Shop s : book.shops.values()) {
			if (s.open() && s.deals(goods)) {
				out.add(s);
			}
		}
		out.sort((a, b) -> Integer.compare(b.stock(goods), a.stock(goods)));
		return out;
	}

	/** The open inn, tavern or hostel, if any (where folk spend their evenings). */
	public static @Nullable Shop inn(ShopBook book) {
		for (Shop s : book.shops.values()) {
			if (s.open() && (s.kind.equals("commerce/inn") || s.kind.equals("commerce/tavern") || s.kind.equals("commerce/hostel"))) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Every shop's day.
	 *
	 * @param payout where a shop's profit goes: (shop, bits) — to its owner if a player owns it, else its keeper's purse
	 * @return the market dues paid to the treasury
	 */
	public static long daily(Settlement settlement, MarketPricer pricer, long day, ObjLongConsumer<Shop> payout) {
		EconomyConfig.Shops cfg = EconomyModule.config().shops();
		ShopBook book = ensure(settlement, day);
		MarketLedger ledger = pricer.ledger();
		long dues = 0;
		for (Shop shop : book.shops.values()) {
			for (Goods g : Goods.VALUES) {
				int i = g.ordinal();
				shop.sales[i] = shop.sales[i] * 0.8F + shop.soldToday[i] * 0.2F;
				shop.soldToday[i] = 0;
			}
			if (shop.open()) {
				restock(settlement, pricer, ledger, shop, cfg);
			}
			returnSurplus(settlement, pricer, ledger, shop, cfg);
			long due = Math.min(shop.till, Math.round(shop.takings * cfg.dueRate()));
			if (due > 0) {
				shop.till -= due;
				settlement.deposit(due);
				dues += due;
			}
			shop.takings = 0;
			long above = shop.till - cfg.workingTill();
			if (above > 0) {
				long profit = (long) Math.floor(above * cfg.keeperShare());
				if (profit > 0) {
					shop.till -= profit;
					payout.accept(shop, profit);
				}
			}
			if (shop.open()) {
				topUp(settlement, shop, cfg);
			}
		}
		book.pruneSour(day);
		settlement.markDirty();
		return dues;
	}

	/**
	 * The working capital an open shop keeps to buy from players with: {@code tillFloor}, scaled by the town's size (half
	 * for a hamlet, up to twice for a town) and the shop's trade (a general store or a fence's back room half again more),
	 * never above {@code workingTill}.
	 */
	public static long floor(Settlement settlement, Shop shop, EconomyConfig.Shops cfg) {
		double town = Math.max(0.5, Math.min(2, Math.sqrt(settlement.population() / 16.0)));
		double trade = shop.general() || shop.fence ? 1.5 : 1;
		return Math.min(cfg.workingTill(), Math.round(cfg.tillFloor() * town * trade));
	}

	/**
	 * Brings an open shop's till back up to its floor from the treasury (at most {@code floorDraw} of the treasury per shop
	 * a day). Coin moves, none is made: the treasury lends its shops the coin to buy with, and gets it back as wholesale,
	 * dues and returns.
	 */
	public static void topUp(Settlement settlement, Shop shop, EconomyConfig.Shops cfg) {
		long want = floor(settlement, shop, cfg) - shop.till;
		long draw = Math.min(want, (long) Math.floor(settlement.treasury() * cfg.floorDraw()));
		if (draw > 0 && settlement.withdraw(draw)) {
			shop.till += draw;
		}
	}

	/** Fills the shelves from what the town can spare, paying wholesale from the till. */
	static void restock(Settlement settlement, MarketPricer pricer, MarketLedger ledger, Shop shop, EconomyConfig.Shops cfg) {
		long budget = (long) Math.floor(shop.till * cfg.restockShare()); // the rest stays to buy from players with
		for (Goods g : Goods.VALUES) {
			if (!shop.deals(g)) {
				continue;
			}
			int i = g.ordinal();
			int want = shop.target(g, cfg.stockDays(), cfg.minStock(), cfg.maxStock()) - shop.stock[i];
			if (want <= 0) {
				continue;
			}
			double unit = pricer.unitMid(g) * cfg.wholesale();
			int affordable = unit <= 0 ? want : (int) Math.min(want, Math.floor(budget / unit));
			int units = Math.min(affordable, spare(settlement, pricer, g, cfg));
			if (units <= 0) {
				continue;
			}
			int taken = settlement.stockpile().take(g, units);
			long cost = (long) Math.ceil(taken * unit);
			cost = Math.min(cost, Math.min(budget, shop.till));
			budget -= cost;
			shop.till -= cost;
			settlement.deposit(cost);
			shop.stock[i] += taken;
			ledger.traded[i] -= taken; // the shop's stock is not the town eating its stores
		}
	}

	/**
	 * What the town's stores let its shops take of a goods: what it holds above {@code townFloor} of its target. The
	 * shops' share comes before the market's surplus (a baker has bread to sell before the town has bread to spare), but
	 * never digs into what the builders and the town itself need.
	 */
	static int spare(Settlement settlement, MarketPricer pricer, Goods g, EconomyConfig.Shops cfg) {
		return (int) Math.max(0, settlement.stockpile().get(g) - pricer.target(g) * cfg.townFloor() - pricer.ledger().need(g));
	}

	/** Shelves fuller than the most a shop keeps send the rest back to the stores, if the treasury can pay wholesale. */
	static void returnSurplus(Settlement settlement, MarketPricer pricer, MarketLedger ledger, Shop shop, EconomyConfig.Shops cfg) {
		for (Goods g : Goods.VALUES) {
			int i = g.ordinal();
			int over = shop.stock[i] - (shop.deals(g) ? cfg.maxStock() : 0);
			if (over <= 0) {
				continue;
			}
			double unit = pricer.unitMid(g) * cfg.wholesale();
			int units = Math.min(over, pricer.room(g));
			if (!shop.deals(g)) {
				units = over; // goods it no longer deals in go back whatever the town pays
			}
			if (units <= 0) {
				continue;
			}
			int added = settlement.stockpile().add(g, units);
			long pay = Math.min(settlement.treasury(), (long) Math.floor(added * unit));
			if (pay > 0 && settlement.withdraw(pay)) {
				shop.till += pay;
			}
			shop.stock[i] -= added;
			ledger.traded[i] += added;
		}
	}

	/** Pays a shop's profit to its keeper's purse (or the treasury if nobody keeps it). */
	public static void payKeeper(Settlement settlement, Shop shop, long bits) {
		Resident keeper = shop.keeper == null ? null : settlement.resident(shop.keeper);
		if (keeper != null) {
			Purses.add(keeper, bits);
		} else {
			settlement.deposit(bits);
		}
	}
}
