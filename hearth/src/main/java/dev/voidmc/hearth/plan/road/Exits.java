package dev.voidmc.hearth.plan.road;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Where a settlement's roads leave it (docs/LIVING_WORLD_2.md §1.2, {@code SettlementPlanner.exits}): the open ends of
 * its road plots, the ones that run out into the country rather than into another road — its {@code highway} arms
 * first, then its streets and lanes — farthest from its heart first. A country road ending short of the town is joined
 * to the nearest of them by a spur. Read from the built plots alone, so it answers for any settlement, planned by this
 * planner or not.
 */
public final class Exits {
	/** Exits named at most. */
	static final int MAX = 8;
	/** An end this close to its heart is not a way out. */
	static final int MIN_DISTANCE = 24;

	private Exits() {
	}

	private record End(int x, int y, int z, int plot, boolean highway, double distance) {
	}

	public static List<BlockPos> of(Settlement s) {
		List<int[]> lines = new ArrayList<>();
		List<PlotView> roads = new ArrayList<>();
		for (PlotView p : s.plots()) {
			if (!p.spec().archetype().getPath().equals("infra/road") || p.state() == PlotState.ABANDONED || p.spec().params().getBooleanOr("country", false)) {
				continue;
			}
			int[] pts = p.spec().paramInts("points").orElse(null);
			if (pts == null || pts.length < 6) {
				continue;
			}
			roads.add(p);
			lines.add(pts);
		}
		BlockPos c = s.center();
		List<End> ends = new ArrayList<>();
		for (int i = 0; i < roads.size(); i++) {
			int[] pts = lines.get(i);
			boolean highway = "highway".equals(roads.get(i).spec().params().getStringOr("kind", ""));
			int n = pts.length / 3;
			for (int v : new int[]{0, n - 1}) {
				int x = pts[v * 3];
				int y = pts[v * 3 + 1];
				int z = pts[v * 3 + 2];
				if (open(x, z, i, lines, roads)) {
					ends.add(new End(x, y, z, i, highway, Math.hypot(x - c.getX(), z - c.getZ())));
				}
			}
		}
		ends.removeIf(e -> e.distance < MIN_DISTANCE);
		ends.sort(Comparator.comparingInt((End e) -> e.highway ? 0 : 1).thenComparingDouble(e -> -e.distance));
		List<BlockPos> out = new ArrayList<>();
		for (End e : ends) {
			if (out.size() >= MAX) {
				break;
			}
			out.add(new BlockPos(e.x, e.y, e.z));
		}
		return out;
	}

	/** Whether an end of road plot {@code self} runs into no other road (no other plot's corridor reaches it). */
	static boolean open(int x, int z, int self, List<int[]> lines, List<PlotView> roads) {
		for (int j = 0; j < lines.size(); j++) {
			if (j == self) {
				continue;
			}
			int width = roads.get(j).spec().params().getIntOr("width", 3);
			if (distance(lines.get(j), x, z) <= width / 2.0 + 1.5) {
				return false;
			}
		}
		return true;
	}

	/** The distance from a column to a polyline. */
	static double distance(int[] pts, int x, int z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 5 < pts.length; i += 3) {
			double ax = pts[i];
			double az = pts[i + 2];
			double ex = pts[i + 3] - ax;
			double ez = pts[i + 5] - az;
			double len2 = ex * ex + ez * ez;
			double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ex + (z - az) * ez) / len2));
			best = Math.min(best, Math.hypot(x - ax - t * ex, z - az - t * ez));
		}
		return best;
	}
}
