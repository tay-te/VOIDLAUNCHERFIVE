package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.purse.Households;
import dev.voidmc.hearth.economy.purse.Inheritance;
import dev.voidmc.hearth.economy.purse.PurseEconomy;
import dev.voidmc.hearth.economy.purse.Purses;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PurseEconomyTest {
	@BeforeAll
	static void data() {
		Towns.install();
	}

	@AfterAll
	static void reset() {
		Towns.reset();
	}

	@Test
	void adultsArriveWithSavingsChildrenWithNone() {
		SimpleSettlement s = Towns.village();
		PurseEconomy.daily(s, new MarketPricer(s), 1);
		for (Resident r : s.residents()) {
			assertTrue(Purses.seeded(r));
			if (r.age == AgeStage.CHILD) {
				assertEquals(0, Purses.get(r) + 0 * Purses.hunger(r), "children hold nothing of their own");
			}
		}
		Resident farmer = s.residents().stream().filter(r -> r.job == JobType.FARMER).findFirst().orElseThrow();
		assertTrue(Purses.get(farmer) > 0);
	}

	@Test
	void coinIsConservedBetweenPursesTreasuryAndTills() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		PurseEconomy.daily(s, new MarketPricer(s), 0); // seeds purses (new coin, once)
		for (long day = 1; day < 60; day++) {
			long before = Towns.coin(s);
			PurseEconomy.Day d = PurseEconomy.daily(s, new MarketPricer(s), day);
			assertEquals(0, d.seeded());
			assertEquals(before, Towns.coin(s), "day " + day + ": wages, taxes, spending and tithes only move coin");
		}
	}

	@Test
	void folkBuyTheirFoodFromTheBakerAndComfortsFromTheShelves() {
		SimpleSettlement s = Towns.village();
		SettlementEconomy.dayPassed(s, 0);
		ShopBook book = s.data(EconomyModule.SHOPS);
		Shop tailor = book.shops.values().stream().filter(sh -> sh.kind.equals("work/tailor")).findFirst().orElseThrow();
		Shop bakery = book.shops.values().stream().filter(sh -> sh.kind.equals("work/bakery")).findFirst().orElseThrow();
		assertTrue(tailor.stock(Goods.CLOTH) > 0, "the tailor stocked up from the stores");
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.CHILD) {
				Purses.set(r, 400); // folk with coin to spare
			}
		}
		long bakerTill = bakery.till;
		int clothOnShelves = 0;
		for (Shop shop : book.shops.values()) {
			clothOnShelves += shop.stock(Goods.CLOTH);
		}
		SettlementEconomy.dayPassed(s, 1);
		float clothSold = 0;
		for (Shop shop : book.shops.values()) {
			clothSold += shop.sales[Goods.CLOTH.ordinal()];
		}
		assertTrue(clothSold > 0, "folk bought cloth off the shelves (" + clothOnShelves + " were there)");
		assertTrue(bakery.till != bakerTill, "the baker took coin for bread");
		assertTrue(s.data(EconomyModule.LEDGER).books.spent() > 0, "folk spent");
	}

	@Test
	void anEmptyPurseGoesHungryAndFallsInStation() {
		SimpleSettlement s = new SimpleSettlement(3, "Pooring", dev.voidmc.hearth.api.Culture.TEMPERATE, net.minecraft.core.BlockPos.ZERO, 0);
		int home = Towns.built(s, "house/hut", 0).id();
		Resident r = Towns.resident(s, 1, JobType.CARPENTER, AgeStage.ADULT, home, Resident.NO_PLOT);
		Purses.markSeeded(r);
		s.stockpile().add(Goods.FOOD, 1000);
		// a treasury with nothing to pay wages: nothing comes in
		for (long day = 0; day < 40; day++) {
			PurseEconomy.daily(s, new MarketPricer(s), day);
		}
		assertTrue(Purses.hunger(r) >= EconomyConfig.Station.DEFAULTS.hungerDays());
		assertTrue(r.station.ordinal() < Station.ARTISAN.ordinal() || r.station == Station.POOR, "ruin brought them down: " + r.station);
	}

	@Test
	void prosperityLiftsAHousehold() {
		SimpleSettlement s = new SimpleSettlement(4, "Richford", dev.voidmc.hearth.api.Culture.TEMPERATE, net.minecraft.core.BlockPos.ZERO, 0);
		int home = Towns.built(s, "house/cottage", 0).id();
		Resident r = Towns.resident(s, 1, JobType.FARMER, AgeStage.ADULT, home, Resident.NO_PLOT);
		Purses.markSeeded(r);
		Purses.set(r, 100_000);
		s.deposit(100_000);
		PurseEconomy.daily(s, new MarketPricer(s), 0);
		assertEquals(Station.ARTISAN, r.station, "a rich farmer lives like an artisan");
		PurseEconomy.daily(s, new MarketPricer(s), 1);
		assertEquals(Station.ARTISAN, r.station, "and not higher: one rung above their trade");
	}

	@Test
	void theTreasuryAndPursesStayBoundedOverYears() {
		SimpleSettlement s = Towns.village();
		for (long day = 0; day < 1500; day++) {
			SettlementEconomy.dayPassed(s, day);
		}
		long purses = 0;
		for (Resident r : s.residents()) {
			purses += Purses.get(r);
		}
		assertTrue(s.treasury() > 0, "the town still has coin: " + s.treasury());
		assertTrue(s.treasury() < 1_000_000, "but not without bound: " + s.treasury());
		assertTrue(purses > 0 && purses < 1_000_000, "folk hold coin, boundedly: " + purses);
		assertTrue(s.data(EconomyModule.LEDGER).books.wages() > 0, "wages are still paid");
	}

	@Test
	void wealthIsCountedByHousehold() {
		SimpleSettlement s = Towns.village();
		PurseEconomy.daily(s, new MarketPricer(s), 0);
		Resident baker = s.residents().stream().filter(r -> r.job == JobType.BAKER).findFirst().orElseThrow();
		long sum = 0;
		for (Resident r : s.residents()) {
			if (r.home == baker.home) {
				sum += Purses.get(r);
			}
		}
		assertEquals(sum, Households.wealth(s, baker));
	}

	@Test
	void theDeadLeaveTheirPurseToTheirHousehold() {
		SimpleSettlement s = Towns.village();
		PurseEconomy.daily(s, new MarketPricer(s), 0);
		Resident baker = s.residents().stream().filter(r -> r.job == JobType.BAKER).findFirst().orElseThrow();
		long household = Households.wealth(s, baker);
		long purse = Purses.get(baker);
		s.removeResident(baker.id);
		Resident heir = Inheritance.settle(s, baker);
		assertNotNull(heir);
		assertEquals(household, Households.wealth(s, heir), "the household keeps it all");
		assertEquals(0, Purses.get(baker));
		assertTrue(purse > 0);

		Resident loner = Towns.resident(s, 999, JobType.FARMER, AgeStage.ADULT, Resident.NO_PLOT, Resident.NO_PLOT);
		Purses.set(loner, 50);
		long t = s.treasury();
		s.removeResident(loner.id);
		assertNull(Inheritance.settle(s, loner));
		assertEquals(t + 50, s.treasury(), "nobody to leave it to: the town has it");
	}

	@Test
	void basketsFollowStationAndConfig() {
		Map<Goods, Double> merchant = PurseEconomy.basket(Station.MERCHANT, EconomyConfig.Households.DEFAULTS.consumedGoods());
		assertTrue(merchant.containsKey(Goods.LUXURY));
		assertTrue(PurseEconomy.basket(Station.POOR, EconomyConfig.Households.DEFAULTS.consumedGoods()).keySet().equals(java.util.Set.of(Goods.CLOTH)));
		assertTrue(PurseEconomy.basket(Station.MERCHANT, java.util.List.of()).isEmpty(), "nothing consumed: nothing bought");
		assertTrue(!PurseEconomy.basket(Station.SCHOLAR, EconomyConfig.Households.DEFAULTS.consumedGoods()).containsKey(Goods.PAPER),
			"paper is not consumed by default");
	}

	@Test
	void stochasticRoundingKeepsTheAverage() {
		long sum = 0;
		for (int day = 0; day < 10_000; day++) {
			sum += PurseEconomy.round(0.3, 1, day, 42);
		}
		assertEquals(3000, sum, 150);
		assertEquals(PurseEconomy.round(2.7, 5, 9, 3), PurseEconomy.round(2.7, 5, 9, 3), "deterministic");
	}
}
