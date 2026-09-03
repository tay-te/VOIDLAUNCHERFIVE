package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Zoom actuator (§6.7): the player's FOV is divided while the zoom key is
 * held. The easing lives in {@code ZoomController}; this only multiplies.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov(FZ)F", at = @At("RETURN"), cancellable = true)
    private void void$zoomFov(float tickDelta, boolean changingFov,
                              CallbackInfoReturnable<Float> cir) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        double factor = client.zoomFactor();
        if (Math.abs(factor - 1.0) < 0.0005) {
            return;
        }
        cir.setReturnValue(Float.valueOf((float) (cir.getReturnValueF() * factor)));
    }
}
