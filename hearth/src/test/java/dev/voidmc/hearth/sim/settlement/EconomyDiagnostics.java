package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prints how a town's economy runs over a few months (run with HEARTH_DIAG=1 in the environment). */
class EconomyDiagnostics {
	@Test
	void printGrowth() {
		Assumptions.assumeTrue(System.getenv("HEARTH_DIAG") != null);
		for (String terrain : new String[] {"flat", "hills", "river"}) {
			SimSettlement s = TestSupport.founded(terrain, 3);
			System.out.println("== " + terrain + ": " + s.land().sites().size() + " sites, territory " + s.land().territory());
			for (SimSite site : s.land().sites()) {
				System.out.println("   " + site);
			}
			for (int d = 1; d <= 240; d++) {
				SettlementClock.advance(s, d * SimRules.DAY);
				if (d % 30 == 0) {
					Map<JobType, Integer> jobs = new EnumMap<>(JobType.class);
					for (Resident r : s.residents()) {
						jobs.merge(r.job, 1, Integer::sum);
					}
					StringBuilder stock = new StringBuilder();
					for (Goods g : Goods.VALUES) {
						int n = s.stockpile().get(g);
						if (n > 0) {
							stock.append(g.getSerializedName()).append('=').append(n).append(' ');
						}
					}
					System.out.printf("day %d: pop %d beds %d tier %s cap %d prosp %.2f mood %d plots %s treasury %d%n   jobs %s%n   stock %s%n", d,
						s.population(), s.beds(), s.tier(), s.land().capacity(), s.land().prosperity(), s.land().mood(), s.plotCounts(), s.treasury(),
						jobs, stock);
				}
			}
		}
	}
}
