package dev.voidmc.lod.mixin.iris;

import com.google.common.collect.ImmutableList;
import dev.voidmc.lod.compat.iris.PackLod;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DISTANT_HORIZONS for the pack while the LOD renders: Iris defines it only with the Distant Horizons mod installed.
 * Packs that support Distant Horizons then fog out to dhRenderDistance instead of the render distance and treat the far
 * terrain in dhDepthTex as land, which is what the LOD gives them.
 */
@Mixin(value = StandardMacros.class, remap = false)
public abstract class StandardMacrosMixin {
	@Inject(method = "createStandardEnvironmentDefines", at = @At("RETURN"), cancellable = true)
	private static void voidLod$distantHorizons(CallbackInfoReturnable<ImmutableList<StringPair>> cir) {
		if (!PackLod.wanted()) {
			return;
		}
		ImmutableList<StringPair> defines = cir.getReturnValue();
		for (StringPair define : defines) {
			if (define.key().equals("DISTANT_HORIZONS")) {
				return;
			}
		}
		cir.setReturnValue(ImmutableList.<StringPair>builder().addAll(defines).add(new StringPair("DISTANT_HORIZONS", "")).build());
	}
}
