package dev.voidmc.fray.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The defender-favoured timing on the combat side (ANIM_EVENTS.md §4; the same rule as Fray Motion's DefenderTiming). */
class DefenderGraceTest {
	@Test
	void graceIsTheRoundTripPlusATickCapped() {
		assertEquals(0, Defense.grace(0, Defense.CAP_TICKS), "a local game holds nothing");
		assertEquals(0, Defense.grace(10, Defense.CAP_TICKS));
		assertEquals(2, Defense.grace(50, Defense.CAP_TICKS), "50 ms: 1 tick + 1");
		assertEquals(4, Defense.grace(150, Defense.CAP_TICKS), "150 ms: 3 ticks + 1");
		assertEquals(5, Defense.grace(250, Defense.CAP_TICKS), "250 ms: capped at 5 ticks");
		assertEquals(2, Defense.grace(250, Defense.PVP_CAP_TICKS), "against players: 100 ms");
		assertEquals(0, Defense.grace(250, 0), "0 turns it off");
	}

	@Test
	void stampsAreClamped() {
		long receipt = 1000;
		assertEquals(997, Defense.clampStamp(997, receipt, 150, Long.MIN_VALUE), "in time: the client's tick");
		assertEquals(996, Defense.clampStamp(900, receipt, 150, Long.MIN_VALUE), "older than the grace: clamped");
		assertEquals(1000, Defense.clampStamp(1010, receipt, 150, Long.MIN_VALUE), "future stamps: the receipt");
		assertEquals(998, Defense.clampStamp(997, receipt, 150, 998), "never before the previous stamp");
		assertEquals(1000, Defense.clampStamp(990, receipt, 0, Long.MIN_VALUE), "a local game: no rewind");
	}
}
