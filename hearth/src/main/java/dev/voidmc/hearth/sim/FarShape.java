package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotSpec;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A building as VOID LOD draws it at the horizon: the columns of its plot the building itself covers (not its yard,
 * garden, field or paths), its walls' and roof's most common map colours, and its eave and ridge above the floor.
 *
 * <p>The mask is kept in the plot's local frame, seen from the street ({@code x} across the frontage, {@code z} from the
 * front row back; one row and column of overhang either side), so a shape taken from one plot can be laid on another
 * plot of its kind facing any way and of another size ({@link #paint}, scaled by nearest cell).
 *
 * <ul>
 *   <li>A column is covered when something solid across its whole cell (a wall, stairs, a slab; not leaves, plants,
 *   fences, panes or posts) stands in it at least 2 above the floor, and it belongs to a piece of at least 4 such columns
 *   (a lone tree trunk or a stack of crates is not a building).</li>
 *   <li>Wall colour: the most common map colour of full blocks on the outside of covered columns from the floor to the
 *   eave; roof colour: the most common map colour of the tops of covered columns at or above the eave. The most common,
 *   not the average: plaster with timber reads as plaster, not as a grey mix of the two.</li>
 *   <li>Ridge: the highest covered top, except that a flat roof (under a fifth of the covered columns rise 2 or more
 *   above the eave) has its ridge at the eave.</li>
 * </ul>
 */
final class FarShape {
	/** The plot's frontage and depth (local frame) this mask was taken on. */
	final int frontage;
	final int depth;
	/** Covered cells, {@code (frontage + 2) × (depth + 2)}, index {@code (z + 1) * (frontage + 2) + x + 1}. */
	final long[] bits;
	final int wall;
	final int roof;
	final int eaveRel;
	final int ridgeRel;

	FarShape(int frontage, int depth, long[] bits, int wall, int roof, int eaveRel, int ridgeRel) {
		this.frontage = frontage;
		this.depth = depth;
		this.bits = bits;
		this.wall = wall;
		this.roof = roof;
		this.eaveRel = eaveRel;
		this.ridgeRel = ridgeRel;
	}

	static int frontage(PlotSpec spec) {
		return spec.facing().getAxis() == Direction.Axis.Z ? spec.sizeX() : spec.sizeZ();
	}

	static int depth(PlotSpec spec) {
		return spec.facing().getAxis() == Direction.Axis.Z ? spec.sizeZ() : spec.sizeX();
	}

	boolean covered(int x, int z) {
		int i = (z + 1) * (this.frontage + 2) + x + 1;
		return (this.bits[i >> 6] >>> (i & 63) & 1) != 0;
	}

	int cells() {
		int n = 0;
		for (long b : this.bits) {
			n += Long.bitCount(b);
		}
		return n;
	}

	/** The shape of a blueprint's building, or null if it covers nothing (a field, a yard, a road, a dug pit). */
	static @Nullable FarShape of(Blueprint bp) {
		PlotSpec spec = bp.spec();
		int f = frontage(spec);
		int d = depth(spec);
		int w = f + 2;
		int h = d + 2;
		int floor = spec.floorY();
		// what stands at each position once every step is done (later steps win), on a dense grid over what is above the
		// floor: local x, z (with the overhang) by y from the floor up
		var box = bp.bounds();
		int y0 = floor;
		int y1 = Math.max(y0, box.maxY());
		int ny = y1 - y0 + 1;
		BlockState[] grid = new BlockState[w * h * ny];
		for (int s = 0; s < bp.stageCount(); s++) {
			int n = bp.stepCount(s);
			for (int i = 0; i < n; i++) {
				bp.step(s, i, (x, y, z, state, tag) -> {
					if (y < y0 || y > y1) {
						return;
					}
					int c = cell(spec, x, z, f, d);
					if (c >= 0) {
						grid[(y - y0) * w * h + c] = state;
					}
				});
			}
		}
		var sil = bp.silhouette();
		int eave = sil.eaveY() - floor;
		int[] top = new int[w * h];
		BlockState[] topState = new BlockState[w * h];
		boolean[] raw = new boolean[w * h];
		for (int c = 0; c < w * h; c++) {
			top[c] = Integer.MIN_VALUE;
			for (int ly = ny - 1; ly >= 2; ly--) {
				BlockState st = grid[ly * w * h + c];
				if (st != null && solid(st)) {
					top[c] = ly;
					topState[c] = st;
					break;
				}
			}
			raw[c] = top[c] >= 2;
		}
		// what is left once lone posts and small bits (lamp posts, signs, a well's crank) are dropped: pieces of 4 or more
		long[] bits = new long[(w * h + 63) >> 6];
		int count = 0;
		int[] piece = new int[w * h];
		boolean[] seen = new boolean[w * h];
		for (int start = 0; start < w * h; start++) {
			if (!raw[start] || seen[start]) {
				continue;
			}
			int n = 0;
			piece[n++] = start;
			seen[start] = true;
			for (int k = 0; k < n; k++) {
				int i = piece[k];
				int x = i % w;
				int z = i / w;
				if (x > 0 && raw[i - 1] && !seen[i - 1]) {
					seen[i - 1] = true;
					piece[n++] = i - 1;
				}
				if (x + 1 < w && raw[i + 1] && !seen[i + 1]) {
					seen[i + 1] = true;
					piece[n++] = i + 1;
				}
				if (z > 0 && raw[i - w] && !seen[i - w]) {
					seen[i - w] = true;
					piece[n++] = i - w;
				}
				if (z + 1 < h && raw[i + w] && !seen[i + w]) {
					seen[i + w] = true;
					piece[n++] = i + w;
				}
			}
			if (n >= 4) {
				for (int k = 0; k < n; k++) {
					bits[piece[k] >> 6] |= 1L << (piece[k] & 63);
				}
				count += n;
			}
		}
		if (count == 0) {
			return null;
		}
		Int2IntOpenHashMap roofs = new Int2IntOpenHashMap();
		Int2IntOpenHashMap walls = new Int2IntOpenHashMap();
		int ridge = Integer.MIN_VALUE;
		int risen = 0;
		int wallTop = Math.min(eave, ny - 1);
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				int i = z * w + x;
				if ((bits[i >> 6] >>> (i & 63) & 1) == 0) {
					continue;
				}
				// the roof: the top of a covered column at or above the eave
				ridge = Math.max(ridge, top[i]);
				if (top[i] >= eave + 2) {
					risen++;
				}
				if (top[i] >= eave - 1) {
					count(roofs, topState[i]);
				}
				// the walls: full blocks on the outside, floor to eave
				for (int ly = 0; ly <= wallTop; ly++) {
					BlockState st = grid[ly * w * h + i];
					if (st == null || st.isAir() || !st.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
						continue;
					}
					int base = ly * w * h;
					if (x == 0 || x + 1 == w || z == 0 || z + 1 == h || open(grid[base + i - 1]) || open(grid[base + i + 1]) || open(grid[base + i - w])
						|| open(grid[base + i + w])) {
						count(walls, st);
					}
				}
			}
		}
		int wall = mode(walls, sil.wallColor());
		int roof = mode(roofs, sil.roofColor());
		ridge = Math.max(ridge, eave);
		if (risen * 5 < count) {
			ridge = eave;
		}
		return new FarShape(f, d, bits, wall, roof, eave, ridge);
	}

	/**
	 * Something a building is made of, seen from above: a block whose collision covers its whole cell across (walls,
	 * stairs, slabs, roofs), not leaves, and not a thin one (fences, railings, panes, posts, lanterns: a yard fence up a
	 * slope stands above the floor too).
	 */
	private static boolean solid(BlockState st) {
		if (st.isAir() || st.getBlock() instanceof LeavesBlock) {
			return false;
		}
		var shape = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) {
			return false;
		}
		var b = shape.bounds();
		return b.minX <= 0.01 && b.minZ <= 0.01 && b.maxX >= 0.99 && b.maxZ >= 0.99;
	}

	private static boolean open(@Nullable BlockState s) {
		return s == null || s.isAir();
	}

	private static void count(Int2IntOpenHashMap acc, @Nullable BlockState s) {
		if (s == null) {
			return;
		}
		int col = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
		if (col != 0) {
			acc.addTo(col, 1);
		}
	}

	private static int mode(Int2IntOpenHashMap acc, int fallback) {
		int best = fallback;
		int bestN = 0;
		for (var e : acc.int2IntEntrySet()) {
			int n = e.getIntValue();
			if (n > bestN || n == bestN && e.getIntKey() < best) {
				best = e.getIntKey();
				bestN = n;
			}
		}
		return best;
	}

	/** The mask cell of a world column, or -1 outside the plot and its overhang. */
	private static int cell(PlotSpec spec, int wx, int wz, int f, int d) {
		int ox = spec.origin().getX();
		int oz = spec.origin().getZ();
		int sx = spec.sizeX();
		int sz = spec.sizeZ();
		int lx;
		int lz;
		switch (spec.facing()) {
			case SOUTH -> {
				lx = wx - ox;
				lz = oz + sz - 1 - wz;
			}
			case NORTH -> {
				lx = ox + sx - 1 - wx;
				lz = wz - oz;
			}
			case EAST -> {
				lx = oz + sz - 1 - wz;
				lz = ox + sx - 1 - wx;
			}
			default -> {
				lx = wz - oz;
				lz = wx - ox;
			}
		}
		if (lx < -1 || lx > f || lz < -1 || lz > d) {
			return -1;
		}
		return (lz + 1) * (f + 2) + lx + 1;
	}

	/** Receives the world columns a shape covers on a plot. */
	@FunctionalInterface
	interface ColumnSink {
		void accept(int x, int z);
	}

	/**
	 * The world columns this shape covers when laid on a plot ({@code ox, oz, sizeX, sizeZ, facing}): exact on a plot of
	 * the size it was taken on, scaled by nearest cell on another (the overhang row stays one row).
	 */
	void paint(int ox, int oz, int sx, int sz, Direction facing, ColumnSink sink) {
		int f = facing.getAxis() == Direction.Axis.Z ? sx : sz;
		int d = facing.getAxis() == Direction.Axis.Z ? sz : sx;
		for (int lz = -1; lz <= d; lz++) {
			int mz = scale(lz, d, this.depth);
			for (int lx = -1; lx <= f; lx++) {
				if (!covered(scale(lx, f, this.frontage), mz)) {
					continue;
				}
				int wx;
				int wz;
				switch (facing) {
					case SOUTH -> {
						wx = ox + lx;
						wz = oz + sz - 1 - lz;
					}
					case NORTH -> {
						wx = ox + sx - 1 - lx;
						wz = oz + lz;
					}
					case EAST -> {
						wx = ox + sx - 1 - lz;
						wz = oz + sz - 1 - lx;
					}
					default -> {
						wx = ox + lz;
						wz = oz + lx;
					}
				}
				sink.accept(wx, wz);
			}
		}
	}

	private static int scale(int l, int target, int source) {
		if (l < 0) {
			return -1;
		}
		if (l >= target) {
			return source;
		}
		return target == source ? l : Math.min(source - 1, (int) ((l + 0.5) * source / target));
	}
}
