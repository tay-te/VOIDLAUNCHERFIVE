package dev.voidmc.lod.mixin.iris;

import dev.voidmc.lod.compat.iris.PackLod;
import dev.voidmc.lod.compat.iris.PackLodRenderer;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris's facade for Distant Horizons, answered by the LOD instead: the pack is compiled with DISTANT_HORIZONS, its
 * dhRenderDistance, dhNearPlane, dhFarPlane, dhProjection and dhDepthTex are the LOD's, and the object behind the facade
 * is the LOD's renderer ({@link PackLodRenderer}) rather than Iris's own Distant Horizons code, which needs Distant
 * Horizons installed. With Distant Horizons installed as well, Iris keeps its own (the LOD then stays out of it).
 */
@Mixin(value = DHCompat.class, remap = false)
public abstract class DHCompatMixin {
	@Shadow
	private Object compatInternalInstance;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void voidLod$create(IrisRenderingPipeline pipeline, boolean renderDHShadow, CallbackInfo ci) {
		if (PackLod.building() && this.compatInternalInstance == null) {
			this.compatInternalInstance = PackLodRenderer.create(pipeline);
		}
	}

	@Inject(method = "hasRenderingEnabled", at = @At("HEAD"), cancellable = true)
	private static void voidLod$enabled(CallbackInfoReturnable<Boolean> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "checkFrame", at = @At("HEAD"), cancellable = true)
	private static void voidLod$checkFrame(CallbackInfoReturnable<Boolean> cir) {
		if (PackLod.checkFrame()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "lastPackIncompatible", at = @At("HEAD"), cancellable = true)
	private static void voidLod$compatible(CallbackInfoReturnable<Boolean> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getRenderDistance", at = @At("HEAD"), cancellable = true)
	private static void voidLod$renderDistance(CallbackInfoReturnable<Integer> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(PackLod.renderDistance());
		}
	}

	@Inject(method = "getNearPlane", at = @At("HEAD"), cancellable = true)
	private static void voidLod$nearPlane(CallbackInfoReturnable<Float> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(PackLod.nearPlane());
		}
	}

	@Inject(method = "getFarPlane", at = @At("HEAD"), cancellable = true)
	private static void voidLod$farPlane(CallbackInfoReturnable<Float> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(PackLod.farPlane());
		}
	}

	@Inject(method = "getProjection()Lorg/joml/Matrix4f;", at = @At("HEAD"), cancellable = true)
	private static void voidLod$projection(CallbackInfoReturnable<Matrix4f> cir) {
		if (PackLod.wanted()) {
			cir.setReturnValue(PackLod.projection());
		}
	}

	@Inject(method = "getDepthTex", at = @At("HEAD"), cancellable = true)
	private void voidLod$depth(CallbackInfoReturnable<Integer> cir) {
		if (this.compatInternalInstance instanceof PackLodRenderer renderer) {
			cir.setReturnValue(renderer.depthTexture());
		}
	}

	@Inject(method = "getDepthTexNoTranslucent", at = @At("HEAD"), cancellable = true)
	private void voidLod$depthNoTranslucent(CallbackInfoReturnable<Integer> cir) {
		if (this.compatInternalInstance instanceof PackLodRenderer renderer) {
			cir.setReturnValue(renderer.depthTextureUnderWater());
		}
	}

	@Inject(method = "clearPipeline", at = @At("HEAD"), cancellable = true)
	private void voidLod$clear(CallbackInfo ci) {
		if (this.compatInternalInstance instanceof PackLodRenderer renderer) {
			renderer.destroy();
			this.compatInternalInstance = null;
			ci.cancel();
		}
	}
}
