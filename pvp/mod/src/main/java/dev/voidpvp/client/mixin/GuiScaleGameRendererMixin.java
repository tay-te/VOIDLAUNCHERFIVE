package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.InventoryScale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code gui_scale.inventory_scale}, drawing half: the one {@code Screen.render} call in
 * {@code GameRenderer.render} (offset 765) goes through {@code visual/InventoryScale}, which
 * draws a container screen under a scale of {@code inventory / gui} with the mouse in the
 * screen's own coordinates, and every other screen exactly as vanilla does.
 */
@Mixin(GameRenderer.class)
public abstract class GuiScaleGameRendererMixin {

    @Shadow
    private MinecraftClient client;

    @Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void void$renderScreen(Screen screen, int mouseX, int mouseY, float delta) {
        InventoryScale.render(screen, client, mouseX, mouseY, delta);
    }
}
