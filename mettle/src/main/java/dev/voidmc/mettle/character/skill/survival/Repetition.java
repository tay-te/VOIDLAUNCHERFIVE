package dev.voidmc.mettle.character.skill.survival;

import java.util.ArrayDeque;

/**
 * Anti-farm taper (CHARACTER.md §3.3) for one player and one kind of action: the first {@link #FREE} within five
 * minutes give full XP, the rest a quarter, and it recovers as old ones age out. Normal play never reaches it; an
 * animal pen or a bone-mealed flower field does.
 */
public final class Repetition {
	public static final int FREE = 60;
	public static final long WINDOW = 6000;
	public static final double TAPERED = 0.25;

	private final ArrayDeque<Long> times = new ArrayDeque<>();

	/** Records one action at {@code now} and returns the XP factor for it. */
	public double next(long now) {
		while (!this.times.isEmpty() && now - this.times.peekFirst() >= WINDOW) {
			this.times.pollFirst();
		}
		double factor = this.times.size() < FREE ? 1.0 : TAPERED;
		// Bounded: once tapered, only the window's count matters.
		if (this.times.size() < FREE * 4) {
			this.times.addLast(now);
		}
		return factor;
	}
}
