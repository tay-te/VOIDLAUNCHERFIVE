package dev.voidmc.reel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.voidmc.reel.client.Director;
import dev.voidmc.reel.shot.CameraState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the camera where the director says: position and angles once the game has lined it up with its entity,
 * roll (which vanilla's camera does not have) into the rotation, the field of view in place of the player's, and
 * the frame's own aspect ratio when frames are drawn at a size other than the window's.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void reel$place(float partialTicks, CallbackInfo ci) {
		CameraState state = Director.get().camera();
		if (state != null) {
			this.setRotation((float) state.yaw(), (float) state.pitch());
			this.setPosition(state.pos().x(), state.pos().y(), state.pos().z());
		}
	}

	@ModifyArg(method = "setRotation", at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"), index = 2)
	private float reel$roll(float roll) {
		CameraState state = Director.get().camera();
		return state != null ? (float) Math.toRadians(state.roll()) : roll;
	}

	@Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
	private void reel$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		CameraState state = Director.get().camera();
		if (state != null) {
			cir.setReturnValue((float) state.fov());
		}
	}

	@ModifyExpressionValue(method = {"update", "createProjectionMatrixForCulling"},
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
	private int reel$frameWidth(int width) {
		return Director.get().frameWidth(width);
	}

	@ModifyExpressionValue(method = {"update", "createProjectionMatrixForCulling"},
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
	private int reel$frameHeight(int height) {
		return Director.get().frameHeight(height);
	}
}
