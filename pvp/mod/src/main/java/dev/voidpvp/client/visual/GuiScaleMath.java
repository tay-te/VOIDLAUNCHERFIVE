package dev.voidpvp.client.visual;

/**
 * The {@code gui_scale} mod's arithmetic: what a requested GUI-scale step resolves to on this
 * window, and the size of a screen laid out at it.
 *
 * <p>{@link #resolve} is vanilla's own {@code ScaledResolution} loop, so a requested step behaves
 * exactly like the same value in Minecraft's GUI Scale option — including being clamped to the
 * largest step that still leaves the 320×240 vanilla menus room, and the unicode-font rule that
 * forces an even step. 0 means "follow Minecraft".</p>
 */
public final class GuiScaleMath {

    private GuiScaleMath() {
    }

    /**
     * The scale factor vanilla would use for {@code requested} on a framebuffer of this size.
     *
     * @param requested  a GUI-scale step, 0 for "auto"
     */
    public static int resolve(int requested, int width, int height, boolean unicode) {
        int limit = requested <= 0 ? 1000 : requested;
        int scale = 1;
        while (scale < limit && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++;
        }
        if (unicode && scale % 2 != 0 && scale != 1) {
            scale--;
        }
        return scale;
    }

    /**
     * The factor this mod uses for one of its two settings: the player's own GUI scale when the
     * setting is 0, otherwise the setting resolved on this window.
     */
    public static int effective(int setting, int vanillaScale, int width, int height,
                                boolean unicode) {
        return setting <= 0 ? vanillaScale : resolve(setting, width, height, unicode);
    }

    /** A dimension in scaled units at a factor: vanilla's {@code ceil(pixels / factor)}. */
    public static int scaled(int pixels, int factor) {
        return (int) Math.ceil(pixels / (double) Math.max(1, factor));
    }
}
