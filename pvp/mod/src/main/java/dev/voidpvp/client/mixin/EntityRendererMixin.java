package dev.voidpvp.client.mixin;

import dev.voidpvp.client.cosmetic.Badges;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The nametag badge. {@code EntityRenderer.renderLabelIfPresent} draws a label in its own
 * billboard matrix: plate, faint pass, full pass, then {@code GlStateManager.enableLighting()}
 * (offset 360, the method's only call to it) and the pop. Injecting at that call puts
 * {@link Badges#draw} inside the same matrix, after the name, with the depth state the full pass
 * left, so the badge is drawn in font pixels beside the name and nowhere else.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    public abstract TextRenderer getFontRenderer();

    @Inject(method = "renderLabelIfPresent", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;enableLighting()V", ordinal = 0))
    private void void$badge(Entity entity, String text, double x, double y, double z, int range, CallbackInfo ci) {
        Badges.draw(entity, text, getFontRenderer());
    }
}
