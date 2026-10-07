package dev.voidmc.lod.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.compat.Shaders;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The places the LOD enters vanilla's frame: it prepares (selects, uploads, builds its draw buffers)
 * while the frame graph is assembled, before any pass runs, and it draws inside the main pass straight
 * after the opaque chunks, so the chunks' depth already rejects every LOD fragment they hide. While an
 * Iris shader pack renders the world it draws instead once the frame graph has run and the pack has
 * written its final picture (Iris finishes just before the model-view stack is popped).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Inject(method = "addMainPass", at = @At("HEAD"))
	private void voidLod$prepare(FrameGraphBuilder frame, FeatureRenderDispatcher.PreparedFrame featureFrame, GpuBufferSlice terrainFog,
		ChunkSectionsToRender chunkSectionsToRender, boolean consistentDepthRequired, CallbackInfo ci) {
		VoidLod lod = VoidLod.get();
		if (lod != null) {
			lod.prepare(this.levelRenderState.cameraRenderState, this.levelRenderState.skyRenderState.sunAngle);
		}
	}

	@Inject(
		method = "executeSolid",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V", shift = At.Shift.AFTER)
	)
	private void voidLod$draw(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass, CallbackInfo ci) {
		VoidLod lod = VoidLod.get();
		if (lod != null && !Shaders.drawOverPack() && !Shaders.packDraws()) {
			lod.draw(renderPass);
		}
	}

	/** A section's visibility, with the radius the chunks dissolve into the LOD by ({@link dev.voidmc.lod.ChunkFade}). */
	@Redirect(method = "extractSectionDrawGroups", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getVisibility(JJ)F"))
	private float voidLod$dissolve(SectionRenderDispatcher.RenderSection section, long now, long fadeDuration) {
		return dev.voidmc.lod.ChunkFade.encode(section.getVisibility(now, fadeDuration));
	}

	/**
	 * With a shader pack that draws the LOD through its Distant Horizons programs: before the main pass, so before the
	 * chunks, which then cover it wherever they stand (as Distant Horizons draws its own).
	 */
	@Inject(method = "lambda$addMainPass$0", at = @At("HEAD"), require = 1)
	private void voidLod$drawThroughPack(CallbackInfo ci) {
		VoidLod lod = VoidLod.get();
		if (lod != null && Shaders.packDraws()) {
			lod.drawPack();
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V"))
	private void voidLod$drawOverPack(CallbackInfo ci) {
		VoidLod lod = VoidLod.get();
		if (lod != null && Shaders.drawOverPack()) {
			lod.drawOverPack();
		}
	}
}
