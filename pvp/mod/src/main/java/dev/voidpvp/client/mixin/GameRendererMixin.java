package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * The hover half of Retina support (§13). The render path builds the mouse position it hands
     * to {@code Screen.render} the same way {@code Screen.handleMouse} builds the click position —
     * {@code Mouse.getX() * scaledWidth / mc.width} — so it needs the same correction, or the
     * pointer draws and highlights in a different place from where it clicks.
     */
    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;getX()I"))
    private int void$mouseXInPixels() {
        return HiDpi.toPixels(Mouse.getX());
    }

    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/input/Mouse;getY()I"))
    private int void$mouseYInPixels() {
        return HiDpi.toPixels(Mouse.getY());
    }
}
