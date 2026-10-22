package dev.voidmc.fray.client.combat.mixin;

import dev.voidmc.fray.client.combat.feel.CameraShake;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake (COMBAT.md §5): after the camera took the entity's view, the shake's offset turns it a little. Only the
 * camera turns: the player's look, aim and hit result are untouched; it runs before the frustum and matrices are built.
 */
@Mixin(Camera.class)
abstract class CameraShakeMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void fray$shake(float partialTicks, CallbackInfo ci) {
		float[] off = CameraShake.frame();
		if (off != null) {
			float y0 = this.yRot, x0 = this.xRot;
			this.setRotation(y0 + off[0], x0 + off[1]);
			CameraShake.applied(this.yRot - y0, this.xRot - x0);
		}
	}
}
