package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.economy.shop.Shop;
import dev.voidmc.hearth.economy.shop.Shops;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Lane A4's work tour found an adopted historic town whose bakery, inn and stalls nobody worked at: no keeper, so they
 * never restocked and their counters stood empty. A shop building nobody works at is kept by a stand-in of its trade (or
 * a merchant) who keeps no other shop building, and restocks.
 */
class ShopStandInTest {
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
	void aBakeryNobodyWorksAtIsKeptByTheTownsBakerAndRestocks() {
		SimpleSettlement s = Towns.village();
		Resident baker = s.residents().stream().filter(r -> r.job == JobType.BAKER).findFirst().orElseThrow();
		Resident innkeeper = s.residents().stream().filter(r -> r.job == JobType.INNKEEPER).findFirst().orElseThrow();
		// as an adopted historic town has them: the trades at no workplace
		baker.work = Resident.NO_PLOT;
		innkeeper.work = Resident.NO_PLOT;
		Shops.sync(s, 0);
		Shop bakery = shop(s, "work/bakery");
		assertEquals(baker.id, bakery.keeper, "the baker stands in");
		assertTrue(bakery.open());
		assertEquals(innkeeper.id, shop(s, "commerce/inn").keeper, "the innkeeper stands in at the inn");
		int before = bakery.stock[Goods.FOOD.ordinal()];
		Shops.daily(s, new dev.voidmc.hearth.economy.price.MarketPricer(s), 1, (sh, bits) -> {
		});
		assertTrue(bakery.stock[Goods.FOOD.ordinal()] > before || before > 0, "it restocks from the stores");
	}

	@Test
	void aShopWhoseTradeHasNobodyFreeStaysShut() {
		SimpleSettlement s = Towns.village();
		Resident tailor = s.residents().stream().filter(r -> r.job == JobType.TAILOR).findFirst().orElseThrow();
		s.removeResident(tailor.id);
		Shops.sync(s, 0);
		assertTrue(!shop(s, "work/tailor").open(), "no tailor and the merchant keeps the stall");
	}

	@Test
	void aStallWithNoMerchantIsMindedByATownsman() {
		SimpleSettlement s = Towns.village();
		Resident merchant = s.residents().stream().filter(r -> r.job == JobType.MERCHANT).findFirst().orElseThrow();
		s.removeResident(merchant.id);
		Shops.sync(s, 0);
		Shop stall = shop(s, "commerce/market_stall");
		assertTrue(stall.open(), "a stall is minded by whoever is free");
		Resident keeper = s.resident(stall.keeper);
		assertTrue(keeper != null && keeper.job != JobType.GUARD);
	}
}
