package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The ground a landmark stands on and the way folk come to it: masonry footings down to firm ground, a podium levelled
 * and paved round the building with stone edges where it stands above the land, an arched doorway, and a forecourt
 * between the building and the street with benches, lamps and planting.
 */
public final class Grounds {
	private Grounds() {
	}

	/**
	 * Footings under a rectangle of walls on floor block row {@code floorBlockY}: masonry under the outline down below the
	 * ground (through water to the bed), the inside made up with fill or dug out to the floor.
	 */
	public static void footings(Draft d, int x0, int z0, int x1, int z1, int floorBlockY) {
		Stone found = Stone.footing(d);
		Mat fill = d.mat(Role.FILL);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!Walls.inPlot(d, x, z)) {
					continue;
				}
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int g = d.site().ground(x, z);
				if (edge) {
					try (var s = d.stage(StageKind.FOUNDATION)) {
						for (int y = Math.min(floorBlockY, g - 2); y <= floorBlockY; y++) {
							d.set(x, y, z, found.full());
						}
					}
				} else {
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int y = Math.max(g - 1, floorBlockY - 12); y < floorBlockY; y++) {
							d.set(x, y, z, fill.full());
						}
						for (int y = floorBlockY + 1; y < g; y++) {
							d.set(x, y, z, air);
						}
					}
				}
			}
		}
	}

	/**
	 * A podium: the rectangle levelled to standing level y and paved, with a masonry edge (a plinth course and its
	 * footing) wherever it stands above the land beyond, and a cut held by a retaining wall where the land rises over
	 * it. Water inside is embanked and filled.
	 */
	public static void podium(Draft d, int x0, int z0, int x1, int z1, int y, boolean pave) {
		Groundwork.level(d, x0, z0, x1, z1, y, true);
		Stone edge = Stone.footing(d);
		Mat paving = d.mat(Role.PAVING);
		int ax = Math.max(0, x0);
		int bx = Math.min(d.frontage() - 1, x1);
		int az = Math.max(0, z0);
		int bz = Math.min(d.depth() - 1, z1);
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				boolean border = x == ax || x == bx || z == az || z == bz;
				int g = d.site().ground(x, z);
				if (border && g < y) {
					try (var s = d.stage(StageKind.FOUNDATION)) {
						for (int yy = g - 1; yy < y; yy++) {
							d.set(x, yy, z, edge.full());
						}
					}
				}
				if (pave && d.isEmpty(x, y, z)) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y - 1, z, border && g < y ? edge.full() : paving.full());
					}
				}
				// a parapet where the platform stands four or more over the ground beyond it (not on the street side,
				// where the way down is laid)
				if (border && z > 0 && y - lowestBeyond(d, x, z, x0, z0, x1, z1) >= 4) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y, z, edge.balustrade(d));
					}
				}
			}
		}
	}

	/**
	 * Opens the podium's parapet before every entrance (call once the doors are drawn): a balustrade laid on the border
	 * before the doors were known never stands in a doorway or across the way to it.
	 */
	public static void openEntrances(Draft d) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] e : d.entrances()) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						net.minecraft.world.level.block.state.BlockState st = d.get(e[0] + dx, e[1], e[2] + dz);
						if (st != null && (st.getBlock() instanceof net.minecraft.world.level.block.WallBlock
							|| st.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)) {
							d.set(e[0] + dx, e[1], e[2] + dz, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	/** The lowest dry ground beside a border cell of the rectangle, outside it. */
	private static int lowestBeyond(Draft d, int x, int z, int x0, int z0, int x1, int z1) {
		int low = Integer.MAX_VALUE;
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (nx >= x0 && nx <= x1 && nz >= z0 && nz <= z1) {
				continue;
			}
			if (d.site().isWater(nx, nz)) {
				// (a drop into water hurts nobody)
				continue;
			}
			low = Math.min(low, d.site().ground(nx, nz));
		}
		return low;
	}

	/**
	 * An arched doorway on a wall: a door at cell i standing on row y under a round head (a transom of glass in the arch),
	 * dressed jambs and voussoirs, a lamp beside it; registers the entrance and the DOOR point.
	 */
	public static void door(Draft d, Side f, int i, int y, Stone dress) {
		try (var s = d.stage(StageKind.OPENINGS)) {
			Details.arch(d, f, i, 1, y, 3, dress, d.mat(Role.GLASS).pane());
		}
		Openings.door(d, f, f.column(i), y, 1, Openings.DoorOpts.MAIN);
		threshold(d, f, i, 1, y, dress);
	}

	/** A sill of the dressing under a doorway where the ground was cut away below it (a slope falling to the street). */
	private static void threshold(Draft d, Side f, int i, int width, int y, Stone dress) {
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int c = 0; c < width; c++) {
				int x = f.x(i + c);
				int z = f.z(i + c);
				if (!d.isSolid(x, y - 1, z)) {
					d.set(x, y - 1, z, dress.full());
				}
			}
		}
	}

	/** A double doorway (two leaves) at cells i, i + 1 under a round head shouldered by stairs. */
	public static void doubleDoor(Draft d, Side f, int i, int y, Stone dress) {
		try (var s = d.stage(StageKind.OPENINGS)) {
			Details.arch(d, f, i, 2, y, 3, dress, d.mat(Role.GLASS).pane());
		}
		Openings.door(d, f, f.column(i), y, 2, Openings.DoorOpts.MAIN);
		threshold(d, f, i, 2, y, dress);
	}

	/**
	 * The forecourt between a building's front {@code outer[1]} and the street: the way from the door (column doorX,
	 * standing level y) to the street, laid with steps where the levels differ; paving round the foot where the ground is
	 * level with it; a bench, a lamp post and planting either side.
	 */
	public static void forecourt(Draft d, int[] outer, int doorX, int y) {
		forecourt(d, outer, doorX, y, outer[1] - 1);
	}

	/** {@link #forecourt} with the entrance cell's row given (a door set back in a recess between piers). */
	public static void forecourt(Draft d, int[] outer, int doorX, int y, int entranceZ) {
		int front = outer[1];
		Mat paving = d.mat(Role.PAVING);
		// the way to the street first, so nothing is put in its path
		if (entranceZ >= 0) {
			Groundwork.approach(d, doorX, y, entranceZ);
		}
		// an apron of paving round the building's foot, where the ground meets it
		for (int z = Math.max(0, outer[1] - 1); z <= Math.min(d.depth() - 1, outer[3] + 1); z++) {
			for (int x = Math.max(0, outer[0] - 1); x <= Math.min(d.frontage() - 1, outer[2] + 1); x++) {
				boolean inside = x >= outer[0] && x <= outer[2] && z >= outer[1] && z <= outer[3];
				if (inside || !d.isEmpty(x, y, z) || !d.isAbsent(x, y - 1, z)) {
					continue;
				}
				if (d.site().ground(x, z) == y && !d.site().isWater(x, z)) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(x, y - 1, z, paving.full());
					}
				}
			}
		}
		// either side of the way: a bench facing the street and a lamp post, flowers in pots
		if (front >= 2) {
			int z = Math.max(0, front - 2);
			for (int side : new int[]{-1, 1}) {
				int bx = doorX + side * 2;
				if (bx < 0 || bx >= d.frontage() || !standable(d, bx, y, z)) {
					continue;
				}
				if (side < 0) {
					Furniture.chair(d, bx, y, z, Dirs.BACK);
					d.poi(PoiType.SEAT, bx, y, z, Dirs.FRONT);
				} else {
					Garden.lampPost(d, bx, y, z, 2, null);
				}
				int px = bx + side;
				if (px >= 0 && px < d.frontage() && standable(d, px, y, z) && Noise.unit(d.seed(), px, y, z) < 0.7) {
					Garden.pot(d, px, y, z);
				}
			}
		}
	}

	/** Whether a cell is inside the plot's footprint. */
	public static boolean inPlotBounds(Draft d, int x, int z) {
		return Walls.inPlot(d, x, z);
	}

	/** Free ground or paving at standing level y with nothing on it. */
	public static boolean standable(Draft d, int x, int y, int z) {
		if (!Walls.inPlot(d, x, z) || !d.isAbsent(x, y, z) && !d.isEmpty(x, y, z)) {
			return false;
		}
		BlockState below = d.get(x, y - 1, z);
		if (below != null) {
			return !below.isAir() && d.isSolid(x, y - 1, z);
		}
		return d.site().ground(x, z) == y && !d.site().isWater(x, z);
	}


	/** Ground beyond the plot beside (x, z) stands high enough to step from onto a leaf at row y (a bank on a slope). */
	private static boolean steppedOnto(Draft d, int x, int y, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (!Walls.inPlot(d, nx, nz) && d.site().ground(nx, nz) >= y - 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A tree on free ground at (x, y, z): a trunk of the style's garden wood and a crown of its leaves in three layers,
	 * every leaf touching the trunk or another leaf, kept inside the plot. Planted only where the ground bears it.
	 */
	public static boolean tree(Draft d, int x, int y, int z, int height) {
		if (!standable(d, x, y, z)) {
			return false;
		}
		Mat log = d.mat(Role.TREE_LOG);
		Mat leaves = d.mat(Role.TREE_LEAVES);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			BlockState below = d.get(x, y - 1, z);
			if (below == null || !below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.DIRT)) {
				d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
			}
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, log.log(Direction.Axis.Y).orElse(log.full()));
			}
			int top = y + height;
			double[] radii = {1.8, 2.2, 1.6, 0.8};
			java.util.Set<Long> leaf = new java.util.HashSet<>();
			for (int k = 0; k < radii.length; k++) {
				int yy = top - 2 + k;
				for (int[] c : Shapes.disc(x, z, radii[k])) {
					boolean edge = Math.hypot(c[0] - x, c[1] - z) > radii[k] - 0.7;
					if (edge && Noise.unit(d.seed() ^ 0x7EE, c[0], yy, c[1]) < 0.3 && k != 1) {
						continue;
					}
					if (Walls.inPlot(d, c[0], c[1]) && d.isEmpty(c[0], yy, c[1]) && d.allowed(c[0], yy, c[1]) && !steppedOnto(d, c[0], yy, c[1])) {
						d.set(c[0], yy, c[1], leaves.leaves());
						leaf.add(Draft.key(c[0], yy, c[1]));
					}
				}
			}
			// keep only the leaves joined to the trunk face to face; the rest would hang in the air
			java.util.Set<Long> held = new java.util.HashSet<>();
			java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
			for (int k = 0; k < height; k++) {
				long t = Draft.key(x, y + k, z);
				held.add(t);
				q.add(t);
			}
			while (!q.isEmpty()) {
				long c = q.poll();
				int cx = Draft.keyX(c);
				int cy = Draft.keyY(c);
				int cz = Draft.keyZ(c);
				for (int[] o : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					long n = Draft.key(cx + o[0], cy + o[1], cz + o[2]);
					if (leaf.contains(n) && held.add(n)) {
						q.add(n);
					}
				}
			}
			for (long c : leaf) {
				if (!held.contains(c)) {
					d.remove(Draft.keyX(c), Draft.keyY(c), Draft.keyZ(c));
				}
			}
		}
		return true;
	}

	/** Paves a rectangle at standing level y where the ground is level with it (or already paved/filled to it). */
	public static void pave(Draft d, int x0, int z0, int x1, int z1, int y, Paint p) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.max(0, z0); z <= Math.min(d.depth() - 1, z1); z++) {
				for (int x = Math.max(0, x0); x <= Math.min(d.frontage() - 1, x1); x++) {
					if (d.isEmpty(x, y, z) && (d.site().ground(x, z) == y || d.isSolid(x, y - 1, z)) && !d.site().isWater(x, z)) {
						d.set(x, y - 1, z, p);
					}
				}
			}
		}
	}

	/** The direction from a cell toward the street-facing outside of a building (for benches facing out). */
	public static Direction out(Side s) {
		return s.out();
	}

	/** Sets a stair step paint for a raised threshold. */
	public static Paint step(Stone st, Direction up) {
		return st.has(Form.STAIRS) ? st.stairs(up, Half.BOTTOM) : st.full();
	}
}
