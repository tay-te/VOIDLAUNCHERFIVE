package dev.voidpvp.client.mixin;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.entity.BaseArrowEntityRenderer;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of the Overlay mod's {@code hide_stuck_arrows} (§6.7): the ones on the ground.
 *
 * <p>Targets the typed {@code render(AbstractArrowEntity, …)} and not the
 * {@code render(Entity, …)} beside it: the latter is the dispatcher-facing override and its
 * whole body is a cast and a call to this one, so injecting there would fire once per arrow and
 * then let the real draw run anyway. The descriptor is spelled out for that reason — the two
 * overloads differ only in their first parameter.</p>
 *
 * <p><b>Only arrows that have landed.</b> {@code inGround} is set when an arrow embeds itself
 * and is what leaves a thicket around a bow exchange for a minute afterwards; an arrow still in
 * flight is a shot on its way to you, which is the last thing a PvP client should hide. The
 * setting's text is "arrows lying where they landed", and that is the field that says so —
 * read through {@link AbstractArrowEntityAccessor} because it is private.</p>
 */
@Mixin(BaseArrowEntityRenderer.class)
public abstract class BaseArrowEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/projectile/AbstractArrowEntity;DDDFF)V",
            at = @At("HEAD"), cancellable = true)
    private void void$hideLandedArrows(AbstractArrowEntity arrow, double x, double y, double z,
                                       float yaw, float tickDelta, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (!state.overlayOn || !state.overlayHideStuckArrows || arrow == null) {
            return;
        }
        if (((AbstractArrowEntityAccessor) arrow).void$inGround()) {
            ci.cancel();
        }
    }
}
