package dev.voidmc.hearth.plan.road;

import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.Arrays;

/**
 * Grades a road along its dense centreline: walking levels that follow the land as closely as the road's grade
 * allows, stair runs where the land is too steep to grade, and bridges over water.
 *
 * <ol>
 *   <li>The ground is smoothed a little (a road does not follow every bump).</li>
 *   <li>Where the land itself climbs faster than {@code roadGrade}, the run becomes a stair street (if allowed): its
 *   grade limit is {@code stairGrade}. Short runs of either kind are merged into their neighbours.</li>
 *   <li>The profile is clamped to the grade limits in a forward and a backward pass, then rounded to whole blocks.</li>
 *   <li>Water runs no longer than {@code bridgeMax} become bridges with a level deck at least {@code clearance} above
 *   the water and no lower than either bank.</li>
 *   <li>The road is usable up to the first column where it would need more than {@code maxCut} of cut or fill, the first
 *   water run it cannot bridge, or the end.</li>
 * </ol>
 */
public final class Profile {
	public final int[] ys;
	/** Runs as (first index, {@link RoadEdge} type, deck y), the format {@link RoadEdge} keeps. */
	public final int[] runs;
	/** Columns usable from the start (the rest is cut off by a cliff, deep water or too much earthwork). */
	public final int usable;

	private Profile(int[] ys, int[] runs, int usable) {
		this.ys = ys;
		this.runs = runs;
		this.usable = usable;
	}

	public record Rules(double roadGrade, double stairGrade, boolean stairs, int maxCut, int bridgeMax, int clearance, boolean boardwalk) {
	}

	public static Profile grade(int[] xs, int[] zs, Ground ground, Rules rules) {
		int n = xs.length;
		int[] g = new int[n];
		boolean[] wet = new boolean[n];
		int[] waterTop = new int[n];
		for (int i = 0; i < n; i++) {
			int w = ground.water(xs[i], zs[i]);
			g[i] = ground.ground(xs[i], zs[i]);
			if (w != dev.voidmc.hearth.api.TerrainProbe.NO_WATER && w > g[i]) {
				wet[i] = true;
				waterTop[i] = w;
			}
		}
		// where can it go: up to the first unbridgeable water run
		int usable = n;
		int[] type = new int[n];
		int[] deck = new int[n];
		for (int i = 0; i < n; ) {
			if (!wet[i]) {
				i++;
				continue;
			}
			int j = i;
			int top = Integer.MIN_VALUE;
			while (j < n && wet[j]) {
				top = Math.max(top, waterTop[j]);
				j++;
			}
			boolean bridgeable = rules.boardwalk || j - i <= rules.bridgeMax;
			if (!bridgeable || (i == 0 && !rules.boardwalk) || (j == n && !rules.boardwalk)) {
				usable = Math.min(usable, i == 0 ? 0 : i);
				break;
			}
			for (int k = i; k < j; k++) {
				type[k] = rules.boardwalk ? RoadEdge.BOARDWALK : RoadEdge.BRIDGE;
				deck[k] = top;
			}
			i = j;
		}
		if (usable == 0) {
			return new Profile(new int[0], new int[0], 0);
		}
		// smoothed land heights; water columns take the deck height later
		double[] t = new double[usable];
		for (int i = 0; i < usable; i++) {
			if (type[i] != RoadEdge.GRADE) {
				t[i] = Double.NaN;
				continue;
			}
			double sum = 0;
			int cnt = 0;
			for (int k = Math.max(0, i - 2); k <= Math.min(usable - 1, i + 2); k++) {
				if (type[k] == RoadEdge.GRADE) {
					sum += g[k] + (k == i ? g[k] : 0);
					cnt += k == i ? 2 : 1;
				}
			}
			t[i] = sum / cnt;
		}
		// stair runs where the land climbs faster than a road may
		if (rules.stairs) {
			for (int i = 0; i < usable; i++) {
				if (type[i] != RoadEdge.GRADE) {
					continue;
				}
				int a = Math.max(0, i - 3);
				int b = Math.min(usable - 1, i + 3);
				while (a < i && type[a] != RoadEdge.GRADE) {
					a++;
				}
				while (b > i && type[b] != RoadEdge.GRADE) {
					b--;
				}
				if (b > a && Math.abs(t[b] - t[a]) / (b - a) > rules.roadGrade * 1.15) {
					type[i] = RoadEdge.STAIRS;
				}
			}
			mergeShortRuns(type, usable, 3);
		}
		// clamp to grade, forward then backward
		double[] y = new double[usable];
		for (int i = 0; i < usable; i++) {
			y[i] = Double.isNaN(t[i]) ? Double.NaN : t[i];
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 1; i < usable; i++) {
				clampTo(y, i, i - 1, type, rules);
			}
			for (int i = usable - 2; i >= 0; i--) {
				clampTo(y, i, i + 1, type, rules);
			}
		}
		int[] ys = new int[usable];
		for (int i = 0; i < usable; i++) {
			ys[i] = Double.isNaN(y[i]) ? Integer.MIN_VALUE : (int) Math.floor(y[i] + 0.5);
		}
		// never more than a block between neighbouring land columns, whatever the smoothing did beside the water
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 1; i < usable; i++) {
				if (ys[i] != Integer.MIN_VALUE && ys[i - 1] != Integer.MIN_VALUE) {
					ys[i] = Math.max(ys[i - 1] - 1, Math.min(ys[i - 1] + 1, ys[i]));
				}
			}
			for (int i = usable - 2; i >= 0; i--) {
				if (ys[i] != Integer.MIN_VALUE && ys[i + 1] != Integer.MIN_VALUE) {
					ys[i] = Math.max(ys[i + 1] - 1, Math.min(ys[i + 1] + 1, ys[i]));
				}
			}
		}
		// bridge decks: level, above the water and no lower than the banks
		for (int i = 0; i < usable; ) {
			if (type[i] == RoadEdge.GRADE || type[i] == RoadEdge.STAIRS) {
				i++;
				continue;
			}
			int j = i;
			while (j < usable && (type[j] == RoadEdge.BRIDGE || type[j] == RoadEdge.BOARDWALK)) {
				j++;
			}
			int before = i > 0 ? ys[i - 1] : Integer.MIN_VALUE;
			int after = j < usable ? ys[j] : Integer.MIN_VALUE;
			int d = Math.max(deck[i] + rules.clearance, Math.max(before, after));
			if (rules.boardwalk) {
				d = deck[i] + rules.clearance;
			}
			for (int k = i; k < j; k++) {
				ys[k] = d;
				deck[k] = d;
			}
			i = j;
		}
		// earthwork limit
		for (int i = 0; i < usable; i++) {
			if (type[i] == RoadEdge.GRADE || type[i] == RoadEdge.STAIRS) {
				int limit = type[i] == RoadEdge.STAIRS ? rules.maxCut + 1 : rules.maxCut;
				if (Math.abs(ys[i] - g[i]) > limit) {
					usable = i;
					break;
				}
			}
		}
		// a road that ends on a bridge is cut back to the bank before it
		while (usable > 0 && type[usable - 1] != RoadEdge.GRADE && type[usable - 1] != RoadEdge.STAIRS && !rules.boardwalk) {
			usable--;
		}
		if (usable < n) {
			ys = Arrays.copyOf(ys, usable);
		}
		return new Profile(ys, packRuns(type, deck, usable), usable);
	}

	private static void clampTo(double[] y, int i, int ref, int[] type, Rules rules) {
		if (Double.isNaN(y[i]) || Double.isNaN(y[ref])) {
			return;
		}
		double limit = type[i] == RoadEdge.STAIRS || type[ref] == RoadEdge.STAIRS ? rules.stairGrade : rules.roadGrade;
		y[i] = Math.max(y[ref] - limit, Math.min(y[ref] + limit, y[i]));
	}

	private static void mergeShortRuns(int[] type, int n, int min) {
		for (int pass = 0; pass < 2; pass++) {
			int i = 0;
			while (i < n) {
				int j = i;
				while (j < n && type[j] == type[i]) {
					j++;
				}
				boolean land = type[i] == RoadEdge.GRADE || type[i] == RoadEdge.STAIRS;
				if (land && j - i < min) {
					int left = i > 0 ? type[i - 1] : -1;
					int right = j < n ? type[j] : -1;
					int fill = left == RoadEdge.GRADE || left == RoadEdge.STAIRS ? left : right == RoadEdge.GRADE || right == RoadEdge.STAIRS ? right : type[i];
					if (pass == 0 && type[i] == RoadEdge.GRADE) {
						fill = RoadEdge.STAIRS;
					}
					if (fill == RoadEdge.GRADE || fill == RoadEdge.STAIRS) {
						for (int k = i; k < j; k++) {
							type[k] = fill;
						}
					}
				}
				i = j;
			}
		}
	}

	private static int[] packRuns(int[] type, int[] deck, int n) {
		int[] out = new int[Math.max(3, n * 3)];
		int k = 0;
		for (int i = 0; i < n; i++) {
			if (i == 0 || type[i] != type[i - 1]) {
				out[k++] = i;
				out[k++] = type[i];
				out[k++] = type[i] == RoadEdge.BRIDGE || type[i] == RoadEdge.BOARDWALK ? deck[i] : 0;
			}
		}
		if (k == 0) {
			return new int[]{0, RoadEdge.GRADE, 0};
		}
		return Arrays.copyOf(out, k);
	}
}
