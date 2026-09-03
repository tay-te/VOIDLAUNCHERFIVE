package dev.voidpvp.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * The two GL primitives the mod draws with: a textured quad and a solid quad.
 *
 * <p>Everything here talks to LWJGL directly rather than through Minecraft's
 * {@code GlStateManager}, and brackets its work with
 * {@code glPushAttrib}/{@code glPopAttrib}. That matters: {@code GlStateManager}
 * caches GL state, so changing state behind its back and leaving it changed
 * would desync the cache and corrupt the game's own rendering. Restoring the
 * real GL state before returning keeps the cache honest.</p>
 */
public final class GlBlit {

    /** Everything we touch, so the pop puts the game back exactly as it was. */
    private static final int ATTRIB_MASK = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_TRANSFORM_BIT | GL11.GL_VIEWPORT_BIT;

    private GlBlit() {
    }

    /** Saves GL state and sets up an orthographic pass over {@code w x h} pixels. */
    public static void begin2d(int width, int height) {
        GL11.glPushAttrib(ATTRIB_MASK);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** Restores everything {@link #begin2d} saved. */
    public static void end2d() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Draws a texture over a rectangle.
     *
     * @param premultiplied Ultralight hands us premultiplied alpha, which needs
     *                      {@code (ONE, ONE_MINUS_SRC_ALPHA)}; pass false for a
     *                      straight-alpha texture
     * @param flipV         true for a top-left-origin texture, which is what
     *                      Ultralight produces and OpenGL does not expect
     */
    public static void drawTexture(int texture, float x, float y, float w, float h,
                                   boolean premultiplied, boolean flipV, float alpha) {
        drawTexture(texture, x, y, w, h, premultiplied, flipV, alpha, 1f, 1f);
    }

    /**
     * As above, sampling only the {@code uvScaleX x uvScaleY} corner of the
     * texture — the binding is allowed to back a view with a larger texture.
     */
    public static void drawTexture(int texture, float x, float y, float w, float h,
                                   boolean premultiplied, boolean flipV, float alpha,
                                   float uvScaleX, float uvScaleY) {
        if (texture == 0) {
            return;
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        if (premultiplied) {
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glColor4f(alpha, alpha, alpha, alpha);

        float u1 = uvScaleX <= 0f ? 1f : uvScaleX;
        float vMax = uvScaleY <= 0f ? 1f : uvScaleY;
        float v0 = flipV ? vMax : 0f;
        float v1 = flipV ? 0f : vMax;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0f, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /** Draws a solid ARGB rectangle. */
    public static void fill(float x, float y, float w, float h, int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
