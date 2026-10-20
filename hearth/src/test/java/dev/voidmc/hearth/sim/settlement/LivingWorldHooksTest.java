package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Lane 0's sim hooks (docs/LIVING_WORLD_2.md §11.2): residents away, and jobs and homes other modules settle. */
class LivingWorldHooksTest {
	@AfterEach
	void forgetClaims() {
		ResidentClaims.clear();
	}

	private static SimSettlement town() {
		SimSettlement s = TestSupport.founded("flat", 71);
		for (int d = 1; d <= 60; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		return s;
	}

	private static Resident worker(SimSettlement s) {
		for (Resident r : s.residents.values()) {
			if (r.age == AgeStage.ADULT && r.job != JobType.NONE) {
				return r;
			}
		}
		throw new AssertionError("no worker in the test town");
	}

	@Test
	void anAwayResidentIsLeftOutOfTheTownsLabourUntilTheyAreBack() {
		SimSettlement s = town();
		Resident r = worker(s);
		int before = SettlementClock.Workforce.of(s, s.ctx()).workers[r.job.ordinal()];
		long now = s.simTick() - s.timeOffset();
		s.setResidentAway(r.id, now + 5_000, "test journey");
		assertTrue(s.isResidentAway(r.id));
		assertTrue(s.isResidentOut(r.id));
		assertEquals(before - 1, SettlementClock.Workforce.of(s, s.ctx()).workers[r.job.ordinal()], "away: not counted at work");
		s.setResidentAway(r.id, Long.MIN_VALUE, "back");
		assertFalse(s.isResidentAway(r.id));
		assertEquals(before, SettlementClock.Workforce.of(s, s.ctx()).workers[r.job.ordinal()], "back: counted again");
		// a tick already past does not send anyone away; one that passes brings them back by itself
		s.setResidentAway(r.id, now - 1, "already over");
		assertFalse(s.isResidentAway(r.id));
		s.setResidentAway(r.id, now + 1, "a moment");
		SettlementClock.advance(s, s.simTick() + 10);
		assertFalse(s.isResidentAway(r.id), "past their tick they are home");
		// unknown residents are never away
		UUID nobody = UUID.randomUUID();
		s.setResidentAway(nobody, now + 5_000, "nobody");
		assertFalse(s.isResidentAway(nobody));
	}

	@Test
	void theAwayBookSavesAndLoads() {
		AwayBook book = new AwayBook();
		UUID id = UUID.randomUUID();
		book.put(id, 1234, "pilgrimage");
		var json = AwayBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		AwayBook back = AwayBook.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(back.away(id, 1000));
		assertFalse(back.away(id, 1234));
		assertTrue(back.expire(2000));
		assertEquals(0, back.size());
	}

	@Test
	void settledJobsAndHomesAreKeptByTheDailyAssignment() {
		SimSettlement s = town();
		Resident servant = worker(s);
		Resident hermit = null;
		for (Resident r : s.residents.values()) {
			if (r != servant && r.age == AgeStage.ADULT) {
				hermit = r;
				break;
			}
		}
		assertNotNull(hermit);
		SimPlot house = null;
		for (SimPlot p : s.plots.values()) {
			if (p.isStanding() && p.beds > 0) {
				house = p;
			}
		}
		assertNotNull(house, "a house stands");
		int houseId = house.id();
		UUID servantId = servant.id;
		UUID hermitId = hermit.id;
		hermit.job = JobType.HERMIT;
		hermit.work = Resident.NO_PLOT;
		ResidentClaims.register(new ResidentClaims.Source() {
			@Override
			public ResidentClaims.@Nullable Job job(Settlement settlement, Resident resident) {
				return resident.id.equals(servantId) ? new ResidentClaims.Job(JobType.SERVANT, houseId) : null;
			}

			@Override
			public @Nullable Integer home(Settlement settlement, Resident resident) {
				return resident.id.equals(servantId) ? houseId : null;
			}

			@Override
			public boolean exempt(Settlement settlement, Resident resident) {
				return resident.id.equals(hermitId);
			}
		});
		SettlementClock.assignHomes(s);
		SettlementClock.assignJobs(s, TestSupport.rules());
		assertEquals(JobType.SERVANT, servant.job);
		assertEquals(houseId, servant.work);
		assertEquals(houseId, servant.home);
		assertEquals(JobType.HERMIT, hermit.job, "an exempt resident keeps the job they have");
	}
}
