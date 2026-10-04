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
		if ("full".equals(System.getenv("HEARTH_DIAG"))) {
			TestSupport.useRealCatalogue();
		} else if ("real".equals(System.getenv("HEARTH_DIAG"))) {
			TestSupport.boot();
			TestSupport.useRealPlanner();
		}
		for (String terrain : new String[] {"flat", "hills", "river"}) {
			SimSettlement s = TestSupport.founded(terrain, 3);
			System.out.println("== " + terrain + ": " + s.land().sites().size() + " sites, territory " + s.land().territory());
			for (SimSite site : s.land().sites()) {
				System.out.println("   " + site);
			}
			for (int d = 1; d <= 300; d++) {
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
					for (SimPlot p : s.plots()) {
						if (p.isActive()) {
							PlotFacts f = s.facts(p);
							StringBuilder need = new StringBuilder();
							if (p.stage() < f.stageCount()) {
								int[] g = f.goods()[p.stage()];
								for (Goods x : Goods.VALUES) {
									if (g[x.ordinal()] > 0) {
										need.append(x.getSerializedName()).append('=').append(g[x.ordinal()]).append(' ');
									}
								}
							}
							System.out.printf("   plot %d %s stage %d/%d funded %s stalled %s held %s needs %s%n", p.id(), p.spec().archetype().getPath(),
								p.stage(), f.stageCount(), p.funded(), p.stalledOn(), p.heldBack, need);
						}
					}
				}
			}
		}
	}
}
