package dev.voidmc.hearth;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** The Hearth creative tab. Modules call {@link #add} from their init; the tab lists items in the order added. */
public final class HearthTab {
	private static final List<ItemLike> ITEMS = new ArrayList<>();
	private static ItemLike icon = Items.BELL;

	private HearthTab() {
	}

	public static void add(ItemLike item) {
		ITEMS.add(item);
	}

	/** The economy module sets a coin here. */
	public static void setIcon(ItemLike item) {
		icon = item;
	}

	static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Hearth.id("hearth"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.hearth"))
			.icon(() -> new ItemStack(icon))
			.displayItems((parameters, output) -> ITEMS.forEach(output::accept))
			.build());
	}
}
