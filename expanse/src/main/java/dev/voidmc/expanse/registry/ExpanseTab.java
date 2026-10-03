package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.Expanse;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ExpanseTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Expanse.id("expanse"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.expanse"))
			.icon(() -> new ItemStack(ExpanseBlocks.WISTERIA.sapling))
			.displayItems((parameters, output) -> ExpanseItems.TAB.forEach(output::accept))
			.build());

	private ExpanseTab() {
	}

	public static void init() {
	}
}
