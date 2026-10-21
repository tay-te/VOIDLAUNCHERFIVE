package dev.voidmc.hearth.social.calendar;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.social.SocialConfig;
import org.jspecify.annotations.Nullable;

/**
 * The fixed calendar of a settlement: its weekly market day and its feast days. A pure function of the settlement's id,
 * culture, founding day and the config, so a town keeps the same weekday for ever, a caught-up history agrees with a
 * live one, and other towns' calendars can be told as rumours without loading anything.
 */
public final class TownCalendar {
	private TownCalendar() {
	}

	/** The settlement's own weekday offset: towns along a road do not all hold market on the same day. */
	public static int weekday(long settlementId, long day, int weekDays) {
		return (int) Math.floorMod(day + Math.floorMod(mix(settlementId), (long) weekDays), (long) weekDays);
	}

	public static boolean isMarketDay(long settlementId, long day, SocialConfig.Market cfg, SettlementTier tier) {
		return cfg.enabled() && tier.ordinal() >= cfg.minTier().ordinal() && weekday(settlementId, day, cfg.weekDays()) == 0;
	}

	/** The next market day at or after {@code from}. */
	public static long nextMarketDay(long settlementId, long from, SocialConfig.Market cfg) {
		int w = weekday(settlementId, from, cfg.weekDays());
		return w == 0 ? from : from + (cfg.weekDays() - w);
	}

	/**
	 * The weekday a town keeps its rest day on: the culture's ({@code wanted}), kept off the market day (weekday 0) and off
	 * the day before it, so the wash day that follows the rest day never falls on the market day either.
	 */
	public static int restWeekday(int wanted, int weekDays) {
		if (weekDays < 3) {
			return -1; // too short a week for a rest day and a wash day besides the market
		}
		return 1 + Math.floorMod(wanted - 1, weekDays - 2);
	}

	/** The rest day (docs/LIVING_WORLD_2.md §2): no work but the listed trades', the service in the morning. */
	public static boolean isRestDay(long settlementId, long day, int weekDays, int wantedWeekday) {
		int w = restWeekday(wantedWeekday, weekDays);
		return w > 0 && weekday(settlementId, day, weekDays) == w;
	}

	/** The wash day: the day after the rest day. */
	public static boolean isWashDay(long settlementId, long day, int weekDays, int wantedWeekday) {
		int w = restWeekday(wantedWeekday, weekDays);
		return w > 0 && weekday(settlementId, day, weekDays) == w + 1;
	}

	/** The next rest day at or after {@code from}. */
	public static long nextRestDay(long settlementId, long from, int weekDays, int wantedWeekday) {
		for (long d = from; d < from + weekDays; d++) {
			if (isRestDay(settlementId, d, weekDays, wantedWeekday)) {
				return d;
			}
		}
		return -1;
	}

	/** The festival held on a day, or null. Midsummer and harvest come first, then the culture's, then founders' day. */
	public static @Nullable Festival festivalOn(long day, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		if (!cfg.enabled() || tier.ordinal() < cfg.minTier().ordinal()) {
			return null;
		}
		int year = cfg.yearDays();
		int d = (int) Math.floorMod(day, (long) year);
		if (d == Math.floorMod(cfg.midsummerDay(), year)) {
			return Festival.MIDSUMMER;
		}
		if (d == Math.floorMod(cfg.harvestDay(), year)) {
			return Festival.HARVEST;
		}
		Festival own = Festival.ofCulture(culture);
		if (own != null && d == Math.floorMod(cfg.cultureDay(), year)) {
			return own;
		}
		if (cfg.foundersDay() && d == Math.floorMod(foundedDay, (long) year) && day != foundedDay) {
			return Festival.FOUNDERS;
		}
		return null;
	}

	/** The next festival at or after {@code from} (searching one year ahead), or null if there are none. */
	public static @Nullable Upcoming nextFestival(long from, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		for (long d = from; d <= from + cfg.yearDays(); d++) {
			Festival f = festivalOn(d, culture, foundedDay, tier, cfg);
			if (f != null) {
				return new Upcoming(f, d);
			}
		}
		return null;
	}

	public record Upcoming(Festival festival, long day) {
	}

	/** When a festival begins and ends on its day, as minutes of the day (end may be past midnight). */
	public static int[] festivalHours(Festival f, SocialConfig.Festivals cfg) {
		if (f.night) {
			return new int[] {Math.floorMod(cfg.start() + 120, SocialTime.MINUTES), Math.floorMod(cfg.end() + 120, SocialTime.MINUTES)};
		}
		return new int[] {cfg.start(), cfg.end()};
	}

	// ---- fairs and tournaments (docs/LIVING_WORLD_2.md §3.1): once a festival year each, on days of the town's own

	/**
	 * The day of the year (0-based) a town's two-day fair opens: fixed per town, clear of its feast days and of its
	 * market day's eve, so a town keeps its fair in the same week every year.
	 */
	public static int fairYearDay(long settlementId, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		return freeYearDay(settlementId * 0x2545F4914F6CDD1DL + 17, 2, culture, foundedDay, tier, cfg, -1);
	}

	/** The day of the year a lord's tournament is held: fixed per town, clear of its feasts and of its fair. */
	public static int tourneyYearDay(long settlementId, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		return freeYearDay(settlementId * 0x9E3779B97F4A7C15L + 29, 1, culture, foundedDay, tier, cfg, fairYearDay(settlementId, culture, foundedDay, tier,
			cfg));
	}

	/** Whether a day is one of the town's two fair days. */
	public static boolean isFairDay(long settlementId, long day, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		int year = Math.max(8, cfg.yearDays());
		int d = (int) Math.floorMod(day, (long) year);
		int open = fairYearDay(settlementId, culture, foundedDay, tier, cfg);
		return d == open || d == Math.floorMod(open + 1, year);
	}

	public static boolean isTourneyDay(long settlementId, long day, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		int year = Math.max(8, cfg.yearDays());
		return (int) Math.floorMod(day, (long) year) == tourneyYearDay(settlementId, culture, foundedDay, tier, cfg);
	}

	/** The next day at or after {@code from} that the predicate's kind falls on (searching a year), or -1. */
	public static long nextFairDay(long settlementId, long from, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg) {
		for (long d = from; d <= from + Math.max(8, cfg.yearDays()); d++) {
			if (isFairDay(settlementId, d, culture, foundedDay, tier, cfg)) {
				return d;
			}
		}
		return -1;
	}

	private static int freeYearDay(long seed, int span, Culture culture, long foundedDay, SettlementTier tier, SocialConfig.Festivals cfg, int avoid) {
		int year = Math.max(8, cfg.yearDays());
		int start = (int) Math.floorMod(mix(seed), (long) year);
		for (int k = 0; k < year; k++) {
			int d = Math.floorMod(start + k * 3, year);
			boolean clear = true;
			for (int i = -1; i <= span; i++) {
				int dd = Math.floorMod(d + i, year);
				if (festivalOn(dd, culture, foundedDay, tier, cfg) != null || dd == avoid || avoid >= 0 && dd == Math.floorMod(avoid + 1, year)) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return d;
			}
		}
		return start;
	}

	/** A cheap, well-mixed hash (splitmix64's finaliser). */
	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** A number in [0, 1) from a few longs, for "does this folk come this hour" decisions that must not flicker. */
	public static double chance(long a, long b, long c) {
		long h = mix(a * 0x9E3779B97F4A7C15L + mix(b + 0x632BE59BD9B4E019L * c));
		return (h >>> 11) * 0x1.0p-53;
	}
}
