package dev.voidpvp.client.cosmetic;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;

/**
 * The few GL operations the cosmetics share: uploading a texture, picking an animation frame
 * through the texture matrix (so a display list compiled once serves every frame), full-bright
 * lightmap for emissive layers, the bloom sprite, and reading the modelview matrix for
 * billboards.
 */
final class Gl {

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int bloomTex = -1;

    private Gl() {
    }

    /** Uploads an image with nearest filtering (pixel art stays crisp); returns the GL name. */
    static int upload(BufferedImage img) {
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        return tex.getGlId();
    }

    /**
     * Selects frame {@code frame} of {@code frames} stacked vertically: UVs authored for one frame
     * are scaled and offset by the texture matrix. Pair with {@link #resetFrame}.
     */
    static void selectFrame(int frame, int frames) {
        if (frames <= 1) {
            return;
        }
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f / frames, 1.0f);
        GlStateManager.translate(0.0f, (float) frame, 0.0f);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    static void resetFrame(int frames) {
        if (frames <= 1) {
            return;
        }
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    /** Sets the lightmap to a packed {@code sky << 16 | block} value. */
    static void lightmap(int packed) {
        GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, (float) (packed & 0xFFFF), (float) (packed >>> 16));
    }

    static void fullBright() {
        GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, 240.0f, 240.0f);
    }

    /** A 32x32 soft round falloff, uploaded once: the bloom sprite. */
    static int bloomTexture() {
        if (bloomTex >= 0) {
            return bloomTex;
        }
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                // A tight core and a long, faint shoulder: reads as light, not as a disc.
                double a = r >= 1 ? 0 : Math.pow(1 - r, 2.4) * 0.75 + Math.pow(Math.max(0, 1 - r * 2.2), 2) * 0.25;
                int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
                img.setRGB(x, y, (al << 24) | 0xFFFFFF);
            }
        }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        bloomTex = tex.getGlId();
        // Linear here, unlike every other cosmetic texture: a bloom must not have pixels.
        GlStateManager.bindTexture(bloomTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return bloomTex;
    }

    /**
     * The current modelview matrix, column-major. One {@code glGet} per call: used once per
     * player that has glow, never per sprite.
     */
    static float[] modelview(float[] out) {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        MATRIX.get(out, 0, 16);
        return out;
    }
}
