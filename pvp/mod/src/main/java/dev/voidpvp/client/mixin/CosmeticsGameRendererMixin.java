package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticsHub;
import dev.voidpvp.client.cosmetic.EmoteCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cosmetics' frame hooks on {@code GameRenderer}:
 *
 * <ul>
 *   <li>{@code render(FJ)V} head and tail: the emote camera's per-frame ease, the cosmetics frame
 *       budget, and the showcase's capture (the frame is complete and still in the bound
 *       framebuffer at the tail).</li>
 *   <li>{@code renderWorld(IFJ)V}, right after {@code ParticleManager.renderParticles} (offset
 *       1013, its only call there): trail particles draw where vanilla's do, with the camera
 *       transform applied and depth from the world.</li>
 *   <li>{@code renderWorld(IFJ)V} head, and its {@code profiler.swap("hand")} (unconditional, just
 *       before the hand's depth clear): the world pass opens and closes for the cosmetics' bloom,
 *       which composites there — after the world, before the hand and the HUD.</li>
 *   <li>{@code transformCamera}: the two reads of {@code lastThirdPersonDistance} and
 *       {@code thirdPersonDistance} (offsets 257-265, one each) go through
 *       {@link EmoteCamera#distance}, the identity unless the local player is emoting. The angle
 *       reads are rerouted in {@link GameRendererMixin}, where freelook already owns them: a field
 *       read can only be redirected once.</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class CosmeticsGameRendererMixin {

    @Shadow
    private MinecraftClient client;

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void void$cosmeticsFrameStart(float tickDelta, long nanos, CallbackInfo ci) {
        CosmeticsHub.frameStart();
    }

    @Inject(method = "render(FJ)V", at = @At("TAIL"))
    private void void$cosmeticsFrameEnd(float tickDelta, long nanos, CallbackInfo ci) {
        CosmeticsHub.frameEnd(client);
    }

    @Inject(method = "renderWorld(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V",
            shift = At.Shift.AFTER))
    private void void$cosmeticsTrails(int pass, float tickDelta, long limit, CallbackInfo ci) {
        CosmeticsHub.worldPass(client, tickDelta);
    }

    @Inject(method = "renderWorld(IFJ)V", at = @At("HEAD"))
    private void void$cosmeticsWorldStart(int pass, float tickDelta, long limit, CallbackInfo ci) {
        CosmeticsHub.worldStart();
    }

    @Inject(method = "renderWorld(IFJ)V", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=hand"))
    private void void$cosmeticsBloom(int pass, float tickDelta, long limit, CallbackInfo ci) {
        CosmeticsHub.beforeHand(client);
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/GameRenderer;thirdPersonDistance:F", opcode = Opcodes.GETFIELD))
    private float void$emoteDistance(GameRenderer self) {
        return EmoteCamera.distance(((GameRendererAccessor) self).void$thirdPersonDistance());
    }

    @Redirect(method = "transformCamera", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/GameRenderer;lastThirdPersonDistance:F", opcode = Opcodes.GETFIELD))
    private float void$emoteLastDistance(GameRenderer self) {
        return EmoteCamera.distance(((GameRendererAccessor) self).void$lastThirdPersonDistance());
    }
}
