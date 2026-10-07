package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.motes.SoftMist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the soft ground mist is drawn ({@link SoftMist}): right after vanilla's main pass (and its improved-transparency
 * passes), as the frame moves on to the entity outlines, while the frame's particle buffers are still alive.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMistMixin {
	@Inject(method = "executeOutline", at = @At("HEAD"))
	private void gloam$drawSoftMist(FeatureRenderDispatcher.PreparedFrame featureFrame, CallbackInfo ci) {
		SoftMist.draw(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}
}
