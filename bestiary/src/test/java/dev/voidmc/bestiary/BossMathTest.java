package dev.voidmc.bestiary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.bestiary.boss.BossMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The encounter rules' maths (BESTIARY.md §8): arena, reset, party health, personal loot, the red rule, the watchdog. */
class BossMathTest {
	@Test
	void arenaCylinder() {
		assertTrue(BossMath.inCylinder(10.5, 64, 0.5, 0.5, 64, 0.5, 10, 4, 10), "on the rim");
		assertFalse(BossMath.inCylinder(10.6, 64, 0.5, 0.5, 64, 0.5, 10, 4, 10), "just outside");
		assertTrue(BossMath.inCylinder(3, 73.9, 3, 0.5, 64, 0.5, 10, 4, 10), "up a pillar, inside the band");
		assertFalse(BossMath.inCylinder(3, 75, 3, 0.5, 64, 0.5, 10, 4, 10), "above the band (a sky tower is outside)");
		assertFalse(BossMath.inCylinder(3, 59, 3, 0.5, 64, 0.5, 10, 4, 10), "below it (a tunnel under the arena)");
	}

	@Test
	void resetAfterAMinuteEmpty() {
		assertFalse(BossMath.resetDue(0, 5000, 1200), "someone is in the arena");
		assertFalse(BossMath.resetDue(4000, 5199, 1200), "59.95 s empty");
		assertTrue(BossMath.resetDue(4000, 5200, 1200), "60 s empty");
	}

	@Test
	void partyHealth() {
		assertEquals(1.0F, BossMath.partyHealth(1), 1e-6);
		assertEquals(1.6F, BossMath.partyHealth(2), 1e-6);
		assertEquals(2.8F, BossMath.partyHealth(4), 1e-6);
		assertEquals(1.0F, BossMath.partyHealth(0), 1e-6, "never below solo");
		// The share is kept: a knight at 600/900 scaled for two players is at 960/1440.
		assertEquals(960F, BossMath.keepShare(600, 900, 1440), 1e-3);
		assertEquals(0.32F, BossMath.keepShare(0.2F, 900, 1440), 1e-5, "a sliver keeps its share, never 0");
	}

	@Test
	void personalLoot() {
		assertTrue(BossMath.earnsLoot(90, 900, 0), "10 % of the damage");
		assertFalse(BossMath.earnsLoot(89, 900, 599), "under 10 % and under 30 s");
		assertTrue(BossMath.earnsLoot(0, 900, 600), "30 s in the arena");
		assertFalse(BossMath.earnsLoot(0, 0, 10), "nobody hit it, 0.5 s present");
	}

	@Test
	void atMostOneRedInAnyThree() {
		assertTrue(BossMath.redAllowed(List.of()));
		assertFalse(BossMath.redAllowed(List.of("red")));
		assertFalse(BossMath.redAllowed(List.of("red", "white")));
		assertTrue(BossMath.redAllowed(List.of("red", "white", "orange")));
		// A random fight that obeys the rule never has two reds in a window of three.
		Random r = new Random(7);
		List<String> seq = new ArrayList<>();
		String[] colours = {"white", "white", "orange", "red", "purple"};
		for (int i = 0; i < 5000; i++) {
			String c = colours[r.nextInt(colours.length)];
			if (c.equals("red") && !BossMath.redAllowed(seq.subList(Math.max(0, seq.size() - 3), seq.size()))) {
				c = "white";
			}
			seq.add(c);
		}
		for (int i = 2; i < seq.size(); i++) {
			int reds = 0;
			for (int k = i - 2; k <= i; k++) {
				reds += seq.get(k).equals("red") ? 1 : 0;
			}
			assertTrue(reds <= 1, "two reds in moves " + (i - 2) + ".." + i);
		}
	}

	@Test
	void watchdog() {
		assertFalse(BossMath.stuck(0.2, 1000, 1150, 200, 1.0), "not long enough yet");
		assertTrue(BossMath.stuck(0.2, 1000, 1200, 200, 1.0), "10 s without a block of progress");
		assertFalse(BossMath.stuck(3.0, 1000, 1200, 200, 1.0), "it moved");
	}
}
