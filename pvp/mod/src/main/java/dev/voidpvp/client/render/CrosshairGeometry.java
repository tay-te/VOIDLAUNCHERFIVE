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
        return rects(style, size, thickness, gap, spread, false);
    }

    /**
     * The rectangles to fill, with the {@code center_dot} setting.
     *
     * <p>The dot rides on top of every drawn style, the ring included — a ring is the one
     * shape with room for it, and a ring with no centre is the hardest crosshair in the game
     * to aim with. {@code none} still draws nothing: off is off. {@code default} draws nothing
     * either, because that style is "leave the vanilla pass alone" and there is no VOID draw
     * to hang a dot on.</p>
     *
     * @param centerDot the {@code center_dot} setting
     */
    public static List<Rect> rects(String style, int size, int thickness, int gap, float spread,
            boolean centerDot) {
        List<Rect> out = new ArrayList<Rect>();
        int t = Math.max(1, thickness);
        float half = t / 2f;
        float g = Math.max(0, gap) + Math.max(0f, spread);
        int s = Math.max(1, size);

        if ("none".equals(style)) {
            return out;
        }
        if (centerDot && style != null && !"default".equals(style)) {
            out.add(new Rect(-half, -half, t, t));
        }
        if (style == null || "default".equals(style) || "circle".equals(style)) {
            return out;
        }
        if ("dot".equals(style)) {
            // With `center_dot` on, the dot is already in the list — adding it again would
            // fill the same rectangle twice, which the outline pass makes visible.
            if (!centerDot) {
                out.add(new Rect(-half, -half, t, t));
            }
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
