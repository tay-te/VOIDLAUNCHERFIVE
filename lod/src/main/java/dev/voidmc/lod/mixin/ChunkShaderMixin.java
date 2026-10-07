package dev.voidmc.lod.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.voidmc.lod.ChunkFade;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's chunk shaders, patched as they load so the chunks dissolve into the LOD behind them ({@link ChunkFade}). */
@Mixin(ShaderManager.Configs.class)
public abstract class ChunkShaderMixin {
	@Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
	private void voidLod$dissolve(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		String source = cir.getReturnValue();
		if (source != null && ChunkFade.TERRAIN.equals(id)) {
			cir.setReturnValue(ChunkFade.patch(source, type));
		} else if (source != null && ChunkFade.SODIUM_TERRAIN.equals(id)) {
			cir.setReturnValue(ChunkFade.patchSodium(source, type));
		}
	}
}
