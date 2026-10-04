package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import org.junit.jupiter.api.Test;

/**
 * ARCHITECTURE.md §12: the abstract step of a town of about 150 stays well under 0.2 ms with the chains and the sites.
 * Grows a town on synthetic ground with the placeholder catalogue, then times ordinary hour-long steps (the dev harness
 * measures the same in game, with the real planner and Expanse terrain).
 */
class StepCostTest {
	@Test
	void anHourOfATownCostsMicroseconds() {
		SimSettlement s = TestSupport.founded("flat", 71);
		int day = 0;
		while (s.population() < 140 && day < 900) {
			day++;
			SettlementClock.advance(s, day * SimRules.DAY);
		}
		long t = s.simTick();
		// warm up, then measure
		for (int i = 0; i < 50; i++) {
			t += SimRules.HOUR;
			SettlementClock.advance(s, t);
		}
		int steps = 400;
		long start = System.nanoTime();
		for (int i = 0; i < steps; i++) {
			t += SimRules.HOUR;
			SettlementClock.advance(s, t);
		}
		double ms = (System.nanoTime() - start) / 1e6 / steps;
		System.out.printf("hearth step cost: %d souls, %d plots, %d sites: %.4f ms per hour-long step (day steps included)%n", s.population(),
			s.plots().size(), s.land().sites().size(), ms);
		// generous for a shared test machine; the harness's figure is the one reported
		assertTrue(ms < 1.0, ms + " ms per step");
	}
}
