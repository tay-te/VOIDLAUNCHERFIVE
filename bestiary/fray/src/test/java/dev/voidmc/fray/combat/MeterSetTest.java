package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.combat.math.MeterSet;
import org.junit.jupiter.api.Test;

/** Build-up meters (CONTRACTS.md §6.1) and the Wet / Oiled interplay. */
class MeterSetTest {
	@Test
	void fillsThenProcsAndEmpties() {
		MeterSet m = new MeterSet();
		float th = MeterSet.threshold(0);
		assertEquals(100, th);
		assertFalse(m.add(StatusType.POISON, 60, th, 0));
		assertEquals(0.6F, m.fraction(StatusType.POISON), 1e-4);
		assertTrue(m.add(StatusType.POISON, 40, th, 1), "full: procs");
		assertEquals(0, m.fraction(StatusType.POISON), "and empties");
	}

	@Test
	void resistanceGrowsTheMeter() {
		assertEquals(200, MeterSet.threshold(1));
		assertEquals(400, MeterSet.threshold(3), "a boss-like ×4");
		assertEquals(10, MeterSet.threshold(-0.9F), 1e-3);
	}

	@Test
	void drainsTenPercentASecondAfterTwoSeconds() {
		MeterSet m = new MeterSet();
		m.add(StatusType.BLEED, 50, 100, 0);
		for (long t = 1; t < 40; t++) {
			assertFalse(m.tick(t));
		}
		assertEquals(0.5F, m.fraction(StatusType.BLEED), 1e-4);
		for (long t = 40; t < 60; t++) {
			m.tick(t);
		}
		assertEquals(0.4F, m.fraction(StatusType.BLEED), 1e-3, "10 % of its size a second");
		assertTrue(m.tick(60));
	}

	@Test
	void chillStacksPerThird() {
		MeterSet m = new MeterSet();
		m.add(StatusType.CHILL, 30, 100, 0);
		assertEquals(0, m.chillStacks());
		m.add(StatusType.CHILL, 5, 100, 0);
		assertEquals(1, m.chillStacks());
		m.add(StatusType.CHILL, 35, 100, 0);
		assertEquals(2, m.chillStacks());
	}

	@Test
	void wetAndOiled() {
		assertEquals(1.5F, MeterSet.reactionScale(StatusType.SHOCK, true, false));
		assertEquals(1.5F, MeterSet.reactionScale(StatusType.CHILL, true, false));
		assertEquals(0.5F, MeterSet.reactionScale(StatusType.BURN, true, false));
		assertEquals(2.0F, MeterSet.reactionScale(StatusType.BURN, false, true));
		assertEquals(1.0F, MeterSet.reactionScale(StatusType.BURN, true, true), "wet halves, oil doubles");
		assertEquals(1.0F, MeterSet.reactionScale(StatusType.POISON, true, true));
	}

	@Test
	void wetShockProcsSooner() {
		MeterSet dry = new MeterSet(), wet = new MeterSet();
		int dryHits = 0, wetHits = 0;
		while (!dry.add(StatusType.SHOCK, 20 * MeterSet.reactionScale(StatusType.SHOCK, false, false), 100, 0)) {
			dryHits++;
		}
		while (!wet.add(StatusType.SHOCK, 20 * MeterSet.reactionScale(StatusType.SHOCK, true, false), 100, 0)) {
			wetHits++;
		}
		assertEquals(4, dryHits);
		assertEquals(3, wetHits);
	}

	@Test
	void networkBytes() {
		MeterSet m = new MeterSet();
		m.add(StatusType.POISON, 80, 100, 0);
		m.add(StatusType.BLEED, 35, 100, 0);
		byte[] b = m.bytes();
		assertEquals(7, b.length);
		assertEquals(204, b[StatusType.POISON.meterIndex()] & 0xFF);
		assertEquals(89, b[StatusType.BLEED.meterIndex()] & 0xFF);
	}
}
