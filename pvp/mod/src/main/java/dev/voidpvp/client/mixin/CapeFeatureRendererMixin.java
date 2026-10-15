package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticState;
import dev.voidpvp.client.cosmetic.EquippedSet;
import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the vanilla cape for a player wearing a VOID cape, so the two are never drawn through each
 * other. Only when the VOID cape will actually draw: with the {@code cosmetics} mod off, or
 * {@code others} off for someone else, the player's own vanilla cape comes back.
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void void$hideVanillaCape(AbstractClientPlayerEntity player, float a, float b, float c, float d,
                                      float e, float f, float g, CallbackInfo ci) {
        LiveState live = LiveState.get();
        if (!live.cosmeticsOn || player == null) {
            return;
        }
        EquippedSet set = CosmeticState.get().of(EquippedSet.normalise(String.valueOf(player.getUuid())));
        if (set == null || set.get("cape") == null) {
            return;
        }
        if (!live.cosmeticsOthers && player != MinecraftClient.getInstance().player) {
            return;
        }
        ci.cancel();
    }
}
