package dev.voidmc.gloam.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.voidmc.gloam.sky.SkyBands;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Draws vanilla's sky disc and its sunrise and sunset fan through Gloam's banded pipelines ({@link SkyBands}) while
 * the sky is drawn pixel-true: the same geometry and colours, in flat steps instead of smooth gradients.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	private static final String COMPILED = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)"
		+ "Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;";

	@ModifyArg(method = "renderSkyDisc", at = @At(value = "INVOKE", target = COMPILED))
	private RenderPipeline gloam$skyDisc(RenderPipeline pipeline) {
		return SkyBands.on() ? SkyBands.SKY : pipeline;
	}

	@ModifyArg(method = "renderSunriseAndSunset", at = @At(value = "INVOKE", target = COMPILED))
	private RenderPipeline gloam$sunrise(RenderPipeline pipeline) {
		return SkyBands.on() ? SkyBands.SUNRISE : pipeline;
	}
}
