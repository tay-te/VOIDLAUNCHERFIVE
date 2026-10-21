package dev.voidmc.hearth.sim.lives;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * What the lives cost (docs/LIVING_WORLD_2.md §4.4, §9.1: ≤ 30 µs per 150 people per sim day): every settlement-day's
 * time, and the same scaled to 150 people, for {@code /hearth lives stats} and the tours.
 */
public final class LivesStats {
	private static final LongAdder DAYS = new LongAdder();
	private static final LongAdder NANOS = new LongAdder();
	private static final LongAdder PEOPLE = new LongAdder();
	private static final AtomicLong MAX = new AtomicLong();

	private LivesStats() {
	}

	static void day(int population, long nanos) {
		DAYS.increment();
		NANOS.add(nanos);
		PEOPLE.add(population);
		MAX.accumulateAndGet(nanos, Math::max);
	}

	/** Average µs a settlement-day, scaled to 150 people. */
	public static double per150() {
		long people = PEOPLE.sum();
		return people == 0 ? 0 : NANOS.sum() / 1e3 * 150.0 / people;
	}

	public static String line() {
		long d = DAYS.sum();
		if (d == 0) {
			return "lives: no settlement-days yet";
		}
		return String.format(Locale.ROOT, "lives: %d settlement-days, %.1f us avg (%.1f people), %.1f us per 150 people, %.1f us max", d,
			NANOS.sum() / 1e3 / d, PEOPLE.sum() / (double) d, per150(), MAX.get() / 1e3);
	}

	public static void reset() {
		DAYS.reset();
		NANOS.reset();
		PEOPLE.reset();
		MAX.set(0);
	}
}
