package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Noise;

/**
 * The snowline as a height, for {@code void_seasonState.w} and F1's snow flag: the y above which Gloam's season makes
 * it snow for a land of a given biome temperature. The same rule as {@code season.Snowline}'s (vanilla's 0.15 line, its
 * cooling of 0.05 per 40 blocks above sea level + 17, moved by the season's cold and warmth, falling faster with height
 * in winter, hot lands moving less), solved for the height. Pure, unit-tested against {@code Snowline}'s constants.
 */
public final class SeasonSnow {
	/** {@code Snowline}'s constants (kept in step by {@code SeasonSnowTest}). */
	static final float WINTER = 0.45F, SUMMER = 0.12F, WINTER_LAPSE = 0.0035F;
	/** Vanilla's cooling with height, per block above sea level + 17. */
	static final float LAPSE = 0.05F / 40.0F;
	/** Where it snows: under this temperature. */
	static final float SNOWS = 0.15F;
	/** What {@link #height} answers for a land where it never snows, and where it always does. */
	public static final float NEVER = 1024, ALWAYS = -1024;

	private SeasonSnow() {
	}

	/**
	 * The height (y) above which it snows.
	 *
	 * @param base     the biome's base temperature (vanilla's scale)
	 * @param seaLevel the level's sea level
	 * @param cold     how deep into the cold the year is, 0..1 ({@code SeasonColours.cold}; 0 with the seasons off)
	 * @param summer   the season's warmth above 0, 0..1 ({@code max(0, SeasonColours.warmth)}; 0 with the seasons off)
	 */
	public static float height(float base, int seaLevel, float cold, float summer) {
		float hot = Noise.smoothstep(0.95F, 1.6F, base);
		float c = cold * (1 - 0.7F * hot);
		float s = summer * (1 - hot);
		float atBase = base - WINTER * c + SUMMER * s;
		float from = seaLevel + 17;
		if (atBase < SNOWS) {
			// it snows from below the line vanilla starts cooling at: down to the valley floors
			return ALWAYS;
		}
		float lapse = LAPSE + WINTER_LAPSE * c;
		return Math.min(NEVER, from + (atBase - SNOWS) / lapse);
	}
}
