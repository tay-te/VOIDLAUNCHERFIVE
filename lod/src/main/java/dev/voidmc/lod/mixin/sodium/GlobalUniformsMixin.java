package dev.voidmc.lod.mixin.sodium;

import dev.voidmc.lod.ChunkFade;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium's chunk uniforms carry the radius its chunks dissolve into the LOD by ({@link ChunkFade}), in place of the
 * render-distance fog's start: negative, which no fog start is, while they do. The chunks never reach that fog (it
 * starts out at the LOD's edge), and the patched shader leaves it out then.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager$GlobalUniforms")
public abstract class GlobalUniformsMixin {
	@Shadow
	@Final
	@Mutable
	private float renderFogStart;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void voidLod$dissolve(CallbackInfo ci) {
		this.renderFogStart = ChunkFade.sodiumFogStart(this.renderFogStart);
	}
}
