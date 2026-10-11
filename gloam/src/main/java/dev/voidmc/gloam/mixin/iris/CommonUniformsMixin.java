package dev.voidmc.gloam.mixin.iris;

import dev.voidmc.gloam.bridge.Uniforms;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the bridge's {@code void_*} uniforms to the set Iris builds a shader pack's custom uniforms from, once per
 * pipeline, so every program (composite, final, the Distant Horizons programs, VOID LOD's) can declare them. Iris's
 * own internals, so the injection is allowed to miss ({@code require = 0}): the pack then reads the data texture's
 * header mirror, and Gloam logs a warning when Iris never asks. Only applied when Iris is loaded
 * ({@link IrisMixinPlugin}).
 */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class CommonUniformsMixin {
	@Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), require = 0, remap = false)
	private static void gloam$addBridge(UniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier,
		CallbackInfo ci) {
		Uniforms.addTo(uniforms);
	}
}
