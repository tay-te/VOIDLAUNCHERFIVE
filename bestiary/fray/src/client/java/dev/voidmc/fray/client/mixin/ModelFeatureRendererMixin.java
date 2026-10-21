package dev.voidmc.fray.client.mixin;

import dev.voidmc.fray.client.PoseApply;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every submitted model (an entity's body, each armour piece, in every pass including Iris's shadow pass) is posed by
 * {@code setupAnim(state)} in {@code prepareModel} just before it is drawn; Fray's pose goes on right after.
 */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void fray$apply(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		PoseApply.apply(submit.model(), submit.state());
	}
}
