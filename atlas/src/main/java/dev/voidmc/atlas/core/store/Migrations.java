package dev.voidmc.atlas.core.store;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * How to bring an old file up to date: per kind of file and per version, a function that turns a payload of that
 * version into a payload of the next one. {@link SafeFile#read} runs them in order, so a version 0 file read by a
 * version 3 build goes 0 to 1, 1 to 2, 2 to 3.
 *
 * <p>A step with nothing registered passes the payload through unchanged: a version bump that only added optional
 * fields needs no migration. A migration that throws makes the file count as unreadable (it is moved aside and the
 * backups are tried).
 */
public final class Migrations {
	private final Map<String, UnaryOperator<String>> steps = new HashMap<>();

	/** Registers the step from {@code fromVersion} to {@code fromVersion + 1}; returns this, for chaining. */
	public synchronized Migrations register(String kind, int fromVersion, UnaryOperator<String> upgrade) {
		if (fromVersion < 0) {
			throw new IllegalArgumentException("fromVersion " + fromVersion);
		}
		if (this.steps.putIfAbsent(key(kind, fromVersion), upgrade) != null) {
			throw new IllegalStateException("a migration for " + kind + " v" + fromVersion + " is already registered");
		}
		return this;
	}

	public synchronized boolean has(String kind, int fromVersion) {
		return this.steps.containsKey(key(kind, fromVersion));
	}

	/** The payload upgraded from version {@code from} to version {@code to}, one step at a time. */
	public String apply(String kind, int from, int to, String payload) {
		String out = payload;
		for (int v = from; v < to; v++) {
			UnaryOperator<String> step;
			synchronized (this) {
				step = this.steps.get(key(kind, v));
			}
			if (step != null) {
				out = step.apply(out);
				if (out == null) {
					throw new IllegalStateException("the migration of " + kind + " from v" + v + " returned nothing");
				}
			}
		}
		return out;
	}

	private static String key(String kind, int version) {
		return kind + "#" + version;
	}
}
