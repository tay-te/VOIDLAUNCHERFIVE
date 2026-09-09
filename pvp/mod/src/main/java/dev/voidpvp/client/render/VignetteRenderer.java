package dev.voidpvp.client.render;

import dev.voidpvp.client.actuator.DamageTint;
import org.lwjgl.opengl.GL11;

/**
 * The low-health vignette of {@code damage_tint} (§3): four gradient quads around the edge of the
 * frame, drawn in the game's own overlay pass beside {@link CrosshairRenderer}.
 *
 * <p>Four quads and not a post-process, and not a node in the Ultralight page.
 * {@code schema/mods/damage_tint.json} gives both reasons: a full-viewport node whose alpha
 * follows health would dirty the whole view on every health tick, which
 * {@code design/rendering-invariants.md} §5 measures at 22 ms against 0.5 ms; and a vignette is
 * one alpha gradient, which is two orders of magnitude cheaper than the full-screen effects the
 * roster refuses on the same budget.</p>
 *
 * <p>Red, and there is no setting for it. The mod that owns a colour is {@code hit_color}, and it
 * defaults away from red so that this one can keep it — the trade is made once, in the schema,
 * rather than left to whichever settings page a player opens second.</p>
 */
public final class VignetteRenderer {

    /** The one colour, unconfigurable on purpose. */
    private static final float R = 1f;
    private static final float G = 0f;
    private static final float B = 0f;

    private VignetteRenderer() {
    }

    /**
     * @param alpha peak alpha at the edge, from
     *              {@link DamageTint#vignetteAlpha(boolean, float, int, float)}; 0 draws nothing
     */
    public static void draw(int width, int height, float alpha) {
        if (alpha <= 0f || width <= 0 || height <= 0) {
            return;
        }
        float band = DamageTint.bandPixels(width, height);
        GlBlit.begin2d(width, height);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_QUADS);
            // Top, bottom, left, right. The corners are covered twice and therefore read a
            // little deeper, which is the direction a vignette wants anyway.
            edge(alpha, 0, 0, width, 0, 0, band);
            edge(alpha, 0, height, width, 0, 0, -band);
            edge(alpha, 0, 0, 0, height, band, 0);
            edge(alpha, width, 0, 0, height, -band, 0);
            GL11.glEnd();
        } finally {
            GlBlit.end2d();
        }
    }

    /**
     * One edge band: a quad at {@code alpha} along the screen edge, running {@code (ex, ey)} to
     * the edge's far corner and fading to nothing {@code (ix, iy)} inward. Exactly one of
     * {@code ex}/{@code ey} is non-zero, and exactly one of {@code ix}/{@code iy}, and never the
     * same axis — that is what makes the quad a band rather than a wedge.
     */
    private static void edge(float alpha, float x, float y, float ex, float ey,
                             float ix, float iy) {
        GL11.glColor4f(R, G, B, alpha);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + ex, y + ey);
        GL11.glColor4f(R, G, B, 0f);
        GL11.glVertex2f(x + ex + ix, y + ey + iy);
        GL11.glVertex2f(x + ix, y + iy);
    }
}
