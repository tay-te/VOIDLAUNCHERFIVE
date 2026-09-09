package dev.voidpvp.client.mixin;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay mod's {@code hide_fire} (§6.7).
 *
 * <p>1.8.9 draws the first-person flames from {@code HeldItemRenderer.renderOverlays}, which is
 * three guarded calls in a row — in-wall, underwater, on fire — and each has its own private
 * draw method. {@code renderFireOverlay} is the third of them, called only from there and only
 * under {@code player.isOnFire()}, and it does nothing but bind the fire sprite and draw eight
 * quads around the camera. So the whole suppression is not entering it: the guard, the other two
 * overlays and the alpha state around all three are untouched, which is what makes this a skip
 * rather than a rewrite.</p>
 *
 * <p>Verified against the 1.8.9 bytecode under {@code net.legacyfabric:yarn:1.8.9+build.604}:
 * one caller, one call site, no other reference in the client.</p>
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private void void$hideFireOverlay(float tickDelta, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (state.overlayOn && state.overlayHideFire) {
            ci.cancel();
        }
    }
}
