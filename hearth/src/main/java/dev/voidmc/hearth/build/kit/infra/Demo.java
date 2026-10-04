package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.build.draft.Draft;
import java.util.Arrays;

/**
 * Default geometry for a linear work drawn without the planner's {@code points} (unit tests, previews,
 * {@code /hearth build}): a course from the back of the plot to the front (the planner's convention: the first point is
 * at the back and the plot faces its last), optionally bending once, graded to the land the way the planner grades a
 * road (smoothed ground, clamped to a grade forward and back, rounded, never more than a block between neighbours),
 * meeting the street in front of the plot at its level and kept inside the plot's height.
 */
public final class Demo {
	private Demo() {
	}

	/**
	 * A course across the plot, as dense points (one vertex per step).
	 *
	 * @param grade the steepest rise per block (0.3 a road, 1.0 a stair street)
	 * @param bend  whether the course bends once on its way
	 */
	public static int[] course(Lay lay, int width, double grade, boolean bend) {
		Draft d = lay.draft();
		int w = d.frontage();
		int dp = d.depth();
		int h = (width - 1) / 2;
		int lo = Math.min(h, (w - 1) / 2);
		int hi = Math.max(lo, w - 1 - h);
		int cx = Math.clamp(w / 2, lo, hi);
		int bx = cx;
		int mx = cx;
		if (bend && w >= width + 4) {
			bx = Math.clamp(cx + d.between(-w / 3, w / 3), lo, hi);
			mx = Math.clamp((bx + cx) / 2 + d.between(-2, 2), lo, hi);
		}
		int[][] local = bend && dp >= 8 ? new int[][]{{bx, dp - 1}, {mx, dp / 2}, {cx, 0}} : new int[][]{{bx, dp - 1}, {cx, 0}};
		int[] xs = new int[0];
		int[] zs = new int[0];
		for (int v = 0; v + 1 < local.length; v++) {
			int[][] r = Course.run(lay.wx(local[v][0], local[v][1]), lay.wz(local[v][0], local[v][1]), lay.wx(local[v + 1][0], local[v + 1][1]),
				lay.wz(local[v + 1][0], local[v + 1][1]));
			int skip = v == 0 ? 0 : 1;
			xs = concat(xs, Arrays.copyOfRange(r[0], skip, r[0].length));
			zs = concat(zs, Arrays.copyOfRange(r[1], skip, r[1].length));
		}
		int street = lay.surface(lay.wx(cx, -1), lay.wz(cx, -1));
		int[] ys = grade(lay, xs, zs, grade, street);
		return Course.denseOf(xs, ys, zs);
	}

	/** Walking levels along a dense line: the planner's grading, the last level pinned to {@code pinLast}. */
	public static int[] grade(Lay lay, int[] xs, int[] zs, double grade, int pinLast) {
		int n = xs.length;
		double[] g = new double[n];
		for (int i = 0; i < n; i++) {
			g[i] = lay.surface(xs[i], zs[i]);
		}
		double[] t = new double[n];
		for (int i = 0; i < n; i++) {
			double sum = 0;
			int cnt = 0;
			for (int k = Math.max(0, i - 2); k <= Math.min(n - 1, i + 2); k++) {
				sum += g[k] * (k == i ? 2 : 1);
				cnt += k == i ? 2 : 1;
			}
			t[i] = sum / cnt;
		}
		t[n - 1] = pinLast;
		for (int pass = 0; pass < 2; pass++) {
			for (int i = n - 2; i >= 0; i--) {
				t[i] = Math.max(t[i + 1] - grade, Math.min(t[i + 1] + grade, t[i]));
			}
			for (int i = 1; i < n - 1; i++) {
				t[i] = Math.max(t[i - 1] - grade, Math.min(t[i - 1] + grade, t[i]));
			}
		}
		int floor = lay.floorY();
		int top = floor + lay.draft().maxHeight() - 5;
		int[] ys = new int[n];
		for (int i = 0; i < n; i++) {
			ys[i] = Math.clamp((int) Math.floor(t[i] + 0.5), floor - 8, Math.max(floor, top));
		}
		ys[n - 1] = Math.clamp(pinLast, floor - 8, Math.max(floor, top));
		for (int pass = 0; pass < 3; pass++) {
			for (int i = n - 2; i >= 0; i--) {
				ys[i] = Math.max(ys[i + 1] - 1, Math.min(ys[i + 1] + 1, ys[i]));
			}
			for (int i = 1; i < n; i++) {
				ys[i] = Math.max(ys[i - 1] - 1, Math.min(ys[i - 1] + 1, ys[i]));
			}
		}
		return ys;
	}

	static int[] concat(int[] a, int[] b) {
		int[] r = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}

	/** The {@code points} param, or null when it is missing or malformed. */
	public static int[] points(Draft d) {
		int[] p = d.params().getIntArray("points").orElse(null);
		return p == null || p.length < 6 || p.length % 3 != 0 ? null : p;
	}

	/** An odd width of at least one. */
	public static int odd(int w) {
		int v = Math.max(1, w);
		return (v & 1) == 0 ? v - 1 : v;
	}
}
