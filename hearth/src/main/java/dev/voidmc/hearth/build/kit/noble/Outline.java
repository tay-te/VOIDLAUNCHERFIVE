package dev.voidmc.hearth.build.kit.noble;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline of a ward that is not a rectangle: a closed polygon through cell centres (local x, z), as a castle's
 * curtain turns where the hill turns, a ring fort runs round its knoll, a bastion juts its point at the field. Cells
 * are inside when their centre lies inside or on the polygon; {@link Curtain#enclose} walls it, {@link Court#fill}
 * levels and paves what it encloses.
 */
public final class Outline {
	private final int[] xs;
	private final int[] zs;
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;

	private Outline(int[] xs, int[] zs) {
		this.xs = xs;
		this.zs = zs;
		int ax = Integer.MAX_VALUE;
		int az = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		for (int i = 0; i < xs.length; i++) {
			ax = Math.min(ax, xs[i]);
			az = Math.min(az, zs[i]);
			bx = Math.max(bx, xs[i]);
			bz = Math.max(bz, zs[i]);
		}
		this.x0 = ax;
		this.z0 = az;
		this.x1 = bx;
		this.z1 = bz;
	}

	/** A polygon through the corners given as x, z pairs, in order round it (either way). */
	public static Outline of(int... xz) {
		int n = xz.length / 2;
		int[] xs = new int[n];
		int[] zs = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = xz[2 * i];
			zs[i] = xz[2 * i + 1];
		}
		return new Outline(xs, zs);
	}

	/** A polygon through a list of {x, z} corners. */
	public static Outline of(List<int[]> corners) {
		int[] xs = new int[corners.size()];
		int[] zs = new int[corners.size()];
		for (int i = 0; i < xs.length; i++) {
			xs[i] = corners.get(i)[0];
			zs[i] = corners.get(i)[1];
		}
		return new Outline(xs, zs);
	}

	/** A rectangle (corners inclusive). */
	public static Outline box(int x0, int z0, int x1, int z1) {
		return of(x0, z0, x1, z0, x1, z1, x0, z1);
	}

	/**
	 * An oval of {@code n} corners round (cx, cz) with half-axes rx and rz, each corner pushed in or out by up to
	 * {@code wobble} of its radius by {@code seed} (a ring wall laid out by eye on a knoll, not with compasses).
	 */
	public static Outline oval(double cx, double cz, double rx, double rz, int n, double wobble, long seed) {
		List<int[]> cs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + Math.PI / n;
			double k = 1 + wobble * (dev.voidmc.hearth.build.draft.Noise.unit(seed, i, 7, 3) * 2 - 1);
			int x = (int) Math.round(cx + Math.cos(a) * rx * k);
			int z = (int) Math.round(cz + Math.sin(a) * rz * k);
			if (cs.isEmpty() || cs.get(cs.size() - 1)[0] != x || cs.get(cs.size() - 1)[1] != z) {
				cs.add(new int[]{x, z});
			}
		}
		return of(cs);
	}

	public int corners() {
		return this.xs.length;
	}

	public int x(int i) {
		return this.xs[Math.floorMod(i, this.xs.length)];
	}

	public int z(int i) {
		return this.zs[Math.floorMod(i, this.zs.length)];
	}

	/** Whether the cell's centre lies inside the polygon or on its edge. */
	public boolean inside(int x, int z) {
		if (x < this.x0 || x > this.x1 || z < this.z0 || z > this.z1) {
			return false;
		}
		if (edge(x, z) < 0.01) {
			return true;
		}
		boolean in = false;
		int n = this.xs.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			int xi = this.xs[i];
			int zi = this.zs[i];
			int xj = this.xs[j];
			int zj = this.zs[j];
			if (zi > z != zj > z) {
				double cross = (double) (xj - xi) * (z - zi) / (zj - zi) + xi;
				if (x < cross) {
					in = !in;
				}
			}
		}
		return in;
	}

	/** The distance from a cell's centre to the nearest edge of the polygon. */
	public double edge(int x, int z) {
		double best = Double.MAX_VALUE;
		int n = this.xs.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			best = Math.min(best, segment(x, z, this.xs[j], this.zs[j], this.xs[i], this.zs[i]));
		}
		return best;
	}

	/** Whether the cell is in the wall of a curtain {@code thick} deep laid inside the outline. */
	public boolean wall(int x, int z, int thick) {
		return inside(x, z) && edge(x, z) < thick - 0.5;
	}

	/** Whether the cell is inside the outline and clear of a wall {@code thick} deep (the ward's own ground). */
	public boolean ward(int x, int z, int thick) {
		return inside(x, z) && edge(x, z) >= thick - 0.5;
	}

	/** The x extent {lo, hi} of the cells of row z inside the outline, or null if none. */
	public int[] span(int z) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int x = this.x0; x <= this.x1; x++) {
			if (inside(x, z)) {
				lo = Math.min(lo, x);
				hi = Math.max(hi, x);
			}
		}
		return lo > hi ? null : new int[]{lo, hi};
	}

	/** The middle of the polygon's corners. */
	public double[] centre() {
		double sx = 0;
		double sz = 0;
		for (int i = 0; i < this.xs.length; i++) {
			sx += this.xs[i];
			sz += this.zs[i];
		}
		return new double[]{sx / this.xs.length, sz / this.xs.length};
	}

	private static double segment(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double len = vx * vx + vz * vz;
		double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len));
		double dx = px - (ax + t * vx);
		double dz = pz - (az + t * vz);
		return Math.sqrt(dx * dx + dz * dz);
	}
}
