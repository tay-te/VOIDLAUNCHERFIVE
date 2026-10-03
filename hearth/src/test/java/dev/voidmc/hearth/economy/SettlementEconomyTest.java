package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.daily.SettlementEconomy;
import dev.voidmc.hearth.economy.market.MarketLedger;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SettlementEconomyTest {
	private static SimpleSettlement town(int people, JobType job) {
		SimpleSettlement s = new SimpleSettlement(7, "Testford", Culture.TEMPERATE, BlockPos.ZERO, 0);
		for (int i = 0; i < people; i++) {
			s.addResident(new Resident(new UUID(i, 9), "A" + i, "B", false, AgeStage.ADULT, -5000, i == 0 ? job : JobType.FARMER, Resident.NO_PLOT,
				Resident.NO_PLOT, i, new CompoundTag()));
		}
		return s;
	}

	@Test
	void treasuryStaysBounded() {
		SimpleSettlement s = town(40, JobType.FARMER);
		s.deposit(100);
		for (int day = 0; day < 3000; day++) {
			SettlementEconomy.dayPassed(s, day);
		}
		long t = s.treasury();
		assertTrue(t > 100, "a working town saves");
		assertTrue(t < 100_000, "upkeep keeps it bounded: " + t);
	}

	@Test
	void sameDayIsCountedOnce() {
		SimpleSettlement s = town(10, JobType.FARMER);
		SettlementEconomy.dayPassed(s, 5);
		long t = s.treasury();
		SettlementEconomy.dayPassed(s, 5);
		assertEquals(t, s.treasury());
	}

	@Test
	void theMintStrikesMetalIntoCoin() {
		SimpleSettlement s = town(10, JobType.MINTER);
		s.stockpile().add(Goods.METAL, 9000);
		SettlementEconomy.dayPassed(s, 1);
		MarketLedger ledger = s.data(EconomyModule.LEDGER);
		assertTrue(ledger.books.minted() > 0);
		assertTrue(s.stockpile().get(Goods.METAL) < 9000);
	}

	@Test
	void noMintWithoutAMinter() {
		SimpleSettlement s = town(10, JobType.FARMER);
		s.stockpile().add(Goods.METAL, 9000);
		SettlementEconomy.dayPassed(s, 1);
		assertEquals(0, s.data(EconomyModule.LEDGER).books.minted());
		assertEquals(9000, s.stockpile().get(Goods.METAL));
	}

	@Test
	void driftStaysWithinItsLimit() {
		SimpleSettlement s = town(5, JobType.FARMER);
		double limit = EconomyConfig.Pricing.DEFAULTS.driftLimit();
		for (int day = 0; day < 2000; day++) {
			SettlementEconomy.dayPassed(s, day);
			for (float d : s.data(EconomyModule.LEDGER).drift) {
				assertTrue(Math.abs(d) <= limit + 1e-6);
			}
		}
	}

	@Test
	void buildersNeedsAreRememberedThenForgotten() {
		SimpleSettlement s = town(5, JobType.BUILDER);
		var plot = s.addPlot(new PlotSpec(Hearth.id("civic/chapel"), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 13, Direction.NORTH, 20, 1,
			new CompoundTag()));
		SettlementEconomy.constructionStalled(s, plot, Goods.STONE, 300, 10);
		MarketLedger ledger = s.data(EconomyModule.LEDGER);
		assertEquals(300, ledger.need(Goods.STONE));
		assertEquals("civic/chapel", ledger.needFor[Goods.STONE.ordinal()]);
		SettlementEconomy.dayPassed(s, 11);
		assertEquals(300, ledger.need(Goods.STONE), "still short");
		s.stockpile().add(Goods.STONE, 400);
		SettlementEconomy.dayPassed(s, 12);
		assertEquals(0, ledger.need(Goods.STONE), "in store now: forgotten");
	}

	@Test
	void useIsLearnedFromStockFallingButNotFromTrade() {
		SimpleSettlement s = town(5, JobType.FARMER);
		s.stockpile().add(Goods.FOOD, 5000);
		MarketLedger ledger = s.data(EconomyModule.LEDGER);
		SettlementEconomy.dayPassed(s, 0);
		for (int day = 1; day < 40; day++) {
			s.stockpile().take(Goods.FOOD, 50);
			SettlementEconomy.dayPassed(s, day);
		}
		assertEquals(50, ledger.use[Goods.FOOD.ordinal()], 2.0);
		// a player buying 1000 food is not the town eating it
		s.stockpile().take(Goods.FOOD, 1000);
		ledger.traded[Goods.FOOD.ordinal()] -= 1000;
		SettlementEconomy.dayPassed(s, 40);
		assertEquals(50 * 0.8, ledger.use[Goods.FOOD.ordinal()], 2.0);
	}
}
