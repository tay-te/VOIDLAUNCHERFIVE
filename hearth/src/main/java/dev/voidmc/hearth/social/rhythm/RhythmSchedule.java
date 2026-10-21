package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementCalendar;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Leisure;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How the week changes a resident's day (docs/LIVING_WORLD_2.md §2.3), as windows over their schedule
 * ({@link ScheduleOverrides}, so the embodied and the placed day agree): on the rest day every trade but the listed ones
 * has the day off from 08:00 (the bakers' early bread and the herders' milking are done by then); tonight's drinkers stay
 * at the inn until closing; in a cold culture's winter folk keep in by the fire of an evening. Pure of the day; asked once
 * per resident per day.
 */
public final class RhythmSchedule implements ScheduleOverrides.Source {
	public static final RhythmSchedule INSTANCE = new RhythmSchedule();
	static final int REST_FROM = 8 * 60;
	static final int REST_DINNER = 12 * 60;
	static final int REST_TO = 17 * 60;
	/** Trades never at the tavern of a night (on duty, at the altar, behind the bar, early to the ovens). */
	static final Set<JobType> NOT_DRINKING = EnumSet.of(JobType.GUARD, JobType.JAILER, JobType.PRIEST, JobType.MONK, JobType.LORD, JobType.INNKEEPER,
		JobType.ENTERTAINER, JobType.BAKER, JobType.HERMIT);
	static final List<String> TAVERNS = List.of("commerce/inn", "commerce/tavern");

	private static final Map<Long, Night> NIGHTS = new LinkedHashMap<>();

	/** Tonight's drinkers in one settlement. */
	record Night(long day, int residents, Set<UUID> drinkers) {
	}

	private RhythmSchedule() {
	}

	@Override
	public List<ScheduleOverrides.Window> windows(Settlement settlement, Resident resident, long day) {
		if (settlement == null || resident == null || !Rhythm.config().rhythm().enabled()) {
			return List.of();
		}
		// the town's own day, as its folk behaviours read it (a settlement run ahead of the world clock keeps its own)
		day = Math.max(day, settlement.data(SocialModule.LEDGER).lastDay);
		SettlementCalendar cal = HearthServices.calendar();
		RhythmData data = RhythmData.of(settlement.culture());
		boolean rest = cal.restDay(settlement, day);
		boolean drinker = drinkers(settlement, day).contains(resident.id);
		boolean winter = RhythmCalendar.winter(data, day, SocialModule.config().festivals().yearDays());
		boolean watch = resident.id.equals(appointedWatch(settlement, day));
		return windows(resident.job, resident.age, FolkFacts.traits(resident), data, rest, drinker, winter,
			resident.id.getMostSignificantBits() ^ resident.id.getLeastSignificantBits(), day, watch);
	}

	/** The windows themselves, from facts alone (pure), with no night watch appointed. */
	public static List<ScheduleOverrides.Window> windows(JobType job, AgeStage age, List<String> traits, RhythmData data, boolean restDay, boolean drinker,
		boolean winter, long seed, long day) {
		return windows(job, age, traits, data, restDay, drinker, winter, seed, day, false);
	}

	// ---------------------------------------------------------------- the night watch

	/** Settlement → {day, population, the guard appointed to the night watch (msb, lsb), or 0 0 for none}. */
	private static final Map<Long, long[]> WATCH = new LinkedHashMap<>();

	/**
	 * Whether a resident keeps the night by the schedule's own rule (a third of the guards, half the jailers, by their
	 * seed; {@code Schedule.of}).
	 */
	public static boolean nightBySeed(Resident r) {
		long seed = r.id.getMostSignificantBits() ^ r.id.getLeastSignificantBits();
		return r.age == AgeStage.ADULT && (r.job == JobType.GUARD && Math.floorMod(seed >> 12, 3) == 0 || r.job == JobType.JAILER && Math.floorMod(seed >> 12, 2) == 0);
	}

	/**
	 * The guard who keeps the night watch in a town where the schedule's rule gave the night to none of its guards and
	 * jailers (a town of one or two guards often has none): a town with guards is never without a watch (§2.3). Null when
	 * the rule already gives one, or the town has no guard. The same guard all day, every placement agreeing.
	 */
	public static synchronized @Nullable UUID appointedWatch(Settlement s, long day) {
		long[] c = WATCH.get(s.id());
		if (c != null && c[0] == day && c[1] == s.population()) {
			return c[2] == 0 && c[3] == 0 ? null : new UUID(c[2], c[3]);
		}
		UUID pick = pickWatch(s.residents(), s.id());
		WATCH.put(s.id(), new long[]{day, s.population(), pick == null ? 0 : pick.getMostSignificantBits(), pick == null ? 0 : pick.getLeastSignificantBits()});
		if (WATCH.size() > 512) {
			WATCH.remove(WATCH.keySet().iterator().next());
		}
		return pick;
	}

	/** Pure: the guard to appoint among these residents, or null (one already keeps the night, or there is no guard). */
	public static @Nullable UUID pickWatch(Iterable<Resident> residents, long town) {
		UUID best = null;
		long bestKey = Long.MAX_VALUE;
		for (Resident r : residents) {
			if (nightBySeed(r)) {
				return null;
			}
			if (r.age == AgeStage.ADULT && r.job == JobType.GUARD) {
				long key = TownCalendar.mix(r.id.getLeastSignificantBits() ^ town * 0x9E3779B97F4A7C15L);
				if (best == null || Long.compareUnsigned(key, bestKey) < 0) {
					best = r.id;
					bestKey = key;
				}
			}
		}
		return best;
	}

	/** A night watchman's day as windows (the schedule's own night-guard day): asleep by day, on duty 17:30 to 07:00. */
	static void nightWatch(List<ScheduleOverrides.Window> out, List<String> traits, long daySeed) {
		out.add(new ScheduleOverrides.Window(17 * 60 + 30, 7 * 60, Activity.WORK, null, null));
		out.add(new ScheduleOverrides.Window(7 * 60, 7 * 60 + 45, Activity.EAT, null, Schedule.Where.HOME));
		out.add(new ScheduleOverrides.Window(7 * 60 + 45, 15 * 60 + 15, Activity.SLEEP, null, Schedule.Where.HOME));
		out.add(new ScheduleOverrides.Window(15 * 60 + 15, 15 * 60 + 45, Activity.WAKE, null, Schedule.Where.HOME));
		out.add(new ScheduleOverrides.Window(15 * 60 + 45, 16 * 60 + 30, Activity.EAT, null, Schedule.Where.HOME));
		out.add(new ScheduleOverrides.Window(16 * 60 + 30, 17 * 60 + 30, Activity.LEISURE, restLeisure(traits, daySeed, 0), null));
	}

	/** The windows themselves, from facts alone (pure; the tests call this). */
	public static List<ScheduleOverrides.Window> windows(JobType job, AgeStage age, List<String> traits, RhythmData data, boolean restDay, boolean drinker,
		boolean winter, long seed, long day, boolean nightWatch) {
		List<ScheduleOverrides.Window> out = new ArrayList<>(4);
		if (nightWatch && age == AgeStage.ADULT) {
			// appointed to the night watch: the whole day is a watchman's (the week does not change a guard's duty)
			nightWatch(out, traits, TownCalendar.mix(seed ^ day * 0x9E3779B97F4A7C15L));
			return out;
		}
		if (drinker) {
			// to closing, and the walk home after it
			int end = Math.floorMod(data.tavern().close() + TavernNight.HOMEWARD, Schedule.DAY);
			out.add(new ScheduleOverrides.Window(data.tavern().open(), end, Activity.LEISURE, Leisure.INN, Schedule.Where.INN));
		} else if (winter && age != AgeStage.CHILD && !data.restDayWork().contains(job)) {
			out.add(new ScheduleOverrides.Window(17 * 60 + 30, 20 * 60 + 30, Activity.HOME, null, Schedule.Where.HOME));
		}
		if (restDay && age != AgeStage.CHILD && !data.restDayWork().contains(job)) {
			long daySeed = TownCalendar.mix(seed ^ day * 0x9E3779B97F4A7C15L);
			out.add(new ScheduleOverrides.Window(REST_FROM, REST_DINNER, Activity.LEISURE, restLeisure(traits, daySeed, 0), null));
			out.add(new ScheduleOverrides.Window(REST_DINNER, REST_DINNER + 60, Activity.EAT, null, Schedule.Where.HOME));
			out.add(new ScheduleOverrides.Window(REST_DINNER + 60, REST_TO, Activity.LEISURE, restLeisure(traits, daySeed, 1), null));
		}
		return out;
	}

	/** Where a rest-day morning or afternoon goes: walks, visits, the square, a bench, prayer for the pious. */
	static Leisure restLeisure(List<String> traits, long daySeed, int slot) {
		Leisure[] kinds = {Leisure.PLAZA, Leisure.VISIT, Leisure.WANDER, Leisure.BENCH, Leisure.DOORSTEP, Leisure.VIEWPOINT, Leisure.PRAY};
		int[] w = {3, 3, 2, 1, 1, 1, 0};
		for (String t : traits) {
			switch (t) {
				case "sociable", "gossip" -> {
					w[0] += 3;
					w[1] += 2;
				}
				case "pious" -> w[6] += 4;
				case "curious", "dreamy" -> {
					w[2] += 3;
					w[5] += 2;
				}
				case "lazy" -> w[3] += 3;
				case "shy" -> {
					w[4] += 3;
					w[0] = 1;
				}
				default -> {
				}
			}
		}
		int total = 0;
		for (int x : w) {
			total += x;
		}
		int r = (int) Math.floorMod(TownCalendar.mix(daySeed + slot * 0x632BE59BD9B4E019L), (long) total);
		for (int i = 0; i < w.length; i++) {
			r -= w[i];
			if (r < 0) {
				return kinds[i];
			}
		}
		return Leisure.PLAZA;
	}

	/** The town's taverns and inns that are built. */
	static List<PlotView> taverns(Settlement s) {
		return Venues.built(s, TAVERNS.toArray(String[]::new));
	}

	/** How many drinkers the town's taverns hold tonight: their seats (at least two a table), at most 16. */
	static int room(Settlement s) {
		int room = 0;
		for (PlotView p : taverns(s)) {
			int seats = Venues.inPlot(s.pois(PoiType.SEAT), p).size();
			int tables = Venues.inPlot(s.pois(PoiType.TABLE), p).size();
			room += Math.max(seats, tables * 2);
		}
		return Math.min(16, room);
	}

	/** Tonight's drinkers: adults drawn afresh each night (the sociable and the idle first), as many as the seats hold. */
	public static synchronized Set<UUID> drinkers(Settlement s, long day) {
		Night n = NIGHTS.get(s.id());
		if (n != null && n.day == day && n.residents == s.population()) {
			return n.drinkers;
		}
		Set<UUID> out = pickDrinkers(s.residents(), room(s), day, Rhythm.config().tavern().share());
		NIGHTS.put(s.id(), new Night(day, s.population(), out));
		if (NIGHTS.size() > 512) {
			NIGHTS.remove(NIGHTS.keySet().iterator().next());
		}
		return out;
	}

	/** Pure: who drinks tonight out of these residents, given the room. */
	public static Set<UUID> pickDrinkers(Iterable<Resident> residents, int room, long day, double share) {
		if (room <= 0) {
			return Set.of();
		}
		List<Resident> pool = new ArrayList<>();
		Map<UUID, Double> score = new java.util.HashMap<>();
		for (Resident r : residents) {
			if (r.age != AgeStage.ADULT || NOT_DRINKING.contains(r.job)) {
				continue;
			}
			double roll = TownCalendar.chance(r.id.getMostSignificantBits(), r.id.getLeastSignificantBits(), day * 31 + 7);
			double odds = share;
			if (FolkFacts.has(r, "sociable") || FolkFacts.has(r, "cheerful")) {
				odds *= 2;
			}
			if (FolkFacts.has(r, "lazy")) {
				odds *= 1.5;
			}
			if (FolkFacts.has(r, "shy") || FolkFacts.has(r, "pious")) {
				odds *= 0.4;
			}
			if (roll < odds) {
				pool.add(r);
				score.put(r.id, roll / odds);
			}
		}
		pool.sort(Comparator.comparingDouble(r -> score.get(r.id)));
		Set<UUID> out = new HashSet<>();
		for (int i = 0; i < pool.size() && i < room; i++) {
			out.add(pool.get(i).id);
		}
		return out;
	}

	static synchronized void clear() {
		NIGHTS.clear();
		WATCH.clear();
	}
}
