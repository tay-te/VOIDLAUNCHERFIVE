package dev.voidmc.mettle.client.mixin.gear;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.client.gear.GearPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Any container screen whose menu carries the gear pockets (inventory, chest, shulker box, crafting table) draws the
 * fallback pocket panel beside its frame, after the background and before the slots; a click on the panel counts as
 * inside.
 */
@Mixin(AbstractContainerScreen.class)
abstract class GearContainerScreenMixin extends Screen {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	@Final
	protected int imageWidth;
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	GearContainerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "extractContents", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = At.Shift.AFTER))
	private void mettle$pocketPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		GearPanel.layoutAndDraw(graphics, font, this, menu, minecraft.player, leftPos, topPos, width, imageWidth);
	}

	@ModifyReturnValue(method = "hasClickedOutside", at = @At("RETURN"))
	private boolean mettle$panelIsInside(boolean outside, double mouseX, double mouseY, int left, int top) {
		return outside && !GearPanel.contains(this, mouseX, mouseY, left, top);
	}
}
