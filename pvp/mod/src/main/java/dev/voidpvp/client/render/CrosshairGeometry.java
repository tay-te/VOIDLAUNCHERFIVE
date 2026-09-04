package dev.voidpvp.client.render;

import java.util.ArrayList;
import java.util.List;

/**
 * The crosshair's shape, in pixels relative to the exact screen centre.
 *
 * <p>The crosshair is the one thing in the client that is not HTML (§3): it has
 * to sit on the exact pixel centre, which a DOM element positioned inside a
 * scaled view cannot promise. It is still configured through the loadout like
 * everything else, so the geometry lives here as plain arithmetic that can be
 * tested, and {@link CrosshairRenderer} only fills the rectangles.</p>
 */
public final class CrosshairGeometry {

    /** {@code [x, y, width, height]}, relative to the centre point. */
    public static final class Rect {
        public final float x;
        public final float y;
        public final float w;
        public final float h;

        Rect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + " " + w + "x" + h + ")";
        }
    }

    private CrosshairGeometry() {
    }

    /** True when the style is drawn as a ring rather than as rectangles. */
    public static boolean isRing(String style) {
        return "circle".equals(style);
    }

    /** True when the vanilla crosshair pass should be left alone. */
    public static boolean keepsVanilla(String style) {
        return style == null || "default".equals(style);
    }

    /**
     * The rectangles to fill.
     *
     * @param style     one of {@code mods.json}'s crosshair styles
     * @param size      half-length of each arm, before GUI scale
     * @param thickness stroke thickness, before GUI scale
     * @param gap       empty gap between the centre and each arm
     * @param spread    extra gap from the {@code dynamic} setting, 0 when off
     */
    public static List<Rect> rects(String style, int size, int thickness, int gap, float spread) {
        List<Rect> out = new ArrayList<Rect>();
        int t = Math.max(1, thickness);
        float half = t / 2f;
        float g = Math.max(0, gap) + Math.max(0f, spread);
        int s = Math.max(1, size);

        if (style == null || "none".equals(style) || "default".equals(style)
                || "circle".equals(style)) {
            return out;
        }
        if ("dot".equals(style)) {
            out.add(new Rect(-half, -half, t, t));
            return out;
        }
        // cross and t_shape share the horizontal bar and the lower arm.
        out.add(new Rect(-g - s, -half, s, t));
        out.add(new Rect(g, -half, s, t));
        out.add(new Rect(-half, g, t, s));
        if (!"t_shape".equals(style)) {
            out.add(new Rect(-half, -g - s, t, s));
        }
        return out;
    }

    /** Extra gap the {@code dynamic} setting adds while the player is sprinting. */
    public static float dynamicSpread(boolean dynamic, boolean sprinting) {
        return dynamic && sprinting ? 2f : 0f;
    }
}
