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
		if (!why.equals(LAST.put(settlement, why))) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan] {} ({} souls): planner.next answers nothing: {}", name, population, why);
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
		return out;
	}

	public static void reset() {
		NOTHING.clear();
		CALLS.reset();
		PLANNED.reset();
		LAST.clear();
	}
}
