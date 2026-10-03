package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.economy.price.PriceModel;
import org.junit.jupiter.api.Test;

class PriceModelTest {
	private final PriceModel model = PriceModel.of(EconomyConfig.Pricing.DEFAULTS);

	@Test
	void scarcerIsDearer() {
		double target = 1000;
		double last = Double.MAX_VALUE;
		for (int stock = 0; stock <= 10_000; stock += 25) {
			double s = this.model.scarcity(stock, target);
			assertTrue(s <= last + 1e-12, "scarcity never rises with stock");
			last = s;
		}
		assertEquals(1.0, this.model.scarcity(1000, target), 1e-9, "at target, base price");
		assertTrue(this.model.scarcity(0, target) > 2.5, "an empty store pays near the ceiling");
		assertEquals(EconomyConfig.Pricing.DEFAULTS.scarcityMin(), this.model.scarcity(1_000_000, target), 1e-9);
	}

	@Test
	void buyAlwaysAboveSellAtAnyStanding() {
		for (int rep = -100; rep <= 100; rep += 5) {
			for (double mid = 0.01; mid < 500; mid *= 1.7) {
				double buy = this.model.buy(mid, rep);
				assertTrue(this.model.sell(mid, rep, false) < buy);
				assertTrue(this.model.sell(mid, rep, true) < buy, "even with the builders' premium");
			}
		}
	}

	@Test
	void standingHelps() {
		double mid = 10;
		assertTrue(this.model.buy(mid, 80) < this.model.buy(mid, 0));
		assertTrue(this.model.buy(mid, -80) > this.model.buy(mid, 0));
		assertTrue(this.model.sell(mid, 80, false) > this.model.sell(mid, 0, false));
	}

	@Test
	void buildersPayMore() {
		assertTrue(this.model.sell(10, 0, true) > this.model.sell(10, 0, false));
	}

	@Test
	void cultureSpecialityMakesPlentifulGoodsCheaper() {
		assertTrue(this.model.culture(0.7) < 1.0);
		assertTrue(this.model.culture(1.3) > 1.0);
		PriceModel flat = new PriceModel(0.3, 0.8, 0.5, 3, 0.25, 0.3, 0.0);
		assertEquals(1.0, flat.culture(0.7), 1e-9, "culture effect 0: every town alike");
	}

	@Test
	void targetUsesConsumptionAndCapacity() {
		assertEquals(350, PriceModel.target(1000, 0, 0.35, 6), 1e-9);
		assertEquals(600, PriceModel.target(1000, 100, 0.35, 6), 1e-9);
		assertEquals(1000, PriceModel.target(1000, 1000, 0.35, 6), 1e-9, "never above capacity");
	}

	@Test
	void lotsKeepRoundingSmall() {
		assertEquals(32, PriceModel.lotSize(0.125, 64, 4), "planks by the 32");
		assertEquals(1, PriceModel.lotSize(6, 64, 4), "ingots singly");
		assertEquals(1, PriceModel.lotSize(0.1, 1, 4), "never more than a stack");
		assertEquals(5, PriceModel.lotPrice(4.2, 1, true), "the market rounds its price up");
		assertEquals(4, PriceModel.lotPrice(4.8, 1, false), "and what it pays down");
		assertEquals(1, PriceModel.lotPrice(0.01, 1, true), "nothing is free");
	}
}
