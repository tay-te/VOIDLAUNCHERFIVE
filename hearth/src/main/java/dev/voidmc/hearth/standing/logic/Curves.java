package dev.voidmc.hearth.standing.logic;

/** The small pieces of arithmetic every layer shares: soft caps, fading, clamps. Pure and cheap. */
public final class Curves {
	private Curves() {
	}

	/**
	 * A change to standing, made harder the further standing already is from 0 in the same direction: at ±100 a deed moves
	 * it {@code 1 − softCap} as much, so a hero is made by many deeds, not one. Changes back toward 0 are never damped.
	 */
	public static double softened(double current, double delta, double softCap) {
		if (delta > 0 && current > 0) {
			return delta * (1 - softCap * Math.min(1, current / 100));
		}
		if (delta < 0 && current < 0) {
			return delta * (1 - softCap * Math.min(1, -current / 100));
		}
		return delta;
	}

	/** Applies a softened change and keeps the result in −100…100. */
	public static double apply(double current, double delta, double softCap) {
		return clamp(current + softened(current, delta, softCap), -100, 100);
	}

	/** {@code 0.5^(age / halfLife)}: what is left of something after {@code age} days. Never negative ages. */
	public static double fade(double ageDays, double halfLifeDays) {
		if (ageDays <= 0) {
			return 1;
		}
		return Math.pow(0.5, ageDays / Math.max(1e-6, halfLifeDays));
	}

	/** Moves {@code value} toward 0 by up to {@code step}, never past it. */
	public static double drift(double value, double negativeStep, double positiveStep) {
		if (value < 0) {
			return Math.min(0, value + negativeStep);
		}
		if (value > 0) {
			return Math.max(0, value - positiveStep);
		}
		return 0;
	}

	public static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	public static int clampInt(long v, int lo, int hi) {
		return (int) (v < lo ? lo : v > hi ? hi : v);
	}

	/** Standing as other modules see it: rounded half away from zero, so −0.5 reads as −1 (a slight is a slight). */
	public static int round(double v) {
		return (int) (v < 0 ? -Math.round(-v) : Math.round(v));
	}
}
