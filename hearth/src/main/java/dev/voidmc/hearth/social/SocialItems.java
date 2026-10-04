package dev.voidmc.hearth.social;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.HearthTab;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The social module's items: loaded dice, weighted toward ones and fives. Carry them to the dice table and you may
 * play them instead of the honest dice; each roll risks the folk noticing (docs/SOCIAL.md §5). Crafted from two bones
 * and an iron nugget; taken from you if you are caught.
 */
public final class SocialItems {
	public static final Item LOADED_DICE = register("loaded_dice");

	private SocialItems() {
	}

	public static void init() {
		HearthTab.add(LOADED_DICE);
	}

	public static boolean carriesLoadedDice(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(LOADED_DICE)) {
				return true;
			}
		}
		return false;
	}

	/** Caught: the dice are taken (one set). */
	public static void confiscateLoadedDice(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(LOADED_DICE)) {
				st.shrink(1);
				inv.setChanged();
				return;
			}
		}
	}

	private static Item register(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hearth.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new LoadedDice(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(key)));
	}

	static final class LoadedDice extends Item {
		LoadedDice(Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.hearth.loaded_dice.desc").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.hearth.loaded_dice.warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
	}
}
