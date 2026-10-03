package dev.voidmc.hearth.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/hearth/economy.json}. Every field is optional, so an old file keeps working when fields are added.
 * See docs/ECONOMY.md for what each number does.
 */
public record EconomyConfig(Pricing pricing, Treasury treasury, Bank bank, Property property, Coins coins, Map<String, CoinLoot> loot) {
	public static final EconomyConfig DEFAULTS = new EconomyConfig(Pricing.DEFAULTS, Treasury.DEFAULTS, Bank.DEFAULTS, Property.DEFAULTS,
		Coins.DEFAULTS, CoinLoot.DEFAULTS);

	public static final Codec<EconomyConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Pricing.CODEC.optionalFieldOf("pricing", Pricing.DEFAULTS).forGetter(EconomyConfig::pricing),
		Treasury.CODEC.optionalFieldOf("treasury", Treasury.DEFAULTS).forGetter(EconomyConfig::treasury),
		Bank.CODEC.optionalFieldOf("bank", Bank.DEFAULTS).forGetter(EconomyConfig::bank),
		Property.CODEC.optionalFieldOf("property", Property.DEFAULTS).forGetter(EconomyConfig::property),
		Coins.CODEC.optionalFieldOf("coins", Coins.DEFAULTS).forGetter(EconomyConfig::coins),
		Codec.unboundedMap(Codec.STRING, CoinLoot.CODEC).optionalFieldOf("loot", CoinLoot.DEFAULTS).forGetter(EconomyConfig::loot)
	).apply(i, EconomyConfig::new));

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
	 */
	public record Pricing(double spread, double scarcityElasticity, double scarcityMin, double scarcityMax, double targetFraction,
		double reserveDays, double surplusAbove, double reputationEffect, int minTradeReputation, double builderPremium,
		double cultureEffect, double driftSigma, double driftReversion, double driftLimit, int waresPerGoods) {
		public static final Pricing DEFAULTS = new Pricing(0.3, 0.8, 0.5, 3.0, 0.35, 6, 1.15, 0.25, -50, 0.3, 1.0, 0.05, 0.15, 0.35, 3);

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
			Codec.INT.optionalFieldOf("wares_per_goods", DEFAULTS.waresPerGoods).forGetter(Pricing::waresPerGoods)
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
}
