package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.GuiScaleMath;
import dev.voidpvp.client.visual.InventoryScale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * {@code gui_scale.inventory_scale}, layout half: a container screen is initialised with the
 * width and height of its own GUI scale. {@code Screen.init(MinecraftClient, int, int)} is the
 * single entry both {@code openScreen} and {@code resize} (window resize) go through. See
 * {@code visual/InventoryScale} for the whole mechanism.
 */
@Mixin(Screen.class)
public abstract class GuiScaleScreenMixin {

    @ModifyVariable(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private int void$width(int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int factor = InventoryScale.factorFor((Screen) (Object) this, mc);
        return factor == 0 ? width : GuiScaleMath.scaled(mc.width, factor);
    }

    @ModifyVariable(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("HEAD"),
            argsOnly = true, ordinal = 1)
    private int void$height(int height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int factor = InventoryScale.factorFor((Screen) (Object) this, mc);
        return factor == 0 ? height : GuiScaleMath.scaled(mc.height, factor);
    }
}
