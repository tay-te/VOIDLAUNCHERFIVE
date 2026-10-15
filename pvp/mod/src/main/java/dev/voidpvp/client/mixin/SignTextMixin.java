package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.PerfConfig;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.Texts;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/**
 * {@code culling.sign_text_distance}: past it, a sign draws its board and not its four lines.
 *
 * <p>1.8.9 re-wraps and re-draws every line of every sign every frame ({@code Texts.wrapLines},
 * then one {@code TextRenderer.draw} per line). The board is a model and cheap; the text is the
 * cost, and at 32 blocks a line is two or three pixels tall. The {@code x, y, z} the renderer is
 * called with are already relative to the camera, so their squared length is the distance.</p>
 */
@Mixin(SignBlockEntityRenderer.class)
public abstract class SignTextMixin {

    @Unique
    private boolean void$skipText;

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At("HEAD"))
    private void void$measure(SignBlockEntity sign, double x, double y, double z, float delta,
                              int stage, CallbackInfo ci) {
        void$skipText = PerfConfig.culling()
                && x * x + y * y + z * z > PerfConfig.signTextDistanceSq;
    }

    @Redirect(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Texts;wrapLines("
                    + "Lnet/minecraft/text/Text;ILnet/minecraft/client/font/TextRenderer;ZZ)"
                    + "Ljava/util/List;"))
    private List<Text> void$wrap(Text text, int width, TextRenderer font, boolean a, boolean b) {
        if (void$skipText) {
            return Collections.emptyList();
        }
        return Texts.wrapLines(text, width, font, a, b);
    }

    @Redirect(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int void$draw(TextRenderer font, String text, int x, int y, int color) {
        if (void$skipText) {
            return 0;
        }
        return font.draw(text, x, y, color);
    }
}
