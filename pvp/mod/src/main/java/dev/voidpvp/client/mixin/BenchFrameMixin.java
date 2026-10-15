package dev.voidpvp.client.mixin;

import dev.voidpvp.client.bench.PerfBench;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The benchmark's frame clock: the head of {@code runGameLoop} is once per rendered frame, so the
 * gap between two calls is one frame's time. A single static boolean check when no bench is
 * running ({@code bench/PerfBench}).
 */
@Mixin(MinecraftClient.class)
public abstract class BenchFrameMixin {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void void$benchFrame(CallbackInfo ci) {
        PerfBench.frame();
    }
}
