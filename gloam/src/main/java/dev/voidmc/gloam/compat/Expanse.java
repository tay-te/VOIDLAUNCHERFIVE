package dev.voidmc.gloam.compat;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.air.Noise;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.Level;

/**
 * What Expanse knows about the land at a place ({@code ExpanseCountry}): how wet it is, how low it lies, how near water,
 * how high, and the air's character there, in Gloam's own terms. Expanse works it out from the world seed without
 * reading chunks, so it is there for the far land as well as the near, and it sees a valley as a valley before its
 * chunks have loaded. Expanse's classes are only touched when it is loaded.
 *
 * <p>The data is the server's: in singleplayer Gloam asks the integrated server's overworld. On a dedicated server's
 * client, or a world not on Expanse's Earth terrain, there is none ({@link #sampler} is null) and Gloam reads the land
 * from the heightmaps and the biomes as before.
 */
public final class Expanse {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("expanse");

	/**
	 * The land at one column, in the air's terms.
	 *
	 * @param downfall the land's wetness, 0 (desert) .. 1 (rainforest), as a biome's downfall
	 * @param lowness  0 on level ground or a rise .. 1 deep in a hollow or valley, as {@code Climate.lowness}
	 * @param water    how near open water (rivers, lakes, the sea), 0 far .. 1 on it, as {@code Climate.water}
	 * @param altitude the ground's height (y)
	 * @param haze     the air's character, a key of {@code Haze.HINTS}
	 */
	public record Land(float downfall, float lowness, float water, float altitude, String haze) {
	}

	/** Asks about the land of one level; safe from any thread, a few microseconds a call. */
	@FunctionalInterface
	public interface Sampler {
		Land at(int x, int z);
	}

	private static IntegratedServer server;
	private static Sampler sampler;
	private static boolean broken;

	private Expanse() {
	}

	/**
	 * The sampler for the land {@code level} shows, or null: only the overworld of a singleplayer world on Expanse's
	 * Earth terrain has one. Cheap after the first call for a world.
	 */
	public static Sampler sampler(ClientLevel level) {
		if (!LOADED || broken || level == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
		if (s == null) {
			server = null;
			sampler = null;
			return null;
		}
		if (s != server) {
			server = s;
			try {
				sampler = Api.sampler(s.overworld());
			} catch (LinkageError | RuntimeException e) {
				broken = true;
				sampler = null;
				Gloam.LOGGER.warn("VOID Gloam: Expanse's country API is not what Gloam expects; the air reads the land without it", e);
			}
		}
		return sampler;
	}

	/** Expanse's lowness (blocks below the mean ground about 100 blocks round) as Gloam's 0..1, like the heightmap read's. */
	public static float lowness(float blocks) {
		return Noise.smoothstep(0.0F, 24.0F, blocks);
	}

	/** How near open water a place is, 0..1, from Expanse's distances to a river or lake and to the sea. */
	public static float water(float waterDistance, float coastDistance) {
		float inland = 1 - Noise.smoothstep(0.0F, 96.0F, waterDistance);
		float sea = 1 - Noise.smoothstep(0.0F, 96.0F, coastDistance);
		return Math.max(inland, sea);
	}

	/** Expanse's side, in a class of its own, so the JVM only resolves it once Expanse is known to be there. */
	private static final class Api {
		static Sampler sampler(net.minecraft.server.level.ServerLevel level) {
			dev.voidmc.expanse.api.ExpanseCountry.Sampler s = level == null ? null : dev.voidmc.expanse.api.ExpanseCountry.sampler(level);
			if (s == null) {
				return null;
			}
			return (x, z) -> {
				dev.voidmc.expanse.api.ExpanseCountry.Here h = s.at(x, z);
				return new Land(h.humidity(), lowness(h.lowness()), water(h.waterDistance(), h.coastDistance()), h.altitude(), h.haze());
			};
		}
	}
}
