package dev.voidmc.mettle.character.skill.survival;

/**
 * The Survival tree's numbers (TREES.md §9, §12; BALANCE.md §3) and the small sums behind them, kept free of game
 * state so JUnit can check them.
 */
public final class SurvivalRules {
	// XP (BALANCE.md §3)
	public static final double XP_ANIMAL = 4;
	public static final double XP_PLANT = 1;
	public static final double XP_COOKED = 1;
	public static final double XP_BIOME = 25;
	public static final double XP_STRUCTURE = 40;
	public static final double XP_LANDMARK = 150;
	/** A kill counts for whoever dealt at least this share of the damage. */
	public static final double KILL_SHARE = 0.5;

	// Roots
	public static final double GATHERER_PER_RANK = 0.10;
	public static final double FIELD_COOK_PER_RANK = 0.10;

	// Hunter
	public static final double QUIET_APPROACH_RANGE = 4.0;
	public static final double SIXTH_SENSE_RANGE = 16.0;
	public static final int SIXTH_SENSE_COOLDOWN = 60;

	// Forager
	public static final int CAMPFIRE_RANGE = 3;
	public static final int CAMPFIRE_REST_INTERVAL = 80;
	public static final float CAMPFIRE_REST_HEAL = 2f;
	public static final double PATHFINDER_SPEED = 0.15;
	public static final float SELF_SUFFICIENT = 0.75f;

	// Masteries
	public static final int TRACKER_STILL = 40;
	public static final int TRACKER_DURATION = 600;
	public static final double TRACKER_RANGE = 96;
	public static final double TRACKER_PRINT_SPACING = 2.0;
	public static final double LIVING_OFF_THE_LAND_CHANCE = 1.0 / 6.0;
	public static final double WILD_FOOD = 2.0;
	/** Lay of the Land: how close counts as having reached the marked landmark. */
	public static final double MARKER_REACHED = 48;

	// Feats and teaching
	public static final int WEATHER_WARNING = 2400;
	public static final int POULTICE_HERBS = 3;
	public static final int POULTICE_TICKS = 160;
	public static final int POULTICE_COOLDOWN = 600;
	public static final float NINE_LIVES_HEALTH = 1f;
	public static final int NINE_LIVES_COOLDOWN = 1200;
	public static final float NINE_LIVES_BELOW = 6f;
	public static final double NINE_LIVES_SPEED = 0.20;
	public static final double JACK_BONUS = 1.25;
	public static final int JACK_BELOW = 10;
	public static final int JACK_LEVEL = 5;
	/** Pack Horse ignores more than this many blocks between two one-second samples (teleports, knockback). */
	public static final double PACK_HORSE_MAX_STEP = 12;

	private SurvivalRules() {
	}

	/** Whether a player who dealt {@code mine} of the {@code total} damage gets the kill. */
	public static boolean ownsKill(double mine, double total) {
		return total > 0 && mine / total >= KILL_SHARE;
	}

	/** Nutrition after a fill multiplier: rounded, and never less than it was. */
	public static int nutrition(int nutrition, double multiplier) {
		return Math.max(nutrition, (int) Math.round(nutrition * multiplier));
	}

	/** How full one bite of food makes this player: Field Cook on cooked food, Living off the Land on wild food. */
	public static double fillMultiplier(boolean cooked, int fieldCookRank, boolean wild, boolean livingOffTheLand) {
		double m = 1.0;
		if (cooked && fieldCookRank > 0) {
			m *= 1.0 + FIELD_COOK_PER_RANK * fieldCookRank;
		}
		if (wild && livingOffTheLand) {
			m *= WILD_FOOD;
		}
		return m;
	}

	/**
	 * Ticks until it starts raining in the overworld, from the weather clock ({@code ServerLevel#advanceWeatherCycle}):
	 * forced clear weather runs out first and rain begins the tick after; otherwise the rain timer counts down to the
	 * flip. -1 when it is raining already or the clock hasn't picked the next delay yet.
	 */
	public static int ticksUntilRain(int clearWeatherTime, int rainTime, boolean raining) {
		if (raining) {
			return -1;
		}
		if (clearWeatherTime > 0) {
			return clearWeatherTime + 1;
		}
		return rainTime > 0 ? rainTime : -1;
	}

	/**
	 * Ticks until a storm (rain and thunder at once), or -1. Rain and thunder run on separate clocks; a storm starts when
	 * the later of the two arrives while the other is still on.
	 */
	public static int ticksUntilStorm(int clearWeatherTime, int rainTime, boolean raining, int thunderTime, boolean thundering) {
		if (thundering && raining) {
			return -1;
		}
		if (raining) {
			// Thunder must start before the rain stops.
			int thunder = clearWeatherTime > 0 ? clearWeatherTime + 1 : thunderTime > 0 ? thunderTime : -1;
			return thunder >= 0 && thunder < rainTime ? thunder : -1;
		}
		int rain = ticksUntilRain(clearWeatherTime, rainTime, false);
		if (rain < 0) {
			return -1;
		}
		if (thundering) {
			// Rain must arrive before the thunder stops.
			return rain < thunderTime ? rain : -1;
		}
		int thunder = clearWeatherTime > 0 ? clearWeatherTime + 1 : thunderTime > 0 ? thunderTime : -1;
		return thunder < 0 ? -1 : Math.max(rain, thunder);
	}

	/** Whether a check every {@code interval} ticks should warn now, so the warning lands once, two minutes out. */
	public static boolean warnNow(int ticksUntil, int interval) {
		return ticksUntil > WEATHER_WARNING - interval && ticksUntil <= WEATHER_WARNING;
	}

	/** Pack Horse: blocks walked since the last sample, or 0 when it wasn't walking under a crushing load. */
	public static double packHorseStep(double dx, double dz, boolean onFoot, boolean heavy) {
		if (!onFoot || !heavy) {
			return 0;
		}
		double d = Math.sqrt(dx * dx + dz * dz);
		return d > PACK_HORSE_MAX_STEP ? 0 : d;
	}
}
