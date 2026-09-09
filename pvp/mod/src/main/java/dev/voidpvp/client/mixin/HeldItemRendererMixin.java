package dev.voidpvp.client.mixin;

import dev.voidpvp.client.actuator.OldAnimations;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay mod's {@code hide_fire}, and the first-person half of Old animations'
 * {@code block_hit} (§6.7).
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

    /**
     * {@code applyEquipAndSwingOffset}, reached from {@code renderArmHoldingItem}'s BLOCK arm and
     * from nowhere else this class touches.
     *
     * <p>Shadowed only so the redirect below can call it back with a different second argument.
     * It is {@code private} in 1.8.9; raising a shadow's visibility is allowed and is what Mixin
     * does to the target, so nothing else in the game gains access to it.</p>
     */
    @Shadow
    protected abstract void applyEquipAndSwingOffset(float equipProgress, float swingProgress);

    /**
     * Old animations' {@code block_hit}, first person (§6.7): pass the live swing progress the
     * BLOCK arm throws away.
     *
     * <p><b>Why ordinal 2.</b> {@code renderArmHoldingItem(F)V} calls
     * {@code applyEquipAndSwingOffset} five times, and in bytecode order they are: offset 195
     * (NONE), 210 (EAT and DRINK, which share an arm), <b>219 (BLOCK)</b>, 232 (BOW) and 254 (the
     * not-using path, the one that already passes the live value). The arm numbering is
     * {@code HeldItemRenderer$1.field_10793}'s {@code <clinit>}, which assigns 4 to
     * {@code UseAction.BLOCK} — guessing from the enum's declaration order points at the wrong
     * arm, so it was read rather than assumed.</p>
     *
     * <p><b>What this deliberately does not do.</b> It does not call {@code translateSwingProgress}
     * as well. 1.7.10 kept that translate inside the <em>else</em> of its own
     * {@code getItemUseTicks() > 0} test, so 1.7 skipped it while an item was in use exactly as
     * 1.8.9 does; adding it would overshoot 1.7 by precisely the piece 1.7 also skipped. It does
     * not touch {@code applySwordBlockTransformation} either — that method's constants are
     * identical in the two versions. And it reverts <b>BLOCK only</b>: 1.7's equip-and-swing block
     * sat outside its use-action branch, so 1.7 fed a live swing to eating, drinking and bow
     * drawing too, and narrowing that to blocking is the mod's own choice, argued in
     * {@code schema/mods/old_animations.json}.</p>
     *
     * <p>{@code tickDelta} is the enclosing method's own argument, captured by appending it to
     * the handler's signature — the swing progress the not-using path uses is
     * {@code player.getHandSwingProgress(tickDelta)} and this reads it the same way, from the
     * same player object, so the two paths cannot drift apart.</p>
     *
     * <p>With the mod off {@link OldAnimations#blockSwingProgress} returns vanilla's own
     * {@code 0.0F} and the call is the one the game would have made.</p>
     */
    @Redirect(method = "renderArmHoldingItem", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;"
                    + "applyEquipAndSwingOffset(FF)V"))
    private void void$blockHitSwing(HeldItemRenderer self, float equipProgress,
                                    float swingProgress, float tickDelta) {
        LiveState state = LiveState.get();
        float swing = swingProgress;
        if (state.oldAnimationsOn && state.oldAnimationsBlockHitOneSeven) {
            MinecraftClient mc = MinecraftClient.getInstance();
            AbstractClientPlayerEntity player = mc == null ? null : mc.player;
            if (player != null) {
                swing = OldAnimations.blockSwingProgress(true, true,
                        player.getHandSwingProgress(tickDelta));
            }
        }
        this.applyEquipAndSwingOffset(equipProgress, swing);
    }
}
