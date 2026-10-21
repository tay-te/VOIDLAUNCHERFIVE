package dev.voidmc.hearth.client.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The contest line (F6, 2026-10-21): open lines follow the server; a result holds six seconds, then the line folds away. */
class ContestHudTest {
	@Test
	void aResultHoldsSixSecondsThenFolds() {
		ContestHud.receive("{\"kind\":\"archery\",\"state\":\"open\",\"score\":7}", 1000);
		assertEquals(7, ContestHud.num(ContestHud.current(1000), "score"));
		ContestHud.receive("", 1500);
		assertNull(ContestHud.current(1500)); // an open line goes as soon as the server says so
		ContestHud.receive("{\"kind\":\"archery\",\"state\":\"won\",\"score\":41}", 2000);
		ContestHud.receive("", 2500); // the next look finds the contest over: the result stays
		assertNotNull(ContestHud.current(2000 + ContestHud.HOLD_MS - 1));
		assertNull(ContestHud.current(2000 + ContestHud.HOLD_MS));
		ContestHud.receive("not json", 9000);
		assertNull(ContestHud.current(9000));
	}
}
