package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import dev.voidpvp.client.actuator.HitTint;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.FloatBuffer;

/**
 * The Hit colour actuator (§6.7): the red flash the game draws on an entity, in a different hue
 * and at a fraction of the alpha the game drew it at.
 *
 * <p><b>Where this is and how that was established.</b> Disassembling the named 1.8.9 jar
 * ({@code net.legacyfabric:yarn:1.8.9+build.604}, Loom's
 * {@code minecraft-merged-legacy-intermediary-1-v2} artifact — not the {@code -intermediary}
 * sibling) with {@code javap -p -c}: {@code LivingEntityRenderer} carries one method with the
 * shape of MCP's {@code RendererLivingEntity.setBrightness}, and legacy yarn does not name it —
 * it is {@code method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z}, called by
 * {@code method_10258(T,F)} with {@code combineTextures = true} from the model render. It sets up
 * two texture units and then fills the {@code protected FloatBuffer buffer} with four floats and
 * hands it to {@code GL11.glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, buffer)}:</p>
 *
 * <ul>
 *   <li>Offsets 36-55 compute vanilla's own condition into a local:
 *       {@code hurtTime > 0 || deathTime > 0}. That local picks the branch.</li>
 *   <li>Offsets 350-387, the hurt branch: {@code put(1.0F) put(0.0F) put(0.0F) put(0.3F)} —
 *       opaque red at alpha 0.3.</li>
 *   <li>Offsets 391-493, the other branch: the entity's own brightness-combined colour, which
 *       has nothing to do with this mod.</li>
 *   <li>Offset 502: the single {@code glTexEnv(IILjava/nio/FloatBuffer;)V} in the method, after
 *       {@code buffer.flip()}. Both branches meet there.</li>
 *   <li>The lightmap unit combines with {@code GLX.interpolate}, source0 {@code constant},
 *       source1 {@code previous}, source2 {@code constant} operand {@code SRC_ALPHA} — that is
 *       {@code constant.rgb * constant.a + previous.rgb * (1 - constant.a)}, so the buffer's
 *       fourth float is the entire strength of the flash. See {@link HitTint}.</li>
 * </ul>
 *
 * <p><b>Injected at the {@code glTexEnv} call rather than at the four {@code put}s.</b> The puts
 * are eight matching {@code FloatBuffer.put(F)} instructions across the two branches and picking
 * four of them by ordinal is a bet on instruction order; the {@code glTexEnv} call is unique in
 * the method and is the actual consumer. It also means this hook holds the erased method
 * parameters, so vanilla's own {@code hurtTime > 0 || deathTime > 0} can be <b>recomputed here
 * rather than assumed</b> — which is what guarantees the mod can only ever recolour a flash the
 * game already drew, never add one. Tinting an entity vanilla did not tint is named in
 * {@code schema/mods/hit_color.json} as what would move this mod to `grey`.</p>
 *
 * <p>The writes are absolute {@code put(int, float)}, so they do not disturb the position and
 * limit {@code flip()} just set; the call two instructions later reads the same four slots.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    /** The same four-float scratch buffer vanilla filled two instructions ago. */
    @Shadow
    protected FloatBuffer buffer;

    @Inject(method = "method_10252", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V"))
    private void void$recolourHurtOverlay(LivingEntity entity, float tickDelta,
                                          boolean combineTextures,
                                          CallbackInfoReturnable<Boolean> cir) {
        LiveState state = LiveState.get();
        if (!state.hitColorOn || entity == null) {
            return;
        }
        // Vanilla's own condition, recomputed. Everything outside it is the other branch, which
        // is the entity's brightness colour and is none of this mod's business.
        boolean vanillaTinted = entity.hurtTime > 0 || entity.deathTime > 0;
        boolean own = false;
        if (state.hitColorOwnHitsOnly) {
            VoidClient client = VoidClient.get();
            own = client != null && client.isOwnHit(entity.getEntityId());
        }
        if (!HitTint.tints(true, vanillaTinted, state.hitColorOwnHitsOnly, own)) {
            return;
        }
        FloatBuffer target = this.buffer;
        if (target == null || target.limit() < 4) {
            return;
        }
        int rgb = state.hitColorRgb;
        target.put(0, HitTint.red(rgb));
        target.put(1, HitTint.green(rgb));
        target.put(2, HitTint.blue(rgb));
        target.put(3, HitTint.alpha(state.hitColorIntensity));
    }
}
