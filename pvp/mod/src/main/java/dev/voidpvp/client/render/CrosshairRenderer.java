package dev.voidpvp.client.render;

import dev.voidpvp.client.state.LiveState;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the crosshair mod at the exact centre of the screen, in GL, in place of
 * the vanilla pass (§3). Twenty lines, as advertised.
 */
public final class CrosshairRenderer {

    private CrosshairRenderer() {
    }

    /**
     * @param width     scaled GUI width, so the centre lands on the same pixel
     *                  the vanilla crosshair would have used
     * @param sprinting feeds the {@code dynamic} setting
     */
    public static void draw(LiveState state, int width, int height, boolean sprinting) {
        String style = state.crosshairStyle;
        if (!state.crosshairOn || CrosshairGeometry.keepsVanilla(style) || "none".equals(style)) {
            return;
        }
        float cx = width / 2f;
        float cy = height / 2f;
        int color = state.crosshairColor;
        float spread = CrosshairGeometry.dynamicSpread(state.crosshairDynamic, sprinting);

        GlBlit.begin2d(width, height);
        try {
            if (CrosshairGeometry.isRing(style)) {
                if (state.crosshairOutline) {
                    ring(cx, cy, state.crosshairSize + 1f, state.crosshairThickness + 2f,
                            0xFF000000);
                }
                ring(cx, cy, state.crosshairSize, state.crosshairThickness, color);
                return;
            }
            List<CrosshairGeometry.Rect> rects = CrosshairGeometry.rects(
                    style, state.crosshairSize, state.crosshairThickness,
                    state.crosshairGap, spread);
            if (state.crosshairOutline) {
                for (CrosshairGeometry.Rect r : rects) {
                    GlBlit.fill(cx + r.x - 1, cy + r.y - 1, r.w + 2, r.h + 2, 0xFF000000);
                }
            }
            for (CrosshairGeometry.Rect r : rects) {
                GlBlit.fill(cx + r.x, cy + r.y, r.w, r.h, color);
            }
        } finally {
            GlBlit.end2d();
        }
    }

    private static void ring(float cx, float cy, float radius, float thickness, int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(Math.max(1f, thickness));
        GL11.glColor4f(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2.0 / 32.0;
            GL11.glVertex2d(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
