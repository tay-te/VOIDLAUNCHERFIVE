package dev.voidmc.reel.math;

import java.util.Locale;

/** How a move starts and stops: maps 0..1 progress in time to 0..1 progress along the move. */
public enum Easing {
	LINEAR,
	IN_SINE,
	OUT_SINE,
	IN_OUT_SINE,
	IN_OUT_CUBIC,
	IN_OUT_QUINT,
	SMOOTHER;

	public double apply(double t) {
		t = Math.clamp(t, 0, 1);
		return switch (this) {
			case LINEAR -> t;
			case IN_SINE -> 1 - Math.cos(t * Math.PI / 2);
			case OUT_SINE -> Math.sin(t * Math.PI / 2);
			case IN_OUT_SINE -> (1 - Math.cos(t * Math.PI)) / 2;
			case IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
			case IN_OUT_QUINT -> t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
			case SMOOTHER -> t * t * t * (t * (t * 6 - 15) + 10);
		};
	}

	/** Parses "inOutSine", "in-out-sine" or "IN_OUT_SINE". */
	public static Easing parse(String name) {
		String key = name.replaceAll("([a-z])([A-Z])", "$1_$2").replace('-', '_').toUpperCase(Locale.ROOT);
		try {
			return valueOf(key);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown ease '" + name + "' (linear, inSine, outSine, inOutSine, inOutCubic, inOutQuint, smoother)");
		}
	}
}
