package dev.voidmc.hearth.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Why {@code planner.next} answered nothing, counted per reason across the server ({@code /hearth plan stats}): a town
 * whose builders stand idle should show here what its most urgent need gave up on (no frontage, roads at their budget,
 * no plot at a working's site...). Live calls (the server thread) only, unless {@link #everyCall}; histories are not
 * counted. Each settlement's reason is logged once when it changes, so a stalled town says why without flooding the log.
 */
public final class PlanStats {
	private static final Map<String, LongAdder> NOTHING = new ConcurrentHashMap<>();
	private static final LongAdder CALLS = new LongAdder();
	private static final LongAdder PLANNED = new LongAdder();
	/** The last reason logged per settlement, so a reason is logged once when it changes. */
	private static final Map<Long, String> LAST = new ConcurrentHashMap<>();

	/**
	 * Time live calls spent past their deadline, by the stage that was running (requests/w7-tour.md §1: a call that ran out
	 * of time still cost 20–95 ms against a limit of 10). A stage is charged only for its time after the deadline.
	 */
	private static final Map<String, LongAdder> PAST = new ConcurrentHashMap<>();
	private static final Map<String, LongAdder> PAST_CALLS = new ConcurrentHashMap<>();
	/** When the current call's last stage ended, and whether it has been counted as overrunning yet (per thread). */
	private static final ThreadLocal<long[]> LAP = ThreadLocal.withInitial(() -> new long[2]);

	/** Count every call, not only live ones (the offline previews and the planner's tests, to see why a town stalls). */
	public static volatile boolean everyCall;

	private PlanStats() {
	}

	static void planned(long settlement) {
		CALLS.increment();
		PLANNED.increment();
		LAST.remove(settlement);
	}

	static void nothing(long settlement, String name, int population, String why) {
		CALLS.increment();
		NOTHING.computeIfAbsent(why, k -> new LongAdder()).increment();
		// (a call out of time is not logged: it is asked again at once, and a log line costs the call's time)
		if (!why.equals(LAST.put(settlement, why)) && !why.contains("out of time")) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan] {} ({} souls): planner.next answers nothing: {}", name, population, why);
		}
	}

	/** The deadline of the call under way (single-threaded benches read it to profile what runs past it). */
	public static volatile long deadlineNow = Long.MAX_VALUE;

	/** A call begins: its stages are timed from now. */
	static void start() {
		long[] lap = LAP.get();
		lap[0] = System.nanoTime();
		lap[1] = 0;
	}

	/**
	 * A stage of a live call has ended: whatever of it ran past the call's deadline is charged to {@code stage} ("reconcile",
	 * "needs list", "housing", "program"...), so {@code /hearth plan stats} shows where an overrunning call's time goes.
	 */
	public static void lap(PlanContext ctx, String stage) {
		if (ctx.deadline == Long.MAX_VALUE) {
			return;
		}
		long now = System.nanoTime();
		long[] lap = LAP.get();
		long from = Math.max(lap[0], ctx.deadline);
		lap[0] = now;
		if (now > from) {
			PAST.computeIfAbsent(stage, k -> new LongAdder()).add(now - from);
			PAST_CALLS.computeIfAbsent(stage, k -> new LongAdder()).increment();
		}
	}

	/** The last reason a settlement's planner answered nothing, or null if its last call planned something. */
	public static @org.jspecify.annotations.Nullable String last(long settlement) {
		return LAST.get(settlement);
	}

	/** Counts by reason, most frequent first. */
	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		long calls = CALLS.sum();
		out.add(String.format(Locale.ROOT, "planner.next: %d live calls, %d planned something, %d nothing", calls, PLANNED.sum(), calls - PLANNED.sum()));
		NOTHING.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
			.limit(16)
			.forEach(e -> out.add(String.format(Locale.ROOT, "  %6d  %s", e.getValue().sum(), e.getKey())));
		if (!PAST.isEmpty()) {
			StringBuilder b = new StringBuilder("planner.next past its deadline (ms, calls):");
			PAST.entrySet().stream()
				.sorted((a, c) -> Long.compare(c.getValue().sum(), a.getValue().sum()))
				.limit(10)
				.forEach(e -> b.append(String.format(Locale.ROOT, " %s %.1f (%d);", e.getKey(), e.getValue().sum() / 1e6,
					PAST_CALLS.get(e.getKey()).sum())));
			out.add(b.toString());
		}
		return out;
	}

	public static void reset() {
		NOTHING.clear();
		CALLS.reset();
		PLANNED.reset();
		LAST.clear();
		PAST.clear();
		PAST_CALLS.clear();
	}
}
