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
import net.minecraft.world.level.block.Block;
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
		topY = Math.min(topY, d.maxHeight() - 1);
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
		level(d, x0, z0, x1, z1, y, false);
	}

	/**
	 * {@link #level}, optionally also filling water inside the rectangle (a yard or a square on a bank: the water is
	 * embanked behind a stone edge and made up with earth).
	 */
	public static void level(Draft d, int x0, int z0, int x1, int z1, int y, boolean fillWater) {
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
				boolean edge = x == ax || x == bx || z == az || z == bz;
				if (d.site().isWater(x, z)) {
					if (!fillWater) {
						continue;
					}
					// embank: stone at the edge of the water, earth within
					boolean waterEdge = edge || d.site().isWater(x + 1, z) != d.site().isWater(x - 1, z)
						|| d.site().isWater(x, z + 1) != d.site().isWater(x, z - 1);
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int yy = g - 1; yy < y - 1; yy++) {
							d.set(x, yy, z, waterEdge ? found.full() : fill.full());
						}
						d.set(x, y - 1, z, waterEdge ? found.full() : ground.full());
						// water standing above the new level: a dyke along the edge, drained within
						int wy = d.site().water(x, z);
						for (int yy = y; yy < wy; yy++) {
							d.set(x, yy, z, edge ? found.full() : Paint.of(air));
						}
						if (wy > y - 1) {
							d.drain(x, z);
						}
					}
					continue;
				}
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

	/** Which columns a {@link #level(Draft, int, int, int, int, int, boolean, Within)} levels. */
	@FunctionalInterface
	public interface Within {
		boolean test(int x, int z);
	}

	/**
	 * {@link #level(Draft, int, int, int, int, int, boolean)} over the columns of a shape that is not a rectangle (a
	 * ward inside a curtain that follows the hill): the columns in the box (x0, z0)–(x1, z1) that {@code in} accepts are
	 * made up or cut to standing level y as one piece. Its edge is where a column meets one outside the shape: only
	 * there does deep fill stand on a wall of foundation stone, and only beyond it does a retaining wall hold up land
	 * higher than the level; within, the made ground is the earth cut elsewhere.
	 */
	public static void level(Draft d, int x0, int z0, int x1, int z1, int y, boolean fillWater, Within in) {
		Mat ground = d.mat(Role.GROUND);
		Mat fill = d.mat(Role.FILL);
		Mat found = d.mat(Role.FOUNDATION);
		BlockState air = Blocks.AIR.defaultBlockState();
		int ax = Math.max(0, Math.min(x0, x1));
		int bx = Math.min(d.frontage() - 1, Math.max(x0, x1));
		int az = Math.max(0, Math.min(z0, z1));
		int bz = Math.min(d.depth() - 1, Math.max(z0, z1));
		Within inside = (x, z) -> x >= ax && x <= bx && z >= az && z <= bz && in.test(x, z);
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				if (!inside.test(x, z)) {
					continue;
				}
				int g = d.site().ground(x, z);
				// the edge: a neighbour outside the shape; the lowest ground beyond it
				boolean edge = false;
				int outside = Integer.MAX_VALUE;
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!inside.test(nx, nz)) {
						edge = true;
						outside = Math.min(outside, d.site().ground(nx, nz));
					}
				}
				if (outside == Integer.MAX_VALUE) {
					outside = g;
				}
				if (d.site().isWater(x, z)) {
					if (!fillWater) {
						continue;
					}
					boolean waterEdge = edge || d.site().isWater(x + 1, z) != d.site().isWater(x - 1, z)
						|| d.site().isWater(x, z + 1) != d.site().isWater(x, z - 1);
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int yy = g - 1; yy < y - 1; yy++) {
							d.set(x, yy, z, waterEdge ? found.full() : fill.full());
						}
						d.set(x, y - 1, z, waterEdge ? found.full() : ground.full());
						int wy = d.site().water(x, z);
						for (int yy = y; yy < wy; yy++) {
							d.set(x, yy, z, edge ? found.full() : Paint.of(air));
						}
						if (wy > y - 1) {
							d.drain(x, z);
						}
					}
					continue;
				}
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
		// a retaining wall where the level is cut below the land beyond its edge
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				if (!inside.test(x, z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int ox = x + Dirs.dx(dir);
					int oz = z + Dirs.dz(dir);
					if (inside.test(ox, oz) || !inPlot(d, ox, oz)) {
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
		boolean turned = false;
		for (int n = 0; n < 16 && inPlot(d, cx, cz); n++) {
			int g = d.site().ground(cx, cz);
			if (g >= cy) {
				break;
			}
			int nx = cx + Dirs.dx(dir);
			int nz = cz + Dirs.dz(dir);
			boolean last = g >= cy - 1;
			if (!turned && !last && !inPlot(d, nx, nz)) {
				// the run would leave the plot before reaching the ground: a landing here, then on along the plot
				Direction r = Dirs.right(dir);
				Direction l = Dirs.left(dir);
				int roomR = 0;
				int roomL = 0;
				while (roomR < 16 && inPlot(d, cx + Dirs.dx(r) * (roomR + 1), cz + Dirs.dz(r) * (roomR + 1))) {
					roomR++;
				}
				while (roomL < 16 && inPlot(d, cx + Dirs.dx(l) * (roomL + 1), cz + Dirs.dz(l) * (roomL + 1))) {
					roomL++;
				}
				try (var st = d.stage(StageKind.EXTERIOR)) {
					d.set(cx, cy - 1, cz, paving.full());
					d.set(cx, cy, cz, Blocks.AIR.defaultBlockState());
					d.set(cx, cy + 1, cz, Blocks.AIR.defaultBlockState());
					for (int yy = g - 1; yy < cy - 1; yy++) {
						d.set(cx, yy, cz, stone.full());
					}
				}
				dir = roomR >= roomL ? r : l;
				across = Dirs.right(dir);
				turned = true;
				cx += Dirs.dx(dir);
				cz += Dirs.dz(dir);
				cy--;
				continue;
			}
			// the step is at cy - 1 (its top at cy - 0.5 .. cy): a stair facing back up the flight
			for (int w = -half; w <= width - 1 - half; w++) {
				int sx = cx + Dirs.dx(across) * w;
				int sz = cz + Dirs.dz(across) * w;
				if (!inPlot(d, sx, sz)) {
					continue;
				}
				try (var st = d.stage(StageKind.EXTERIOR)) {
					d.set(sx, cy - 1, sz, paving.has(Form.STAIRS) ? paving.stairs(dir.getOpposite(), Half.BOTTOM) : stone.stairsOrSlab(dir.getOpposite(), Half.BOTTOM));
					d.set(sx, cy, sz, Blocks.AIR.defaultBlockState());
					d.set(sx, cy + 1, sz, Blocks.AIR.defaultBlockState());
					for (int yy = d.site().ground(sx, sz) - 1; yy < cy - 1; yy++) {
						d.set(sx, yy, sz, stone.full());
					}
				}
			}
			cx = nx;
			cz = nz;
			cy--;
		}
		return new int[]{cx, cy, cz};
	}

	/**
	 * A way from a door down (or up) to the street over uneven ground: a path found through the free cells of the plot
	 * from the entrance cell (x, z) to the front row, then laid step by step so its level changes by at most one block per
	 * cell, with stairs where it descends, paving where it runs level, a footing under every step and a cutting where the
	 * ground rises over it. Returns false if no free route reaches the front row.
	 *
	 * @param y the standing level at the entrance cell
	 */
	public static boolean approach(Draft d, int x, int y, int z) {
		int w = d.frontage();
		int dp = d.depth();
		if (!inPlot(d, x, z)) {
			return true;
		}
		// breadth-first over free cells (nothing built at the walking height) toward the front row
		int[] prev = new int[w * dp];
		java.util.Arrays.fill(prev, -2);
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		prev[z * w + x] = -1;
		q.add(new int[]{x, z});
		int end = -1;
		while (!q.isEmpty()) {
			int[] c = q.poll();
			if (c[1] == 0) {
				end = c[1] * w + c[0];
				break;
			}
			// prefer heading for the street: front first
			for (Direction dir : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
				int nx = c[0] + Dirs.dx(dir);
				int nz = c[1] + Dirs.dz(dir);
				if (!inPlot(d, nx, nz) || prev[nz * w + nx] != -2 || !free(d, nx, nz, y, surface(d, nx, nz))) {
					continue;
				}
				prev[nz * w + nx] = c[1] * w + c[0];
				q.add(new int[]{nx, nz});
			}
		}
		if (end < 0) {
			return false;
		}
		java.util.List<int[]> route = new java.util.ArrayList<>();
		for (int i = end; i != -1; i = prev[i]) {
			route.add(0, new int[]{i % w, i / w});
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		Mat path = d.mat(Role.PATH);
		int h = y;
		// the first cell (outside the door) must bear a person at the door's level: a landing on a footing if not
		int[] first = route.get(0);
		int g0 = surface(d, first[0], first[1]);
		if (!d.isSolid(first[0], h - 1, first[1]) && g0 < h) {
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(first[0], h - 1, first[1], paving.full());
				for (int yy = g0 - 1; yy < h - 1; yy++) {
					d.set(first[0], yy, first[1], stone.full());
				}
			}
		} else if (g0 > h) {
			// cut into rising ground: paving at the door's level, the earth above taken away
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(first[0], h - 1, first[1], paving.full());
				for (int yy = h; yy <= Math.max(h + 1, g0 - 1); yy++) {
					d.set(first[0], yy, first[1], Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int k = 1; k < route.size(); k++) {
			int[] from = route.get(k - 1);
			int[] c = route.get(k);
			Direction back = Dirs.of(from[0] - c[0], from[1] - c[1]);
			int g = surface(d, c[0], c[1]);
			// the ground ahead decides: descend a step, keep level, or climb a step
			int target = Math.clamp(g, h - 1, h + 1);
			try (var st = d.stage(StageKind.EXTERIOR)) {
				if (d.isSolid(c[0], h - 1, c[1]) && d.isEmpty(c[0], h, c[1]) && !groundLike(d.get(c[0], h - 1, c[1]))) {
					// a deck or a floor already there: walk on it
					continue;
				}
				int top;
				if (target < h) {
					// a stair whose high step is a block below where we stand: one down
					top = h - 2;
					d.set(c[0], top, c[1], paving.has(Form.STAIRS) ? paving.stairs(back, Half.BOTTOM) : stone.stairsOrSlab(back, Half.BOTTOM));
					h--;
				} else if (target > h) {
					// climbing: a step up, cut into the ground beyond
					top = h;
					d.set(c[0], top, c[1], paving.has(Form.STAIRS) ? paving.stairs(back.getOpposite(), Half.BOTTOM)
						: stone.stairsOrSlab(back.getOpposite(), Half.BOTTOM));
					h++;
				} else {
					top = h - 1;
					d.set(c[0], top, c[1], g >= h - 1 && g <= h ? path.full() : paving.full());
				}
				for (int yy = g - 1; yy < top; yy++) {
					d.set(c[0], yy, c[1], stone.full());
				}
				for (int yy = top + 1; yy <= Math.max(top + 2, g - 1); yy++) {
					d.set(c[0], yy, c[1], Blocks.AIR.defaultBlockState());
				}
			}
		}
		// not yet at the street's level at the front row: carry on down (or up) along it, toward the roomier side; the
		// last step lands level with the street, or a block above it (one step down)
		int[] last = route.get(route.size() - 1);
		int cx = last[0];
		if (!atStreet(d, cx, h)) {
			boolean up = street(d, cx) > h;
			int roomL = 0;
			int roomR = 0;
			while (roomL < w && inPlot(d, cx - roomL - 1, 0) && free(d, cx - roomL - 1, 0, h, surface(d, cx - roomL - 1, 0))) {
				roomL++;
			}
			while (roomR < w && inPlot(d, cx + roomR + 1, 0) && free(d, cx + roomR + 1, 0, h, surface(d, cx + roomR + 1, 0))) {
				roomR++;
			}
			int step = roomR >= roomL ? 1 : -1;
			Direction ahead = step > 0 ? Dirs.RIGHT : Dirs.LEFT;
			int room = Math.max(roomL, roomR);
			for (int k = 1; k <= room; k++) {
				int x1 = cx + step * k;
				int g = surface(d, x1, 0);
				try (var st = d.stage(StageKind.EXTERIOR)) {
					// a stair down has its top a block below the level we stand on; a stair up stands at our level
					int top = up ? h : h - 2;
					Direction face = up ? ahead : ahead.getOpposite();
					d.set(x1, top, 0, paving.has(Form.STAIRS) ? paving.stairs(face, Half.BOTTOM) : stone.stairsOrSlab(face, Half.BOTTOM));
					for (int yy = g - 1; yy < top; yy++) {
						d.set(x1, yy, 0, stone.full());
					}
					for (int yy = top + 1; yy <= Math.max(top + 2, g - 1); yy++) {
						d.set(x1, yy, 0, Blocks.AIR.defaultBlockState());
					}
				}
				h += up ? 1 : -1;
				if (atStreet(d, x1, h)) {
					break;
				}
			}
		}
		return true;
	}

	/** The street's standing level in front of column x (on the water where it is wet). */
	private static int street(Draft d, int x) {
		int wy = d.site().water(x, -1);
		return Math.max(d.site().ground(x, -1), wy == Site.NONE ? Integer.MIN_VALUE : wy);
	}

	/** Standing at level h on the front row at x, the street in front can be stepped onto (level, or a step down). */
	private static boolean atStreet(Draft d, int x, int h) {
		int s = street(d, x);
		return h == s || h == s + 1;
	}

	/**
	 * The standing level on the ground as the draft leaves it so far: the natural ground, raised by fill or a retaining
	 * wall laid on it (earthwork or foundation), or lowered by a cutting.
	 */
	public static int surface(Draft d, int x, int z) {
		int natural = d.site().ground(x, z);
		int g = natural;
		for (int k = 0; k < 64; k++) {
			Draft.Cell c = d.cell(x, g, z);
			if (c == null || c.state().isAir() || c.stage() != StageKind.EARTHWORK && c.stage() != StageKind.FOUNDATION) {
				break;
			}
			g++;
		}
		if (g == natural) {
			for (int k = 0; k < 64; k++) {
				Draft.Cell c = d.cell(x, g - 1, z);
				if (c == null || !c.state().isAir() || c.stage() != StageKind.EARTHWORK) {
					break;
				}
				g--;
			}
		}
		return g;
	}

	/**
	 * Nothing in the way where a person would walk between level y and the ground g: every cell empty, earth, or the
	 * deck or floor they would walk on at y - 1.
	 */
	private static boolean free(Draft d, int x, int z, int y, int g) {
		for (int yy = Math.min(y, g) - 1; yy <= Math.max(y, g) + 1; yy++) {
			BlockState s = d.get(x, yy, z);
			if (s == null || s.isAir() || groundLike(s) || yy == y - 1 && d.isSolid(x, yy, z) || yy < Math.min(y, g)) {
				continue;
			}
			return false;
		}
		return true;
	}

	/** Earth, sand, gravel, a path: what levelling and paths lay down, which a way may be cut through or laid over. */
	static boolean groundLike(BlockState s) {
		if (s == null) {
			return false;
		}
		Block b = s.getBlock();
		return b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.DIRT_PATH || b == Blocks.SAND || b == Blocks.RED_SAND
			|| b == Blocks.GRAVEL || b == Blocks.PODZOL || b == Blocks.MUD || b == Blocks.SNOW_BLOCK || b == Blocks.MOSS_BLOCK || b == Blocks.ROOTED_DIRT
			|| b == Blocks.PACKED_MUD || s.isAir();
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
