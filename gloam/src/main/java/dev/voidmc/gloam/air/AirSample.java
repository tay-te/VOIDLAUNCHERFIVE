package dev.voidmc.gloam.air;

/**
 * The air where the camera is, this tick: what every part of Gloam reads (the sky's layers, the motes, the soundscape,
 * the far weather). Smoothed by {@link Atmosphere} so nothing jumps when the camera crosses a biome edge.
 *
 * @param year       0..1 through the year, 0 the first day of spring
 * @param season     the season {@code year} falls in
 * @param warmth     the season's warmth, -1 midwinter .. 1 midsummer
 * @param hour       the hour of the day, 0..24
 * @param wind       the wind now
 * @param front      how stormy the sky is overhead, 0..1
 * @param cloudCover how much of the sky is cloud, 0..1
 * @param rain       how hard it rains here, 0..1 (vanilla's rain, made regional by the fronts)
 * @param storm      how much of a storm is overhead, 0..1
 * @param mist       how thick the ground mist is, 0..1
 * @param mistTop    the height the mist reaches up to, in world y
 * @param wetness    how wet the ground still is from recent rain, 0..1
 * @param climate    what the land here gives the air
 * @param indoors    0 under open sky .. 1 well inside a building or cave, from how much sky the camera sees
 */
public record AirSample(float year, Season season, float warmth, float hour, Wind wind, float front, float cloudCover, float rain, float storm,
	float mist, float mistTop, float wetness, Climate climate, float indoors) {
	public static final AirSample CLEAR = new AirSample(Air.YEAR_START, Season.SPRING, 0, 12, Wind.CALM, 0, 0, 0, 0, 0, 0, 0, Climate.TEMPERATE, 0);

	/** How dark the sky is between the stars: 0 a clear sky .. 1 a full overcast. */
	public float overcast() {
		return Math.max(this.cloudCover, this.rain);
	}

	/** Whether it is night: the sun well below the horizon. */
	public boolean night() {
		return this.hour < 5.0F || this.hour > 19.0F;
	}
}
