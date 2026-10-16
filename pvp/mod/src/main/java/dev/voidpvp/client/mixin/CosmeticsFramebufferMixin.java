package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticsHub;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Framebuffer.attachTexture} rebuilds the FBO (window resize): the cosmetics' bloom mask
 * that was attached to the old one has to be attached again.
 */
@Mixin(Framebuffer.class)
public abstract class CosmeticsFramebufferMixin {

    @Inject(method = "attachTexture", at = @At("TAIL"))
    private void void$rebuilt(int width, int height, CallbackInfo ci) {
        CosmeticsHub.framebufferRebuilt();
    }
}
