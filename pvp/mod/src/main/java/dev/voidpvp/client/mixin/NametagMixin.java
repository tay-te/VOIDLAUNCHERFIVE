package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code nametags} mod's styling half, on {@code EntityRenderer.renderLabelIfPresent} — the
 * one method every nametag in 1.8.9 is drawn by (MCP's {@code renderLivingLabel}).
 *
 * <ul>
 *   <li>The four {@code 0.25f} vertex alphas of the plate behind the name become
 *       {@code tag_background}.</li>
 *   <li>The {@code 0.016666668f} that, times 1.6, scales a label into the world is multiplied by
 *       {@code tag_scale}.</li>
 *   <li>The second of the two text draws — the opaque pass after the see-through one at
 *       {@code 0x20FFFFFF} — is drawn with Minecraft's text shadow when {@code tag_shadow} is
 *       on. Only the opaque pass: a shadow on the see-through copy would darken names behind
 *       walls.</li>
 *   <li>{@code hide_others} cancels the label for anyone but the player. Armour stands are
 *       exempt: lobby holograms are stacks of named stands, and they are world text, not
 *       nametags.</li>
 * </ul>
 * <p>{@code show_own} is the other half, on {@code LivingEntityRenderer} — see
 * {@code NametagOwnMixin}.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class NametagMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void void$hideOthers(Entity entity, String text, double x, double y, double z,
                                 int maxDistance, CallbackInfo ci) {
        if (VisualConfig.nametagsOn && VisualConfig.nametagHideOthers
                && entity != MinecraftClient.getInstance().player
                && !(entity instanceof ArmorStandEntity)) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderLabelIfPresent", constant = @Constant(floatValue = 0.25F))
    private float void$plateAlpha(float vanilla) {
        return VisualConfig.nametagsOn ? VisualConfig.nametagBackground : vanilla;
    }

    @ModifyConstant(method = "renderLabelIfPresent", constant = @Constant(floatValue = 0.016666668F))
    private float void$scale(float vanilla) {
        return VisualConfig.nametagsOn ? vanilla * VisualConfig.nametagScale : vanilla;
    }

    @Redirect(method = "renderLabelIfPresent", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int void$shadow(TextRenderer font, String text, int x, int y, int color) {
        if (VisualConfig.nametagsOn && VisualConfig.nametagShadow) {
            return font.drawWithShadow(text, x, y, color);
        }
        return font.draw(text, x, y, color);
    }
}
