package dev.voidmc.fray.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.client.FrayStats;
import dev.voidmc.fray.client.PoseApply;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fray's three hooks on living entities (ANIMATION.md §7.3), all at points verified in 26.3's
 * {@code LivingEntityRenderer}: sample at the end of {@code extractRenderState}; apply the root transform to the
 * PoseStack right after vanilla's {@code translate(0, -1.501, 0)} (the {@code isBodyVisible} call follows it); apply the
 * pose after the {@code setupAnim} that layers (held items) read.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Shadow
	protected EntityModel<?> model;

	@Unique
	private final Matrix4f fray$root = new Matrix4f();

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void fray$sample(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		ClientMotion.sample(entity, state, partialTicks);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
	private void fray$root(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (PoseApply.root(state, this.fray$root)) {
			poseStack.mulPose(this.fray$root);
			FrayStats.rootApplies++;
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void fray$applyForLayers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		PoseApply.apply(this.model, state);
	}
}
