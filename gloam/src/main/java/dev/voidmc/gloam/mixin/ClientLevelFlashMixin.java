package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.sky.GloamSky;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning lights the whole sky only where a storm is overhead ({@link GloamSky#flashes}): a bolt the server strikes
 * while the front over the camera is clear is seen and heard, but does not flash a clear sky white. The flash counting
 * down is never held up.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelFlashMixin {
	@Shadow
	private int skyFlashTime;

	@Inject(method = "setSkyFlashTime", at = @At("HEAD"), cancellable = true)
	private void gloam$flash(int time, CallbackInfo ci) {
		if (time > this.skyFlashTime && !GloamSky.flashes((ClientLevel) (Object) this)) {
			ci.cancel();
		}
	}
}
