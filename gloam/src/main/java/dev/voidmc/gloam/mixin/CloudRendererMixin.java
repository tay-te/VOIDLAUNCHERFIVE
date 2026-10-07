package dev.voidmc.gloam.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.voidmc.gloam.far.FarAir;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Has vanilla's cloud renderer draw Gloam's cells instead of its own ({@link FarAir#replacesClouds}): its own mesh is not
 * built, and where it would draw it, through whatever pipeline it chose (fancy or fast, plain or improved
 * transparency), Gloam's groups are drawn with that same pipeline. Vanilla still decides whether clouds are drawn at all
 * (the cloud option, {@code CLOUD_COLOR}'s alpha, a shader pack that turns them off for clouds of its own), and a pack's
 * clouds program, which Iris swaps in for vanilla's pipelines, draws Gloam's cells as it would vanilla's. When Gloam or
 * its clouds are switched off, vanilla's come straight back.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@Shadow
	private int quadCount;

	@Shadow
	private boolean needsRebuild;

	@Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true)
	private void gloam$prepare(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition, long gameTime, float partialTicks,
		CallbackInfo ci) {
		if (FarAir.replacesClouds()) {
			// vanilla draws when it has quads: Gloam's say whether there is anything; its own mesh is built afresh later
			this.quadCount = FarAir.prepareClouds(color, cameraPosition, gameTime, partialTicks) ? 1 : 0;
			this.needsRebuild = true;
			ci.cancel();
		}
	}

	@Inject(method = "render(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V", at = @At("HEAD"),
		cancellable = true)
	private void gloam$render(RenderPass renderPass, RenderPipeline renderPipeline, CallbackInfo ci) {
		if (FarAir.replacesClouds()) {
			FarAir.drawClouds(renderPass, renderPipeline);
			ci.cancel();
		}
	}
}
