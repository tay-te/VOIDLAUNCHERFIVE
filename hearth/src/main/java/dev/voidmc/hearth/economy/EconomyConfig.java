package dev.voidmc.hearth.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/hearth/economy.json}. Every field is optional, so an old file keeps working when fields are added.
 * See docs/ECONOMY.md for what each number does.
 */
public record EconomyConfig(Pricing pricing, Treasury treasury, Bank bank, Property property, Coins coins, Map<String, CoinLoot> loot,
	Households households, Shops shops, Haggle haggle, Holdings holdings, Works works, Fence fence, Map<String, Double> services,
	Station station) {
	public static final EconomyConfig DEFAULTS = new EconomyConfig(Pricing.DEFAULTS, Treasury.DEFAULTS, Bank.DEFAULTS, Property.DEFAULTS,
		Coins.DEFAULTS, CoinLoot.DEFAULTS, Households.DEFAULTS, Shops.DEFAULTS, Haggle.DEFAULTS, Holdings.DEFAULTS, Works.DEFAULTS,
		Fence.DEFAULTS, Services.DEFAULTS, Station.DEFAULTS);

	public static final Codec<EconomyConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Pricing.CODEC.optionalFieldOf("pricing", Pricing.DEFAULTS).forGetter(EconomyConfig::pricing),
		Treasury.CODEC.optionalFieldOf("treasury", Treasury.DEFAULTS).forGetter(EconomyConfig::treasury),
		Bank.CODEC.optionalFieldOf("bank", Bank.DEFAULTS).forGetter(EconomyConfig::bank),
		Property.CODEC.optionalFieldOf("property", Property.DEFAULTS).forGetter(EconomyConfig::property),
		Coins.CODEC.optionalFieldOf("coins", Coins.DEFAULTS).forGetter(EconomyConfig::coins),
		Codec.unboundedMap(Codec.STRING, CoinLoot.CODEC).optionalFieldOf("loot", CoinLoot.DEFAULTS).forGetter(EconomyConfig::loot),
		Households.CODEC.optionalFieldOf("households", Households.DEFAULTS).forGetter(EconomyConfig::households),
		Shops.CODEC.optionalFieldOf("shops", Shops.DEFAULTS).forGetter(EconomyConfig::shops),
		Haggle.CODEC.optionalFieldOf("haggle", Haggle.DEFAULTS).forGetter(EconomyConfig::haggle),
		Holdings.CODEC.optionalFieldOf("holdings", Holdings.DEFAULTS).forGetter(EconomyConfig::holdings),
		Works.CODEC.optionalFieldOf("works", Works.DEFAULTS).forGetter(EconomyConfig::works),
		Fence.CODEC.optionalFieldOf("fence", Fence.DEFAULTS).forGetter(EconomyConfig::fence),
		Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("services", Services.DEFAULTS).forGetter(EconomyConfig::services),
		Station.CODEC.optionalFieldOf("station", Station.DEFAULTS).forGetter(EconomyConfig::station)
	).apply(i, EconomyConfig::new));

	/** A service's base price in bits (the built-in default when the file leaves it out; 0 if unknown). */
	public double service(String name) {
		Double v = this.services.get(name);
		return v != null ? v : Services.DEFAULTS.getOrDefault(name, 0.0);
	}

	/** The loot for a category, falling back to the built-in default when the file leaves it out. */
	public CoinLoot loot(String category) {
		CoinLoot l = this.loot.get(category);
		return l != null ? l : CoinLoot.DEFAULTS.getOrDefault(category, CoinLoot.NONE);
	}

	/**
	 * How market prices are made: {@code base × scarcity × reputation × culture × drift}, then the spread.
	 *
	 * @param spread              gap between what a market charges and what it pays, as a fraction of the mid price
	 * @param scarcityElasticity  how hard price answers stock: scarcity = (target / stock) ^ elasticity
	 * @param scarcityMin         floor of the scarcity factor (a glut)
	 * @param scarcityMax         ceiling of the scarcity factor (nothing in store)
	 * @param targetFraction      the stock a town is content with, as a fraction of its capacity
	 * @param reserveDays         ... or this many days of its own consumption, if more
	 * @param surplusAbove        a town sells goods only when stock exceeds target × this
	 * @param reputationEffect    at reputation ±100, prices move this fraction in the player's favour (or against)
	 * @param minTradeReputation  below this a merchant will not deal with the player
	 * @param builderPremium      extra a town pays for goods its builders are waiting on
	 * @param cultureEffect       scales the culture specialities (0 = every town alike)
	 * @param driftSigma          day-to-day wander of each town's prices (log scale)
	 * @param driftReversion      how quickly the wander pulls back toward 1 each day
	 * @param driftLimit          the wander never exceeds ± this (log scale)
	 * @param waresPerGoods       how many different items a market lists per kind of goods
	 * @param siteEffect          goods the settlement's own resource sites yield sell for this much less there (0: no effect)
	 */
	public record Pricing(double spread, double scarcityElasticity, double scarcityMin, double scarcityMax, double targetFraction,
		double reserveDays, double surplusAbove, double reputationEffect, int minTradeReputation, double builderPremium,
		double cultureEffect, double driftSigma, double driftReversion, double driftLimit, int waresPerGoods, double siteEffect) {
		public static final Pricing DEFAULTS = new Pricing(0.3, 0.8, 0.5, 3.0, 0.35, 6, 1.15, 0.25, -50, 0.3, 1.0, 0.05, 0.15, 0.35, 3, 0.15);

		public static final Codec<Pricing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("spread", DEFAULTS.spread).forGetter(Pricing::spread),
			Codec.DOUBLE.optionalFieldOf("scarcity_elasticity", DEFAULTS.scarcityElasticity).forGetter(Pricing::scarcityElasticity),
			Codec.DOUBLE.optionalFieldOf("scarcity_min", DEFAULTS.scarcityMin).forGetter(Pricing::scarcityMin),
			Codec.DOUBLE.optionalFieldOf("scarcity_max", DEFAULTS.scarcityMax).forGetter(Pricing::scarcityMax),
			Codec.DOUBLE.optionalFieldOf("target_fraction", DEFAULTS.targetFraction).forGetter(Pricing::targetFraction),
			Codec.DOUBLE.optionalFieldOf("reserve_days", DEFAULTS.reserveDays).forGetter(Pricing::reserveDays),
			Codec.DOUBLE.optionalFieldOf("surplus_above", DEFAULTS.surplusAbove).forGetter(Pricing::surplusAbove),
			Codec.DOUBLE.optionalFieldOf("reputation_effect", DEFAULTS.reputationEffect).forGetter(Pricing::reputationEffect),
			Codec.INT.optionalFieldOf("min_trade_reputation", DEFAULTS.minTradeReputation).forGetter(Pricing::minTradeReputation),
			Codec.DOUBLE.optionalFieldOf("builder_premium", DEFAULTS.builderPremium).forGetter(Pricing::builderPremium),
			Codec.DOUBLE.optionalFieldOf("culture_effect", DEFAULTS.cultureEffect).forGetter(Pricing::cultureEffect),
			Codec.DOUBLE.optionalFieldOf("drift_sigma", DEFAULTS.driftSigma).forGetter(Pricing::driftSigma),
			Codec.DOUBLE.optionalFieldOf("drift_reversion", DEFAULTS.driftReversion).forGetter(Pricing::driftReversion),
			Codec.DOUBLE.optionalFieldOf("drift_limit", DEFAULTS.driftLimit).forGetter(Pricing::driftLimit),
			Codec.INT.optionalFieldOf("wares_per_goods", DEFAULTS.waresPerGoods).forGetter(Pricing::waresPerGoods),
			Codec.doubleRange(0, 0.9).optionalFieldOf("site_effect", DEFAULTS.siteEffect).forGetter(Pricing::siteEffect)
		).apply(i, Pricing::new));
	}

	/**
	 * The daily settlement economy (one step per sim day, on {@code SettlementEvents.DAY_PASSED}).
	 *
	 * @param taxPerHead          bits each resident brings the treasury a day (taxes, tithes, market dues)
	 * @param wagePerWorker       bits the treasury pays each working adult a day
	 * @param tradeIncomeRate     share of the market value of the town's surplus earned a day from passing trade
	 * @param upkeepRate          share of the treasury spent a day on upkeep (keeps treasuries bounded)
	 * @param mintUnitsPerDay     metal units a settlement's mint strikes a day (only with a minter or a bank)
	 * @param mintUnitsPerMinter  more per minter
	 * @param bitsPerMetalUnit    coin struck from one unit of metal
	 */
	public record Treasury(double taxPerHead, double wagePerWorker, double tradeIncomeRate, double upkeepRate, int mintUnitsPerDay,
		int mintUnitsPerMinter, int bitsPerMetalUnit) {
		public static final Treasury DEFAULTS = new Treasury(2.0, 1.0, 0.02, 0.004, 2, 4, 6);

		public static final Codec<Treasury> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("tax_per_head", DEFAULTS.taxPerHead).forGetter(Treasury::taxPerHead),
			Codec.DOUBLE.optionalFieldOf("wage_per_worker", DEFAULTS.wagePerWorker).forGetter(Treasury::wagePerWorker),
			Codec.DOUBLE.optionalFieldOf("trade_income_rate", DEFAULTS.tradeIncomeRate).forGetter(Treasury::tradeIncomeRate),
			Codec.DOUBLE.optionalFieldOf("upkeep_rate", DEFAULTS.upkeepRate).forGetter(Treasury::upkeepRate),
			Codec.INT.optionalFieldOf("mint_units_per_day", DEFAULTS.mintUnitsPerDay).forGetter(Treasury::mintUnitsPerDay),
			Codec.INT.optionalFieldOf("mint_units_per_minter", DEFAULTS.mintUnitsPerMinter).forGetter(Treasury::mintUnitsPerMinter),
			Codec.INT.optionalFieldOf("bits_per_metal_unit", DEFAULTS.bitsPerMetalUnit).forGetter(Treasury::bitsPerMetalUnit)
		).apply(i, Treasury::new));
	}

	/**
	 * The bank counter and its money changer.
	 *
	 * @param emeraldBuyPrice     bits the changer pays for an emerald
	 * @param emeraldSellPrice    bits the changer asks for an emerald
	 * @param emeraldReserveBase  emeralds a bank keeps to sell, plus {@code emeraldReservePerTier} per settlement tier
	 */
	public record Bank(int emeraldBuyPrice, int emeraldSellPrice, int emeraldReserveBase, int emeraldReservePerTier) {
		public static final Bank DEFAULTS = new Bank(18, 24, 4, 6);

		public static final Codec<Bank> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("emerald_buy_price", DEFAULTS.emeraldBuyPrice).forGetter(Bank::emeraldBuyPrice),
			Codec.INT.optionalFieldOf("emerald_sell_price", DEFAULTS.emeraldSellPrice).forGetter(Bank::emeraldSellPrice),
			Codec.INT.optionalFieldOf("emerald_reserve_base", DEFAULTS.emeraldReserveBase).forGetter(Bank::emeraldReserveBase),
			Codec.INT.optionalFieldOf("emerald_reserve_per_tier", DEFAULTS.emeraldReservePerTier).forGetter(Bank::emeraldReservePerTier)
		).apply(i, Bank::new));
	}

	/**
	 * Buying land and houses.
	 *
	 * @param houseMinReputation  standing a player needs to buy a built house
	 * @param plotMinReputation   standing a player needs to buy an empty plot
	 * @param landPerBlock        bits per block of footprint, at village prices (hamlets cheaper, cities dearer)
	 * @param buildingPerBlock    bits per block of footprint per storey of a built house, at finish tier 0
	 * @param reputationDiscount  discount at reputation 100
	 */
	public record Property(int houseMinReputation, int plotMinReputation, double landPerBlock, double buildingPerBlock,
		double reputationDiscount) {
		public static final Property DEFAULTS = new Property(25, 10, 6.0, 4.0, 0.2);

		public static final Codec<Property> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("house_min_reputation", DEFAULTS.houseMinReputation).forGetter(Property::houseMinReputation),
			Codec.INT.optionalFieldOf("plot_min_reputation", DEFAULTS.plotMinReputation).forGetter(Property::plotMinReputation),
			Codec.DOUBLE.optionalFieldOf("land_per_block", DEFAULTS.landPerBlock).forGetter(Property::landPerBlock),
			Codec.DOUBLE.optionalFieldOf("building_per_block", DEFAULTS.buildingPerBlock).forGetter(Property::buildingPerBlock),
			Codec.DOUBLE.optionalFieldOf("reputation_discount", DEFAULTS.reputationDiscount).forGetter(Property::reputationDiscount)
		).apply(i, Property::new));
	}

	/**
	 * The coins themselves.
	 *
	 * @param pouchCapacity  coins one pouch holds
	 * @param meltBit        copper nuggets from melting a copper bit (0 stops coins being melted)
	 * @param meltMark       iron nuggets from melting an iron mark
	 * @param meltCrown      gold nuggets from melting a gold crown
	 */
	public record Coins(int pouchCapacity, int meltBit, int meltMark, int meltCrown) {
		public static final Coins DEFAULTS = new Coins(512, 1, 1, 2);

		public static final Codec<Coins> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 100_000).optionalFieldOf("pouch_capacity", DEFAULTS.pouchCapacity).forGetter(Coins::pouchCapacity),
			Codec.intRange(0, 64).optionalFieldOf("melt_bit", DEFAULTS.meltBit).forGetter(Coins::meltBit),
			Codec.intRange(0, 64).optionalFieldOf("melt_mark", DEFAULTS.meltMark).forGetter(Coins::meltMark),
			Codec.intRange(0, 64).optionalFieldOf("melt_crown", DEFAULTS.meltCrown).forGetter(Coins::meltCrown)
		).apply(i, Coins::new));
	}

	/**
	 * Coin added to a kind of chest: with {@code chance}, a few bits and marks, and maybe crowns.
	 */
	public record CoinLoot(float chance, int bitsMin, int bitsMax, int marksMin, int marksMax, float crownChance, int crownsMax) {
		public static final CoinLoot NONE = new CoinLoot(0, 0, 0, 0, 0, 0, 0);

		public static final Codec<CoinLoot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0, 1).optionalFieldOf("chance", 0F).forGetter(CoinLoot::chance),
			Codec.intRange(0, 64).optionalFieldOf("bits_min", 0).forGetter(CoinLoot::bitsMin),
			Codec.intRange(0, 64).optionalFieldOf("bits_max", 0).forGetter(CoinLoot::bitsMax),
			Codec.intRange(0, 64).optionalFieldOf("marks_min", 0).forGetter(CoinLoot::marksMin),
			Codec.intRange(0, 64).optionalFieldOf("marks_max", 0).forGetter(CoinLoot::marksMax),
			Codec.floatRange(0, 1).optionalFieldOf("crown_chance", 0F).forGetter(CoinLoot::crownChance),
			Codec.intRange(0, 64).optionalFieldOf("crowns_max", 1).forGetter(CoinLoot::crownsMax)
		).apply(i, CoinLoot::new));

		/** Sensible amounts per kind of place: a village chest has small change, buried treasure has a captain's purse. */
		public static final Map<String, CoinLoot> DEFAULTS = defaults();

		private static Map<String, CoinLoot> defaults() {
			Map<String, CoinLoot> m = new LinkedHashMap<>();
			m.put("village", new CoinLoot(0.65F, 2, 9, 0, 2, 0.03F, 1));
			m.put("shipwreck_treasure", new CoinLoot(1F, 6, 20, 2, 7, 0.4F, 2));
			m.put("shipwreck", new CoinLoot(0.4F, 1, 6, 0, 1, 0F, 1));
			m.put("ruin", new CoinLoot(0.7F, 3, 12, 0, 3, 0.08F, 1));
			m.put("buried_treasure", new CoinLoot(1F, 10, 30, 4, 12, 0.75F, 3));
			m.put("dungeon", new CoinLoot(0.8F, 4, 14, 1, 4, 0.1F, 1));
			m.put("mineshaft", new CoinLoot(0.55F, 3, 12, 0, 3, 0.05F, 1));
			m.put("ancient_city", new CoinLoot(0.9F, 0, 0, 3, 9, 0.5F, 3));
			m.put("temple", new CoinLoot(0.8F, 4, 16, 1, 5, 0.25F, 2));
			m.put("stronghold", new CoinLoot(0.6F, 2, 10, 1, 4, 0.1F, 1));
			m.put("mansion", new CoinLoot(0.8F, 4, 16, 2, 6, 0.3F, 2));
			m.put("outpost", new CoinLoot(0.7F, 3, 10, 1, 4, 0.1F, 1));
			m.put("camp", new CoinLoot(0.5F, 1, 6, 0, 2, 0F, 1));
			m.put("expanse", new CoinLoot(0.6F, 2, 10, 0, 3, 0.04F, 1));
			m.put("expanse_hidden", new CoinLoot(0.9F, 4, 16, 2, 6, 0.3F, 2));
			m.put("expanse_lost", new CoinLoot(0.75F, 3, 12, 1, 4, 0.12F, 1));
			return m;
		}
	}

	/**
	 * Purses: what folk earn, pay and spend each sim day (docs/ECONOMY.md, "Money through people").
	 *
	 * @param wages             bits a working adult earns a day, by station ({@code poor}, {@code peasant}, ... {@code lord})
	 * @param pollTax           bits each adult pays the treasury a day, if their purse can
	 * @param foodPerHead       units of food each person buys a day (the sim eats them; folk pay for them)
	 * @param reserveDays       days of its own costs a household keeps before it spends on comforts
	 * @param comfortShare      share of a household's purse above its reserve spent on comforts each day
	 * @param innShare          share of comfort spending that goes to the inn or tavern (meals, ale), where there is one
	 * @param tithe             share of a purse given each day in tithes, alms and dues (keeps purses bounded)
	 * @param startingPurseDays days of wages an adult already has the first time the economy sees them
	 * @param wageCapShare      the treasury pays at most this share of itself in wages a day (it never empties at once)
	 * @param consumedGoods     goods folk buy and use up (taken from shop or town stock); food is paid for, not taken
	 * @param enabled           purses at all: off, the treasury taxes and pays wages to nobody in particular (phase 2)
	 */
	public record Households(Map<String, Double> wages, double pollTax, double foodPerHead, double reserveDays, double comfortShare,
		double innShare, double tithe, double startingPurseDays, double wageCapShare, List<String> consumedGoods, boolean enabled) {
		public static final Households DEFAULTS = new Households(defaultWages(), 1.0, 1.0, 6, 0.06, 0.3, 0.004, 10, 0.25,
			List.of("cloth", "candles", "luxury", "books", "dye", "specialty"), true);

		public static final Codec<Households> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("wages", DEFAULTS.wages).forGetter(Households::wages),
			Codec.DOUBLE.optionalFieldOf("poll_tax", DEFAULTS.pollTax).forGetter(Households::pollTax),
			Codec.DOUBLE.optionalFieldOf("food_per_head", DEFAULTS.foodPerHead).forGetter(Households::foodPerHead),
			Codec.DOUBLE.optionalFieldOf("reserve_days", DEFAULTS.reserveDays).forGetter(Households::reserveDays),
			Codec.doubleRange(0, 1).optionalFieldOf("comfort_share", DEFAULTS.comfortShare).forGetter(Households::comfortShare),
			Codec.doubleRange(0, 1).optionalFieldOf("inn_share", DEFAULTS.innShare).forGetter(Households::innShare),
			Codec.doubleRange(0, 0.5).optionalFieldOf("tithe", DEFAULTS.tithe).forGetter(Households::tithe),
			Codec.DOUBLE.optionalFieldOf("starting_purse_days", DEFAULTS.startingPurseDays).forGetter(Households::startingPurseDays),
			Codec.doubleRange(0, 1).optionalFieldOf("wage_cap_share", DEFAULTS.wageCapShare).forGetter(Households::wageCapShare),
			Codec.STRING.listOf().optionalFieldOf("consumed_goods", DEFAULTS.consumedGoods).forGetter(Households::consumedGoods),
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Households::enabled)
		).apply(i, Households::new));

		/** A day's wage for a station (the built-in default when the file leaves it out). */
		public double wage(dev.voidmc.hearth.api.Station station) {
			Double v = this.wages.get(station.getSerializedName());
			return v != null ? v : defaultWages().getOrDefault(station.getSerializedName(), 3.0);
		}

		private static Map<String, Double> defaultWages() {
			Map<String, Double> m = new LinkedHashMap<>();
			m.put("poor", 2.0);
			m.put("peasant", 4.0);
			m.put("artisan", 5.0);
			m.put("merchant", 7.0);
			m.put("clergy", 4.0);
			m.put("scholar", 5.0);
			m.put("official", 6.0);
			m.put("gentry", 12.0);
			m.put("lord", 25.0);
			return m;
		}
	}

	/**
	 * Shops: every shop keeps its own stock and till (as KCD's merchants do), restocked from the town's stores.
	 *
	 * @param stockDays        a shop keeps this many days of its sales in stock
	 * @param minStock         ... and at least this many units of each goods it deals in
	 * @param maxStock         ... and at most this many (the rest goes back to the town's stores)
	 * @param wholesale        share of the town's mid price a shop pays the treasury for stock (and is paid for returns)
	 * @param startingTill     coin a new shop's till starts with, lent from the treasury if it can
	 * @param workingTill      coin a till keeps for buying; the keeper takes a share of what is above it
	 * @param keeperShare      share of the till above its working coin the keeper (or the owner) takes each day
	 * @param dueRate          share of a shop's takings paid to the treasury as market dues
	 * @param townFloor        shops restock from the town's stores down to this share of its target stock (never below)
	 */
	public record Shops(double stockDays, int minStock, int maxStock, double wholesale, long startingTill, long workingTill, double keeperShare,
		double dueRate, double townFloor) {
		public static final Shops DEFAULTS = new Shops(4, 16, 512, 0.75, 200, 400, 0.3, 0.08, 0.6);

		public static final Codec<Shops> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("stock_days", DEFAULTS.stockDays).forGetter(Shops::stockDays),
			Codec.INT.optionalFieldOf("min_stock", DEFAULTS.minStock).forGetter(Shops::minStock),
			Codec.INT.optionalFieldOf("max_stock", DEFAULTS.maxStock).forGetter(Shops::maxStock),
			Codec.doubleRange(0, 1).optionalFieldOf("wholesale", DEFAULTS.wholesale).forGetter(Shops::wholesale),
			Codec.LONG.optionalFieldOf("starting_till", DEFAULTS.startingTill).forGetter(Shops::startingTill),
			Codec.LONG.optionalFieldOf("working_till", DEFAULTS.workingTill).forGetter(Shops::workingTill),
			Codec.doubleRange(0, 1).optionalFieldOf("keeper_share", DEFAULTS.keeperShare).forGetter(Shops::keeperShare),
			Codec.doubleRange(0, 1).optionalFieldOf("due_rate", DEFAULTS.dueRate).forGetter(Shops::dueRate),
			Codec.doubleRange(0, 2).optionalFieldOf("town_floor", DEFAULTS.townFloor).forGetter(Shops::townFloor)
		).apply(i, Shops::new));
	}

	/**
	 * Haggling at the trade screen.
	 *
	 * @param room            how far below asking (or above offering) a merchant will go at heart, as a share of the price
	 * @param charismaRoom    charisma −1..1 widens or narrows that room by this share of itself
	 * @param standingRoom    standing −100..100 widens or narrows it by this share of itself
	 * @param patience        a merchant's patience at the start (traits and opinion move it)
	 * @param roundCost       patience each counter-offer costs
	 * @param outrageCost     extra patience lost per price-width an offer is beyond what the merchant would ever take
	 * @param sourDays        days a merchant stays sour after you push them too far
	 * @param sourMarkup      a sour merchant's prices move this share against you
	 * @param sourWeight      the standing deed for souring a merchant (negative)
	 */
	public record Haggle(double room, double charismaRoom, double standingRoom, double patience, double roundCost, double outrageCost,
		int sourDays, double sourMarkup, int sourWeight) {
		public static final Haggle DEFAULTS = new Haggle(0.15, 0.5, 0.4, 100, 14, 90, 1, 0.15, -2);

		public static final Codec<Haggle> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(0, 0.9).optionalFieldOf("room", DEFAULTS.room).forGetter(Haggle::room),
			Codec.DOUBLE.optionalFieldOf("charisma_room", DEFAULTS.charismaRoom).forGetter(Haggle::charismaRoom),
			Codec.DOUBLE.optionalFieldOf("standing_room", DEFAULTS.standingRoom).forGetter(Haggle::standingRoom),
			Codec.DOUBLE.optionalFieldOf("patience", DEFAULTS.patience).forGetter(Haggle::patience),
			Codec.DOUBLE.optionalFieldOf("round_cost", DEFAULTS.roundCost).forGetter(Haggle::roundCost),
			Codec.DOUBLE.optionalFieldOf("outrage_cost", DEFAULTS.outrageCost).forGetter(Haggle::outrageCost),
			Codec.intRange(0, 365).optionalFieldOf("sour_days", DEFAULTS.sourDays).forGetter(Haggle::sourDays),
			Codec.doubleRange(0, 0.9).optionalFieldOf("sour_markup", DEFAULTS.sourMarkup).forGetter(Haggle::sourMarkup),
			Codec.INT.optionalFieldOf("sour_weight", DEFAULTS.sourWeight).forGetter(Haggle::sourWeight)
		).apply(i, Haggle::new));
	}

	/**
	 * Owning a business: a workshop, a shop, a farm or an extraction site.
	 *
	 * @param minReputation   standing needed to buy a business
	 * @param incomeDays      a business costs its land and building plus this many days of its expected income
	 * @param productivity    what a worker's day is worth to the business, in wages (1.8: a worker earns 1, the owner 0.8)
	 * @param upkeep          share of the owner's income spent on the business's upkeep
	 * @param hireCost        days of wages paid up front to hire a worker into your business
	 */
	public record Holdings(int minReputation, double incomeDays, double productivity, double upkeep, double hireCost) {
		public static final Holdings DEFAULTS = new Holdings(35, 60, 1.8, 0.1, 5);

		public static final Codec<Holdings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("min_reputation", DEFAULTS.minReputation).forGetter(Holdings::minReputation),
			Codec.DOUBLE.optionalFieldOf("income_days", DEFAULTS.incomeDays).forGetter(Holdings::incomeDays),
			Codec.DOUBLE.optionalFieldOf("productivity", DEFAULTS.productivity).forGetter(Holdings::productivity),
			Codec.doubleRange(0, 1).optionalFieldOf("upkeep", DEFAULTS.upkeep).forGetter(Holdings::upkeep),
			Codec.DOUBLE.optionalFieldOf("hire_cost", DEFAULTS.hireCost).forGetter(Holdings::hireCost)
		).apply(i, Holdings::new));
	}

	/**
	 * Funding construction and supply contracts.
	 *
	 * @param importMarkup       a funded project buys what the town lacks from passing traders at the town's price times this
	 * @param fundWeightPerCrown standing deed weight per crown given to a project (at most 25 per gift)
	 * @param contractPremium    a supply contract pays the town's buying price plus this share
	 * @param contractDays       days a taken contract runs before it is due
	 * @param offerDays          days an untaken contract stays on offer
	 * @param maxContracts       contracts a settlement offers at once
	 * @param contractScarcity   a goods is offered under contract when its scarcity reaches this (or the builders need it)
	 * @param failWeight         standing deed for letting a contract lapse (negative)
	 * @param doneWeight         standing deed for fulfilling one
	 */
	public record Works(double importMarkup, double fundWeightPerCrown, double contractPremium, int contractDays, int offerDays, int maxContracts,
		double contractScarcity, int failWeight, int doneWeight) {
		public static final Works DEFAULTS = new Works(1.25, 0.5, 0.35, 8, 6, 3, 1.6, -6, 4);

		public static final Codec<Works> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("import_markup", DEFAULTS.importMarkup).forGetter(Works::importMarkup),
			Codec.DOUBLE.optionalFieldOf("fund_weight_per_crown", DEFAULTS.fundWeightPerCrown).forGetter(Works::fundWeightPerCrown),
			Codec.DOUBLE.optionalFieldOf("contract_premium", DEFAULTS.contractPremium).forGetter(Works::contractPremium),
			Codec.intRange(1, 365).optionalFieldOf("contract_days", DEFAULTS.contractDays).forGetter(Works::contractDays),
			Codec.intRange(1, 365).optionalFieldOf("offer_days", DEFAULTS.offerDays).forGetter(Works::offerDays),
			Codec.intRange(0, 16).optionalFieldOf("max_contracts", DEFAULTS.maxContracts).forGetter(Works::maxContracts),
			Codec.DOUBLE.optionalFieldOf("contract_scarcity", DEFAULTS.contractScarcity).forGetter(Works::contractScarcity),
			Codec.INT.optionalFieldOf("fail_weight", DEFAULTS.failWeight).forGetter(Works::failWeight),
			Codec.INT.optionalFieldOf("done_weight", DEFAULTS.doneWeight).forGetter(Works::doneWeight)
		).apply(i, Works::new));
	}

	/**
	 * Stolen goods and fences (docs/LIVING_WORLD.md §4).
	 *
	 * @param refuseStolen     honest merchants refuse goods stolen in their own town
	 * @param refuseAnywhere   ... or stolen anywhere (stricter servers)
	 * @param rate             a fence pays this share of what an honest merchant would
	 */
	public record Fence(boolean refuseStolen, boolean refuseAnywhere, double rate) {
		public static final Fence DEFAULTS = new Fence(true, false, 0.4);

		public static final Codec<Fence> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("refuse_stolen", DEFAULTS.refuseStolen).forGetter(Fence::refuseStolen),
			Codec.BOOL.optionalFieldOf("refuse_anywhere", DEFAULTS.refuseAnywhere).forGetter(Fence::refuseAnywhere),
			Codec.doubleRange(0, 1).optionalFieldOf("rate", DEFAULTS.rate).forGetter(Fence::rate)
		).apply(i, Fence::new));
	}

	/** Base prices (bits, at village size) of the services other modules sell: inns, baths, stables. */
	public static final class Services {
		public static final Map<String, Double> DEFAULTS = defaults();

		private Services() {
		}

		private static Map<String, Double> defaults() {
			Map<String, Double> m = new LinkedHashMap<>();
			m.put("inn_bed", 6.0);
			m.put("inn_room_week", 30.0);
			m.put("meal", 2.0);
			m.put("ale", 1.0);
			m.put("bath", 3.0);
			m.put("stable_hire_day", 15.0);
			m.put("stable_keep_day", 2.0);
			m.put("horse", 800.0);
			m.put("tailor_dye", 8.0);
			m.put("healer", 10.0);
			m.put("dice_stake", 5.0);
			return m;
		}
	}

	/**
	 * A household's station follows its purse (docs/LIVING_WORLD.md §8.1).
	 *
	 * @param followPurse   whether the economy moves stations at all (turn off if another module owns them)
	 * @param riseDays      a household rises a step when it holds this many days of the next station's wages per head
	 * @param fallDays      ... and falls a step below this many days of its own wages per head, once it has gone hungry
	 * @param hungerDays    days without food money before a household can fall
	 * @param holdDays      days a station is kept before it can move again
	 */
	public record Station(boolean followPurse, double riseDays, double fallDays, int hungerDays, int holdDays) {
		public static final Station DEFAULTS = new Station(true, 25, 2, 3, 10);

		public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("follow_purse", DEFAULTS.followPurse).forGetter(Station::followPurse),
			Codec.DOUBLE.optionalFieldOf("rise_days", DEFAULTS.riseDays).forGetter(Station::riseDays),
			Codec.DOUBLE.optionalFieldOf("fall_days", DEFAULTS.fallDays).forGetter(Station::fallDays),
			Codec.intRange(0, 365).optionalFieldOf("hunger_days", DEFAULTS.hungerDays).forGetter(Station::hungerDays),
			Codec.intRange(0, 3650).optionalFieldOf("hold_days", DEFAULTS.holdDays).forGetter(Station::holdDays)
		).apply(i, Station::new));
	}
}
