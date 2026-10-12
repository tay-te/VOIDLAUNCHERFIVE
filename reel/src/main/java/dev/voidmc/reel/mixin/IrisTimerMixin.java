package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.Director;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shader packs animate water, clouds and wind by Iris's frameTimeCounter, which follows the wall clock: a frame that
 * takes a second to render would move the waves a second. While the director films, the counter is the shot's own
 * time instead, so packs animate exactly with the frames (and with each motion blur sample). Skipped without Iris.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer")
public abstract class IrisTimerMixin {
	@Shadow
	private float frameTimeCounter;

	@Shadow
	private float lastFrameTime;

	@Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, require = 0)
	private void reel$shotTime(long frameStartNanos, CallbackInfo ci) {
		Director director = Director.get();
		if (director.clockActive()) {
			this.frameTimeCounter = (float) director.packTime();
			this.lastFrameTime = (float) director.packFrameTime();
			ci.cancel();
		}
	}
}
