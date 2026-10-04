package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.market.StolenGoods;
import dev.voidmc.hearth.economy.market.TradeCounter;
import dev.voidmc.hearth.economy.price.MarketPricer;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.ShopBook;
import dev.voidmc.hearth.economy.shop.Shops;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShopsTest {
	@BeforeAll
	static void data() {
		Towns.install();
	}

	@AfterAll
	static void reset() {
		Towns.reset();
	}

	private static Shop shop(SimpleSettlement s, String kind) {
		return Shops.book(s).shops.values().stream().filter(sh -> sh.kind.equals(kind)).findFirst().orElseThrow();
	}

	@Test
	void shopBuildingsOpenWithTheirWorkersAsKeepers() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		ShopBook book = Shops.book(s);
		assertEquals(4, book.shops.size(), "stall, bakery, tailor, inn");
		Shop bakery = shop(s, "work/bakery");
		Resident baker = s.residents().stream().filter(r -> r.job == JobType.BAKER).findFirst().orElseThrow();
		assertEquals(baker.id, bakery.keeper);
		assertTrue(bakery.deals(Goods.FOOD) && !bakery.deals(Goods.TOOLS));
		assertTrue(shop(s, "commerce/market_stall").general());
		assertTrue(bakery.till > 0, "its till was lent from the treasury");
		assertEquals(bakery, Shops.keptBy(s, baker.id, 0));
	}

	@Test
	void aTraderWithoutAShopBuildingKeepsAStallAndAFenceABackRoom() {
		SimpleSettlement s = Towns.village();
		Resident fence = Towns.resident(s, 500, JobType.FENCE, AgeStage.ADULT, Resident.NO_PLOT, Resident.NO_PLOT);
		Shops.sync(s, 0);
		Shop back = Shops.keptBy(s, fence.id, 0);
		assertNotNull(back);
		assertTrue(back.fence);
		assertTrue(back.kind.startsWith("job/"));
	}

	@Test
	void aShopWithNobodyAtWorkIsShutAndAGoneOneGivesBackItsStockAndTill() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		Shop tailor = shop(s, "work/tailor");
		Resident keeper = s.resident(tailor.keeper);
		s.removeResident(keeper.id);
		Shops.sync(s, 1);
		assertFalse(tailor.open(), "nobody works there: shut");
		tailor.stock[Goods.CLOTH.ordinal()] = 50;
		int clothBefore = s.stockpile().get(Goods.CLOTH);
		long treasury = s.treasury();
		long till = tailor.till;
		((SimpleSettlement.SimplePlot) s.plot(tailor.plot)).state = PlotState.PLANNED; // torn down for a rebuild
		Shops.sync(s, 2);
		assertNull(Shops.book(s).get(tailor.key));
		assertEquals(clothBefore + 50, s.stockpile().get(Goods.CLOTH));
		assertEquals(treasury + till, s.treasury());
	}

	@Test
	void restockingIsLimitedByTheTillAndByWhatTheTownCanSpare() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		Shop bakery = shop(s, "work/bakery");
		bakery.till = 5;
		MarketPricer pricer = new MarketPricer(s);
		Shops.daily(s, pricer, 0, (shop, bits) -> Shops.payKeeper(s, shop, bits));
		assertTrue(bakery.stock(Goods.FOOD) <= 5, "half of five bits buys only a few units: " + bakery.stock(Goods.FOOD));
		Shop stall = shop(s, "commerce/market_stall");
		assertTrue(stall.till >= 99, "a general shop keeps half its till to buy from players with: " + stall.till);

		SimpleSettlement poor = Towns.village();
		poor.stockpile().take(Goods.FOOD, 6000);
		Shops.sync(poor, 0);
		Shop b2 = shop(poor, "work/bakery");
		Shops.daily(poor, new MarketPricer(poor), 0, (shop, bits) -> Shops.payKeeper(poor, shop, bits));
		assertEquals(0, b2.stock(Goods.FOOD), "an empty store has nothing to spare");
	}

	@Test
	void aShopCannotBuyWhatItCannotPayFor() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		Shop tailor = shop(s, "work/tailor");
		tailor.till = 10;
		TradeCounter counter = new TradeCounter.ShopCounter(tailor);
		assertFalse(counter.bought(Goods.CLOTH, 4, 11), "eleven bits from a till of ten");
		assertEquals(10, tailor.till);
		assertTrue(counter.bought(Goods.CLOTH, 4, 10));
		assertEquals(0, tailor.till);
		assertEquals(4, tailor.stock(Goods.CLOTH));
		assertEquals(0, counter.room(Goods.TOOLS), "it does not buy what it does not deal in");
	}

	@Test
	void fullTillsPayTheirKeeperAndTakingsPayDues() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		Shop stall = shop(s, "commerce/market_stall");
		Resident keeper = s.resident(stall.keeper);
		stall.till = 10_000;
		stall.takings = 1000;
		long purse = dev.voidmc.hearth.economy.purse.Purses.get(keeper);
		long dues = Shops.daily(s, new MarketPricer(s), 1, (shop, bits) -> Shops.payKeeper(s, shop, bits));
		assertEquals(Math.round(1000 * EconomyConfig.Shops.DEFAULTS.dueRate()), dues);
		assertTrue(dev.voidmc.hearth.economy.purse.Purses.get(keeper) > purse, "the keeper took a share");
	}

	@Test
	void sournessLastsItsDays() {
		ShopBook book = new ShopBook();
		UUID p = new UUID(1, 2);
		book.setSour("p3", p, 10);
		assertTrue(book.sour("p3", p, 10));
		assertFalse(book.sour("p3", p, 11));
		assertFalse(book.sour("p4", p, 10));
		book.pruneSour(11);
		assertTrue(book.soured.isEmpty());
	}

	@Test
	void theShopBookSurvivesSaving() {
		SimpleSettlement s = Towns.village();
		Shops.sync(s, 0);
		ShopBook book = Shops.book(s);
		shop(s, "work/tailor").stock[Goods.DYE.ordinal()] = 7;
		book.setSour("town", new UUID(3, 4), 99);
		var json = ShopBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		ShopBook back = ShopBook.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(book.shops.keySet(), back.shops.keySet());
		Shop t = back.shops.values().stream().filter(sh -> sh.kind.equals("work/tailor")).findFirst().orElseThrow();
		assertEquals(7, t.stock(Goods.DYE));
		assertEquals(shop(s, "work/tailor").keeper, t.keeper);
		assertTrue(back.sour("town", new UUID(3, 4), 50));
	}

	@Test
	void stolenGoodsRules() {
		EconomyConfig.Fence cfg = EconomyConfig.Fence.DEFAULTS;
		assertEquals(StolenGoods.Verdict.CLEAN, StolenGoods.judge(false, 0, 5, false, cfg));
		assertEquals(StolenGoods.Verdict.REFUSED, StolenGoods.judge(true, 5, 5, false, cfg), "stolen here");
		assertEquals(StolenGoods.Verdict.CLEAN, StolenGoods.judge(true, 7, 5, false, cfg), "stolen elsewhere: they don't know");
		assertEquals(StolenGoods.Verdict.FENCED, StolenGoods.judge(true, 5, 5, true, cfg), "a fence buys it anywhere");
		EconomyConfig.Fence strict = new EconomyConfig.Fence(true, true, 0.4);
		assertEquals(StolenGoods.Verdict.REFUSED, StolenGoods.judge(true, 7, 5, false, strict));
		EconomyConfig.Fence lax = new EconomyConfig.Fence(false, false, 0.4);
		assertEquals(StolenGoods.Verdict.CLEAN, StolenGoods.judge(true, 5, 5, false, lax));
	}
}
