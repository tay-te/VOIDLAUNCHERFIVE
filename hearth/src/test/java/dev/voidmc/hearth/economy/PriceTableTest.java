package dev.voidmc.hearth.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.economy.price.PriceData;
import dev.voidmc.hearth.economy.price.PriceTable;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Base values and wares against the real item registry. A bare bootstrap binds neither tags nor item components, so the
 * rarity guess, wear and goods are covered in game (the dev pilot), not here.
 */
class PriceTableTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		double[] ones = new double[Goods.VALUES.length];
		java.util.Arrays.fill(ones, 1.0);
		PriceData data = new PriceData(Map.of(Identifier.withDefaultNamespace("bread"), 1.0, Identifier.withDefaultNamespace("iron_pickaxe"), 23.0),
			List.of(), ones, ones, 0.25, Map.of(Rarity.EPIC, 40.0),
			Map.of(Goods.WOOD, List.of("minecraft:oak_planks")),
			Map.of(Culture.KARST, new PriceData.CultureEntry(ones, Map.of(Goods.WOOD, List.of("expanse:not_installed|minecraft:bamboo_planks",
				"minecraft:bamboo_block")))));
		PriceTable.setData(data);
	}

	@AfterAll
	static void reset() {
		PriceTable.setData(PriceData.EMPTY);
	}

	@Test
	void ownEntriesWin() {
		assertEquals(1.0, PriceTable.base(Items.BREAD), 1e-9);
	}

	@Test
	void waresFallBackToVanillaAndToDefaults() {
		assertEquals(List.of(Items.BAMBOO_PLANKS, Items.BAMBOO_BLOCK), PriceTable.wares(Culture.KARST, Goods.WOOD));
		assertEquals(List.of(Items.OAK_PLANKS), PriceTable.wares(Culture.TEMPERATE, Goods.WOOD), "no culture entry: the default wares");
		assertTrue(PriceTable.wares(Culture.TEMPERATE, Goods.LUXURY).isEmpty());
	}
}
