package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.economy.loot.CoinLootTables;
import dev.voidmc.hearth.economy.property.PropertyMarket;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PropertyAndLootTest {
	private static final EconomyConfig.Property CFG = EconomyConfig.Property.DEFAULTS;

	@Test
	void propertyPrices() {
		long plot = PropertyMarket.price(80, 1, 0, SettlementTier.VILLAGE, false, 0, CFG);
		long house = PropertyMarket.price(80, 2, 1, SettlementTier.VILLAGE, true, 0, CFG);
		assertTrue(house > plot, "a house costs more than its land");
		assertTrue(PropertyMarket.price(80, 2, 1, SettlementTier.CITY, true, 0, CFG) > house, "city land is dear");
		assertTrue(PropertyMarket.price(120, 2, 1, SettlementTier.VILLAGE, true, 0, CFG) > house, "bigger costs more");
		assertTrue(PropertyMarket.price(80, 2, 1, SettlementTier.VILLAGE, true, 100, CFG) < house, "a hero gets a discount");
		assertEquals(Math.round(80 * CFG.landPerBlock()), plot);
	}

	@Test
	void lootCategories() {
		assertEquals("village", CoinLootTables.category(Identifier.withDefaultNamespace("chests/village/village_plains_house")));
		assertEquals("buried_treasure", CoinLootTables.category(Identifier.withDefaultNamespace("chests/buried_treasure")));
		assertEquals("expanse_lost", CoinLootTables.category(Identifier.fromNamespaceAndPath("expanse", "chests/micro/lost_chest")));
		assertEquals("expanse_hidden", CoinLootTables.category(Identifier.fromNamespaceAndPath("expanse", "chests/graveyard_hidden")));
		assertEquals("expanse", CoinLootTables.category(Identifier.fromNamespaceAndPath("expanse", "chests/caravanserai")));
		assertNull(CoinLootTables.category(Identifier.fromNamespaceAndPath("expanse", "chests/graveyard_archaeology")));
		assertNull(CoinLootTables.category(Identifier.withDefaultNamespace("entities/zombie")));
		for (String category : EconomyConfig.CoinLoot.DEFAULTS.keySet()) {
			EconomyConfig.CoinLoot l = EconomyConfig.DEFAULTS.loot(category);
			assertTrue(l.bitsMin() <= l.bitsMax() && l.marksMin() <= l.marksMax(), category);
		}
	}
}
