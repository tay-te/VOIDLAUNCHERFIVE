package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.Arrays;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * {@code next} must stay within a few milliseconds even for a town of 150 (ARCHITECTURE.md §5.1, §12): it runs on the
 * server thread and during world generation. Grows towns past 150 folk, then times further calls.
 */
class PlanPerformanceTest {
	@Test
	void nextForATownOf150() {
		for (LayoutType layout : new LayoutType[]{LayoutType.CROSSROADS, LayoutType.WALLED, LayoutType.HILLSIDE}) {
			LayoutChooser.FORCED.set(layout);
			MiniSim sim;
			try {
				sim = Plans.grow(layout == LayoutType.HILLSIDE ? Culture.MEDITERRANEAN : Culture.TEMPERATE,
					layout == LayoutType.HILLSIDE ? "slope" : "hills", 51, 240);
			} finally {
				LayoutChooser.FORCED.remove();
			}
			assertTrue(sim.settlement.population() >= 150, layout + " grew to " + sim.settlement.population());
			RandomSource random = RandomSource.create(7);
			int n = 120;
			long[] times = new long[n];
			for (int i = 0; i < n; i++) {
				long t0 = System.nanoTime();
				SettlementPlanner.GrowthProject p = Plans.PLANNER.next(sim.settlement, sim.terrain, random);
				times[i] = System.nanoTime() - t0;
				if (p != null) {
					sim.settlement.addPlot(p.spec());
				}
			}
			Arrays.sort(times);
			double median = times[n / 2] / 1e6;
			double p90 = times[n * 9 / 10] / 1e6;
			double mean = Arrays.stream(times).average().orElse(0) / 1e6;
			System.out.printf("%s, %d folk, %d plots: next median %.2f ms, p90 %.2f ms, mean %.2f ms, max %.2f ms%n", layout,
				sim.settlement.population(), sim.settlement.plots().size(), median, p90, mean, times[n - 1] / 1e6);
			assertTrue(median < 4, layout + ": median " + median + " ms");
			assertTrue(p90 < 12, layout + ": p90 " + p90 + " ms");
		}
	}
}
