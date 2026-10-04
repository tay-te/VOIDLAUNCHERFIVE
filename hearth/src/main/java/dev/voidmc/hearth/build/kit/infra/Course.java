package dev.voidmc.hearth.build.kit.infra;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * The centreline and corridor of a linear work (road, stair street, bridge, dock, walkway) in <b>world</b> coordinates,
 * exactly as the planner reserves them (PLANNING.md §7, binding):
 *
 * <ul>
 *   <li><b>Dense centreline.</b> Between consecutive vertices {@code a}, {@code b} with {@code n = max(|dx|, |dz|)}, step
 *   {@code i} of {@code n} is the column {@code (a.x + r(dx·i/n), a.z + r(dz·i/n))} at walking level
 *   {@code a.y + r(dy·i/n)}, {@code r(v) = floor(v + 1/2)}.</li>
 *   <li><b>Corridor.</b> A column belongs to a corridor of width {@code w} when the distance from its centre to the
 *   polyline through the vertices' column centres is less than {@code (w + 0.5) / 2}.</li>
 * </ul>
 *
 * <p>The rules are computed in world coordinates because rounding is not symmetric under the local frame's reflection;
 * an archetype draws the columns through {@link Lay}. Every corridor column is described once ({@link Col}): its nearest
 * centreline step (whose walking level it takes, as the planner's own maps do), its signed offset from the centreline
 * (positive on the right of the direction of travel), its chainage, whether it lies in the rounded cap beyond an end,
 * and how far it is from the corridor's side ({@code ring}: 0 on the side edge, 1 next to it...). Columns are listed in
 * a fixed order (by z, then x), so everything drawn from them is deterministic.
 */
public final class Course {
	private final int[] points;
	public final int width;
	private final int[] xs;
	private final int[] ys;
	private final int[] zs;
	private final double[] chain;
	private final double length;
	private final List<Col> cols;
	private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;

	/**
	 * One corridor column.
	 *
	 * @param k      the nearest dense centreline step (squared distance, first on ties)
	 * @param off    signed distance from the polyline, positive on the right hand of the direction of travel
	 * @param s      chainage: distance along the polyline to the column's foot
	 * @param beyond in the rounded cap past the first or last vertex
	 * @param ring   4-steps from the nearest side-edge column (0 = on the side edge); end caps do not count as sides
	 */
	public record Col(int x, int z, int k, double off, double s, boolean beyond, int ring) {
	}

	public Course(int[] points, int width) {
		if (points.length < 6 || points.length % 3 != 0) {
			throw new IllegalArgumentException("a course needs at least two x,y,z vertices");
		}
		this.points = points.clone();
		this.width = Math.max(1, width);
		int n = points.length / 3;
		// the dense centreline
		int total = 1;
		for (int v = 0; v + 1 < n; v++) {
			total += Math.max(Math.abs(x(v + 1) - x(v)), Math.abs(z(v + 1) - z(v)));
		}
		this.xs = new int[total];
		this.ys = new int[total];
		this.zs = new int[total];
		int k = 0;
		this.xs[0] = x(0);
		this.ys[0] = y(0);
		this.zs[0] = z(0);
		for (int v = 0; v + 1 < n; v++) {
			int dx = x(v + 1) - x(v);
			int dy = y(v + 1) - y(v);
			int dz = z(v + 1) - z(v);
			int steps = Math.max(Math.abs(dx), Math.abs(dz));
			for (int i = 1; i <= steps; i++) {
				k++;
				this.xs[k] = x(v) + lerp(dx, i, steps);
				this.ys[k] = y(v) + lerp(dy, i, steps);
				this.zs[k] = z(v) + lerp(dz, i, steps);
			}
		}
		// chainage at each vertex
		this.chain = new double[n];
		for (int v = 1; v < n; v++) {
			this.chain[v] = this.chain[v - 1] + Math.hypot(x(v) - x(v - 1), z(v) - z(v - 1));
		}
		this.length = this.chain[n - 1];
		// the corridor, in a fixed order
		List<long[]> found = new ArrayList<>();
		Long2IntOpenHashMap seen = new Long2IntOpenHashMap();
		corridor(points, this.width, (cx, cz) -> {
			long key = key(cx, cz);
			if (!seen.containsKey(key)) {
				seen.put(key, 1);
				found.add(new long[]{cz, cx});
			}
		});
		found.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		List<double[]> feet = new ArrayList<>(found.size());
		for (long[] f : found) {
			int cx = (int) f[1];
			int cz = (int) f[0];
			x0 = Math.min(x0, cx);
			z0 = Math.min(z0, cz);
			x1 = Math.max(x1, cx);
			z1 = Math.max(z1, cz);
			feet.add(foot(cx, cz));
		}
		this.minX = x0;
		this.minZ = z0;
		this.maxX = x1;
		this.maxZ = z1;
		for (int i = 0; i < found.size(); i++) {
			this.index.put(key((int) found.get(i)[1], (int) found.get(i)[0]), i);
		}
		// rings: side-edge columns have a 4-neighbour outside the corridor that is not past an end
		int[] ring = new int[found.size()];
		Arrays.fill(ring, Integer.MAX_VALUE);
		java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
		for (int i = 0; i < found.size(); i++) {
			int cx = (int) found.get(i)[1];
			int cz = (int) found.get(i)[0];
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int nx = cx + d.getStepX();
				int nz = cz + d.getStepZ();
				if (!this.index.containsKey(key(nx, nz)) && foot(nx, nz)[3] == 0) {
					ring[i] = 0;
					q.add(i);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			int i = q.poll();
			int cx = (int) found.get(i)[1];
			int cz = (int) found.get(i)[0];
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int j = this.index.getOrDefault(key(cx + d.getStepX(), cz + d.getStepZ()), -1);
				if (j >= 0 && ring[j] > ring[i] + 1) {
					ring[j] = ring[i] + 1;
					q.add(j);
				}
			}
		}
		List<Col> out = new ArrayList<>(found.size());
		for (int i = 0; i < found.size(); i++) {
			int cx = (int) found.get(i)[1];
			int cz = (int) found.get(i)[0];
			double[] ft = feet.get(i);
			out.add(new Col(cx, cz, nearest(cx, cz), ft[1], ft[2], ft[3] != 0, ring[i] == Integer.MAX_VALUE ? this.width : ring[i]));
		}
		this.cols = Collections.unmodifiableList(out);
	}

	/** {@code floor(d·i/n + 1/2)} in integers (the planner's rounding). */
	static int lerp(int d, int i, int n) {
		return Math.floorDiv(2 * d * i + n, 2 * n);
	}

	public static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	// ------------------------------------------------------------------------------------------------ vertices

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

	public int[] points() {
		return this.points.clone();
	}

	// ------------------------------------------------------------------------------------------------ dense line

	/** Number of dense centreline steps. */
	public int steps() {
		return this.xs.length;
	}

	public int dx(int k) {
		return this.xs[k];
	}

	public int dy(int k) {
		return this.ys[k];
	}

	public int dz(int k) {
		return this.zs[k];
	}

	/** Total length of the polyline between its first and last vertex (column centres). */
	public double length() {
		return this.length;
	}

	/** The nearest dense step to (x, z) by squared distance, first on ties. */
	public int nearest(int x, int z) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < this.xs.length; i++) {
			long ddx = this.xs[i] - x;
			long ddz = this.zs[i] - z;
			long dd = ddx * ddx + ddz * ddz;
			if (dd < bestD) {
				bestD = dd;
				best = i;
			}
		}
		return best;
	}

	/** The direction of travel at dense step k, as a unit vector {dx, dz} (from the neighbours either side). */
	public double[] heading(int k) {
		int a = Math.max(0, k - 2);
		int b = Math.min(this.xs.length - 1, k + 2);
		double hx = this.xs[b] - this.xs[a];
		double hz = this.zs[b] - this.zs[a];
		if (hx == 0 && hz == 0) {
			hx = x(vertexCount() - 1) - x(0);
			hz = z(vertexCount() - 1) - z(0);
		}
		double len = Math.hypot(hx, hz);
		return len == 0 ? new double[]{0, 1} : new double[]{hx / len, hz / len};
	}

	/** The world cardinal direction nearest the direction of travel at step k. */
	public Direction cardinal(int k) {
		double[] h = heading(k);
		return Math.abs(h[0]) >= Math.abs(h[1]) ? (h[0] >= 0 ? Direction.EAST : Direction.WEST) : (h[1] >= 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Whether the whole polyline runs along one axis (a straight run east-west or north-south). */
	public boolean straight() {
		boolean alongX = true;
		boolean alongZ = true;
		for (int v = 1; v < vertexCount(); v++) {
			alongX &= z(v) == z(0);
			alongZ &= x(v) == x(0);
		}
		return alongX || alongZ;
	}

	// ------------------------------------------------------------------------------------------------ corridor

	public List<Col> cols() {
		return this.cols;
	}

	public boolean contains(int x, int z) {
		return this.index.containsKey(key(x, z));
	}

	/** The index in {@link #cols()} of the column at (x, z), or -1 outside the corridor. */
	public int indexOf(int x, int z) {
		return this.index.getOrDefault(key(x, z), -1);
	}

	/** The column at (x, z), or null outside the corridor. */
	public Col col(int x, int z) {
		int i = this.index.getOrDefault(key(x, z), -1);
		return i < 0 ? null : this.cols.get(i);
	}

	/** The walking level the planner gives the column (its nearest dense step's). */
	public int level(Col c) {
		return this.ys[c.k()];
	}

	public int minX() {
		return this.minX;
	}

	public int minZ() {
		return this.minZ;
	}

	public int maxX() {
		return this.maxX;
	}

	public int maxZ() {
		return this.maxZ;
	}

	/** The highest walking level on the dense line. */
	public int topLevel() {
		int m = Integer.MIN_VALUE;
		for (int y : this.ys) {
			m = Math.max(m, y);
		}
		return m;
	}

	public int bottomLevel() {
		int m = Integer.MAX_VALUE;
		for (int y : this.ys) {
			m = Math.min(m, y);
		}
		return m;
	}

	/**
	 * The foot of the perpendicular from a column centre onto the polyline: {segment, signed offset, chainage, beyond
	 * (1 past the first vertex, 2 past the last, else 0)}.
	 */
	private double[] foot(int px, int pz) {
		int n = vertexCount();
		double best = Double.MAX_VALUE;
		double off = 0;
		double s = 0;
		int beyond = 0;
		int seg = 0;
		for (int v = 0; v + 1 < n; v++) {
			double ax = x(v);
			double az = z(v);
			double ex = x(v + 1) - ax;
			double ez = z(v + 1) - az;
			double len2 = ex * ex + ez * ez;
			double qx = px - ax;
			double qz = pz - az;
			double raw = len2 == 0 ? 0 : (qx * ex + qz * ez) / len2;
			double t = Math.max(0, Math.min(1, raw));
			double rx = qx - t * ex;
			double rz = qz - t * ez;
			double dd = rx * rx + rz * rz;
			if (dd < best - 1e-9) {
				best = dd;
				seg = v;
				double len = Math.sqrt(len2);
				double cross = len == 0 ? 0 : (ex * qz - ez * qx) / len;
				off = cross == 0 ? 0 : Math.signum(cross) * Math.sqrt(dd);
				if (raw >= 0 && raw <= 1) {
					off = cross;
				}
				s = this.chain[v] + t * len;
				beyond = v == 0 && raw < 0 ? 1 : v + 2 == n && raw > 1 ? 2 : 0;
			}
		}
		return new double[]{seg, off, s, beyond};
	}

	/** Visits every column of a corridor (columns may be visited more than once), exactly the planner's rule. */
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

	/**
	 * Densifies a polyline to one vertex per step (each consecutive pair one column apart), which reproduces itself under
	 * the dense rule: the form a generated default course takes.
	 */
	public static int[] denseOf(int[] xs, int[] ys, int[] zs) {
		int[] out = new int[xs.length * 3];
		for (int i = 0; i < xs.length; i++) {
			out[i * 3] = xs[i];
			out[i * 3 + 1] = ys[i];
			out[i * 3 + 2] = zs[i];
		}
		return out;
	}

	/** The dense centreline's columns between two world points (the dense rule applied to one run). */
	public static int[][] run(int ax, int az, int bx, int bz) {
		int n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
		int[] xs = new int[n + 1];
		int[] zs = new int[n + 1];
		for (int i = 0; i <= n; i++) {
			xs[i] = ax + (n == 0 ? 0 : lerp(bx - ax, i, n));
			zs[i] = az + (n == 0 ? 0 : lerp(bz - az, i, n));
		}
		return new int[][]{xs, zs};
	}
}
