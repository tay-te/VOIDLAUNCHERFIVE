package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.Goods;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a blueprint step costs in {@link Goods}. One unit per block of its main good, plus a second good for a few
 * blocks made of two things (a lantern is metal and fuel, a bookshelf wood and books). The mapping, by block:
 *
 * <pre>
 *   nothing  air, water, plants (flowers, grass, crops, leaves, saplings, vines), snow, and site earth
 *            (dirt, grass, sand, gravel, mud, paths, farmland, moss): dug or grown on the spot
 *   WOOD     logs, wood, planks and everything cut from them (stairs, slabs, fences, doors, trapdoors, signs),
 *            barrels, chests, ladders, scaffolding, workbenches, lecterns, looms, composters, shelves, beehives
 *   STONE    stone, cobble, stone bricks, deepslate, tuff, andesite/diorite/granite, sandstone, limestone,
 *            basalt, blackstone, quartz, prismarine, calcite, smooth stone, furnaces, stonecutters
 *   CLAY     bricks, terracotta (glazed too), mud bricks, packed mud, clay, flower pots, decorated pots
 *   GLASS    glass and panes (stained too)
 *   METAL    iron, copper and gold blocks, bars, chains, lanterns, anvils, cauldrons, bells, rails, iron doors
 *   CLOTH    wool, carpets, beds, banners
 *   FOOD     hay bales, melons, pumpkins, cakes
 *   FUEL     coal blocks, torches, campfires (with WOOD), lanterns (with METAL)
 *   LUXURY   bookshelves (with WOOD), candles, glazed terracotta (with CLAY), sea lanterns
 * </pre>
 * Work is one unit per non-air step, and one per air step that digs into natural ground (excavation); clearing air
 * above the ground costs nothing, since where nothing grows there is nothing to do.
 */
public final class MaterialBill {
	private static final Map<Block, int[]> CACHE = new ConcurrentHashMap<>();
	private static final int[] NONE = new int[0];

	private MaterialBill() {
	}

	/** Adds a placement's goods to {@code goods} (indexed by {@link Goods#ordinal()}). */
	public static void add(BlockState state, int[] goods) {
		for (int g : of(state.getBlock())) {
			goods[g]++;
		}
	}

	/** The goods ordinals one block costs (empty for free blocks); cached per block. */
	public static int[] of(Block block) {
		return CACHE.computeIfAbsent(block, MaterialBill::classify);
	}

	private static int[] classify(Block b) {
		String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
		BlockState s = b.defaultBlockState();
		if (s.isAir() || b instanceof LiquidBlock || b instanceof VegetationBlock || b instanceof CropBlock || b instanceof LeavesBlock
			|| b instanceof SaplingBlock || b instanceof VineBlock || b instanceof BushBlock) {
			return NONE;
		}
		if (p.equals("snow") || p.equals("snow_block") || p.equals("powder_snow") || p.equals("ice")) {
			return NONE;
		}
		if (p.equals("dirt") || p.equals("grass_block") || p.equals("coarse_dirt") || p.equals("podzol") || p.equals("rooted_dirt") || p.equals("mud")
			|| p.equals("sand") || p.equals("red_sand") || p.equals("gravel") || p.equals("dirt_path") || p.equals("farmland") || p.equals("moss_block")
			|| p.equals("moss_carpet") || p.equals("mycelium") || p.endsWith("_sand") || p.equals("peat") || p.equals("red_earth") || p.equals("bamboo")
			|| p.equals("sugar_cane") || p.equals("cactus") || p.equals("kelp") || p.equals("kelp_plant") || p.equals("lily_pad") || p.contains("coral")) {
			return NONE;
		}
		if (p.contains("glass")) {
			return one(Goods.GLASS);
		}
		if (p.endsWith("_wool") || p.endsWith("_carpet") || p.endsWith("_bed") || p.endsWith("_banner")) {
			return one(Goods.CLOTH);
		}
		if (p.contains("glazed_terracotta")) {
			return two(Goods.CLAY, Goods.LUXURY);
		}
		if (p.contains("terracotta") || p.equals("bricks") || p.startsWith("brick_") || p.startsWith("mud_brick") || p.equals("packed_mud")
			|| p.equals("clay") || p.equals("flower_pot") || p.startsWith("potted_") || p.equals("decorated_pot")) {
			return one(Goods.CLAY);
		}
		if (p.equals("hay_block") || p.equals("melon") || p.equals("pumpkin") || p.equals("carved_pumpkin") || p.equals("cake") || p.equals("dried_kelp_block")) {
			return one(Goods.FOOD);
		}
		if (p.equals("jack_o_lantern")) {
			return two(Goods.FOOD, Goods.FUEL);
		}
		if (p.equals("sea_lantern")) {
			return one(Goods.LUXURY);
		}
		if (p.contains("lantern")) {
			return two(Goods.METAL, Goods.FUEL);
		}
		if (p.equals("coal_block")) {
			return one(Goods.FUEL);
		}
		if (p.contains("torch")) {
			return one(Goods.FUEL);
		}
		if (p.contains("campfire")) {
			return two(Goods.WOOD, Goods.FUEL);
		}
		if (p.contains("candle")) {
			return one(Goods.LUXURY);
		}
		if (p.equals("bookshelf") || p.equals("chiseled_bookshelf")) {
			return two(Goods.WOOD, Goods.LUXURY);
		}
		if (p.contains("iron") || p.contains("copper") || p.contains("gold") || p.contains("chain") || p.endsWith("_bars") || p.contains("anvil")
			|| p.contains("cauldron") || p.equals("bell") || p.contains("rail") || p.equals("hopper") || p.contains("lightning_rod")) {
			return one(Goods.METAL);
		}
		if (p.equals("blast_furnace") || p.equals("stonecutter")) {
			return two(Goods.STONE, Goods.METAL);
		}
		if (p.equals("smithing_table")) {
			return two(Goods.WOOD, Goods.METAL);
		}
		if (p.equals("smoker")) {
			return two(Goods.STONE, Goods.WOOD);
		}
		if (s.ignitedByLava() || p.contains("planks") || p.endsWith("_log") || p.endsWith("_wood") || p.contains("bamboo") || p.equals("ladder")
			|| p.equals("scaffolding") || p.equals("barrel") || p.contains("chest") || p.equals("crafting_table") || p.equals("lectern") || p.equals("loom")
			|| p.equals("composter") || p.contains("shelf") || p.equals("beehive") || p.contains("_table") || p.contains("sign")) {
			return one(Goods.WOOD);
		}
		return one(Goods.STONE);
	}

	private static int[] one(Goods g) {
		return new int[]{g.ordinal()};
	}

	private static int[] two(Goods a, Goods b) {
		return new int[]{a.ordinal(), b.ordinal()};
	}
}
