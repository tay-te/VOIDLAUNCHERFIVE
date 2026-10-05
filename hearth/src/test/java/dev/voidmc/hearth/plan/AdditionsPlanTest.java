package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.testing.MiniSim;
import org.junit.jupiter.api.Test;

/**
 * Houses that grow with their households (docs/buildings/age.md): new houses with a yard are planned with
 * {@code additions: 0}, and the planner counts additions up on the houses its households fill, in place (the same
 * house, seed and ground).
 */
class AdditionsPlanTest {
	@Test
	void housesAreBuiltToGrowAndDo() {
		MiniSim sim = Plans.grow(Culture.TEMPERATE, "hills", 61, 260);
		int growing = 0;
		int grown = 0;
		for (PlotView p : sim.settlement.plots()) {
			if (p.spec().params().contains("additions")) {
				growing++;
				if (p.spec().params().getIntOr("additions", 0) > 0) {
					grown++;
				}
			}
		}
		assertTrue(growing > 0, "new houses with a yard are built to grow");
		assertTrue(grown > 0, "households that fill their houses add to them: " + growing + " growing");
	}
}
