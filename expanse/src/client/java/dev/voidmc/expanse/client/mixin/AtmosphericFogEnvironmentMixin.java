package dev.voidmc.expanse.client.mixin;

import dev.voidmc.expanse.client.RealmSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In a realm the fog is the realm's own colour, with no sunset glow and no night (see {@link RealmSky}). */
@Mixin(AtmosphericFogEnvironment.class)
abstract class AtmosphericFogEnvironmentMixin {
	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void expanse$realmFog(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Vector3fc> cir) {
		Holder<Biome> realm = RealmSky.realm(level, camera);
		if (realm != null) {
			cir.setReturnValue(realm.value().getAttributes().applyModifier(EnvironmentAttributes.FOG_COLOR, cir.getReturnValue()));
		}
	}
}
