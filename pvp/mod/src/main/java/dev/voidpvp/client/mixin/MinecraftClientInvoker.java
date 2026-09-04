package dev.voidpvp.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code MinecraftClient.onResolutionChanged}, which is private.
 *
 * <p>Retina (§13) rewrites that method's arguments into pixels, but 1.8.9 only calls it when the
 * window is actually resized: at startup it assigns {@code width}/{@code height} directly from the
 * size the launcher asked for. Without a nudge the game would therefore run at point size until the
 * player happened to drag the window. Calling it once, with the same arguments the resize path
 * would pass, puts the game at the display's real resolution from the first frame.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientInvoker {

    @Invoker("onResolutionChanged")
    void void$onResolutionChanged(int width, int height);
}
