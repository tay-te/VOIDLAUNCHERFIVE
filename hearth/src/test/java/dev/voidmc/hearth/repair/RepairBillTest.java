package dev.voidmc.hearth.repair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RepairBillTest {
	@BeforeAll
	static void boot() {
		RepairTestKit.boot();
	}

	@Test
	void buildingsAskForProcessedMaterials() {
		assertArrayEquals(new Goods[]{Goods.WOOD}, RepairBill.of(Blocks.OAK_PLANKS.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.WOOD}, RepairBill.of(Blocks.SPRUCE_DOOR.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.LOGS}, RepairBill.of(Blocks.OAK_LOG.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.STONE}, RepairBill.of(Blocks.COBBLESTONE.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.DRESSED_STONE}, RepairBill.of(Blocks.STONE_BRICKS.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.BRICK}, RepairBill.of(Blocks.BRICKS.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.GLASS}, RepairBill.of(Blocks.GLASS_PANE.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.METAL, Goods.CANDLES}, RepairBill.of(Blocks.LANTERN.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.CANDLES}, RepairBill.of(Blocks.WALL_TORCH.defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.CLOTH}, RepairBill.of(Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()));
		assertArrayEquals(new Goods[]{Goods.WOOD, Goods.BOOKS}, RepairBill.of(Blocks.BOOKSHELF.defaultBlockState()));
		assertEquals(0, RepairBill.of(Blocks.DIRT.defaultBlockState()).length, "earth is dug on the spot");
		assertEquals(0, RepairBill.of(Blocks.AIR.defaultBlockState()).length);
	}

	@Test
	void chargesAndRefundsTheStockpile() {
		SimpleSettlement s = RepairTestKit.settlement();
		var lantern = Blocks.LANTERN.defaultBlockState();
		assertFalse(RepairBill.affordable(s.stockpile(), lantern));
		assertEquals(Goods.METAL, RepairBill.missing(s.stockpile(), lantern));
		s.stockpile().add(Goods.METAL, 1);
		assertEquals(Goods.CANDLES, RepairBill.missing(s.stockpile(), lantern));
		s.stockpile().add(Goods.CANDLES, 1);
		assertTrue(RepairBill.affordable(s.stockpile(), lantern));
		assertNull(RepairBill.missing(s.stockpile(), lantern));
		assertTrue(RepairBill.charge(s.stockpile(), lantern));
		assertEquals(0, s.stockpile().get(Goods.METAL));
		RepairBill.refund(s.stockpile(), lantern);
		assertEquals(1, s.stockpile().get(Goods.CANDLES));
	}

	@Test
	void worthWithoutAnEconomy() {
		SimpleSettlement s = RepairTestKit.settlement();
		assertEquals(1, RepairBill.worth(s, Blocks.OAK_PLANKS.defaultBlockState()));
		assertEquals(3, RepairBill.worth(s, Blocks.GLASS_PANE.defaultBlockState()), "glass is dearer than timber");
		assertEquals(10, RepairBill.worth(s, Blocks.LANTERN.defaultBlockState()));
		assertEquals(0, RepairBill.worth(s, Blocks.DIRT.defaultBlockState()));
	}
}
