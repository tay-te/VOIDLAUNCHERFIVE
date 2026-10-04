package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Turns the abstract goods a stalled build or a short workshop wants into concrete items a player can bring, in the
 * settlement's own materials: a northern town short of logs asks for spruce or redwood, a desert town for acacia, a bayou
 * for willow or mangrove; a karst town's rough stone is limestone, its dressed stone limestone bricks. Expanse items are
 * used when Expanse is loaded and fall back to vanilla otherwise. Every {@link Goods} has choices (docs/LIVING_WORLD.md
 * §7.5): raw, processed and finished.
 *
 * <p>Each choice carries the goods units one item is worth (a log is four planks' worth of sawn timber) and a base price
 * in bits, used for rewards only when the economy module is absent.
 */
public final class GoodsItems {
	/** A concrete item for some goods. */
	public record Choice(Identifier item, int units, int baseBits) {
	}

	private static final Map<Goods, List<Choice>> COMMON = new EnumMap<>(Goods.class);
	private static final Map<Culture, Map<Goods, List<Choice>>> REGIONAL = new EnumMap<>(Culture.class);
	private static final Map<Identifier, Integer> BASE_BITS = new HashMap<>();
	private static final Map<Identifier, Goods> GOODS_OF = new HashMap<>();

	static {
		// Raw goods first, so an item that is both (wheat: grain, and flour once milled) counts as the raw kind when brought
		// for anything but a flour need.
		common(Goods.LOGS, c("oak_log", 1, 1), c("spruce_log", 1, 1), c("birch_log", 1, 1));
		common(Goods.STONE, c("cobblestone", 1, 1), c("stone", 1, 1), c("cobbled_deepslate", 1, 1));
		common(Goods.ORE, c("raw_iron", 1, 4), c("raw_copper", 1, 2));
		common(Goods.CLAY, c("clay", 1, 2), c("mud", 1, 1));
		common(Goods.SAND, c("sand", 1, 1), c("red_sand", 1, 1));
		common(Goods.GRAIN, c("wheat", 1, 1));
		common(Goods.MEAT, c("beef", 1, 2), c("porkchop", 1, 2), c("mutton", 1, 2), c("chicken", 1, 2));
		common(Goods.FISH, c("cod", 1, 2), c("salmon", 1, 2));
		common(Goods.WOOL, c("white_wool", 1, 2));
		common(Goods.WAX, c("honeycomb", 1, 3));
		common(Goods.SALT, x("salt_block", 4, 6));
		common(Goods.PRODUCE, c("carrot", 1, 1), c("potato", 1, 1), c("beetroot", 1, 1), c("apple", 1, 2));
		common(Goods.SPECIALTY, c("amethyst_shard", 1, 6), c("cocoa_beans", 1, 3), c("honey_bottle", 1, 6));

		common(Goods.WOOD, c("oak_log", 4, 3), c("spruce_log", 4, 3));
		common(Goods.DRESSED_STONE, c("stone_bricks", 1, 2), c("smooth_stone", 1, 2), c("polished_andesite", 1, 2));
		common(Goods.BRICK, c("bricks", 1, 3), c("terracotta", 1, 2));
		common(Goods.GLASS, c("glass", 1, 2));
		common(Goods.METAL, c("iron_ingot", 1, 8), c("copper_ingot", 1, 3));
		common(Goods.FLOUR, c("wheat", 1, 2));
		common(Goods.LEATHER, c("leather", 1, 4));
		common(Goods.CLOTH, c("white_wool", 1, 3), c("white_carpet", 1, 2));
		common(Goods.FUEL, c("coal", 1, 2), c("charcoal", 1, 2));
		common(Goods.PAPER, c("paper", 1, 2));
		common(Goods.DYE, c("red_dye", 1, 2), c("blue_dye", 1, 3), c("yellow_dye", 1, 2));
		common(Goods.TOOLS, c("iron_pickaxe", 4, 30), c("iron_axe", 4, 30), c("iron_shovel", 2, 12));
		common(Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2));
		common(Goods.CANDLES, c("candle", 1, 4));
		common(Goods.BOOKS, c("book", 1, 12));
		common(Goods.LUXURY, c("book", 2, 12), c("candle", 1, 4), c("honey_bottle", 1, 6));

		regional(Culture.TEMPERATE, Goods.WOOD, c("oak_log", 4, 3), c("birch_log", 4, 3), x("aspen_log", 4, 3), x("beech_log", 4, 3));
		regional(Culture.TEMPERATE, Goods.LOGS, c("oak_log", 1, 1), c("birch_log", 1, 1), x("beech_log", 1, 1), x("maple_log", 1, 1));
		regional(Culture.TEMPERATE, Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2), c("apple", 1, 2));
		regional(Culture.TEMPERATE, Goods.PRODUCE, c("apple", 1, 2), c("carrot", 1, 1), c("potato", 1, 1));
		regional(Culture.TEMPERATE, Goods.SPECIALTY, c("honey_bottle", 1, 6), x("bluebells", 1, 2));
		regional(Culture.WISTERIA, Goods.WOOD, x("wisteria_log", 4, 4), c("cherry_log", 4, 4), c("oak_log", 4, 3));
		regional(Culture.WISTERIA, Goods.LOGS, x("wisteria_log", 1, 2), c("cherry_log", 1, 2), c("oak_log", 1, 1));
		regional(Culture.WISTERIA, Goods.BRICK, c("terracotta", 1, 2), c("white_terracotta", 1, 2));
		regional(Culture.WISTERIA, Goods.LUXURY, c("book", 2, 12), c("honey_bottle", 1, 6), c("pink_dye", 1, 3));
		regional(Culture.WISTERIA, Goods.DYE, c("pink_dye", 1, 3), c("purple_dye", 1, 3), c("magenta_dye", 1, 3));
		regional(Culture.WISTERIA, Goods.PRODUCE, c("apple", 1, 2), c("sweet_berries", 1, 1));
		regional(Culture.WISTERIA, Goods.SPECIALTY, x("wisteria_blossoms", 1, 3), c("pink_petals", 1, 2));
		regional(Culture.NORTHERN, Goods.WOOD, x("redwood_log", 4, 4), c("spruce_log", 4, 3), x("pine_log", 4, 3));
		regional(Culture.NORTHERN, Goods.LOGS, x("redwood_log", 1, 2), c("spruce_log", 1, 1), x("pine_log", 1, 1));
		regional(Culture.NORTHERN, Goods.DRESSED_STONE, c("stone_bricks", 1, 2), c("polished_deepslate", 1, 3));
		regional(Culture.NORTHERN, Goods.FOOD, x("cooked_venison", 1, 4), c("bread", 1, 3), c("cooked_salmon", 1, 3));
		regional(Culture.NORTHERN, Goods.MEAT, x("venison", 1, 3), c("mutton", 1, 2), c("rabbit", 1, 2));
		regional(Culture.NORTHERN, Goods.CLOTH, c("brown_wool", 1, 3), c("white_wool", 1, 3));
		regional(Culture.NORTHERN, Goods.WOOL, c("brown_wool", 1, 2), c("white_wool", 1, 2));
		regional(Culture.NORTHERN, Goods.PRODUCE, x("cranberries", 1, 2), c("sweet_berries", 1, 1));
		regional(Culture.NORTHERN, Goods.SPECIALTY, x("amber", 1, 8), c("sweet_berries", 1, 1));
		regional(Culture.ALPINE, Goods.WOOD, c("spruce_log", 4, 3), x("pine_log", 4, 3), x("larch_log", 4, 3));
		regional(Culture.ALPINE, Goods.LOGS, c("spruce_log", 1, 1), x("pine_log", 1, 1));
		regional(Culture.ALPINE, Goods.STONE, c("cobblestone", 1, 1), c("stone", 1, 1), c("andesite", 1, 1));
		regional(Culture.ALPINE, Goods.ORE, c("raw_iron", 1, 4), c("raw_copper", 1, 2));
		regional(Culture.ALPINE, Goods.FOOD, c("bread", 1, 3), c("cooked_mutton", 1, 3), c("pumpkin_pie", 2, 5));
		regional(Culture.ALPINE, Goods.MEAT, c("mutton", 1, 2), c("beef", 1, 2));
		regional(Culture.ALPINE, Goods.PRODUCE, c("potato", 1, 1), c("carrot", 1, 1));
		regional(Culture.ALPINE, Goods.SPECIALTY, x("edelweiss", 1, 5), c("amethyst_shard", 1, 6));
		regional(Culture.TUNDRA, Goods.WOOD, c("spruce_log", 4, 4), x("larch_log", 4, 4));
		regional(Culture.TUNDRA, Goods.LOGS, c("spruce_log", 1, 2));
		regional(Culture.TUNDRA, Goods.CLOTH, c("brown_wool", 1, 3), c("white_wool", 1, 3));
		regional(Culture.TUNDRA, Goods.WOOL, c("brown_wool", 1, 2), c("white_wool", 1, 2));
		regional(Culture.TUNDRA, Goods.LEATHER, c("leather", 1, 4), c("rabbit_hide", 1, 2));
		regional(Culture.TUNDRA, Goods.FUEL, c("coal", 1, 3), c("charcoal", 1, 3));
		regional(Culture.TUNDRA, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), x("cooked_venison", 1, 4));
		regional(Culture.TUNDRA, Goods.MEAT, x("venison", 1, 3), c("rabbit", 1, 2));
		regional(Culture.TUNDRA, Goods.FISH, c("cod", 1, 2), c("salmon", 1, 2));
		regional(Culture.TUNDRA, Goods.PRODUCE, x("cranberries", 1, 2), c("sweet_berries", 1, 2));
		regional(Culture.TUNDRA, Goods.SPECIALTY, x("frostbloom", 1, 6), x("reindeer_lichen", 1, 3));
		regional(Culture.MOORLAND, Goods.WOOD, c("oak_log", 4, 4), c("spruce_log", 4, 4), c("birch_log", 4, 4));
		regional(Culture.MOORLAND, Goods.LOGS, c("oak_log", 1, 2), c("birch_log", 1, 2));
		regional(Culture.MOORLAND, Goods.STONE, c("cobblestone", 1, 1), c("mossy_cobblestone", 1, 2), c("stone", 1, 1));
		regional(Culture.MOORLAND, Goods.DRESSED_STONE, c("stone_bricks", 1, 2), c("mossy_stone_bricks", 1, 2));
		regional(Culture.MOORLAND, Goods.CLOTH, c("white_wool", 1, 3), c("gray_wool", 1, 3));
		regional(Culture.MOORLAND, Goods.WOOL, c("white_wool", 1, 2), c("gray_wool", 1, 2));
		regional(Culture.MOORLAND, Goods.FOOD, c("cooked_mutton", 1, 3), c("bread", 1, 3));
		regional(Culture.MOORLAND, Goods.MEAT, c("mutton", 1, 2));
		regional(Culture.MOORLAND, Goods.FUEL, x("peat", 1, 2), c("charcoal", 1, 2));
		regional(Culture.MOORLAND, Goods.PRODUCE, c("potato", 1, 1), c("beetroot", 1, 1));
		regional(Culture.MOORLAND, Goods.SPECIALTY, x("heather", 1, 3), c("honey_bottle", 1, 6));
		regional(Culture.MARITIME, Goods.WOOD, c("spruce_log", 4, 3), c("oak_log", 4, 3), c("dark_oak_log", 4, 3));
		regional(Culture.MARITIME, Goods.LOGS, c("spruce_log", 1, 1), c("dark_oak_log", 1, 1));
		regional(Culture.MARITIME, Goods.STONE, c("cobblestone", 1, 1), c("andesite", 1, 1), c("stone", 1, 1));
		regional(Culture.MARITIME, Goods.DRESSED_STONE, c("stone_bricks", 1, 2), c("polished_andesite", 1, 2));
		regional(Culture.MARITIME, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), c("dried_kelp", 1, 1));
		regional(Culture.MARITIME, Goods.FISH, c("cod", 1, 2), c("salmon", 1, 2));
		regional(Culture.MARITIME, Goods.CLOTH, c("white_wool", 1, 3), c("string", 1, 1));
		regional(Culture.MARITIME, Goods.SPECIALTY, c("kelp", 1, 1), c("dried_kelp_block", 4, 4), c("nautilus_shell", 1, 12));
		regional(Culture.TROPICAL, Goods.WOOD, x("palm_log", 4, 3), c("jungle_log", 4, 3), x("kapok_log", 4, 3));
		regional(Culture.TROPICAL, Goods.LOGS, x("palm_log", 1, 1), c("jungle_log", 1, 1), x("kapok_log", 1, 1));
		regional(Culture.TROPICAL, Goods.FOOD, c("cooked_cod", 1, 3), c("melon_slice", 1, 1), x("cooked_crab", 1, 3), c("cocoa_beans", 1, 2));
		regional(Culture.TROPICAL, Goods.FISH, x("crab_meat", 1, 3), c("tropical_fish", 1, 2), c("cod", 1, 2));
		regional(Culture.TROPICAL, Goods.SAND, x("coral_sand", 1, 1), c("sand", 1, 1));
		regional(Culture.TROPICAL, Goods.CLOTH, c("string", 1, 1), c("white_wool", 1, 3));
		regional(Culture.TROPICAL, Goods.DYE, c("lime_dye", 1, 3), c("green_dye", 1, 3), c("cyan_dye", 1, 3));
		regional(Culture.TROPICAL, Goods.PRODUCE, c("melon_slice", 1, 1), c("glow_berries", 1, 2));
		regional(Culture.TROPICAL, Goods.SPECIALTY, c("cocoa_beans", 1, 3), c("bamboo", 1, 1));
		regional(Culture.DESERT, Goods.WOOD, c("acacia_log", 4, 5), x("palm_log", 4, 5));
		regional(Culture.DESERT, Goods.LOGS, c("acacia_log", 1, 2), x("palm_log", 1, 2));
		regional(Culture.DESERT, Goods.STONE, c("sandstone", 1, 1), x("opal_sandstone", 1, 2));
		regional(Culture.DESERT, Goods.DRESSED_STONE, c("cut_sandstone", 1, 2), c("smooth_sandstone", 1, 2), x("cut_opal_sandstone", 1, 3));
		regional(Culture.DESERT, Goods.BRICK, c("mud_bricks", 1, 2), c("terracotta", 1, 2));
		regional(Culture.DESERT, Goods.CLAY, c("mud", 1, 1), c("clay", 1, 2));
		regional(Culture.DESERT, Goods.SAND, c("sand", 1, 1), x("opal_sand", 1, 2));
		regional(Culture.DESERT, Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2), c("sweet_berries", 1, 1));
		regional(Culture.DESERT, Goods.DYE, c("orange_dye", 1, 3), c("yellow_dye", 1, 2));
		regional(Culture.DESERT, Goods.PRODUCE, x("desert_pea", 1, 2), c("melon_slice", 1, 1));
		regional(Culture.DESERT, Goods.SPECIALTY, x("prismite_cluster", 1, 10), c("cactus", 1, 1));
		regional(Culture.MEDITERRANEAN, Goods.WOOD, x("olive_log", 4, 4), c("oak_log", 4, 3), c("dark_oak_log", 4, 3));
		regional(Culture.MEDITERRANEAN, Goods.LOGS, x("olive_log", 1, 2), c("oak_log", 1, 1));
		regional(Culture.MEDITERRANEAN, Goods.STONE, c("stone", 1, 1), c("diorite", 1, 1), c("calcite", 1, 2));
		regional(Culture.MEDITERRANEAN, Goods.DRESSED_STONE, c("smooth_stone", 1, 2), c("polished_diorite", 1, 2), c("stone_bricks", 1, 2));
		regional(Culture.MEDITERRANEAN, Goods.BRICK, c("terracotta", 1, 2), c("orange_terracotta", 1, 2), c("bricks", 1, 3));
		regional(Culture.MEDITERRANEAN, Goods.FOOD, c("bread", 1, 3), c("cooked_cod", 1, 3), c("sweet_berries", 1, 1));
		regional(Culture.MEDITERRANEAN, Goods.DYE, c("blue_dye", 1, 3), c("yellow_dye", 1, 2), c("light_blue_dye", 1, 3));
		regional(Culture.MEDITERRANEAN, Goods.PRODUCE, c("sweet_berries", 1, 1), c("apple", 1, 2), c("melon_slice", 1, 1));
		regional(Culture.MEDITERRANEAN, Goods.SPECIALTY, x("lavender", 1, 3), c("honey_bottle", 1, 6));
		regional(Culture.STEPPE, Goods.WOOD, x("baobab_log", 4, 4), c("acacia_log", 4, 4), x("eucalyptus_log", 4, 4));
		regional(Culture.STEPPE, Goods.LOGS, x("baobab_log", 1, 2), c("acacia_log", 1, 2));
		regional(Culture.STEPPE, Goods.BRICK, c("terracotta", 1, 2), c("orange_terracotta", 1, 2), c("mud_bricks", 1, 2));
		regional(Culture.STEPPE, Goods.CLOTH, c("orange_wool", 1, 3), c("white_wool", 1, 3));
		regional(Culture.STEPPE, Goods.LEATHER, c("leather", 1, 4));
		regional(Culture.STEPPE, Goods.FOOD, c("cooked_beef", 1, 4), c("bread", 1, 3), c("cooked_mutton", 1, 3));
		regional(Culture.STEPPE, Goods.MEAT, c("beef", 1, 2), c("mutton", 1, 2));
		regional(Culture.STEPPE, Goods.PRODUCE, c("carrot", 1, 1), c("melon_slice", 1, 1));
		regional(Culture.STEPPE, Goods.SPECIALTY, x("spinifex", 1, 2), c("leather", 1, 4));
		regional(Culture.KARST, Goods.WOOD, c("bamboo_block", 2, 2), x("teak_log", 4, 4), c("jungle_log", 4, 3));
		regional(Culture.KARST, Goods.LOGS, x("teak_log", 1, 2), c("jungle_log", 1, 1), c("bamboo_block", 1, 1));
		regional(Culture.KARST, Goods.STONE, x("limestone", 1, 1), c("stone", 1, 1));
		regional(Culture.KARST, Goods.DRESSED_STONE, x("limestone_bricks", 1, 2), x("polished_limestone", 1, 2), c("stone_bricks", 1, 2));
		regional(Culture.KARST, Goods.FOOD, c("cooked_cod", 1, 3), c("bread", 1, 3), c("melon_slice", 1, 1));
		regional(Culture.KARST, Goods.FISH, c("cod", 1, 2), c("salmon", 1, 2));
		regional(Culture.KARST, Goods.LUXURY, c("book", 2, 12), c("candle", 1, 4), c("lantern", 2, 8));
		regional(Culture.KARST, Goods.DYE, c("red_dye", 1, 2), c("black_dye", 1, 3));
		regional(Culture.KARST, Goods.PRODUCE, c("melon_slice", 1, 1), c("sweet_berries", 1, 1));
		regional(Culture.KARST, Goods.SPECIALTY, c("bamboo", 1, 1), x("glowcap", 1, 4));
		regional(Culture.BAYOU, Goods.WOOD, x("willow_log", 4, 3), c("mangrove_log", 4, 3), c("oak_log", 4, 3));
		regional(Culture.BAYOU, Goods.LOGS, x("willow_log", 1, 1), c("mangrove_log", 1, 1));
		regional(Culture.BAYOU, Goods.BRICK, c("mud_bricks", 1, 2), c("packed_mud", 1, 1));
		regional(Culture.BAYOU, Goods.CLAY, c("mud", 1, 1), c("clay", 1, 2));
		regional(Culture.BAYOU, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), c("baked_potato", 1, 2));
		regional(Culture.BAYOU, Goods.FISH, c("cod", 1, 2), c("salmon", 1, 2));
		regional(Culture.BAYOU, Goods.PRODUCE, c("potato", 1, 1), c("beetroot", 1, 1));
		regional(Culture.BAYOU, Goods.SPECIALTY, x("spanish_moss", 1, 2), x("cattail", 1, 2));
		regional(Culture.MESA, Goods.WOOD, c("acacia_log", 4, 4), c("dark_oak_log", 4, 4));
		regional(Culture.MESA, Goods.LOGS, c("acacia_log", 1, 2), c("dark_oak_log", 1, 2));
		regional(Culture.MESA, Goods.STONE, c("red_sandstone", 1, 1), c("sandstone", 1, 1));
		regional(Culture.MESA, Goods.DRESSED_STONE, c("cut_red_sandstone", 1, 2), c("smooth_red_sandstone", 1, 2));
		regional(Culture.MESA, Goods.BRICK, c("terracotta", 1, 2), c("red_terracotta", 1, 2), c("orange_terracotta", 1, 2));
		regional(Culture.MESA, Goods.SAND, c("red_sand", 1, 1));
		regional(Culture.MESA, Goods.ORE, c("raw_gold", 1, 8), c("raw_iron", 1, 4));
		regional(Culture.MESA, Goods.FOOD, c("bread", 1, 3), c("cooked_beef", 1, 4), c("baked_potato", 1, 2));
		regional(Culture.MESA, Goods.DYE, c("orange_dye", 1, 3), c("red_dye", 1, 2));
		regional(Culture.MESA, Goods.SPECIALTY, x("red_earth", 1, 2), c("gold_nugget", 1, 2));
	}

	private GoodsItems() {
	}

	/**
	 * A concrete item for the goods, in the culture's materials, among those that exist (Expanse choices drop out without
	 * Expanse); null if none of the choices exists (salt without Expanse), and the quest is then not made.
	 */
	public static @Nullable Choice pick(Culture culture, Goods goods, RandomSource random, Predicate<Identifier> exists) {
		List<Choice> pool = new ArrayList<>();
		Map<Goods, List<Choice>> regional = REGIONAL.get(culture);
		if (regional != null && regional.containsKey(goods)) {
			for (Choice ch : regional.get(goods)) {
				if (exists.test(ch.item)) {
					pool.add(ch);
				}
			}
		}
		if (pool.isEmpty()) {
			for (Choice ch : COMMON.get(goods)) {
				if (exists.test(ch.item)) {
					pool.add(ch);
				}
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		// The first regional choice is the town's own material; favour it.
		return random.nextInt(3) == 0 ? pool.get(random.nextInt(pool.size())) : pool.getFirst();
	}

	/** Every choice for the goods (regional first), for tests and the docs. */
	public static List<Choice> choices(Culture culture, Goods goods) {
		List<Choice> out = new ArrayList<>(REGIONAL.getOrDefault(culture, Map.of()).getOrDefault(goods, List.of()));
		out.addAll(COMMON.get(goods));
		return out;
	}

	/** Every item named anywhere, for tests. */
	public static java.util.Set<Identifier> allItems() {
		return java.util.Collections.unmodifiableSet(BASE_BITS.keySet());
	}

	/** The goods and units an item counts as, if it is one of the choices; null otherwise. */
	public static @Nullable Choice find(Identifier item) {
		Goods goods = GOODS_OF.get(item);
		if (goods == null) {
			return null;
		}
		for (Choice ch : COMMON.get(goods)) {
			if (ch.item.equals(item)) {
				return ch;
			}
		}
		for (Map<Goods, List<Choice>> m : REGIONAL.values()) {
			for (Choice ch : m.getOrDefault(goods, List.of())) {
				if (ch.item.equals(item)) {
					return ch;
				}
			}
		}
		return null;
	}

	/** The goods an item counts as when brought to a settlement, if it is one of the choices. */
	public static @Nullable Goods goodsOf(Identifier item) {
		return GOODS_OF.get(item);
	}

	/** The units one item is worth as these goods (a log is 4 units of sawn timber, 1 of logs), or 0 if it is no choice. */
	public static int units(Identifier item, Goods goods) {
		for (Choice ch : COMMON.get(goods)) {
			if (ch.item.equals(item)) {
				return ch.units;
			}
		}
		for (Map<Goods, List<Choice>> m : REGIONAL.values()) {
			for (Choice ch : m.getOrDefault(goods, List.of())) {
				if (ch.item.equals(item)) {
					return ch.units;
				}
			}
		}
		return 0;
	}

	/** A base price in bits for one item, for rewards without the economy module (2 for items it does not know). */
	public static int baseBits(Identifier item) {
		return BASE_BITS.getOrDefault(item, 2);
	}

	private static Choice c(String path, int units, int bits) {
		Choice ch = new Choice(Identifier.withDefaultNamespace(path), units, bits);
		BASE_BITS.putIfAbsent(ch.item, bits);
		return ch;
	}

	private static Choice x(String path, int units, int bits) {
		Choice ch = new Choice(Identifier.fromNamespaceAndPath("expanse", path), units, bits);
		BASE_BITS.putIfAbsent(ch.item, bits);
		return ch;
	}

	private static void common(Goods goods, Choice... choices) {
		COMMON.put(goods, List.of(choices));
		for (Choice ch : choices) {
			GOODS_OF.putIfAbsent(ch.item, goods);
		}
	}

	private static void regional(Culture culture, Goods goods, Choice... choices) {
		REGIONAL.computeIfAbsent(culture, k -> new EnumMap<>(Goods.class)).put(goods, List.of(choices));
		for (Choice ch : choices) {
			GOODS_OF.putIfAbsent(ch.item, goods);
		}
	}
}
