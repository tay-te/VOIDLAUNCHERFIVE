package dev.voidmc.hearth.law.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PickpocketTest {
	@Test
	void awarenessFillsAndCatchesTheThief() {
		PickpocketModel m = new PickpocketModel(0.2);
		int ticks = 0;
		while (!m.caught() && ticks < 1000) {
			m.tick(0.05);
			ticks++;
		}
		assertTrue(m.caught());
		assertEquals(100, ticks, 1); // 1 / 0.2 per second = 5 s
	}

	@Test
	void liftingIsFasterToNoticeAndCompletes() {
		PickpocketModel m = new PickpocketModel(0.1);
		m.lift(2, 1);
		int done = -1;
		double t = 0;
		while (done < 0 && !m.caught()) {
			done = m.tick(0.05);
			t += 0.05;
		}
		assertEquals(2, done);
		assertEquals(PickpocketModel.liftSeconds(1), t, 0.06);
		assertEquals(0.1 * PickpocketModel.LIFT_FACTOR * t, m.awareness(), 0.01);
		assertEquals(-1, m.lifting());
	}

	@Test
	void heavierThingsTakeLonger() {
		assertTrue(PickpocketModel.liftSeconds(6) > PickpocketModel.liftSeconds(1));
	}

	@Test
	void nothingIsLiftedOnceCaught() {
		PickpocketModel m = new PickpocketModel(100);
		m.tick(0.05);
		assertTrue(m.caught());
		m.lift(0, 1);
		assertEquals(-1, m.tick(1.0));
	}

	private static Resident resident(JobType job, AgeStage age) {
		return new Resident(UUID.fromString("7a4b3c2d-1111-2222-3333-444455556666"), "Edda", "Miller", true, age, -3000, job, 1, 2, 5,
			new CompoundTag());
	}

	@Test
	void pocketsAreTheSameEachLookAndFollowTheTrade() {
		Resident smith = resident(JobType.SMITH, AgeStage.ADULT);
		List<Pockets.PocketItem> a = Pockets.of(smith, 10, true, true);
		List<Pockets.PocketItem> b = Pockets.of(smith, 10, true, true);
		assertEquals(a, b);
		assertEquals(Pockets.Kind.COIN, a.get(0).kind());
		assertEquals(Pockets.Kind.KEY, a.get(1).kind());
		assertFalse(Pockets.of(smith, 10, false, false).stream().anyMatch(p -> p.kind() != Pockets.Kind.ITEM));
		long[] purse = Pockets.purse(Station.ARTISAN);
		assertTrue(a.get(0).bits() >= purse[0] && a.get(0).bits() <= purse[1]);
	}

	@Test
	void richerFolkCarryMore() {
		assertTrue(Pockets.purse(Station.LORD)[0] > Pockets.purse(Station.POOR)[1]);
	}

	@Test
	void liftedThingsStayGoneUntilThePocketsFillAgain() {
		Resident r = resident(JobType.FARMER, AgeStage.ADULT);
		long period = Pockets.period(31, 3);
		assertEquals(10, period);
		assertTrue(Pockets.taken(r, period).isEmpty());
		Pockets.markTaken(r, period, 1);
		Pockets.markTaken(r, period, 1);
		Pockets.markTaken(r, period, 0);
		assertEquals(List.of(1, 0), Pockets.taken(r, period));
		assertTrue(Pockets.taken(r, period + 1).isEmpty());
	}
}
