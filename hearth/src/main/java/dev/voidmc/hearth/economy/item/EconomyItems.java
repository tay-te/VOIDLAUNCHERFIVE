package dev.voidmc.hearth.economy.item;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.HearthTab;
import dev.voidmc.hearth.economy.coin.Coin;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jspecify.annotations.Nullable;

/** The economy's items: three coins, the coin pouch and the deed. */
public final class EconomyItems {
	private static final Map<Coin, CoinItem> COINS = new EnumMap<>(Coin.class);

	public static final CoinItem COPPER_BIT = coin(Coin.BIT, Rarity.COMMON);
	public static final CoinItem IRON_MARK = coin(Coin.MARK, Rarity.COMMON);
	public static final CoinItem GOLD_CROWN = coin(Coin.CROWN, Rarity.UNCOMMON);
	public static final CoinPouchItem COIN_POUCH = register("coin_pouch", CoinPouchItem::new,
		new Item.Properties().stacksTo(1).component(EconomyComponents.POUCH_CONTENTS, PouchContents.EMPTY));
	public static final DeedItem DEED = register("deed", DeedItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	private EconomyItems() {
	}

	public static void init() {
		COINS.values().forEach(HearthTab::add);
		HearthTab.add(COIN_POUCH);
		HearthTab.setIcon(GOLD_CROWN);
	}

	public static CoinItem coin(Coin coin) {
		return COINS.get(coin);
	}

	/** The coin this stack is, or null if it is not a coin. */
	public static @Nullable Coin coinOf(ItemStack stack) {
		return stack.getItem() instanceof CoinItem c ? c.coin() : null;
	}

	public static boolean isPouch(ItemStack stack) {
		return stack.getItem() instanceof CoinPouchItem;
	}

	private static CoinItem coin(Coin coin, Rarity rarity) {
		CoinItem item = register(coin.id, p -> new CoinItem(coin, p), new Item.Properties().rarity(rarity));
		COINS.put(coin, item);
		return item;
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearth.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
