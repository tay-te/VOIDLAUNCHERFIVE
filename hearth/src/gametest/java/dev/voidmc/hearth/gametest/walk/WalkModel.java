package dev.voidmc.hearth.gametest.walk;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Where a player (0.6 x 1.8, step 0.6, jump 1.25) can stand and go in and round a building as it stands in the live
 * world, read from the blocks' real collision shapes: a cell is a block column position whose floor (the top of what is
 * under the feet) lies in {@code [y - 0.4, y + 0.6)} with 1.8 free above it, or a ladder. Moves: walk (a rise of up to 0.6, or
 * up a stair from its low side), jump (up to 1.25 with the head room for it), drop (up to 3.5), climb (ladders). Doors
 * are read as they are in the world (the tour opens the ones a hand opens first). Used to propose the paths the real
 * player is then driven along, and to find rooms, stairs, head room and dark corners.
 */
public final class WalkModel {
	public static final int START = 0;
	public static final int WALK = 1;
	public static final int STAIR = 2;
	public static final int JUMP = 3;
	public static final int DROP = 4;
	public static final int UP = 5;
	public static final int DOWN = 6;
	public static final int EXIT = 7;
	public static final String[] EDGE = {"start", "walk", "stair", "jump", "drop", "climb up", "climb down", "ladder exit"};

	private static final double HALF_W = 0.3;
	private static final double H = 1.8;

	/** A place to stand. {@code stair}: the floor is the top of a bottom stair facing that way (ascending toward it). */
	public record Cell(int x, int y, int z, double floor, double clear, boolean climb, boolean door, @Nullable Direction stair) {
		public long key() {
			return BlockPos.asLong(this.x, this.y, this.z);
		}

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		@Override
		public String toString() {
			return this.x + "," + this.y + "," + this.z;
		}
	}

	public final ServerLevel level;
	public final Blueprint bp;
	public final BoundingBox bounds;
	public final BoundingBox region;
	private final Map<Long, Cell> cells = new HashMap<>();
	private final Set<Long> notCells = new HashSet<>();
	private final Map<Long, Long> parent = new HashMap<>();
	private final Map<Long, Integer> edge = new HashMap<>();
	private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
	public @Nullable Cell start;

	public WalkModel(ServerLevel level, Blueprint bp) {
		this.level = level;
		this.bp = bp;
		this.bounds = bp.bounds();
		this.region = new BoundingBox(this.bounds.minX() - 5, this.bounds.minY() - 4, this.bounds.minZ() - 5, this.bounds.maxX() + 5, this.bounds.maxY() + 3,
			this.bounds.maxZ() + 5);
	}

	// ---------------------------------------------------------------- cells

	public @Nullable Cell cell(int x, int y, int z) {
		long k = BlockPos.asLong(x, y, z);
		Cell c = this.cells.get(k);
		if (c != null) {
			return c;
		}
		if (this.notCells.contains(k)) {
			return null;
		}
		c = compute(x, y, z);
		if (c == null) {
			this.notCells.add(k);
		} else {
			this.cells.put(k, c);
		}
		return c;
	}

	private @Nullable Cell compute(int x, int y, int z) {
		double best = Double.NaN;
		Direction stair = null;
		for (int dy = -1; dy <= 0; dy++) {
			this.m.set(x, y + dy, z);
			BlockState st = this.level.getBlockState(this.m);
			if (st.isAir()) {
				continue;
			}
			VoxelShape sh = st.getCollisionShape(this.level, this.m);
			if (sh.isEmpty()) {
				continue;
			}
			for (AABB b : sh.toAabbs()) {
				if (b.maxX <= 0.5 - HALF_W || b.minX >= 0.5 + HALF_W || b.maxZ <= 0.5 - HALF_W || b.minZ >= 0.5 + HALF_W) {
					continue;
				}
				double top = y + dy + b.maxY;
				if (top >= y - 0.4 - 1e-6 && top < y + 0.6 - 1e-6 && (Double.isNaN(best) || top > best + 1e-6)) {
					best = top;
					stair = st.getBlock() instanceof StairBlock && st.getValue(StairBlock.HALF) == Half.BOTTOM && dy == -1 ? st.getValue(StairBlock.FACING) : null;
				}
			}
		}
		this.m.set(x, y, z);
		BlockState feet = this.level.getBlockState(this.m);
		boolean climb = feet.is(BlockTags.CLIMBABLE) && !feet.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
		if (Double.isNaN(best)) {
			if (!climb) {
				return null;
			}
			best = y;
		}
		if (!free(x + 0.5, best, z + 0.5, H)) {
			return null;
		}
		double clear = free(x + 0.5, best, z + 0.5, 2.5) ? 2.5 : free(x + 0.5, best, z + 0.5, 2.0) ? 2.0 : free(x + 0.5, best, z + 0.5, 1.9) ? 1.9 : 1.8;
		boolean door = feet.is(BlockTags.DOORS);
		return new Cell(x, y, z, best, clear, climb, door, stair);
	}

	/** Nothing in the way of a player standing with feet at (cx, f, cz), {@code h} tall. */
	private boolean free(double cx, double f, double cz, double h) {
		return this.level.noCollision(new AABB(cx - HALF_W, f + 1e-3, cz - HALF_W, cx + HALF_W, f + h, cz + HALF_W));
	}

	/** The box a player sweeps moving between the two cells' centres with feet at height f. */
	private boolean sweep(Cell a, Cell b, double f) {
		double x0 = Math.min(a.x, b.x) + 0.5 - HALF_W;
		double x1 = Math.max(a.x, b.x) + 0.5 + HALF_W;
		double z0 = Math.min(a.z, b.z) + 0.5 - HALF_W;
		double z1 = Math.max(a.z, b.z) + 0.5 + HALF_W;
		return this.level.noCollision(new AABB(x0, f + 1e-3, z0, x1, f + H, z1));
	}

	/** The move from a to the horizontally adjacent b, or -1 when a player cannot make it. */
	public int move(Cell a, Cell b, Direction dir) {
		double rise = b.floor - a.floor;
		if (Math.abs(rise) <= 0.6 + 1e-6) {
			return sweep(a, b, Math.max(a.floor, b.floor)) ? WALK : -1;
		}
		if (rise > 0) {
			boolean stairUp = rise <= 1.0 + 1e-6 && b.stair == dir;
			if (stairUp || a.climb && rise <= 1.1) {
				return sweep(a, b, b.floor) && free(a.x + 0.5, a.floor, a.z + 0.5, rise + H) ? (stairUp ? STAIR : EXIT) : -1;
			}
			if (rise <= 1.25) {
				// a jump: the head room over the take-off for the whole rise, then across at the top
				return free(a.x + 0.5, a.floor, a.z + 0.5, rise + H) && sweep(a, b, b.floor) ? JUMP : -1;
			}
			return -1;
		}
		boolean stairDown = rise >= -1.0 - 1e-6 && a.stair == dir.getOpposite();
		if (rise < -3.5) {
			return -1;
		}
		// across at the upper height, then down the target column
		return sweep(a, b, a.floor) && free(b.x + 0.5, b.floor, b.z + 0.5, a.floor - b.floor + H) ? (stairDown ? STAIR : DROP) : -1;
	}

	/** The cell a player ends in going one block in {@code dir} from {@code a}. */
	public @Nullable Cell step(Cell a, Direction dir, int[] kind) {
		int nx = a.x + dir.getStepX();
		int nz = a.z + dir.getStepZ();
		for (int dy : new int[]{0, 1, -1, -2, -3}) {
			Cell b = cell(nx, a.y + dy, nz);
			if (b == null) {
				continue;
			}
			int k = move(a, b, dir);
			if (k >= 0) {
				kind[0] = k;
				return b;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- search

	/** What a move costs the search: walking is cheapest, so a path jumps or drops only where it must. */
	private static double cost(int kind) {
		return switch (kind) {
			case JUMP -> 8;
			case DROP -> 3;
			case UP, DOWN, EXIT -> 1.5;
			default -> 1;
		};
	}

	/** Every cell reachable from {@code from} within the region, by the cheapest way (Dijkstra). */
	public void search(Cell from) {
		this.start = from;
		this.parent.clear();
		this.edge.clear();
		Map<Long, Double> dist = new HashMap<>();
		java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((u, v) -> Double.compare(u[0], v[0]));
		dist.put(from.key(), 0.0);
		this.parent.put(from.key(), Long.MIN_VALUE);
		this.edge.put(from.key(), START);
		q.add(new double[]{0, Double.longBitsToDouble(from.key())});
		int[] kind = new int[1];
		int visited = 0;
		while (!q.isEmpty() && visited++ < 600_000) {
			double[] top = q.poll();
			long ak = Double.doubleToRawLongBits(top[1]);
			if (top[0] > dist.getOrDefault(ak, Double.MAX_VALUE)) {
				continue;
			}
			Cell a = this.cells.get(ak);
			List<Cell> next = new ArrayList<>(6);
			List<Integer> kinds = new ArrayList<>(6);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				Cell b = step(a, d, kind);
				if (b != null && inRegion(b)) {
					next.add(b);
					kinds.add(kind[0]);
				}
			}
			if (a.climb) {
				Cell up = cell(a.x, a.y + 1, a.z);
				if (up != null && inRegion(up) && free(a.x + 0.5, a.floor, a.z + 0.5, up.floor - a.floor + H)) {
					next.add(up);
					kinds.add(UP);
				}
			}
			Cell down = cell(a.x, a.y - 1, a.z);
			if (down != null && down.climb && inRegion(down)
				&& (a.climb || this.level.getBlockState(this.m.set(a.x, a.y - 1, a.z)).getCollisionShape(this.level, this.m).isEmpty())) {
				next.add(down);
				kinds.add(DOWN);
			}
			for (int i = 0; i < next.size(); i++) {
				Cell b = next.get(i);
				double nd = top[0] + cost(kinds.get(i));
				if (nd < dist.getOrDefault(b.key(), Double.MAX_VALUE)) {
					dist.put(b.key(), nd);
					this.parent.put(b.key(), ak);
					this.edge.put(b.key(), kinds.get(i));
					q.add(new double[]{nd, Double.longBitsToDouble(b.key())});
				}
			}
		}
	}

	private boolean inRegion(Cell c) {
		return this.region.isInside(c.x, c.y, c.z);
	}

	public boolean reached(Cell c) {
		return this.parent.containsKey(c.key());
	}

	public boolean reached(long key) {
		return this.parent.containsKey(key);
	}

	public int edgeInto(Cell c) {
		return this.edge.getOrDefault(c.key(), -1);
	}

	public Set<Long> reachedKeys() {
		return this.parent.keySet();
	}

	public @Nullable Cell byKey(long k) {
		return this.cells.get(k);
	}

	/** The path from the start to a reached cell, start first, with the move into each cell. */
	public List<Step> path(Cell to) {
		List<Step> out = new ArrayList<>();
		Long k = to.key();
		while (k != null && k != Long.MIN_VALUE) {
			Cell c = this.cells.get(k);
			out.add(new Step(c, this.edge.getOrDefault(k, WALK)));
			k = this.parent.get(k);
		}
		java.util.Collections.reverse(out);
		return out;
	}

	public record Step(Cell cell, int edge) {
	}

	/** A standing cell in the column at (x, z) near height y (the highest within y - 4 .. y + 3), or null. */
	public @Nullable Cell standAt(int x, int y, int z) {
		for (int dy = 3; dy >= -4; dy--) {
			Cell c = cell(x, y + dy, z);
			if (c != null && !c.climb) {
				return c;
			}
		}
		return null;
	}

	/** The reached cell nearest (within one block across and one up or down) a position, or null. */
	public @Nullable Cell reachedNear(BlockPos p, int radius) {
		Cell best = null;
		int bd = Integer.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > radius) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					Cell c = this.cells.get(BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
					int d = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
					if (c != null && reached(c) && d < bd) {
						bd = d;
						best = c;
					}
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- what is round a cell

	/** Something with a body over the head within the building's height: a room or a porch, not the open street. */
	public boolean roofed(Cell c) {
		for (int y = c.y + 2; y <= this.bounds.maxY() + 1; y++) {
			this.m.set(c.x, y, c.z);
			BlockState st = this.level.getBlockState(this.m);
			if (!st.isAir() && !st.getCollisionShape(this.level, this.m).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The block light where the player's feet and head are (the brighter). */
	public int light(Cell c) {
		BlockPos feet = BlockPos.containing(c.x + 0.5, c.floor + 0.05, c.z + 0.5);
		return Math.max(this.level.getBrightness(LightLayer.BLOCK, feet), this.level.getBrightness(LightLayer.BLOCK, feet.above()));
	}

	public boolean inside(Cell c) {
		return this.bounds.isInside(c.x, c.y, c.z);
	}

	/** Rooms: roofed cells inside the building joined by level walking (no stairs, ladders or doorways between). */
	public List<List<Cell>> rooms() {
		List<Cell> all = new ArrayList<>();
		for (int y = this.bounds.minY(); y <= this.bounds.maxY(); y++) {
			for (int x = this.bounds.minX(); x <= this.bounds.maxX(); x++) {
				for (int z = this.bounds.minZ(); z <= this.bounds.maxZ(); z++) {
					Cell c = cell(x, y, z);
					if (c != null && !c.climb && !c.door && c.stair == null && roofed(c)) {
						all.add(c);
					}
				}
			}
		}
		Set<Long> in = new HashSet<>();
		for (Cell c : all) {
			in.add(c.key());
		}
		Set<Long> seen = new HashSet<>();
		List<List<Cell>> out = new ArrayList<>();
		for (Cell s : all) {
			if (!seen.add(s.key())) {
				continue;
			}
			List<Cell> room = new ArrayList<>();
			ArrayDeque<Cell> q = new ArrayDeque<>();
			q.add(s);
			while (!q.isEmpty()) {
				Cell a = q.poll();
				room.add(a);
				for (Direction d : Direction.Plane.HORIZONTAL) {
					for (int dy = -1; dy <= 1; dy++) {
						Cell b = this.cells.get(BlockPos.asLong(a.x + d.getStepX(), a.y + dy, a.z + d.getStepZ()));
						if (b != null && in.contains(b.key()) && !seen.contains(b.key()) && Math.abs(b.floor - a.floor) <= 0.6 + 1e-6
							&& sweep(a, b, Math.max(a.floor, b.floor))) {
							seen.add(b.key());
							q.add(b);
						}
					}
				}
			}
			out.add(room);
		}
		return out;
	}

	/** Whether a POI is reached: a reached cell at it, or beside it (a bed, a chest, a bench), within a storey. */
	public @Nullable Cell poiReached(Poi p) {
		Cell c = reachedNear(p.pos(), 0);
		if (c != null) {
			return c;
		}
		c = reachedNear(p.pos(), 1);
		if (c != null || p.type() != PoiType.BED) {
			return c;
		}
		// the foot of the bed
		BlockState st = this.level.getBlockState(p.pos());
		if (st.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
			Direction f = st.getValue(net.minecraft.world.level.block.BedBlock.FACING);
			return reachedNear(p.pos().relative(f.getOpposite()), 1);
		}
		return null;
	}
}
