package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * What things are worth and what kind of goods they are, from {@link PriceData} and the item tags
 * {@code #hearth:goods/<goods>}. Lookups are cached per item and the caches are dropped whenever data or tags reload.
 *
 * <p>An item's base value is, in order: its own entry; the first tag entry it matches (later files win); otherwise a
 * guess from its goods kind and rarity, so items from any mod get a sensible price.
 */
public final class PriceTable {
	private static final Map<Goods, TagKey<Item>> GOODS_TAGS = new EnumMap<>(Goods.class);
	private static final Map<Item, Double> BASE = new ConcurrentHashMap<>();
	private static final Map<Item, Goods> GOODS = new ConcurrentHashMap<>();
	private static final Map<Item, Boolean> UNGOODED = new ConcurrentHashMap<>();
	private static final Map<Long, List<Item>> WARES = new ConcurrentHashMap<>();
	private static volatile PriceData data = PriceData.EMPTY;

	static {
		for (Goods g : Goods.VALUES) {
			GOODS_TAGS.put(g, TagKey.create(Registries.ITEM, Hearth.id("goods/" + g.getSerializedName())));
		}
	}

	private PriceTable() {
	}

	public static PriceData data() {
		return data;
	}

	public static void setData(PriceData d) {
		data = d;
		invalidate();
	}

	/** Drops every cached lookup (data or tags changed). */
	public static void invalidate() {
		BASE.clear();
		GOODS.clear();
		UNGOODED.clear();
		WARES.clear();
	}

	public static TagKey<Item> tag(Goods goods) {
		return GOODS_TAGS.get(goods);
	}

	/** The goods this item counts as, or null if no market deals in it. */
	public static @Nullable Goods goods(Item item) {
		Goods cached = GOODS.get(item);
		if (cached != null || UNGOODED.containsKey(item)) {
			return cached;
		}
		ItemStack probe = new ItemStack(item);
		for (Goods g : Goods.VALUES) {
			if (probe.is(GOODS_TAGS.get(g))) {
				GOODS.put(item, g);
				return g;
			}
		}
		UNGOODED.put(item, Boolean.TRUE);
		return null;
	}

	/** Base value of one of this item, new and plain, in bits. */
	public static double base(Item item) {
		Double cached = BASE.get(item);
		if (cached != null) {
			return cached;
		}
		double v = computeBase(item);
		BASE.put(item, v);
		return v;
	}

	/**
	 * Base value of one item of this stack: worn tools are worth their remaining durability (at least a fifth), and each
	 * enchantment level adds a fifth.
	 */
	public static double base(ItemStack stack) {
		double v = base(stack.getItem());
		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			v *= Math.max(0.2, 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage());
		}
		ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		int levels = 0;
		for (var e : ench.entrySet()) {
			levels += e.getIntValue();
		}
		return v * (1 + 0.2 * levels);
	}

	/**
	 * Stockpile units one of this item is worth: its value against its goods' unit (a log is four planks of WOOD, a block
	 * of iron nine ingots of METAL), so a town's stores and its prices agree. At least 1, at most 64.
	 */
	public static int units(Item item) {
		Goods g = goods(item);
		if (g == null) {
			return 1;
		}
		return Mth.clamp((int) Math.round(base(item) / Math.max(1e-6, data.unitValue(g))), 1, 64);
	}

	/**
	 * What a culture's market sells for this goods: its own wares, else the default wares, resolved to items that exist
	 * (so Expanse wood is sold where Expanse is installed, vanilla wood where it is not).
	 */
	public static List<Item> wares(Culture culture, Goods goods) {
		long key = (long) culture.ordinal() << 8 | goods.ordinal();
		return WARES.computeIfAbsent(key, k -> {
			PriceData d = data;
			PriceData.CultureEntry entry = d.cultures().get(culture);
			List<String> names = entry != null && entry.wares().containsKey(goods) ? entry.wares().get(goods) : d.defaultWares().getOrDefault(goods, List.of());
			List<Item> items = new ArrayList<>();
			for (String name : names) {
				Item item = resolve(name);
				if (item != null && !items.contains(item)) {
					items.add(item);
				}
			}
			return List.copyOf(items);
		});
	}

	/** The culture's price factor for this goods (1 if the data says nothing). */
	public static double speciality(Culture culture, Goods goods) {
		PriceData.CultureEntry entry = data.cultures().get(culture);
		return entry == null ? 1.0 : entry.speciality(goods);
	}

	/** {@code "a|b|c"}: the first alternative that is a registered item. */
	static @Nullable Item resolve(String alternatives) {
		for (String name : alternatives.split("\\|")) {
			Identifier id = Identifier.tryParse(name.trim());
			if (id != null) {
				Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item != null && item != Items.AIR) {
					return item;
				}
			}
		}
		return null;
	}

	private static double computeBase(Item item) {
		PriceData d = data;
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		Double own = d.items().get(id);
		if (own != null) {
			return own;
		}
		ItemStack probe = new ItemStack(item);
		// Later tag entries win, so walk backwards and take the first match.
		for (int i = d.tags().size() - 1; i >= 0; i--) {
			PriceData.TagValue tv = d.tags().get(i);
			if (probe.is(TagKey.create(Registries.ITEM, tv.tag()))) {
				return tv.value();
			}
		}
		Goods g = goods(item);
		double guess = g == null ? d.untaggedDefault() : d.goodsDefault(g);
		return guess * d.rarity(probe.getRarity());
	}
}
