package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The bow's zoom-in while drawing reaches its end at the same moment the perk-scaled draw does. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getTicksUsingItem()I"))
	private int mettle$drawZoom(int ticks) {
		return Math.round(Draw.effectiveTicks((AbstractClientPlayer) (Object) this, ticks));
	}
}
