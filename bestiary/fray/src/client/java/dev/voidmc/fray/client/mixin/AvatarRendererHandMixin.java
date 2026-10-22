package dev.voidmc.fray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.Elbows;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's first-person hand resets the arm only: its elbow is straightened too (the model is shared by players). */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererHandMixin {
	@Inject(method = "renderHand", at = @At("HEAD"))
	private void fray$straighten(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
		Elbows.straighten(arm);
	}
}
