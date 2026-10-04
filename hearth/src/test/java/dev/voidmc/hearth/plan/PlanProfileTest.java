package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.testing.MiniSim;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * A profiling tool, not a test: with {@code HEARTH_PLAN_PROFILE=1} it grows a town as the performance test does, then
 * traces every need of the calls that follow, so the slow ones can be found.
 */
class PlanProfileTest {
	@Test
	void profile() {
		if (System.getenv("HEARTH_PLAN_PROFILE") == null) {
			return;
		}
		LayoutType layout = LayoutType.valueOf(System.getenv().getOrDefault("HEARTH_PLAN_LAYOUT", "CROSSROADS"));
		LayoutChooser.FORCED.set(layout);
		MiniSim sim;
		try {
			sim = Plans.grow(Culture.TEMPERATE, System.getenv().getOrDefault("HEARTH_PLAN_TERRAIN", "hills"), 51, 240);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		RandomSource random = RandomSource.create(7);
		Growth.trace = true;
		for (int i = 0; i < 120; i++) {
			long t0 = System.nanoTime();
			SettlementPlanner.GrowthProject p = Plans.PLANNER.next(sim.settlement, sim.terrain, random);
			long dt = System.nanoTime() - t0;
			System.out.printf("call %d: %.2f ms %s%n", i, dt / 1e6, p == null ? "-" : p.reason());
			if (p != null) {
				sim.settlement.addPlot(p.spec());
			}
		}
		Growth.trace = false;
	}
}
