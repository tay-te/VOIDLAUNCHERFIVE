package dev.voidmc.hearth.plan.road;

import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Routes a road over the heightfield: A* on a grid of {@code cell}-block squares in eight directions, the search state
 * carrying the heading so turns cost something and roads run in long straight lines that bend where the land asks.
 * Steps cost their length, more as they climb (a road prefers the contour), much more as stairs; water costs a bridge;
 * planned plots block; existing roads are cheap to reuse. Deterministic: ties break on insertion order.
 */
public final class Router {
	private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};

	private Router() {
	}

	/**
	 * @param cell        grid size in blocks (1 for short local roads, 4 for long arms and tracks)
	 * @param margin      how far beyond the start–goal box the search may wander, in blocks
	 * @param roadGrade   grade a road takes without stairs
	 * @param stairGrade  steepest grade allowed at all (stairs above {@code roadGrade}); equal to roadGrade for no stairs
	 * @param waterCost   extra cost per block of water (a bridge); negative forbids water
	 * @param turnCost    cost per 45° of turning, in blocks
	 * @param toAccess    end at the first road, bridge or plaza column reached instead of at the goal
	 */
	public record Params(int cell, int margin, double roadGrade, double stairGrade, double waterCost, double turnCost, boolean toAccess,
		int maxStates, double straightCost, int clearance) {
		public Params(int cell, int margin, double roadGrade, double stairGrade, double waterCost, double turnCost, boolean toAccess, int maxStates) {
			this(cell, margin, roadGrade, stairGrade, waterCost, turnCost, toAccess, maxStates, 0, 1);
		}

		public Params(int cell, int margin, double roadGrade, double stairGrade, double waterCost, double turnCost, boolean toAccess, int maxStates,
			double straightCost) {
			this(cell, margin, roadGrade, stairGrade, waterCost, turnCost, toAccess, maxStates, straightCost, 2);
		}
	}

	/** The fine path from start to goal as {@code [xs, zs]}, or null if there is none within the window. */
	public static int @Nullable [][] route(Ground ground, Occupancy occ, int sx, int sz, int gx, int gz, Params p) {
		int c = p.cell;
		int x0 = Math.floorDiv(Math.min(sx, gx) - p.margin, c);
		int z0 = Math.floorDiv(Math.min(sz, gz) - p.margin, c);
		int x1 = Math.floorDiv(Math.max(sx, gx) + p.margin, c);
		int z1 = Math.floorDiv(Math.max(sz, gz) + p.margin, c);
		int w = x1 - x0 + 1;
		int h = z1 - z0 + 1;
		if ((long) w * h * 8 > p.maxStates) {
			return null;
		}
		int states = w * h * 8;
		double[] cost = new double[states];
		Arrays.fill(cost, Double.POSITIVE_INFINITY);
		int[] parent = new int[states];
		boolean[] closed = new boolean[states];
		// per cell: surface height and whether passable, computed lazily
		double[] height = new double[w * h];
		byte[] info = new byte[w * h]; // 0 unknown, 1 land, 2 water, 3 blocked, 4 access
		Heap heap = new Heap();

		int scx = Math.floorDiv(sx, c) - x0;
		int scz = Math.floorDiv(sz, c) - z0;
		int gcx = Math.floorDiv(gx, c) - x0;
		int gcz = Math.floorDiv(gz, c) - z0;
		classify(ground, occ, x0, z0, w, scx, scz, c, p.clearance, height, info);
		for (int d = 0; d < 8; d++) {
			int s = ((scz * w) + scx) * 8 + d;
			cost[s] = 0;
			parent[s] = -1;
			heap.push(s, heuristic(scx, scz, gcx, gcz, c, p));
		}
		int found = -1;
		while (heap.size > 0) {
			int s = heap.pop();
			if (closed[s]) {
				continue;
			}
			closed[s] = true;
			int ci = s >> 3;
			int dir = s & 7;
			int cx = ci % w;
			int cz = ci / w;
			boolean atGoal = p.toAccess ? (info[ci] == 4 && (cx != scx || cz != scz)) : (cx == gcx && cz == gcz);
			if (atGoal) {
				found = s;
				break;
			}
			for (int turn = -2; turn <= 2; turn++) {
				int nd = (dir + turn + 8) & 7;
				boolean first = parent[s] == -1;
				int nx = cx + DX[nd];
				int nz = cz + DZ[nd];
				if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
					continue;
				}
				int ni = nz * w + nx;
				classify(ground, occ, x0, z0, w, nx, nz, c, p.clearance, height, info);
				if (info[ni] == 3) {
					continue;
				}
				if (info[ni] == 2 && p.waterCost < 0) {
					continue;
				}
				double len = c * ((nd & 1) == 1 ? 1.4142135623730951 : 1.0);
				double dh = Math.abs(height[ni] - height[ci]);
				double grade = dh / len;
				double step;
				if (info[ni] == 2 || info[ci] == 2) {
					step = len * (1 + Math.max(0, p.waterCost));
				} else if (grade <= p.roadGrade) {
					step = len * (1 + 2.5 * grade);
				} else if (grade <= p.stairGrade) {
					step = len * (4 + 8 * grade);
				} else {
					continue;
				}
				if (info[ni] == 4) {
					step *= 0.6;
				}
				if (!first) {
					step += Math.abs(turn) * p.turnCost * (Math.abs(turn) == 2 ? 2.5 : 1);
				}
				if (p.straightCost > 0 && !p.toAccess && (nx != gcx || nz != gcz)) {
					// keep heading for the goal: a main road runs true unless the land turns it
					int want = headingTo(gcx - cx, gcz - cz);
					int off = Math.abs(nd - want) & 7;
					step += Math.min(off, 8 - off) * p.straightCost * c;
				}
				int ns = ni * 8 + nd;
				double nc = cost[s] + step;
				if (nc < cost[ns]) {
					cost[ns] = nc;
					parent[ns] = s;
					heap.push(ns, nc + (p.toAccess ? 0 : heuristic(nx, nz, gcx, gcz, c, p)));
				}
			}
			if (heap.size > p.maxStates) {
				return null;
			}
		}
		if (found < 0) {
			return null;
		}
		// walk back to cells, then expand cell centres to a fine path
		int len = 0;
		for (int s = found; s != -1; s = parent[s]) {
			len++;
		}
		int[] cxs = new int[len];
		int[] czs = new int[len];
		int k = len - 1;
		for (int s = found; s != -1; s = parent[s]) {
			int ci = s >> 3;
			cxs[k] = (ci % w + x0) * c + (c == 1 ? 0 : c / 2);
			czs[k] = (ci / w + z0) * c + (c == 1 ? 0 : c / 2);
			k--;
		}
		cxs[0] = sx;
		czs[0] = sz;
		if (!p.toAccess) {
			cxs[len - 1] = gx;
			czs[len - 1] = gz;
		}
		return densify(cxs, czs);
	}

	/** Joins consecutive points with the dense-line rule of {@link Polyline}. */
	public static int[][] densify(int[] xs, int[] zs) {
		int total = 1;
		for (int i = 0; i + 1 < xs.length; i++) {
			total += Math.max(Math.abs(xs[i + 1] - xs[i]), Math.abs(zs[i + 1] - zs[i]));
		}
		int[] ox = new int[total];
		int[] oz = new int[total];
		int k = 0;
		ox[0] = xs[0];
		oz[0] = zs[0];
		for (int i = 0; i + 1 < xs.length; i++) {
			int dx = xs[i + 1] - xs[i];
			int dz = zs[i + 1] - zs[i];
			int n = Math.max(Math.abs(dx), Math.abs(dz));
			for (int s = 1; s <= n; s++) {
				k++;
				ox[k] = xs[i] + Polyline.lerp(dx, s, n);
				oz[k] = zs[i] + Polyline.lerp(dz, s, n);
			}
		}
		return new int[][]{ox, oz};
	}

	private static int headingTo(int dx, int dz) {
		double a = StrictMath.atan2(dz, dx);
		return ((int) Math.round(a / (Math.PI / 4)) + 8) & 7;
	}

	private static double heuristic(int x, int z, int gx, int gz, int c, Params p) {
		double dx = Math.abs(x - gx);
		double dz = Math.abs(z - gz);
		return c * (Math.max(dx, dz) + 0.41421356 * Math.min(dx, dz));
	}

	private static void classify(Ground ground, Occupancy occ, int x0, int z0, int w, int cx, int cz, int c, int clearance, double[] height,
		byte[] info) {
		int ci = cz * w + cx;
		if (info[ci] != 0) {
			return;
		}
		int bx = (cx + x0) * c + (c == 1 ? 0 : c / 2);
		int bz = (cz + z0) * c + (c == 1 ? 0 : c / 2);
		byte code = occ.get(bx, bz);
		boolean wet = ground.isWater(bx, bz);
		height[ci] = c >= 4 ? (ground.smooth(bx, bz) * 2 + ground.surface(bx - c / 2, bz - c / 2) + ground.surface(bx + c / 2 - 1, bz + c / 2 - 1)) / 4.0
			: ground.smooth(bx, bz);
		if (Cell.isAccess(code)) {
			info[ci] = 4;
		} else if (code == Cell.FREE || code == Cell.SETBACK || code == Cell.PLANNED_ROAD) {
			info[ci] = (byte) (wet ? 2 : 1);
		} else {
			info[ci] = 3;
		}
		int r = Math.max(clearance, c / 2);
		if (r > 0 && info[ci] != 3) {
			// blocked if anything stands within the road's half width (or the coarse cell's footprint)
			for (int dz = -r; dz <= r && info[ci] != 3; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					byte b = occ.get(bx + dx, bz + dz);
					if (!Cell.isAccess(b) && b != Cell.FREE && b != Cell.SETBACK && b != Cell.PLANNED_ROAD) {
						info[ci] = 3;
						break;
					}
				}
			}
		}
	}

	/** A binary min-heap of int states keyed by double priorities; equal keys pop in insertion order. */
	private static final class Heap {
		int[] items = new int[256];
		double[] keys = new double[256];
		long[] order = new long[256];
		int size;
		long counter;

		void push(int item, double key) {
			if (this.size == this.items.length) {
				this.items = Arrays.copyOf(this.items, this.size * 2);
				this.keys = Arrays.copyOf(this.keys, this.size * 2);
				this.order = Arrays.copyOf(this.order, this.size * 2);
			}
			int i = this.size++;
			this.items[i] = item;
			this.keys[i] = key;
			this.order[i] = this.counter++;
			while (i > 0) {
				int parent = (i - 1) >> 1;
				if (!less(i, parent)) {
					break;
				}
				swap(i, parent);
				i = parent;
			}
		}

		int pop() {
			int top = this.items[0];
			this.size--;
			if (this.size > 0) {
				this.items[0] = this.items[this.size];
				this.keys[0] = this.keys[this.size];
				this.order[0] = this.order[this.size];
				int i = 0;
				while (true) {
					int l = 2 * i + 1;
					int r = l + 1;
					int m = i;
					if (l < this.size && less(l, m)) {
						m = l;
					}
					if (r < this.size && less(r, m)) {
						m = r;
					}
					if (m == i) {
						break;
					}
					swap(i, m);
					i = m;
				}
			}
			return top;
		}

		private boolean less(int a, int b) {
			return this.keys[a] < this.keys[b] || (this.keys[a] == this.keys[b] && this.order[a] < this.order[b]);
		}

		private void swap(int a, int b) {
			int ti = this.items[a];
			this.items[a] = this.items[b];
			this.items[b] = ti;
			double tk = this.keys[a];
			this.keys[a] = this.keys[b];
			this.keys[b] = tk;
			long to = this.order[a];
			this.order[a] = this.order[b];
			this.order[b] = to;
		}
	}
}
