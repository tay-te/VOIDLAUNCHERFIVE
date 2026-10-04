package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.model.SimRules;

/**
 * Food spoils without a granary (docs/LIVING_WORLD.md §7.5): each day a share of every perishable good outside cool
 * stores goes off (fish and meat fastest, grain slowest), much less inside them (granaries, cellars: the data's
 * {@code cool} archetypes), and meat and fish keep longer while there is salt to cure them (which uses a little salt).
 */
public final class Spoilage {
	private Spoilage() {
	}

	static void daily(SimSettlement s, SimRules r, SimData data) {
		SimConfig.Spoilage cfg = r.resources.spoilage();
		if (!cfg.enabled()) {
			return;
		}
		double[] rate = data.perishable();
		int[] amounts = s.stockpile().amounts();
		double[] frac = s.clock.fractions;
		int salt = Goods.SALT.ordinal();
		double saltUsed = 0;
		for (int g = 0; g < rate.length; g++) {
			if (rate[g] <= 0 || amounts[g] <= 0) {
				continue;
			}
			int cool = Math.min(amounts[g], s.coolCapacity(g));
			int warm = amounts[g] - cool;
			double rg = rate[g] * cfg.rate();
			if ((g == Goods.MEAT.ordinal() || g == Goods.FISH.ordinal()) && amounts[salt] + frac[salt] - saltUsed > 1) {
				rg *= cfg.salted();
				// a pound of salt cures a hundredweight
				saltUsed += warm * 0.01;
			}
			frac[g] -= warm * rg + cool * rg * cfg.cool();
		}
		frac[salt] -= saltUsed;
		Production.settle(s, r, frac, 0);
	}
}
