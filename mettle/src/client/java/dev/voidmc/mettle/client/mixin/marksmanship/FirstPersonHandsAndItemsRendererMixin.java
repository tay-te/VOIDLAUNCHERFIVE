package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first-person bow pull (arm back, bow stretched) follows the perk-scaled draw. The only 20-tick constant in this
 * method is the bow's full-draw time.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "CONSTANT", args = "floatValue=20.0F"))
	private float mettle$drawTime(float fullDraw) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? fullDraw : fullDraw / Draw.speed(player, player.getTicksUsingItem());
	}
}
