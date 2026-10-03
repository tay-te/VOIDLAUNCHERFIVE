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
 * Turns the abstract goods a stalled build is short of into concrete items a player can bring, in the settlement's own
 * materials: a northern town short of wood asks for spruce or redwood logs, a desert town for acacia, a bayou for willow
 * or mangrove. Expanse items are used when Expanse is loaded and fall back to vanilla otherwise.
 *
 * <p>Each choice carries the goods units one item is worth (a log is four planks' worth of wood) and a base price in
 * bits, used for rewards only when the economy module is absent.
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
		common(Goods.WOOD, c("oak_log", 4, 3), c("spruce_log", 4, 3));
		common(Goods.STONE, c("cobblestone", 1, 1), c("stone_bricks", 1, 1));
		common(Goods.CLAY, c("bricks", 1, 2), c("terracotta", 1, 2));
		common(Goods.GLASS, c("glass", 1, 2));
		common(Goods.METAL, c("iron_ingot", 1, 8), c("copper_ingot", 1, 3));
		common(Goods.CLOTH, c("white_wool", 1, 3), c("leather", 1, 4));
		common(Goods.FUEL, c("coal", 1, 2), c("charcoal", 1, 2));
		common(Goods.TOOLS, c("iron_pickaxe", 4, 30), c("iron_axe", 4, 30), c("iron_shovel", 2, 12));
		common(Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2));
		common(Goods.LUXURY, c("book", 2, 12), c("candle", 1, 4), c("honey_bottle", 1, 6));

		regional(Culture.TEMPERATE, Goods.WOOD, c("oak_log", 4, 3), c("birch_log", 4, 3), x("aspen_log", 4, 3), x("beech_log", 4, 3));
		regional(Culture.TEMPERATE, Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2), c("apple", 1, 2));
		regional(Culture.WISTERIA, Goods.WOOD, x("wisteria_log", 4, 4), c("cherry_log", 4, 4), c("oak_log", 4, 3));
		regional(Culture.WISTERIA, Goods.CLAY, c("terracotta", 1, 2), c("white_terracotta", 1, 2));
		regional(Culture.WISTERIA, Goods.LUXURY, c("book", 2, 12), c("honey_bottle", 1, 6), c("pink_dye", 1, 3));
		regional(Culture.NORTHERN, Goods.WOOD, x("redwood_log", 4, 4), c("spruce_log", 4, 3), x("pine_log", 4, 3));
		regional(Culture.NORTHERN, Goods.FOOD, x("cooked_venison", 1, 4), c("bread", 1, 3), c("cooked_salmon", 1, 3));
		regional(Culture.NORTHERN, Goods.CLOTH, c("leather", 1, 4), c("brown_wool", 1, 3));
		regional(Culture.ALPINE, Goods.WOOD, c("spruce_log", 4, 3), x("pine_log", 4, 3), x("larch_log", 4, 3));
		regional(Culture.ALPINE, Goods.STONE, c("cobblestone", 1, 1), c("stone", 1, 1), c("stone_bricks", 1, 1));
		regional(Culture.ALPINE, Goods.FOOD, c("bread", 1, 3), c("cooked_mutton", 1, 3), c("pumpkin_pie", 2, 5));
		regional(Culture.TUNDRA, Goods.WOOD, c("spruce_log", 4, 4), x("larch_log", 4, 4));
		regional(Culture.TUNDRA, Goods.CLOTH, c("brown_wool", 1, 3), c("leather", 1, 4), c("white_wool", 1, 3));
		regional(Culture.TUNDRA, Goods.FUEL, c("coal", 1, 3), c("charcoal", 1, 3));
		regional(Culture.TUNDRA, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), x("cooked_venison", 1, 4));
		regional(Culture.MOORLAND, Goods.WOOD, c("oak_log", 4, 4), c("spruce_log", 4, 4), c("birch_log", 4, 4));
		regional(Culture.MOORLAND, Goods.STONE, c("cobblestone", 1, 1), c("mossy_cobblestone", 1, 2), c("stone_bricks", 1, 1));
		regional(Culture.MOORLAND, Goods.CLOTH, c("white_wool", 1, 3), c("gray_wool", 1, 3));
		regional(Culture.MOORLAND, Goods.FOOD, c("cooked_mutton", 1, 3), c("bread", 1, 3));
		regional(Culture.MARITIME, Goods.WOOD, c("spruce_log", 4, 3), c("oak_log", 4, 3), c("dark_oak_log", 4, 3));
		regional(Culture.MARITIME, Goods.STONE, c("stone_bricks", 1, 1), c("cobblestone", 1, 1), c("andesite", 1, 1));
		regional(Culture.MARITIME, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), c("dried_kelp", 1, 1));
		regional(Culture.MARITIME, Goods.CLOTH, c("white_wool", 1, 3), c("string", 1, 1));
		regional(Culture.TROPICAL, Goods.WOOD, x("palm_log", 4, 3), c("jungle_log", 4, 3), x("kapok_log", 4, 3));
		regional(Culture.TROPICAL, Goods.FOOD, c("cooked_cod", 1, 3), c("melon_slice", 1, 1), x("cooked_crab", 1, 3), c("cocoa_beans", 1, 2));
		regional(Culture.TROPICAL, Goods.CLOTH, c("string", 1, 1), c("white_wool", 1, 3));
		regional(Culture.DESERT, Goods.WOOD, c("acacia_log", 4, 5), x("palm_log", 4, 5));
		regional(Culture.DESERT, Goods.STONE, c("sandstone", 1, 1), c("smooth_sandstone", 1, 2), x("opal_sandstone", 1, 2));
		regional(Culture.DESERT, Goods.CLAY, c("mud_bricks", 1, 2), c("terracotta", 1, 2));
		regional(Culture.DESERT, Goods.FOOD, c("bread", 1, 3), c("baked_potato", 1, 2), c("sweet_berries", 1, 1));
		regional(Culture.MEDITERRANEAN, Goods.WOOD, x("olive_log", 4, 4), c("oak_log", 4, 3), c("dark_oak_log", 4, 3));
		regional(Culture.MEDITERRANEAN, Goods.STONE, c("stone_bricks", 1, 1), c("smooth_stone", 1, 2), c("calcite", 1, 2));
		regional(Culture.MEDITERRANEAN, Goods.CLAY, c("terracotta", 1, 2), c("orange_terracotta", 1, 2), c("bricks", 1, 2));
		regional(Culture.MEDITERRANEAN, Goods.FOOD, c("bread", 1, 3), c("cooked_cod", 1, 3), c("sweet_berries", 1, 1));
		regional(Culture.STEPPE, Goods.WOOD, x("baobab_log", 4, 4), c("acacia_log", 4, 4), x("eucalyptus_log", 4, 4));
		regional(Culture.STEPPE, Goods.CLAY, c("terracotta", 1, 2), c("orange_terracotta", 1, 2), c("mud_bricks", 1, 2));
		regional(Culture.STEPPE, Goods.CLOTH, c("leather", 1, 4), c("orange_wool", 1, 3));
		regional(Culture.STEPPE, Goods.FOOD, c("cooked_beef", 1, 4), c("bread", 1, 3), c("cooked_mutton", 1, 3));
		regional(Culture.KARST, Goods.WOOD, c("bamboo_block", 2, 2), x("teak_log", 4, 4), c("jungle_log", 4, 3));
		regional(Culture.KARST, Goods.STONE, x("limestone", 1, 1), x("limestone_bricks", 1, 2), c("stone_bricks", 1, 1));
		regional(Culture.KARST, Goods.FOOD, c("cooked_cod", 1, 3), c("bread", 1, 3), c("melon_slice", 1, 1));
		regional(Culture.KARST, Goods.LUXURY, c("book", 2, 12), c("candle", 1, 4), c("lantern", 2, 8));
		regional(Culture.BAYOU, Goods.WOOD, x("willow_log", 4, 3), c("mangrove_log", 4, 3), c("oak_log", 4, 3));
		regional(Culture.BAYOU, Goods.CLAY, c("mud_bricks", 1, 2), c("packed_mud", 1, 1));
		regional(Culture.BAYOU, Goods.FOOD, c("cooked_cod", 1, 3), c("cooked_salmon", 1, 3), c("baked_potato", 1, 2));
		regional(Culture.MESA, Goods.WOOD, c("acacia_log", 4, 4), c("dark_oak_log", 4, 4));
		regional(Culture.MESA, Goods.STONE, c("red_sandstone", 1, 1), c("smooth_red_sandstone", 1, 2));
		regional(Culture.MESA, Goods.CLAY, c("terracotta", 1, 2), c("red_terracotta", 1, 2), c("orange_terracotta", 1, 2));
		regional(Culture.MESA, Goods.FOOD, c("bread", 1, 3), c("cooked_beef", 1, 4), c("baked_potato", 1, 2));
	}

	private GoodsItems() {
	}

	/**
	 * A concrete item for the goods, in the culture's materials, among those that exist (Expanse choices drop out without
	 * Expanse). Never null: falls back to the common choices.
	 */
	public static Choice pick(Culture culture, Goods goods, RandomSource random, Predicate<Identifier> exists) {
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
			pool.add(COMMON.get(goods).getFirst());
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

	/** The goods and units an item counts as, if it is one of the choices; null otherwise. */
	public static @Nullable Choice find(Identifier item) {
		for (List<Choice> list : COMMON.values()) {
			for (Choice ch : list) {
				if (ch.item.equals(item)) {
					return ch;
				}
			}
		}
		for (Map<Goods, List<Choice>> m : REGIONAL.values()) {
			for (List<Choice> list : m.values()) {
				for (Choice ch : list) {
					if (ch.item.equals(item)) {
						return ch;
					}
				}
			}
		}
		return null;
	}

	/** The goods an item counts as when brought to a settlement, if it is one of the choices. */
	public static @Nullable Goods goodsOf(Identifier item) {
		return GOODS_OF.get(item);
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
