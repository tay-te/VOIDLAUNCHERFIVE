package dev.voidmc.hearth.npc.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Trait;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link ScheduleOverrides#overlay} (requests/lwA2.md, 1): the merge gives the very day the walk of every minute gave, and
 * costs a microsecond or two, not tens.
 */
class ScheduleOverlayTest {
	/** The former overlay: each minute of the day owned by the earliest window over it, else the base. */
	static Schedule minuteByMinute(Schedule base, List<ScheduleOverrides.Window> windows) {
		int[] owner = new int[Schedule.DAY];
		Arrays.fill(owner, -1);
		for (int w = windows.size() - 1; w >= 0; w--) {
			ScheduleOverrides.Window win = windows.get(w);
			int start = Math.floorMod(win.start(), Schedule.DAY);
			int len = Math.floorMod(win.end() - win.start(), Schedule.DAY);
			for (int i = 0; i < len; i++) {
				owner[(start + i) % Schedule.DAY] = w;
			}
		}
		List<Schedule.Block> out = new ArrayList<>();
		Object last = null;
		for (int m = 0; m < Schedule.DAY; m++) {
			Object here;
			Schedule.Block block;
			if (owner[m] >= 0) {
				ScheduleOverrides.Window win = windows.get(owner[m]);
				here = win;
				block = new Schedule.Block(m, win.activity(), win.leisure(), win.where());
			} else {
				Schedule.Block b = base.at(m);
				here = b;
				block = new Schedule.Block(m, b.activity(), b.leisure(), b.where());
			}
			if (here != last) {
				out.add(block);
				last = here;
			}
		}
		return Schedule.ofBlocks(out);
	}

	static List<ScheduleOverrides.Window> randomWindows(Random r) {
		List<ScheduleOverrides.Window> out = new ArrayList<>();
		int n = r.nextInt(6);
		for (int i = 0; i < n; i++) {
			int start = r.nextInt(Schedule.DAY + 200) - 100;
			int end = r.nextInt(5) == 0 ? start : start + r.nextInt(Schedule.DAY);
			Activity a = Activity.values()[r.nextInt(Activity.values().length)];
			Leisure l = r.nextBoolean() ? null : Leisure.values()[r.nextInt(Leisure.values().length)];
			Schedule.Where where = r.nextBoolean() ? null : Schedule.Where.values()[r.nextInt(3)];
			out.add(new ScheduleOverrides.Window(start, end, a, l, where));
		}
		return out;
	}

	@Test
	void theMergeIsTheMinuteWalk() {
		Random r = new Random(20261021);
		JobType[] jobs = JobType.values();
		for (int i = 0; i < 4000; i++) {
			JobType job = jobs[r.nextInt(jobs.length)];
			AgeStage age = AgeStage.values()[r.nextInt(AgeStage.values().length)];
			EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
			for (Trait t : Trait.values()) {
				if (r.nextInt(5) == 0) {
					traits.add(t);
				}
			}
			Schedule base = Schedule.of(job, age, traits, Culture.values()[r.nextInt(Culture.values().length)], r.nextLong(), r.nextInt(400));
			List<ScheduleOverrides.Window> ws = randomWindows(r);
			Schedule want = minuteByMinute(base, ws);
			Schedule got = ScheduleOverrides.overlay(base, ws);
			for (int m = 0; m < Schedule.DAY; m++) {
				Schedule.Block a = want.at(m);
				Schedule.Block b = got.at(m);
				assertEquals(a.activity(), b.activity(), "case " + i + " minute " + m);
				assertEquals(a.leisure(), b.leisure(), "case " + i + " minute " + m);
				assertEquals(a.where(), b.where(), "case " + i + " minute " + m);
				assertEquals(want.minutesInto(m), got.minutesInto(m), "case " + i + " minute " + m + " (block starts)");
			}
			assertEquals(want.blocks().size(), got.blocks().size(), "case " + i + " blocks");
		}
	}

	@Test
	void itCostsAMicrosecondOrTwo() {
		Schedule base = Schedule.of(JobType.SMITH, AgeStage.ADULT, EnumSet.noneOf(Trait.class), Culture.TEMPERATE, 5, 3);
		List<ScheduleOverrides.Window> ws = List.of(new ScheduleOverrides.Window(8 * 60, 12 * 60, Activity.LEISURE, Leisure.PLAZA, null),
			new ScheduleOverrides.Window(12 * 60, 13 * 60, Activity.EAT, null, Schedule.Where.HOME),
			new ScheduleOverrides.Window(13 * 60, 17 * 60, Activity.LEISURE, Leisure.VISIT, null),
			new ScheduleOverrides.Window(18 * 60 + 30, 23 * 60 + 50, Activity.LEISURE, Leisure.INN, Schedule.Where.INN));
		int sink = 0;
		for (int i = 0; i < 50_000; i++) {
			sink += ScheduleOverrides.overlay(base, ws).blocks().size();
		}
		int n = 200_000;
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			sink += ScheduleOverrides.overlay(base, ws).blocks().size();
		}
		double us = (System.nanoTime() - t0) / 1e3 / n;
		long t1 = System.nanoTime();
		for (int i = 0; i < 2000; i++) {
			sink += minuteByMinute(base, ws).blocks().size();
		}
		double before = (System.nanoTime() - t1) / 1e3 / 2000;
		System.out.printf(java.util.Locale.ROOT, "schedule overlay: %.2f us a resident's day (the minute walk %.1f us)%n", us, before);
		assertTrue(sink > 0);
		assertTrue(us < 8, "overlay " + us + " us");
	}
}
