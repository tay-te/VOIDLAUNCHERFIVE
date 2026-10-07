package dev.voidmc.gloam.sound;

import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Noise;

/**
 * Chooses how loud each bed should be from the air at the camera and the land around it. Pure: no world, no sound
 * engine, so it is unit-tested ({@code MixTest}). Every bed is a product of plain factors, each 0..1: the hour, the
 * time of year, the biome's warmth, the weather, the habitat around (trees, grass, water) and how much of the outdoors
 * reaches the ear (walls and roofs muffle it, the ground above a cave shuts it out).
 */
public final class Mix {
	/*
	 * The year in eighths (early spring, late spring, early summer, late summer, early autumn, late autumn, early winter,
	 * late winter), read with linear steps between: how much of each kind of life is about at that time of year.
	 */
	private static final float[] CHORUS_YEAR = {0.7F, 1.0F, 0.9F, 0.6F, 0.3F, 0.15F, 0.08F, 0.25F};
	private static final float[] BIRDS_YEAR = {0.8F, 0.9F, 0.8F, 0.65F, 0.5F, 0.35F, 0.25F, 0.4F};
	private static final float[] CRICKETS_YEAR = {0, 0.15F, 0.7F, 1.0F, 0.8F, 0.25F, 0, 0};
	private static final float[] FROGS_YEAR = {0.7F, 1.0F, 0.7F, 0.4F, 0.1F, 0, 0, 0.1F};
	private static final float[] CICADAS_YEAR = {0, 0, 0.6F, 1.0F, 0.4F, 0, 0, 0};
	/** How full the trees' leaves are, for the rustle (the winter woods are thinner and whistle more). */
	private static final float[] LEAVES_YEAR = {0.75F, 1.0F, 1.0F, 1.0F, 1.0F, 0.8F, 0.5F, 0.5F};

	private Mix() {
	}

	/**
	 * Fills {@code out} (indexed by {@link Bed#ordinal()}) with each bed's volume, 0..1 times its {@link Bed#gain()},
	 * before the {@code sound} setting and Minecraft's ambient slider.
	 */
	public static void volumes(AirSample air, Surroundings land, float[] out) {
		float hour = air.hour(), year = air.year(), warmth = air.warmth();
		float temperature = air.climate().temperature();
		float rain = air.rain(), storm = air.storm();
		float wind = air.wind().now(), gust = air.wind().gust();

		// how much of the outdoors reaches the ear: walls take most of it, the ground above a cave all of it
		float outside = (1 - 0.85F * air.indoors()) * (1 - land.buried());
		float sheltered = outside * outside; // the wind is stopped harder by walls than birdsong is
		float woods = Math.max(land.canopy(), land.trees() * 0.85F);
		// a bare summit: high, standing above the land around, out of the trees
		float peak = Noise.smoothstep(45, 110, land.height()) * (0.4F + 0.6F * land.exposure()) * (1 - woods);
		float cold = Noise.smoothstep(-0.2F, -0.8F, warmth);
		float lowland = 1 - Noise.smoothstep(50, 110, land.height());

		// ---- wind: over the grass in the open, through the leaves in a wood, hollow on the heights and in winter
		float breeze = Noise.smoothstep(0.5F, 11.0F, wind);
		float open = (1 - woods) * (1 - peak);
		set(out, Bed.WIND_OPEN, (0.15F + 0.85F * breeze) * open * sheltered);
		set(out, Bed.WIND_LEAVES, (0.1F + 0.9F * breeze) * (0.75F + 0.35F * gust) * woods * (1 - peak) * yearly(LEAVES_YEAR, year) * sheltered);
		float hollow = Math.max(peak, 0.4F * cold * breeze * (1 - woods));
		set(out, Bed.WIND_HIGH, (0.25F + 0.75F * breeze) * hollow * sheltered);

		// ---- rain: on the leaves under trees, on the open ground, on the roof when under one
		float shower = (float) Math.pow(rain, 0.7);
		float underRoof = land.roof() * Noise.smoothstep(0.25F, 0.7F, air.indoors()) * (1 - land.buried());
		set(out, Bed.RAIN_ROOF, shower * underRoof);
		set(out, Bed.RAIN_LEAVES, shower * woods * outside);
		set(out, Bed.RAIN_GROUND, shower * (1 - 0.6F * woods) * outside);

		// ---- birds: the dawn chorus, then a few distant songs through the day; few in the cold, the wet, the gales
		float birdLife = Noise.smoothstep(-0.3F, 0.3F, temperature) * (1 - 0.7F * Noise.smoothstep(1.6F, 2.0F, temperature) * (1 - woods))
			* (1 - 0.85F * rain) * (1 - 0.5F * Noise.smoothstep(6, 13, wind)) * (1 - 0.8F * peak) * outside;
		float birdHabitat = 0.35F + 0.65F * Math.max(woods, land.grass() * 0.5F);
		set(out, Bed.DAWN_CHORUS, window(hour, 4.3F, 5.2F, 6.6F, 7.6F) * yearly(CHORUS_YEAR, year) * birdLife * birdHabitat);
		set(out, Bed.BIRDS_DAY, window(hour, 6.5F, 8.0F, 18.5F, 20.0F) * yearly(BIRDS_YEAR, year) * birdLife * birdHabitat);

		// ---- insects and frogs: warm nights and warm lands; none in winter, none in the rain
		float insectWarmth = Noise.smoothstep(0.25F, 0.7F, temperature) * Noise.smoothstep(-0.6F, 0.1F, warmth);
		float dry = (1 - rain) * (1 - rain);
		set(out, Bed.CRICKETS, window(hour, 18.6F, 20.5F, 4.0F, 5.4F) * yearly(CRICKETS_YEAR, year) * insectWarmth * dry
			* (0.25F + 0.75F * land.grass()) * lowland * outside);
		set(out, Bed.FROGS, window(hour, 17.5F, 19.5F, 1.5F, 4.0F) * yearly(FROGS_YEAR, year) * Noise.smoothstep(0.2F, 0.6F, temperature)
			* Noise.smoothstep(-0.6F, 0.0F, warmth) * (1 - 0.6F * storm) * land.still() * lowland * outside);
		set(out, Bed.CICADAS, window(hour, 9.0F, 11.5F, 17.0F, 19.0F) * yearly(CICADAS_YEAR, year) * Noise.smoothstep(0.8F, 1.3F, temperature)
			* dry * (1 - rain) * (0.3F + 0.7F * woods) * outside);

		// ---- water: a brook nearby, waves on a shore (higher in the wind)
		set(out, Bed.STREAM, land.running() * outside);
		set(out, Bed.WAVES, land.shore() * (0.35F + 0.65F * Noise.smoothstep(1, 10, wind)) * outside);
	}

	/** The pitch of the wind beds: up a little with the wind's strength, its gusts, and in the cold. */
	public static float windPitch(AirSample air) {
		float breeze = Noise.smoothstep(0.5F, 11.0F, air.wind().now());
		float cold = Noise.smoothstep(-0.2F, -0.8F, air.warmth());
		return Math.clamp(0.93F + 0.06F * breeze + 0.08F * air.wind().gust() + 0.05F * cold, 0.85F, 1.2F);
	}

	private static void set(float[] out, Bed bed, float strength) {
		out[bed.ordinal()] = Math.clamp(strength, 0, 1) * bed.gain();
	}

	/**
	 * A daily window: 0 before {@code rise0}, up to 1 by {@code rise1}, down again from {@code fall0} to 0 at
	 * {@code fall1}. Hours run 0..24 and a window may cross midnight (evening to early morning).
	 */
	static float window(float hour, float rise0, float rise1, float fall0, float fall1) {
		float x = wrap(hour - rise0);
		float up = Noise.smoothstep(0, wrap(rise1 - rise0), x);
		float down = Noise.smoothstep(wrap(fall0 - rise0), wrap(fall1 - rise0), x);
		return up * (1 - down);
	}

	private static float wrap(float hours) {
		return ((hours % 24) + 24) % 24;
	}

	/** Reads a table of eight values through the year (0..1 from the first day of spring), one at each eighth's middle. */
	static float yearly(float[] table, float year) {
		float at = (((year % 1) + 1) % 1) * table.length - 0.5F;
		int i = (int) Math.floor(at);
		float t = at - i;
		float a = table[Math.floorMod(i, table.length)], b = table[Math.floorMod(i + 1, table.length)];
		return a + (b - a) * t;
	}
}
