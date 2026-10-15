package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.CullingHooks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code culling} mod's block-entity filter: chests, signs, banners, skulls and the rest.
 *
 * <p>{@code renderEntity(BlockEntity, float, int)} is the funnel both of {@code WorldRenderer}'s
 * block-entity loops draw through, and its own first act is vanilla's distance test against
 * {@code getSquaredRenderDistance()} (64 blocks). Cancelling at its head with the same distance
 * the dispatcher is about to compute is the whole hook: past {@code block_entity_distance}, or
 * hidden behind a wall, the tile entity is not drawn this frame.</p>
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class CullingBlockEntityRenderDispatcherMixin {

    @Shadow
    public double cameraX;
    @Shadow
    public double cameraY;
    @Shadow
    public double cameraZ;

    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;FI)V",
            at = @At("HEAD"), cancellable = true)
    private void void$cull(BlockEntity be, float tickDelta, int destroyStage, CallbackInfo ci) {
        if (be == null) {
            return;
        }
        double distSq = be.getSquaredDistance(cameraX, cameraY, cameraZ);
        if (CullingHooks.blockEntityVerdict(be, distSq) != CullingHooks.DRAW) {
            ci.cancel();
        }
    }
}
