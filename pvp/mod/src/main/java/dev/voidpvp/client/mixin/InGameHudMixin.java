package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
     * <p>1.8.9 draws the crosshair inline in the overlay pass, guarded by
     * {@code gameSettings.thirdPersonView == 0}; there is no separate method to
     * cancel. Reporting a non-zero perspective for that one read skips the
     * vanilla draw and nothing else — {@code perspective} is read exactly once
     * in this method, which is why the ordinal is pinned to 0. Worth a look in
     * game if the crosshair ever doubles up.</p>
     */
    @Redirect(method = "render",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/option/GameOptions;perspective:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0))
    private int void$hideVanillaCrosshair(GameOptions options) {
        VoidClient client = VoidClient.get();
        if (client != null && client.suppressesVanillaCrosshair()) {
            return 1;
        }
        return options.perspective;
    }
}
