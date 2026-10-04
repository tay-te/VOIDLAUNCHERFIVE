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
