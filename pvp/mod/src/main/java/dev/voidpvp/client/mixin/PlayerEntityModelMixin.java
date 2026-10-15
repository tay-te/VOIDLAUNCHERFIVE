package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.EmotePose;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes pose the player model at the tail of {@code PlayerEntityModel.setAngles}: after vanilla
 * (and old animations, which only touches {@code BiPedModel}'s blocking constant) has posed the
 * limbs and copied them to the overlay layers, so the emote blends from exactly what would have
 * been drawn. See {@link EmotePose}. A no-op unless this entity is emoting.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void void$emote(float limbAngle, float limbDistance, float age, float headYaw, float headPitch,
                            float scale, Entity entity, CallbackInfo ci) {
        EmotePose.apply((PlayerEntityModel) (Object) this, entity);
    }
}
