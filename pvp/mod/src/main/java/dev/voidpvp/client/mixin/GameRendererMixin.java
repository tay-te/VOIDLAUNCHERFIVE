package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.input.Mouse;
import org.objectweb.asm.Opcodes;
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

    /**
     * The Overlay mod's {@code view_bobbing}, camera half (§6.7).
     *
     * <p>1.8.9 has one bob routine, {@code bobView(float)}, and calls it from two places behind
     * the same {@code GameOptions.bobView} flag: once in {@code setupCamera}, where it moves the
     * <em>camera</em>, and twice in {@code renderHand}, where it moves the <em>held item</em>.
     * Vanilla's own switch is all-or-nothing because it is one field; {@code minimal} is the
     * split, and the split exists in the game already — it is just not exposed. So this answers
     * the flag differently per call site rather than cancelling {@code bobView} itself, which
     * could not tell the two apart.</p>
     *
     * <p>Redirecting the field <em>read</em> and not the {@code bobView} call is deliberate: it
     * is the same shape as vanilla's own guard, so a frame with the mod off runs byte-identical
     * vanilla, and the player's real "View Bobbing" option still wins — {@code vanillaValue &&}
     * means this can only ever take bobbing away, never turn it on for someone who switched it
     * off.</p>
     *
     * <p>Both {@code minimal} and {@code off} hold the camera still; that is what they have in
     * common and what most players actually want from the vanilla switch.</p>
     */
    @Redirect(method = "setupCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;bobView:Z",
            opcode = Opcodes.GETFIELD))
    private boolean void$cameraBobEnabled(GameOptions options) {
        LiveState state = LiveState.get();
        return options.bobView && !(state.overlayOn && state.overlayLockCameraBob);
    }

    /**
     * The Overlay mod's {@code view_bobbing}, held-item half (§6.7).
     *
     * <p>Only {@code off} reaches this one. {@code minimal} keeps the hand moving on purpose:
     * the hand is where the bob reads as your own speed rather than as the world moving, so
     * taking the camera's and leaving the hand's is the setting most players are reaching for
     * when they turn the vanilla switch off and then miss something.</p>
     *
     * <p>Two reads in {@code renderHand} share this, and both should: the first wraps the item
     * draw and the second is vanilla's own trailing call after the matrix has been popped. They
     * are the same flag answering the same question, so answering them differently would be a
     * distinction with no picture behind it.</p>
     */
    @Redirect(method = "renderHand", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;bobView:Z",
            opcode = Opcodes.GETFIELD))
    private boolean void$handBobEnabled(GameOptions options) {
        LiveState state = LiveState.get();
        return options.bobView && !(state.overlayOn && state.overlayLockHandBob);
    }
}
