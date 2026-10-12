package dev.voidmc.reel.math;

import java.util.List;

/**
 * A centripetal Catmull-Rom curve through every point, parameterised by u from 0 (the first point) to n-1 (the
 * last). Centripetal (alpha 0.5) is the variant that never loops or cusps between close points, so a camera path
 * drawn through a few positions passes through each of them and bends no more than it has to. The ends are
 * extended by reflection, so the curve leaves the first point heading for the second.
 */
public final class Spline {
	private static final double ALPHA = 0.5;
	private static final double EPSILON = 1e-4;

	private final Vec3[] points;

	public Spline(List<Vec3> points) {
		if (points.isEmpty()) {
			throw new IllegalArgumentException("a spline needs at least one point");
		}
		this.points = points.toArray(Vec3[]::new);
	}

	/** The largest u: the number of segments. */
	public int segments() {
		return this.points.length - 1;
	}

	public Vec3 point(int i) {
		return this.points[i];
	}

	public Vec3 at(double u) {
		int n = this.points.length;
		if (n == 1) {
			return this.points[0];
		}
		u = Math.clamp(u, 0, n - 1);
		int i = Math.min((int) Math.floor(u), n - 2);
		double t = u - i;
		Vec3 p1 = this.points[i];
		Vec3 p2 = this.points[i + 1];
		Vec3 p0 = i > 0 ? this.points[i - 1] : p1.add(p1.sub(p2));
		Vec3 p3 = i + 2 < n ? this.points[i + 2] : p2.add(p2.sub(p1));
		return segment(p0, p1, p2, p3, t);
	}

	/** Barry and Goldman's pyramid for one segment, from p1 (t 0) to p2 (t 1). */
	static Vec3 segment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double t0 = 0;
		double t1 = t0 + knot(p0, p1);
		double t2 = t1 + knot(p1, p2);
		double t3 = t2 + knot(p2, p3);
		double tt = t1 + (t2 - t1) * t;
		Vec3 a1 = mix(p0, p1, t0, t1, tt);
		Vec3 a2 = mix(p1, p2, t1, t2, tt);
		Vec3 a3 = mix(p2, p3, t2, t3, tt);
		Vec3 b1 = mix(a1, a2, t0, t2, tt);
		Vec3 b2 = mix(a2, a3, t1, t3, tt);
		return mix(b1, b2, t1, t2, tt);
	}

	private static double knot(Vec3 a, Vec3 b) {
		return Math.max(Math.pow(a.distance(b), ALPHA), EPSILON);
	}

	private static Vec3 mix(Vec3 a, Vec3 b, double ta, double tb, double t) {
		return a.lerp(b, (t - ta) / (tb - ta));
	}
}
