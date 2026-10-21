package dev.voidmc.fray.client.mixin;

import dev.voidmc.fray.client.FrayStats;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame-time probe: one timestamp per rendered frame (FrayStats keeps the last 4096 frame times). */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "render()V", at = @At("HEAD"))
	private void fray$frame(CallbackInfo ci) {
		FrayStats.frame();
	}
}
