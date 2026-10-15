package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code nametags.show_own}: your own nametag over your head in third person.
 *
 * <p>Vanilla's {@code LivingEntityRenderer.hasLabel(LivingEntity)} ends (offsets 126-163) with
 * {@code isHudEnabled && entity != dispatcher.cameraEntity && !invisible && rider == null}, and
 * the {@code != cameraEntity} clause is the whole reason you never see your own name. This answers
 * yes for the player when that clause is the only thing that said no: HUD on, not invisible,
 * nobody riding you. Your model is only drawn in third person, so that is the only time the label
 * can appear.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NametagOwnMixin {

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("RETURN"),
            cancellable = true)
    private void void$showOwn(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !VisualConfig.nametagsOn || !VisualConfig.nametagShowOwn) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && entity == mc.getCameraEntity()
                && MinecraftClient.isHudEnabled()
                && !entity.isInvisibleTo(mc.player) && entity.rider == null) {
            cir.setReturnValue(true);
        }
    }
}
