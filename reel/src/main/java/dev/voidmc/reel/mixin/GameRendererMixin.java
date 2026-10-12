package dev.voidmc.reel.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.reel.client.Director;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frames drawn at the job's size (the main target follows the window state, so setting that resizes every target),
 * captured once the world and its post effects are drawn and before the GUI, and steady: no view bob or hurt tilt
 * while the director holds the camera.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private GameRenderState gameRenderState;

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	@Inject(method = "extractWindow", at = @At("TAIL"))
	private void reel$frameSize(CallbackInfo ci) {
		Director director = Director.get();
		if (director.clockActive()) {
			this.gameRenderState.windowRenderState.width = director.frameWidth(this.gameRenderState.windowRenderState.width);
			this.gameRenderState.windowRenderState.height = director.frameHeight(this.gameRenderState.windowRenderState.height);
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void reel$capture(CallbackInfo ci) {
		Director.get().onWorldRendered(this.mainRenderTarget());
	}

	@Inject(method = {"bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
	private void reel$steady(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (Director.get().camera() != null) {
			ci.cancel();
		}
	}
}
