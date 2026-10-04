package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.economy.purse.StationRule;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class StationRuleTest {
	private static final EconomyConfig.Station CFG = EconomyConfig.Station.DEFAULTS;
	private static final ToDoubleFunction<Station> WAGE = EconomyConfig.Households.DEFAULTS::wage;

	private static Station next(Station current, Station base, double perHead, int hunger, boolean gentry) {
		return StationRule.next(current, base, perHead, hunger, gentry, WAGE, CFG);
	}

	@Test
	void savingsLiftAHouseholdOneRung() {
		double artisanWage = WAGE.applyAsDouble(Station.ARTISAN);
		assertEquals(Station.PEASANT, next(Station.PEASANT, Station.PEASANT, CFG.riseDays() * artisanWage - 1, 0, false), "not yet");
		assertEquals(Station.ARTISAN, next(Station.PEASANT, Station.PEASANT, CFG.riseDays() * artisanWage, 0, false), "a prosperous farmer");
	}

	@Test
	void nobodyRisesMoreThanOneRungAboveTheirTrade() {
		assertEquals(Station.ARTISAN, next(Station.ARTISAN, Station.PEASANT, 1e9, 0, true));
	}

	@Test
	void gentryOnlyInATownOrCity() {
		assertEquals(Station.MERCHANT, next(Station.MERCHANT, Station.MERCHANT, 1e9, 0, false));
		assertEquals(Station.GENTRY, next(Station.MERCHANT, Station.MERCHANT, 1e9, 0, true));
	}

	@Test
	void hungerAndAnEmptyPurseBringAHouseholdDown() {
		assertEquals(Station.ARTISAN, next(Station.ARTISAN, Station.ARTISAN, 0, CFG.hungerDays() - 1, false), "not hungry long enough");
		assertEquals(Station.PEASANT, next(Station.ARTISAN, Station.ARTISAN, 0, CFG.hungerDays(), false));
		assertEquals(Station.POOR, next(Station.PEASANT, Station.ARTISAN, 0, CFG.hungerDays(), false), "ruin goes below their trade");
		assertEquals(Station.POOR, next(Station.POOR, Station.ARTISAN, 0, 99, false), "nothing below poor");
	}

	@Test
	void hungryHouseholdsDoNotRise() {
		assertEquals(Station.PEASANT, next(Station.PEASANT, Station.PEASANT, 1e9, 1, false));
	}

	@Test
	void officesHoldTheirStation() {
		assertEquals(Station.CLERGY, next(Station.CLERGY, Station.CLERGY, 0, 99, false));
		assertEquals(Station.OFFICIAL, next(Station.OFFICIAL, Station.OFFICIAL, 1e9, 0, true));
		assertEquals(Station.LORD, next(Station.LORD, Station.LORD, 0, 0, false));
	}

	@Test
	void aChangeOfTradeResetsTheStation() {
		assertEquals(Station.PEASANT, next(Station.CLERGY, Station.PEASANT, 0, 0, false), "a priest who took up farming");
		assertEquals(Station.PEASANT, next(Station.GENTRY, Station.POOR, 1e9, 0, true), "a labourer cannot be gentry");
	}
}
