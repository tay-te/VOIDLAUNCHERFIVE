package dev.voidpvp.client.mixin;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Nametags mod (§6.7): the floating names, smaller, thinner, nearer or gone.
 *
 * <p><b>Every setting is one instruction, and the method was read rather than remembered.</b>
 * {@code EntityRenderer.renderLabelIfPresent(T, String, double, double, double, int)}:</p>
 *
 * <ul>
 *   <li>offsets 0-25 — the distance cull, {@code if (squaredDistanceTo(camera) > param9 * param9)
 *       return}. Narrowing the parameter narrows vanilla's own cull rather than adding a second
 *       one, so the comparison, the squaring and the early return all stay the game's;</li>
 *   <li>offset 32 — {@code float scale = 1.6F}, which offsets 37-43 turn into the
 *       {@code 0.016666668F * scale} the label is scaled by at 102-110. The whole size of a
 *       nametag comes out of that one constant;</li>
 *   <li>offsets 176-307 — the background quad, bracketed by the method's only
 *       {@code disableTexture} / {@code enableTexture} pair and containing its only four
 *       {@code BufferBuilder.color} calls.</li>
 * </ul>
 *
 * <p><b>The plate is drawn transparent rather than skipped.</b> Cancelling
 * {@code Tessellator.draw()} — which is also the only one in the method, and therefore tempting —
 * would leave the buffer in its building state, and a {@code begin} without a {@code draw} is not
 * a no-op: it corrupts whatever draws next. Four vertices at zero alpha cost nothing measurable
 * and leave GL exactly as vanilla left it, which is the property that matters this deep in a
 * render path.</p>
 *
 * <p><b>No colour.</b> §1 would allow one — a nametag is a mark in the world, the exemption
 * {@code block_outline.color} lives under — but the colour is not ours to choose: it is written
 * by a scoreboard team, and in every mode that uses teams it is the difference between a
 * teammate and someone about to hit you. The Scoreboard mod refuses the same thing about the
 * sidebar's colour codes.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /** {@code nametags.hide} — the method is the label, so not entering it is the suppression. */
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void void$hideNametag(Entity entity, String text, double x, double y, double z,
            int maxDistance, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (state.nametagsOn && state.nametagsHide) {
            ci.cancel();
        }
    }

    /**
     * {@code nametags.max_distance} — vanilla's own cull, narrowed.
     *
     * <p>Only ever narrowed. The parameter is the game's answer to "how far does a name reach",
     * and raising it would be drawing names the unmodified client does not draw — which is a
     * different mod, and one §6.1 would have something to say about. {@code Math.min} is the
     * whole guard.</p>
     */
    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true,
            ordinal = 0)
    private int void$nametagDistance(int maxDistance) {
        LiveState state = LiveState.get();
        if (!state.nametagsOn) {
            return maxDistance;
        }
        return Math.min(maxDistance, (int) Math.max(4, Math.min(64, state.nametagsMaxDistance)));
    }

    /**
     * {@code nametags.nametag_scale} — the {@code 1.6F} every label's size is built from.
     *
     * <p>A constant rather than the {@code scale(FFF)} call three instructions later, because
     * that call takes {@code -f1, -f1, f1} and the negation is vanilla's own coordinate flip: a
     * redirect there would have to reproduce a sign convention, where this multiplies the one
     * number the convention is applied to.</p>
     */
    @ModifyConstant(method = "renderLabelIfPresent", constant = @Constant(floatValue = 1.6F))
    private float void$nametagScale(float vanilla) {
        LiveState state = LiveState.get();
        if (!state.nametagsOn) {
            return vanilla;
        }
        return (float) (vanilla * Math.max(0.5, Math.min(2.0, state.nametagScale)));
    }

    /**
     * {@code nametags.plate} — the plate's four vertex colours, at zero alpha when off.
     *
     * <p>The four calls in this method are the quad and nothing else; the text is drawn by
     * {@code TextRenderer.draw} afterwards and never touches the buffer. Redirecting the colour
     * rather than the draw is argued in this class's own header.</p>
     */
    @Redirect(method = "renderLabelIfPresent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;"))
    private BufferBuilder void$nametagPlate(BufferBuilder buffer, float red, float green,
            float blue, float alpha) {
        LiveState state = LiveState.get();
        boolean hidden = state.nametagsOn && !state.nametagsPlate;
        return buffer.color(red, green, blue, hidden ? 0.0F : alpha);
    }
}
