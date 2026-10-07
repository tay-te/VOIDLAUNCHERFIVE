package dev.voidmc.gloam.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.voidmc.gloam.far.FarAir;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stands vanilla's clouds down while Gloam's cloud deck replaces them ({@link FarAir#replacesClouds}): no mesh built, no
 * draw, in either of vanilla's transparency modes. When Gloam or its clouds are switched off, vanilla's come straight
 * back (its mesh is rebuilt as soon as the camera moves a cell).
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true)
	private void gloam$prepare(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition, long gameTime, float partialTicks,
		CallbackInfo ci) {
		if (FarAir.replacesClouds()) {
			ci.cancel();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
	private void gloam$render(CloudStatus cloudStatus, RenderPass renderPass, CallbackInfo ci) {
		if (FarAir.replacesClouds()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
	private void gloam$renderOit(CloudStatus cloudStatus, OitStage stage, GpuTextureView mainDepthTextureView, OitRenderPassProvider.Parameters params,
		CallbackInfo ci) {
		if (FarAir.replacesClouds()) {
			ci.cancel();
		}
	}
}
