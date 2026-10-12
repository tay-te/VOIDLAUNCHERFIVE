package dev.voidmc.reel.math;

/**
 * A smooth curve through (x, y) keys that never overshoots them (Fritsch and Carlson's monotone cubic): between
 * two keys it stays between their values, and it is flat at a key that is a peak or a trough. Used for every
 * scalar a shot keys (field of view, angles, timing), where a plain spline's overshoot would read as a wobble.
 */
public final class Monotone {
	private final double[] xs;
	private final double[] ys;
	private final double[] slopes;

	public Monotone(double[] xs, double[] ys) {
		int n = xs.length;
		if (n == 0 || n != ys.length) {
			throw new IllegalArgumentException("a monotone curve needs matching, non-empty keys");
		}
		for (int i = 1; i < n; i++) {
			if (!(xs[i] > xs[i - 1])) {
				throw new IllegalArgumentException("monotone curve keys must increase: " + xs[i - 1] + " then " + xs[i]);
			}
		}
		this.xs = xs.clone();
		this.ys = ys.clone();
		this.slopes = new double[n];
		if (n == 1) {
			return;
		}
		double[] secant = new double[n - 1];
		for (int i = 0; i < n - 1; i++) {
			secant[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
		}
		this.slopes[0] = secant[0];
		this.slopes[n - 1] = secant[n - 2];
		for (int i = 1; i < n - 1; i++) {
			this.slopes[i] = secant[i - 1] * secant[i] <= 0 ? 0 : (secant[i - 1] + secant[i]) / 2;
		}
		for (int i = 0; i < n - 1; i++) {
			if (secant[i] == 0) {
				this.slopes[i] = 0;
				this.slopes[i + 1] = 0;
				continue;
			}
			double a = this.slopes[i] / secant[i];
			double b = this.slopes[i + 1] / secant[i];
			double h = a * a + b * b;
			if (h > 9) {
				double k = 3 / Math.sqrt(h);
				this.slopes[i] = k * a * secant[i];
				this.slopes[i + 1] = k * b * secant[i];
			}
		}
	}

	public double at(double x) {
		int n = this.xs.length;
		if (n == 1 || x <= this.xs[0]) {
			return this.ys[0];
		}
		if (x >= this.xs[n - 1]) {
			return this.ys[n - 1];
		}
		int i = 0;
		while (x > this.xs[i + 1]) {
			i++;
		}
		double h = this.xs[i + 1] - this.xs[i];
		double t = (x - this.xs[i]) / h;
		double t2 = t * t;
		double t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * this.ys[i] + (t3 - 2 * t2 + t) * h * this.slopes[i]
			+ (-2 * t3 + 3 * t2) * this.ys[i + 1] + (t3 - t2) * h * this.slopes[i + 1];
	}
}
