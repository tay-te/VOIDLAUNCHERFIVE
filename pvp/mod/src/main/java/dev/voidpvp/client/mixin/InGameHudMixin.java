package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    /**
     * The Overlay mod's {@code hide_pumpkin} (§6.7).
     *
     * <p>The private draw the {@code GameOptions.perspective} read above guards — the one the
     * crosshair suppression used to redirect by mistake, which is why this file already knows
     * where it is. It blits the pumpkin-blur texture over the whole screen and does nothing
     * else, so not entering it is the whole suppression; the first-person check that decides
     * whether it is called at all stays vanilla's.</p>
     */
    @Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true)
    private void void$hidePumpkinBlur(Window window, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (state.overlayOn && state.overlayHidePumpkin) {
            ci.cancel();
        }
    }

    /**
     * Whether {@link #void$scoreboardBefore} pushed a matrix this draw.
     *
     * <p>The two injectors have to agree, and they cannot agree by re-reading {@link LiveState}:
     * every field there is {@code volatile} and written from the WS and UI threads, so a setting
     * that changed between the head and the return would leave the matrix stack one deep — which
     * is not a scoreboard bug, it is every subsequent draw in the frame under a stale transform.
     * One field, written and read on the render thread inside one method call.</p>
     */
    @Unique
    private boolean void$scoreboardPushed;

    /**
     * The Scoreboard mod (§6.7): hide, scale or move the server's sidebar.
     *
     * <p><b>The geometry was read, not remembered.</b> In
     * {@code InGameHud.renderScoreboardObjective(ScoreboardObjective, Window)} the sidebar's left
     * edge is computed at offsets 221-231 as {@code window.getWidth() - maxWidth - 3} and its top
     * at 205-216 as {@code window.getHeight() / 2 + rows * fontHeight / 3}. So it hangs from a
     * point on the <em>right edge at half height</em> and grows left and down from there — which
     * is why the scale is taken about that point. A scale about the origin would slide the
     * sidebar towards the middle of the screen as it shrank, which is the opposite of what a
     * player asking for a smaller scoreboard wants.</p>
     *
     * <p><b>Hiding cancels rather than transforming.</b> The whole method is the sidebar and
     * nothing else, so not entering it is the entire suppression and it costs less than vanilla
     * rather than more. It also means the cancel must happen <em>before</em> the push: a cancelled
     * {@code CallbackInfo} returns from the method at this injection point, so the {@code RETURN}
     * injector below never runs and a matrix pushed here would never be popped.</p>
     *
     * <p>The transform order is {@code offset · anchor · scale · anchor⁻¹}, so a vertex ends at
     * {@code offset + anchor + scale * (v - anchor)} — the offset is in screen pixels rather than
     * in scaled ones, which is what makes "move it up 40" mean the same thing at every scale.</p>
     */
    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void void$scoreboardBefore(ScoreboardObjective objective, Window window,
            CallbackInfo ci) {
        void$scoreboardPushed = false;
        LiveState state = LiveState.get();
        if (!state.scoreboardOn) {
            return;
        }
        if (state.scoreboardHide) {
            ci.cancel();
            return;
        }
        if (!state.scoreboardTransformed()) {
            return;
        }
        float anchorX = window.getWidth();
        float anchorY = window.getHeight() / 2.0F;
        float scale = (float) state.scoreboardScale;
        GlStateManager.pushMatrix();
        void$scoreboardPushed = true;
        GlStateManager.translate((float) state.scoreboardOffsetX, (float) state.scoreboardOffsetY,
                0.0F);
        GlStateManager.translate(anchorX, anchorY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-anchorX, -anchorY, 0.0F);
    }

    /** The other half of {@link #void$scoreboardBefore}; see there for why the flag exists. */
    @Inject(method = "renderScoreboardObjective", at = @At("RETURN"))
    private void void$scoreboardAfter(ScoreboardObjective objective, Window window,
            CallbackInfo ci) {
        if (void$scoreboardPushed) {
            void$scoreboardPushed = false;
            GlStateManager.popMatrix();
        }
    }
}
