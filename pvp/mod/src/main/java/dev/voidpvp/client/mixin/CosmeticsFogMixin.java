package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.cosmetic.CosmeticsHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the fog switch and mode as {@code GlStateManager} sets them, for the cosmetics'
 * material shader: GLSL reads the fog's colour, range and density from the built-ins, but not
 * whether fog is on or which formula it uses, and asking GL every frame would be a read-back.
 */
@Mixin(GlStateManager.class)
public abstract class CosmeticsFogMixin {

    @Inject(method = "enableFog", at = @At("HEAD"))
    private static void void$fogOn(CallbackInfo ci) {
        CosmeticsHub.fogEnabled(true);
    }

    @Inject(method = "disableFog", at = @At("HEAD"))
    private static void void$fogOff(CallbackInfo ci) {
        CosmeticsHub.fogEnabled(false);
    }

    @Inject(method = "fogMode", at = @At("HEAD"))
    private static void void$fogMode(int mode, CallbackInfo ci) {
        CosmeticsHub.fogMode(mode);
    }
}
