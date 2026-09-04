package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code keys} sensor (§6.6): edge-triggered, straight off
 * {@code KeyBinding.setKeyPressed}, which is the one place Minecraft funnels
 * every key and mouse-button change through.
 *
 * <p>Reading rather than writing: the UI gets the player's keys as data, and
 * Ultralight receives no input at all while the HUD is up (§6.3).</p>
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    @Inject(method = "setKeyPressed", at = @At("TAIL"))
    private static void void$onKeyState(int keyCode, boolean pressed, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onKeyState(keyCode, pressed);
        }
    }

    @Inject(method = "releaseAllKeys", at = @At("TAIL"))
    private static void void$onReleaseAll(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onKeysReleased();
        }
    }
}
