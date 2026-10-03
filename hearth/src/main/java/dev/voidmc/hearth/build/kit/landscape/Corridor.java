package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The ground a linear plot (a town wall, a palisade) stands on, read from the planner's polyline (PLANNING.md §7: the
 * {@code points} param, world coordinates at walking level, the dense-centreline and corridor rules) into the plot's
 * local frame. It knows, for every column of the corridor, which step of the centreline it belongs to, how far along
 * the line that is, and how far to which side: positive offsets are the town side.
 *
 * <p>The town side is the {@code heart} param's side when it is given (int array {x, z}), else the right-hand side
 * walking from the first point to the last: the planner lays its rings clockwise seen from above, so the heart is on
 * the right. Columns inside a {@code keep} rectangle (int array x0, z0, x1, z1, ... world, inclusive: towers and
 * gatehouses standing on the line) are left out. Without {@code points} (previews, tests) the line runs down the
 * middle of the plot from the back to the front, sometimes with a bend, as the planner's would.
 */
public final class Corridor {
	/** Half a block of slack at each end: neighbouring segments meet without a gap. */
	private static final double END_SLACK = 0.5;

	private final Draft d;
	public final int width;
	/** Dense centreline, local columns and the natural ground (standing level, relative) under them. */
	public final int[] lx;
	public final int[] lz;
	public final int[] ground;
	/** Cumulative distance along the line at each dense step. */
	public final double[] along;
	public final List<Cell> cells;
	private final Long2ObjectOpenHashMap<Cell> byPos = new Long2ObjectOpenHashMap<>();
	private final int[] keep;
	private final boolean openStart;
	private final boolean openEnd;

	/**
	 * One column of the corridor.
	 *
	 * @param index   the centreline step it belongs to
	 * @param t       distance along the line of its nearest point
	 * @param offset  signed distance from the line (positive: the town side)
	 * @param walkBand how far from the line a column still counts as on it (a four-connected band of the line's
	 *                 direction: half a block across a straight line, a little more across a diagonal)
	 * @param beyond  past an end of the line (the rounded cap of the corridor)
	 * @param inward  the local direction from this column toward the line (null on the line)
	 * @param ahead   the local direction the line runs here (first point toward last), rounded to a side
	 */
	public record Cell(int x, int z, int index, double t, double offset, double walkBand, int beyond, @Nullable Direction inward, Direction ahead,
		double straightness) {
		public boolean onLine() {
			return Math.abs(this.offset) <= this.walkBand;
		}

		public boolean town() {
			return this.offset > this.walkBand;
		}

		public boolean field() {
			return this.offset < -this.walkBand;
		}
	}

	public Corridor(Draft d, int defaultWidth) {
		this.d = d;
		this.width = Math.max(1, d.param("width", defaultWidth));
		LocalFrame f = d.frame();
		int[] pts = d.params().getIntArray("points").orElse(null);
		if (pts == null || pts.length < 6 || pts.length % 3 != 0) {
			pts = defaultLine(d, f);
		}
		this.keep = d.params().getIntArray("keep").orElse(new int[0]);
		this.openStart = d.param("join_start", false);
		this.openEnd = d.param("join_end", false);
		int nv = pts.length / 3;
		// the dense centreline
		List<int[]> dense = new ArrayList<>();
		dense.add(new int[]{pts[0], pts[2]});
		int[] segBase = new int[nv];
		int[] segSteps = new int[nv];
		for (int v = 0; v + 1 < nv; v++) {
			int ax = pts[v * 3];
			int az = pts[v * 3 + 2];
			int dx = pts[v * 3 + 3] - ax;
			int dz = pts[v * 3 + 5] - az;
			int n = Math.max(Math.abs(dx), Math.abs(dz));
			segBase[v] = dense.size() - 1;
			segSteps[v] = n;
			for (int i = 1; i <= n; i++) {
				dense.add(new int[]{ax + r(dx * (double) i / n), az + r(dz * (double) i / n)});
			}
		}
		int n = dense.size();
		this.lx = new int[n];
		this.lz = new int[n];
		this.ground = new int[n];
		this.along = new double[n];
		for (int i = 0; i < n; i++) {
			int[] c = dense.get(i);
			this.lx[i] = f.localX(c[0], c[1]);
			this.lz[i] = f.localZ(c[0], c[1]);
			this.ground[i] = d.site().ground(this.lx[i], this.lz[i]);
			if (i > 0) {
				this.along[i] = this.along[i - 1] + Math.hypot(this.lx[i] - this.lx[i - 1], this.lz[i] - this.lz[i - 1]);
			}
		}
		// which side is the town: the heart's side, else the right of travel
		int insideSign = 1;
		int[] heart = d.params().getIntArray("heart").orElse(null);
		if (heart != null && heart.length >= 2) {
			int v = Math.max(0, nv / 2 - 1);
			double s = side(pts[v * 3], pts[v * 3 + 2], pts[v * 3 + 3], pts[v * 3 + 5], heart[0], heart[1]);
			insideSign = s >= 0 ? 1 : -1;
		}
		// the corridor
		double reach = (this.width + 0.5) / 2;
		List<Cell> out = new ArrayList<>();
		double[] segStart = new double[nv];
		for (int v = 1; v < nv; v++) {
			segStart[v] = segStart[v - 1] + Math.hypot(pts[v * 3] - pts[v * 3 - 3], pts[v * 3 + 2] - pts[v * 3 - 1]);
		}
		for (int z = 0; z < d.depth(); z++) {
			for (int x = 0; x < d.frontage(); x++) {
				int wx = f.worldX(x, z);
				int wz = f.worldZ(x, z);
				double best = Double.MAX_VALUE;
				int bv = 0;
				double bt = 0;
				double braw = 0;
				for (int v = 0; v + 1 < Math.max(2, nv); v++) {
					double ax = pts[v * 3];
					double az = pts[v * 3 + 2];
					double bx = pts[v * 3 + 3];
					double bz = pts[v * 3 + 5];
					double ddx = bx - ax;
					double ddz = bz - az;
					double len2 = ddx * ddx + ddz * ddz;
					double raw = len2 == 0 ? 0 : ((wx - ax) * ddx + (wz - az) * ddz) / len2;
					double t = Math.clamp(raw, 0, 1);
					double px = ax + ddx * t;
					double pz = az + ddz * t;
					double dist = Math.hypot(wx - px, wz - pz);
					if (dist < best - 1e-9) {
						best = dist;
						bv = v;
						bt = t;
						braw = raw;
					}
				}
				if (best >= reach) {
					continue;
				}
				double ax = pts[bv * 3];
				double az = pts[bv * 3 + 2];
				double bx = pts[bv * 3 + 3];
				double bz = pts[bv * 3 + 5];
				double segLen = Math.hypot(bx - ax, bz - az);
				int beyond = 0;
				if (bv == 0 && braw < 0) {
					if (-braw * segLen > END_SLACK) {
						continue;
					}
					beyond = -1;
				}
				if (bv == nv - 2 && braw > 1) {
					if ((braw - 1) * segLen > END_SLACK) {
						continue;
					}
					beyond = 1;
				}
				if (inKeep(wx, wz)) {
					continue;
				}
				double s = side(ax, az, bx, bz, wx, wz) * insideSign;
				double signed = Math.abs(s) < 1e-9 ? 0 : Math.copySign(best, s);
				int index = Math.clamp(segBase[bv] + (int) Math.round(bt * segSteps[bv]), 0, n - 1);
				double cos = segLen == 0 ? 1 : Math.abs(bx - ax) / segLen;
				double sin = segLen == 0 ? 0 : Math.abs(bz - az) / segLen;
				double band = 0.5 * (cos + sin) + 0.02;
				// local ends of the nearest segment (the frame is affine, so points along it map along it)
				int lax = f.localX((int) ax, (int) az);
				int laz = f.localZ((int) ax, (int) az);
				int ex = f.localX((int) bx, (int) bz) - lax;
				int ez = f.localZ((int) bx, (int) bz) - laz;
				Direction inward = null;
				if (Math.abs(signed) > band) {
					double lpx = lax + ex * bt;
					double lpz = laz + ez * bt;
					inward = Dirs.nearest(lpx - x, lpz - z);
				}
				Direction ahead = Dirs.nearest(ex, ez);
				Cell c = new Cell(x, z, index, segStart[bv] + bt * segLen, signed, band, beyond, inward, ahead, Math.max(cos, sin));
				out.add(c);
				this.byPos.put(Draft.key(x, 0, z), c);
			}
		}
		out.sort((a, b) -> a.index != b.index ? Integer.compare(a.index, b.index) : Double.compare(a.offset, b.offset));
		this.cells = Collections.unmodifiableList(out);
	}

	private static int r(double v) {
		return (int) Math.floor(v + 0.5);
	}

	/** Positive when (px, pz) is right of the way from a to b (world: x east, z south). */
	private static double side(double ax, double az, double bx, double bz, double px, double pz) {
		double tx = bx - ax;
		double tz = bz - az;
		return (px - ax) * -tz + (pz - az) * tx;
	}

	private boolean inKeep(int wx, int wz) {
		for (int k = 0; k + 3 < this.keep.length; k += 4) {
			if (wx >= Math.min(this.keep[k], this.keep[k + 2]) && wx <= Math.max(this.keep[k], this.keep[k + 2])
				&& wz >= Math.min(this.keep[k + 1], this.keep[k + 3]) && wz <= Math.max(this.keep[k + 1], this.keep[k + 3])) {
				return true;
			}
		}
		return false;
	}

	/** The line down the middle of the plot from the back to the front, bent by seed where the plot is wide enough. */
	private static int[] defaultLine(Draft d, LocalFrame f) {
		int w = d.frontage();
		int dp = d.depth();
		int c = (w - 1) / 2;
		int half = d.param("width", 3) / 2 + 1;
		int room = Math.min(c - half, w - 1 - half - c);
		List<int[]> local = new ArrayList<>();
		local.add(new int[]{c, dp - 1});
		if (room >= 1 && dp >= 10) {
			double u = d.random().nextDouble();
			if (u < 0.35) {
				// a dog-leg: across and on
				int off = (d.random().nextBoolean() ? 1 : -1) * Math.min(room, 2);
				local.add(new int[]{c, dp * 2 / 3});
				local.add(new int[]{c + off, dp / 3});
			} else if (u < 0.6) {
				int off = (d.random().nextBoolean() ? 1 : -1) * Math.min(room, 3);
				local.set(0, new int[]{c - off, dp - 1});
				local.add(new int[]{c + off, dp / 2});
			}
		}
		local.add(new int[]{local.get(local.size() - 1)[0], 0});
		// to world, at most 8 apart (the polyline rule)
		List<Integer> out = new ArrayList<>();
		for (int k = 0; k < local.size(); k++) {
			int[] p = local.get(k);
			if (k > 0) {
				int[] a = local.get(k - 1);
				int n = Math.max(Math.abs(p[0] - a[0]), Math.abs(p[1] - a[1]));
				int steps = (n + 7) / 8;
				for (int s = 1; s < steps; s++) {
					int ix = a[0] + (p[0] - a[0]) * s / steps;
					int iz = a[1] + (p[1] - a[1]) * s / steps;
					add(out, d, f, ix, iz);
				}
			}
			add(out, d, f, p[0], p[1]);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private static void add(List<Integer> out, Draft d, LocalFrame f, int x, int z) {
		out.add(f.worldX(x, z));
		out.add(d.site().floorY() + d.site().ground(x, z));
		out.add(f.worldZ(x, z));
	}

	public int length() {
		return this.lx.length;
	}

	public @Nullable Cell at(int x, int z) {
		return this.byPos.get(Draft.key(x, 0, z));
	}

	/** Whether the start (first point) or the end of the line continues into a neighbour (a kept tower, or a join). */
	public boolean open(boolean start) {
		if (start ? this.openStart : this.openEnd) {
			return true;
		}
		int i = start ? 0 : length() - 1;
		int j = start ? Math.min(1, length() - 1) : Math.max(0, length() - 2);
		LocalFrame f = this.d.frame();
		for (int k = 1; k <= 2; k++) {
			int cx = this.lx[i] + (this.lx[i] - this.lx[j]) * k;
			int cz = this.lz[i] + (this.lz[i] - this.lz[j]) * k;
			if (inKeep(f.worldX(cx, cz), f.worldZ(cx, cz))) {
				return true;
			}
		}
		return false;
	}

	/** The cells of one centreline step. */
	public List<Cell> step(int index) {
		List<Cell> out = new ArrayList<>();
		for (Cell c : this.cells) {
			if (c.index == index) {
				out.add(c);
			}
		}
		return out;
	}

	/** The local direction the line runs at a step (toward the last point), as a side. */
	public Direction ahead(int i) {
		int a = Math.max(0, i - 1);
		int b = Math.min(length() - 1, i + 1);
		if (a == b) {
			return Dirs.FRONT;
		}
		return Dirs.nearest(this.lx[b] - this.lx[a], this.lz[b] - this.lz[a]);
	}
}
