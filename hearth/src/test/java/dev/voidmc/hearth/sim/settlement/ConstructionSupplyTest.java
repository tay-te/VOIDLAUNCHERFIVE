package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.sim.TestSupport;
import org.junit.jupiter.api.Test;

/** Where a young town's building goods come from when it has none (requests/w5-tour.md §4). */
class ConstructionSupplyTest {
	@Test
	void idleBuildersFetchMaterials() {
		// every stage waits for goods nobody has: the builders' day goes on logs and stone from the town's land instead
		SimSettlement s = TestSupport.founded("flat", 15);
		for (Goods g : Goods.VALUES) {
			s.stockpile().take(g, s.stockpile().get(g));
		}
		for (SimPlot p : s.plots()) {
			if (p.isActive() && !p.funded()) {
				// (not the founders' own: those came provisioned)
				p.reason = "test";
			}
		}
		for (int h = 1; h <= 12; h++) {
			SettlementClock.advance(s, h * 1000L);
		}
		int logs = s.stockpile().get(Goods.LOGS) + s.stockpile().get(Goods.WOOD)
			+ s.stockpile().get(Goods.STONE) + s.stockpile().get(Goods.DRESSED_STONE);
		assertTrue(logs > 20, "fetched " + logs);
	}


	@Test
	void aTownBartersWhatItHasInPlenty() {
		// no coin, a granary of grain and nothing else: the waiting stage's goods come in barter for the grain
		SimSettlement s = TestSupport.founded("flat", 16);
		for (Goods g : Goods.VALUES) {
			s.stockpile().take(g, s.stockpile().get(g));
		}
		s.withdraw(s.treasury());
		for (SimPlot p : s.plots()) {
			if (p.isActive() && !p.funded()) {
				p.reason = "test";
			}
		}
		s.stockpile().add(Goods.GRAIN, 6000);
		dev.voidmc.hearth.sim.model.SimContext c = s.ctx();
		var r = c.rules();
		// one day: the stage stalls, and at the day's end passing traders take grain for what it lacks
		SettlementClock.advance(s, dev.voidmc.hearth.sim.model.SimRules.DAY);
		int grain = s.stockpile().get(Goods.GRAIN);
		assertTrue(grain < 6000 - s.population() * r.foodAdult * 2, "grain bartered: " + grain + " left");
	}
}
