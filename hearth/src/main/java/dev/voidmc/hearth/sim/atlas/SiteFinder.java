package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.TerrainProbe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Chooses where in a region a settlement was founded, the way people chose: dry, gentle ground with water close by
 * (a river, a lake, the sea), not too high, with room to grow, and not on top of anyone else's buildings. A cheap survey
 * of a grid of candidates first, then a closer look at the best few. Reads only the terrain probe, so it gives the same
 * answer on any thread, in any session.
 */
public final class SiteFinder {
	private static final int GRID = 24;
	private static final int SHORTLIST = 32;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

	private SiteFinder() {
	}

	/** A chosen spot and its score. */
	public record Spot(int x, int z, double score) {
	}

	/**
	 * @param minX the window's west edge (the region's edge plus the site margin), and so on
	 * @param clear whether a column is clear of structures (asked only for the shortlist, best first)
	 */
	public static @Nullable Spot find(TerrainProbe t, int minX, int minZ, int span, RandomSource random, java.util.function.BiPredicate<Integer, Integer> clear) {
		List<Spot> survey = new ArrayList<>();
		for (int gx = 0; gx * GRID <= span; gx++) {
			for (int gz = 0; gz * GRID <= span; gz++) {
				int x = minX + gx * GRID + random.nextInt(9) - 4;
				int z = minZ + gz * GRID + random.nextInt(9) - 4;
				double q = quick(t, x, z);
				if (q > Double.NEGATIVE_INFINITY) {
					survey.add(new Spot(x, z, q + random.nextDouble()));
				}
			}
		}
		survey.sort(Comparator.comparingDouble(Spot::score).reversed());
		List<Spot> shortlist = new ArrayList<>();
		for (int i = 0; i < Math.min(SHORTLIST, survey.size()); i++) {
			Spot s = survey.get(i);
			double score = full(t, s.x, s.z);
			if (score > Double.NEGATIVE_INFINITY) {
				shortlist.add(new Spot(s.x, s.z, score + random.nextDouble() * 2));
			}
		}
		shortlist.sort(Comparator.comparingDouble(Spot::score).reversed());
		for (Spot s : shortlist) {
			if (clear.test(s.x, s.z)) {
				return s;
			}
		}
		return null;
	}

	/** Five columns: dry land above the sea, and not a cliff. */
	static double quick(TerrainProbe t, int x, int z) {
		int g = t.groundY(x, z);
		if (t.isWater(x, z) || g <= t.seaLevel()) {
			return Double.NEGATIVE_INFINITY;
		}
		double rough = 0;
		boolean water = false;
		for (int i = 0; i < 4; i++) {
			int[] d = DIRS[i];
			rough += Math.abs(t.groundY(x + d[0] * 24, z + d[1] * 24) - g);
			water |= t.isWater(x + d[0] * 64, z + d[1] * 64);
		}
		return -rough * 0.6 + (water ? 6 : 0) - Math.max(0, g - t.seaLevel() - 90) * 0.3;
	}

	/** About sixty columns: how gentle the ground is round about, how much of it is flat, and how near the water. */
	static double full(TerrainProbe t, int x, int z) {
		int g = t.groundY(x, z);
		if (t.isWater(x, z) || g <= t.seaLevel()) {
			return Double.NEGATIVE_INFINITY;
		}
		for (int[] d : DIRS) {
			// the heart of a town stands on dry land
			if (t.isWater(x + d[0] * 10, z + d[1] * 10)) {
				return Double.NEGATIVE_INFINITY;
			}
		}
		double slope = 0;
		int flat = 0;
		int n = 0;
		for (int r : new int[]{12, 24, 40}) {
			for (int[] d : DIRS) {
				int sx = x + d[0] * r;
				int sz = z + d[1] * r;
				int h = t.isWater(sx, sz) ? g : t.groundY(sx, sz);
				slope += Math.abs(h - g) / (double) r;
				if (Math.abs(h - g) <= 3) {
					flat++;
				}
				n++;
			}
		}
		slope /= n;
		double water = 0;
		search:
		for (int r : new int[]{56, 80, 112}) {
			for (int[] d : DIRS) {
				if (t.isWater(x + d[0] * r, z + d[1] * r)) {
					water = r == 56 ? 10 : r == 80 ? 6 : 3;
					break search;
				}
			}
		}
		double altitude = Math.max(0, g - t.seaLevel() - 90) * 0.3;
		return -40 * slope + 0.5 * flat + water - altitude;
	}
}
