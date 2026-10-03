package dev.voidmc.hearth.quest;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.HearthTab;
import dev.voidmc.hearth.quest.block.NoticeBoardBlock;
import dev.voidmc.hearth.quest.item.JournalItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** The quest module's block and items: the notice board and the journal. */
public final class QuestRegistry {
	public static final Block NOTICE_BOARD = registerBlock("notice_board", NoticeBoardBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.PODZOL)
		.instrument(NoteBlockInstrument.BASS)
		.strength(2.0F, 3.0F)
		.sound(SoundType.WOOD)
		.ignitedByLava()
		.noOcclusion());
	public static final Item NOTICE_BOARD_ITEM = registerItem("notice_board", p -> new BlockItem(NOTICE_BOARD, p),
		new Item.Properties().useBlockDescriptionPrefix());
	public static final Item JOURNAL = registerItem("journal", JournalItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	private QuestRegistry() {
	}

	public static void init() {
		HearthTab.add(NOTICE_BOARD_ITEM);
		HearthTab.add(JOURNAL);
	}

	private static Block registerBlock(String id, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hearth.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerItem(String id, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearth.id(id));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
