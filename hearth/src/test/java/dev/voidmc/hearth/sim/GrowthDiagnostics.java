package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Prints a settlement's growth curve (run with HEARTH_GROWTH=terrain in the environment); asserts nothing. */
class GrowthDiagnostics {
	@Test
	void printGrowth() {
		if (System.getenv("HEARTH_GROWTH") == null) {
			return;
		}
		if (System.getenv("HEARTH_GROWTH_REAL") != null) {
			// the real planner and the dev catalogue, as a dev server runs them before the build module lands
			TestSupport.boot();
			TestSupport.useRealPlanner();
		}
		SimSettlement s = TestSupport.founded(System.getenv().getOrDefault("HEARTH_GROWTH", "flat"), 1);
		for (int d = 1; d <= Integer.getInteger("hearth.test.days", 200); d++) {
			long t0 = System.nanoTime();
			SettlementClock.advance(s, d * SimRules.DAY);
			long dt = System.nanoTime() - t0;
			if (d % Integer.getInteger("hearth.test.every", 10) == 0 || d < 6) {
				Map<JobType, Integer> jobs = new EnumMap<>(JobType.class);
				for (Resident r : s.residents()) {
					jobs.merge(r.job, 1, Integer::sum);
				}
				StringBuilder active = new StringBuilder();
				for (SimPlot p : s.plots()) {
					if (p.isActive()) {
						active.append(p.spec().archetype().getPath()).append('@').append(p.stage()).append(':').append(p.step())
							.append(p.stalledOn() != null ? "!" + p.stalledOn().getSerializedName() : "").append(' ');
					}
				}
				System.out.printf("day %3d: %3d souls, %3d beds, %s %s, plots %s, active [%s], stores %s, %.3f ms%n", d, s.population(), s.beds(),
					s.tier().getSerializedName(), jobs, s.plotCounts(), active, s.stockpile(), dt / 1e6);
			}
		}
	}
}
