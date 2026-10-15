package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.visual.GuiScaleMath;
import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two Wave 7 mods on {@code InGameHud.render}.
 *
 * <h2>{@code scoreboard}</h2>
 * <p>While the mod is on, VOID draws the sidebar on its HUD from the {@code scoreboard} bridge
 * channel, so vanilla's {@code renderScoreboardObjective} is cancelled at its head. Off restores
 * vanilla's untouched.</p>
 *
 * <h2>{@code gui_scale.hotbar_scale}</h2>
 * <p>The hotbar and the four things laid out against it — the status bars, the horse's health,
 * the experience bar and the held-item name — are each wrapped in a scale about the bottom centre
 * of the screen, so they move as one block. Each call is bracketed individually (an inject before
 * it, one after) rather than as one span from hotbar to held-item name, because the crosshair,
 * the boss bar and the sleep fade are drawn between them and must not move. Injects rather than
 * redirects, so another mod's redirect of the same call still composes.</p>
 */
@Mixin(InGameHud.class)
public abstract class VisualHudMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private boolean void$scaled;

    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void void$ownScoreboard(ScoreboardObjective objective, Window window,
                                    CallbackInfo ci) {
        if (VisualConfig.scoreboardOn) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(Lnet/minecraft/client/util/Window;F)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusBars(Lnet/minecraft/client/util/Window;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHorseHealth(Lnet/minecraft/client/util/Window;I)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderExperienceBar(Lnet/minecraft/client/util/Window;I)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHeldItemName(Lnet/minecraft/client/util/Window;)V")
    })
    private void void$pushHotbarScale(float tickDelta, CallbackInfo ci) {
        void$scaled = false;
        if (!VisualConfig.guiScaleOn || VisualConfig.hotbarScale <= 0) {
            return;
        }
        Window window = new Window(client);
        int vanilla = window.getScaleFactor();
        int wanted = GuiScaleMath.effective(VisualConfig.hotbarScale, vanilla, client.width,
                client.height, client.forcesUnicodeFont());
        if (wanted == vanilla) {
            return;
        }
        float s = wanted / (float) vanilla;
        float cx = window.getWidth() / 2f;
        float by = window.getHeight();
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, by, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-cx, -by, 0f);
        void$scaled = true;
    }

    @Inject(method = "render", at = {
            @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(Lnet/minecraft/client/util/Window;F)V"),
            @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusBars(Lnet/minecraft/client/util/Window;)V"),
            @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHorseHealth(Lnet/minecraft/client/util/Window;I)V"),
            @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/gui/hud/InGameHud;renderExperienceBar(Lnet/minecraft/client/util/Window;I)V"),
            @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHeldItemName(Lnet/minecraft/client/util/Window;)V")
    })
    private void void$popHotbarScale(float tickDelta, CallbackInfo ci) {
        if (void$scaled) {
            GlStateManager.popMatrix();
            void$scaled = false;
        }
    }
}
