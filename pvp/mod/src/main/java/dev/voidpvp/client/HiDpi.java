package dev.voidpvp.client;

import org.lwjgl.opengl.Display;

/**
 * Retina support for 1.8.9 (§13).
 *
 * <p>The launcher starts the JVM with {@code -Dorg.lwjgl.opengl.Display.enableHighDPI=true}, so on
 * a 2x display AppKit gives LWJGL a backing store at the real pixel size. LWJGL then only
 * <em>records</em> the ratio, behind {@link Display#getPixelScaleFactor()}: it does not apply it to
 * {@link Display#getWidth()} or to {@code Mouse}, both of which keep reporting points. 1.8.9 knows
 * nothing about any of this — it sizes its viewport and its framebuffer from {@code Display
 * .getWidth()} — so left alone the game draws at half resolution into one corner of the drawable.
 *
 * <p>This class is the one place that ratio is applied. {@code MinecraftClientMixin} converts the
 * size 1.8.9 is told about into pixels, which carries through to the framebuffer and to
 * {@code Window}'s GUI scale; {@code ScreenMixin} and {@code GameRendererMixin} convert mouse
 * positions the same way, because every one of 1.8.9's mouse mappings divides by that same size.
 * Miss one and the pointer lands at half the position the player aimed at.
 *
 * <p>Everywhere without a scaled display — Windows, Linux, a 1x monitor — the factor is 1 and every
 * method here is the identity.
 */
public final class HiDpi {

    private HiDpi() {
    }

    /** The backing-store ratio, or 1 when there is no scaling or the display is not up yet. */
    public static float scale() {
        try {
            float s = Display.getPixelScaleFactor();
            return s > 0f ? s : 1f;
        } catch (Throwable ignored) {
            // Called before Display.create(), or a platform without the concept.
            return 1f;
        }
    }

    /** Whether the drawable is bigger than the window, i.e. any of this does anything. */
    public static boolean active() {
        return scale() > 1.0001f;
    }

    /** Points to pixels, for a size or a coordinate 1.8.9 will divide by a pixel size. */
    public static int toPixels(int points) {
        return active() ? Math.round(points * scale()) : points;
    }

    /** Points to pixels for a double coordinate. */
    public static double toPixels(double points) {
        return active() ? points * scale() : points;
    }
}
