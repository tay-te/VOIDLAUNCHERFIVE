package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The auto-tuner against simulated machines: it must find a step that fits, come back when there is room, and never hunt. */
class AutoTuneTest {
	/**
	 * A machine where the LOD costs {@code gpu} and {@code prepare} milliseconds at full detail, and a step costs
	 * that times (reach / detail²) to the power {@code steepness}: 1 is the tuner's own model, more is a machine
	 * where finer detail costs more than the model predicts. Measurements carry 10% noise, and the GPU time is
	 * smoothed as the renderer smooths it.
	 */
	private static final class Machine {
		final AutoTune tune = new AutoTune();
		final Random random = new Random(42);
		double gpu;
		double prepare;
		double steepness = 1;
		double fps = 144;
		double capMillis;
		long nanos = 1_000_000_000L;
		double smoothed = -1;
		int changes;
		/** Simulated seconds spent over the GPU target, and in total. */
		double overSeconds;
		double seconds;

		Machine(double gpu, double prepare) {
			this.gpu = gpu;
			this.prepare = prepare;
		}

		double cost() {
			double d = this.tune.detailScale();
			return Math.pow(this.tune.reachScale() / (d * d), this.steepness);
		}

		void run(double seconds) {
			long frame = (long) (1e9 / this.fps);
			for (long end = this.nanos + (long) (seconds * 1e9); this.nanos < end; this.nanos += frame) {
				double gpu = this.gpu < 0 ? -1 : this.gpu * this.cost() * (0.9 + 0.2 * this.random.nextDouble());
				this.smoothed = gpu < 0 ? -1 : this.smoothed < 0 ? gpu : this.smoothed * 0.9 + gpu * 0.1;
				double prepare = this.prepare * this.cost() * (0.9 + 0.2 * this.random.nextDouble());
				if (this.tune.sample(this.nanos, this.smoothed, prepare, false, this.capMillis)) {
					this.changes++;
				}
				if (gpu > 1.5) {
					this.overSeconds += frame / 1e9;
				}
				this.seconds += frame / 1e9;
			}
		}
	}

	@Test
	void leavesAMachineWithRoomAlone() {
		Machine m = new Machine(0.5, 0.2);
		m.run(120);
		assertEquals(0, m.tune.step());
		assertEquals(0, m.changes);
	}

	@Test
	void coarsensUntilItFitsWithoutRushing() {
		Machine m = new Machine(4, 0.2);
		m.run(0.9);
		assertEquals(0, m.tune.step(), "waits for a second of evidence");
		m.run(60);
		assertTrue(m.tune.step() > 0);
		assertTrue(m.gpu * m.cost() <= 1.5, "fits: " + m.gpu * m.cost() + " ms at step " + m.tune.step());
		assertTrue(m.changes <= 3, "a few large steps, not many small ones: " + m.changes);
		// and stays put
		int changes = m.changes;
		m.run(600);
		assertEquals(changes, m.changes);
	}

	@Test
	void restoresTheDetailWhenThereIsRoomAgain() {
		Machine m = new Machine(5, 0.2);
		m.run(30);
		assertTrue(m.tune.step() >= 3);
		// the view turns to open sea: the LOD costs a tenth
		m.gpu = 0.5;
		m.run(90);
		assertEquals(0, m.tune.step(), "back to the settings");
		assertEquals(1.0, m.tune.detailScale());
	}

	@Test
	void stepsUpOnlyWhereThePredictionFits() {
		// at step 1 it costs 0.6 of the target: step 0 would cost about 0.86, over the 0.8 margin, so it stays
		Machine m = new Machine(0.6 * 1.5 * 1.44, 0.1);
		m.run(1.5);
		m.gpu = 5;
		m.run(1.5);
		m.gpu = 0.6 * 1.5 * 1.44;
		int changes = m.changes;
		m.run(300);
		assertTrue(m.tune.step() >= 1);
		assertTrue(m.changes - changes <= m.tune.step() + 1, "climbs back without overshooting: " + (m.changes - changes) + " changes");
	}

	@Test
	void doesNotHuntWhereTheModelIsWrong() {
		// finer detail costs far more than the model says: every step up the tuner takes goes over, and comes back
		// at step 1 it costs half the target, so the model invites a step up; at step 0 it really costs 1.12 times the target
		Machine m = new Machine(1.68, 0.1);
		m.steepness = 2.2;
		m.run(1800);
		System.out.printf("model wrong: %d changes in 30 minutes, over the target %.2f%% of the time, settled at step %d%n", m.changes,
			100 * m.overSeconds / m.seconds, m.tune.step());
		assertTrue(m.changes <= 24, "backs off instead of flickering: " + m.changes + " changes in 30 minutes");
		assertTrue(m.overSeconds / m.seconds < 0.05, "over the target " + Math.round(100 * m.overSeconds / m.seconds) + "% of the time");
	}

	@Test
	void leavesAGameAtItsFrameCapAlone() {
		Machine m = new Machine(4, 0.2);
		m.fps = 60;
		m.capMillis = 1000.0 / 60;
		m.run(60);
		assertEquals(0, m.tune.step(), "the game holds 60 fps: the LOD is not what holds it back");
		// the frame rate falls below the cap: now it counts
		m.fps = 40;
		m.run(10);
		assertTrue(m.tune.step() > 0);
	}

	@Test
	void shortensTheReachOnlyOnceTheDetailIsAtItsCoarsest() {
		Machine m = new Machine(50, 0.2);
		for (int i = 0; i < 120; i++) {
			m.run(0.5);
			assertTrue(m.tune.reachScale() == 1 || m.tune.detailScale() == 3, "reach " + m.tune.reachScale() + " at detail " + m.tune.detailScale());
		}
		assertEquals(0.5, m.tune.reachScale(), "a machine nothing helps ends at the coarsest step");
	}

	@Test
	void judgesPrepareTimeOnQuietFramesAndItsMedian() {
		AutoTune tune = new AutoTune();
		long nanos = 0;
		int changes = 0;
		for (int frame = 0; frame < 144 * 60; frame++, nanos += 6_944_444L) {
			// tiles stream in every other frame at 3 ms; the quiet frames take 0.2 ms, with a 10 ms hitch now and then
			boolean streaming = frame % 2 == 0;
			double prepare = streaming ? 3 : frame % 51 == 1 ? 10 : 0.2;
			changes += tune.sample(nanos, 0.4, prepare, streaming, 0) ? 1 : 0;
		}
		assertEquals(0, changes);
		assertEquals(0.2, tune.prepareMillis, 1e-6);
	}

	@Test
	void worksFromPrepareTimeAloneWithoutAGpuTimer() {
		Machine m = new Machine(-1, 2);
		m.run(30);
		assertTrue(m.tune.step() > 0);
		assertTrue(m.prepare * m.cost() <= 0.5, "fits: " + m.prepare * m.cost() + " ms");
	}

	@Test
	void resetGoesBackToTheSettings() {
		Machine m = new Machine(5, 0.2);
		m.run(30);
		assertTrue(m.tune.step() > 0);
		m.tune.reset();
		assertEquals(0, m.tune.step());
		assertEquals(1.0, m.tune.detailScale());
		assertEquals(1.0, m.tune.reachScale());
	}
}
