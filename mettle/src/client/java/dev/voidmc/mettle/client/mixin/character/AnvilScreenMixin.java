package dev.voidmc.mettle.client.mixin.character;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Smith's Eye, client half: show the real cost instead of "Too Expensive!", matching what the server allows. */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
	@ModifyExpressionValue(method = "extractLabels", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;hasInfiniteMaterials()Z"))
	private boolean mettle$smithsEye(boolean infinite) {
		Minecraft mc = Minecraft.getInstance();
		return infinite || (mc.player != null && SignaturePerks.has(mc.player, SignaturePerks.SMITHS_EYE));
	}
}
