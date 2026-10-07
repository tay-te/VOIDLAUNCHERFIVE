package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.far.FarAir;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stretches the clouds' fog out to Gloam's clouds' reach: vanilla's cloud shader thins clouds linearly to nothing at the
 * cloud distance, so the deck that now reaches VOID LOD's horizon fades into the sky over that whole distance, as the
 * far land hazes, instead of stopping at a couple of kilometres. In open air only, and in proportion, so whatever the
 * environment attributes do to the clouds' fog (mist, rain) still counts.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererCloudMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void gloam$cloudFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
		CallbackInfoReturnable<FogData> cir) {
		double reach = FarAir.cloudReach();
		if (reach <= 0 || camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float range = Minecraft.getInstance().options.cloudRange().get() * 16.0F;
		if (range > 0 && reach > range) {
			fog.cloudEnd *= (float) (reach / range);
		}
	}
}
