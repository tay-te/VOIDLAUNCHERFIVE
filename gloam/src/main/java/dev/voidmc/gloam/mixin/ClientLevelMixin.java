package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.sky.GloamSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the client's environment attributes (sky, fog, light, stars, ambient sound and particles) Gloam's layers, on
 * top of the dimension's, the biomes', the timelines' and the weather's. Everything that reads those attributes then
 * follows Gloam: vanilla's sky and fog, VOID LOD's haze, and the uniforms shader packs are given.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
	private void gloam$layers(EnvironmentAttributeSystem.Builder builder, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		GloamSky.addLayers(builder, (ClientLevel) (Object) this);
	}
}
