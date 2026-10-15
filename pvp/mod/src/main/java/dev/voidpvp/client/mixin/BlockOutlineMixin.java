package dev.voidpvp.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code block_outline} mod.
 *
 * <p>{@code WorldRenderer.drawBlockOutline} sets a colour ({@code 0, 0, 0, 0.4} at offset 34),
 * a line width ({@code 2.0} at offset 38), and hands the targeted block's selection box —
 * already expanded by 0.002 and rebased on the camera — to {@code drawBox}. Each of the three is
 * redirected, so the block, the moment and the shape all stay vanilla's; only how the box is
 * inked changes. {@code none} cancels the method at its head, which is also what happens when
 * the method's own gate would have drawn nothing.</p>
 */
@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void void$none(PlayerEntity player, BlockHitResult hit, int mode, float tickDelta,
                           CallbackInfo ci) {
        if (VisualConfig.outlineOn && !VisualConfig.outlineLines && !VisualConfig.outlineFill) {
            ci.cancel();
        }
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"))
    private void void$color(float r, float g, float b, float a) {
        if (VisualConfig.outlineOn) {
            void$ink(VisualConfig.outlineColor);
        } else {
            GlStateManager.color(r, g, b, a);
        }
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"))
    private void void$width(float vanilla) {
        GL11.glLineWidth(VisualConfig.outlineOn ? VisualConfig.outlineWidth : vanilla);
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/util/math/Box;)V"))
    private void void$box(Box box) {
        if (!VisualConfig.outlineOn) {
            WorldRenderer.drawBox(box);
            return;
        }
        if (VisualConfig.outlineFill) {
            void$ink(VisualConfig.outlineFillColor);
            GlStateManager.disableCull();
            void$fill(box);
            GlStateManager.enableCull();
            void$ink(VisualConfig.outlineColor);
        }
        if (VisualConfig.outlineLines) {
            WorldRenderer.drawBox(box);
        }
    }

    private static void void$ink(int argb) {
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    /** The six faces of the box as quads in the current colour. */
    private static void void$fill(Box b) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION);
        // Bottom and top.
        buf.vertex(b.minX, b.minY, b.minZ).next();
        buf.vertex(b.maxX, b.minY, b.minZ).next();
        buf.vertex(b.maxX, b.minY, b.maxZ).next();
        buf.vertex(b.minX, b.minY, b.maxZ).next();
        buf.vertex(b.minX, b.maxY, b.minZ).next();
        buf.vertex(b.minX, b.maxY, b.maxZ).next();
        buf.vertex(b.maxX, b.maxY, b.maxZ).next();
        buf.vertex(b.maxX, b.maxY, b.minZ).next();
        // North and south.
        buf.vertex(b.minX, b.minY, b.minZ).next();
        buf.vertex(b.minX, b.maxY, b.minZ).next();
        buf.vertex(b.maxX, b.maxY, b.minZ).next();
        buf.vertex(b.maxX, b.minY, b.minZ).next();
        buf.vertex(b.minX, b.minY, b.maxZ).next();
        buf.vertex(b.maxX, b.minY, b.maxZ).next();
        buf.vertex(b.maxX, b.maxY, b.maxZ).next();
        buf.vertex(b.minX, b.maxY, b.maxZ).next();
        // West and east.
        buf.vertex(b.minX, b.minY, b.minZ).next();
        buf.vertex(b.minX, b.minY, b.maxZ).next();
        buf.vertex(b.minX, b.maxY, b.maxZ).next();
        buf.vertex(b.minX, b.maxY, b.minZ).next();
        buf.vertex(b.maxX, b.minY, b.minZ).next();
        buf.vertex(b.maxX, b.maxY, b.minZ).next();
        buf.vertex(b.maxX, b.maxY, b.maxZ).next();
        buf.vertex(b.maxX, b.minY, b.maxZ).next();
        t.draw();
    }
}
