package dev.voidpvp.client.mixin;

import dev.voidpvp.client.perf.FrameCap;
import dev.voidpvp.client.perf.PerfConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code frame_limiter} mod.
 *
 * <p>{@code MinecraftClient.getMaxFramerate()} is what the game loop hands {@code Display.sync}
 * (offsets 927-947 of {@code runGameLoop}), gated on {@code isFramerateValid()}, which is itself
 * "is {@code getMaxFramerate()} below the slider's unlimited end". So lowering this one return is
 * the whole mod: vanilla's own limiter does the sleeping. Vanilla's body already returns 30 on
 * the title screen; this replaces that and adds the other states.</p>
 */
@Mixin(MinecraftClient.class)
public abstract class FrameLimiterMixin {

    @Shadow
    public ClientWorld world;
    @Shadow
    public Screen currentScreen;

    @Inject(method = "getMaxFramerate", at = @At("RETURN"), cancellable = true)
    private void void$cap(CallbackInfoReturnable<Integer> cir) {
        // A running benchmark measures culling and particles; a background window must not
        // halve its numbers through this cap.
        if (!PerfConfig.frameLimiter() || dev.voidpvp.client.bench.PerfBench.active()) {
            return;
        }
        boolean focused;
        boolean visible;
        try {
            focused = Display.isActive();
            visible = Display.isVisible();
        } catch (Throwable t) {
            return;
        }
        boolean inMenu = world == null ? currentScreen != null
                : currentScreen instanceof GameMenuScreen;
        cir.setReturnValue(FrameCap.cap(cir.getReturnValueI(), focused, visible, inMenu,
                PerfConfig.unfocusedFps, PerfConfig.menuFps, PerfConfig.idleMinimized));
    }
}
