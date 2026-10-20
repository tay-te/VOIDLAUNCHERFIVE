package dev.voidmc.hearth.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Server time each living-world lane spends (docs/LIVING_WORLD_2.md §9.1), shown by {@code /hearth sim stats}: a lane
 * takes its {@link #timer} once and wraps its tick work in it, so the budgets can be checked in tours and the dev harness.
 */
public final class LivingWorldStats {
	private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();

	private LivingWorldStats() {
	}

	/** The named timer (created on first use): {@code roads}, {@code travel}, {@code rhythm}, {@code crowds}... */
	public static Timer timer(String name) {
		return TIMERS.computeIfAbsent(name, Timer::new);
	}

	/** One line per timer that ran, by name. */
	public static List<String> lines() {
		List<Timer> timers = new ArrayList<>(TIMERS.values());
		timers.sort((a, b) -> a.name.compareTo(b.name));
		List<String> out = new ArrayList<>();
		for (Timer t : timers) {
			long calls = t.calls.sum();
			if (calls == 0) {
				continue;
			}
			out.add(String.format(Locale.ROOT, "living world %s: %d passes, %.3f ms avg, %.3f ms max", t.name, calls, t.nanos.sum() / 1e6 / calls,
				t.maxNanos.get() / 1e6));
		}
		return out;
	}

	public static final class Timer {
		final String name;
		final LongAdder nanos = new LongAdder();
		final LongAdder calls = new LongAdder();
		final AtomicLong maxNanos = new AtomicLong();

		Timer(String name) {
			this.name = name;
		}

		/** Records one pass that took {@code nanos}. */
		public void add(long nanos) {
			this.nanos.add(nanos);
			this.calls.increment();
			this.maxNanos.accumulateAndGet(nanos, Math::max);
		}

		/** Runs {@code work} and records its time. */
		public void time(Runnable work) {
			long t0 = System.nanoTime();
			try {
				work.run();
			} finally {
				add(System.nanoTime() - t0);
			}
		}

		public double averageMs() {
			long c = this.calls.sum();
			return c == 0 ? 0 : this.nanos.sum() / 1e6 / c;
		}
	}
}
