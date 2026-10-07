package dev.voidmc.gloam.sky;

import dev.voidmc.gloam.air.Noise;

/**
 * How much light a night sky gives: the moon by its phase, the stars, and how much of both the clouds let through.
 * Vanilla lights every night the same; with Gloam a clear full moon still lights the fields (silver-blue, enough to
 * walk by), a clear moonless night leaves starlight, and a cloudy night without a moon is nearly black, so a torch or
 * a lit window matters. Pure, unit-tested.
 */
public final class Night {
	/** What the night sky gives under a full overcast with no moon: next to nothing. */
	public static final float FLOOR = 0.03F;
	/** What the stars give on a clear night, as a share of a clear full moon's light. */
	public static final float STARS = 0.16F;
	/** How much moonlight the thickest cloud still lets through. */
	public static final float THROUGH_CLOUD = 0.12F;

	private Night() {
	}

	/**
	 * The night's light, as a share of vanilla's night (a clear full moon), {@link #FLOOR}..1.
	 *
	 * @param moon  how bright the moon is by its phase, 0 new .. 1 full ({@code DimensionType.MOON_BRIGHTNESS_PER_PHASE})
	 * @param cloud how much of the sky is cloud, 0..1
	 */
	public static float light(float moon, float cloud) {
		float clear = clear(cloud);
		float moonlight = moon * (THROUGH_CLOUD + (1 - THROUGH_CLOUD) * clear);
		return Math.min(1.0F, FLOOR + STARS * clear + (1 - FLOOR - STARS) * moonlight);
	}

	/**
	 * How much to scale vanilla's night sky light by: 1 where Gloam leaves the night alone, down to {@code light} deep
	 * in the night with the setting at full ({@code night} 0..1 how far into the night, {@code strength} the
	 * {@code darkNights} setting).
	 */
	public static float scale(float light, float night, float strength) {
		return 1 - (1 - light) * night * strength;
	}

	/**
	 * How bright the stars may shine under {@code cloud}, with the moon at {@code moon}: hidden behind cloud, and more
	 * of them out on a clear moonless night than under a bright moon that washes the faint ones away.
	 */
	public static float stars(float moon, float cloud) {
		float clear = clear(cloud);
		return clear * clear * (1.5F - 0.5F * moon);
	}

	/**
	 * How much open sky there is between the clouds, 1..0, for {@code cloud} 0..1: a scattered sky is still mostly
	 * open between its clouds, and only past half cover does it close in fast.
	 */
	public static float clear(float cloud) {
		return 1 - Noise.smoothstep(0.25F, 1.0F, cloud);
	}
}
