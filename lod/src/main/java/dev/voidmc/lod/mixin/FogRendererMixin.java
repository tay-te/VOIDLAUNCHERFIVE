package dev.voidmc.lod.mixin;

import dev.voidmc.lod.VoidLod;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves the fog from the edge of the chunks to the edge of the LOD. Vanilla fades the world to the fog
 * colour at the render distance, which with the LOD behind it would draw a wall of haze at the seam; the
 * render-distance fog goes to the LOD's own edge instead, and clear-air haze is stretched to match, so
 * the land fades into the sky over kilometres the way distance actually looks. Water, lava, powder snow
 * and blindness keep their own short fog: those leave the environmental fog well short of the render
 * distance, and are left alone.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void voidLod$farFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
		CallbackInfoReturnable<FogData> cir) {
		VoidLod lod = VoidLod.get();
		if (lod == null || !lod.active()) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float far = (float) lod.radius;
		float vanilla = renderDistanceInChunks * 16;
		fog.renderDistanceStart = far * 0.9F;
		fog.renderDistanceEnd = far;
		if (fog.environmentalEnd >= vanilla) {
			// clear air: stretch the haze in proportion to how much further the view now reaches
			float stretch = Math.max(1, far / Math.max(vanilla, 1));
			fog.environmentalStart = Math.max(fog.environmentalStart, vanilla * 0.5F) * Math.min(stretch, 4);
			fog.environmentalEnd = Math.max(fog.environmentalEnd * stretch * 0.5F, far * 1.15F);
		}
	}
}
