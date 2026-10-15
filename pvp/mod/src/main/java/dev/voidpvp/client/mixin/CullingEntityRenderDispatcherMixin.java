package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.CullingHooks;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code culling} mod's entity filter.
 *
 * <p>{@code EntityRenderDispatcher.shouldRender} is the frustum test {@code WorldRenderer}'s
 * per-chunk entity loop asks before every {@code renderEntity} (offset 842 of
 * {@code renderEntities} in the named 1.8.9 jar). Hooking its return means the cull only ever
 * turns vanilla's "draw" into "skip" — an entity outside the frustum is never reconsidered — and
 * the spectator outline pass, which asks {@code Entity.shouldRender} directly, is untouched.</p>
 *
 * <p>An entity hidden behind a wall still gets its nametag, through the renderer's own label
 * method, when {@code nametags_through_walls} is on (the default): vanilla shows player names
 * through walls, and a performance mod must not take that away.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class CullingEntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void void$cull(Entity entity, CameraView view, double camX, double camY, double camZ,
                           CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        int verdict = CullingHooks.entityVerdict(entity);
        if (verdict == CullingHooks.DRAW) {
            return;
        }
        cir.setReturnValue(false);
        if (verdict == CullingHooks.OCCLUDED) {
            CullingHooks.renderLabelOnly((EntityRenderDispatcher) (Object) this, entity);
        }
    }
}
