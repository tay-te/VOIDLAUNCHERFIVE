package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * A town's day of events (docs/LIVING_WORLD_2.md §3.2): from its facts and the rules, at most one major event and three
 * minor ones, each with a time, its people, its plot and an outcome seed. Pure and deterministic in (town, day): the
 * same day gives the same events however often it is asked and whether anyone is near.
 *
 * <p>Triggered events (a wedding, a funeral, a trial, a topping-out, a christening, a pillory, an execution) come in as
 * {@link Offer}s from what the town's books already hold; the rest are rolled here: tax by the domain's round, raids by
 * the bands about, fairs and tournaments on their days, the small events by chance. {@link Filler} adds the facts an
 * outcome needs (the plot that burns, the band's strength, the stores) and may drop an event that cannot be (no plot).
 */
public final class EventScheduler {
	/** Days between the bailiff's rounds (§3.2). */
	public static final int TAX_DAYS = 14;
	public static final int MAX_MINOR = 3;

	private EventScheduler() {
	}

	/**
	 * An event something else brought about.
	 *
	 * @param minute the minute of the day it begins, or -1 for its rule's window
	 * @param minutes how long it lasts, or -1 for its rule's
	 */
	public record Offer(EventKind kind, int minute, int minutes, List<UUID> principals, List<UUID> family, int plot, CompoundTag args) {
		public static Offer of(EventKind kind, List<UUID> principals, List<UUID> family) {
			return new Offer(kind, -1, -1, principals, family, -1, new CompoundTag());
		}
	}

	/** Completes an event's facts before it goes in the book; false drops it (nothing to burn, nobody to try). */
	@FunctionalInterface
	public interface Filler {
		boolean fill(EventKind kind, Draft draft);

		Filler NONE = (k, d) -> true;
	}

	/** An event being planned: the filler may set its plot, people and facts. */
	public static final class Draft {
		public final EventKind kind;
		public final long seed;
		public int plot;
		public final List<UUID> principals;
		public final List<UUID> family;
		public final CompoundTag args;
		int minute;
		int minutes;
		final double roll;

		Draft(EventKind kind, long seed, Offer o, double roll) {
			this.kind = kind;
			this.seed = seed;
			this.plot = o.plot();
			this.principals = new ArrayList<>(o.principals());
			this.family = new ArrayList<>(o.family());
			this.args = o.args().copy();
			this.minute = o.minute();
			this.minutes = o.minutes();
			this.roll = roll;
		}
	}

	/**
	 * The day's events.
	 *
	 * @param lastTax the last day the bailiff came (from the book), or {@link Long#MIN_VALUE}
	 * @param raidQuietUntil no raid before this day
	 */
	public static List<Scheduled> plan(TownFacts f, long day, Map<EventKind, EventRule> rules, List<Offer> offers, long lastTax, long raidQuietUntil,
		Filler filler, IntSupplier ids) {
		List<Draft> majors = new ArrayList<>();
		List<Draft> minors = new ArrayList<>();
		for (Offer o : offers) {
			EventRule rule = rules.get(o.kind());
			if (rule == null || !allowed(rule, f, true)) {
				continue;
			}
			long seed = seed(f.id(), day, o.kind());
			double roll = TownCalendar.chance(seed, 1, 2);
			if (roll >= rule.chanceFor(f.tier())) {
				continue;
			}
			(o.kind().major ? majors : minors).add(new Draft(o.kind(), seed, o, roll));
		}
		for (EventRule rule : rules.values()) {
			EventKind k = rule.kind();
			if (!allowed(rule, f, false)) {
				continue;
			}
			double p = switch (k.trigger) {
				case CHANCE -> rule.chanceFor(f.tier()) * factor(k, f);
				case DOMAIN -> taxDue(f, day, lastTax) ? rule.chanceFor(f.tier()) : 0;
				case BAND -> day >= raidQuietUntil && f.threat() > f.defence() ? rule.chanceFor(f.tier()) * (f.threat() - f.defence()) / (double) f.threat() : 0;
				case FAIR_DAY -> f.fairDay() ? rule.chanceFor(f.tier()) : 0;
				case TOURNEY_DAY -> f.tourneyDay() && f.lord() && f.has("tilt_yard") ? rule.chanceFor(f.tier()) : 0;
				default -> 0; // the triggered kinds come in as offers
			};
			if (p <= 0) {
				continue;
			}
			long seed = seed(f.id(), day, k);
			double roll = TownCalendar.chance(seed, 1, 2);
			if (roll < p) {
				(k.major ? majors : minors).add(new Draft(k, seed, Offer.of(k, List.of(), List.of()), roll));
			}
		}
		majors.sort(Comparator.comparingInt(d -> rank(d.kind)));
		minors.sort(Comparator.comparingDouble(d -> d.roll));
		List<Scheduled> out = new ArrayList<>();
		for (Draft d : majors) {
			if (filler.fill(d.kind, d)) {
				out.add(schedule(d, rules.get(d.kind), day, ids));
				break; // one major event a day
			}
		}
		int minor = 0;
		for (Draft d : minors) {
			if (minor >= MAX_MINOR) {
				break;
			}
			if (filler.fill(d.kind, d)) {
				out.add(schedule(d, rules.get(d.kind), day, ids));
				minor++;
			}
		}
		return out;
	}

	private static boolean allowed(EventRule rule, TownFacts f, boolean offered) {
		if (f.tier().ordinal() < rule.minTier().ordinal() || f.population() < rule.minPopulation()) {
			return false;
		}
		if (!offered && rule.kind().trigger != EventKind.Trigger.CHANCE && rule.kind().trigger != EventKind.Trigger.DOMAIN
			&& rule.kind().trigger != EventKind.Trigger.BAND && rule.kind().trigger != EventKind.Trigger.FAIR_DAY
			&& rule.kind().trigger != EventKind.Trigger.TOURNEY_DAY) {
			return false;
		}
		if (offered && rule.kind().trigger == EventKind.Trigger.FAMILY) {
			return true; // a wedding or funeral is held wherever there is a town: its venue falls back to the square
		}
		for (String need : rule.needs()) {
			if (!f.meets(need)) {
				return false;
			}
		}
		return true;
	}

	/** How the town's state moves a chance event's odds. */
	static double factor(EventKind k, TownFacts f) {
		return switch (k) {
			case FIRE -> (0.4 + 1.2 * f.timber()) * (f.season() == 1 ? 1.2 : f.season() == 3 ? 1.1 : 1.0);
			case PREACHER, WELL_QUARREL -> 1 + Math.max(0, 0.5 - f.prosperity());
			case TROUPE, HORSE_FAIR -> 0.6 + f.prosperity();
			default -> 1;
		};
	}

	/** Whether the bailiff comes today: a domain town (not the seat), on its own day of the round, fourteen days on. */
	public static boolean taxDue(TownFacts f, long day, long lastTax) {
		if (f.seat() == 0 || f.seat() == f.id()) {
			return false;
		}
		if (lastTax != Long.MIN_VALUE && day - lastTax < TAX_DAYS) {
			return false;
		}
		long phase = Math.floorMod(TownCalendar.mix(f.seat() * 31 + f.id()), (long) TAX_DAYS);
		// on its day of the round, or the first day after it when another major event kept the bailiff away
		return Math.floorMod(day, TAX_DAYS) == phase || lastTax != Long.MIN_VALUE && day - lastTax > TAX_DAYS;
	}

	/** Which major event a day keeps when several come: the dead first, then the lord's business, then the rest. */
	static int rank(EventKind k) {
		return switch (k) {
			case FUNERAL -> 0;
			case EXECUTION -> 1;
			case TAX -> 2;
			case TRIAL -> 3;
			case WEDDING -> 4;
			case RAID -> 5;
			case FAIR -> 6;
			case TOURNAMENT -> 7;
			case FIRE -> 8;
			default -> 20;
		};
	}

	private static Scheduled schedule(Draft d, EventRule rule, long day, IntSupplier ids) {
		int minute = d.minute;
		if (minute < 0) {
			int from = rule.start();
			int to = rule.latestStart();
			int span = Math.floorMod(to - from, SocialTime.MINUTES) + 1;
			// quarter hours only: town events start on the bell
			int quarters = Math.max(1, span / 15);
			minute = Math.floorMod(from + 15 * (int) Math.floorMod(d.seed >>> 7, (long) quarters), SocialTime.MINUTES);
		}
		int minutes = d.minutes > 0 ? d.minutes : rule.duration();
		long start = SocialTime.at(day, minute);
		long end = start + ticks(minutes);
		return new Scheduled(ids.getAsInt(), d.kind, day, start, end, List.copyOf(d.principals), List.copyOf(d.family), d.plot, d.seed, d.args,
			Scheduled.Phase.SCHEDULED);
	}

	/** One event outside the day's plan (a pillory after a trial, a scene staged now), on its rule's times unless the offer sets them. */
	public static Scheduled single(long town, long day, Offer o, EventRule rule, int id) {
		long seed = seed(town, day, o.kind()) ^ id;
		Draft d = new Draft(o.kind(), seed, o, 0);
		return schedule(d, rule, day, () -> id);
	}

	/** A draft of an event already made (a forced one), for the filler to complete. */
	public static Draft draft(Scheduled e) {
		return new Draft(e.kind(), e.seed(), new Offer(e.kind(), -1, -1, e.principals(), e.family(), e.plot(), e.args()), 0);
	}

	/** The event with what the filler put in its draft (plot, people, facts). */
	public static Scheduled withDraft(Scheduled e, Draft d) {
		return new Scheduled(e.id(), e.kind(), e.day(), e.start(), e.end(), List.copyOf(d.principals), List.copyOf(d.family), d.plot, e.seed(), d.args,
			e.phase());
	}

	/** Game ticks in so many minutes of the day. */
	public static long ticks(int minutes) {
		return (long) minutes * SocialTime.DAY_TICKS / SocialTime.MINUTES;
	}

	static long seed(long town, long day, EventKind kind) {
		return TownCalendar.mix(town * 0x9E3779B97F4A7C15L + day * 0x632BE59BD9B4E019L + kind.ordinal());
	}

	/** The tier's place in the rules' chance lists (for tests). */
	public static int tierIndex(SettlementTier t) {
		return t.ordinal();
	}

	/** The first event of a kind in a list, or null. */
	public static @Nullable Scheduled first(List<Scheduled> events, EventKind kind) {
		for (Scheduled e : events) {
			if (e.kind() == kind) {
				return e;
			}
		}
		return null;
	}
}
