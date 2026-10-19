package dev.voidmc.hearth.npc.look;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.person.Office;
import org.junit.jupiter.api.Test;

class FolkLookKeyTest {
	@Test
	void aLookComesBackFromItsKey() {
		for (int appearance : new int[] {0, 12345, -98765, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			for (Office office : Office.values()) {
				FolkLook look = new FolkLook(appearance, Culture.values()[Culture.values().length - 1], JobType.values()[JobType.values().length - 1],
					AgeStage.values()[AgeStage.values().length - 1], true, Station.values()[Station.values().length - 1], office);
				assertEquals(look, FolkLook.fromKey(look.key()));
			}
		}
		FolkLook plain = new FolkLook(7, Culture.TEMPERATE, JobType.NONE, AgeStage.ADULT, false);
		assertEquals(plain, FolkLook.fromKey(plain.key()));
	}
}
