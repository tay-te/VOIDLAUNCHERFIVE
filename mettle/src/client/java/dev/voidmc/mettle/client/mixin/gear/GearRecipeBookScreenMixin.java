package dev.voidmc.mettle.client.mixin.gear;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.client.gear.GearPanel;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A click on the gear panel is a click inside the inventory, not a throw of the carried stack. */
@Mixin(AbstractRecipeBookScreen.class)
abstract class GearRecipeBookScreenMixin {
	@ModifyReturnValue(method = "hasClickedOutside", at = @At("RETURN"))
	private boolean mettle$panelIsInside(boolean outside, double mouseX, double mouseY, int left, int top) {
		return outside && !GearPanel.contains(this, mouseX, mouseY, left, top);
	}
}
