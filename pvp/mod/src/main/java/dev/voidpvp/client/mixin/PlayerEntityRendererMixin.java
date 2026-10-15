package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.CosmeticRenderer;
import dev.voidpvp.client.cosmetic.EmotePose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmetics on the two player renderers (wide and slim arms).
 *
 * <ul>
 *   <li><b>The layer.</b> {@link CosmeticRenderer} is appended to the feature list at the end of
 *       the two-argument constructor; the one-argument constructor delegates to it, so both
 *       renderers in the dispatcher's skin map get exactly one layer each. Last in the list, after
 *       vanilla's cape and skull layers, so the vanilla cape it replaces has already been skipped
 *       by {@link CapeFeatureRendererMixin}.</li>
 *   <li><b>The emote root.</b> At the tail of {@code setupTransforms} ({@code method_5777}, the
 *       player overload), where the matrix is rotated to the body's yaw and not yet flipped, the
 *       emote's whole-body offset is applied. Model, layers and armour move with it; the shadow,
 *       nametag and hitbox do not, which is right for a player sitting down where they stand.</li>
 * </ul>
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void void$addCosmetics(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).void$features()
                .add(new CosmeticRenderer((PlayerEntityRenderer) (Object) this));
    }

    @Inject(method = "method_5777(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFF)V", at = @At("TAIL"))
    private void void$emoteRoot(AbstractClientPlayerEntity player, float age, float yaw, float tickDelta,
                                CallbackInfo ci) {
        EmotePose.applyRoot(player);
    }
}
