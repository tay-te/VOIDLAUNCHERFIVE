package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.road.Polyline;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The planner's invariants, checked on a grown settlement: what PLANNING.md promises the build module and the sim.
 * Each check returns a list of human-readable violations (empty when the plan is sound).
 */
final class PlanChecks {
	private PlanChecks() {
	}

	/** Whether a plot stands on the ground as a building (not a road, wall, terrace, walkway or farm). */
	static boolean building(PlotSpec s, Catalogue c) {
		var id = s.archetype();
		if (Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY) || id.equals(Archetypes.TERRACE)) {
			return false;
		}
		ArchetypeInfo info = c.info(id);
		return info != null && info.category() != ArchetypeCategory.INFRASTRUCTURE;
	}

	/** Footprint overlaps between plots that may not overlap. */
	static List<String> overlaps(Settlement s, Catalogue c) {
		List<String> out = new ArrayList<>();
		List<PlotView> plots = sorted(s);
		// column -> plot id for buildings
		Map<Long, Integer> owner = new HashMap<>();
		for (PlotView p : plots) {
			PlotSpec sp = p.spec();
			if (!building(sp, c)) {
				continue;
			}
			int stackedOn = sp.params().getIntOr("stacked_on", -1);
			boolean furniture = Archetypes.isPlazaFurniture(sp.archetype()) || sp.archetype().equals(Archetypes.PLAZA);
			for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
				for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
					long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
					Integer other = owner.putIfAbsent(key, p.id());
					if (other == null || other == p.id()) {
						continue;
					}
					PlotView o = s.plot(other);
					PlotSpec os = o.spec();
					boolean allowed = other == stackedOn || os.params().getIntOr("stacked_on", -1) == p.id()
						|| furniture && (Archetypes.isPlazaFurniture(os.archetype()) || os.archetype().equals(Archetypes.PLAZA))
						|| sp.params().getIntOr("replaces", -1) == other || os.params().getIntOr("replaces", -1) == p.id()
						|| defence(sp, c) && defence(os, c);
					if (!allowed) {
						out.add("plot " + p.id() + " " + sp.archetype() + " overlaps plot " + other + " " + os.archetype() + " at " + x + "," + z);
						break;
					}
				}
			}
		}
		// buildings against road corridors
		Occupancy roads = new Occupancy();
		Map<Long, Integer> roadOwner = new HashMap<>();
		for (PlotView p : plots) {
			var id = p.spec().archetype();
			if (id.equals(Archetypes.ROAD) || id.equals(Archetypes.STAIRS) || id.equals(Archetypes.BRIDGE)) {
				PlotGeometry.forEachColumn(p.spec(), (x, z) -> {
					roads.set(x, z, Cell.ROAD);
					roadOwner.put(((long) x << 32) ^ (z & 0xFFFFFFFFL), p.id());
				});
			}
		}
		for (PlotView p : plots) {
			PlotSpec sp = p.spec();
			if (!building(sp, c) || sp.params().getBooleanOr("over_road", false)) {
				continue;
			}
			int n = 0;
			int road = -1;
			for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
				for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
					if (roads.get(x, z) == Cell.ROAD) {
						n++;
						road = roadOwner.get(((long) x << 32) ^ (z & 0xFFFFFFFFL));
					}
				}
			}
			if (n > 0 && !sp.archetype().equals(Archetypes.PLAZA)) {
				out.add("plot " + p.id() + " " + sp.archetype() + " stands on " + n + " road columns of plot " + road + " "
					+ s.plot(road).spec().params().getStringOr("kind", "") + " " + s.plot(road).spec().archetype());
			}
		}
		return out;
	}

	private static boolean defence(PlotSpec s, Catalogue c) {
		ArchetypeInfo info = c.info(s.archetype());
		return info != null && info.category() == ArchetypeCategory.DEFENSE;
	}

	/** Buildings whose entrance does not open onto a road, bridge, plaza or a path to one. */
	static List<String> access(Settlement s, Catalogue c) {
		List<String> out = new ArrayList<>();
		Occupancy pub = new Occupancy();
		for (PlotView p : s.plots()) {
			var id = p.spec().archetype();
			if (id.equals(Archetypes.ROAD) || id.equals(Archetypes.STAIRS) || id.equals(Archetypes.BRIDGE) || id.equals(Archetypes.DOCK)) {
				PlotGeometry.forEachColumn(p.spec(), (x, z) -> pub.set(x, z, Cell.ROAD));
			} else if (id.equals(Archetypes.PLAZA)) {
				PlotGeometry.forEachColumn(p.spec(), (x, z) -> pub.set(x, z, Cell.PLAZA));
			}
		}
		for (PlotView p : sorted(s)) {
			PlotSpec sp = p.spec();
			if (!building(sp, c) || sp.archetype().equals(Archetypes.PLAZA)) {
				continue;
			}
			var params = sp.params();
			// stacked dwellings are reached across the roof below; wall towers from the wall walk; furniture stands in the square
			if (params.contains("stacked_on") || params.getBooleanOr("on_wall", false) || params.getBooleanOr("on_square", false)
				|| params.getBooleanOr("over_road", false)) {
				continue;
			}
			int[] e = PlotGeometry.entrance(sp);
			if (pub.get(e[0], e[1]) == Cell.FREE && !squareGreen(s, e[0], e[1])) {
				out.add("plot " + p.id() + " " + sp.archetype() + " at " + sp.origin() + " facing " + sp.facing() + ": entrance " + e[0] + "," + e[1]
					+ " is not on a road");
			}
		}
		return out;
	}

	/** The main square before it is paved is a village green: public ground all the same. */
	private static boolean squareGreen(Settlement s, int x, int z) {
		var st = s.data(PlanModule.STATE);
		var r = st.reservation("square");
		return r != null && r.contains(x, z);
	}

	/** Road plots climbing faster than a road may, and stair plots steeper than a stair. */
	static List<String> grades(Settlement s, PlanConfig cfg) {
		List<String> out = new ArrayList<>();
		for (PlotView p : sorted(s)) {
			var id = p.spec().archetype();
			boolean road = id.equals(Archetypes.ROAD);
			boolean stairs = id.equals(Archetypes.STAIRS);
			if (!road && !stairs) {
				continue;
			}
			int[] pts = p.spec().params().getIntArray("points").orElse(null);
			if (pts == null) {
				out.add("road plot " + p.id() + " has no points");
				continue;
			}
			Polyline.Dense d = new Polyline(pts).dense();
			for (int i = 1; i < d.length(); i++) {
				if (Math.abs(d.ys()[i] - d.ys()[i - 1]) > 1) {
					out.add((road ? "road " : "stairs ") + p.id() + " jumps " + (d.ys()[i] - d.ys()[i - 1]) + " blocks at step " + i);
					break;
				}
			}
			if (road && !p.spec().params().getStringOr("kind", "").equals("path")) {
				int w = 8;
				for (int i = 0; i + w < d.length(); i++) {
					double grade = Math.abs(d.ys()[i + w] - d.ys()[i]) / (double) w;
					// a graded road keeps its grade (with a block of rounding); steeper runs are issued as stairs
					if (grade > cfg.roadGrade() + 1.0 / w + 0.05) {
						out.add("road " + p.id() + " climbs " + String.format("%.2f", grade) + " over " + w + " blocks at step " + i);
						break;
					}
				}
			}
		}
		return out;
	}

	/** Buildings on ground too uneven for them that are not terraced (nor stepping down it as hillside archetypes). */
	static List<String> slopes(Settlement s, TerrainProbe t, Catalogue c, PlanConfig cfg) {
		List<String> out = new ArrayList<>();
		for (PlotView p : sorted(s)) {
			PlotSpec sp = p.spec();
			if (!building(sp, c) || sp.params().getBooleanOr("terraced", false) || sp.params().contains("stacked_on")
				|| Archetypes.isPlazaFurniture(sp.archetype()) || sp.archetype().equals(Archetypes.PLAZA) || sp.params().contains("replaces")) {
				continue;
			}
			ArchetypeInfo info = c.info(sp.archetype());
			int tol = info.flags().contains(SiteFlag.HILLSIDE) ? cfg.hillsideTolerance() : cfg.slopeTolerance();
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z += 2) {
				for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x += 2) {
					if (t.waterY(x, z) != TerrainProbe.NO_WATER) {
						continue;
					}
					int g = t.groundY(x, z);
					lo = Math.min(lo, g);
					hi = Math.max(hi, g);
				}
			}
			if (lo != Integer.MAX_VALUE && hi - lo > tol + 2 && info.category() != ArchetypeCategory.FARM) {
				out.add("plot " + p.id() + " " + sp.archetype() + " has " + (hi - lo) + " blocks of relief and no terrace");
			}
		}
		return out;
	}

	/** Plots outside their archetype's size ranges, or in water they cannot stand in. */
	static List<String> sizes(Settlement s, TerrainProbe t, Catalogue c) {
		List<String> out = new ArrayList<>();
		for (PlotView p : sorted(s)) {
			PlotSpec sp = p.spec();
			if (!building(sp, c) || sp.archetype().equals(Archetypes.PLAZA) || sp.params().contains("replaces")) {
				continue;
			}
			ArchetypeInfo info = c.info(sp.archetype());
			int f = sp.frontage();
			int d = sp.depth();
			boolean square = Archetypes.isPlazaFurniture(sp.archetype());
			if (!square && (f < info.minFrontage() || f > info.maxFrontage() + 1 || d < info.minDepth() || d > info.maxDepth() + 1)) {
				out.add("plot " + p.id() + " " + sp.archetype() + " is " + f + "x" + d + ", outside " + info.minFrontage() + "-" + info.maxFrontage()
					+ " x " + info.minDepth() + "-" + info.maxDepth());
			}
			boolean wetOk = info.flags().contains(SiteFlag.ON_WATER) || info.flags().contains(SiteFlag.WATERFRONT);
			if (!wetOk) {
				for (int z = sp.origin().getZ(); z < sp.origin().getZ() + sp.sizeZ(); z++) {
					for (int x = sp.origin().getX(); x < sp.origin().getX() + sp.sizeX(); x++) {
						if (t.waterY(x, z) != TerrainProbe.NO_WATER) {
							out.add("plot " + p.id() + " " + sp.archetype() + " stands in water at " + x + "," + z);
							z = Integer.MAX_VALUE - 1;
							break;
						}
					}
				}
			}
		}
		return out;
	}

	static List<PlotView> sorted(Settlement s) {
		List<PlotView> plots = new ArrayList<>(s.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		return plots;
	}
}
