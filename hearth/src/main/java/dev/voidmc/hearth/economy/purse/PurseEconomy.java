package dev.voidmc.hearth.economy.purse;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.holdings.Holdings;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money through people (docs/LIVING_WORLD.md §7.7), one sim day at a time: wages flow from the treasury, the shops and
 * the businesses players own into residents' purses; folk pay their poll tax, buy their food from the baker, the inn or
 * the market, spend what they can spare on the comforts their station wants (cloth from the tailor, candles from the
 * chandler, books, fine things) — real demand that takes goods off shop shelves and out of the stores — and give a
 * little in tithes. A household's station then follows its purse ({@link StationRule}).
 *
 * <pre>
 *   treasury ──wages──► purses ──food, comforts──► shop tills ──wholesale, dues──► treasury
 *       ▲                  │                          │
 *       └──── poll tax, tithes, purchases from the town's stores ◄───────┘ keeper's share ──► purses
 * </pre>
 *
 * Food is paid for but not taken: the sim already ate it from the stores. The comforts in
 * {@link EconomyConfig.Households#consumedGoods} are taken. Costs O(residents + shops) per settlement per day, no world
 * access. Coin is conserved everywhere except where it is meant to enter (an adult's starting savings, the first time
 * the economy sees them) or leave (nowhere here).
 */
public final class PurseEconomy {
	private PurseEconomy() {
	}

	/** What the day did, for the books. */
	public record Day(long wages, boolean shortPaid, long taxes, long spent, long tithes, int hungry, long seeded) {
	}

	public static Day daily(Settlement settlement, MarketPricer pricer, long day) {
		EconomyConfig cfg = EconomyModule.config();
		EconomyConfig.Households h = cfg.households();
		long seeded = seed(settlement, h);
		ShopBook book = Shops.ensure(settlement, day);
		Holdings holdings = settlement.data(EconomyModule.HOLDINGS);
		Wages w = wages(settlement, book, holdings, h, cfg.holdings(), day);
		long taxes = pollTax(settlement, h, day);
		List<Households.Household> households = Households.of(settlement);
		Spending s = spend(settlement, pricer, book, households, h, cfg.pricing().spread(), day);
		if (cfg.station().followPurse()) {
			stations(settlement, households, h, cfg.station(), day);
		}
		settlement.markDirty();
		return new Day(w.paid, w.shortPaid, taxes, s.spent, s.tithes, s.hungry, seeded);
	}

	/** An adult the economy has not seen before arrives with some savings (new coin, as a settler's purse is). */
	static long seed(Settlement settlement, EconomyConfig.Households h) {
		long total = 0;
		for (Resident r : settlement.residents()) {
			if (Purses.seeded(r)) {
				continue;
			}
			if (r.age != AgeStage.CHILD) {
				long bits = Math.round(h.startingPurseDays() * h.wage(Station.of(r.job)) * (r.age == AgeStage.ELDER ? 1.5 : 1.0));
				Purses.add(r, bits);
				total += bits;
			}
			Purses.markSeeded(r);
		}
		return total;
	}

	private record Wages(long paid, boolean shortPaid) {
	}

	/**
	 * Each working adult earns the wage of the station their trade brings: from their business's revenue if a player owns
	 * their workplace (the owner keeps the rest), from their shop's till if they work at a shop that can pay, otherwise
	 * from the treasury. The treasury pays at most {@code wageCapShare} of itself in a day; below that, everyone it pays
	 * is short-paid alike.
	 */
	static Wages wages(Settlement settlement, ShopBook book, Holdings holdings, EconomyConfig.Households h, EconomyConfig.Holdings hc, long day) {
		List<Resident> fromTreasury = new ArrayList<>();
		List<Holdings.Holding> business = new ArrayList<>();
		Map<Integer, Shop> byPlot = new HashMap<>();
		Map<UUID, Shop> byKeeper = new HashMap<>();
		for (Shop shop : book.shops.values()) {
			if (shop.kind.startsWith("job/")) {
				if (shop.keeper != null) {
					byKeeper.put(shop.keeper, shop);
				}
			} else {
				byPlot.put(shop.plot, shop);
			}
		}
		double due = 0;
		long paid = 0;
		for (Resident r : settlement.residents()) {
			if (r.age != AgeStage.ADULT || r.job == JobType.NONE) {
				continue;
			}
			double wage = h.wage(Station.of(r.job));
			Holdings.Holding owned = r.work != Resident.NO_PLOT ? holdings.get(r.work) : null;
			if (owned != null) {
				fromTreasury.add(r);
				business.add(owned);
				due += wage * hc.productivity();
				continue;
			}
			Shop shop = r.work != Resident.NO_PLOT ? byPlot.get(r.work) : null;
			if (shop == null) {
				shop = byKeeper.get(r.id);
			}
			long whole = round(wage, settlement.id(), day, r.id.hashCode());
			if (shop != null && shop.till >= whole) {
				shop.till -= whole;
				Purses.add(r, whole);
				paid += whole;
				continue;
			}
			fromTreasury.add(r);
			business.add(null);
			due += wage;
		}
		double factor = due <= 0 ? 1 : Math.min(1, settlement.treasury() * h.wageCapShare() / due);
		for (int k = 0; k < fromTreasury.size(); k++) {
			Resident r = fromTreasury.get(k);
			Holdings.Holding owned = business.get(k);
			double wage = h.wage(Station.of(r.job)) * factor;
			long pay = round(wage, settlement.id(), day, r.id.hashCode());
			if (owned == null) {
				pay = Math.min(pay, settlement.treasury());
				if (pay > 0 && settlement.withdraw(pay)) {
					Purses.add(r, pay);
					paid += pay;
				}
				continue;
			}
			// the town buys the business's work at its worth; the worker is paid, the owner keeps the rest less upkeep
			long revenue = Math.min(settlement.treasury(), round(wage * hc.productivity(), settlement.id(), day, r.id.hashCode() * 31));
			if (revenue <= 0 || !settlement.withdraw(revenue)) {
				continue;
			}
			long toWorker = Math.min(revenue, pay);
			Purses.add(r, toWorker);
			paid += toWorker;
			double margin = revenue - toWorker;
			double upkeep = margin * hc.upkeep();
			owned.today += margin - upkeep;
			settlement.deposit((long) Math.floor(upkeep));
		}
		return new Wages(paid, factor < 0.999);
	}

	/** Every grown resident pays the poll tax if their purse holds it. */
	static long pollTax(Settlement settlement, EconomyConfig.Households h, long day) {
		long total = 0;
		for (Resident r : settlement.residents()) {
			if (r.age == AgeStage.CHILD) {
				continue;
			}
			long tax = round(h.pollTax(), settlement.id(), day, r.id.hashCode() ^ 0x7A11);
			if (tax > 0 && Purses.take(r, tax)) {
				settlement.deposit(tax);
				total += tax;
			}
		}
		return total;
	}

	private record Spending(long spent, long tithes, int hungry) {
	}

	static Spending spend(Settlement settlement, MarketPricer pricer, ShopBook book, List<Households.Household> households, EconomyConfig.Households h,
		double spread, long day) {
		EconomyConfig.Shops sc = EconomyModule.config().shops();
		long spent = 0;
		long tithes = 0;
		int hungry = 0;
		List<Shop> foodVendors = honest(Shops.vendors(book, Goods.FOOD));
		Shop inn = Shops.inn(book);
		double foodUnit = pricer.unitMid(Goods.FOOD) * (1 + spread / 2);
		Market market = new Market(pricer, book, h, spread);
		for (int k = 0; k < households.size(); k++) {
			Households.Household hh = households.get(k);
			int n = hh.members().size();
			long salt = (long) hh.home() * 0x9E3779B1L + k;

			// food: paid for to whoever sells it; the sim has already eaten it from the stores
			long foodCost = round(n * h.foodPerHead() * foodUnit, settlement.id(), day, salt);
			long paid = hh.spend(foodCost);
			int hunger = paid < foodCost ? Purses.hunger(hh.members().getFirst()) + 1 : 0;
			if (hunger > 0) {
				hungry++;
			}
			for (Resident r : hh.members()) {
				Purses.setHunger(r, hunger);
			}
			if (paid > 0) {
				Shop vendor = foodVendors.isEmpty() ? null : foodVendors.get(k % foodVendors.size());
				takeIn(settlement, vendor, paid, sc.wholesale());
				spent += paid;
			}

			// comforts, from what is above a few days' reserve
			long wealth = hh.wealth();
			double reserve = h.reserveDays() * (foodCost + h.pollTax() * hh.adults());
			long comfort = round(h.comfortShare() * Math.max(0, wealth - reserve), settlement.id(), day, salt ^ 0xC0F0);
			if (comfort > 0) {
				long innSpend = inn == null ? 0 : Math.round(comfort * h.innShare());
				if (innSpend > 0) {
					long got = hh.spend(innSpend);
					takeIn(settlement, inn, got, sc.wholesale());
					spent += got;
				}
				spent += comforts(settlement, market, hh, comfort - innSpend, day, salt);
			}

			long tithe = round(hh.wealth() * h.tithe(), settlement.id(), day, salt ^ 0x717E);
			if (tithe > 0) {
				long got = hh.spend(tithe);
				settlement.deposit(got);
				tithes += got;
			}
		}
		return new Spending(spent, tithes, hungry);
	}

	/** Coin a shop takes for something it sold from the town's stores: it keeps its margin, the rest goes to the treasury. */
	private static void takeIn(Settlement settlement, Shop vendor, long bits, double wholesale) {
		if (vendor == null) {
			settlement.deposit(bits);
			return;
		}
		long toTown = (long) Math.floor(bits * wholesale);
		vendor.till += bits - toTown;
		vendor.takings += bits;
		settlement.deposit(toTown);
	}

	/**
	 * The day's market as folk see it, worked out once a day: each goods' price to folk, the shops that sell it, and
	 * each station's basket.
	 */
	private static final class Market {
		final MarketPricer pricer;
		final double[] unit = new double[Goods.VALUES.length];
		final List<List<Shop>> vendors = new ArrayList<>(Goods.VALUES.length);
		final Map<Station, Map<Goods, Double>> baskets = new EnumMap<>(Station.class);
		final EconomyConfig.Households h;
		private final ShopBook book;
		private final double spread;

		Market(MarketPricer pricer, ShopBook book, EconomyConfig.Households h, double spread) {
			this.pricer = pricer;
			this.h = h;
			this.book = book;
			this.spread = spread;
			for (Goods g : Goods.VALUES) {
				this.unit[g.ordinal()] = -1;
				this.vendors.add(null);
			}
		}

		double unit(Goods g) {
			int i = g.ordinal();
			if (this.unit[i] < 0) {
				this.unit[i] = this.pricer.unitMid(g) * (1 + this.spread / 2);
			}
			return this.unit[i];
		}

		List<Shop> vendors(Goods g) {
			int i = g.ordinal();
			if (this.vendors.get(i) == null) {
				this.vendors.set(i, honest(Shops.vendors(this.book, g)));
			}
			return this.vendors.get(i);
		}

		Map<Goods, Double> basket(Station s) {
			return this.baskets.computeIfAbsent(s, st -> PurseEconomy.basket(st, this.h.consumedGoods()));
		}
	}

	/** Spends a household's comfort budget over its station's basket: from a shop's shelves, else the town's surplus. */
	private static long comforts(Settlement settlement, Market market, Households.Household hh, long budget, long day, long salt) {
		if (budget <= 0) {
			return 0;
		}
		MarketPricer pricer = market.pricer;
		Map<Goods, Double> basket = market.basket(hh.station());
		double weights = 0;
		for (double w : basket.values()) {
			weights += w;
		}
		if (weights <= 0) {
			return 0;
		}
		long spent = 0;
		for (Map.Entry<Goods, Double> e : basket.entrySet()) {
			Goods g = e.getKey();
			double unit = market.unit(g);
			if (unit <= 0) {
				continue;
			}
			int units = (int) round(budget * e.getValue() / weights / unit, settlement.id(), day, salt ^ g.ordinal() * 0x51L);
			if (units <= 0) {
				continue;
			}
			Shop vendor = null;
			for (Shop s : market.vendors(g)) {
				if (s.stock(g) > 0) {
					vendor = s;
					break;
				}
			}
			if (vendor != null) {
				units = Math.min(units, vendor.stock(g));
				long cost = hh.spend((long) Math.ceil(units * unit - 1e-9));
				vendor.sold(g, units, cost);
				spent += cost;
			} else {
				units = Math.min(units, pricer.surplus(g));
				if (units <= 0) {
					continue;
				}
				long cost = hh.spend((long) Math.ceil(units * unit - 1e-9));
				settlement.stockpile().take(g, units);
				settlement.deposit(cost);
				spent += cost;
			}
		}
		return spent;
	}

	private static List<Shop> honest(List<Shop> shops) {
		shops.removeIf(s -> s.fence);
		return shops;
	}

	/**
	 * What a household of a station buys when it has coin to spare, weighted, limited to the goods folk use up
	 * ({@code consumed}): the poor buy cloth; peasants cloth, candles and dye; merchants and gentry fine things.
	 */
	public static Map<Goods, Double> basket(Station station, List<String> consumed) {
		Map<Goods, Double> m = new EnumMap<>(Goods.class);
		switch (station) {
			case POOR -> put(m, Goods.CLOTH, 1);
			case PEASANT -> {
				put(m, Goods.CLOTH, 2);
				put(m, Goods.CANDLES, 2);
				put(m, Goods.DYE, 0.5);
			}
			case ARTISAN -> {
				put(m, Goods.CLOTH, 2);
				put(m, Goods.CANDLES, 2);
				put(m, Goods.DYE, 1);
				put(m, Goods.BOOKS, 0.5);
				put(m, Goods.LUXURY, 0.5);
			}
			case MERCHANT -> {
				put(m, Goods.CLOTH, 2);
				put(m, Goods.CANDLES, 2);
				put(m, Goods.LUXURY, 2);
				put(m, Goods.BOOKS, 1);
				put(m, Goods.DYE, 1);
				put(m, Goods.SPECIALTY, 1);
			}
			case CLERGY -> {
				put(m, Goods.CANDLES, 3);
				put(m, Goods.BOOKS, 2);
				put(m, Goods.CLOTH, 1);
				put(m, Goods.WAX, 1);
			}
			case SCHOLAR -> {
				put(m, Goods.BOOKS, 3);
				put(m, Goods.PAPER, 2);
				put(m, Goods.CANDLES, 2);
				put(m, Goods.CLOTH, 1);
			}
			case OFFICIAL -> {
				put(m, Goods.CLOTH, 2);
				put(m, Goods.CANDLES, 2);
				put(m, Goods.LUXURY, 1);
				put(m, Goods.BOOKS, 1);
			}
			case GENTRY, LORD -> {
				put(m, Goods.LUXURY, station == Station.LORD ? 4 : 3);
				put(m, Goods.CLOTH, 2);
				put(m, Goods.CANDLES, station == Station.LORD ? 3 : 2);
				put(m, Goods.BOOKS, 1);
				put(m, Goods.DYE, 1);
				put(m, Goods.SPECIALTY, 2);
			}
		}
		m.keySet().removeIf(g -> !consumed.contains(g.getSerializedName()));
		return m;
	}

	private static void put(Map<Goods, Double> m, Goods g, double w) {
		m.put(g, w);
	}

	/** Moves each household's station after its purse, at most one step, and not again within the hold. */
	static void stations(Settlement settlement, List<Households.Household> households, EconomyConfig.Households h, EconomyConfig.Station cfg,
		long day) {
		boolean gentry = settlement.tier().ordinal() >= SettlementTier.TOWN.ordinal();
		for (Households.Household hh : households) {
			Resident first = hh.members().getFirst();
			long last = Purses.stationDay(first);
			if (last != Long.MIN_VALUE && day - last < cfg.holdDays()) {
				continue;
			}
			Station base = null;
			for (Resident r : hh.members()) {
				if (r.age != AgeStage.CHILD) {
					Station b = Station.of(r.job);
					if (base == null || StationRule.rank(b) > StationRule.rank(base)) {
						base = b;
					}
				}
			}
			if (base == null) {
				continue; // a household of children: they keep their parents' station
			}
			Station current = hh.station();
			Station next = StationRule.next(current, base, hh.wealth() / (double) hh.members().size(), Purses.hunger(first), gentry, h::wage, cfg);
			boolean changed = false;
			for (Resident r : hh.members()) {
				if (r.station != next) {
					r.station = next;
					changed = true;
				}
			}
			if (changed || last == Long.MIN_VALUE) {
				for (Resident r : hh.members()) {
					Purses.setStationDay(r, day);
				}
			}
		}
	}

	/**
	 * Rounds an amount to whole bits, up or down with the chance of its fraction, deterministically per (settlement, day,
	 * salt): small amounts (a 0.4-bit candle budget) are paid on the right share of days, and a caught-up history repeats.
	 */
	public static long round(double amount, long settlement, long day, long salt) {
		if (amount <= 0) {
			return 0;
		}
		double floor = Math.floor(amount);
		double frac = amount - floor;
		long x = settlement * 0x9E3779B97F4A7C15L ^ day * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		x ^= x >>> 33;
		x *= 0xFF51AFD7ED558CCDL;
		x ^= x >>> 33;
		double u = (x >>> 11) * 0x1.0p-53;
		return (long) floor + (u < frac ? 1 : 0);
	}
}
