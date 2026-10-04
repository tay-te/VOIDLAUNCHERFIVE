package dev.voidmc.hearth.law.item;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.HearthTab;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** The law's items: lockpicks (a thief's tools, they snap) and keys (a household's, stealable). */
public final class LawItems {
	public static final Item LOCKPICK = register("lockpick", Item::new, new Item.Properties().stacksTo(16));
	public static final Item KEY = register("key", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	private LawItems() {
	}

	public static void init() {
		HearthTab.add(LOCKPICK);
	}

	/** A key to every lock of a plot. */
	public static ItemStack key(KeyData data) {
		ItemStack stack = new ItemStack(KEY);
		stack.set(LawComponents.KEY, data);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
			Component.translatable("item.hearth.key.named", data.label()).withStyle(s -> s.withItalic(false)));
		return stack;
	}

	private static <T extends Item> T register(String id, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearth.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
