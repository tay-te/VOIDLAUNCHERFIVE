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

	/**
	 * The land at one column in Expanse's own terms, for the shader-pack bridge's land tiles.
	 *
	 * @param humidity      0 (desert) .. 1 (rainforest)
	 * @param temperature   about -1 .. 1, cooler with height
	 * @param lowness       blocks below the mean ground about 100 blocks round (negative on rises)
	 * @param waterDistance blocks to the nearest river bank or lake shore (0 on the water, 1000 with none near)
	 * @param coastDistance blocks to the sea (negative out at sea)
	 * @param altitude      the ground's height (y)
	 * @param haze          the haze hint, a key of {@code Haze.HINTS}
	 */
	public record Raw(float humidity, float temperature, float lowness, float waterDistance, float coastDistance, float altitude, String haze) {
	}

	/** {@link Sampler}'s raw form; safe from any thread. */
	@FunctionalInterface
	public interface RawSampler {
		Raw at(int x, int z);

		/**
		 * The land averaged over a cell of half a kilometre and more round (x, z), for the bridge's far tiles and the
		 * weather tiles' land lattices: Expanse's far-view model (its plates on a 512-block grid, a few microseconds a
		 * call), which never builds a full plate, so sampling a 300 km reach neither takes minutes nor evicts the plates
		 * the chunk generator is using. No rivers, lakes or local lowness: {@code waterDistance} is 1000 and
		 * {@code lowness} 0 on land. Without the far model, {@link #at}.
		 */
		default Raw far(int x, int z) {
			return this.at(x, z);
		}
	}

	private static IntegratedServer server;
	private static Sampler sampler;
	private static RawSampler raw;
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
			raw = null;
			return null;
		}
		if (s != server) {
			server = s;
			try {
				raw = Api.sampler(s.overworld());
				RawSampler r = raw;
				sampler = r == null ? null : (x, z) -> {
					Raw h = r.at(x, z);
					return new Land(h.humidity(), lowness(h.lowness()), water(h.waterDistance(), h.coastDistance()), h.altitude(), h.haze());
				};
			} catch (LinkageError | RuntimeException e) {
				broken = true;
				sampler = null;
				raw = null;
				Gloam.LOGGER.warn("VOID Gloam: Expanse's country API is not what Gloam expects; the air reads the land without it", e);
			}
		}
		return sampler;
	}

	/** The raw sampler for the land {@code level} shows, or null, as {@link #sampler}. Call on the client thread. */
	public static RawSampler raw(ClientLevel level) {
		return sampler(level) == null ? null : raw;
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
		static RawSampler sampler(net.minecraft.server.level.ServerLevel level) {
			dev.voidmc.expanse.api.ExpanseCountry.Sampler s = level == null ? null : dev.voidmc.expanse.api.ExpanseCountry.sampler(level);
			if (s == null) {
				return null;
			}
			RawSampler far = far(level);
			return new RawSampler() {
				@Override
				public Raw at(int x, int z) {
					dev.voidmc.expanse.api.ExpanseCountry.Here h = s.at(x, z);
					return new Raw(h.humidity(), h.temperature(), h.lowness(), h.waterDistance(), h.coastDistance(), h.altitude(), h.haze());
				}

				@Override
				public Raw far(int x, int z) {
					return far == null ? this.at(x, z) : far.at(x, z);
				}
			};
		}

		/**
		 * Expanse's far-view column ({@code TerrainModel.sampleFar}) in {@link Raw}'s terms, or null if that part of
		 * Expanse is not what Gloam expects. Expanse's API has no far call yet, so this reaches into its terrain model
		 * (public, but not API); the haze hint follows {@code ExpanseCountry}'s rule.
		 */
		private static RawSampler far(net.minecraft.server.level.ServerLevel level) {
			try {
				if (!(level.getChunkSource().getGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
					return null;
				}
				dev.voidmc.expanse.world.terrain.TerrainModel model = earth.model(level.getChunkSource().randomState());
				ThreadLocal<dev.voidmc.expanse.world.terrain.TerrainModel.FarColumn> columns =
					ThreadLocal.withInitial(dev.voidmc.expanse.world.terrain.TerrainModel.FarColumn::new);
				RawSampler far = (x, z) -> {
					dev.voidmc.expanse.world.terrain.TerrainModel.FarColumn c = columns.get();
					model.sampleFar(x, z, c);
					float humidity = Math.clamp((c.humidity + 1) / 2, 0F, 1F);
					float coast = c.ocean ? Math.min(c.coast, -1) : c.coast;
					String haze;
					if (c.temperature < -0.2F && c.height > 150) {
						haze = "clear_alpine";
					} else if (humidity < 0.35F && c.temperature > 0.2F) {
						haze = "dusty";
					} else if (c.country == 4) {
						haze = "misty_lakes";
					} else if (c.country == 6 || c.country == 2) {
						haze = "forest_damp";
					} else if (humidity > 0.6F && c.height < 100) {
						haze = "humid_lowland";
					} else {
						haze = "temperate";
					}
					return new Raw(humidity, c.temperature, 0, 1000, coast, c.height, haze);
				};
				// once here, so a changed far model fails now (and is caught) rather than on the bridge's worker
				far.at(0, 0);
				return far;
			} catch (LinkageError | RuntimeException e) {
				Gloam.LOGGER.warn("VOID Gloam: Expanse's far-view model is not what Gloam expects; the far tiles sample its countries instead", e);
				return null;
			}
		}
	}
}
