package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Bloom sprites collected in eye space during one player's cosmetic pass and drawn in one batch
 * at the end: camera-facing by construction (they are quads in eye space), depth-tested against
 * the world so a wall still hides them, additive so they read as light on a dark sky and fade to
 * nearly nothing against a bright one — the way a real glow does — and never written to depth.
 *
 * <p>Deliberately modest: a soft falloff texture, alpha well under one, a size in pixels of the
 * item. A glow you notice as a glow is already too much.</p>
 */
final class GlowBatch {

    private static final int MAX = 64;
    private final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX];
    private final float[] size = new float[MAX], r = new float[MAX], g = new float[MAX], b = new float[MAX], a = new float[MAX];
    private int n;

    void clear() {
        n = 0;
    }

    boolean isEmpty() {
        return n == 0;
    }

    /** Adds a sprite centred at eye-space ({@code ex}, {@code ey}, {@code ez}), {@code half} wide. */
    void add(float ex, float ey, float ez, float half, int rgb, float alpha) {
        if (n >= MAX || alpha <= 0.004f || half <= 0) {
            return;
        }
        x[n] = ex;
        y[n] = ey;
        z[n] = ez;
        size[n] = half;
        r[n] = ((rgb >> 16) & 255) / 255f;
        g[n] = ((rgb >> 8) & 255) / 255f;
        b[n] = (rgb & 255) / 255f;
        a[n] = Math.min(1, alpha);
        n++;
    }

    /** Draws and clears. Leaves blend off, depth mask on, lighting as it found it (on). */
    void flush() {
        if (n == 0) {
            return;
        }
        GlStateManager.pushMatrix();
        GL11.glLoadIdentity();
        GlStateManager.bindTexture(Gl.bloomTexture());
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0f);
        Gl.fullBright();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int i = 0; i < n; i++) {
            float s = size[i];
            // Pull the sprite a little toward the eye so it is not cut in half by the surface it
            // sits on; scaled by distance so the nudge is the same on screen near and far.
            float pull = Math.min(0.08f, s * 0.6f);
            float len = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]);
            float k = len > 1e-4f ? (len - pull) / len : 1;
            float cx = x[i] * k, cy = y[i] * k, cz = z[i] * k;
            buf.vertex(cx - s, cy - s, cz).texture(0, 1).color(r[i], g[i], b[i], a[i]).next();
            buf.vertex(cx + s, cy - s, cz).texture(1, 1).color(r[i], g[i], b[i], a[i]).next();
            buf.vertex(cx + s, cy + s, cz).texture(1, 0).color(r[i], g[i], b[i], a[i]).next();
            buf.vertex(cx - s, cy + s, cz).texture(0, 0).color(r[i], g[i], b[i], a[i]).next();
        }
        t.draw();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
        n = 0;
    }
}
