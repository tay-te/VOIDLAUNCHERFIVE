package dev.voidmc.reel.mixin;

import dev.voidmc.reel.client.Director;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each frame starts with the director (preview time, time of day), and a render never pauses for lost focus. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void reel$beforeFrame(boolean advanceGameTime, CallbackInfo ci) {
		Director.get().beforeFrame((Minecraft) (Object) this);
	}

	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void reel$keepFilming(CallbackInfo ci) {
		if (Director.get().clockActive()) {
			ci.cancel();
		}
	}
}
