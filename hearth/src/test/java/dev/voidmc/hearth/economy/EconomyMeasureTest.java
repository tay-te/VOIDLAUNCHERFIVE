package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.purse.Purses;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Measurements of the daily economy (printed, loosely asserted): what a village's money does over two years, and what
 * a day costs for a town of 150 (ARCHITECTURE.md §12: the daily step runs in catch-up too, so it must be cheap).
 */
class EconomyMeasureTest {
	@BeforeAll
	static void data() {
		Towns.install();
	}

	@AfterAll
	static void reset() {
		Towns.reset();
	}

	@Test
	void aVillagesMoneyOverTwoYears() {
		SimpleSettlement s = Towns.village();
		StringBuilder out = new StringBuilder("day  treasury  purses  wages  taxes  spent  dues  hungry  stations\n");
		for (long day = 0; day <= 720; day++) {
			SettlementEconomy.dayPassed(s, day);
			if (day % 120 == 0) {
				long purses = 0;
				Map<Station, Integer> st = new EnumMap<>(Station.class);
				for (Resident r : s.residents()) {
					purses += Purses.get(r);
					st.merge(r.station, 1, Integer::sum);
				}
				MarketLedger.Books b = s.data(EconomyModule.LEDGER).books;
				out.append(String.format("%4d %9d %7d %6d %6d %6d %5d %7d  %s%n", day, s.treasury(), purses, b.wages(), b.taxes(), b.spent(), b.dues(),
					b.hungry(), st));
			}
		}
		System.out.println(out);
		assertTrue(s.treasury() > 0);
	}

	@Test
	void aDayForATownOf150() {
		SimpleSettlement s = Towns.village();
		for (int i = 0; i < 120; i++) {
			Towns.resident(s, 1000 + i, i % 5 == 0 ? JobType.CARPENTER : JobType.FARMER, AgeStage.ADULT, s.plots().iterator().next().id() + i % 8,
				Resident.NO_PLOT);
		}
		for (long day = 0; day < 200; day++) {
			SettlementEconomy.dayPassed(s, day); // warm up
		}
		long start = System.nanoTime();
		int days = 2000;
		for (long day = 200; day < 200 + days; day++) {
			SettlementEconomy.dayPassed(s, day);
		}
		double micros = (System.nanoTime() - start) / 1000.0 / days;
		System.out.printf("daily economy, %d residents, %d shops: %.1f µs a day%n", s.population(), s.data(EconomyModule.SHOPS).shops.size(), micros);
		assertTrue(micros < 5000, "a day costs " + micros + " µs");
	}
}
