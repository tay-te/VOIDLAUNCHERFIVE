package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Finds the way a country road takes between two points (docs/LIVING_WORLD_2.md §1.2), the way road builders did: as
 * short as the land allows, climbing no steeper than a loaded cart can (1 in 6; switchbacks on a mountain flank), over
 * rivers by a bridge (up to {@code bridgeMax} wide) or a ford where the bed is within a block, round lakes and the sea,
 * and wide of other settlements' hearts and of structures.
 *
 * <p>Two searches: an A* over a coarse lattice ({@code coarseStep}) on the cheap probe, inside an ellipse about the two
 * ends (no road wanders further than {@code ellipse} times the straight distance), then a fine A* ({@code fineStep}) on
 * the exact probe within {@code corridor} blocks of the coarse way. The result is smoothed (Chaikin), walked out column
 * by column, cut into graded runs, fords and bridges, and graded: every column within a block of the last and never
 * more than one rise in six columns. Pure and deterministic: the same probe and ends give the same road on any thread.
 */
public final class RoadRouter {
	private RoadRouter() {
	}

	/** Extra cost a block at a column ({@code 0} none), or {@link Double#POSITIVE_INFINITY} where no road may go. */
	@FunctionalInterface
	public interface Avoid {
		double cost(int x, int z);

		Avoid NONE = (x, z) -> 0;
	}

	/**
	 * @param ellipse    the furthest a road may wander: the sum of its distances to the two ends, over the straight distance
	 * @param maxStates  search states expanded at most (both searches together)
	 * @param grade      the steepest a graded run climbs (rise over run, per column)
	 * @param bridgeMax  the widest water a bridge spans
	 * @param fordDepth  water this deep or shallower is forded
	 * @param fordMax    the widest ford
	 */
	public record Params(double ellipse, int maxStates, double grade, int bridgeMax, int fordDepth, int fordMax, int coarseStep, int fineStep,
		int corridor) {
		public static final Params DEFAULTS = new Params(1.35, 200_000, 1.0 / 6.0, 24, 1, 12, 16, 4, 40);
	}

	/** Why the last search on this thread found no road (for the harness). */
	private static final ThreadLocal<String> WHY = ThreadLocal.withInitial(() -> "");

	public static String lastFailure() {
		return WHY.get();
	}

	public static @Nullable Route route(TerrainProbe exact, TerrainProbe rough, int ax, int az, int bx, int bz, Avoid avoid) {
		return route(exact, rough, ax, az, bx, bz, avoid, Params.DEFAULTS);
	}

	public static @Nullable Route route(TerrainProbe exact, TerrainProbe rough, int ax, int az, int bx, int bz, Avoid avoid, Params p) {
		Route r = routeOnce(exact, rough, ax, az, bx, bz, avoid, p);
		if (r != null || !TRAPPED.get()) {
			return r;
		}
		// the road's first end is shut in on the coarse lattice (a port on its spit, a town on a ledge): the search runs from
		// the other end (an end is always reached), then on a finer lattice
		String why = WHY.get();
		r = routeOnce(exact, rough, bx, bz, ax, az, avoid, p);
		if (r != null) {
			return r.reversed();
		}
		if (TRAPPED.get() && p.coarseStep() > p.fineStep()) {
			Params finer = new Params(p.ellipse(), p.maxStates(), p.grade(), p.bridgeMax(), p.fordDepth(), p.fordMax(), Math.max(p.fineStep(), p.coarseStep() / 2),
				p.fineStep(), p.corridor());
			return route(exact, rough, ax, az, bx, bz, avoid, finer);
		}
		WHY.set(why + "; from the other end: " + WHY.get());
		return null;
	}

	/** Whether the last failed search on this thread never left its start (every step from it refused). */
	private static final ThreadLocal<Boolean> TRAPPED = ThreadLocal.withInitial(() -> false);

	private static @Nullable Route routeOnce(TerrainProbe exact, TerrainProbe rough, int ax, int az, int bx, int bz, Avoid avoid, Params p) {
		WHY.set("");
		TRAPPED.set(false);
		LongOpenHashSet refused = new LongOpenHashSet();
		// a way the dense walk finds runs through water too wide to bridge: that water is refused and the search runs again
		boolean cliffs = false;
		for (int attempt = 0; attempt < 4; attempt++) {
			int[] budget = {p.maxStates()};
			Avoid a = refused.isEmpty() ? avoid : (x, z) -> refused.contains(key(x >> 3, z >> 3)) ? Double.POSITIVE_INFINITY : avoid.cost(x, z);
			int[] coarse = search(rough, ax, az, bx, bz, p.coarseStep(), a, p, null, budget, cliffs);
			if (coarse == null && !cliffs) {
				// no way round: a cliff the road has to be cut up (a long ramp of earthwork, dear but possible)
				cliffs = true;
				budget[0] = p.maxStates();
				coarse = search(rough, ax, az, bx, bz, p.coarseStep(), a, p, null, budget, true);
			}
			if (coarse == null) {
				WHY.set("no coarse way (" + WHY.get() + ")");
				return null;
			}
			LongOpenHashSet allowed = corridorCells(coarse, p.fineStep(), p.corridor());
			int[] fine = search(exact, ax, az, bx, bz, p.fineStep(), a, p, allowed, budget, cliffs);
			int[] way = fine != null ? fine : coarse;
			int states = p.maxStates() - budget[0];
			Route r = walk(exact, way, p, states, refused);
			if (r != null) {
				return r;
			}
		}
		WHY.set("water too wide on every way tried");
		return null;
	}

	private static long key(int i, int j) {
		return (long) i << 32 ^ j & 0xFFFF_FFFFL;
	}

	// ---------------------------------------------------------------- A*

	private static final int[] DI = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final int[] DJ = {0, 1, 1, 1, 0, -1, -1, -1};

	/** Ground and water at a lattice column, packed: ground in the high half, water top (or NO_WATER) in the low. */
	private static long sample(TerrainProbe t, int x, int z) {
		int g = t.groundY(x, z);
		int w = t.waterY(x, z);
		return (long) g << 32 | (w & 0xFFFF_FFFFL);
	}

	private static long sampled(Long2LongOpenHashMap cache, TerrainProbe t, long key, int x, int z) {
		if (cache.containsKey(key)) {
			return cache.get(key);
		}
		long s = sample(t, x, z);
		cache.put(key, s);
		return s;
	}

	private static int ground(long s) {
		return (int) (s >> 32);
	}

	private static int water(long s) {
		return (int) s;
	}

	private static boolean wet(long s) {
		int w = water(s);
		return w != TerrainProbe.NO_WATER && w > ground(s);
	}

	/**
	 * A* from (ax, az) to the lattice point nearest (bx, bz). Returns the way as packed x, z pairs (from a to b, b itself
	 * last), or null.
	 */
	static int @Nullable [] search(TerrainProbe t, int ax, int az, int bx, int bz, int step, Avoid avoid, Params p, @Nullable LongOpenHashSet allowed,
		int[] budget, boolean cliffs) {
		double straight = Math.hypot(bx - ax, bz - az);
		double reach = straight * p.ellipse() + step * 2;
		int gi = Math.round((bx - ax) / (float) step);
		int gj = Math.round((bz - az) / (float) step);
		long goal = key(gi, gj);
		Long2LongOpenHashMap samples = new Long2LongOpenHashMap();
		Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
		best.defaultReturnValue(Double.POSITIVE_INFINITY);
		Long2LongOpenHashMap from = new Long2LongOpenHashMap();
		Heap open = new Heap();
		long start = key(0, 0);
		best.put(start, 0);
		open.push(start, Math.hypot(gi * step, gj * step));
		boolean found = false;
		int refusals = 0;
		int expanded = 0;
		while (!open.isEmpty()) {
			if (budget[0]-- <= 0) {
				WHY.set("search budget spent");
				return null;
			}
			double f = open.topScore();
			long u = open.pop();
			int ui = (int) (u >> 32);
			int uj = (int) u;
			double gu = best.get(u);
			if (f - heuristic(ui, uj, gi, gj, step) > gu + 1e-6) {
				continue;   // a stale entry
			}
			if (u == goal) {
				found = true;
				break;
			}
			expanded++;
			int ux = ax + ui * step;
			int uz = az + uj * step;
			long su = sampled(samples, t, u, ux, uz);
			for (int d = 0; d < 8; d++) {
				int vi = ui + DI[d];
				int vj = uj + DJ[d];
				long v = key(vi, vj);
				int vx = ax + vi * step;
				int vz = az + vj * step;
				if (allowed != null && !allowed.contains(key(Math.floorDiv(vx, step), Math.floorDiv(vz, step))) && v != goal) {
					continue;
				}
				if (Math.hypot(vx - ax, vz - az) + Math.hypot(vx - bx, vz - bz) > reach) {
					continue;
				}
				long sv = sampled(samples, t, v, vx, vz);
				double len = (d & 1) == 1 ? step * Math.sqrt(2) : step;
				double c = stepCost(t, su, sv, ux, uz, vx, vz, len, avoid, p, cliffs);
				if (c == Double.POSITIVE_INFINITY) {
					if (v != goal) {
						refusals++;
						continue;
					}
					// the road's end itself is always reached (its lattice point may stand in a pond beside it)
					c = len * 4;
				}
				double gv = gu + c;
				if (gv < best.get(v) - 1e-9) {
					best.put(v, gv);
					from.put(v, u);
					open.push(v, gv + heuristic(vi, vj, gi, gj, step));
				}
			}
		}
		if (!found) {
			TRAPPED.set(expanded <= 1);
			WHY.set("no way within the ellipse: " + expanded + " points reached, " + refusals + " steps refused, lattice " + step);
			return null;
		}
		List<Long> path = new ArrayList<>();
		for (long k = goal; ; k = from.get(k)) {
			path.add(k);
			if (k == start) {
				break;
			}
		}
		int[] out = new int[(path.size() + 1) * 2];
		int n = 0;
		for (int i = path.size() - 1; i >= 0; i--) {
			long k = path.get(i);
			out[n++] = ax + (int) (k >> 32) * step;
			out[n++] = az + (int) k * step;
		}
		if (out[n - 2] != bx || out[n - 1] != bz) {
			out[n++] = bx;
			out[n++] = bz;
		}
		return Arrays.copyOf(out, n);
	}

	private static double heuristic(int i, int j, int gi, int gj, int step) {
		return Math.hypot((gi - i) * step, (gj - j) * step);
	}

	/** What a step of a road from one lattice column to the next costs. */
	private static double stepCost(TerrainProbe t, long su, long sv, int ux, int uz, int vx, int vz, double len, Avoid avoid, Params p, boolean cliffs) {
		double extra = avoid.cost(vx, vz);
		if (extra == Double.POSITIVE_INFINITY) {
			return extra;
		}
		double cost = len * (1 + extra);
		if (wet(sv)) {
			if (lake(t, vx, vz, p.bridgeMax() / 2 + 2)) {
				return Double.POSITIVE_INFINITY;
			}
			int depth = water(sv) - ground(sv);
			// a ford is cheap; a bridge is dear (and the dense walk sees whether it can be built)
			return cost + len * (depth <= p.fordDepth() ? 3 : 30);
		}
		int hu = wet(su) ? water(su) : ground(su);
		int hv = ground(sv);
		double dh = Math.abs(hv - hu);
		if (dh > len * 0.75 + 1) {
			if (!cliffs) {
				return Double.POSITIVE_INFINITY;   // a cliff
			}
			cost += dh * 40;
		}
		cost += dh * 0.6;
		double excess = dh - len * p.grade();
		if (excess > 0) {
			// steeper than a cart can climb: earthwork, so the search goes the long way round (switchbacks)
			cost += excess * 14;
		}
		return cost;
	}

	/** Water in every direction {@code r} blocks out: a lake or the sea, not a river to bridge. */
	private static boolean lake(TerrainProbe t, int x, int z, int r) {
		int d = (int) Math.round(r * 0.7071);
		return t.isWater(x + r, z) && t.isWater(x - r, z) && t.isWater(x, z + r) && t.isWater(x, z - r) && t.isWater(x + d, z + d)
			&& t.isWater(x - d, z - d) && t.isWater(x + d, z - d) && t.isWater(x - d, z + d);
	}

	/** Fine lattice cells (as {@code floorDiv(x, step)} keys) within {@code radius} blocks of a coarse way. */
	static LongOpenHashSet corridorCells(int[] way, int step, int radius) {
		LongOpenHashSet cells = new LongOpenHashSet();
		double r2 = (double) radius * radius;
		for (int i = 0; i + 3 < way.length; i += 2) {
			int ax = way[i];
			int az = way[i + 1];
			int bx = way[i + 2];
			int bz = way[i + 3];
			int x0 = Math.floorDiv(Math.min(ax, bx) - radius, step);
			int x1 = Math.floorDiv(Math.max(ax, bx) + radius, step);
			int z0 = Math.floorDiv(Math.min(az, bz) - radius, step);
			int z1 = Math.floorDiv(Math.max(az, bz) + radius, step);
			double ex = bx - ax;
			double ez = bz - az;
			double len2 = ex * ex + ez * ez;
			for (int ci = x0; ci <= x1; ci++) {
				for (int cj = z0; cj <= z1; cj++) {
					double px = ci * step - ax;
					double pz = cj * step - az;
					double tt = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * ex + pz * ez) / len2));
					double qx = px - tt * ex;
					double qz = pz - tt * ez;
					if (qx * qx + qz * qz <= r2) {
						cells.add(key(ci, cj));
					}
				}
			}
		}
		return cells;
	}

	/** A binary heap of lattice keys by score (ties: the smaller key first, so the search is deterministic). */
	private static final class Heap {
		private long[] keys = new long[256];
		private double[] scores = new double[256];
		private int size;

		boolean isEmpty() {
			return this.size == 0;
		}

		double topScore() {
			return this.scores[0];
		}

		void push(long key, double score) {
			if (this.size == this.keys.length) {
				this.keys = Arrays.copyOf(this.keys, this.size * 2);
				this.scores = Arrays.copyOf(this.scores, this.size * 2);
			}
			int i = this.size++;
			while (i > 0) {
				int parent = (i - 1) >>> 1;
				if (!less(score, key, this.scores[parent], this.keys[parent])) {
					break;
				}
				this.keys[i] = this.keys[parent];
				this.scores[i] = this.scores[parent];
				i = parent;
			}
			this.keys[i] = key;
			this.scores[i] = score;
		}

		long pop() {
			long top = this.keys[0];
			int n = --this.size;
			long key = this.keys[n];
			double score = this.scores[n];
			int i = 0;
			while (true) {
				int c = 2 * i + 1;
				if (c >= n) {
					break;
				}
				if (c + 1 < n && less(this.scores[c + 1], this.keys[c + 1], this.scores[c], this.keys[c])) {
					c++;
				}
				if (!less(this.scores[c], this.keys[c], score, key)) {
					break;
				}
				this.keys[i] = this.keys[c];
				this.scores[i] = this.scores[c];
				i = c;
			}
			if (n > 0) {
				this.keys[i] = key;
				this.scores[i] = score;
			}
			return top;
		}

		private static boolean less(double sa, long ka, double sb, long kb) {
			return sa < sb || sa == sb && ka < kb;
		}
	}

	// ---------------------------------------------------------------- the dense walk

	/**
	 * Smooths a lattice way, walks it column by column, cuts it into runs and grades it. Returns null (and adds the
	 * offending water to {@code refused}) where it crosses water too wide to bridge.
	 */
	static @Nullable Route walk(TerrainProbe t, int[] way, Params p, int states, LongOpenHashSet refused) {
		double[] xs = new double[way.length / 2];
		double[] zs = new double[way.length / 2];
		for (int i = 0; i < xs.length; i++) {
			xs[i] = way[i * 2];
			zs[i] = way[i * 2 + 1];
		}
		for (int pass = 0; pass < 2; pass++) {
			double[][] s = chaikin(xs, zs);
			xs = s[0];
			zs = s[1];
		}
		int[][] cols = dense(xs, zs);
		int[] cx = cols[0];
		int[] cz = cols[1];
		int n = cx.length;
		int[] g = new int[n];
		int[] w = new int[n];
		boolean[] wet = new boolean[n];
		for (int i = 0; i < n; i++) {
			g[i] = t.groundY(cx[i], cz[i]);
			w[i] = t.waterY(cx[i], cz[i]);
			wet[i] = w[i] != TerrainProbe.NO_WATER && w[i] > g[i];
		}
		// a sliver of land between two wet runs is bridged with them
		for (int i = 1; i < n - 1; i++) {
			if (!wet[i] && wet[i - 1]) {
				int j = i;
				while (j < n && !wet[j] && j - i < 3) {
					j++;
				}
				if (j < n && wet[j] && j - i <= 2) {
					for (int k = i; k < j; k++) {
						wet[k] = true;
						w[k] = Math.max(w[k], Math.max(w[i - 1], w[j]));
					}
				}
			}
		}
		byte[] type = new byte[n];
		int[] deck = new int[n];
		for (int i = 0; i < n; ) {
			if (!wet[i]) {
				i++;
				continue;
			}
			int j = i;
			int depth = 0;
			int top = Integer.MIN_VALUE;
			while (j < n && wet[j]) {
				depth = Math.max(depth, w[j] - g[j]);
				top = Math.max(top, w[j]);
				j++;
			}
			int len = j - i;
			if (len <= p.fordMax() && depth <= p.fordDepth() && i > 0 && j < n) {
				for (int k = i; k < j; k++) {
					type[k] = Route.FORD;
				}
			} else if (len <= p.bridgeMax() && i > 0 && j < n) {
				for (int k = i; k < j; k++) {
					type[k] = Route.BRIDGE;
					deck[k] = top;
				}
			} else {
				for (int k = i; k < j; k++) {
					refused.add(key(cx[k] >> 3, cz[k] >> 3));
				}
				WHY.set("water " + len + " wide at " + cx[i] + "," + cz[i]);
				return null;
			}
			i = j;
		}
		int[] ys = grade(g, w, type, deck, p.grade());
		return new Route(cx, ys, cz, type, deck, states);
	}

	/** Corner cutting, keeping both ends. */
	static double[][] chaikin(double[] xs, double[] zs) {
		int n = xs.length;
		if (n < 3) {
			return new double[][]{xs, zs};
		}
		double[] ox = new double[2 * (n - 1)];
		double[] oz = new double[2 * (n - 1)];
		int k = 0;
		ox[k] = xs[0];
		oz[k++] = zs[0];
		for (int i = 0; i + 1 < n; i++) {
			if (i > 0) {
				ox[k] = 0.75 * xs[i] + 0.25 * xs[i + 1];
				oz[k++] = 0.75 * zs[i] + 0.25 * zs[i + 1];
			}
			if (i + 2 < n) {
				ox[k] = 0.25 * xs[i] + 0.75 * xs[i + 1];
				oz[k++] = 0.25 * zs[i] + 0.75 * zs[i + 1];
			}
		}
		ox[k] = xs[n - 1];
		oz[k++] = zs[n - 1];
		return new double[][]{Arrays.copyOf(ox, k), Arrays.copyOf(oz, k)};
	}

	/**
	 * Walks a polyline column by column: each next column a king's move from the last, no column twice, no corner where
	 * a diagonal step would do.
	 */
	static int[][] dense(double[] xs, double[] zs) {
		int[] ox = new int[64];
		int[] oz = new int[64];
		int n = 0;
		for (int s = 0; s + 1 < xs.length || s == 0 && xs.length == 1; s++) {
			double ax = xs[s];
			double az = zs[s];
			double bx = s + 1 < xs.length ? xs[s + 1] : ax;
			double bz = s + 1 < zs.length ? zs[s + 1] : az;
			double len = Math.hypot(bx - ax, bz - az);
			int steps = Math.max(1, (int) Math.ceil(len * 3));
			for (int i = 0; i <= steps; i++) {
				double f = i / (double) steps;
				int x = (int) Math.floor(ax + (bx - ax) * f + 0.5);
				int z = (int) Math.floor(az + (bz - az) * f + 0.5);
				if (n > 0 && ox[n - 1] == x && oz[n - 1] == z) {
					continue;
				}
				if (n + 2 >= ox.length) {
					ox = Arrays.copyOf(ox, ox.length * 2);
					oz = Arrays.copyOf(oz, oz.length * 2);
				}
				if (n > 0) {
					// a jump of more than one column: walk it out
					while (Math.max(Math.abs(x - ox[n - 1]), Math.abs(z - oz[n - 1])) > 1) {
						if (n + 2 >= ox.length) {
							ox = Arrays.copyOf(ox, ox.length * 2);
							oz = Arrays.copyOf(oz, oz.length * 2);
						}
						ox[n] = ox[n - 1] + Integer.signum(x - ox[n - 1]);
						oz[n] = oz[n - 1] + Integer.signum(z - oz[n - 1]);
						n++;
					}
					if (ox[n - 1] == x && oz[n - 1] == z) {
						continue;
					}
					// an L-shaped corner: the middle column goes, the step becomes diagonal
					if (n >= 2 && Math.abs(x - ox[n - 2]) <= 1 && Math.abs(z - oz[n - 2]) <= 1) {
						n--;
						if (ox[n - 1] == x && oz[n - 1] == z) {
							continue;
						}
					}
				}
				ox[n] = x;
				oz[n] = z;
				n++;
			}
		}
		return new int[][]{Arrays.copyOf(ox, n), Arrays.copyOf(oz, n)};
	}

	/**
	 * Walking levels for every column: the land lightly smoothed, fords at the water's top, held to {@code grade} per
	 * column (bridges span freely), then rounded with hysteresis so the road rises in clean single steps at least six
	 * columns apart. Bridge decks are set level at the higher of the water plus clearance and both banks.
	 */
	static int[] grade(int[] g, int[] w, byte[] type, int[] deck, double grade) {
		int n = g.length;
		double[] target = new double[n];
		for (int i = 0; i < n; i++) {
			if (type[i] == Route.BRIDGE) {
				target[i] = Double.NaN;
			} else if (type[i] == Route.FORD) {
				target[i] = w[i];
			} else {
				double sum = 0;
				int cnt = 0;
				for (int k = Math.max(0, i - 3); k <= Math.min(n - 1, i + 3); k++) {
					if (type[k] == Route.GRADE) {
						int weight = k == i ? 2 : 1;
						sum += g[k] * weight;
						cnt += weight;
					}
				}
				target[i] = cnt == 0 ? g[i] : sum / cnt;
			}
		}
		double s = grade - 1e-9;
		double[] y = target.clone();
		for (int pass = 0; pass < 3; pass++) {
			int last = -1;
			for (int i = 0; i < n; i++) {
				if (Double.isNaN(y[i])) {
					continue;
				}
				if (last >= 0) {
					double lim = s * (i - last);
					y[i] = Math.max(y[last] - lim, Math.min(y[last] + lim, y[i]));
				}
				last = i;
			}
			last = -1;
			for (int i = n - 1; i >= 0; i--) {
				if (Double.isNaN(y[i])) {
					continue;
				}
				if (last >= 0) {
					double lim = s * (last - i);
					y[i] = Math.max(y[last] - lim, Math.min(y[last] + lim, y[i]));
				}
				last = i;
			}
		}
		int[] ys = new int[n];
		int level = Integer.MIN_VALUE;
		int sinceStep = 6;
		for (int i = 0; i < n; i++) {
			if (Double.isNaN(y[i])) {
				ys[i] = Integer.MIN_VALUE;
				sinceStep = 6;   // the bridge lets the road start fresh on the far bank
				level = Integer.MIN_VALUE;
				continue;
			}
			if (level == Integer.MIN_VALUE) {
				level = (int) Math.floor(y[i] + 0.5);
			} else if (sinceStep >= 6 && y[i] - level >= 0.75) {
				level++;
				sinceStep = 0;
			} else if (sinceStep >= 6 && level - y[i] >= 0.75) {
				level--;
				sinceStep = 0;
			}
			ys[i] = level;
			sinceStep++;
		}
		// bridge decks: level over the whole run, above the water and no lower than either bank
		for (int i = 0; i < n; ) {
			if (type[i] != Route.BRIDGE) {
				i++;
				continue;
			}
			int j = i;
			int top = Integer.MIN_VALUE;
			while (j < n && type[j] == Route.BRIDGE) {
				top = Math.max(top, deck[j]);
				j++;
			}
			int before = i > 0 ? ys[i - 1] : Integer.MIN_VALUE;
			int after = j < n ? ys[j] : Integer.MIN_VALUE;
			int d = Math.max(top + 2, Math.max(before, after));
			for (int k = i; k < j; k++) {
				ys[k] = d;
			}
			i = j;
		}
		return ys;
	}
}
