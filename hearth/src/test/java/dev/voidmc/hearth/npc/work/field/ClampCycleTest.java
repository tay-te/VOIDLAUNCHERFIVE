package dev.voidmc.hearth.npc.work.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The charcoal clamp's burn and the logging camp's timber, from the books (docs/LIVING_WORLD_2.md §6.1–6.2). */
class ClampCycleTest {
	@BeforeAll
	static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test
	void aBurnIsADayBuildingTheMiddleDaysSmoulderingAndADayRaking() {
		Map<ClampCycle.Stage, Integer> days = new EnumMap<>(ClampCycle.Stage.class);
		for (long day = 0; day < 40; day++) {
			days.merge(ClampCycle.stage(true, day, 3, 4), 1, Integer::sum);
		}
		assertEquals(10, days.get(ClampCycle.Stage.BUILDING));
		assertEquals(20, days.get(ClampCycle.Stage.BURNING), "two days in four smouldering");
		assertEquals(10, days.get(ClampCycle.Stage.RAKING));
		// the order: building, then burning, then raking
		int start = 0;
		while (ClampCycle.stage(true, start, 3, 4) != ClampCycle.Stage.BUILDING) {
			start++;
		}
		assertEquals(ClampCycle.Stage.BURNING, ClampCycle.stage(true, start + 1, 3, 4));
		assertEquals(ClampCycle.Stage.BURNING, ClampCycle.stage(true, start + 2, 3, 4));
		assertEquals(ClampCycle.Stage.RAKING, ClampCycle.stage(true, start + 3, 3, 4));
		assertEquals(ClampCycle.Stage.BUILDING, ClampCycle.stage(true, start + 4, 3, 4));
	}

	@Test
	void nobodyBurningIsAColdClampAndTwoKilnsDoNotLightTogether() {
		assertEquals(ClampCycle.Stage.COLD, ClampCycle.stage(false, 12, 3, 4));
		boolean differ = false;
		for (long day = 0; day < 8; day++) {
			differ |= ClampCycle.stage(true, day, 1, 4) != ClampCycle.stage(true, day, 2, 4);
		}
		assertTrue(differ, "each kiln on its own day of the cycle");
	}

	@Test
	void theCampsStackGrowsAndShrinksWithTheLogsInStore() {
		assertEquals(0, Clamps.timberCourses(0));
		assertEquals(1, Clamps.timberCourses(5));
		assertEquals(2, Clamps.timberCourses(100));
		assertEquals(4, Clamps.timberCourses(5000));
		int last = 0;
		for (int logs = 0; logs < 600; logs += 7) {
			int c = Clamps.timberCourses(logs);
			assertTrue(c >= last, "never fewer for more logs");
			last = c;
		}
	}
}
