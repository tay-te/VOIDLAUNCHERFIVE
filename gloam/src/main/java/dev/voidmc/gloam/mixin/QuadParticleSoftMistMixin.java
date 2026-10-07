package dev.voidmc.gloam.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.gloam.motes.SoftMist;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the ground mist's sheets back from vanilla's particle pass, to be drawn soft once the main pass is over
 * ({@link SoftMist}): the pass's uniforms are noted as it binds them, and the sheets' draw call is skipped and handed
 * over instead.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public abstract class QuadParticleSoftMistMixin {
	@Shadow
	private @Nullable GpuBufferSlice dynamicTransforms;

	@Inject(method = "executeGroup", at = @At("HEAD"))
	private void gloam$captureUniforms(FeatureFrameContext context, @Nullable OitStage stage, RenderPass renderPass, int groupIndex,
		List<QuadParticleFeatureRenderer.Submit> submits, boolean strictlyOrdered, CallbackInfo ci) {
		SoftMist.capture(this.dynamicTransforms, context.lightmap());
	}

	@WrapOperation(method = "drawLayers", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
	private static void gloam$holdBackMist(RenderPass pass, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance,
		Operation<Void> original, @Local(argsOnly = true) @Nullable OitStage stage, @Local Map.Entry<?, StagedVertexBuffer.Draw> entry,
		@Local StagedVertexBuffer.ExecuteInfo info, @Local AbstractTexture texture) {
		if (!SoftMist.defer(entry.getKey(), stage != null, info, texture)) {
			original.call(pass, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
		}
	}
}
