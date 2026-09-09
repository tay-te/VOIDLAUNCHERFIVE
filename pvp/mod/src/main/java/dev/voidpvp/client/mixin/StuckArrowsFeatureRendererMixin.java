package dev.voidpvp.client.mixin;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.StuckArrowsFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Half of the Overlay mod's {@code hide_stuck_arrows} (§6.7): the ones sticking out of you.
 *
 * <p>1.8.9 draws these from a feature layer of their own, which reads {@code getStuckArrows()}
 * and renders that many throwaway arrow models around the body at positions seeded from the
 * entity id. Returning before any of that is the suppression, and it costs the pass rather than
 * just hiding it.</p>
 *
 * <p><b>Own model only</b>, matching {@code ArmorFeatureRendererMixin} and for a sharper reason:
 * arrows in an <em>opponent</em> are a record of hits that landed, and this mod does not remove
 * that. The setting's own text is about the several sticking out of <em>you</em> a moment after
 * an exchange, which tell you nothing you did not feel.</p>
 *
 * <p>The other half — arrows lying where they landed — is
 * {@link BaseArrowEntityRendererMixin}.</p>
 */
@Mixin(StuckArrowsFeatureRenderer.class)
public abstract class StuckArrowsFeatureRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void void$hideOwnStuckArrows(LivingEntity entity, float limbAngle,
                                         float limbDistance, float tickDelta,
                                         float animationProgress, float headYaw,
                                         float headPitch, float scale, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (!state.overlayOn || !state.overlayHideStuckArrows) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && entity != null && entity == mc.player) {
            ci.cancel();
        }
    }
}
