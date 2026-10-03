package dev.voidmc.hearth.economy.daily;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Stockpile;
import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.price.PriceModel;
import dev.voidmc.hearth.economy.price.PriceTable;

/**
 * A settlement's money, one sim day at a time: on {@code SettlementEvents.DAY_PASSED} (including every day caught up
 * while nobody was near), so it is a handful of arithmetic per settlement and touches no world.
 *
 * <pre>
 *   income   taxes (per head) + passing trade (a share of the market value of its surplus)
 *   out      wages (per working adult) + upkeep (a small share of the treasury: keeps treasuries bounded)
 *   mint     with a minter or a bank, metal from the stockpile is struck into coin
 *   prices   each goods' price wanders a little, pulled back toward normal; the town's own use of each goods is
 *            tracked (trade with players excluded) so prices know how fast it eats through its stores
 *   builders a shortage the builders reported is forgotten once it is in store or a week has passed
 * </pre>
 *
 * The sim module owns production and population; this only moves coin, and metal into the mint.
 */
public final class SettlementEconomy {
	/** Days a builders' shortage is remembered without being met. */
	static final int NEED_MEMORY_DAYS = 8;
	/** Smoothing of the daily use estimate (weight of the newest day). */
	static final float USE_SMOOTHING = 0.2F;

	private SettlementEconomy() {
	}

	public static void dayPassed(Settlement settlement, long day) {
		EconomyConfig cfg = EconomyModule.config();
		MarketLedger ledger = settlement.data(EconomyModule.LEDGER);
		if (ledger.lastDay == day) {
			return;
		}
		ledger.lastDay = day;
		Stockpile stock = settlement.stockpile();
		EconomyConfig.Pricing pricing = cfg.pricing();

		for (Goods g : Goods.VALUES) {
			int i = g.ordinal();
			int now = stock.get(g);
			if (ledger.lastStock[i] >= 0) {
				int ownChange = now - ledger.lastStock[i] - ledger.traded[i];
				ledger.use[i] = ledger.use[i] * (1 - USE_SMOOTHING) + Math.max(0, -ownChange) * USE_SMOOTHING;
			}
			ledger.lastStock[i] = now;
			ledger.traded[i] = 0;
			if (ledger.need[i] > 0 && (now >= ledger.need[i] || day - ledger.needDay[i] > NEED_MEMORY_DAYS)) {
				ledger.need[i] = 0;
				ledger.needFor[i] = "";
			}
			ledger.drift[i] = (float) drift(ledger.drift[i], settlement.id(), day, i, pricing);
		}

		EconomyConfig.Treasury t = cfg.treasury();
		int workers = 0;
		int minters = 0;
		for (Resident r : settlement.residents()) {
			if (r.age == AgeStage.ADULT && r.job != JobType.NONE) {
				workers++;
			}
			if (r.job == JobType.MINTER) {
				minters++;
			}
		}
		long taxes = Math.round(settlement.population() * t.taxPerHead());
		long trade = Math.round(surplusValue(settlement, pricing) * t.tradeIncomeRate());
		settlement.deposit(taxes + trade);

		long wages = Math.round(workers * t.wagePerWorker());
		int unpaid = ledger.books.unpaid();
		if (settlement.withdraw(wages)) {
			unpaid = 0;
		} else {
			wages = settlement.treasury();
			settlement.withdraw(wages);
			unpaid++;
		}
		long upkeep = (long) Math.floor(settlement.treasury() * t.upkeepRate());
		settlement.withdraw(upkeep);

		long minted = 0;
		if (minters > 0 || hasBuiltBank(settlement)) {
			int keep = (int) (PriceModel.target(stock.capacity(Goods.METAL), ledger.use[Goods.METAL.ordinal()], pricing.targetFraction(),
				pricing.reserveDays()) * 0.5);
			int units = Math.min(Math.max(0, stock.get(Goods.METAL) - keep), t.mintUnitsPerDay() + t.mintUnitsPerMinter() * minters);
			if (units > 0) {
				int taken = stock.take(Goods.METAL, units);
				minted = (long) taken * t.bitsPerMetalUnit();
				settlement.deposit(minted);
				ledger.lastStock[Goods.METAL.ordinal()] -= taken; // the mint's metal is not the town's daily use
			}
		}

		ledger.books = new MarketLedger.Books(taxes, trade, wages, upkeep, minted, unpaid);
		ledger.standingGain.values().removeIf(packed -> (packed >> 8) < day);
		settlement.markDirty();
	}

	/** Remembers what the builders are short of, for the market (and its note) until it is in store. */
	public static void constructionStalled(Settlement settlement, PlotView plot, Goods goods, int missing, long day) {
		MarketLedger ledger = settlement.data(EconomyModule.LEDGER);
		int i = goods.ordinal();
		ledger.need[i] = Math.max(ledger.need[i], missing);
		ledger.needDay[i] = day;
		ledger.needFor[i] = plot.spec().archetype().getPath();
		settlement.markDirty();
	}

	/** Price wander: mean-reverting, deterministic per (settlement, day, goods) so a caught-up history is reproducible. */
	static double drift(double current, long settlement, long day, int goods, EconomyConfig.Pricing p) {
		double z = gaussian(settlement * 0x9E3779B97F4A7C15L ^ day * 0xC2B2AE3D27D4EB4FL ^ goods * 0x165667B19E3779F9L);
		double next = current * (1 - p.driftReversion()) + p.driftSigma() * z;
		return Math.max(-p.driftLimit(), Math.min(p.driftLimit(), next));
	}

	/** An approximately standard normal number from a seed (sum of four uniforms, rescaled). */
	static double gaussian(long seed) {
		long x = seed;
		double sum = 0;
		for (int k = 0; k < 4; k++) {
			x ^= x >>> 33;
			x *= 0xFF51AFD7ED558CCDL;
			x ^= x >>> 33;
			x *= 0xC4CEB9FE1A85EC53L;
			x ^= x >>> 33;
			sum += (x >>> 11) * 0x1.0p-53;
		}
		return (sum - 2.0) * Math.sqrt(3.0);
	}

	/** Market value of what the town holds above its target, at base unit values. */
	static double surplusValue(Settlement settlement, EconomyConfig.Pricing pricing) {
		MarketLedger ledger = settlement.data(EconomyModule.LEDGER);
		Stockpile stock = settlement.stockpile();
		double value = 0;
		for (Goods g : Goods.VALUES) {
			double target = PriceModel.target(stock.capacity(g), ledger.use[g.ordinal()], pricing.targetFraction(), pricing.reserveDays());
			double over = stock.get(g) - target * pricing.surplusAbove();
			if (over > 0) {
				value += over * PriceTable.data().unitValue(g);
			}
		}
		return value;
	}

	static boolean hasBuiltBank(Settlement settlement) {
		for (PlotView p : settlement.plots()) {
			if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals("commerce/bank")) {
				return true;
			}
		}
		return false;
	}
}
