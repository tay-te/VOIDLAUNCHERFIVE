package dev.voidmc.atlas.core.journal;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One line of the journal's timeline: something the player did for the first time, or reached.
 *
 * <p>Old events past the journal's cap are merged into {@link Kind#SUMMARY} events, one per game day (or, once even
 * those are too many, one per run of days): a summary keeps how many of each kind it stands for in {@link #tally}, so
 * nothing is forgotten, only told more briefly ("Day 12: 3 discoveries, 2 new areas, travelled 1 km").
 *
 * @param kind      what happened
 * @param text      a short line to show as it is: "Discovered Plains village", "Reached Y -54", "Travelled 3 km"
 * @param day       the game day it happened on (for a summary, its first day); -1 if unknown
 * @param lastDay   the last game day a summary covers; the same as {@code day} for any other event
 * @param epoch     when it happened, epoch milliseconds (for a summary, its first event's)
 * @param dimension like {@code minecraft:overworld}; null for a summary
 * @param x         where it happened (the place's middle for a discovery; the player otherwise); 0 for a summary
 * @param y         where it happened
 * @param z         where it happened
 * @param placeId   the place discovered, for a {@link Kind#DISCOVERY}; null otherwise
 * @param subject   what it is about: the place's kind for a discovery, the biome for an area, the dimension for a
 *                  visit; null otherwise
 * @param tally     for a summary, how many events of each kind it stands for; empty otherwise
 */
public record JournalEvent(Kind kind, String text, long day, long lastDay, long epoch, String dimension, int x, int y, int z, String placeId,
	String subject, Map<Kind, Integer> tally) {

	public enum Kind {
		/** A place came within reach for the first time. */
		DISCOVERY,
		/** A biome area (a named stretch of one biome) entered for the first time. */
		AREA,
		/** A dimension visited for the first time. */
		DIMENSION,
		/** The deepest Y so far in a dimension. */
		DEPTH,
		/** Another whole kilometre travelled. */
		DISTANCE,
		/** Older events merged into one line per day. */
		SUMMARY
	}

	public JournalEvent {
		Objects.requireNonNull(kind, "kind");
		text = text == null ? "" : text;
		lastDay = Math.max(day, lastDay);
		tally = tally == null || tally.isEmpty() ? Map.of() : Map.copyOf(tally);
	}

	/** An event that is not a summary. */
	public static JournalEvent of(Kind kind, String text, long day, long epoch, String dimension, int x, int y, int z, String placeId, String subject) {
		return new JournalEvent(kind, text, day, day, epoch, dimension, x, y, z, placeId, subject, Map.of());
	}

	public boolean isSummary() {
		return this.kind == Kind.SUMMARY;
	}

	/** How many events this stands for: 1, or a summary's total. */
	public int count() {
		if (!this.isSummary()) {
			return 1;
		}
		int n = 0;
		for (int v : this.tally.values()) {
			n += v;
		}
		return n;
	}

	/** How many events of {@code kind} this stands for. */
	public int count(Kind kind) {
		if (!this.isSummary()) {
			return this.kind == kind ? 1 : 0;
		}
		return this.tally.getOrDefault(kind, 0);
	}

	/** A summary of {@code a} and {@code b} (either may be a summary already), covering both their days. */
	static JournalEvent merge(JournalEvent a, JournalEvent b) {
		Map<Kind, Integer> tally = new EnumMap<>(Kind.class);
		for (JournalEvent e : new JournalEvent[] {a, b}) {
			for (Kind k : Kind.values()) {
				int n = e.count(k);
				if (n > 0) {
					tally.merge(k, n, Integer::sum);
				}
			}
		}
		long day = minDay(a.day, b.day);
		long lastDay = Math.max(a.lastDay, b.lastDay);
		long epoch = a.epoch == 0 ? b.epoch : b.epoch == 0 ? a.epoch : Math.min(a.epoch, b.epoch);
		return new JournalEvent(Kind.SUMMARY, summaryText(day, lastDay, tally), day, lastDay, epoch, null, 0, 0, 0, null, null, tally);
	}

	/** A summary of one event (so it can be merged with the summary before it). */
	static JournalEvent summaryOf(JournalEvent e) {
		if (e.isSummary()) {
			return e;
		}
		Map<Kind, Integer> tally = new EnumMap<>(Kind.class);
		tally.put(e.kind, 1);
		return new JournalEvent(Kind.SUMMARY, summaryText(e.day, e.lastDay, tally), e.day, e.lastDay, e.epoch, null, 0, 0, 0, null, null, tally);
	}

	private static long minDay(long a, long b) {
		if (a < 0) {
			return b;
		}
		if (b < 0) {
			return a;
		}
		return Math.min(a, b);
	}

	/** "Day 12: 3 discoveries, 2 new areas, travelled 1 km", or "Days 12-15: ..." for a run of days. */
	static String summaryText(long day, long lastDay, Map<Kind, Integer> tally) {
		StringBuilder sb = new StringBuilder();
		if (day < 0) {
			sb.append("Earlier");
		} else if (lastDay > day) {
			sb.append("Days ").append(day).append('-').append(lastDay);
		} else {
			sb.append("Day ").append(day);
		}
		String sep = ": ";
		int n;
		if ((n = tally.getOrDefault(Kind.DISCOVERY, 0)) > 0) {
			sb.append(sep).append(n).append(n == 1 ? " discovery" : " discoveries");
			sep = ", ";
		}
		if ((n = tally.getOrDefault(Kind.AREA, 0)) > 0) {
			sb.append(sep).append(n).append(n == 1 ? " new area" : " new areas");
			sep = ", ";
		}
		if ((n = tally.getOrDefault(Kind.DIMENSION, 0)) > 0) {
			sb.append(sep).append(n).append(n == 1 ? " new dimension" : " new dimensions");
			sep = ", ";
		}
		if ((n = tally.getOrDefault(Kind.DEPTH, 0)) > 0) {
			sb.append(sep).append(n).append(n == 1 ? " depth record" : " depth records");
			sep = ", ";
		}
		if ((n = tally.getOrDefault(Kind.DISTANCE, 0)) > 0) {
			sb.append(sep).append("travelled ").append(n).append(" km");
		}
		return sb.toString();
	}
}
