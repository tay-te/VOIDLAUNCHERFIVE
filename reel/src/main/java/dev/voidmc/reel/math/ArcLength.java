package dev.voidmc.reel.math;

/**
 * The distance travelled along a spline, tabulated so a camera can be placed by distance rather than by spline
 * parameter: a spline's parameter runs fast on long segments and slow on short ones, and a move timed by it
 * lurches. Timed by distance, it glides.
 */
public final class ArcLength {
	private static final int STEPS_PER_SEGMENT = 256;

	private final double[] u;
	private final double[] s;

	public ArcLength(Spline spline) {
		int segments = spline.segments();
		int count = Math.max(1, segments * STEPS_PER_SEGMENT);
		this.u = new double[count + 1];
		this.s = new double[count + 1];
		Vec3 last = spline.at(0);
		for (int i = 1; i <= count; i++) {
			double ui = segments == 0 ? 0 : (double) i * segments / count;
			Vec3 p = spline.at(ui);
			this.u[i] = ui;
			this.s[i] = this.s[i - 1] + p.distance(last);
			last = p;
		}
	}

	public double total() {
		return this.s[this.s.length - 1];
	}

	/** The distance travelled by spline parameter u. */
	public double distanceAt(double u) {
		if (this.u[this.u.length - 1] == 0) {
			return 0;
		}
		return interpolate(this.u, this.s, search(this.u, u), u);
	}

	/** The spline parameter at which the curve has covered distance s. */
	public double parameterAt(double s) {
		if (this.total() == 0) {
			return 0;
		}
		return interpolate(this.s, this.u, search(this.s, s), s);
	}

	private static int search(double[] xs, double x) {
		int lo = 0;
		int hi = xs.length - 2;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (xs[mid] <= x) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	private static double interpolate(double[] xs, double[] ys, int i, double x) {
		double span = xs[i + 1] - xs[i];
		double f = span <= 0 ? 0 : Math.clamp((x - xs[i]) / span, 0, 1);
		return ys[i] + (ys[i + 1] - ys[i]) * f;
	}
}
