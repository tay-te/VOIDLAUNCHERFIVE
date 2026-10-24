package dev.voidmc.reel.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How far along a paced move the camera is at each moment. The camera runs at the speeds its keys ask for (blocks a
 * second, carried between keys by distance), ramps up from rest and down to rest as {@link Ramps} says, and may stop
 * at keys to hold. This is what lets one shot hand its speed to the next: a shot that leaves at 9 blocks a second
 * with no out ramp cuts to one that arrives at 9 with no in ramp.
 *
 * <p>Everything has a closed form. With G the seconds a stretch takes at the keyed speeds, the stretch lasts
 * G / k + (in + out) / 2 seconds, where k scales every speed so the whole move fits the shot's duration (k is 1 when
 * the duration comes from the speeds). So keyed speeds give the move its shape, and the duration can still be set
 * to the voice-over.
 */
public final class Pace {
	private static final int SAMPLES = 4096;

	/** A stretch of the move between holds, from one distance to another, starting and ending at these times. */
	private record Leg(int fromKey, int toKey, double from, double to, double start, double end, double in, double out) {
	}

	private final double total;
	private final double[] s;
	private final double[] g;
	private final Monotone speed;
	private final List<Leg> legs = new ArrayList<>();
	private final double scale;
	private final double duration;
	private final double[] keyAt;
	private final double[] holds;
	private final Ramps ramps;

	/**
	 * @param keyAt    the distance along the path of each key, never decreasing
	 * @param speeds   each key's speed, or null where the key leaves it to its neighbours
	 * @param holds    the seconds the camera rests at each key
	 * @param duration the shot's length, or null to take it from the speeds
	 */
	public Pace(double[] keyAt, Double[] speeds, double[] holds, Ramps ramps, Double duration) {
		int n = keyAt.length;
		this.keyAt = keyAt.clone();
		this.holds = holds.clone();
		this.ramps = ramps;
		this.total = keyAt[n - 1] - keyAt[0];

		List<Double> xs = new ArrayList<>();
		List<Double> vs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (speeds[i] == null) {
				continue;
			}
			if (!xs.isEmpty() && !(keyAt[i] > xs.getLast())) {
				throw new IllegalArgumentException("two keys set a speed at the same place (key " + i + "): a speed belongs to a key the camera travels to");
			}
			xs.add(keyAt[i]);
			vs.add(speeds[i]);
		}
		this.speed = xs.isEmpty() ? null
			: new Monotone(xs.stream().mapToDouble(Double::doubleValue).toArray(), vs.stream().mapToDouble(Double::doubleValue).toArray());

		this.s = new double[SAMPLES + 1];
		this.g = new double[SAMPLES + 1];
		for (int i = 0; i <= SAMPLES; i++) {
			this.s[i] = keyAt[0] + this.total * i / SAMPLES;
			if (i > 0) {
				this.g[i] = this.g[i - 1] + (this.s[i] - this.s[i - 1]) * (0.5 / this.keyedSpeed(this.s[i - 1]) + 0.5 / this.keyedSpeed(this.s[i]));
			}
		}

		// stretches between holds; a hold at the first or last key is a wait before or after the move
		List<int[]> stretches = new ArrayList<>();
		int from = 0;
		for (int i = 1; i < n; i++) {
			if (i == n - 1 || holds[i] > 0) {
				stretches.add(new int[] {from, i});
				from = i;
			}
		}
		double seconds = 0;
		double ramped = 0;
		for (int k = 0; k < stretches.size(); k++) {
			int[] st = stretches.get(k);
			double gs = this.gAt(keyAt[st[1]]) - this.gAt(keyAt[st[0]]);
			if (gs > 0) {
				seconds += gs;
				ramped += (rampIn(k) + rampOut(k, stretches.size())) / 2;
			}
		}
		double held = 0;
		for (double h : holds) {
			held += h;
		}

		if (duration != null) {
			double free = duration - ramped - held;
			if (seconds > 0 && free <= 1e-9) {
				throw new IllegalArgumentException(String.format(Locale.ROOT,
					"the ramps (%.2f s at half speed) and holds (%.2f s) leave no time to move in %.2f s: shorten them or lengthen the shot", ramped, held, duration));
			}
			this.scale = seconds > 0 ? seconds / free : 1;
			this.duration = duration;
		} else {
			if (this.speed == null) {
				throw new IllegalArgumentException("a paced move needs the shot's duration, or a speed on at least one key");
			}
			this.scale = 1;
			this.duration = seconds + ramped + held;
		}

		double t = holds[0];
		for (int k = 0; k < stretches.size(); k++) {
			int[] st = stretches.get(k);
			double a = keyAt[st[0]];
			double b = keyAt[st[1]];
			double gs = this.gAt(b) - this.gAt(a);
			double in = rampIn(k);
			double out = rampOut(k, stretches.size());
			double length = gs > 0 ? gs / this.scale + (in + out) / 2 : 0;
			if (gs > 0 && (in + out) / 2 > gs / this.scale + 1e-9) {
				throw new IllegalArgumentException(String.format(Locale.ROOT,
					"keys %d to %d take %.2f s at these speeds, too short for ramps of %.2f s and %.2f s: shorten the ramps or slow the move",
					st[0], st[1], length, in, out));
			}
			this.legs.add(new Leg(st[0], st[1], a, b, t, t + length, in, out));
			t += length + holds[st[1]];
		}
	}

	private double rampIn(int stretch) {
		return stretch == 0 ? this.ramps.in() : this.ramps.settle();
	}

	private double rampOut(int stretch, int count) {
		return stretch == count - 1 ? this.ramps.out() : this.ramps.settle();
	}

	/** The speed the keys ask for at distance d, before scaling and ramps (1 when no key sets one). */
	private double keyedSpeed(double d) {
		return this.speed == null ? 1 : this.speed.at(d);
	}

	/** Seconds at keyed speed from the start to distance d. */
	private double gAt(double d) {
		if (this.total <= 0) {
			return 0;
		}
		double i = (d - this.keyAt[0]) / this.total * SAMPLES;
		int lo = (int) Math.clamp(Math.floor(i), 0, SAMPLES - 1);
		double f = Math.clamp(i - lo, 0, 1);
		return this.g[lo] + (this.g[lo + 1] - this.g[lo]) * f;
	}

	/** The distance at which the keyed-speed seconds reach gs. */
	private double distanceForG(double gs) {
		int lo = 0;
		int hi = SAMPLES - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (this.g[mid] <= gs) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		double span = this.g[lo + 1] - this.g[lo];
		double f = span <= 0 ? 0 : Math.clamp((gs - this.g[lo]) / span, 0, 1);
		return this.s[lo] + (this.s[lo + 1] - this.s[lo]) * f;
	}

	public double duration() {
		return this.duration;
	}

	/** How much every keyed speed was multiplied by to fit the duration (1 when the speeds set it). */
	public double scale() {
		return this.scale;
	}

	public boolean keyedSpeeds() {
		return this.speed != null;
	}

	/** The distance travelled by time t. */
	public double distanceAt(double t) {
		t = Math.clamp(t, 0, this.duration);
		double at = this.keyAt[0];
		for (Leg leg : this.legs) {
			if (t < leg.start()) {
				return at;   // holding at the key before this stretch
			}
			if (t <= leg.end() && leg.end() > leg.start()) {
				double run = Ramps.run(t - leg.start(), leg.end() - leg.start(), leg.in(), leg.out());
				return Math.clamp(this.distanceForG(this.gAt(leg.from()) + this.scale * run), leg.from(), leg.to());
			}
			at = leg.to();
		}
		return at;
	}

	/** The camera's speed at time t, in blocks a second. */
	public double speedAt(double t) {
		for (Leg leg : this.legs) {
			if (t >= leg.start() && t <= leg.end() && leg.end() > leg.start()) {
				double share = Ramps.factor(t - leg.start(), leg.end() - leg.start(), leg.in(), leg.out());
				return this.scale * share * this.keyedSpeed(this.distanceAt(t));
			}
		}
		return 0;
	}

	public String describe() {
		StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "paced, in %.1fs out %.1fs", this.ramps.in(), this.ramps.out()));
		double held = 0;
		for (double h : this.holds) {
			held += h;
		}
		if (held > 0) {
			b.append(String.format(Locale.ROOT, ", holds %.1fs (settle %.1fs)", held, this.ramps.settle()));
		}
		if (this.speed != null && Math.abs(this.scale - 1) > 1e-6) {
			b.append(String.format(Locale.ROOT, ", keyed speeds x%.2f to fit %.1fs", this.scale, this.duration));
		}
		return b.toString();
	}
}
