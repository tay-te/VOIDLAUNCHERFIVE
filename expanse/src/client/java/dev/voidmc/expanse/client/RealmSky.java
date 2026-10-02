package dev.voidmc.expanse.client;

import dev.voidmc.expanse.Expanse;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The sky of the realms underground ({@code #expanse:realms}). The overworld's day timeline is applied
 * after the biomes and overrides the sun's angle and the sunset glow, and darkens fog and sky at night,
 * so a biome alone cannot keep the sun out of a cavern or its fog the same at midnight as at noon. In a
 * realm the sun, moon, stars and sunset are hidden and the realm's own fog and sky colours hold
 * (client/mixin: SkyRendererMixin, AtmosphericFogEnvironmentMixin). Kept out of the mixin package, which
 * may hold only mixins.
 */
public final class RealmSky {
	public static final TagKey<Biome> REALMS = TagKey.create(Registries.BIOME, Expanse.id("realms"));

	private RealmSky() {
	}

	/** The realm biome the camera is in, or null. */
	public static @Nullable Holder<Biome> realm(ClientLevel level, Camera camera) {
		Holder<Biome> biome = level.getBiome(camera.blockPosition());
		return biome.is(REALMS) ? biome : null;
	}
}
