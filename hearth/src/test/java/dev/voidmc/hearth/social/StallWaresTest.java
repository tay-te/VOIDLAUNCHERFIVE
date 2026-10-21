package dev.voidmc.hearth.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.quest.gen.GoodsItems;
import dev.voidmc.hearth.social.life.StallWares;
import dev.voidmc.hearth.testing.ItemBoot;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Stalls stocked with the town's real goods (docs/LIVING_WORLD_2.md §6.1, §6.8). */
class StallWaresTest {
	@BeforeAll
	static void boot() {
		ItemBoot.boot();
	}

	private static Map<Goods, Integer> stock(Object... pairs) {
		Map<Goods, Integer> m = new EnumMap<>(Goods.class);
		for (int i = 0; i < pairs.length; i += 2) {
			m.put((Goods) pairs[i], (Integer) pairs[i + 1]);
		}
		return m;
	}

	@Test
	void theWaresShownAreGoodsTheShopHoldsMostStockedFirst() {
		Map<Goods, Integer> s = stock(Goods.FOOD, 30, Goods.FISH, 10, Goods.CANDLES, 2, Goods.TOOLS, 0);
		List<Goods> shown = StallWares.shown(s, 6);
		assertEquals(6, shown.size(), "a full stall: six wares");
		for (Goods g : shown) {
			assertTrue(s.get(g) > 0, g + " is held");
		}
		assertFalse(shown.contains(Goods.TOOLS), "none of it on the shelves, none on the board");
		assertEquals(Goods.FOOD, shown.get(0), "most-stocked first");
		assertTrue(shown.indexOf(Goods.FISH) > shown.lastIndexOf(Goods.FOOD), "then the next");
		assertTrue(shown.contains(Goods.CANDLES), "every goods held gets a ware while there is room");
		long food = shown.stream().filter(g -> g == Goods.FOOD).count();
		assertTrue(food >= 3, "the bread takes most of the board: " + shown);
	}

	@Test
	void aShopSoldOutShowsEmptyBoardsAndRestockingFillsThem() {
		assertTrue(StallWares.shown(stock(), 6).isEmpty());
		assertTrue(StallWares.shown(stock(Goods.FOOD, 0, Goods.FISH, 0), 6).isEmpty());
		// a ware for every four units: the shelves filling
		assertEquals(1, StallWares.shown(stock(Goods.FOOD, 1), 6).size());
		assertEquals(2, StallWares.shown(stock(Goods.FOOD, 8), 6).size());
		assertEquals(5, StallWares.shown(stock(Goods.FOOD, 20), 6).size());
		assertEquals(6, StallWares.shown(stock(Goods.FOOD, 400), 6).size(), "never more than the stall takes");
		assertEquals(0, StallWares.shown(stock(Goods.FOOD, 40), 0).size());
	}

	@Test
	void theWaresAreTheCulturesOwnItemsForItsGoods() {
		Set<Identifier> choices = new HashSet<>();
		for (GoodsItems.Choice c : GoodsItems.choices(Culture.NORTHERN, Goods.FISH)) {
			choices.add(c.item());
		}
		for (long seed = 0; seed < 20; seed++) {
			ItemStack st = GoodsItems.SERVICE.sample(Goods.FISH, Culture.NORTHERN, seed);
			assertFalse(st.isEmpty());
			assertTrue(choices.contains(BuiltInRegistries.ITEM.getKey(st.getItem())), st + " is one of the north's fish");
		}
		// the same seed, the same ware: a stall keeps its wares
		assertEquals(GoodsItems.SERVICE.sample(Goods.FOOD, Culture.DESERT, 7).getItem(), GoodsItems.SERVICE.sample(Goods.FOOD, Culture.DESERT, 7).getItem());
		// every goods of every culture has a ware to show
		for (Culture c : Culture.values()) {
			for (Goods g : Goods.VALUES) {
				assertFalse(GoodsItems.SERVICE.sample(g, c, 3).isEmpty(), c + " " + g);
			}
		}
	}

	@Test
	void aMarketDayStallWithNoShopOfItsKeepersShowsTheTownsStores() {
		dev.voidmc.hearth.api.testing.SimpleSettlement s = new dev.voidmc.hearth.api.testing.SimpleSettlement(5, "Ashbrook", Culture.TEMPERATE,
			net.minecraft.core.BlockPos.ZERO, 0);
		s.stockpile().add(Goods.FOOD, 40);
		s.stockpile().add(Goods.FISH, 8);
		s.stockpile().add(Goods.STONE, 500);
		Map<Goods, Integer> stores = StallWares.stores(s);
		assertFalse(stores.containsKey(Goods.STONE), "no quarry stone on a market board");
		List<Goods> shown = StallWares.shown(stores, 6);
		assertEquals(Goods.FOOD, shown.get(0));
		for (Goods g : shown) {
			assertTrue(s.stockpile().get(g) > 0, g + " is in the stores");
		}
	}
}
