package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

public final class ExpanseItems {
	/** Everything that goes in the creative tab, in registration order. */
	static final List<Item> TAB = new ArrayList<>();

	public static final Item VENISON = register("venison", Item::new,
		new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
	public static final Item COOKED_VENISON = register("cooked_venison", Item::new,
		new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.9F).build()));
	public static final Item CRAB_MEAT = register("crab_meat", Item::new,
		new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build()));
	public static final Item COOKED_CRAB = register("cooked_crab", Item::new,
		new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build()));

	private ExpanseItems() {
	}

	public static void init() {
	}

	static Item registerBlockItem(String id, Block block, UnaryOperator<Item.Properties> properties) {
		return registerBlockItem(id, block, (b, p) -> new BlockItem(b, properties.apply(p)));
	}

	static Item registerBlockItem(String id, Block block, BiFunction<Block, Item.Properties, Item> factory) {
		return register(id, p -> factory.apply(block, p), new Item.Properties().useBlockDescriptionPrefix());
	}

	public static Item registerSpawnEgg(String id, EntityType<?> type) {
		return register(id, SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}

	static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Expanse.id(id));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		TAB.add(item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
