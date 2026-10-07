package dev.voidmc.expanse.api;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What the land is like at a place, for mods that dress it (VOID Gloam's mist, haze and weather): which country it
 * lies in, how wet and warm, how low it sits, how far from water and the sea, how high. A pure function of the world
 * seed and the coordinates (no chunk is read or made), cheap enough to ask every tick at the camera and on a coarse
 * grid over the far view.
 *
 * <p>The countries ({@code TerrainModel}'s map of them): {@code none}, {@code heartland}, {@code rock_city},
 * {@code volcanic_hills}, {@code lake_district}, {@code gorges}, {@code old_forest}.
 */
public final class ExpanseCountry {
	private static final String[] NAMES = {"none", "heartland", "rock_city", "volcanic_hills", "lake_district", "gorges", "old_forest"};

	private ExpanseCountry() {
	}

	/**
	 * The land at one column.
	 *
	 * @param country       the country's name (see above)
	 * @param strength      1 well inside the country, falling to 0 at a border with another kind
	 * @param humidity      0 (desert) to 1 (rainforest)
	 * @param temperature   the biome source's temperature here, cooler with height (about -1 to 1)
	 * @param lowness       how far below the mean ground about 100 blocks round it the column lies (blocks; positive
	 *                      in hollows and valleys, negative on rises)
	 * @param waterDistance blocks to the nearest river bank or lake shore (0 on the water; 1000 when there is none near)
	 * @param coastDistance blocks to the sea (positive inland, negative out at sea)
	 * @param altitude      the ground's height (y)
	 * @param haze          a hint at the air's character: {@code humid_lowland}, {@code misty_lakes},
	 *                      {@code forest_damp}, {@code clear_alpine}, {@code dusty} or {@code temperate}
	 */
	public record Here(String country, float strength, float humidity, float temperature, float lowness, float waterDistance,
		float coastDistance, float altitude, String haze) {
	}

	/** Asks about one level's land. */
	@FunctionalInterface
	public interface Sampler {
		Here at(int x, int z);
	}

	/**
	 * A sampler for the level, or null if it is not on the Expanse's Earth terrain. In singleplayer a client-side mod
	 * can pass the integrated server's overworld.
	 */
	public static @Nullable Sampler sampler(ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return null;
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		return (x, z) -> at(model, x, z);
	}

	private static Here at(TerrainModel model, int x, int z) {
		Column c = model.sample(x, z);
		int country = c.country;
		float strength = c.countryStrength;
		float humidity = Mth.clamp((c.humidity + 1) / 2, 0, 1);
		float temperature = c.temperature;
		float height = c.height;
		float coast = c.coast;
		float water = c.lake > -900 && c.lakeEdge > 0 ? 0 : c.lake > -900 ? Math.max(0, -c.lakeEdge)
			: c.nearRiver() ? Math.max(0, c.riverDist - c.riverHalfWidth) : 1000;
		float around = 0;
		for (int[] o : new int[][]{{100, 0}, {-100, 0}, {0, 100}, {0, -100}, {70, 70}, {-70, -70}, {70, -70}, {-70, 70}}) {
			around += model.sample(x + o[0], z + o[1]).height;
		}
		float lowness = around / 8 - height;
		String haze;
		if (temperature < -0.2F && height > 150) {
			haze = "clear_alpine";
		} else if (humidity < 0.35F && temperature > 0.2F) {
			haze = "dusty";
		} else if (country == 4) {
			haze = "misty_lakes";
		} else if (country == 6 || country == 2) {
			haze = "forest_damp";
		} else if (humidity > 0.6F && height < 100) {
			haze = "humid_lowland";
		} else {
			haze = "temperate";
		}
		return new Here(NAMES[Math.max(0, Math.min(NAMES.length - 1, country))], strength, humidity, temperature, lowness, water, coast, height, haze);
	}
}
