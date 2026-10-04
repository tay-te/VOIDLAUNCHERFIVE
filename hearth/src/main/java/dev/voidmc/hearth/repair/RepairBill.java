package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Stockpile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What putting one block back costs, in the phase 3 {@link Goods} (docs/LIVING_WORLD.md §7.5: buildings ask for the
 * processed materials), and what a broken block is worth in coin (the fine for vandalism, the bill for a deeded house).
 *
 * <pre>
 *   nothing        air, fluids, plants, snow, natural ground dug on the spot
 *   WOOD           planks and what is cut from them; doors, fences, stairs, signs, chests, barrels, ladders
 *   LOGS           logs and wood (round timber in a frame)
 *   STONE          cobble, rubble, mossy and rough stone
 *   DRESSED_STONE  stone bricks, polished, smooth and cut stone, sandstone and limestone ashlar, quartz, deepslate tiles
 *   BRICK          bricks, terracotta, mud brick, flower pots
 *   CLAY           clay and packed mud
 *   GLASS          glass and panes
 *   METAL          iron, copper, gold, bars, chains, bells, anvils, rails (+ CANDLES for lanterns)
 *   CLOTH          wool, carpets, beds, banners
 *   CANDLES        candles, torches (the town's light, LIVING_WORLD.md §1)
 *   BOOKS          bookshelves (+ WOOD)
 *   FOOD           hay, melons, pumpkins
 * </pre>
 * Kept in step with the build module's material bill by hand (docs/requests/p3-repair.md asks for one shared bill).
 */
public final class RepairBill {
	private static final Map<Block, Goods[]> CACHE = new ConcurrentHashMap<>();
	private static final Goods[] NONE = new Goods[0];

	private RepairBill() {
	}

	/** The goods one block of this kind costs to put back (empty for free blocks). */
	public static Goods[] of(BlockState state) {
		return CACHE.computeIfAbsent(state.getBlock(), RepairBill::classify);
	}

	/** Whether the stockpile holds the goods for this block. */
	public static boolean affordable(Stockpile stock, BlockState state) {
		for (Goods g : of(state)) {
			if (stock.get(g) < 1) {
				return false;
			}
		}
		return true;
	}

	/** The first good the stockpile lacks for this block, or null if it has them all. */
	public static @Nullable Goods missing(Stockpile stock, BlockState state) {
		for (Goods g : of(state)) {
			if (stock.get(g) < 1) {
				return g;
			}
		}
		return null;
	}

	/** Takes one block's goods; returns whether all of them were there. */
	public static boolean charge(Stockpile stock, BlockState state) {
		boolean all = true;
		for (Goods g : of(state)) {
			all &= stock.take(g, 1) == 1;
		}
		return all;
	}

	/** Puts one block's goods back (a mended block that could not go in after all). */
	public static void refund(Stockpile stock, BlockState state) {
		for (Goods g : of(state)) {
			stock.add(g, 1);
		}
	}

	/**
	 * What a block is worth in bits: the settlement market's buying price for the block's item when an economy module runs,
	 * otherwise a plain value per good. Never less than 1 for a block that costs anything.
	 */
	public static long worth(Settlement settlement, BlockState state) {
		Goods[] goods = of(state);
		if (goods.length == 0) {
			return 0;
		}
		EconomyService economy = HearthServices.economy();
		if (economy != null && state.getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
			try {
				long p = economy.price(settlement, new ItemStack(state.getBlock().asItem()), false);
				if (p > 0) {
					return p;
				}
			} catch (RuntimeException ignored) {
				// an economy without a price for this item: fall back to the plain values
			}
		}
		long sum = 0;
		for (Goods g : goods) {
			sum += plainValue(g);
		}
		return Math.max(1, sum);
	}

	/** A good's worth in bits with no economy to ask (processed and finished goods are dearer). */
	static long plainValue(Goods g) {
		return switch (g) {
			case GLASS, BRICK, DRESSED_STONE, CLOTH -> 3;
			case METAL, CANDLES, LEATHER, PAPER, DYE -> 5;
			case BOOKS, LUXURY, TOOLS -> 12;
			default -> 1;
		};
	}

	private static Goods[] classify(Block b) {
		String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
		BlockState s = b.defaultBlockState();
		if (s.isAir() || b instanceof LiquidBlock || b instanceof VegetationBlock || b instanceof CropBlock || b instanceof LeavesBlock
			|| b instanceof SaplingBlock || b instanceof VineBlock || b instanceof BushBlock) {
			return NONE;
		}
		if (p.equals("snow") || p.equals("snow_block") || p.equals("powder_snow") || p.equals("ice") || p.equals("light") || p.equals("fire")
			|| p.equals("soul_fire")) {
			return NONE;
		}
		if (p.equals("dirt") || p.equals("grass_block") || p.equals("coarse_dirt") || p.equals("podzol") || p.equals("rooted_dirt") || p.equals("mud")
			|| p.equals("sand") || p.equals("red_sand") || p.equals("gravel") || p.equals("dirt_path") || p.equals("farmland") || p.equals("moss_block")
			|| p.equals("moss_carpet") || p.equals("mycelium") || p.endsWith("_sand") || p.equals("sugar_cane") || p.equals("cactus") || p.contains("kelp")
			|| p.equals("lily_pad") || p.contains("coral")) {
			return NONE;
		}
		if (p.contains("glass")) {
			return one(Goods.GLASS);
		}
		if (p.endsWith("_wool") || p.endsWith("_carpet") || p.endsWith("_bed") || p.endsWith("_banner")) {
			return one(Goods.CLOTH);
		}
		if (p.contains("terracotta") || p.equals("bricks") || p.startsWith("brick_") || p.startsWith("mud_brick") || p.equals("flower_pot")
			|| p.startsWith("potted_") || p.equals("decorated_pot")) {
			return one(Goods.BRICK);
		}
		if (p.equals("clay") || p.equals("packed_mud")) {
			return one(Goods.CLAY);
		}
		if (p.equals("hay_block") || p.equals("melon") || p.equals("pumpkin") || p.equals("carved_pumpkin") || p.equals("jack_o_lantern")) {
			return one(Goods.FOOD);
		}
		if (p.contains("candle") || p.contains("torch")) {
			return one(Goods.CANDLES);
		}
		if (p.equals("sea_lantern") || p.equals("glowstone") || p.equals("shroomlight")) {
			return one(Goods.SPECIALTY);
		}
		if (p.contains("lantern")) {
			return two(Goods.METAL, Goods.CANDLES);
		}
		if (p.equals("coal_block") || p.contains("campfire")) {
			return one(Goods.FUEL);
		}
		if (p.equals("bookshelf") || p.equals("chiseled_bookshelf")) {
			return two(Goods.WOOD, Goods.BOOKS);
		}
		if (p.contains("iron") || p.contains("copper") || p.contains("gold") || p.contains("chain") || p.endsWith("_bars") || p.contains("anvil")
			|| p.contains("cauldron") || p.equals("bell") || p.contains("rail") || p.equals("hopper") || p.contains("lightning_rod")) {
			return one(Goods.METAL);
		}
		if (p.endsWith("_log") || p.endsWith("_wood") || p.endsWith("_stem") || p.endsWith("_hyphae") || p.equals("bamboo_block")) {
			return one(Goods.LOGS);
		}
		if (s.ignitedByLava() || p.contains("planks") || p.contains("bamboo") || p.equals("ladder") || p.equals("scaffolding") || p.equals("barrel")
			|| p.contains("chest") || p.equals("crafting_table") || p.equals("lectern") || p.equals("loom") || p.equals("composter") || p.contains("shelf")
			|| p.equals("beehive") || p.contains("_table") || p.contains("sign")) {
			return one(Goods.WOOD);
		}
		if (p.equals("cobblestone") || p.startsWith("cobblestone_") || p.startsWith("mossy_cobblestone") || p.equals("stone") || p.equals("cobbled_deepslate")
			|| p.startsWith("cobbled_deepslate_") || p.equals("andesite") || p.equals("diorite") || p.equals("granite") || p.equals("tuff")
			|| p.equals("basalt") || p.equals("blackstone") || p.equals("calcite") || p.equals("deepslate") || p.endsWith("_stone") && !p.startsWith("smooth")) {
			return one(Goods.STONE);
		}
		// everything else stone-like has been cut and dressed by a mason
		return one(Goods.DRESSED_STONE);
	}

	private static Goods[] one(Goods g) {
		return new Goods[]{g};
	}

	private static Goods[] two(Goods a, Goods b) {
		return new Goods[]{a, b};
	}
}
