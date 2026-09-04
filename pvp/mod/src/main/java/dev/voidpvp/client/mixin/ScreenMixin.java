package dev.voidpvp.client.mixin;

import dev.voidpvp.client.HiDpi;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The click half of Retina support (§13).
 *
 * <p>{@code Screen.handleMouse} turns a raw mouse position into GUI coordinates with
 * {@code Mouse.getEventX() * this.width / mc.width}. Once {@code MinecraftClientMixin} has put
 * {@code mc.width} in pixels, that divisor is twice what LWJGL's still-in-points event coordinate
 * was measured against, so every click in every vanilla screen — inventory, chat, options — lands
 * at half the position the player aimed at. Scaling the event coordinate restores the ratio.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Redirect(method = "handleMouse",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventX()I"))
    private int void$eventXInPixels() {
        return HiDpi.toPixels(Mouse.getEventX());
    }

    @Redirect(method = "handleMouse",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventY()I"))
    private int void$eventYInPixels() {
        return HiDpi.toPixels(Mouse.getEventY());
    }
}
