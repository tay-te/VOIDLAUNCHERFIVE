package dev.voidmc.hearth.plan.road;

import java.util.Arrays;

/**
 * A road's centreline: vertices {@code x,y,z,...} in world block coordinates, y at walking level. This is exactly the
 * {@code points} param of the infrastructure archetypes, and these rules (PLANNING.md, "Infrastructure params") are the
 * contract the infrastructure blueprints build to:
 *
 * <ul>
 *   <li><b>Dense centreline.</b> Between consecutive vertices {@code a} and {@code b}, with {@code n = max(|dx|, |dz|)},
 *   step {@code i} of {@code n} is the column {@code (a.x + r(dx·i/n), a.z + r(dz·i/n))} at walking level
 *   {@code a.y + r(dy·i/n)}, where {@code r(v) = floor(v + 1/2)}. Consecutive vertices are at most
 *   {@link #MAX_RUN} blocks apart.</li>
 *   <li><b>Corridor.</b> A column belongs to a road of width {@code w} when the distance from its centre to the
 *   polyline through the vertices' column centres is less than {@code (w + 0.5) / 2}. Widths are odd, so a straight
 *   road is exactly {@code w} columns across and a diagonal one is as wide.</li>
 * </ul>
 */
public final class Polyline {
	/** The longest run between two vertices, so the walking level is spelled out at least this often. */
	public static final int MAX_RUN = 8;

	private final int[] points;

	public Polyline(int[] points) {
		if (points.length < 6 || points.length % 3 != 0) {
			throw new IllegalArgumentException("a polyline needs at least two x,y,z vertices");
		}
		this.points = points.clone();
	}

	public int[] points() {
		return this.points.clone();
	}

	public int vertexCount() {
		return this.points.length / 3;
	}

	public int x(int v) {
		return this.points[v * 3];
	}

	public int y(int v) {
		return this.points[v * 3 + 1];
	}

	public int z(int v) {
		return this.points[v * 3 + 2];
	}

	/** The dense centreline, one column per step (see the class comment). */
	public Dense dense() {
		int n = vertexCount();
		int total = 1;
		for (int v = 0; v + 1 < n; v++) {
			total += Math.max(Math.abs(x(v + 1) - x(v)), Math.abs(z(v + 1) - z(v)));
		}
		int[] xs = new int[total];
		int[] ys = new int[total];
		int[] zs = new int[total];
		int k = 0;
		xs[0] = x(0);
		ys[0] = y(0);
		zs[0] = z(0);
		for (int v = 0; v + 1 < n; v++) {
			int dx = x(v + 1) - x(v);
			int dy = y(v + 1) - y(v);
			int dz = z(v + 1) - z(v);
			int steps = Math.max(Math.abs(dx), Math.abs(dz));
			for (int i = 1; i <= steps; i++) {
				k++;
				xs[k] = x(v) + lerp(dx, i, steps);
				ys[k] = y(v) + lerp(dy, i, steps);
				zs[k] = z(v) + lerp(dz, i, steps);
			}
		}
		return new Dense(xs, ys, zs);
	}

	/** {@code floor(d·i/n + 1/2)} in integers. */
	static int lerp(int d, int i, int n) {
		return Math.floorDiv(2 * d * i + n, 2 * n);
	}

	/**
	 * The fewest vertices that reproduce a dense path exactly under the dense rule: a vertex wherever the path turns,
	 * changes grade in a way a straight run would not reproduce, or has run {@link #MAX_RUN} blocks.
	 */
	public static Polyline fromDense(int[] xs, int[] ys, int[] zs, int from, int to) {
		int[] out = new int[(to - from + 1) * 3];
		int n = 0;
		int a = from;
		out[n++] = xs[a];
		out[n++] = ys[a];
		out[n++] = zs[a];
		while (a < to) {
			int best = a + 1;
			for (int b = a + 2; b <= to && b - a <= MAX_RUN; b++) {
				if (!reproduces(xs, ys, zs, a, b)) {
					break;
				}
				best = b;
			}
			out[n++] = xs[best];
			out[n++] = ys[best];
			out[n++] = zs[best];
			a = best;
		}
		return new Polyline(Arrays.copyOf(out, n));
	}

	private static boolean reproduces(int[] xs, int[] ys, int[] zs, int a, int b) {
		int dx = xs[b] - xs[a];
		int dy = ys[b] - ys[a];
		int dz = zs[b] - zs[a];
		int steps = Math.max(Math.abs(dx), Math.abs(dz));
		if (steps != b - a) {
			return false;
		}
		for (int i = 1; i < steps; i++) {
			if (xs[a + i] != xs[a] + lerp(dx, i, steps) || zs[a + i] != zs[a] + lerp(dz, i, steps)
				|| ys[a + i] != ys[a] + lerp(dy, i, steps)) {
				return false;
			}
		}
		return true;
	}

	/** Visits every column of the corridor of a polyline of the given width (columns may be visited more than once). */
	public void corridor(int width, ColumnVisitor visitor) {
		corridor(this.points, width, visitor);
	}

	public static void corridor(int[] points, int width, ColumnVisitor visitor) {
		double limit = (width + 0.5) / 2.0;
		double limit2 = limit * limit;
		int reach = (int) Math.ceil(limit);
		int n = points.length / 3;
		if (n == 1) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dx = -reach; dx <= reach; dx++) {
					if (dx * dx + dz * dz < limit2) {
						visitor.visit(points[0] + dx, points[2] + dz);
					}
				}
			}
			return;
		}
		for (int v = 0; v + 1 < n; v++) {
			int ax = points[v * 3];
			int az = points[v * 3 + 2];
			int bx = points[v * 3 + 3];
			int bz = points[v * 3 + 5];
			int x0 = Math.min(ax, bx) - reach;
			int x1 = Math.max(ax, bx) + reach;
			int z0 = Math.min(az, bz) - reach;
			int z1 = Math.max(az, bz) + reach;
			double ex = bx - ax;
			double ez = bz - az;
			double len2 = ex * ex + ez * ez;
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					double px = x - ax;
					double pz = z - az;
					double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * ex + pz * ez) / len2));
					double qx = px - t * ex;
					double qz = pz - t * ez;
					if (qx * qx + qz * qz < limit2) {
						visitor.visit(x, z);
					}
				}
			}
		}
	}

	@FunctionalInterface
	public interface ColumnVisitor {
		void visit(int x, int z);
	}

	/** A dense centreline: one column per step. */
	public record Dense(int[] xs, int[] ys, int[] zs) {
		public int length() {
			return this.xs.length;
		}

		/** The index of the centreline column nearest (x, z) (squared horizontal distance; first on ties). */
		public int nearest(int x, int z) {
			int best = 0;
			long bestD = Long.MAX_VALUE;
			for (int i = 0; i < this.xs.length; i++) {
				long dx = this.xs[i] - x;
				long dz = this.zs[i] - z;
				long d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = i;
				}
			}
			return best;
		}

		public long distance2(int i, int x, int z) {
			long dx = this.xs[i] - x;
			long dz = this.zs[i] - z;
			return dx * dx + dz * dz;
		}
	}
}
