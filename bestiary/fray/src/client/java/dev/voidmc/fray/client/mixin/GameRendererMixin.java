package dev.voidmc.fray.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.FirstPersonBody;
import dev.voidmc.fray.client.FrayStats;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame-time probe (one timestamp per rendered frame) and the first-person camera channels: added to the level's
 * view right after vanilla's hurt bob in {@code renderLevel} (the hand pass's own bob call is left alone, so the arms
 * stay framed while the view dips).
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "render()V", at = @At("HEAD"))
	private void fray$frame(CallbackInfo ci) {
		FrayStats.frame();
	}

	@WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void fray$cameraKick(GameRenderer self, CameraRenderState camera, PoseStack view, Operation<Void> original) {
		original.call(self, camera, view);
		FirstPersonBody.cameraKick(view, camera.cameraEntityPartialTicks);
	}
}
