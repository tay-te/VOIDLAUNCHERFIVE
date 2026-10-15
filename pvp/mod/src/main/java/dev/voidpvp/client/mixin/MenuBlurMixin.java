package dev.voidpvp.client.mixin;

import dev.voidpvp.client.screen.MenuBlur;
import dev.voidpvp.client.screen.VoidMenuScreen;
import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code menu_blur} mod, on {@code Screen.renderBackground(int)}.
 *
 * <p>That method is how every in-world vanilla screen darkens the game behind it: with a world
 * loaded it is one {@code fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010)}. The blur is
 * drawn at its head, over the frame the world pass just finished, and the gradient's two alphas
 * are scaled by {@code menu_dim} so the wash goes on top of the blur rather than replacing it.
 * Screens that never call it — chat, VOID's own menu — are never blurred, which is the intended
 * scope. With no world (the title screen) vanilla draws dirt and nothing here runs.</p>
 */
@Mixin(Screen.class)
public abstract class MenuBlurMixin extends DrawableHelper {

    @Shadow
    protected MinecraftClient client;
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderBackground(I)V", at = @At("HEAD"))
    private void void$blur(int alpha, CallbackInfo ci) {
        if (VisualConfig.menuBlurOn && client != null && client.world != null
                && !((Object) this instanceof VoidMenuScreen)) {
            MenuBlur.get().draw(this, width, height);
        }
    }

    @Redirect(method = "renderBackground(I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V"))
    private void void$dim(Screen self, int x1, int y1, int x2, int y2, int top, int bottom) {
        if (VisualConfig.menuBlurOn) {
            top = void$scaleAlpha(top, VisualConfig.menuDim);
            bottom = void$scaleAlpha(bottom, VisualConfig.menuDim);
        }
        this.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    private static int void$scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
