package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.Leisure;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A named character's windows laid over their day (docs/LIVING_WORLD_2.md §11.2): the rest of the day is unchanged. */
class ScheduleOverridesTest {
	private static Schedule day() {
		return Schedule.of(JobType.SMITH, AgeStage.ADULT, EnumSet.noneOf(Trait.class), Culture.TEMPERATE, 42, 7);
	}

	@Test
	void aWindowTakesItsStretchAndTheDayResumesAfter() {
		Schedule base = day();
		Schedule over = ScheduleOverrides.overlay(base, List.of(new ScheduleOverrides.Window(14 * 60, 15 * 60, Activity.LEISURE, Leisure.VISIT,
			Schedule.Where.HOME)));
		for (int m = 0; m < Schedule.DAY; m++) {
			Schedule.Block b = over.at(m);
			if (m >= 14 * 60 && m < 15 * 60) {
				assertEquals(Activity.LEISURE, b.activity(), "minute " + m);
				assertEquals(Leisure.VISIT, b.leisure());
			} else {
				assertEquals(base.at(m).activity(), b.activity(), "minute " + m);
				assertEquals(base.at(m).leisure(), b.leisure(), "minute " + m);
			}
		}
	}

	@Test
	void aWindowMayRunPastMidnight() {
		Schedule base = day();
		Schedule over = ScheduleOverrides.overlay(base, List.of(new ScheduleOverrides.Window(23 * 60, 60, Activity.WORK, null, Schedule.Where.WORK)));
		assertEquals(Activity.WORK, over.at(23 * 60 + 30).activity());
		assertEquals(Activity.WORK, over.at(30).activity());
		assertEquals(base.at(2 * 60).activity(), over.at(2 * 60).activity());
	}

	@Test
	void withNoSourcesTheDayIsTheSame() {
		Schedule base = day();
		assertSame(base, ScheduleOverrides.apply(base, null, null, 7));
	}
}
