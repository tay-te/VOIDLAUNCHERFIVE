package dev.voidmc.mettle.client.mixin.talents;

import dev.voidmc.mettle.character.cards.TalentHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Night Eyes (a talent): the dark is a little less dark, as a soft night-vision share in the lightmap. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class TalentsLightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void mettle$nightEyes(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (state.needsUpdate && player != null) {
			state.nightVisionEffectIntensity = TalentHooks.nightEyes(player, state.nightVisionEffectIntensity);
		}
	}
}
