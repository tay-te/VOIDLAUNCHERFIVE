package dev.voidmc.hearth.build.light;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.build.blueprint.MaterialBill;
import java.util.Arrays;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

/** The phase 3 bill (docs/LIVING_WORLD.md §7.5): processed goods, two-material blocks, candles by the candle. */
class MaterialBillTest {
	static {
		LightSupport.init();
	}

	private static Goods[] goods(Block b) {
		return Arrays.stream(MaterialBill.of(b)).mapToObj(i -> Goods.VALUES[i]).toArray(Goods[]::new);
	}

	private static void is(Block b, Goods... expected) {
		assertArrayEquals(expected, goods(b), b + " costs " + Arrays.toString(goods(b)));
	}

	@Test
	void timberStoneAndClay() {
		is(Blocks.OAK_LOG, Goods.LOGS);
		is(Blocks.STRIPPED_SPRUCE_WOOD, Goods.LOGS);
		is(Blocks.OAK_PLANKS, Goods.WOOD);
		is(Blocks.SPRUCE_STAIRS, Goods.WOOD);
		is(Blocks.DARK_OAK_FENCE, Goods.WOOD);
		is(Blocks.SPRUCE_DOOR, Goods.WOOD);
		is(Blocks.COBBLESTONE, Goods.STONE);
		is(Blocks.MOSSY_COBBLESTONE_WALL, Goods.STONE);
		is(Blocks.COBBLED_DEEPSLATE, Goods.STONE);
		is(Blocks.STONE_BRICKS, Goods.DRESSED_STONE);
		is(Blocks.CUT_SANDSTONE, Goods.DRESSED_STONE);
		is(Blocks.POLISHED_ANDESITE, Goods.DRESSED_STONE);
		is(Blocks.SMOOTH_STONE_SLAB, Goods.DRESSED_STONE);
		is(Blocks.DEEPSLATE_TILES, Goods.DRESSED_STONE);
		is(Blocks.BRICKS, Goods.BRICK);
		is(Blocks.TERRACOTTA, Goods.BRICK);
		is(Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), Goods.BRICK, Goods.DYE);
		is(Blocks.MUD_BRICKS, Goods.CLAY);
		is(Blocks.MUSHROOM_STEM, Goods.CLAY);
		is(Blocks.SAND, Goods.SAND);
		is(Blocks.HAY_BLOCK, Goods.GRAIN);
	}

	@Test
	void glassMetalClothBooks() {
		is(Blocks.GLASS_PANE, Goods.GLASS);
		is(Blocks.STAINED_GLASS.pick(DyeColor.RED), Goods.GLASS, Goods.DYE);
		is(Blocks.IRON_BARS, Goods.METAL);
		is(Blocks.IRON_CHAIN, Goods.METAL);
		is(Blocks.WOOL.pick(DyeColor.WHITE), Goods.CLOTH);
		is(Blocks.CARPET.pick(DyeColor.RED), Goods.CLOTH, Goods.DYE);
		is(Blocks.BOOKSHELF, Goods.WOOD, Goods.BOOKS);
		is(Blocks.LECTERN, Goods.WOOD, Goods.BOOKS);
	}

	@Test
	void light() {
		is(Blocks.LANTERN, Goods.METAL, Goods.CANDLES);
		is(Blocks.SOUL_LANTERN, Goods.METAL, Goods.CANDLES);
		is(Blocks.TORCH, Goods.FUEL);
		is(Blocks.CAMPFIRE, Goods.LOGS, Goods.FUEL);
		is(Blocks.CANDLE, Goods.CANDLES);
		is(Blocks.DYED_CANDLE.pick(DyeColor.RED), Goods.CANDLES, Goods.DYE);
		is(Blocks.LIGHT, Goods.CANDLES);
		is(Blocks.GLOWSTONE, Goods.SPECIALTY);
		int[] four = new int[Goods.VALUES.length];
		MaterialBill.add(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4), four);
		assertEquals(4, four[Goods.CANDLES.ordinal()], "four candles in one block cost four");
	}

	@Test
	void freeAndKinds() {
		assertEquals(0, MaterialBill.of(Blocks.AIR).length);
		assertEquals(0, MaterialBill.of(Blocks.POPPY).length);
		assertEquals(0, MaterialBill.of(Blocks.DIRT).length);
		assertEquals(0, MaterialBill.of(Blocks.OAK_LEAVES).length);
		assertTrue(MaterialBill.masonry(Blocks.STONE_BRICKS));
		assertTrue(MaterialBill.masonry(Blocks.BRICKS));
		assertTrue(MaterialBill.masonry(Blocks.COBBLESTONE));
		assertFalse(MaterialBill.masonry(Blocks.OAK_PLANKS));
		assertTrue(MaterialBill.timber(Blocks.OAK_PLANKS));
		assertTrue(MaterialBill.timber(Blocks.OAK_LOG));
		assertTrue(MaterialBill.earth(Blocks.SAND));
	}
}
