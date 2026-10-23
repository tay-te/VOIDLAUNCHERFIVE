package dev.voidmc.bestiary.ai.math;

import java.util.Locale;

/**
 * Response curves for utility considerations (BESTIARY.md §2.1): each maps an input (a distance, a fraction, a flag)
 * to 0..1. Pure maths, no Minecraft classes (JUnit: {@code AiMathTest}).
 */
public final class Curves {
	public enum Curve {
		/** 0 at a, 1 at b, clamped (a greater than b falls instead). */
		LINEAR,
		/** 1 - LINEAR. */
		INVERSE,
		/** 1 inside [a, b], easing to 0 over a soft edge (a quarter of the band, at least half a block) outside it. */
		BAND,
		/** 1 at or above a, else b (0 by default). */
		STEP,
		/** a when the input is true (above 0.5), b when false. */
		BOOL,
		/** A bell: 1 at a, half at a plus or minus b. */
		BELL,
		/** Always a. */
		CONST;

		public static Curve byId(String id) {
			return valueOf(id.toUpperCase(Locale.ROOT));
		}
	}

	private Curves() {
	}

	public static float eval(Curve c, float x, float a, float b) {
		return switch (c) {
			case LINEAR -> clamp01(a == b ? (x >= a ? 1 : 0) : (x - a) / (b - a));
			case INVERSE -> 1 - clamp01(a == b ? (x >= a ? 1 : 0) : (x - a) / (b - a));
			case BAND -> band(x, Math.min(a, b), Math.max(a, b));
			case STEP -> x >= a ? 1 : clamp01(b);
			case BOOL -> x > 0.5F ? clamp01(a) : clamp01(b);
			case BELL -> {
				float w = Math.max(1e-3F, b);
				float d = (x - a) / w;
				yield (float) Math.pow(2, -d * d);
			}
			case CONST -> clamp01(a);
		};
	}

	static float band(float x, float lo, float hi) {
		if (x >= lo && x <= hi) {
			return 1;
		}
		float soft = Math.max(0.5F, (hi - lo) * 0.25F);
		float d = x < lo ? lo - x : x - hi;
		return clamp01(1 - d / soft);
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
