package dev.voidmc.gloam.sky;

import dev.voidmc.gloam.air.Noise;

/**
 * Dawn and dusk: when the low sun gilds the land, when the afterglow and the blue hour follow it, and what colour the
 * band of sky round the sun is meanwhile. Vanilla keeps the band's strength (its alpha, which follows the sun) and
 * Gloam gives it the colours of a real evening: gold while the sun is still up, orange as it touches the horizon,
 * red and rose just after it sets, a dusky violet as the afterglow goes. Mornings run the same way backwards, a
 * little paler and pinker. Pure functions of the clock, so they are unit-tested and cost nothing.
 *
 * <p>Times are the overworld clock's ticks within the day ({@code 0} sunrise, {@code 6000} noon, {@code 12000}
 * sunset, {@code 18000} midnight). Vanilla's sunset band is strongest at about 12800, just after the sun has gone.
 */
public final class Twilight {
	/** The colour of the band round the sun at the dusk times in {@link #KEYS}: gold, orange, red, rose, violet. */
	private static final float[][] COLOURS = {
		{1.00F, 0.80F, 0.46F},
		{1.00F, 0.60F, 0.28F},
		{0.97F, 0.43F, 0.21F},
		{0.80F, 0.31F, 0.28F},
		{0.44F, 0.24F, 0.34F},
	};
	private static final float[] KEYS = {11500, 12300, 12850, 13400, 14100};
	/** What a morning leans to: the cooler, cleaner air at dawn gives peach and pink more than fire. */
	private static final float[] DAWN = {1.00F, 0.72F, 0.68F};

	private Twilight() {
	}

	/**
	 * The time of day folded onto the evening: a dusk time stays as it is, a dawn time becomes the dusk time with the
	 * sun as high (vanilla's own sunrise and sunset are mirror images: 71 matches 11929, 23757 matches 12243).
	 */
	public static float duskTime(long clock) {
		int t = (int) Math.floorMod(clock, 24000L);
		if (t >= 9000 && t < 18000) {
			return t;
		}
		return t >= 18000 ? 36000 - t : 12000 - t;
	}

	/** Whether the clock is in the morning half of the day (midnight to noon). */
	public static boolean morning(long clock) {
		int t = (int) Math.floorMod(clock, 24000L);
		return t >= 18000 || t < 6000;
	}

	/** How deep in the night it is, 0 day .. 1 night, on vanilla's own ramps for the light (11270-13140, 22860-730). */
	public static float night(long clock) {
		float u = duskTime(clock);
		return Noise.smoothstep(11270, 13140, u);
	}

	/**
	 * How far the day's last light has gone, 0..1: later than {@link #night}, which follows vanilla's light, so dusk
	 * keeps its glow on the land and the dark of a moonless night closes in only after the blue hour.
	 */
	public static float dark(long clock) {
		return Noise.smoothstep(12700, 13700, duskTime(clock));
	}

	/** The golden hour: the sun low but up, the light warm and long, 0..1. */
	public static float golden(long clock) {
		float u = duskTime(clock);
		return Noise.smoothstep(10000, 11800, u) * (1 - Noise.smoothstep(12500, 13050, u));
	}

	/** The blue hour: the sun just gone, the sky a deep clear blue overhead and the land cool, 0..1. */
	public static float blue(long clock) {
		float u = duskTime(clock);
		return Noise.smoothstep(12550, 13150, u) * (1 - Noise.smoothstep(13500, 14300, u));
	}

	/**
	 * The colour of the band of sky round the sun at {@code clock}, as RGB in 0..1. {@code vivid} (about 0.7..1.4)
	 * says how fiery this particular evening is; every day has its own.
	 */
	public static float[] colour(long clock, float vivid) {
		float u = duskTime(clock);
		float[] c;
		if (u <= KEYS[0]) {
			c = COLOURS[0].clone();
		} else if (u >= KEYS[KEYS.length - 1]) {
			c = COLOURS[COLOURS.length - 1].clone();
		} else {
			int i = 0;
			while (u > KEYS[i + 1]) {
				i++;
			}
			float t = (u - KEYS[i]) / (KEYS[i + 1] - KEYS[i]);
			c = new float[3];
			for (int k = 0; k < 3; k++) {
				c[k] = COLOURS[i][k] + (COLOURS[i + 1][k] - COLOURS[i][k]) * t;
			}
		}
		if (morning(clock)) {
			for (int k = 0; k < 3; k++) {
				c[k] += (DAWN[k] - c[k]) * 0.25F;
			}
		}
		float grey = 0.2126F * c[0] + 0.7152F * c[1] + 0.0722F * c[2];
		for (int k = 0; k < 3; k++) {
			c[k] = Math.clamp(grey + (c[k] - grey) * vivid, 0.0F, 1.0F);
		}
		return c;
	}

	/**
	 * How fiery the sunrise or sunset of the day {@code clock} falls in is, about 0.75..1.25: some evenings burn, some
	 * fade out pale, the same for everyone in the world ({@code seed}). A broken sky, half cloud, makes the best ones,
	 * the clouds lit from below; a full overcast hides it ({@code cloud} 0..1, muted elsewhere).
	 */
	public static float vivid(long seed, long clock, float cloud) {
		double day = Math.floorDiv(clock + 6000, 12000L) * 0.5;
		float v = 0.75F + 0.5F * Noise.value(seed + 31, day * 1.7);
		float broken = Noise.smoothstep(0.1F, 0.4F, cloud) * (1 - Noise.smoothstep(0.55F, 0.8F, cloud));
		return v * (1 + 0.2F * broken);
	}
}
