package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.Agenda;
import dev.voidmc.hearth.social.calendar.Festival;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's week and year for other modules ({@code HearthServices.calendar()}, docs/LIVING_WORLD_2.md §2.2): the
 * market day, the rest day and the wash day after it, the feasts and the first snow. Computed, never stored: a pure
 * function of the settlement's id, culture, founding day and the data, so lanes that send market goers or stage events
 * agree with what the town keeps. A command may hold a rest or wash day in one town for a day (tours, testing).
 */
public final class RhythmCalendar implements SettlementCalendar {
	public static final RhythmCalendar INSTANCE = new RhythmCalendar();
	/** The first snow's feast id ({@link #feast}). */
	public static final String FIRST_SNOW = "first_snow";

	public enum Forced {
		REST, WASH, PLAIN
	}

	/** Days held by command: settlement → (day, kind). */
	private static final Map<Long, long[]> FORCED = new ConcurrentHashMap<>();

	private RhythmCalendar() {
	}

	public static void force(long settlement, long day, @Nullable Forced kind) {
		if (kind == null) {
			FORCED.remove(settlement);
		} else {
			FORCED.put(settlement, new long[] {day, kind.ordinal()});
		}
	}

	static @Nullable Forced forced(long settlement, long day) {
		long[] f = FORCED.get(settlement);
		// held for the day it was asked on and the next (a town run ahead of the world clock turns its day over early)
		return f == null || day < f[0] || day > f[0] + 1 ? null : Forced.values()[(int) f[1]];
	}

	static void clearForced() {
		FORCED.clear();
	}

	static int weekDays() {
		return SocialModule.config().market().weekDays();
	}

	/** Rest day by the data alone (no command), for a town's id and culture: tests and the abstract side. */
	public static boolean restDay(long settlementId, RhythmData data, long day, SocialConfig cfg) {
		return TownCalendar.isRestDay(settlementId, day, cfg.market().weekDays(), data.restWeekday());
	}

	public static boolean washDay(long settlementId, RhythmData data, long day, SocialConfig cfg) {
		return TownCalendar.isWashDay(settlementId, day, cfg.market().weekDays(), data.restWeekday());
	}

	/** Whether a year day is in the cold cultures' winter (from the first snow for its span). */
	public static boolean winter(RhythmData data, long day, int yearDays) {
		if (!data.snow().cold() || yearDays <= 0) {
			return false;
		}
		int d = (int) Math.floorMod(day, (long) yearDays);
		int from = Math.floorMod(data.snow().day(), yearDays);
		int into = Math.floorMod(d - from, yearDays);
		return into < Math.max(1, data.snow().winterDays());
	}

	public static boolean firstSnow(RhythmData data, long day, int yearDays) {
		return data.snow().cold() && yearDays > 0 && Math.floorMod(day, (long) yearDays) == Math.floorMod(data.snow().day(), yearDays);
	}

	@Override
	public boolean restDay(Settlement s, long day) {
		Forced f = forced(s.id(), day);
		if (f != null) {
			return f == Forced.REST;
		}
		return !marketDay(s, day) && restDay(s.id(), RhythmData.of(s.culture()), day, SocialModule.config());
	}

	@Override
	public boolean washDay(Settlement s, long day) {
		Forced f = forced(s.id(), day);
		if (f != null) {
			return f == Forced.WASH;
		}
		return washDay(s.id(), RhythmData.of(s.culture()), day, SocialModule.config());
	}

	@Override
	public boolean marketDay(Settlement s, long day) {
		Forced f = forced(s.id(), day);
		if (f == Forced.REST) {
			return false;
		}
		return TownCalendar.isMarketDay(s.id(), day, SocialModule.config().market(), s.tier());
	}

	@Override
	public @Nullable String feast(Settlement s, long day) {
		SocialConfig cfg = SocialModule.config();
		Festival f = TownCalendar.festivalOn(day, s.culture(), s.foundedDay(), s.tier(), cfg.festivals());
		if (f != null) {
			return f.id();
		}
		return firstSnow(RhythmData.of(s.culture()), day, cfg.festivals().yearDays()) ? FIRST_SNOW : null;
	}

	@Override
	public int curfew(Settlement s) {
		return RhythmData.of(s.culture()).curfew();
	}

	@Override
	public int dawn(Settlement s) {
		List<Integer> times = SocialModule.config().bells().times();
		return times.isEmpty() ? 6 * 60 : times.getFirst();
	}

	@Override
	public List<String> events(Settlement s, long day) {
		List<String> out = new ArrayList<>();
		for (TownEvent e : Agenda.of(s, s.data(SocialModule.LEDGER), day, SocialModule.config())) {
			out.add(e.key());
		}
		return out;
	}
}
