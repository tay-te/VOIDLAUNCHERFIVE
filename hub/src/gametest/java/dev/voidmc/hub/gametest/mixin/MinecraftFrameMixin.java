package dev.voidmc.hub.gametest.mixin;

import dev.voidmc.hub.gametest.FrameHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame and tick timing hooks for the perf game test (test source set only). */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void voidhubtest$frameStart(boolean advanceGameTime, CallbackInfo ci) {
		FrameHooks.frameStart();
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;submit()V"))
	private void voidhubtest$beforeSubmit(boolean advanceGameTime, CallbackInfo ci) {
		FrameHooks.beforeSubmit();
	}

	@Inject(method = "renderFrame", at = @At("TAIL"))
	private void voidhubtest$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
		FrameHooks.frameEnd();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void voidhubtest$tick(CallbackInfo ci) {
		FrameHooks.tick();
	}
}
