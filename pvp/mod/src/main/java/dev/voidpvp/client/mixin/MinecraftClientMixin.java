package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-tick, resize and server connect/disconnect sensors (§6.6).
 *
 * <p>Thin on purpose: each hook forwards to {@link VoidClient} and the logic
 * lives in plain classes that can be unit-tested, because a Mixin cannot.</p>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /** One client tick: the 20 Hz beat behind the {@code tick} event. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void void$onTick(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onClientTick();
        }
    }

    /** The framebuffer changed size; the Ultralight view follows it (§6.2). */
    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void void$onResize(int width, int height, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client != null) {
            client.onResize();
        }
    }

    /**
     * A world is being loaded or torn down. This is 1.8.9's single
     * connect/disconnect funnel — joining a server, leaving one and switching
     * all pass through here — which makes it a steadier hook than the packet
     * handlers, several of which legacy yarn leaves unnamed.
     */
    @Inject(method = "connect(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V",
            at = @At("HEAD"))
    private void void$onConnect(ClientWorld world, String message, CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        MinecraftClient mc = (MinecraftClient) (Object) this;
        ServerInfo entry = mc.getCurrentServerEntry();
        client.onWorldChanged(world != null && entry != null,
                entry == null ? null : entry.address);
    }
}
