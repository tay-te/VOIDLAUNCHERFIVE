package dev.voidmc.mettle.client.mixin.gear;

import dev.voidmc.mettle.client.gear.GearPanel;
import dev.voidmc.mettle.gear.menu.GearSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative's inventory tab lays slots out by index, so indices past 45 would land on the hotbar row. The three worn
 * slots go right of the armour; the pockets are hidden there (BURDEN.md §6).
 */
@Mixin(CreativeModeInventoryScreen.class)
abstract class GearCreativeScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
	GearCreativeScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title, int w, int h) {
		super(menu, inventory, title, w, h);
	}

	@Inject(method = "selectTab", at = @At("TAIL"))
	private void mettle$placeGearSlots(CreativeModeTab tab, CallbackInfo ci) {
		if (!((CreativeModeInventoryScreen) (Object) this).isInventoryOpen()) {
			return;
		}
		for (Slot slot : menu.slots) {
			if (slot.container instanceof GearSlots.GearContainer) {
				GearPanel.placeForCreative(slot);
			}
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void mettle$wornSlotBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (((CreativeModeInventoryScreen) (Object) this).isInventoryOpen()) {
			GearPanel.drawWornSlots(graphics, leftPos, topPos, GearPanel.CREATIVE_X, GearPanel.CREATIVE_Y);
		}
	}
}
