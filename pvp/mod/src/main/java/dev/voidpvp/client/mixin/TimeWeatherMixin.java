package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.world.World;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code time_weather} mod, on the <b>client</b> world only.
 *
 * <p>{@code World.getSkyAngle} is what the sky, the sun and moon, the stars and the client's
 * sky-light darkening all read the time of day through; answering it from {@code time_ticks}
 * moves all of them together. {@code getRainGradient} and {@code getThunderGradient} are what
 * rain rendering, rain sounds and splash particles, and the sky's rain darkening read; 0 is a dry
 * sky. Every hook checks {@code isClient}, because the integrated server's worlds are the same
 * class and must keep the real clock and weather — mobs burn by the server's sun.</p>
 */
@Mixin(World.class)
public abstract class TimeWeatherMixin {

    @Shadow
    @Final
    public boolean isClient;

    @Shadow
    @Final
    public Dimension dimension;

    @Inject(method = "getSkyAngle", at = @At("HEAD"), cancellable = true)
    private void void$lockTime(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (isClient && VisualConfig.timeWeatherOn && VisualConfig.lockTime && dimension != null) {
            cir.setReturnValue(dimension.getSkyAngle(VisualConfig.timeTicks, 0f));
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void void$noRain(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (isClient && VisualConfig.timeWeatherOn && VisualConfig.clearWeather) {
            cir.setReturnValue(0f);
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void void$noThunder(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (isClient && VisualConfig.timeWeatherOn && VisualConfig.clearWeather) {
            cir.setReturnValue(0f);
        }
    }
}
