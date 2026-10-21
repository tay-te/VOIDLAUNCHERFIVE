package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Leisure;
import dev.voidmc.hearth.npc.routine.Schedule;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleTest {
	private static final Set<Trait> NONE = EnumSet.noneOf(Trait.class);

	@Test
	void dayTimeMapsToClock() {
		assertEquals(6 * 60, Schedule.minuteOfDay(0));
		assertEquals(12 * 60, Schedule.minuteOfDay(6000));
		assertEquals(0, Schedule.minuteOfDay(18000));
		assertEquals(6 * 60, Schedule.minuteOfDay(24000 * 5L));
		for (int m = 0; m < Schedule.DAY; m += 37) {
			assertEquals(m, Schedule.minuteOfDay(Schedule.dayTimeOf(m)), 1);
		}
	}

	@Test
	void ordinaryWorkerKeepsADay() {
		Schedule s = Schedule.of(JobType.SMITH, AgeStage.ADULT, NONE, Culture.TEMPERATE, 42, 1);
		assertEquals(Activity.SLEEP, s.at(2 * 60).activity(), s.toString());
		assertEquals(Activity.WORK, s.at(10 * 60).activity(), s.toString());
		assertEquals(Activity.EAT, s.at(12 * 60 + 15).activity(), s.toString());
		assertEquals(Activity.WORK, s.at(15 * 60).activity(), s.toString());
		assertEquals(Activity.SLEEP, s.at(23 * 60 + 30).activity(), s.toString());
	}

	@Test
	void sameResidentSameDaySameSchedule() {
		Set<Trait> t = EnumSet.of(Trait.SOCIABLE, Trait.CURIOUS);
		assertEquals(Schedule.of(JobType.FARMER, AgeStage.ADULT, t, Culture.KARST, 7, 3).toString(),
			Schedule.of(JobType.FARMER, AgeStage.ADULT, t, Culture.KARST, 7, 3).toString());
	}

	@Test
	void freeTimeVariesFromDayToDay() {
		Set<String> days = new HashSet<>();
		for (int day = 0; day < 20; day++) {
			days.add(Schedule.of(JobType.CARPENTER, AgeStage.ADULT, NONE, Culture.TEMPERATE, 99, day).toString());
		}
		assertTrue(days.size() > 3, "free time should not be the same every day");
	}

	@Test
	void bakersRiseEarly() {
		Schedule s = Schedule.of(JobType.BAKER, AgeStage.ADULT, NONE, Culture.TEMPERATE, 1, 0);
		assertEquals(Activity.WORK, s.at(5 * 60).activity(), s.toString());
		assertNotEquals(Activity.WORK, s.at(16 * 60).activity(), s.toString());
	}

	@Test
	void industriousWakeBeforeLazy() {
		Schedule busy = Schedule.of(JobType.MASON, AgeStage.ADULT, EnumSet.of(Trait.INDUSTRIOUS), Culture.TEMPERATE, 5, 0);
		Schedule idle = Schedule.of(JobType.MASON, AgeStage.ADULT, EnumSet.of(Trait.LAZY), Culture.TEMPERATE, 5, 0);
		assertTrue(firstStart(busy, Activity.WAKE) < firstStart(idle, Activity.WAKE));
	}

	@Test
	void childrenPlayAndSleepEarly() {
		Schedule s = Schedule.of(JobType.NONE, AgeStage.CHILD, NONE, Culture.TEMPERATE, 3, 0);
		assertEquals(Activity.LEISURE, s.at(10 * 60).activity());
		assertEquals(Activity.SLEEP, s.at(20 * 60 + 45).activity(), s.toString());
		int play = 0;
		for (int day = 0; day < 10; day++) {
			Schedule d = Schedule.of(JobType.NONE, AgeStage.CHILD, NONE, Culture.TEMPERATE, 3, day);
			if (d.at(10 * 60).leisure() == Leisure.PLAY) {
				play++;
			}
		}
		assertTrue(play >= 5, "children mostly play");
	}

	@Test
	void someGuardsKeepTheNightWatch() {
		int night = 0;
		for (long seed = 0; seed < 300; seed++) {
			Schedule s = Schedule.of(JobType.GUARD, AgeStage.ADULT, NONE, Culture.NORTHERN, seed << 12 | seed, 0);
			if (s.at(1 * 60).activity() == Activity.WORK) {
				night++;
				assertEquals(Activity.SLEEP, s.at(11 * 60).activity(), s.toString());
			}
		}
		assertTrue(night > 50 && night < 150, "about a third of guards watch at night: " + night);
	}

	@Test
	void theWatchIsNeverEmpty() {
		// Ten guards of any temper: at every quarter hour of the day some of them are on duty, day watch or night watch.
		for (Set<Trait> traits : List.of(NONE, EnumSet.of(Trait.LAZY), EnumSet.of(Trait.INDUSTRIOUS))) {
			for (Culture culture : List.of(Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT)) {
				for (int m = 0; m < Schedule.DAY; m += 15) {
					int onDuty = 0;
					for (long seed = 0; seed < 10; seed++) {
						Schedule s = Schedule.of(JobType.GUARD, AgeStage.ADULT, traits, culture, seed << 12 | seed * 7, 3);
						onDuty += s.at(m).activity() == Activity.WORK ? 1 : 0;
					}
					assertTrue(onDuty >= 1, "no guard on duty at " + m / 60 + ":" + m % 60 + " (" + traits + ", " + culture + ")");
				}
			}
		}
	}

	@Test
	void siestaCulturesRestAfterLunch() {
		Schedule s = Schedule.of(JobType.CARPENTER, AgeStage.ADULT, NONE, Culture.MEDITERRANEAN, 11, 0);
		assertEquals(Activity.HOME, s.at(13 * 60 + 30).activity(), s.toString());
		assertNotEquals(Activity.SLEEP, s.at(22 * 60).activity(), s.toString());
	}

	@Test
	void everyScheduleCoversTheWholeDay() {
		for (JobType job : JobType.values()) {
			for (AgeStage age : AgeStage.values()) {
				for (Culture culture : Culture.values()) {
					for (long seed = 0; seed < 8; seed++) {
						Set<Trait> traits = seed % 2 == 0 ? EnumSet.of(Trait.LAZY, Trait.SOCIABLE) : EnumSet.of(Trait.INDUSTRIOUS, Trait.PIOUS);
						Schedule s = Schedule.of(job, age, traits, culture, seed * 7919, seed);
						int last = -1;
						for (Schedule.Block b : s.blocks()) {
							assertTrue(b.start() > last && b.start() < Schedule.DAY, "blocks sorted and in range: " + s);
							last = b.start();
						}
						boolean sleeps = s.blocks().stream().anyMatch(b -> b.activity() == Activity.SLEEP);
						assertTrue(sleeps, "everyone sleeps: " + s);
					}
				}
			}
		}
	}

	private static int firstStart(Schedule s, Activity a) {
		return s.blocks().stream().filter(b -> b.activity() == a).mapToInt(Schedule.Block::start).min().orElse(-1);
	}

	// ---- the week's rhythm (docs/LIVING_WORLD_2.md §2.8): the rest day, the watch, the tavern night

	private static Schedule rested(JobType job, AgeStage age, Culture culture, long seed, boolean rest, boolean drinker) {
		dev.voidmc.hearth.social.rhythm.RhythmData data = dev.voidmc.hearth.social.rhythm.RhythmData.of(culture);
		Schedule base = Schedule.of(job, age, NONE, culture, seed, 4);
		List<dev.voidmc.hearth.npc.routine.ScheduleOverrides.Window> w = dev.voidmc.hearth.social.rhythm.RhythmSchedule.windows(job, age, List.of(), data,
			rest, drinker, false, seed, 4);
		// as ScheduleOverrides.apply: no windows, the day unchanged
		return w.isEmpty() ? base : dev.voidmc.hearth.npc.routine.ScheduleOverrides.overlay(base, w);
	}

	@Test
	void noWorkOnTheRestDayButTheListedTrades() {
		for (Culture culture : Culture.values()) {
			Set<JobType> listed = dev.voidmc.hearth.social.rhythm.RhythmData.of(culture).restDayWork();
			for (JobType job : JobType.values()) {
				for (long seed = 1; seed <= 6; seed++) {
					Schedule rest = rested(job, AgeStage.ADULT, culture, seed * 7919 + (seed << 12), true, false);
					Schedule plain = Schedule.of(job, AgeStage.ADULT, NONE, culture, seed * 7919 + (seed << 12), 4);
					for (int m = 8 * 60; m < 17 * 60; m += 10) {
						if (listed.contains(job)) {
							assertEquals(plain.at(m).activity(), rest.at(m).activity(), job + " keeps its hours on the rest day: " + rest);
						} else {
							assertNotEquals(Activity.WORK, rest.at(m).activity(), job + " " + culture + " works on the rest day at " + m + ": " + rest);
						}
					}
					// before eight the bakers' bread and the herders' milking stand as on any day
					for (int m = 0; m < 8 * 60; m += 15) {
						assertEquals(plain.at(m).activity(), rest.at(m).activity(), job + " early hours on the rest day");
					}
				}
			}
		}
	}

	@Test
	void theRestDayChangesNothingOnOtherDays() {
		for (JobType job : JobType.values()) {
			Schedule plain = Schedule.of(job, AgeStage.ADULT, NONE, Culture.TEMPERATE, 77, 4);
			assertEquals(plain.toString(), rested(job, AgeStage.ADULT, Culture.TEMPERATE, 77, false, false).toString());
		}
	}

	@Test
	void theNightWatchKeepsTheDarkHours() {
		int watch = 0;
		for (long seed = 0; seed < 60; seed++) {
			Schedule s = Schedule.of(JobType.GUARD, AgeStage.ADULT, NONE, Culture.TEMPERATE, seed << 12 | seed, 4);
			if (s.at(23 * 60).activity() != Activity.WORK) {
				continue;
			}
			watch++;
			dev.voidmc.hearth.social.rhythm.RhythmData.Watch hours = dev.voidmc.hearth.social.rhythm.RhythmData.of(Culture.TEMPERATE).watch();
			for (int m = 0; m < Schedule.DAY; m += 10) {
				if (hours.covers(m)) {
					assertEquals(Activity.WORK, s.at(m).activity(), "the night watch is on duty at " + m + ": " + s);
				}
			}
			// and the rest day does not stand the watch down
			assertEquals(s.toString(), rested(JobType.GUARD, AgeStage.ADULT, Culture.TEMPERATE, seed << 12 | seed, true, false).toString(),
				"the watch works the rest day");
		}
		assertTrue(watch > 10, "about a third of the guards keep the night watch");
	}

	@Test
	void tonightsDrinkersStayUntilClosing() {
		dev.voidmc.hearth.social.rhythm.RhythmData data = dev.voidmc.hearth.social.rhythm.RhythmData.of(Culture.TEMPERATE);
		Schedule s = rested(JobType.CARPENTER, AgeStage.ADULT, Culture.TEMPERATE, 5, false, true);
		for (int m = data.tavern().open(); m < data.tavern().close(); m += 10) {
			assertEquals(Activity.LEISURE, s.at(m).activity(), "at the tavern at " + m + ": " + s);
			assertEquals(Leisure.INN, s.at(m).leisure(), s.toString());
		}
		assertEquals(Activity.SLEEP, s.at(2 * 60).activity(), "and home to bed after: " + s);
	}
}
