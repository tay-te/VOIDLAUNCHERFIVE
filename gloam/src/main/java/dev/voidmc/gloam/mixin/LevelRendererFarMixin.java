package dev.voidmc.gloam.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.gloam.far.FarAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the far air enters vanilla's frame (FarAir): it prepares while the frame graph is assembled, before any pass
 * runs, and draws inside the main pass after the translucent terrain and before the weather, where vanilla draws its
 * clouds; with vanilla's improved transparency, whose clouds and weather go through passes of their own, it draws in a
 * pass of its own once those are composited.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFarMixin {
	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Inject(method = "addMainPass", at = @At("HEAD"))
	private void gloam$prepareFar(FrameGraphBuilder frame, FeatureRenderDispatcher.PreparedFrame featureFrame, GpuBufferSlice terrainFog,
		ChunkSectionsToRender chunkSectionsToRender, boolean consistentDepthRequired, CallbackInfo ci) {
		FarAir.prepare(this.levelRenderState);
	}

	@Inject(
		method = "executeClassicTransparency",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V")
	)
	private void gloam$drawFar(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass,
		CallbackInfo ci) {
		FarAir.draw(renderPass);
	}

	@Inject(method = "executeOit", at = @At("TAIL"))
	private void gloam$drawFarAfterOit(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame, CallbackInfo ci) {
		FarAir.drawOwnPass(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}
}
