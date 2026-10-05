package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.client.marksmanship.MarksmanshipClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hunter's Mark outlines in amber, so a marked mob is told apart from one glowing for every player. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int mettle$markColor(int color, @Local(argsOnly = true) Entity entity) {
		return !entity.isCurrentlyGlowing() && MarksmanshipClient.marked(entity) ? MarksmanshipClient.MARK_COLOR : color;
	}
}
