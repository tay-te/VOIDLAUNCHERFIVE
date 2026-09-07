package dev.voidpvp.client.mixin;

import dev.voidpvp.client.render.HitboxGeometry;
import dev.voidpvp.client.render.HitboxRenderer;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Hitboxes actuator's appearance (§6.7): {@code line_width}, {@code color} and
 * {@code show_eye_line}.
 *
 * <p>{@code EntityRenderDispatcher.renderHitbox} is the single funnel every debug box goes
 * through, and vanilla's version hard-codes its own white box, its own eye-level box and a blue
 * look ray — none of them configurable. So the mod cancels it and draws the same shape with the
 * loadout's values. <b>Whether it runs at all is still vanilla's decision</b>: the caller gates
 * this on the {@code renderHitboxes} flag, which is the flag {@code VoidClient.applyActuators}
 * sets from {@code hitboxes.on} and F3+B sets by hand. That is why this method does not need to
 * re-check the mod's own switch to avoid drawing when it is off — though {@link HitboxRenderer}
 * checks anyway, because a renderer that trusts its caller for whether to draw is a renderer
 * that draws when a future caller forgets.</p>
 *
 * <p>The eye-level box vanilla draws for living entities is deliberately not reproduced. It is a
 * second, nearly-coincident box a few hundredths of a block tall, and at any distance it reads
 * as a thickening of the main box rather than as information; the look ray answers the question
 * it was there for, and is what {@code show_eye_line} names.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private void void$renderHitbox(Entity entity, double x, double y, double z,
                                   float yaw, float tickDelta, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (!state.hitboxesOn || entity == null) {
            // The flag was set by F3+B rather than by the mod. Leave vanilla's box alone —
            // taking over a debug key we were not asked for is how a mod becomes a nuisance.
            return;
        }
        Box box = entity.getBoundingBox();
        if (box == null) {
            return;
        }
        ci.cancel();

        HitboxGeometry.Aabb rebased = HitboxGeometry.rebase(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                entity.x, entity.y, entity.z, x, y, z);

        HitboxGeometry.Edge eye = null;
        if (state.hitboxEyeLine) {
            Vec3d look = entity.getRotationVector(tickDelta);
            if (look != null) {
                eye = HitboxGeometry.eyeLine(x, y, z, entity.getEyeHeight(),
                        look.x, look.y, look.z);
            }
        }
        HitboxRenderer.draw(state, rebased, eye);
    }
}
