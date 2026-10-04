package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.Goods;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What a blueprint step costs in {@link Goods} (docs/LIVING_WORLD.md §7.5: bills ask for <b>processed</b> goods, so the
 * production chains matter). One unit per block of its main good, plus one of a second good for blocks made of two
 * things (a lantern is iron and the candle it burns, a bookshelf timber and books, stained glass glass and dye). The
 * mapping, by block (vanilla and Expanse alike, by their ids):
 *
 * <pre>
 *   nothing        air, water, plants (flowers, grass, crops, leaves, saplings, vines, moss carpet), snow, ice, fire,
 *                  and earth dug on the spot (dirt, grass, mud, paths, farmland, moss, podzol, mycelium)
 *   LOGS           logs, wood, stripped logs and wood, stems and hyphae, bamboo blocks
 *   WOOD           planks and all cut from them (stairs, slabs, fences, gates, doors, trapdoors, signs, plates, buttons),
 *                  bamboo planks and mosaic, ladders, scaffolding, chests, barrels, crafting, fletching and cartography
 *                  tables (the last + PAPER), looms, composters, shelves, beds (+ CLOTH)
 *   STONE          rough stone: cobble and mossy cobble, rubble, field stone, natural rock laid as it is (stone,
 *                  andesite, diorite, granite, tuff, calcite, basalt, blackstone, deepslate, plain sandstone, limestone),
 *                  furnaces
 *   DRESSED_STONE  cut stone: stone bricks (mossy, cracked, chiseled), deepslate and tuff bricks and tiles, smooth and
 *                  polished stone, cut, smooth and chiseled sandstone, quartz, purpur, prismarine bricks
 *   BRICK          fired clay: bricks, nether bricks, terracotta (coloured and glazed + DYE), flower pots, decorated pots
 *   CLAY           raw clay, sun-dried mud and render: clay, packed mud, mud bricks, plaster (and the mushroom stem
 *                  the styles lay as white plaster)
 *   SAND           sand (red too), gravel, concrete and its powder (+ DYE, but white: render)
 *   GLASS          glass and panes (stained + DYE)
 *   METAL          iron, copper and gold (gold + LUXURY), bars, chains, anvils, cauldrons, bells, rails, hoppers,
 *                  iron doors and trapdoors, brewing stands; hanging signs WOOD + METAL; smithing tables WOOD + METAL;
 *                  blast furnaces and stonecutters DRESSED_STONE + METAL
 *   METAL+CANDLES  lanterns (iron, soul, copper): the lamp and what burns in it
 *   CANDLES        candles, one per candle in the block (dyed + DYE); the invisible light block (a lamp kept burning
 *                  out of sight); jack o'lanterns PRODUCE + CANDLES; candle cakes FOOD + CANDLES
 *   FUEL           torches, coal blocks; campfires LOGS + FUEL
 *   CLOTH          wool, carpets, banners (coloured + DYE; white is wool's own colour)
 *   BOOKS          bookshelves and lecterns: WOOD + BOOKS
 *   WAX            honeycomb blocks; beehives WOOD + WAX
 *   GRAIN          hay bales, thatch
 *   PRODUCE        melons, pumpkins
 *   FOOD           cakes
 *   SPECIALTY      glowstone, shroomlight, froglights, amethyst, sea lanterns, Expanse prismite, amber and glowcaps
 * </pre>
 * Earth laid as fill in an EARTHWORK stage (sand, gravel, dirt, clay) is free: it is the ground cut elsewhere on the
 * plot. Work is one unit per non-air step, and one per air step that digs into natural ground (excavation); clearing air
 * above the ground costs nothing, since where nothing grows there is nothing to do. A building's largest stage (a
 * cathedral's walls) asks for about two thousand units.
 */
public final class MaterialBill {
	private static final Map<Block, int[]> CACHE = new ConcurrentHashMap<>();
	private static final int[] NONE = new int[0];

	private MaterialBill() {
	}

	/** Adds a placement's goods to {@code goods} (indexed by {@link Goods#ordinal()}). */
	public static void add(BlockState state, int[] goods) {
		int[] of = of(state.getBlock());
		if (state.getBlock() instanceof CandleBlock && state.hasProperty(BlockStateProperties.CANDLES)) {
			// a candle block holds one to four candles
			goods[Goods.CANDLES.ordinal()] += state.getValue(BlockStateProperties.CANDLES) - 1;
		}
		for (int g : of) {
			goods[g]++;
		}
	}

	/** The goods ordinals one block costs (empty for free blocks); cached per block. */
	public static int[] of(Block block) {
		return CACHE.computeIfAbsent(block, MaterialBill::classify);
	}

	/**
	 * Earth moved on the site (sand, gravel, dirt, mud, clay): free when it is laid as fill in an EARTHWORK stage, where
	 * it is the same ground cut elsewhere on the plot (the work is counted, not the goods).
	 */
	public static boolean earth(Block block) {
		String p = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return p.equals("sand") || p.equals("red_sand") || p.endsWith("_sand") || p.equals("gravel") || p.equals("dirt") || p.equals("coarse_dirt")
			|| p.equals("mud") || p.equals("clay") || p.equals("grass_block") || p.equals("rooted_dirt") || p.equals("podzol");
	}

	/** Whether a block is masonry (rough or dressed stone, brick, clay): what a stone church is built of. */
	public static boolean masonry(Block block) {
		int[] g = of(block);
		if (g.length == 0) {
			return false;
		}
		int m = g[0];
		return m == Goods.STONE.ordinal() || m == Goods.DRESSED_STONE.ordinal() || m == Goods.BRICK.ordinal() || m == Goods.CLAY.ordinal();
	}

	/** Whether a block is timber (sawn or in the log). */
	public static boolean timber(Block block) {
		int[] g = of(block);
		return g.length > 0 && (g[0] == Goods.WOOD.ordinal() || g[0] == Goods.LOGS.ordinal());
	}

	private static int[] classify(Block b) {
		String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
		BlockState s = b.defaultBlockState();
		// white is the stuff's own colour or a whitewash (white wool, white render, white terracotta): no dye
		boolean dyed = dyed(p) && !p.startsWith("white_");
		if (p.equals("light")) {
			return one(Goods.CANDLES);
		}
		if (s.isAir() || b instanceof LiquidBlock || b instanceof VegetationBlock || b instanceof CropBlock || b instanceof LeavesBlock
			|| b instanceof SaplingBlock || b instanceof VineBlock || b instanceof BushBlock) {
			return NONE;
		}
		if (p.equals("snow") || p.equals("snow_block") || p.equals("powder_snow") || p.equals("ice") || p.endsWith("_ice") || p.equals("fire")
			|| p.equals("soul_fire")) {
			return NONE;
		}
		if (p.equals("dirt") || p.equals("grass_block") || p.equals("coarse_dirt") || p.equals("podzol") || p.equals("rooted_dirt") || p.equals("mud")
			|| p.equals("dirt_path") || p.equals("farmland") || p.equals("moss_block") || p.equals("moss_carpet") || p.equals("pale_moss_block")
			|| p.equals("pale_moss_carpet") || p.equals("mycelium") || p.equals("peat") || p.equals("red_earth") || p.equals("bamboo")
			|| p.equals("sugar_cane") || p.equals("cactus") || p.equals("kelp") || p.equals("kelp_plant") || p.equals("lily_pad") || p.contains("coral")) {
			return NONE;
		}
		// light, and what burns
		if (p.equals("sea_lantern")) {
			return one(Goods.SPECIALTY);
		}
		if (p.equals("jack_o_lantern")) {
			return two(Goods.PRODUCE, Goods.CANDLES);
		}
		if (p.contains("lantern")) {
			return two(Goods.METAL, Goods.CANDLES);
		}
		if (p.contains("candle_cake")) {
			return two(Goods.FOOD, Goods.CANDLES);
		}
		if (b instanceof CandleBlock || p.endsWith("candle")) {
			return dyed ? two(Goods.CANDLES, Goods.DYE) : one(Goods.CANDLES);
		}
		if (p.contains("torch")) {
			return one(Goods.FUEL);
		}
		if (p.contains("campfire")) {
			return two(Goods.LOGS, Goods.FUEL);
		}
		if (p.equals("coal_block")) {
			return one(Goods.FUEL);
		}
		if (p.equals("glowstone") || p.equals("shroomlight") || p.endsWith("froglight") || p.contains("amethyst") || p.contains("prismite")
			|| p.contains("amber") || p.contains("glowcap")) {
			return one(Goods.SPECIALTY);
		}
		// sand and what is made of it
		if (p.contains("concrete")) {
			return dyed ? two(Goods.SAND, Goods.DYE) : one(Goods.SAND);
		}
		if (p.contains("glass")) {
			return p.contains("stained") ? two(Goods.GLASS, Goods.DYE) : one(Goods.GLASS);
		}
		if (p.equals("sand") || p.equals("red_sand") || p.endsWith("_sand") || p.equals("gravel") || p.equals("suspicious_gravel")) {
			return one(Goods.SAND);
		}
		// cloth
		if (p.endsWith("_bed")) {
			return dyed ? three(Goods.CLOTH, Goods.WOOD, Goods.DYE) : two(Goods.CLOTH, Goods.WOOD);
		}
		if (p.endsWith("_wool") || p.endsWith("_carpet") || p.endsWith("_banner")) {
			return dyed ? two(Goods.CLOTH, Goods.DYE) : one(Goods.CLOTH);
		}
		// fired clay
		if (p.contains("terracotta")) {
			return dyed || p.contains("glazed") ? two(Goods.BRICK, Goods.DYE) : one(Goods.BRICK);
		}
		if (p.equals("bricks") || p.startsWith("brick_") || p.contains("nether_brick") || p.equals("flower_pot") || p.startsWith("potted_")
			|| p.equals("decorated_pot")) {
			return one(Goods.BRICK);
		}
		if (p.equals("clay") || p.equals("packed_mud") || p.startsWith("mud_brick") || p.equals("mushroom_stem") || p.contains("plaster") || p.contains("daub")) {
			return one(Goods.CLAY);
		}
		// farm goods
		if (p.equals("hay_block") || p.contains("thatch")) {
			return one(Goods.GRAIN);
		}
		if (p.equals("melon") || p.equals("pumpkin") || p.equals("carved_pumpkin")) {
			return one(Goods.PRODUCE);
		}
		if (p.equals("cake")) {
			return one(Goods.FOOD);
		}
		if (p.equals("dried_kelp_block")) {
			return one(Goods.SPECIALTY);
		}
		if (p.equals("honeycomb_block")) {
			return one(Goods.WAX);
		}
		if (p.equals("beehive") || p.equals("bee_nest")) {
			return two(Goods.WOOD, Goods.WAX);
		}
		// books
		if (p.equals("bookshelf") || p.equals("chiseled_bookshelf") || p.equals("lectern")) {
			return two(Goods.WOOD, Goods.BOOKS);
		}
		// workstations of two materials
		if (p.equals("blast_furnace") || p.equals("stonecutter")) {
			return two(Goods.DRESSED_STONE, Goods.METAL);
		}
		if (p.equals("smithing_table")) {
			return two(Goods.WOOD, Goods.METAL);
		}
		if (p.equals("smoker")) {
			return two(Goods.STONE, Goods.LOGS);
		}
		if (p.equals("grindstone")) {
			return two(Goods.STONE, Goods.WOOD);
		}
		if (p.equals("cartography_table")) {
			return two(Goods.WOOD, Goods.PAPER);
		}
		if (p.contains("hanging_sign")) {
			return two(Goods.WOOD, Goods.METAL);
		}
		// metal
		if (p.contains("gold") && !p.contains("marigold")) {
			return two(Goods.METAL, Goods.LUXURY);
		}
		if (p.contains("iron") || p.contains("copper") || p.contains("chain") || p.endsWith("_bars") || p.contains("anvil") || p.contains("cauldron")
			|| p.equals("bell") || p.contains("rail") || p.equals("hopper") || p.contains("lightning_rod") || p.equals("brewing_stand")
			|| p.equals("heavy_core")) {
			return one(Goods.METAL);
		}
		// timber: in the log, or sawn
		if (p.endsWith("_log") || p.endsWith("_wood") && !p.contains("petrified") || p.endsWith("_stem") || p.endsWith("_hyphae")
			|| p.equals("bamboo_block") || p.equals("stripped_bamboo_block") || p.equals("mangrove_roots") || p.equals("muddy_mangrove_roots")) {
			return one(Goods.LOGS);
		}
		if (s.ignitedByLava() || p.contains("planks") || p.contains("bamboo") || p.equals("ladder") || p.equals("scaffolding") || p.equals("barrel")
			|| p.contains("chest") || p.equals("crafting_table") || p.equals("loom") || p.equals("composter") || p.contains("shelf") || p.contains("_table")
			|| p.contains("sign")) {
			return one(Goods.WOOD);
		}
		// stone: dressed, or rough
		if (dressed(p)) {
			return one(Goods.DRESSED_STONE);
		}
		return one(Goods.STONE);
	}

	/** Cut and finished stone, by the vanilla (and Expanse) naming of its cuts. */
	private static boolean dressed(String p) {
		if (p.contains("cobble") || p.contains("rubble")) {
			return false;
		}
		return p.contains("brick") || p.contains("polished") || p.contains("smooth") || p.startsWith("cut_") || p.contains("_cut_")
			|| p.contains("chiseled") || p.contains("pillar") || p.contains("tiles") || p.contains("tile_") || p.contains("quartz")
			|| p.contains("purpur") || p.contains("prismarine") || p.contains("dressed") || p.contains("ashlar") || p.contains("carved");
	}

	private static final String[] COLOURS = {"white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_", "pink_", "gray_", "light_gray_",
		"cyan_", "purple_", "blue_", "brown_", "green_", "red_", "black_"};

	/** A block named by a dye colour (red_wool, light_blue_stained_glass, magenta_glazed_terracotta...). */
	private static boolean dyed(String p) {
		for (String c : COLOURS) {
			if (p.startsWith(c) || p.contains("_" + c)) {
				return true;
			}
		}
		return false;
	}

	private static int[] one(Goods g) {
		return new int[]{g.ordinal()};
	}

	private static int[] two(Goods a, Goods b) {
		return new int[]{a.ordinal(), b.ordinal()};
	}

	private static int[] three(Goods a, Goods b, Goods c) {
		return new int[]{a.ordinal(), b.ordinal(), c.ordinal()};
	}
}
