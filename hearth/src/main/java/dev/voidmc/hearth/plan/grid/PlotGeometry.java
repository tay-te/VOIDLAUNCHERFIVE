package dev.voidmc.hearth.plan.grid;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.road.Polyline;
import net.minecraft.resources.Identifier;

/**
 * Where a plot really stands: the columns it takes and the {@link Cell} code it shows in the occupancy grid. Buildings
 * take their footprint; linear plots (roads, stairs, bridges, docks, walls) take only the corridor of their polyline,
 * not their bounding box; walkways take nothing on the ground (they pass overhead). Tests use the same rules to check
 * plans, so what the planner thinks it built is what is checked.
 */
public final class PlotGeometry {
	private PlotGeometry() {
	}

	/** The occupancy code a plot shows, from its archetype id (and catalogue category for anything else). */
	public static byte code(PlotSpec spec, Catalogue catalogue) {
		Identifier id = spec.archetype();
		if (id.equals(Archetypes.ROAD) || id.equals(Archetypes.STAIRS)) {
			return Cell.ROAD;
		}
		if (id.equals(Archetypes.BRIDGE)) {
			return Cell.BRIDGE;
		}
		if (id.equals(Archetypes.DOCK)) {
			return Cell.DOCK;
		}
		if (id.equals(Archetypes.WALKWAY)) {
			return Cell.FREE;
		}
		if (id.equals(Archetypes.TERRACE)) {
			return Cell.TERRACE;
		}
		if (id.equals(Archetypes.WALL) || id.equals(Archetypes.PALISADE)) {
			return Cell.WALL;
		}
		if (id.equals(Archetypes.PLAZA)) {
			return Cell.PLAZA;
		}
		ArchetypeInfo info = catalogue.info(id);
		ArchetypeCategory category = info != null ? info.category() : guessCategory(id);
		return switch (category) {
			case FARM -> Cell.FARM;
			case LANDSCAPE -> Cell.LANDSCAPE;
			default -> Cell.BUILDING;
		};
	}

	/** Visits every column the plot takes on the ground. */
	public static void forEachColumn(PlotSpec spec, Polyline.ColumnVisitor visitor) {
		Identifier id = spec.archetype();
		if (id.equals(Archetypes.WALKWAY)) {
			return;
		}
		if (Archetypes.isLinear(id)) {
			int[] pts = spec.paramInts("points").orElse(null);
			int width = spec.paramInt("width", 1);
			if (pts != null && pts.length >= 6) {
				Polyline.corridor(pts, width, visitor);
				return;
			}
		}
		int x0 = spec.origin().getX();
		int z0 = spec.origin().getZ();
		for (int z = z0; z < z0 + spec.sizeZ(); z++) {
			for (int x = x0; x < x0 + spec.sizeX(); x++) {
				visitor.visit(x, z);
			}
		}
	}

	/** Marks a plot in the grid (only over lower-ranked codes). */
	public static void mark(Occupancy occ, PlotSpec spec, Catalogue catalogue) {
		byte code = code(spec, catalogue);
		if (code == Cell.FREE) {
			return;
		}
		forEachColumn(spec, (x, z) -> occ.mark(x, z, code));
		// the side gaps a detached building keeps (params "gap", "back_gap") stay open ground
		if (!Archetypes.isLinear(spec.archetype())) {
			int gap = spec.paramInt("gap", 0);
			int back = spec.paramInt("back_gap", 0);
			if (gap > 0 || back > 0) {
				int x0 = spec.origin().getX();
				int z0 = spec.origin().getZ();
				int x1 = x0 + spec.sizeX() - 1;
				int z1 = z0 + spec.sizeZ() - 1;
				boolean alongX = spec.facing().getAxis() == net.minecraft.core.Direction.Axis.Z;
				if (alongX) {
					occ.markRect(x0 - gap, z0, x0 - 1, z1, Cell.SETBACK);
					occ.markRect(x1 + 1, z0, x1 + gap, z1, Cell.SETBACK);
				} else {
					occ.markRect(x0, z0 - gap, x1, z0 - 1, Cell.SETBACK);
					occ.markRect(x0, z1 + 1, x1, z1 + gap, Cell.SETBACK);
				}
				switch (spec.facing()) {
					case NORTH -> occ.markRect(x0, z1 + 1, x1, z1 + back, Cell.SETBACK);
					case SOUTH -> occ.markRect(x0, z0 - back, x1, z0 - 1, Cell.SETBACK);
					case WEST -> occ.markRect(x1 + 1, z0, x1 + back, z1, Cell.SETBACK);
					default -> occ.markRect(x0 - back, z0, x0 - 1, z1, Cell.SETBACK);
				}
			}
		}
	}

	/**
	 * The rectangle {x0, z0, x1, z1} (inclusive) holding every column {@link #mark} may write for the plot, its kept gaps
	 * and a corridor's reach included; null if it marks nothing.
	 */
	public static int @org.jspecify.annotations.Nullable [] bounds(PlotSpec spec) {
		Identifier id = spec.archetype();
		if (id.equals(Archetypes.WALKWAY)) {
			return null;
		}
		if (Archetypes.isLinear(id)) {
			int[] pts = spec.paramInts("points").orElse(null);
			if (pts != null && pts.length >= 6) {
				return pointsBounds(pts, Polyline.reach(spec.paramInt("width", 1)));
			}
		}
		int pad = Archetypes.isLinear(id) ? 0 : Math.max(0, Math.max(spec.paramInt("gap", 0), spec.paramInt("back_gap", 0)));
		int x0 = spec.origin().getX();
		int z0 = spec.origin().getZ();
		return new int[]{x0 - pad, z0 - pad, x0 + spec.sizeX() - 1 + pad, z0 + spec.sizeZ() - 1 + pad};
	}

	/** The rectangle round a polyline's vertices (x, y, z triples), widened by {@code reach} on every side. */
	public static int[] pointsBounds(int[] pts, int reach) {
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (int i = 0; i + 2 < pts.length; i += 3) {
			x0 = Math.min(x0, pts[i]);
			x1 = Math.max(x1, pts[i]);
			z0 = Math.min(z0, pts[i + 2]);
			z1 = Math.max(z1, pts[i + 2]);
		}
		return new int[]{x0 - reach, z0 - reach, x1 + reach, z1 + reach};
	}

	/** Whether two inclusive rectangles {x0, z0, x1, z1} share a column. */
	public static boolean overlaps(int[] a, int[] b) {
		return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
	}

	/** The column in front of a building's main entrance: just outside the middle of its front edge. */
	public static int[] entrance(PlotSpec spec) {
		int x0 = spec.origin().getX();
		int z0 = spec.origin().getZ();
		int x1 = x0 + spec.sizeX() - 1;
		int z1 = z0 + spec.sizeZ() - 1;
		return switch (spec.facing()) {
			case NORTH -> new int[]{x0 + spec.sizeX() / 2, z0 - 1};
			case SOUTH -> new int[]{x0 + spec.sizeX() / 2, z1 + 1};
			case WEST -> new int[]{x0 - 1, z0 + spec.sizeZ() / 2};
			default -> new int[]{x1 + 1, z0 + spec.sizeZ() / 2};
		};
	}

	private static ArchetypeCategory guessCategory(Identifier id) {
		String path = id.getPath();
		int slash = path.indexOf('/');
		String head = slash < 0 ? path : path.substring(0, slash);
		return switch (head) {
			case "house" -> ArchetypeCategory.HOUSING;
			case "farm" -> ArchetypeCategory.FARM;
			case "commerce" -> ArchetypeCategory.COMMERCE;
			case "civic" -> ArchetypeCategory.CIVIC;
			case "storage" -> ArchetypeCategory.STORAGE;
			case "defense" -> ArchetypeCategory.DEFENSE;
			case "infra" -> ArchetypeCategory.INFRASTRUCTURE;
			case "landscape" -> ArchetypeCategory.LANDSCAPE;
			case "landmark" -> ArchetypeCategory.LANDMARK;
			default -> ArchetypeCategory.WORKPLACE;
		};
	}
}
