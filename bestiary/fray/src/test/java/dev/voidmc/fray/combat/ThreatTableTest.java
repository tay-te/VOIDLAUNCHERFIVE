package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.combat.math.ThreatTable;
import org.junit.jupiter.api.Test;

/** Threat (CONTRACTS.md §8): eight entries, top, taunts, decay. */
class ThreatTableTest {
	@Test
	void topIsTheMostThreat() {
		ThreatTable t = new ThreatTable();
		t.add(1, 10, 0);
		t.add(2, 25, 0);
		t.add(1, 20, 1);
		assertEquals(1, t.top(1), "30 beats 25");
		assertEquals(30, t.of(1), 1e-4);
	}

	@Test
	void atMostEightEntriesTheLowestGoes() {
		ThreatTable t = new ThreatTable();
		for (int i = 0; i < 8; i++) {
			t.add(i, 10 + i, 0);
		}
		t.add(100, 5, 0);
		assertEquals(8, t.size);
		assertEquals(-1, t.indexOf(100), "too little to displace anyone");
		t.add(101, 50, 0);
		assertEquals(8, t.size);
		assertEquals(-1, t.indexOf(0), "the lowest (10) was replaced");
		assertEquals(101, t.top(0));
	}

	@Test
	void tauntHoldsTopForItsTicks() {
		ThreatTable t = new ThreatTable();
		t.add(1, 100, 0);
		t.add(2, 10, 0);
		t.taunt(2, 40, 0);
		assertEquals(2, t.top(10));
		assertTrue(t.of(2) >= 120, "top + 20 %");
		t.add(1, 500, 20);
		assertEquals(2, t.top(39), "still taunted");
		assertEquals(1, t.top(41), "taunt over");
	}

	@Test
	void decaysAfterFiveSeconds() {
		ThreatTable t = new ThreatTable();
		t.add(1, 10, 0);
		for (long n = 1; n < 100; n++) {
			t.tick(n);
		}
		assertEquals(10, t.of(1), 1e-4);
		for (long n = 100; n < 120; n++) {
			t.tick(n);
		}
		assertTrue(t.of(1) < 9.6F && t.of(1) > 9.3F, "about 5 % a second: " + t.of(1));
		for (long n = 120; n < 10000; n++) {
			t.tick(n);
		}
		assertEquals(0, t.size, "forgotten");
	}
}
