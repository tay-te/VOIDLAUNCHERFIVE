package dev.voidmc.lod.mixin;

import dev.voidmc.lod.VoidLod;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the far plane out to the LOD's reach. 26.3 renders with reversed depth into a 32-bit float
 * buffer, where precision is spread evenly in log space, so a far plane at 16 km instead of 800 m costs
 * nearby geometry nothing.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float depthFar;

	@Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void voidLod$farPlane(CallbackInfo ci) {
		VoidLod lod = VoidLod.get();
		if (lod != null && lod.active()) {
			this.depthFar = Math.max(this.depthFar, (float) (lod.radius * 1.5));
		}
	}
}
