package dev.voidmc.lod.mixin;

import dev.voidmc.lod.gui.LodSettingsScreen;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A way to the LOD's settings from vanilla's Options screen: a row of its own under the grid of buttons.
 * Mods that replace Video Settings with their own screen (Sodium) leave this one alone, so the settings
 * stay reachable with them too.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
	@ModifyArg(
		method = "init",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;")
	)
	private LayoutElement voidLod$addSettingsButton(LayoutElement contents) {
		if (contents instanceof GridLayout grid) {
			// two buttons a row: the next row is under the last, spanning both columns
			int[] children = new int[1];
			grid.visitChildren(child -> children[0]++);
			grid.addChild(LodSettingsScreen.button((Screen) (Object) this, 308), (children[0] + 1) / 2, 0, 1, 2);
		}
		return contents;
	}
}
