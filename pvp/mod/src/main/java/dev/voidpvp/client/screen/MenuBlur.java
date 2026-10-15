package dev.voidpvp.client.screen;

import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;

/**
 * The {@code menu_blur} mod: the game blurred behind Minecraft's own screens.
 *
 * <p>Reuses {@link BlurBackdrop} — the framebuffer copy and quarter-resolution Gaussian VOID
 * already carries — in its forced mode. The menu's own backdrop is a different instance and stays
 * off ({@code design/quiet-cell-system.md} §1 keeps blur out of VOID's chrome); this one only
 * ever draws under vanilla screens, from {@code mixin/MenuBlurMixin}.</p>
 *
 * <p>The fade is keyed on the screen object: a new screen starts its blur from zero, a screen
 * that stays open keeps it. The GL objects are released by {@link #idle} once no screen is open,
 * from the client tick, so a closed inventory holds no framebuffers.</p>
 */
public final class MenuBlur {

    private static final MenuBlur INSTANCE = new MenuBlur();
    /** The fade-in, in nanoseconds: a fifth of a second. */
    private static final long FADE_NANOS = 200_000_000L;

    private BlurBackdrop backdrop;
    private Object screen;
    private long openedAt;

    private MenuBlur() {
    }

    public static MenuBlur get() {
        return INSTANCE;
    }

    /** Blurs what is in the framebuffer now, for {@code current}, under a screen of this size. */
    public void draw(Object current, int screenWidth, int screenHeight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (backdrop == null) {
            backdrop = new BlurBackdrop(true);
        }
        long now = System.nanoTime();
        if (current != screen) {
            screen = current;
            openedAt = now;
        }
        float alpha = 1f;
        if (VisualConfig.blurFade) {
            alpha = Math.min(1f, (now - openedAt) / (float) FADE_NANOS);
        }
        backdrop.drawBlur(screenWidth, screenHeight, Math.max(1, mc.width), Math.max(1, mc.height),
                VisualConfig.blurRadius, alpha);
    }

    /** No screen is open: forget the fade and free the framebuffers. Render thread only. */
    public void idle() {
        screen = null;
        if (backdrop != null) {
            backdrop.release();
        }
    }
}
