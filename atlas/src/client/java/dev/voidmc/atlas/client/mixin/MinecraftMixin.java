package dev.voidmc.atlas.client.mixin;

import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.Perf;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The start of every frame: Atlas's frame timing, and its once-per-frame client-thread work (tile uploads, ingest). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void atlas$frame(boolean advanceGameTime, CallbackInfo ci) {
		Perf.frame();
		AtlasClient client = AtlasClient.get();
		if (client != null) {
			client.frame();
		}
	}
}
