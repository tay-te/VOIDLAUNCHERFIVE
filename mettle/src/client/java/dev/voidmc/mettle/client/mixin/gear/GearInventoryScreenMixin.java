package dev.voidmc.mettle.client.mixin.gear;

import dev.voidmc.mettle.client.gear.GearPanel;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The survival inventory: backgrounds for the three worn slots inside the frame (the pocket panel is drawn for every
 * container screen by GearContainerScreenMixin). */
@Mixin(InventoryScreen.class)
abstract class GearInventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
	GearInventoryScreenMixin(InventoryMenu menu, RecipeBookComponent<?> book, Inventory inventory, Component title) {
		super(menu, book, inventory, title);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void mettle$gearBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		GearPanel.drawWornSlots(graphics, leftPos, topPos, GearPanel.WORN_X, GearSlots.WORN_Y);
	}
}
