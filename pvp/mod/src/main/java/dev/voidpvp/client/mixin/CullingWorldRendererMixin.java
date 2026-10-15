package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.CullingHooks;
import dev.voidpvp.client.perf.ParticleThinner;
import dev.voidpvp.client.perf.PerfConfig;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two FPS-boost hooks on {@code WorldRenderer}.
 *
 * <ul>
 *   <li>The head of {@code renderEntities} hands the culling thread this frame's eye position
 *       ({@code perf/CullingHooks.beginEntityPass}). It is the one place every frame passes with
 *       the camera entity and the partial tick both in hand.</li>
 *   <li>The head of {@code addParticle(int, boolean, …)} thins explosion particles. Every
 *       particle a packet or an emitter spawns through the world comes through here with its
 *       {@code ParticleType} id, and the three explosion types — including the huge-explosion
 *       emitter, which re-spawns large explosions through this same method every tick — are the
 *       only ones touched.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public abstract class CullingWorldRendererMixin {

    @Shadow
    private ClientWorld world;

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void void$beginEntityPass(Entity camera, CameraView view, float tickDelta,
                                      CallbackInfo ci) {
        CullingHooks.beginEntityPass(world, camera, tickDelta);
    }

    @Inject(method = "addParticle(IZDDDDDD[I)V", at = @At("HEAD"), cancellable = true)
    private void void$thinExplosions(int id, boolean force, double x, double y, double z,
                                     double vx, double vy, double vz, int[] args,
                                     CallbackInfo ci) {
        if (PerfConfig.particles() && ParticleThinner.isExplosion(id)
                && !ParticleThinner.EXPLOSIONS.keep(PerfConfig.explosionParticles)) {
            ci.cancel();
        }
    }
}
