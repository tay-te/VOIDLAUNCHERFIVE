package dev.voidmc.hearth.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A concrete item for abstract goods, in a culture's own materials: what a stall shows, what a cart carries, what a
 * quest asks for. {@link HearthServices#goodsItems()}, never null: {@link #VANILLA} until a module registers its own.
 */
public interface GoodsItems {
	/** One item standing for {@code goods}; {@code seed} picks among the choices, so a stall keeps its wares. */
	ItemStack sample(Goods goods, Culture culture, long seed);

	/** One plain vanilla item per kind of goods. */
	GoodsItems VANILLA = (goods, culture, seed) -> new ItemStack(switch (goods) {
		case FOOD -> Items.BREAD;
		case WOOD -> Items.OAK_PLANKS;
		case STONE -> Items.COBBLESTONE;
		case CLAY -> Items.CLAY_BALL;
		case GLASS -> Items.GLASS;
		case METAL -> Items.IRON_INGOT;
		case CLOTH, WOOL -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
		case FUEL -> Items.COAL;
		case TOOLS -> Items.IRON_PICKAXE;
		case LUXURY -> Items.GOLD_INGOT;
		case LOGS -> Items.OAK_LOG;
		case ORE -> Items.RAW_IRON;
		case DRESSED_STONE -> Items.STONE_BRICKS;
		case BRICK -> Items.BRICKS;
		case SAND -> Items.SAND;
		case GRAIN, FLOUR -> Items.WHEAT;
		case MEAT -> Items.BEEF;
		case FISH -> Items.COD;
		case LEATHER -> Items.LEATHER;
		case WAX -> Items.HONEYCOMB;
		case CANDLES -> Items.CANDLE;
		case PAPER -> Items.PAPER;
		case BOOKS -> Items.BOOK;
		case DYE -> Items.DYE.pick(net.minecraft.world.item.DyeColor.RED);
		case SALT -> Items.SUGAR;
		case PRODUCE -> Items.CARROT;
		case SPECIALTY -> Items.AMETHYST_SHARD;
	});
}
