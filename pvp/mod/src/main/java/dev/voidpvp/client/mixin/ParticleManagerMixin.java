package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.ParticleThinner;
import dev.voidpvp.client.perf.PerfConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code particles} mod.
 *
 * <ul>
 *   <li>{@code particle_cap} replaces the {@code sipush 4000} in {@code addParticle(Particle)}
 *       (vanilla's own per-layer limit), enforced the way vanilla enforces it: the oldest
 *       particle goes when a new one arrives at the cap.</li>
 *   <li>{@code frustum_cull} skips {@code Particle.draw} for particles outside the view cone in
 *       {@code renderParticles}. The frustum is built once per call from the matrices the world
 *       pass left current, exactly as vanilla's entity pass builds its own.</li>
 *   <li>{@code block_break_particles} thins the {@code addParticle} calls inside
 *       {@code addBlockBreakParticles} (the 64-piece burst) and
 *       {@code addBlockBreakingParticles} (the chips while mining).</li>
 * </ul>
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Shadow
    public abstract void addParticle(Particle particle);

    @Unique
    private CullingCameraView void$frustum;

    @ModifyConstant(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            constant = @Constant(intValue = 4000))
    private int void$cap(int vanilla) {
        return PerfConfig.particles() ? Math.max(1, PerfConfig.particleCap) : vanilla;
    }

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void void$buildFrustum(Entity camera, float tickDelta, CallbackInfo ci) {
        if (PerfConfig.particles() && PerfConfig.particleFrustumCull) {
            CullingCameraView view = new CullingCameraView();
            // Particle positions are world coordinates; the view's origin is the same
            // interpolated camera position renderParticles draws relative to.
            view.setPos(Particle.field_1722, Particle.field_1723, Particle.field_1724);
            void$frustum = view;
        } else {
            void$frustum = null;
        }
    }

    @Redirect(method = "renderParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;draw("
                    + "Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void void$drawIfVisible(Particle particle, BufferBuilder buffer, Entity camera,
                                    float tickDelta, float a, float b, float c, float d, float e) {
        CullingCameraView view = void$frustum;
        if (view != null) {
            Box box = particle.getBoundingBox();
            // Half a block of margin: a particle's sprite is wider than its tiny collision box.
            if (box != null && !view.isBoxInFrustum(box.minX - 0.5, box.minY - 0.5,
                    box.minZ - 0.5, box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5)) {
                return;
            }
        }
        particle.draw(buffer, camera, tickDelta, a, b, c, d, e);
    }

    @Redirect(method = {"addBlockBreakParticles", "addBlockBreakingParticles"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;"
                    + "addParticle(Lnet/minecraft/client/particle/Particle;)V"))
    private void void$thinBlockDust(ParticleManager self, Particle particle) {
        if (PerfConfig.particles()
                && !ParticleThinner.BLOCK_BREAK.keep(PerfConfig.blockBreakParticles)) {
            return;
        }
        addParticle(particle);
    }
}
