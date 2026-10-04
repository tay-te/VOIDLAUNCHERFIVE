package dev.voidmc.hub.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import dev.voidmc.hub.HubConfig;
import dev.voidmc.hub.screen.HubChestScreen;
import dev.voidmc.hub.screen.HubInventoryScreen;
import dev.voidmc.hub.screen.HubScreen;
import dev.voidmc.hub.ui.HubView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Two small hooks on {@link Gui}:
 *
 * <ul>
 *   <li>{@code setScreen}: when the VOID inventory is on, vanilla's survival inventory and plain
 *       chest screens are swapped for the VOID ones over the <em>same menu</em>, so the menu,
 *       its container id and every click stay vanilla's. Exact classes only — a modded subclass
 *       or any other container keeps its own screen.</li>
 *   <li>The HUD is not drawn under a VOID screen (the design's shell replaces it).</li>
 * </ul>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private Screen screen;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen voidhub$swapScreen(Screen screen) {
		if (screen == null || !HubConfig.replaceInventory() || this.minecraft.player == null) {
			return screen;
		}
		if (screen.getClass() == InventoryScreen.class && !this.minecraft.player.hasInfiniteMaterials() && HubView.available()) {
			return new HubInventoryScreen(this.minecraft.player);
		}
		if (screen.getClass() == ContainerScreen.class && HubView.available()) {
			ContainerScreen chest = (ContainerScreen) screen;
			return new HubChestScreen(chest.getMenu(), this.minecraft.player.getInventory(), chest.getTitle());
		}
		return screen;
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private boolean voidhub$hideHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		return !(this.screen instanceof HubScreen<?> && HubConfig.hideHud());
	}
}
