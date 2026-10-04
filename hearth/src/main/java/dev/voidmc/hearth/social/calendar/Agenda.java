package dev.voidmc.hearth.social.calendar;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.office.Office;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A settlement's day: what is on, when, and who comes. Built from the fixed calendar ({@link TownCalendar}), the
 * weddings and funerals in the ledger, and the lord's court. Pure apart from reading the settlement; cheap enough to
 * rebuild whenever the day changes (callers cache it per settlement and day).
 */
public final class Agenda {
	/** Minutes a wedding ceremony, its feast and a funeral last. */
	static final int CEREMONY = 60;
	static final int FEAST = 180;
	static final int FUNERAL = 60;

	private Agenda() {
	}

	public static List<TownEvent> of(Settlement s, SocialLedger ledger, long day, SocialConfig cfg) {
		List<TownEvent> out = new ArrayList<>();
		if (!cfg.life().enabled()) {
			return out;
		}
		boolean market = TownCalendar.isMarketDay(s.id(), day, cfg.market(), s.tier());
		if (market) {
			out.add(new TownEvent("market:" + day, TownEvent.Kind.MARKET, SocialTime.at(day, cfg.market().open()), SocialTime.at(day, cfg.market().close()),
				null, List.of(), List.of(), List.of()));
		}
		Festival f = TownCalendar.festivalOn(day, s.culture(), s.foundedDay(), s.tier(), cfg.festivals());
		if (f != null) {
			int[] h = TownCalendar.festivalHours(f, cfg.festivals());
			out.add(new TownEvent("festival:" + f.id() + ":" + day, TownEvent.Kind.FESTIVAL, SocialTime.at(day, h[0]), end(day, h[0], h[1]), f, List.of(),
				List.of(), List.of()));
		}
		for (SocialLedger.FamilyEvent e : ledger.family) {
			if (e.day() != day) {
				continue;
			}
			switch (e.kind()) {
				case WEDDING -> {
					int t = cfg.family().weddingTime();
					String id = e.people().isEmpty() ? "?" : e.people().get(0).toString();
					out.add(new TownEvent("wedding:" + day + ":" + id, TownEvent.Kind.WEDDING, SocialTime.at(day, t), SocialTime.at(day, t + CEREMONY), null,
						e.people(), e.family(), e.names()));
					out.add(new TownEvent("feast:" + day + ":" + id, TownEvent.Kind.FEAST, SocialTime.at(day, t + CEREMONY),
						SocialTime.at(day, t + CEREMONY + FEAST), null, e.people(), e.family(), e.names()));
				}
				case FUNERAL -> {
					int t = cfg.family().funeralTime();
					String id = e.people().isEmpty() ? "?" : e.people().get(0).toString();
					out.add(new TownEvent("funeral:" + day + ":" + id, TownEvent.Kind.FUNERAL, SocialTime.at(day, t), SocialTime.at(day, t + FUNERAL), null,
						List.of(), e.family(), e.names()));
				}
			}
		}
		UUID lord = ledger.offices.get(Office.LORD.id());
		if (lord != null && !market && f == null) {
			List<UUID> court = new ArrayList<>();
			court.add(lord);
			UUID steward = ledger.offices.get(Office.STEWARD.id());
			if (steward != null) {
				court.add(steward);
			}
			out.add(new TownEvent("court:" + day, TownEvent.Kind.COURT, SocialTime.at(day, cfg.offices().courtStart()),
				SocialTime.at(day, cfg.offices().courtEnd()), null, court, List.of(), List.of()));
		}
		return out;
	}

	/** An end time that may run past midnight (still the same sim day, which ends at sunrise). */
	private static long end(long day, int startMinute, int endMinute) {
		long s = SocialTime.at(day, startMinute);
		long e = SocialTime.at(day, endMinute);
		return e > s ? e : s + 60L * SocialTime.DAY_TICKS / SocialTime.MINUTES;
	}

	/**
	 * Whether a resident comes to an event at this moment. Kin and principals always come; others by a chance that is
	 * fixed per person and event (or per hour, for the market's coming and going), so nobody flickers in and out.
	 */
	public static boolean attends(TownEvent e, Resident r, long townTick, SocialConfig cfg) {
		if (!e.activeAt(townTick)) {
			return false;
		}
		if (e.involves(r.id)) {
			return true;
		}
		long seed = r.id.getMostSignificantBits() ^ r.id.getLeastSignificantBits();
		int hour = SocialTime.minute(townTick) / 60;
		return switch (e.kind()) {
			case MARKET -> {
				boolean free = r.age != AgeStage.ADULT || r.job == JobType.NONE;
				if (!free && hour != 12) {
					yield false; // working folk come in their dinner hour
				}
				yield TownCalendar.chance(seed, e.key().hashCode(), hour) < cfg.market().crowdShare() * (free ? 1.3 : 1.0);
			}
			case FESTIVAL -> TownCalendar.chance(seed, e.key().hashCode(), 0) < cfg.festivals().share();
			// the same guests at the ceremony and the feast
			case WEDDING, FEAST -> TownCalendar.chance(seed, e.principals().isEmpty() ? e.key().hashCode() : e.principals().get(0).hashCode(), 1)
				< cfg.family().guestShare();
			case FUNERAL -> r.age != AgeStage.CHILD && TownCalendar.chance(seed, e.key().hashCode(), 2) < cfg.family().mournerShare();
			case COURT -> r.age == AgeStage.ADULT && TownCalendar.chance(seed, e.key().hashCode(), hour) < 0.06;
		};
	}

	/** The event a resident should be at now, by precedence (a funeral before a feast before a market...), or null. */
	public static @org.jspecify.annotations.Nullable TownEvent forResident(List<TownEvent> events, Resident r, long townTick, SocialConfig cfg) {
		TownEvent best = null;
		int bestRank = Integer.MAX_VALUE;
		for (TownEvent e : events) {
			if (!attends(e, r, townTick, cfg)) {
				continue;
			}
			int rank = switch (e.kind()) {
				case FUNERAL -> 0;
				case WEDDING -> 1;
				case COURT -> e.principals().contains(r.id) ? 1 : 5;
				case FEAST -> 2;
				case FESTIVAL -> 3;
				case MARKET -> 4;
			};
			if (rank < bestRank) {
				bestRank = rank;
				best = e;
			}
		}
		return best;
	}
}
