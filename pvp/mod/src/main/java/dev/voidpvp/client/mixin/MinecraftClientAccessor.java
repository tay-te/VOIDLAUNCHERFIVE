package dev.voidpvp.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Minecraft.debugFPS} — the FPS sensor's only source (§3). It is a
 * private static field with no getter, so it takes an accessor to read.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("currentFps")
    static int void$currentFps() {
        throw new AssertionError("mixin accessor was not applied");
    }
}
