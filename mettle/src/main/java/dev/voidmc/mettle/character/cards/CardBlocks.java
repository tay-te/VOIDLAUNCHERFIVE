package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** The Card Shrine block and its item (docs/CARDS.md §7.3). Crafted: a lectern, 4 amethyst shards, 2 gold ingots, 2 polished deepslate. */
public final class CardBlocks {
	public static final Block CARD_SHRINE = registerBlock("card_shrine", CardShrineBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DEEPSLATE)
		.strength(2.5F, 6.0F)
		.sound(SoundType.POLISHED_DEEPSLATE)
		.requiresCorrectToolForDrops()
		.lightLevel(s -> 3)
		.noOcclusion());
	public static final Item CARD_SHRINE_ITEM = registerItem("card_shrine", p -> new BlockItem(CARD_SHRINE, p), new Item.Properties().useBlockDescriptionPrefix());

	private CardBlocks() {
	}

	static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(CARD_SHRINE_ITEM));
	}

	private static Block registerBlock(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Mettle.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerItem(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mettle.id(id));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
