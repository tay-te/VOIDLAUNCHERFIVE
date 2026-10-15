package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticRenderer;
import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the inventory's paper-doll render of the player, which fakes the body yaw to follow the
 * mouse. The cape must not simulate against that fake yaw (it would drag the real cape round), so
 * {@link CosmeticRenderer} draws the cloth's last shape and steps nothing while this is set.
 */
@Mixin(SurvivalInventoryScreen.class)
public abstract class SurvivalInventoryScreenMixin {

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private static void void$previewBegin(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity,
                                          CallbackInfo ci) {
        CosmeticRenderer.inGuiPreview = true;
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private static void void$previewEnd(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity,
                                        CallbackInfo ci) {
        CosmeticRenderer.inGuiPreview = false;
    }
}
