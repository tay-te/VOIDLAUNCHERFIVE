package dev.voidmc.hearth.law.lock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LockpickModelTest {
	private static final float DT = 0.02F;

	@Test
	void specIsDeterministicAndHarderLocksHaveMorePinsAndNarrowerSpots() {
		LockpickModel.Spec a = LockpickModel.spec(3, 42, 0);
		LockpickModel.Spec b = LockpickModel.spec(3, 42, 0);
		assertArrayEquals(a.sweet(), b.sweet());
		assertEquals(1, LockpickModel.spec(1, 1, 0).pins());
		assertEquals(2, LockpickModel.spec(3, 1, 0).pins());
		assertEquals(3, LockpickModel.spec(5, 1, 0).pins());
		assertTrue(LockpickModel.spec(5, 1, 0).tolerance() < LockpickModel.spec(1, 1, 0).tolerance());
		assertTrue(LockpickModel.spec(5, 1, 0).strainRate() > LockpickModel.spec(1, 1, 0).strainRate());
		for (float s : LockpickModel.spec(5, 7, 0).sweet()) {
			assertTrue(s >= 15 && s <= LockpickModel.ARC - 15);
		}
	}

	@Test
	void skillWidensTheSweetSpot() {
		assertTrue(LockpickModel.spec(4, 1, 20).tolerance() > LockpickModel.spec(4, 1, 0).tolerance());
		assertTrue(LockpickModel.spec(4, 1, 20).strainRate() < LockpickModel.spec(4, 1, 0).strainRate());
	}

	@Test
	void turningInTheSweetSpotOpensTheLock() {
		LockpickModel.Spec spec = LockpickModel.spec(4, 99, 0);
		LockpickModel m = new LockpickModel(spec);
		float seconds = 0;
		LockpickModel.Event last = LockpickModel.Event.NONE;
		while (!m.open() && seconds < 30) {
			float target = spec.sweet()[Math.min(m.setPins(), spec.pins() - 1)];
			// find the spot first, then turn
			boolean onSpot = Math.abs(m.pick() - target) < 0.5F;
			last = m.tick(target, onSpot, DT);
			seconds += DT;
		}
		assertTrue(m.open());
		assertEquals(LockpickModel.Event.OPENED, last);
		assertEquals(0, m.breaks());
		assertTrue(seconds < 10, "took " + seconds);
	}

	@Test
	void forcingABoundCylinderSnapsThePick() {
		LockpickModel.Spec spec = LockpickModel.spec(2, 5, 0);
		LockpickModel m = new LockpickModel(spec);
		float off = spec.sweet()[0] > 90 ? spec.sweet()[0] - 30 : spec.sweet()[0] + 30;
		for (int i = 0; i < 50; i++) {
			m.tick(off, false, DT);
		}
		boolean broke = false;
		for (int i = 0; i < 500 && !broke; i++) {
			broke = m.tick(off, true, DT) == LockpickModel.Event.BROKE;
		}
		assertTrue(broke);
		assertEquals(1, m.breaks());
		assertEquals(0, m.rotation(), 1e-6);
	}

	@Test
	void theCylinderGivesLessTheFurtherOffTheSpot() {
		LockpickModel.Spec spec = LockpickModel.spec(3, 11, 0);
		LockpickModel m = new LockpickModel(spec);
		float s = spec.sweet()[0];
		assertEquals(1.0F, m.give(s), 1e-6);
		float near = m.give(s + spec.tolerance() + 5);
		float far = m.give(s + spec.tolerance() + 20);
		assertTrue(near < 1.0F && far < near);
		assertEquals(0, m.give(s + spec.tolerance() + spec.falloff() + 1), 1e-6);
	}

	@Test
	void setPinsDropWhenTensionIsLetGo() {
		LockpickModel.Spec spec = LockpickModel.spec(5, 3, 0);
		LockpickModel m = new LockpickModel(spec);
		for (int i = 0; i < 400 && m.setPins() < 1; i++) {
			float target = spec.sweet()[0];
			m.tick(target, Math.abs(m.pick() - target) < 0.5F, DT);
		}
		assertEquals(1, m.setPins());
		boolean dropped = false;
		for (int i = 0; i < 200 && !dropped; i++) {
			dropped = m.tick(m.pick(), false, DT) == LockpickModel.Event.PIN_DROPPED;
		}
		assertTrue(dropped);
		assertEquals(0, m.setPins());
	}
}
