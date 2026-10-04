package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.person.Stations;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** A station follows a change of trade unless the purse has moved it. */
class StationsTest {
	private static Resident make(JobType job) {
		return new Resident(UUID.randomUUID(), "A", "B", false, AgeStage.ADULT, 0, job, Resident.NO_PLOT, Resident.NO_PLOT, 0, new CompoundTag());
	}

	@Test
	void aNewTradeBringsItsStation() {
		Resident r = make(JobType.NONE);
		assertEquals(Station.POOR, r.station);
		r.job = JobType.FARMER;
		assertTrue(Stations.sync(r));
		assertEquals(Station.PEASANT, r.station);
		assertFalse(Stations.sync(r), "nothing changes twice");
		r.job = JobType.MERCHANT;
		assertTrue(Stations.sync(r));
		assertEquals(Station.MERCHANT, r.station);
	}

	@Test
	void aStationThePurseMovedIsKept() {
		Resident r = make(JobType.FARMER);
		Stations.sync(r);
		// the economy raised this farmer's household
		r.station = Station.MERCHANT;
		r.job = JobType.BAKER;
		Stations.sync(r);
		assertEquals(Station.MERCHANT, r.station);
	}
}
