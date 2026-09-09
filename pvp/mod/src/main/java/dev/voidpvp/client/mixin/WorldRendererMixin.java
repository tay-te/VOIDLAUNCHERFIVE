package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Block outline mod (§6.7): the box vanilla draws round the block you are looking at.
 *
 * <p><b>Every setting is one instruction, and the method was read rather than remembered.</b>
 * {@code WorldRenderer.drawBlockOutline(PlayerEntity, BlockHitResult, int, float)}:</p>
 *
 * <ul>
 *   <li>offsets 0-11 — the whole body is guarded by {@code param3 == 0 && hit.type == BLOCK}, so
 *       cancelling at HEAD is the entire suppression and costs less than vanilla rather than
 *       more;</li>
 *   <li>offsets 28-34 — {@code GlStateManager.color(0.0F, 0.0F, 0.0F, 0.4F)}, the ink;</li>
 *   <li>offsets 37-38 — {@code GL11.glLineWidth(2.0F)}, the width.</li>
 * </ul>
 *
 * <p>Each of those two calls appears <b>exactly once</b> in the method, which is what makes the
 * redirects unambiguous without an ordinal — and the reason that is worth stating is that an
 * ordinal is the part of an injection that goes stale silently. A second {@code color} call added
 * to this method upstream would not break a build; it would start tinting the wrong thing.</p>
 *
 * <p><b>Why this is a redirect and not a drawing.</b> {@code docs/mod-roster.md} §3.3 #3 rates
 * this M with "world-space GL, so it costs more than it looks", which is true of drawing an
 * outline and not of changing one. Vanilla draws it every frame you look at a block; the whole
 * mod is answering two of its questions differently.</p>
 *
 * <p><b>The colour is allowed to be a colour.</b> {@code design/quiet-cell-system.md} §1 rules a
 * per-element colour out on the menu's own surface and explicitly not on marks drawn over the
 * game world — the same terms that let {@code crosshair.color}, {@code hitboxes.color} and
 * {@code hit_color.color} exist. Alpha rides in the same value rather than in a second slider,
 * because every reason to change the outline is a reason about how far it stands out from the
 * block behind it and hue and alpha only mean anything together.</p>
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    /** {@code block_outline.hide} — the guarded body is the outline, so not entering it is all. */
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void void$hideBlockOutline(PlayerEntity player, BlockHitResult hit, int pass,
            float tickDelta, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (state.blockOutlineOn && state.blockOutlineHide) {
            ci.cancel();
        }
    }

    /**
     * {@code block_outline.color} — the one {@code color} call in the method.
     *
     * <p>Falls through to vanilla's own four arguments when the mod is off, rather than
     * reconstructing them from the default: a frame with the mod off then runs byte-identical to
     * one before this existed, and the constant that defines "vanilla's outline" stays in exactly
     * one place — the game.</p>
     */
    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"))
    private void void$blockOutlineColor(float red, float green, float blue, float alpha) {
        LiveState state = LiveState.get();
        if (!state.blockOutlineOn) {
            GlStateManager.color(red, green, blue, alpha);
            return;
        }
        int argb = state.blockOutlineColor;
        GlStateManager.color(
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
    }

    /** {@code block_outline.line_width} — the one {@code glLineWidth} call in the method. */
    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"))
    private void void$blockOutlineWidth(float width) {
        LiveState state = LiveState.get();
        // Clamped here as well as in the schema, because this argument leaves the JVM: a driver
        // handed a width outside its own range clamps silently, and a value that arrives here
        // wrong from an edited loadout would look like the mod not working rather than like a
        // number out of range.
        GL11.glLineWidth(state.blockOutlineOn
                ? (float) Math.max(0.5, Math.min(5.0, state.blockOutlineWidth))
                : width);
    }
}
