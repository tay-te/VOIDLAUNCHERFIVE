package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.caravan.Journey;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.routine.Activity;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Schedule;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The days of the phase 3 trades, the routine's priorities, and caravans' progress along the road. */
class LivingWorldScheduleTest {
	private static final Set<Trait> NONE = EnumSet.noneOf(Trait.class);

	@BeforeAll
	static void bootstrap() {
		// Level (for the caravan's dimension key) pulls in the registries
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void everyTradeKeepsAWholeDay() {
		for (JobType job : JobType.values()) {
			for (long seed = 0; seed < 12; seed++) {
				Schedule s = Schedule.of(job, AgeStage.ADULT, NONE, Culture.TEMPERATE, seed * 7919, 3);
				boolean sleeps = false;
				boolean works = job == JobType.NONE;
				for (Schedule.Block b : s.blocks()) {
					sleeps |= b.activity() == Activity.SLEEP;
					works |= b.activity() == Activity.WORK;
				}
				assertTrue(sleeps, job + " never sleeps: " + s);
				assertTrue(works, job + " never works: " + s);
			}
		}
	}

	@Test
	void theLordHoldsCourtInTheMorningAndKeepsLateHours() {
		Schedule s = Schedule.of(JobType.LORD, AgeStage.ADULT, NONE, Culture.TEMPERATE, 5, 1);
		assertEquals(Activity.WORK, s.at(10 * 60).activity(), s.toString());
		assertEquals(Activity.EAT, s.at(13 * 60).activity(), s.toString());
		assertFalse(s.at(22 * 60).activity() == Activity.SLEEP, s.toString());
		assertEquals(Activity.SLEEP, s.at(7 * 60).activity(), "the lord is abed while the bakers work: " + s);
	}

	@Test
	void entertainersWorkTheEvening() {
		Schedule s = Schedule.of(JobType.ENTERTAINER, AgeStage.ADULT, NONE, Culture.TEMPERATE, 11, 1);
		assertEquals(Activity.WORK, s.at(21 * 60).activity(), s.toString());
		assertEquals(Activity.SLEEP, s.at(7 * 60).activity(), s.toString());
	}

	@Test
	void someJailersKeepTheNightWatch() {
		int night = 0;
		for (long seed = 0; seed < 40; seed++) {
			Schedule s = Schedule.of(JobType.JAILER, AgeStage.ADULT, NONE, Culture.TEMPERATE, seed << 12, 1);
			if (s.at(2 * 60).activity() == Activity.WORK) {
				night++;
			}
		}
		assertTrue(night > 5 && night < 35, "about half the jailers keep the night: " + night);
	}

	@Test
	void routinePrioritiesSitBelowReactions() {
		assertTrue(FolkBrain.routinePriority(Activity.WORK) < 100);
		assertTrue(FolkBrain.routinePriority(Activity.SLEEP) < 100);
		assertTrue(FolkBrain.routinePriority(Activity.LEISURE) < FolkBrain.routinePriority(Activity.WORK));
		assertTrue(FolkBrain.routinePriority(Activity.FLEE) >= 200 && FolkBrain.routinePriority(Activity.FLEE) < 300);
	}

	@Test
	void caravansMoveAlongTheRoadAtTheirPace() {
		Journey j = new Journey(1, Level.OVERWORLD, 1, 2, "Eastby", new BlockPos(0, 70, 0), new BlockPos(1000, 70, 0), new int[]{10, 20}, 1000);
		assertEquals(1000, j.length(), 1e-9);
		assertEquals(0.0, j.progress(1000, 2.0), 1e-9);
		// 2 blocks a second for 100 seconds: a fifth of the way
		assertEquals(0.2, j.progress(1000 + 20 * 100, 2.0), 1e-9);
		assertEquals(1.0, j.progress(1000 + 20 * 10_000, 2.0), 1e-9);
		assertEquals(new BlockPos(500, 70, 0), j.at(0.5));
		assertEquals(30, j.units());
	}
}
