package dev.voidmc.gloam.air;

/**
 * The air over one world: the year and its seasons, the wind, the weather fronts that drift across the land, and the
 * mist that settles in the low places. Everything here is a pure function of the world's seed and its clocks, so every
 * client and the server work out the same sky at the same place without anything being sent; the parts that need
 * history (how wet the ground still is from the last rain) are passed in by whoever keeps it.
 *
 * <p>Times are Minecraft's: {@code clock} is the overworld clock's total ticks (24000 a day, tick 0 at sunrise,
 * 6000 noon, 18000 midnight), {@code gameTime} the ticks the world has run, which never jump when someone sleeps or
 * sets the time.
 *
 * <p>Vanilla's weather stays in charge of what weather does in the game (crops, fire, snow, mobs); Gloam decides where
 * it shows. Under vanilla rain most of the land is wet but the fronts leave gaps; under a clear vanilla sky the fronts
 * still bring cloud, and now and then a passing shower ({@link #showers}).
 */
public final class Air {
	public static final int TICKS_PER_DAY = 24000;
	/** Where a new world's year starts: a little into spring, the green time the Bohemian countryside opens in. */
	public static final float YEAR_START = 0.15F;
	/** How fast the fronts drift east, in blocks per second (about 20 km/h). */
	public static final float FRONT_DRIFT = 6.0F;
	/** The size of a weather system, in blocks: a front is a few kilometres across. */
	public static final float FRONT_SCALE = 3000.0F;
	/** The share of vanilla's rain that still falls under the clearest part of the fronts: a drizzle. */
	public static final float DRIZZLE = 0.35F;

	private final long seed;
	private final int seasonDays;

	/**
	 * @param seed       the world's seed, or anything all clients of a world agree on (the hashed seed a server sends)
	 * @param seasonDays how many Minecraft days one season lasts
	 */
	public Air(long seed, int seasonDays) {
		this.seed = seed;
		this.seasonDays = Math.max(1, seasonDays);
	}

	public long seed() {
		return this.seed;
	}

	public int seasonDays() {
		return this.seasonDays;
	}

	// ----------------------------------------------------------------------------------------------------- the year

	/** Where the year is, 0..1: 0 the first day of spring, 0.25 of summer, 0.5 of autumn, 0.75 of winter. */
	public float year(long clock) {
		double days = clock / (double) TICKS_PER_DAY;
		double year = days / (4.0 * this.seasonDays) + YEAR_START;
		return (float) (year - Math.floor(year));
	}

	public Season season(long clock) {
		return Season.of(this.year(clock));
	}

	/**
	 * How warm the season is, -1 (midwinter) .. 1 (midsummer): a smooth swing through the year that lags the seasons'
	 * starts the way real weather does, warmest halfway through summer and coldest halfway through winter.
	 */
	public static float warmth(float year) {
		return (float) Math.cos(2 * Math.PI * (year - 0.375));
	}

	/** The hour of the day, 0..24 (0 midnight, 6 sunrise, 12 noon, 18 sunset). */
	public static float hour(long clock) {
		return Math.floorMod(clock + 6000, TICKS_PER_DAY) / 1000.0F;
	}

	// ----------------------------------------------------------------------------------------------------- the wind

	/**
	 * The wind at {@code gameTime}. It mostly blows from the west, swings round over hours, freshens and drops over tens
	 * of minutes, and gusts over seconds; a storm where the camera is ({@code storm} 0..1) makes it stronger and gustier.
	 */
	public Wind wind(long gameTime, float storm) {
		double seconds = gameTime / 20.0;
		float swing = (Noise.fbm(this.seed + 11, seconds / 5400.0, 0.5, 0.5, 2) - 0.5F) * 2.0F;
		double angle = swing * Math.PI * 0.9 + 0.15;
		float strength = Noise.fbm(this.seed + 12, seconds / 900.0, 1.5, 0.5, 3);
		float speed = 0.8F + 7.0F * Noise.smoothstep(0.3F, 0.8F, strength) + storm * 6.0F;
		float gust = Noise.smoothstep(0.45F, 0.85F, Noise.fbm(this.seed + 13, seconds / 6.0, 2.5, 0.5, 3));
		gust = Math.min(1, gust * (0.6F + 0.4F * strength + storm * 0.5F));
		return new Wind((float) Math.cos(angle), (float) Math.sin(angle), speed, gust);
	}

	// ------------------------------------------------------------------------------------------------ weather fronts

	/**
	 * How stormy the sky is over (x, z), 0 clear .. 1 the heart of a storm: a slow fractal field, a few kilometres to a
	 * system, that drifts east across the land and changes shape as it goes. The season and the biome lean on it:
	 * autumn and wet biomes are cloudier, summer clearer, deserts nearly always clear.
	 */
	public float front(double x, double z, long gameTime, float year, float downfall) {
		double seconds = gameTime / 20.0;
		double fx = (x - FRONT_DRIFT * seconds) / FRONT_SCALE;
		double fz = (z - FRONT_DRIFT * 0.2 * seconds) / FRONT_SCALE;
		float field = Noise.fbm(this.seed + 21, fx, fz, seconds / 7200.0, 4);
		float stretched = (field - 0.5F) * 1.6F + 0.5F;
		return Math.clamp(stretched + seasonBias(year) + (downfall - 0.5F) * 0.16F, 0.0F, 1.0F);
	}

	/** How much wetter or drier than the year's average the season runs: wettest in autumn, driest in high summer. */
	static float seasonBias(float year) {
		return (float) (0.06 * Math.cos(2 * Math.PI * (year - 0.55)) - 0.02 * Math.cos(4 * Math.PI * year));
	}

	/** How much of the sky the clouds cover where the front is {@code front}, with the rain there at {@code rain}. */
	public static float cloudCover(float front, float rain) {
		return Math.max(Noise.smoothstep(0.30F, 0.75F, front), rain * 0.95F);
	}

	/** The passing showers a front brings under a clear vanilla sky, 0..0.6: only its wettest parts rain. */
	public static float showers(float front) {
		return 0.6F * Noise.smoothstep(0.74F, 0.9F, front);
	}

	/** The storm in a front, 0..1: thunderheads, the strongest wind, the heaviest rain. Summer brings them lower. */
	public static float storm(float front, float year) {
		float summer = Math.max(0, warmth(year));
		return Noise.smoothstep(0.9F - 0.05F * summer, 0.98F, front);
	}

	/**
	 * How hard it rains over a place, 0..1, given vanilla's rain level ({@code vanilla}) and the front there. Under
	 * vanilla rain it rains everywhere: full strength wherever the front is above about 0.3, and at least a drizzle
	 * ({@link #DRIZZLE} of vanilla's) under the clearest parts of the field. Only while vanilla's rain is setting in or
	 * clearing do those clear parts stay dry a little longer, so a downpour arrives and leaves with the fronts. Under
	 * a clear vanilla sky only the showers fall (when {@code showers} allows them).
	 */
	public static float rain(float vanilla, float front, boolean showers) {
		float floor = DRIZZLE * Noise.smoothstep(0.2F, 0.7F, vanilla);
		float under = vanilla * (floor + (1 - floor) * Noise.smoothstep(0.1F, 0.3F, front));
		return Math.max(under, showers ? showers(front) : 0);
	}


	// ----------------------------------------------------------------------------------------------------- the mist

	/**
	 * How thick the mist lies near the ground, 0..1. It gathers after midnight in the low, wet places, is thickest at
	 * sunrise and burns off by mid-morning; after sunset a thinner veil lifts off water and wet meadows. Autumn and the
	 * cold make more of it, high summer less, and recent rain ({@code wetness} 0..1) leaves the ground steaming. Hot
	 * dry land (deserts, badlands) has none.
	 */
	public static float mist(long clock, float year, Climate climate, float wetness) {
		float hour = hour(clock);
		float dawn;
		if (hour >= 1 && hour < 6.2F) {
			dawn = Noise.smoothstep(1.0F, 6.0F, hour);
		} else if (hour >= 6.2F && hour < 10) {
			dawn = 1 - Noise.smoothstep(6.4F, 9.6F, hour);
		} else {
			dawn = 0;
		}
		float evening = hour >= 18.5F ? Noise.smoothstep(18.5F, 21.0F, hour) : hour < 1 ? 1 : 0;
		float humid = Math.clamp(climate.downfall(), 0, 1);
		float dry = Noise.smoothstep(1.2F, 2.0F, climate.temperature());
		float season = switch (Season.of(year)) {
			case SPRING -> 1.0F;
			case SUMMER -> 0.7F;
			case AUTUMN -> 1.5F;
			case WINTER -> 1.15F;
		};
		float settle = 0.35F + 0.65F * climate.lowness();
		float m = dawn * (0.3F + 0.7F * humid) * settle * season;
		m += evening * 0.35F * Math.max(climate.water(), climate.lowness() * humid) * season;
		m += Math.min(1, wetness) * 0.35F * (0.4F + 0.6F * Math.max(dawn, evening));
		m += climate.water() * 0.15F * Math.max(dawn, evening);
		return Math.clamp(m * (1 - dry), 0.0F, 1.0F);
	}

	/**
	 * How high the mist reaches above the ground it lies on, in blocks: a shallow layer that fills hollows and valley
	 * floors, deeper where it is thick, so hills and treetops stand out of it.
	 */
	public static float mistDepth(float mist) {
		return 4.0F + 26.0F * mist;
	}
}
