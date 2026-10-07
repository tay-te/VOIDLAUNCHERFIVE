package dev.voidmc.gloam.season;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.mixin.BiomeTemperatureInvoker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/**
 * The snowline through the year, for how the weather looks. Vanilla snows where a biome's temperature, falling slowly
 * with height above sea level + 17, is under 0.15; that is a fixed line. Here the temperature the look is decided by
 * drops with the season's cold, and falls faster with height in winter, so in deep winter it snows on the temperate
 * hills and high ground while the valleys still get rain (a plains-warm land snows from some 40 blocks above its
 * valleys, a forest's from 20), in late autumn only on the high hills, and in summer the line sits higher than
 * vanilla's. Hot lands move less. Only the client's look follows (the falling snow, the splashes, the sound); whether
 * snow lies and water freezes is the server's, and stays vanilla's.
 */
public final class Snowline {
	/** How much colder the deep winter is than vanilla's biome temperature, at the valley floor. */
	private static final float WINTER = 0.45F;
	/** How much warmer high summer is. */
	private static final float SUMMER = 0.12F;
	/** How much faster than vanilla's (0.05 per 40 blocks) the temperature falls with height in deep winter, per block. */
	private static final float WINTER_LAPSE = 0.0035F;

	private Snowline() {
	}

	public static boolean active() {
		GloamSettings s = GloamSettings.get();
		return s.enabled && s.seasonSnow && Seasons.colours().on();
	}

	/** Rain or snow at {@code pos} this time of year, where vanilla says {@code vanilla} (rain or snow, not none). */
	public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos, Biome.Precipitation vanilla) {
		Biome biome = level.getBiome(pos).value();
		int sea = level.getSeaLevel();
		float t = ((BiomeTemperatureInvoker) (Object) biome).gloam$heightAdjustedTemperature(pos, sea);
		return temperature(t, biome.getBaseTemperature(), pos.getY(), sea, Seasons.colours()) < 0.15F ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	/**
	 * The temperature the weather's look is decided by: vanilla's at the place ({@code vanilla}), moved by the season,
	 * for a biome of {@code base} temperature at height {@code y}.
	 */
	static float temperature(float vanilla, float base, int y, int seaLevel, SeasonColours colours) {
		float hot = Noise.smoothstep(0.95F, 1.6F, base);
		float cold = colours.cold() * (1 - 0.7F * hot);
		float summer = Math.max(0, colours.warmth()) * (1 - hot);
		float above = Math.max(0, y - (seaLevel + 17));
		return vanilla - WINTER * cold + SUMMER * summer - WINTER_LAPSE * cold * above;
	}
}
