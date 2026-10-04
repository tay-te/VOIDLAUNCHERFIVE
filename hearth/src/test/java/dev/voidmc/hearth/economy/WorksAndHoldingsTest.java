package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.holdings.BusinessMarket;
import dev.voidmc.hearth.economy.holdings.Holdings;
import dev.voidmc.hearth.economy.market.MarketLedger;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.purse.PurseEconomy;
import dev.voidmc.hearth.economy.purse.Purses;
import dev.voidmc.hearth.economy.works.Contracts;
import dev.voidmc.hearth.economy.works.Projects;
import dev.voidmc.hearth.economy.works.Works;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorksAndHoldingsTest {
	private static final UUID ALICE = new UUID(10, 1);
	private static final UUID BOB = new UUID(10, 2);

	@BeforeAll
	static void data() {
		Towns.install();
	}

	@AfterAll
	static void reset() {
		Towns.reset();
	}

	private static SimpleSettlement.SimplePlot chapel(SimpleSettlement s) {
		return s.addPlot(new PlotSpec(Hearth.id("civic/chapel"), Hearth.id("temperate"), 9, new BlockPos(300, 64, 0), 9, 15, Direction.NORTH, 20, 1,
			new CompoundTag()));
	}

	@Test
	void publicBuildingsAreFundableHomesAreNot() {
		SimpleSettlement s = Towns.village();
		assertTrue(Projects.fundable(chapel(s)));
		SimpleSettlement.SimplePlot house = s.addPlot(new PlotSpec(Hearth.id("house/cottage"), Hearth.id("temperate"), 1, BlockPos.ZERO, 9, 9,
			Direction.NORTH, 10, 1, new CompoundTag()));
		assertFalse(Projects.fundable(house));
		SimpleSettlement.SimplePlot done = chapel(s);
		done.state = PlotState.BUILT;
		assertFalse(Projects.fundable(done));
	}

	@Test
	void aFundBuysWhatAStalledBuildingLacksAndTheChangeGoesToTheTown() {
		SimpleSettlement s = Towns.village();
		SimpleSettlement.SimplePlot plot = chapel(s);
		Works works = Projects.works(s);
		Works.Project project = new Works.Project(plot.id(), 0);
		project.fund = 2000;
		project.contributions.put(ALICE, 2000L);
		works.projects.put(plot.id(), project);
		assertEquals(java.util.Set.of(plot.id()), Projects.funded(s), "the planner and sim see it as funded");

		s.stockpile().take(Goods.DRESSED_STONE, 6000);
		Projects.stalled(s, plot, Goods.DRESSED_STONE, 300);
		assertEquals(300, s.stockpile().get(Goods.DRESSED_STONE), "bought in at once");
		assertTrue(project.fund < 2000 && project.spent > 0);
		assertEquals(2000, project.fund + project.spent);

		long treasury = s.treasury();
		long left = project.fund;
		plot.state = PlotState.BUILT;
		Projects.daily(s, null);
		assertTrue(works.projects.isEmpty());
		assertEquals(treasury + left, s.treasury(), "the town keeps the change");
		assertTrue(Projects.funded(s).isEmpty());
	}

	@Test
	void aFundRunsOutRatherThanOverspending() {
		SimpleSettlement s = Towns.village();
		SimpleSettlement.SimplePlot plot = chapel(s);
		Works.Project project = new Works.Project(plot.id(), 0);
		project.fund = 30;
		project.contributions.put(ALICE, 30L);
		Projects.works(s).projects.put(plot.id(), project);
		s.stockpile().take(Goods.GLASS, 6000);
		Projects.stalled(s, plot, Goods.GLASS, 10_000);
		assertTrue(project.fund >= 0);
		assertTrue(s.stockpile().get(Goods.GLASS) > 0 && s.stockpile().get(Goods.GLASS) < 10_000);
	}

	@Test
	void anAbandonedProjectWithoutAServerGivesItsFundToTheTown() {
		SimpleSettlement s = Towns.village();
		SimpleSettlement.SimplePlot plot = chapel(s);
		Works.Project project = new Works.Project(plot.id(), 0);
		project.fund = 500;
		project.contributions.put(ALICE, 300L);
		project.contributions.put(BOB, 200L);
		Projects.works(s).projects.put(plot.id(), project);
		long t = s.treasury();
		Projects.abandoned(s, plot.id(), null);
		assertEquals(t + 500, s.treasury());
		assertTrue(Projects.works(s).projects.isEmpty());
	}

	@Test
	void scarceGoodsAreOfferedUnderContractIfTheTownCanPay() {
		SimpleSettlement s = Towns.village();
		s.stockpile().take(Goods.CANDLES, 6000);
		MarketPricer pricer = new MarketPricer(s);
		Contracts.daily(s, pricer, 5);
		Works works = Projects.works(s);
		Works.Contract c = works.contracts.stream().filter(k -> k.goods == Goods.CANDLES).findFirst().orElseThrow();
		assertTrue(c.units >= 32 && c.units % 16 == 0);
		assertTrue(c.total > 0 && c.total <= s.treasury());
		assertTrue(works.contracts.size() <= EconomyConfig.Works.DEFAULTS.maxContracts());

		SimpleSettlement broke = Towns.village();
		broke.withdraw(broke.treasury());
		broke.stockpile().take(Goods.CANDLES, 6000);
		Contracts.daily(broke, new MarketPricer(broke), 5);
		assertTrue(Projects.works(broke).contracts.isEmpty(), "a town that could not pay does not offer");
	}

	@Test
	void aLapsedContractReturnsItsEscrowAndCostsStanding() {
		SimpleSettlement s = Towns.village();
		Works works = Projects.works(s);
		Works.Contract c = new Works.Contract(works.nextContract++, Goods.ORE, 64, 400, 0);
		works.contracts.add(c);
		s.withdraw(400);
		c.escrow = 400;
		c.taker = ALICE;
		c.due = 8;
		c.delivered = 16;
		c.paid = c.earned();
		c.escrow -= c.paid;
		assertEquals(100, c.paid, "a quarter delivered, a quarter paid");
		long t = s.treasury();
		Contracts.daily(s, new MarketPricer(s), 9);
		assertTrue(works.contracts.stream().noneMatch(k -> k.id == c.id));
		assertEquals(t + 300, s.treasury(), "the unspent escrow comes back");
		assertEquals(EconomyConfig.Works.DEFAULTS.failWeight(), s.reputation(ALICE), "and the town remembers");
	}

	@Test
	void anOwnedBusinessPaysItsOwnerWhatItsWorkersEarnAboveTheirWages() {
		SimpleSettlement s = Towns.village();
		SimpleSettlement.SimplePlot smithy = Towns.built(s, "work/smithy", 60);
		Resident smith = Towns.resident(s, 700, JobType.SMITH, AgeStage.ADULT, Resident.NO_PLOT, smithy.id());
		Purses.markSeeded(smith);
		Holdings holdings = BusinessMarket.holdings(s);
		holdings.byPlot.put(smithy.id(), new Holdings.Holding(smithy.id(), ALICE, 0));
		long treasury = s.treasury();
		for (long day = 0; day < 10; day++) {
			PurseEconomy.daily(s, new MarketPricer(s), day);
			BusinessMarket.settle(s, null);
		}
		Holdings.Holding h = holdings.get(smithy.id());
		assertTrue(h.pending > 0, "income waits for the bank: " + h.pending);
		assertTrue(Purses.get(smith) > 0, "the smith is still paid");
		double perDay = BusinessMarket.expectedIncome(s, smithy, 1);
		assertTrue(perDay > 0);
		assertEquals(ALICE, BusinessMarket.owner(s, smithy.id()));
		assertTrue(treasury > 0);

		smithy.state = PlotState.PLANNED; // torn down: the holding ends
		BusinessMarket.settle(s, null);
		assertNull(holdings.get(smithy.id()));
	}

	@Test
	void businessesAreWorkplacesNotHomes() {
		assertTrue(BusinessMarket.isBusiness("work/smithy"));
		assertTrue(BusinessMarket.isBusiness("extract/quarry_pit"));
		assertTrue(BusinessMarket.isBusiness("farm/orchard"));
		assertTrue(BusinessMarket.isBusiness("commerce/inn"));
		assertFalse(BusinessMarket.isBusiness("house/cottage"));
		assertFalse(BusinessMarket.isBusiness("commerce/bank"));
		assertFalse(BusinessMarket.isBusiness("civic/chapel"));
	}

	@Test
	void servicePricesFollowTownSizeAndStanding() {
		assertEquals(6, Tariff.price(6, SettlementTier.VILLAGE, 0));
		assertTrue(Tariff.price(6, SettlementTier.CITY, 0) > Tariff.price(6, SettlementTier.HAMLET, 0));
		assertTrue(Tariff.price(100, SettlementTier.VILLAGE, 100) < Tariff.price(100, SettlementTier.VILLAGE, -100));
		assertEquals(1, Tariff.price(0.1, SettlementTier.CAMP, 100), "never free");
		SimpleSettlement s = Towns.village();
		assertTrue(Tariff.price(s, Tariff.INN_BED, null) > 0);
		assertEquals(0, Tariff.price(s, "no_such_service", null));
	}

	@Test
	void worksHoldingsAndBooksSurviveSaving() {
		Works w = new Works();
		Works.Project p = new Works.Project(4, 2);
		p.fund = 90;
		p.missing[Goods.BRICK.ordinal()] = 12;
		p.contributions.put(ALICE, 90L);
		w.projects.put(4, p);
		Works.Contract c = new Works.Contract(7, Goods.SALT, 48, 300, 3);
		c.taker = BOB;
		w.contracts.add(c);
		w.nextContract = 8;
		Works wb = Works.CODEC.parse(JsonOps.INSTANCE, Works.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow()).getOrThrow();
		assertEquals(90, wb.projects.get(4).fund);
		assertEquals(12, wb.projects.get(4).missing[Goods.BRICK.ordinal()]);
		assertEquals(BOB, wb.contract(7).taker);
		assertEquals(Goods.SALT, wb.contract(7).goods);
		assertEquals(8, wb.nextContract);

		Holdings h = new Holdings();
		Holdings.Holding hold = new Holdings.Holding(3, ALICE, 5);
		hold.pending = 17;
		hold.fraction = 0.25;
		h.byPlot.put(3, hold);
		Holdings hb = Holdings.CODEC.parse(JsonOps.INSTANCE, Holdings.CODEC.encodeStart(JsonOps.INSTANCE, h).getOrThrow()).getOrThrow();
		assertEquals(17, hb.get(3).pending);
		assertEquals(ALICE, hb.owner(3));

		MarketLedger.Books b = new MarketLedger.Books(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		assertEquals(b, MarketLedger.Books.CODEC.parse(JsonOps.INSTANCE, MarketLedger.Books.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow())
			.getOrThrow());
	}
}
