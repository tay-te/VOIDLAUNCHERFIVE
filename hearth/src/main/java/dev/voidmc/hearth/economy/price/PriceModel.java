package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.economy.EconomyConfig;

/**
 * The price arithmetic, free of the game so it can be tested: a settlement's price for one item is
 *
 * <pre>
 *   mid  = base × scarcity(stock, target) × culture × drift
 *   buy  = mid × (1 + spread/2) × reputation(player buys)          what the player pays
 *   sell = mid × (1 − spread/2) × reputation(player sells) × need   what the town pays
 * </pre>
 *
 * Scarcity is {@code (target / stock) ^ elasticity} clamped to [min, max]: a town holding its target pays the base
 * price, one with nothing in store pays up to {@code max}, a glutted one down to {@code min}. Reputation moves both
 * prices toward the player as standing rises. What the town pays is always below what it charges.
 */
public record PriceModel(double spread, double elasticity, double scarcityMin, double scarcityMax, double reputationEffect,
	double builderPremium, double cultureEffect) {

	public static PriceModel of(EconomyConfig.Pricing p) {
		return new PriceModel(p.spread(), p.scarcityElasticity(), p.scarcityMin(), p.scarcityMax(), p.reputationEffect(), p.builderPremium(),
			p.cultureEffect());
	}

	/** The stock a town is content to hold: a share of its capacity, or enough for some days of its own use if more. */
	public static double target(int capacity, double dailyUse, double targetFraction, double reserveDays) {
		double t = Math.max(capacity * targetFraction, dailyUse * reserveDays);
		return Math.max(1, Math.min(t, Math.max(1, capacity)));
	}

	/** Non-increasing in {@code stock}: the less a town holds against its target, the dearer. */
	public double scarcity(int stock, double target) {
		double ratio = Math.max(target, 1) / Math.max(stock, 0.5);
		return clamp(Math.pow(ratio, this.elasticity), this.scarcityMin, this.scarcityMax);
	}

	/** A culture's speciality (below 1: plentiful here), softened or sharpened by {@code cultureEffect}. */
	public double culture(double speciality) {
		return Math.pow(Math.max(speciality, 0.05), this.cultureEffect);
	}

	/** The player's standing (−100..100) as a price factor: lower prices to buy and higher to sell as it rises. */
	public double reputation(int reputation, boolean playerBuys) {
		double r = clamp(reputation, -100, 100) / 100.0;
		return playerBuys ? 1 - this.reputationEffect * r : 1 + this.reputationEffect * r;
	}

	public double mid(double base, double scarcity, double culture, double drift) {
		return base * scarcity * culture * drift;
	}

	/** What the player pays for one item. */
	public double buy(double mid, int reputation) {
		return mid * (1 + this.spread / 2) * reputation(reputation, true);
	}

	/** What the town pays for one item (more if its builders need it), never above 95 % of what it would charge. */
	public double sell(double mid, int reputation, boolean needed) {
		double s = mid * (1 - this.spread / 2) * reputation(reputation, false) * (needed ? 1 + this.builderPremium : 1);
		return Math.min(s, buy(mid, reputation) * 0.95);
	}

	/**
	 * How many items trade as one lot: the smallest power of two (up to the stack size) worth at least {@code minLot}
	 * bits at mid price, so that rounding to whole bits stays small. Cheap things go by the dozen, dear things singly.
	 */
	public static int lotSize(double mid, int maxStack, double minLot) {
		int lot = 1;
		while (lot < maxStack && mid * lot < minLot) {
			lot *= 2;
		}
		return Math.min(lot, maxStack);
	}

	/** A lot's price in whole bits: rounded up when the player buys (at least 1), down when they sell. */
	public static long lotPrice(double unit, int lot, boolean playerBuys) {
		double v = unit * lot;
		return playerBuys ? Math.max(1, (long) Math.ceil(v - 1e-9)) : (long) Math.floor(v + 1e-9);
	}

	static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}
}
