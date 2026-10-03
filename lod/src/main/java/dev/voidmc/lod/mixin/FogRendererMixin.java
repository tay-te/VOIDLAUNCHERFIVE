package dev.voidmc.lod.mixin;

import dev.voidmc.lod.VoidLod;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the fog at the edge of the chunks with the air's own haze. Vanilla fades the world to the fog
 * colour at the render distance and hazes clear air linearly out to 1024 blocks; with the LOD behind the
 * chunks, the first would draw a wall at the seam and the second would hide everything past a kilometre.
 *
 * <p>Instead the LOD gets haze that thins with height ({@code lod.vsh}): thick in the valleys, thin over the
 * peaks, reaching {@code void.lod.haze} blocks at sea level. The chunks get the same haze in vanilla's linear
 * form, matched to it at the camera's height, so the seam between them disappears; the render-distance fog
 * moves out to the LOD's edge, where the land finally melts into the sky. Rain and misty biomes thicken the
 * haze. Water, lava, powder snow, blindness and boss fog keep their own short fog, which hides the LOD.
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
		float chunks = renderDistanceInChunks * 16;
		Entity entity = camera.entity();
		boolean blind = entity instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
		boolean open = camera.getFluidInCamera() == FogType.NONE && !blind && !Minecraft.getInstance().gui.hud.getBossOverlay().shouldCreateWorldFog();
		if (open) {
			// Open air. Vanilla's rain pulls the fog's start in by up to 160 blocks and its end by 256; a misty
			// biome sets its own, shorter end. Both thicken the haze, the biome's by the square root of how much
			// shorter it asks for, so a cloud forest is murky and still has a horizon.
			float rain = Mth.clamp(-fog.environmentalStart / 160.0F, 0, 1);
			float visibility = Mth.clamp((fog.environmentalEnd + 256 * rain) / 1024.0F, 0, 1);
			float reach = (float) lod.hazeReach * Math.max(0.25F, (float) Math.sqrt(visibility)) * (1 - 0.7F * rain);
			float base = level.getSeaLevel();
			float above = (float) camera.position().y - base;
			float atCamera = reach * (float) Math.exp(Mth.clamp(above / VoidLod.HAZE_SCALE_HEIGHT, -4, 4));
			fog.environmentalEnd = fog.environmentalStart + atCamera;
			lod.setHaze(reach, base, -fog.environmentalStart, false);
		} else {
			lod.setHaze(0, 0, 0, fog.environmentalEnd < chunks);
		}
		fog.renderDistanceStart = far * 0.8F;
		fog.renderDistanceEnd = far;
	}
}
