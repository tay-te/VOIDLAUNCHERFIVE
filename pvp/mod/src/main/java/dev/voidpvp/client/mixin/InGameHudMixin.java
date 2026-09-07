package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The HUD layer (§6.2) and the crosshair actuator (§6.7).
 *
 * <p>The Ultralight view is painted at the very end of the overlay pass, so it
 * sits above everything vanilla draws and below any open screen.</p>
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /** End of the overlay: run the view and paint the HUD layer. */
    @Inject(method = "render", at = @At("TAIL"))
    private void void$renderOverlay(float tickDelta, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onRenderOverlay();
        }
    }

    /**
     * Suppresses the vanilla crosshair when the crosshair mod replaces it.
     *
     * <p>1.8.9 guards its crosshair with {@code InGameHud.showCrosshair()} and draws it inline,
     * so answering that question is the whole of the suppression. Returning {@code false} skips
     * the blend setup and the 16x16 blit and nothing else.</p>
     *
     * <p><b>This used to redirect a {@code GameOptions.perspective} field read, and that was
     * wrong in a way nothing caught.</b> The comment claimed {@code perspective} was read exactly
     * once in {@code render} and that the read was the crosshair's guard. The first half is true
     * — there is exactly one, so {@code ordinal = 0} was unambiguous and the injection applied
     * cleanly, and Mixin's {@code defaultRequire} was satisfied. The second half is false: that
     * read guards the <em>pumpkin-blur overlay</em>, and the crosshair is guarded by this method.
     * So for as long as it stood, the mod drew its crosshair <em>on top of</em> the vanilla one
     * rather than in place of it, and quietly stopped a pumpkin helmet from blurring the screen.
     * Measured in game: {@code crosshair.style: none} with the mod on was pixel-identical to the
     * mod being off, when it should have left no crosshair at all.</p>
     */
    @Inject(method = "showCrosshair", at = @At("RETURN"), cancellable = true)
    private void void$hideVanillaCrosshair(CallbackInfoReturnable<Boolean> cir) {
        VoidClient client = VoidClient.get();
        if (client != null && client.suppressesVanillaCrosshair()) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
