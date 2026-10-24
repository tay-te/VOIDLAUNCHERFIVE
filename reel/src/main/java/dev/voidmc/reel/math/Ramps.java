package dev.voidmc.reel.math;

/**
 * How a move gets up to speed and comes to rest: over the first "in" seconds the speed rises from nothing, over the
 * last "out" seconds it falls back, and in between it holds. A ramp of 0 means the move is already at speed on its
 * first frame, or still at speed on its last: a shot that is cut into, or out of, mid-motion. "settle" is the ramp
 * into and out of a hold partway through a move.
 *
 * <p>The ramps are sine-shaped (the speed rises as sin^2), so the acceleration also starts and ends at zero and the
 * camera never jolts. Seen as progress, a ramp of a seconds covers what a/2 seconds at full speed would.
 */
public record Ramps(double in, double out, double settle) {
	public static final Ramps DEFAULT = new Ramps(1, 1, 1);

	/** The share of full speed at time t into a stretch lasting length seconds, ramping up over a and down over b. */
	public static double factor(double t, double length, double a, double b) {
		if (t < 0 || t > length) {
			return 0;
		}
		if (t < a) {
			double s = Math.sin(Math.PI * t / (2 * a));
			return s * s;
		}
		if (t > length - b) {
			double s = Math.sin(Math.PI * (length - t) / (2 * b));
			return s * s;
		}
		return 1;
	}

	/**
	 * How many seconds at full speed the stretch has covered by time t: the integral of {@link #factor}. At the end it
	 * is length - (a + b) / 2.
	 */
	public static double run(double t, double length, double a, double b) {
		t = Math.clamp(t, 0, length);
		if (t < a) {
			return ramped(t, a);
		}
		double full = length - (a + b) / 2;
		if (t > length - b) {
			return full - ramped(length - t, b);
		}
		return a / 2 + (t - a);
	}

	/** The integral of sin^2(pi x / 2a) from 0 to t. */
	private static double ramped(double t, double a) {
		return t / 2 - a / (2 * Math.PI) * Math.sin(Math.PI * t / a);
	}
}
