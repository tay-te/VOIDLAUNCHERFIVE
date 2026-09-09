package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook's input half (§6.7): the mouse goes into the camera instead of into the player.
 *
 * <p><b>{@code Entity.increaseTransforms(FF)V} is the entire mouse-look path of 1.8.9, and that
 * is a fact about the jar rather than a hope.</b> Disassembling the named 1.8.9 jar
 * ({@code net.legacyfabric:yarn:1.8.9+build.604}, Loom's
 * {@code minecraft-merged-legacy-intermediary-1-v2} artifact — not the {@code -intermediary}
 * sibling) with {@code javap -p -c} and searching every class in it, the method has exactly two
 * callers, both in {@code GameRenderer.render(FJ)V} and both on {@code client.player}: offset
 * 327 on the smooth-camera branch and offset 358 on the ordinary one. Nothing else in the game
 * turns an entity with it. So cancelling it here is the same two injection points a redirect on
 * those two call sites would have reached, with no third case to have missed.</p>
 *
 * <p>They sit inside {@code render}'s "mouse" profiler section, behind
 * {@code client.focused && Display.isActive()} — so this runs only with the mouse grabbed and no
 * screen open, once per frame. That is why it is also where freelook samples its key: sampling
 * it anywhere else leaves a frame in which the key was down and the delta still turned the
 * player, and a mod whose promise is "a look and never a turn" cannot afford one.</p>
 *
 * <p><b>Why not a {@code @Redirect} on the two call sites, which is what this was first.</b>
 * The two instructions name the method on the subclass — {@code invokevirtual
 * ClientPlayerEntity.increaseTransforms} — but legacy yarn declares it on {@code Entity}, so
 * {@code ClientPlayerEntity.increaseTransforms} is not a member the mapping knows. Reading the
 * remapped classes back out of {@code build/libs} showed the consequence: every other injection
 * target in this mod came back in intermediary form and that one came back verbatim as
 * {@code Lnet/minecraft/entity/player/ClientPlayerEntity;increaseTransforms:(FF)V}, which at
 * runtime would have matched nothing and failed the mixin's own {@code require}. It compiled, it
 * passed the tests, and it would have crashed the game on load. Targeting the declaring class is
 * what makes the name remappable.</p>
 *
 * <p>Cancelling and not modifying the arguments: the deltas that arrive here have already been
 * through {@code sensitivity * 0.6 + 0.2} cubed times eight, the smoothing and
 * {@code invertYMouse}, so they are exactly what vanilla was about to turn the player by, and
 * freelook sweeps at the player's own sensitivity by construction rather than by a second copy
 * of the curve. When freelook is not engaged nothing is cancelled and the method runs
 * unchanged.</p>
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "increaseTransforms", at = @At("HEAD"), cancellable = true)
    private void void$freelookTurn(float dx, float dy, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null && client.freelookTurn((Entity) (Object) this, dx, dy)) {
            ci.cancel();
        }
    }
}
