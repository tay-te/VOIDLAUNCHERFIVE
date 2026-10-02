package dev.voidmc.expanse.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a realm: no sun, moon, stars or sunset, and the realm's own sky colour whatever the hour (see {@link RealmSky}). */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void expanse$realmSky(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		Holder<Biome> realm = RealmSky.realm(level, camera);
		if (realm != null && state.skyColor != null) {
			state.rainBrightness = 0.0F;  // the alpha the sun and moon are drawn with
			state.starBrightness = 0.0F;
			state.sunriseAndSunsetColor = new Vector4f();
			state.skyColor = realm.value().getAttributes().applyModifier(EnvironmentAttributes.SKY_COLOR, state.skyColor);
		}
	}
}
