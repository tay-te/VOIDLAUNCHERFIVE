package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.sky.GloamSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client's rain and thunder levels follow the fronts over the camera ({@link GloamSky#rainLevel},
 * {@link GloamSky#thunderLevel}): vanilla's rain renderer, the rain's sound and fog, the weather's grey on the sky and a
 * shader pack's {@code rainStrength} read these. Client levels only (a client-side mixin, and checked): the integrated
 * server's levels keep vanilla's weather, and with it every gameplay rule.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void gloam$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel level) {
			cir.setReturnValue(GloamSky.rainLevel(level, partialTick, cir.getReturnValueF()));
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void gloam$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel level) {
			cir.setReturnValue(GloamSky.thunderLevel(level, partialTick, cir.getReturnValueF()));
		}
	}
}
