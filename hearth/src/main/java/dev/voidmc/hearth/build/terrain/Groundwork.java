package dev.voidmc.hearth.build.terrain;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Reconciling a building with the land under it, so it stands the way a builder would set it: footings down to firm
 * ground (never floating), cut and fill to the floor with natural-looking edges, retaining walls where the cut or fill is
 * deep, stilts over water, steps up to doors, and the clearing of what grows in the way. Every helper keeps to the plot.
 *
 * <p>All heights are local (relative to the floor level, see {@link Site}).
 */
public final class Groundwork {
	private Groundwork() {
	}

	private static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/**
	 * Clears what grows (trees, bushes, snow) from the natural ground up to {@code topY} over a rectangle (clipped to
	 * the plot): air steps in the CLEAR stage where nothing else is placed. Applied over a building's volume and a margin
	 * round it, this is what fells the tree that stood on the plot.
	 */
	public static void clear(Draft d, int x0, int z0, int x1, int z1, int topY) {
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.CLEAR)) {
			for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(d.depth() - 1, Math.max(z0, z1)); z++) {
				for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(d.frontage() - 1, Math.max(x0, x1)); x++) {
					int g = d.site().ground(x, z);
					int w = d.site().water(x, z);
					int from = w == Site.NONE ? g : w;
					for (int y = from; y <= topY; y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, air);
						}
					}
				}
			}
		}
	}

	/**
	 * Footings for a rectangle of walls standing on floor block row {@code floorBlockY}: foundation masonry under the wall
	 * line from that row down into the natural ground (one course below its surface, so nothing ever shows daylight
	 * under it), and under the floor inside, the ground made up with fill where it is low. Where the ground stands above
	 * the floor, the inside is dug out.
	 */
	public static void footings(Draft d, int x0, int z0, int x1, int z1, int floorBlockY) {
		Mat found = d.mat(Role.FOUNDATION);
		Mat fill = d.mat(Role.FILL);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int g = d.site().ground(x, z);
				int bottom = Math.min(floorBlockY, g - 2);
				if (edge) {
					try (var s = d.stage(StageKind.FOUNDATION)) {
						for (int y = bottom; y <= floorBlockY; y++) {
							d.set(x, y, z, found.full());
						}
					}
				} else if (g - 1 < floorBlockY - 1) {
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int y = Math.max(g - 1, floorBlockY - 6); y < floorBlockY; y++) {
							d.set(x, y, z, fill.full());
						}
					}
				}
				// dig out ground above the floor inside the walls
				if (!edge) {
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int y = floorBlockY + 1; y < g; y++) {
							d.set(x, y, z, air);
						}
					}
				}
			}
		}
	}

	/**
	 * Levels a rectangle of yard to standing level {@code y}: fills low ground with earth topped by the yard surface, cuts
	 * high ground down (topped with the yard surface again). Where the platform's edge stands more than a block above or
	 * below the land beyond it, a retaining wall of foundation stone holds the edge; otherwise the fill slopes off
	 * naturally.
	 */
	public static void level(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat ground = d.mat(Role.GROUND);
		Mat fill = d.mat(Role.FILL);
		Mat found = d.mat(Role.FOUNDATION);
		BlockState air = Blocks.AIR.defaultBlockState();
		int ax = Math.max(0, Math.min(x0, x1));
		int bx = Math.min(d.frontage() - 1, Math.max(x0, x1));
		int az = Math.max(0, Math.min(z0, z1));
		int bz = Math.min(d.depth() - 1, Math.max(z0, z1));
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				int g = d.site().ground(x, z);
				if (d.site().isWater(x, z)) {
					continue;
				}
				boolean edge = x == ax || x == bx || z == az || z == bz;
				int outside = edgeGround(d, x, z, ax, az, bx, bz);
				try (var s = d.stage(StageKind.EARTHWORK)) {
					if (g < y) {
						boolean wall = edge && y - outside > 1;
						for (int yy = g - 1; yy < y - 1; yy++) {
							d.set(x, yy, z, wall ? found.full() : fill.full());
						}
						d.set(x, y - 1, z, wall ? found.full() : ground.full());
					} else if (g > y) {
						for (int yy = y; yy < g; yy++) {
							d.set(x, yy, z, air);
						}
						d.set(x, y - 1, z, ground.full());
					}
				}
			}
		}
		// a retaining wall where the yard is cut below the land beyond its edge
		for (Side s : Side.of(ax, az, bx, bz)) {
			for (int i = 0; i < s.length(); i++) {
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (!inPlot(d, ox, oz)) {
					continue;
				}
				int og = d.site().ground(ox, oz);
				if (og - y > 1) {
					try (var st = d.stage(StageKind.EARTHWORK)) {
						for (int yy = y - 1; yy < og; yy++) {
							if (d.isAbsent(ox, yy, oz)) {
								d.set(ox, yy, oz, found.full());
							}
						}
					}
				}
			}
		}
	}

	private static int edgeGround(Draft d, int x, int z, int ax, int az, int bx, int bz) {
		int m = Integer.MAX_VALUE;
		if (x == ax) {
			m = Math.min(m, d.site().ground(x - 1, z));
		}
		if (x == bx) {
			m = Math.min(m, d.site().ground(x + 1, z));
		}
		if (z == az) {
			m = Math.min(m, d.site().ground(x, z - 1));
		}
		if (z == bz) {
			m = Math.min(m, d.site().ground(x, z + 1));
		}
		return m == Integer.MAX_VALUE ? d.site().ground(x, z) : m;
	}

	/**
	 * Slopes the yard round a building toward a target level: each yard column is made up (or cut) toward the floor near
	 * the walls and back to the natural ground with distance, one block of height per block of distance, so a house on a
	 * gentle slope sits on a natural little terrace rather than on a cliff of dirt.
	 *
	 * @param x0..z1 the building's footprint (walls)
	 * @param reach  how far from the walls to shape
	 */
	public static void apron(Draft d, int x0, int z0, int x1, int z1, int y, int reach) {
		Mat ground = d.mat(Role.GROUND);
		Mat fill = d.mat(Role.FILL);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = z0 - reach; z <= z1 + reach; z++) {
			for (int x = x0 - reach; x <= x1 + reach; x++) {
				if (!inPlot(d, x, z) || x >= x0 && x <= x1 && z >= z0 && z <= z1 || d.site().isWater(x, z)) {
					continue;
				}
				int dist = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
				int g = d.site().ground(x, z);
				int target = g < y ? Math.max(g, y - (dist - 1)) : Math.min(g, y + (dist - 1));
				if (target == g) {
					continue;
				}
				try (var s = d.stage(StageKind.EARTHWORK)) {
					if (target > g) {
						for (int yy = g - 1; yy < target - 1; yy++) {
							d.setIfAbsent(x, yy, z, fill.full());
						}
						d.setIfAbsent(x, target - 1, z, ground.full());
					} else {
						for (int yy = target; yy < g; yy++) {
							d.setIfAbsent(x, yy, z, Paint.of(air));
						}
						d.setIfAbsent(x, target - 1, z, ground.full());
					}
				}
			}
		}
	}

	/**
	 * Steps from a door down (or up) to the ground outside: starting at the entrance cell (x, y, z) and heading
	 * {@code dir}, stairs one block lower per block until the natural ground is reached, each on a footing. Stays in the
	 * plot; if the run would leave it, the remaining height is taken by a landing of blocks (the planner sets floors
	 * near the street level, so this is rare). Returns the cell after the last step.
	 */
	public static int[] steps(Draft d, int x, int y, int z, Direction dir, int width) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		int cx = x;
		int cz = z;
		int cy = y;
		Direction across = Dirs.right(dir);
		int half = (width - 1) / 2;
		for (int n = 0; n < 12; n++) {
			int g = d.site().ground(cx, cz);
			if (g >= cy || !inPlot(d, cx, cz)) {
				break;
			}
			// the step is at cy - 1 (its top at cy - 0.5 .. cy): a stair facing back toward the door
			for (int w = -half; w <= width - 1 - half; w++) {
				int sx = cx + Dirs.dx(across) * w;
				int sz = cz + Dirs.dz(across) * w;
				if (!inPlot(d, sx, sz)) {
					continue;
				}
				try (var st = d.stage(StageKind.EXTERIOR)) {
					boolean first = n == 0 && g >= cy - 1;
					d.set(sx, cy - 1, sz, paving.has(Form.STAIRS) ? paving.stairs(dir.getOpposite(), Half.BOTTOM) : stone.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
					d.set(sx, cy, sz, Blocks.AIR.defaultBlockState());
					d.set(sx, cy + 1, sz, Blocks.AIR.defaultBlockState());
					for (int yy = d.site().ground(sx, sz) - 1; yy < cy - 1; yy++) {
						d.set(sx, yy, sz, stone.full());
					}
				}
			}
			cx += Dirs.dx(dir);
			cz += Dirs.dz(dir);
			cy--;
		}
		return new int[]{cx, cy, cz};
	}

	/**
	 * Stilts under a floor at {@code floorBlockY}: posts down to the bed or the ground at the corners and every
	 * {@code spacing} blocks along the walls, cross-braced with fences where they are tall. Used over water and over ground
	 * that falls away too far for a footing.
	 */
	public static void stilts(Draft d, int x0, int z0, int x1, int z1, int floorBlockY, int spacing) {
		Mat post = d.mat(Role.STILT);
		Mat fence = d.mat(Role.STILT);
		for (Side s : Side.of(x0, z0, x1, z1)) {
			for (int i = 0; i < s.length(); i++) {
				if (!(s.isCorner(i) || i % spacing == 0)) {
					continue;
				}
				int x = s.x(i);
				int z = s.z(i);
				int g = d.site().ground(x, z);
				try (var st = d.stage(StageKind.FOUNDATION)) {
					for (int y = g - 2; y < floorBlockY; y++) {
						d.set(x, y, z, post.log(Direction.Axis.Y));
					}
				}
				// a diagonal brace toward the next post on tall stilts
				int h = floorBlockY - g;
				if (h >= 3 && i + 1 < s.length() && fence.has(Form.FENCE)) {
					int bx = s.x(i + 1);
					int bz = s.z(i + 1);
					try (var st = d.stage(StageKind.FOUNDATION)) {
						d.setIfAbsent(bx, floorBlockY - 1, bz, fence.fence());
					}
				}
			}
		}
	}

	/** Whether a rectangle needs stilts: water under it, or ground that falls more than {@code maxDrop} below the floor. */
	public static boolean needsStilts(Draft d, int x0, int z0, int x1, int z1, int maxDrop) {
		return d.site().anyWater(x0, z0, x1, z1) || d.site().minGround(x0, z0, x1, z1) < -maxDrop;
	}

	/** A plain ladder against a wall (only where nothing better fits). */
	public static void ladder(Draft d, int x, int z, int y0, int y1, Direction facing) {
		for (int y = y0; y <= y1; y++) {
			d.set(x, y, z, States.facing(Blocks.LADDER, facing));
		}
	}
}
