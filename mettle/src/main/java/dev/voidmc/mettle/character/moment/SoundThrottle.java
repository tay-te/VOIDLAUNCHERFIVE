package dev.voidmc.mettle.character.moment;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps progress sounds from piling up: after a group plays, it stays quiet for its interval unless something of a
 * higher priority comes (a skill level and the character level in the same tick chime once; a tier opening still
 * rings over a plain level). Plain Java: unit-tested in {@code MomentQueueTest}.
 */
public final class SoundThrottle {
	private final Map<String, Long> lastAt = new HashMap<>();
	private final Map<String, Integer> lastPriority = new HashMap<>();

	/** Whether a sound of this group and priority may play now; if so, it counts as played. */
	public boolean allow(String group, int priority, long intervalMs, long nowMs) {
		Long at = this.lastAt.get(group);
		boolean quiet = at == null || nowMs - at >= intervalMs;
		if (!quiet && priority <= this.lastPriority.getOrDefault(group, 0)) {
			return false;
		}
		this.lastAt.put(group, nowMs);
		this.lastPriority.put(group, priority);
		return true;
	}

	public boolean allow(MomentKind kind, long nowMs) {
		return allow(kind.soundGroup(), kind.soundPriority(), kind.soundIntervalMs(), nowMs);
	}

	public void clear() {
		this.lastAt.clear();
		this.lastPriority.clear();
	}
}
