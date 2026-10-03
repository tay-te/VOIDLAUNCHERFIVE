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
}
