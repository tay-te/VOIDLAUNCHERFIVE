package dev.voidmc.lod.mixin;

import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.source.WorldgenSource;
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
 * form, as thick as the LOD's where the two meet, so the seam between them disappears; the render-distance fog
 * moves out to the LOD's edge, where the land finally melts into the sky. Misty biomes thicken the haze; rain
 * hangs a curtain over it that does not thin with height, as dense as vanilla's rain fog, so in a downpour the
 * far land goes as it does in vanilla, even from a peak. Water, lava, powder snow, blindness and boss fog keep
 * their own short fog, which hides the LOD.
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
			// biome sets its own, shorter end. A misty biome thickens the haze by the square root of how much
			// shorter it asks for, so a cloud forest is murky and still has a horizon; rain is its own curtain.
			float rain = Mth.clamp(-fog.environmentalStart / VoidLod.RAIN_OFFSET, 0, 1);
			float visibility = Mth.clamp((fog.environmentalEnd + 256 * rain) / 1024.0F, 0, 1);
			float reach = (float) lod.hazeReach * Math.max(0.25F, (float) Math.sqrt(visibility));
			float base = level.getSeaLevel();
			if (lod.engine().source() instanceof WorldgenSource source && source.floating()) {
				// Over the void (the End) there is no sea for the haze to settle toward: it is as thick at the
				// camera's height as at sea level elsewhere, and thicker, so far islands sink into the dark
				// before the LOD's edge instead of standing against the sky in a line.
				base = (float) camera.position().y;
				reach *= 0.6F;
			}
			float above = (float) camera.position().y - base;
			float atCamera = reach * (float) Math.exp(Mth.clamp(above / VoidLod.HAZE_SCALE_HEIGHT, -4, 4));
			// the chunks' linear fog, as thick as the haze and the rain together where they meet the LOD: the
			// same optical depth as lod.vsh's at the seam, so a downpour leaves no step there either
			float seam = Math.max(16, (float) VoidLod.clipRadius(Minecraft.getInstance()));
			float depth = seam / atCamera + (seam + VoidLod.RAIN_OFFSET * rain) * rain / VoidLod.RAIN_REACH;
			float hazed = Math.max(1e-4F, 1 - (float) Math.exp(-depth));
			fog.environmentalEnd = fog.environmentalStart + Math.max(1, seam - fog.environmentalStart) / hazed;
			lod.setHaze(reach, base, rain, false);
		} else {
			lod.setHaze(0, 0, 0, fog.environmentalEnd < chunks);
		}
		fog.renderDistanceStart = far * 0.8F;
		fog.renderDistanceEnd = far;
	}
}
