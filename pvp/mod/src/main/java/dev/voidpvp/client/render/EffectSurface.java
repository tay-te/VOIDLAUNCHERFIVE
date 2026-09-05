package dev.voidpvp.client.render;

/**
 * One rectangle the host draws a shadow behind, as the page reported it through
 * {@code void.setSurfaces} (§6.5, {@code bridge.json}).
 *
 * <p><b>Why the host draws these at all.</b> The overlay's CSS carries no blurred shadows: a
 * blurred {@code box-shadow} is the costliest primitive a CPU rasteriser has, and it is worse than
 * its area suggests because the shadow extends past its element, so changing one card dirties a
 * rectangle much larger than the card. Measured in game on a mod toggle, the authored ramp cost
 * 43-80 ms a repaint against 15-25 ms with the shadows off — more than every other optimisation
 * combined, and tightening the radii bought nothing because the cost is having a blur pass at all.
 *
 * <p>Dropping the effect is not the same as dropping the design. A blur is close to free on the GPU
 * that is already drawing the game, so the shadows are drawn there instead, from the values the
 * page sends — which the token build copies verbatim out of {@code design/tokens.css}. What lands
 * on screen is the Figma shadow, not an approximation of it, and a designer changing that file
 * changes both the launcher and the overlay with nothing to keep in sync by hand.</p>
 *
 * <p>Geometry arrives in CSS pixels, the space the page lays out in; the caller scales by the
 * device scale it already knows.</p>
 */
public final class EffectSurface {

    /** Which surface this is. Diagnostics only; never interpreted. */
    public final String id;
    /** Element bounds in CSS pixels. */
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    /** Corner radius in CSS pixels. */
    public final float radius;
    /** Shadow offset in CSS pixels. */
    public final float dx;
    public final float dy;
    /** Blur radius in CSS pixels; 0 is a hard edge. */
    public final float blur;
    /** Spread in CSS pixels. Negative shrinks the shadow, which is how the design uses it. */
    public final float spread;
    /** Straight-alpha colour, each channel 0-1. */
    public final float red;
    public final float green;
    public final float blue;
    public final float alpha;

    public EffectSurface(String id, float x, float y, float width, float height, float radius,
                         float dx, float dy, float blur, float spread,
                         float red, float green, float blue, float alpha) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.dx = dx;
        this.dy = dy;
        this.blur = blur;
        this.spread = spread;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /** Whether this is worth drawing at all. */
    public boolean visible() {
        return width > 0 && height > 0 && alpha > 0;
    }

    @Override
    public String toString() {
        return "EffectSurface{" + id + " " + width + "x" + height + " @" + x + "," + y
                + " blur=" + blur + "}";
    }
}
