package dev.voidpvp.client.visual;

import dev.voidpvp.client.HiDpi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Mouse;
import com.mojang.blaze3d.platform.GlStateManager;

/**
 * {@code gui_scale.inventory_scale}: container screens laid out and drawn at their own GUI scale.
 *
 * <p>A screen's coordinate space is just its {@code width} and {@code height}: vanilla hands it
 * {@code ceil(pixels / scale)} at {@code init}, converts every click to it with
 * {@code eventX * width / mc.width}, and draws it under an orthographic projection of the same
 * size. So a container screen at its own scale needs three things and no more:</p>
 * <ol>
 *   <li>its {@code init} width and height computed at the inventory scale
 *       ({@code GuiScaleScreenMixin}) — clicks then convert correctly on their own, because they
 *       divide by the screen's own width;</li>
 *   <li>its {@code render} call wrapped in a scale of {@code inventory / gui}, so a coordinate in
 *       its space lands on the pixel it means under vanilla's GUI projection, with the mouse
 *       position recomputed in its space ({@link #render});</li>
 *   <li>a re-{@code init} when the setting changes while the screen is open, which is the same
 *       {@code resize} a window resize triggers.</li>
 * </ol>
 * <p>Only {@code HandledScreen}s — the inventory, chests, furnaces, the creative inventory and
 * the rest. Chat, menus and VOID's own screen keep Minecraft's scale.</p>
 */
public final class InventoryScale {

    private InventoryScale() {
    }

    /** The factor a screen should be laid out at, or 0 when this mod leaves it alone. */
    public static int factorFor(Screen screen, MinecraftClient mc) {
        if (!(screen instanceof HandledScreen) || !VisualConfig.guiScaleOn
                || VisualConfig.inventoryScale <= 0) {
            return 0;
        }
        return GuiScaleMath.resolve(VisualConfig.inventoryScale, mc.width, mc.height,
                mc.forcesUnicodeFont());
    }

    /** Draws {@code screen} at its own scale. Called in place of vanilla's {@code render} call. */
    public static void render(Screen screen, MinecraftClient mc, int mouseX, int mouseY,
                              float delta) {
        Window window = new Window(mc);
        int factor = factorFor(screen, mc);
        int expectedWidth = factor == 0 ? window.getWidth() : GuiScaleMath.scaled(mc.width, factor);
        if (screen instanceof HandledScreen && screen.width != expectedWidth) {
            // The setting moved under an open screen (or the screen predates the mod turning on):
            // lay it out again exactly as a window resize would.
            screen.resize(mc, window.getWidth(), window.getHeight());
        }
        if (factor == 0 || factor == window.getScaleFactor()) {
            screen.render(mouseX, mouseY, delta);
            return;
        }
        int mx = HiDpi.toPixels(Mouse.getX()) * screen.width / Math.max(1, mc.width);
        int my = screen.height - HiDpi.toPixels(Mouse.getY()) * screen.height / Math.max(1, mc.height) - 1;
        float s = factor / (float) window.getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1f);
        try {
            screen.render(mx, my, delta);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
