package dev.voidmc.lod.mixin.iris;

import dev.voidmc.lod.compat.iris.PackLod;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The LOD's water, drawn by the pack's dh_water program once the world's translucent pass is done and before Iris runs
 * the pack's composite passes over the frame: where Distant Horizons' own water goes.
 */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisPipelineMixin {
	@Inject(method = "finalizeLevelRendering", at = @At("HEAD"))
	private void voidLod$water(CallbackInfo ci) {
		PackLod.drawWater();
	}
}
