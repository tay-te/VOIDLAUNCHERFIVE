package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.visual.FogMath;
import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code fog} mod, on {@code GameRenderer.renderFog(int, float)}.
 *
 * <p>The method picks one of four branches — blindness, water, lava, or air (linear distance
 * fog, including the nether's thick fog) — and sets the fog through {@code GlStateManager}. The
 * head works out which branch this call will take, the same way the method does (blindness on the
 * camera entity, then {@code Camera.getSubmergedBlock}'s material), and the three setters are
 * redirected through {@code visual/FogMath} as transforms of vanilla's own values. Blindness and
 * the sky pass ({@code mode == -1}) pass through untouched.</p>
 */
@Mixin(GameRenderer.class)
public abstract class FogMixin {

    /** 0 = leave vanilla alone, 1 = air (linear), 2 = water, 3 = lava. */
    @Unique
    private int void$fogKind;
    @Unique
    private float void$pendingStart;

    @Inject(method = "renderFog", at = @At("HEAD"))
    private void void$classify(int mode, float tickDelta, CallbackInfo ci) {
        void$fogKind = 0;
        if (!VisualConfig.fogOn || mode == -1) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.world == null) {
            return;
        }
        if (camera instanceof LivingEntity
                && ((LivingEntity) camera).hasStatusEffect(StatusEffect.BLINDNESS)) {
            return;
        }
        Block block = Camera.getSubmergedBlock(mc.world, camera, tickDelta);
        Material m = block == null ? null : block.getMaterial();
        if (m == Material.WATER) {
            void$fogKind = 2;
        } else if (m == Material.LAVA) {
            void$fogKind = 3;
        } else {
            void$fogKind = 1;
        }
    }

    @Redirect(method = "renderFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogStart(F)V"))
    private void void$start(float vanilla) {
        if (void$fogKind == 1) {
            // Held until the end arrives: `reduced` is defined relative to where the fog ends,
            // and vanilla always sets the start immediately before the end.
            void$pendingStart = vanilla;
            return;
        }
        GlStateManager.fogStart(vanilla);
    }

    @Redirect(method = "renderFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogEnd(F)V"))
    private void void$end(float vanilla) {
        if (void$fogKind == 1) {
            int level = VisualConfig.distanceFog;
            GlStateManager.fogStart(FogMath.start(level, void$pendingStart, vanilla));
            GlStateManager.fogEnd(FogMath.end(level, vanilla));
            return;
        }
        GlStateManager.fogEnd(vanilla);
    }

    @Redirect(method = "renderFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogDensity(F)V"))
    private void void$density(float vanilla) {
        if (void$fogKind == 2) {
            GlStateManager.fogDensity(FogMath.density(VisualConfig.waterFog, vanilla));
        } else if (void$fogKind == 3) {
            GlStateManager.fogDensity(FogMath.density(VisualConfig.lavaFog, vanilla));
        } else {
            GlStateManager.fogDensity(vanilla);
        }
    }
}
