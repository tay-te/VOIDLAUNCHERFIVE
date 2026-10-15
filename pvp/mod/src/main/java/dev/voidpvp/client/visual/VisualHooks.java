package dev.voidpvp.client.visual;

import dev.voidpvp.client.screen.MenuBlur;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

/**
 * Render-side helpers for the Wave 7 visual mods that are more than one line in a Mixin.
 */
public final class VisualHooks {

    private static int seenGlassEpoch;

    private VisualHooks() {
    }

    /** Whether clear glass skips this block when chunks are built. */
    public static boolean isClearedGlass(Block b) {
        if (!VisualConfig.clearGlassOn || b == null) {
            return false;
        }
        if (b == Blocks.GLASS) {
            return true;
        }
        if (b == Blocks.GLASS_PANE) {
            return VisualConfig.clearPanes;
        }
        if (b == Blocks.STAINED_GLASS) {
            return VisualConfig.clearStained;
        }
        if (b == Blocks.STAINED_GLASS_PANE) {
            return VisualConfig.clearStained && VisualConfig.clearPanes;
        }
        return false;
    }

    /**
     * Once a client tick: rebuilds the loaded chunks when clear glass changed (glass is baked into
     * chunk meshes, so a switch only reaches the world through a rebuild — the same
     * {@code WorldRenderer.reload()} F3+A runs), and lets the menu blur free its framebuffers once
     * no screen is open.
     */
    public static void tick(MinecraftClient mc) {
        int epoch = VisualConfig.clearGlassEpoch;
        if (epoch != seenGlassEpoch) {
            seenGlassEpoch = epoch;
            if (mc.worldRenderer != null && mc.world != null) {
                mc.worldRenderer.reload();
            }
        }
        if (mc.currentScreen == null) {
            MenuBlur.get().idle();
        }
    }
}
