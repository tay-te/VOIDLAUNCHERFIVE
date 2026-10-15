package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticsHub;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cosmetics' client beat, kept out of {@code VoidClient}: {@link CosmeticsHub#tick} at the end
 * of every client tick, {@link CosmeticsHub#worldChanged} when a world is joined or left. Separate
 * from {@link MinecraftClientMixin} so the cosmetics can be read, and removed, as one unit.
 */
@Mixin(MinecraftClient.class)
public abstract class CosmeticsHooksMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void void$cosmeticsTick(CallbackInfo ci) {
        CosmeticsHub.tick((MinecraftClient) (Object) this);
    }

    @Inject(method = "connect(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("TAIL"))
    private void void$cosmeticsWorld(ClientWorld world, String message, CallbackInfo ci) {
        CosmeticsHub.worldChanged();
    }
}
