package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Furnishing rooms so they look lived in and stay walkable. A {@link Room} is a rectangle of floor at a standing level
 * with the cells that must stay free (in front of doors, on and around stairs) and the cells folk must be able to reach;
 * {@link #furnish} lines its walls with what the room is for, keeps a walkway joining every door and every piece's
 * front, puts a table in the middle of rooms big enough for one, a rug in the open floor and light overhead.
 *
 * <p>Everything is placed against walls first; a piece is only kept if, with it in place, every door, stair and the
 * front of every piece placed so far can still be reached, so a room never ends up blocked.
 */
public final class Rooms {
	private Rooms() {
	}

	public enum Type {
		BEDROOM, KITCHEN, LIVING, WORKSHOP, STORAGE, HALL,
		/** A common room furnished to the walls: an inn's tap room, a tavern, a refectory ({@link #dining}). */
		DINING
	}

	/** A room's floor and what must stay free. Coordinates are local; the rectangle is the interior (inside the walls). */
	public static final class Room {
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		public final int y;
		/** Air rows above the floor (3 for an ordinary storey, 2 under a low ceiling). */
		public final int height;
		private final boolean[] blocked;
		private final boolean[] reserved;
		/** Cells with something overhead (the underside of a stair): furniture fits, nobody walks there. */
		private final boolean[] low;
		/** Cells a ladder climbs through (its foot, or the hole at its head): nobody stands there, they are reached from beside. */
		private final boolean[] climb;
		private final List<int[]> reach = new ArrayList<>();

		public Room(int x0, int z0, int x1, int z1, int y, int height) {
			this.x0 = Math.min(x0, x1);
			this.z0 = Math.min(z0, z1);
			this.x1 = Math.max(x0, x1);
			this.z1 = Math.max(z0, z1);
			this.y = y;
			this.height = height;
			int n = (this.x1 - this.x0 + 1) * (this.z1 - this.z0 + 1);
			this.blocked = new boolean[n];
			this.reserved = new boolean[n];
			this.low = new boolean[n];
			this.climb = new boolean[n];
		}

		public boolean inside(int x, int z) {
			return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
		}

		private int idx(int x, int z) {
			return (z - this.z0) * (this.x1 - this.x0 + 1) + (x - this.x0);
		}

		/** Keeps a cell free and reachable (a door's inside cell, a stair foot). */
		public Room keep(int x, int z) {
			if (inside(x, z)) {
				this.reserved[idx(x, z)] = true;
				this.reach.add(new int[]{x, z});
			}
			return this;
		}

		/** Keeps a rectangle free (a stair's run and its headroom). */
		public Room keepArea(int ax, int az, int bx, int bz) {
			for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
				for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
					if (inside(x, z)) {
						this.reserved[idx(x, z)] = true;
					}
				}
			}
			return this;
		}

		/**
		 * Claims one free cell for something the caller builds itself (a hearth), if the room stays connected; the
		 * cell is then taken. Answers whether it was.
		 */
		public boolean claim(int x, int z) {
			return tryTake(this, new int[][]{{x, z}}, null);
		}

		/** Marks a cell as taken by something already built (a chimney breast, a post). */
		public Room block(int x, int z) {
			if (inside(x, z)) {
				this.blocked[idx(x, z)] = true;
			}
			return this;
		}

		boolean free(int x, int z) {
			return inside(x, z) && !this.blocked[idx(x, z)] && !this.reserved[idx(x, z)];
		}

		boolean open(int x, int z) {
			return inside(x, z) && !this.blocked[idx(x, z)] && !this.low[idx(x, z)];
		}

		public int width() {
			return this.x1 - this.x0 + 1;
		}

		public int length() {
			return this.z1 - this.z0 + 1;
		}

		/**
		 * Whether every cell that must be reached still can be, from the first one. A kept cell nobody stands in (the
		 * foot or the head of a ladder, taken by the ladder or the hole it climbs through) is reached from beside it.
		 */
		boolean connected() {
			if (this.reach.isEmpty()) {
				return true;
			}
			boolean[] seen = new boolean[this.blocked.length];
			ArrayDeque<int[]> q = new ArrayDeque<>();
			int[] s = null;
			for (int[] c : this.reach) {
				if (open(c[0], c[1])) {
					s = c;
					break;
				}
				if (!inside(c[0], c[1]) || !this.climb[idx(c[0], c[1])]) {
					return false;
				}
			}
			if (s == null) {
				return false;
			}
			q.add(s);
			seen[idx(s[0], s[1])] = true;
			while (!q.isEmpty()) {
				int[] c = q.poll();
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = c[0] + Dirs.dx(dir);
					int nz = c[1] + Dirs.dz(dir);
					if (open(nx, nz) && !seen[idx(nx, nz)]) {
						seen[idx(nx, nz)] = true;
						q.add(new int[]{nx, nz});
					}
				}
			}
			for (int[] r : this.reach) {
				if (!inside(r[0], r[1])) {
					return false;
				}
				if (seen[idx(r[0], r[1])]) {
					continue;
				}
				boolean beside = false;
				if (this.blocked[idx(r[0], r[1])] && this.climb[idx(r[0], r[1])]) {
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = r[0] + Dirs.dx(dir);
						int nz = r[1] + Dirs.dz(dir);
						beside |= inside(nx, nz) && seen[idx(nx, nz)];
					}
				}
				if (!beside) {
					return false;
				}
			}
			return true;
		}
	}

	/** A wall slot: a free cell against a wall, and the direction into the room (away from that wall). */
	private record Slot(int x, int z, Direction into, boolean underWindow) {
	}

	private static List<Slot> slots(Draft d, Room r) {
		List<Slot> out = new ArrayList<>();
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				if (!r.free(x, z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int wx = x + Dirs.dx(dir);
					int wz = z + Dirs.dz(dir);
					if (r.inside(wx, wz)) {
						continue;
					}
					BlockState wall = d.get(wx, r.y, wz);
					if (wall == null || wall.isAir() || !Openings.isWallBlock(d, wx, r.y, wz)) {
						continue;
					}
					BlockState above = d.get(wx, r.y + 1, wz);
					boolean window = above != null && !Openings.isWallBlock(d, wx, r.y + 1, wz);
					out.add(new Slot(x, z, dir.getOpposite(), window));
				}
			}
		}
		long seed = d.seed();
		out.sort(Comparator.comparingDouble(s -> Noise.unit(seed ^ 0x51077, s.x, r.y, s.z)));
		return out;
	}

	/** Tries a placement: blocks cells, keeps the front reachable; undoes and answers false if the room would be cut. */
	private static boolean tryTake(Room r, int[][] cells, int[] front) {
		for (int[] c : cells) {
			if (!r.free(c[0], c[1])) {
				return false;
			}
		}
		if (front != null && !r.open(front[0], front[1])) {
			return false;
		}
		for (int[] c : cells) {
			r.blocked[r.idx(c[0], c[1])] = true;
		}
		if (front != null) {
			r.reach.add(front);
		}
		if (!r.connected()) {
			for (int[] c : cells) {
				r.blocked[r.idx(c[0], c[1])] = false;
			}
			if (front != null) {
				r.reach.remove(r.reach.size() - 1);
			}
			return false;
		}
		if (front != null) {
			r.reserved[r.idx(front[0], front[1])] = true;
		}
		return true;
	}

	/**
	 * Furnishes a room for its purpose.
	 *
	 * @param beds how many beds a bedroom should hold (others ignore it)
	 * @param job  a workshop's trade
	 */
	public static void furnish(Draft d, Room r, Type type, int beds, JobType job) {
		// whatever is already built on the floor (a chimney breast, stairs, posts) is taken; a hole in the floor (a
		// stairwell) cannot be stood on; a stair's top step can be walked but not furnished
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				BlockState s = d.get(x, r.y, z);
				BlockState below = d.get(x, r.y - 1, z);
				boolean hole = below != null ? below.isAir() : r.y - 1 >= d.site().ground(x, z);
				BlockState over = d.get(x, r.y + 1, z);
				if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock
					|| hole && (ladder(d.get(x, r.y - 1, z)) || ladder(d.get(x, r.y - 2, z)))) {
					r.climb[r.idx(x, z)] = true;
				}
				boolean through = s != null && (s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock
					|| s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty());
				if (s != null && !s.isAir() && through && !hole) {
					// a ladder, a carpet: walked through, never furnished
					r.keepArea(x, z, x, z);
				} else if (s != null && !s.isAir() || hole) {
					r.block(x, z);
				} else if (below != null && !d.isSolid(x, r.y - 1, z)) {
					r.keepArea(x, z, x, z);
				} else if (over != null && !over.isAir()
					&& !over.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()) {
					r.low[r.idx(x, z)] = true;
				}
			}
		}
		List<Slot> slots = slots(d, r);
		switch (type) {
			case BEDROOM -> {
				int placed = 0;
				for (Slot s : slots) {
					if (placed >= beds) {
						break;
					}
					int fx = s.x + Dirs.dx(s.into);
					int fz = s.z + Dirs.dz(s.into);
					Direction side = Dirs.right(s.into);
					int[] front = {fx + Dirs.dx(side), fz + Dirs.dz(side)};
					if (!r.free(front[0], front[1])) {
						front = new int[]{fx - Dirs.dx(side), fz - Dirs.dz(side)};
					}
					if (tryTake(r, new int[][]{{s.x, s.z}, {fx, fz}}, front)) {
						Furniture.bed(d, s.x, r.y, s.z, s.into);
						placed++;
						// a chest or a bedside table at the bed's head
						int cx = s.x - Dirs.dx(side);
						int cz = s.z - Dirs.dz(side);
						if (tryTake(r, new int[][]{{cx, cz}}, new int[]{cx + Dirs.dx(s.into), cz + Dirs.dz(s.into)})) {
							if (Noise.unit(d.seed(), cx, r.y, cz) < 0.5) {
								Furniture.chest(d, cx, r.y, cz, s.into, List.of(new Object[]{"minecraft:white_wool", 2}, new Object[]{"minecraft:book", 1}));
							} else {
								Furniture.barrel(d, cx, r.y, cz, s.into);
								d.set(cx, r.y + 1, cz, Blocks.CANDLE.defaultBlockState().setValue(
									net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
							}
						}
					}
				}
				wallPieces(d, r, slots, new String[]{"wardrobe", "shelf", "pot"});
			}
			case KITCHEN -> {
				// a counter along the longest free wall run
				wallPieces(d, r, slots, new String[]{"counter", "barrel", "shelf_kitchen", "barrel", "chest"});
				centreTable(d, r);
			}
			case LIVING -> {
				wallPieces(d, r, slots, new String[]{"bookshelf", "armchair", "shelf", "pot", "bookshelf", "chest"});
				centreTable(d, r);
			}
			case WORKSHOP -> {
				wallPieces(d, r, slots, new String[]{"work", "chest", "work2", "barrel", "shelf_tools", "barrel"}, job);
			}
			case STORAGE -> {
				wallPieces(d, r, slots, new String[]{"chest", "barrel", "chest", "hay", "barrel", "barrel", "chest"});
			}
			case HALL -> wallPieces(d, r, slots, new String[]{"pot", "shelf"});
			case DINING -> dining(d, r, slots);
		}
		light(d, r);
		if (type == Type.BEDROOM || type == Type.LIVING) {
			rugInOpen(d, r);
		}
	}

	private static boolean ladder(BlockState s) {
		return s != null && s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock;
	}

	private static void wallPieces(Draft d, Room r, List<Slot> slots, String[] wants) {
		wallPieces(d, r, slots, wants, JobType.NONE);
	}

	private static void wallPieces(Draft d, Room r, List<Slot> slots, String[] wants, JobType job) {
		int w = 0;
		for (Slot s : slots) {
			if (w >= wants.length) {
				break;
			}
			if (!r.free(s.x, s.z)) {
				continue;
			}
			String want = wants[w];
			boolean tall = want.equals("wardrobe") || want.equals("bookshelf");
			if (tall && s.underWindow) {
				continue;
			}
			int[] front = {s.x + Dirs.dx(s.into), s.z + Dirs.dz(s.into)};
			if (!tryTake(r, new int[][]{{s.x, s.z}}, front)) {
				continue;
			}
			int x = s.x;
			int z = s.z;
			int y = r.y;
			switch (want) {
				case "counter" -> Furniture.counter(d, x, y, z, s.into, Dirs.right(s.into), 1);
				case "barrel" -> Furniture.barrel(d, x, y, z, Direction.UP);
				case "chest" -> Furniture.chest(d, x, y, z, s.into, List.of(new Object[]{"minecraft:bread", 3}, new Object[]{"minecraft:coal", 4}));
				case "hay" -> d.inStage(dev.voidmc.hearth.api.StageKind.INTERIOR, () -> d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState()));
				case "wardrobe" -> {
					Furniture.barrel(d, x, y, z, s.into);
					if (r.height >= 3) {
						Furniture.barrel(d, x, y + 1, z, s.into);
					}
				}
				case "bookshelf" -> {
					Furniture.bookshelf(d, x, y, z);
					if (r.height >= 3) {
						Furniture.bookshelf(d, x, y + 1, z);
					}
				}
				case "armchair", "settle" -> Furniture.chair(d, x, y, z, s.into.getOpposite());
				case "shelf", "shelf_kitchen", "shelf_tools" -> {
					Furniture.barrel(d, x, y, z, Direction.UP);
					if (!s.underWindow && r.height >= 3) {
						Furniture.shelf(d, x, y + 1, z, s.into, want.equals("shelf_kitchen") ? "kitchen" : want.equals("shelf_tools") ? "tools" : "study");
					}
				}
				case "pot" -> {
					Furniture.table(d, x, y, z);
				}
				case "work", "work2" -> Furniture.workstation(d, want.equals("work") ? job : secondStation(job), x, y, z, s.into);
				default -> {
				}
			}
			w++;
		}
	}

	private static JobType secondStation(JobType job) {
		return switch (job) {
			case BUILDER -> JobType.MASON;
			case SMITH -> JobType.GUARD;
			default -> job;
		};
	}

	/**
	 * A common room furnished to the walls, as a busy inn's is: booths (a table between two benches) standing out from
	 * both long walls, back to back down the room; a long table with benches down the middle where the booths leave a
	 * band five wide; settles and side tables along what wall is left. Every piece is tried against the walkway, so the
	 * doors, the bar's front, the hearth and every table's end stay joined.
	 */
	private static void dining(Draft d, Room r, List<Slot> slots) {
		boolean alongX = r.width() >= r.length();
		int along = alongX ? r.width() : r.length();
		int across = alongX ? r.length() : r.width();
		// booths reach in from both long walls, leaving an aisle (two wide in a wide room)
		int aisle = across >= 8 ? 2 : 1;
		int reach = Math.min(3, (across - aisle) / 2);
		int booths = 0;
		if (reach >= 2) {
			for (int side = 0; side < 2; side++) {
				for (int t = 0; t + 2 < along; ) {
					boolean placed = booth(d, r, alongX, across, side, t, reach);
					booths += placed ? 1 : 0;
					t += placed ? 3 : 1;
				}
			}
		}
		// a long table down the middle, in lengths of up to four with a way through between them
		int band = across - (booths > 0 ? 2 * reach : 0);
		if (band >= 5 || booths == 0 && across >= 5) {
			int mid = across / 2;
			Direction run = alongX ? Dirs.RIGHT : Dirs.BACK;
			for (int u = 1; u + 1 < along - 1; ) {
				boolean done = false;
				for (int len = Math.min(4, along - 1 - u); len >= 2 && !done; len--) {
					int[][] cells = new int[len * 3][];
					for (int k = 0; k < len; k++) {
						for (int j = 0; j < 3; j++) {
							cells[k * 3 + j] = cell(r, alongX, u + k, mid - 1 + j);
						}
					}
					if (tryTake(r, cells, cell(r, alongX, u + len, mid))) {
						int[] c0 = cell(r, alongX, u, mid);
						Furniture.longTable(d, c0[0], r.y, c0[1], run, len);
						for (int k = 0; k < len; k++) {
							int[] a = cell(r, alongX, u + k, mid - 1);
							int[] b = cell(r, alongX, u + k, mid + 1);
							Furniture.chair(d, a[0], r.y, a[1], toward(!alongX, -1));
							Furniture.chair(d, b[0], r.y, b[1], toward(!alongX, 1));
						}
						u += len + 1;
						done = true;
					}
				}
				if (!done) {
					u++;
				}
			}
		}
		// what floor is left: tables backed against a wall, a stair or the bar, a bench before each
		benchRows(d, r);
		// settles and side tables along the walls the booths left
		wallPieces(d, r, slots, new String[]{"settle", "pot", "settle", "settle", "barrel", "settle", "pot", "settle", "settle", "pot"});
	}

	/**
	 * Runs of tables (up to four long) backed against whatever bounds the free floor (a wall, the bar, the underside of
	 * a stair), each with a bench before it on the open side and the walkway beyond the bench: how a narrow strip of
	 * floor beside a bar still seats a row of drinkers.
	 */
	private static void benchRows(Draft d, Room r) {
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				for (Direction dir : Dirs.HORIZONTAL) {
					if (!r.free(x, z) || r.free(x - Dirs.dx(dir), z - Dirs.dz(dir))) {
						continue;
					}
					Direction side = Dirs.right(dir);
					int len = 0;
					while (len < 4) {
						int tx = x + Dirs.dx(side) * len;
						int tz = z + Dirs.dz(side) * len;
						int bx = tx + Dirs.dx(dir);
						int bz = tz + Dirs.dz(dir);
						// the table backs onto something, the bench before it, the walkway before the bench
						if (r.free(tx - Dirs.dx(dir), tz - Dirs.dz(dir))
							|| !tryTake(r, new int[][]{{tx, tz}, {bx, bz}}, new int[]{bx + Dirs.dx(dir), bz + Dirs.dz(dir)})) {
							break;
						}
						len++;
					}
					if (len == 0) {
						continue;
					}
					if (len >= 2) {
						Furniture.longTable(d, x, r.y, z, side, len);
					} else {
						Furniture.table(d, x, r.y, z);
					}
					for (int k = 0; k < len; k++) {
						Furniture.chair(d, x + Dirs.dx(side) * k + Dirs.dx(dir), r.y, z + Dirs.dz(side) * k + Dirs.dz(dir), dir);
					}
				}
			}
		}
	}

	/**
	 * One booth against a long wall at position {@code t} along it: a table run from the wall into the room between two
	 * benches, {@code reach} deep (or shorter where that does not fit). Side 0 is the wall before the first row across,
	 * side 1 the wall after the last. Answers whether it was placed.
	 */
	private static boolean booth(Draft d, Room r, boolean alongX, int across, int side, int t, int reach) {
		int[] wallCell = cell(r, alongX, t + 1, side == 0 ? -1 : across);
		if (!Openings.isWallBlock(d, wallCell[0], r.y, wallCell[1])) {
			return false;
		}
		for (int len = reach; len >= 2; len--) {
			int[][] cells = new int[len * 3][];
			for (int k = 0; k < len; k++) {
				int v = side == 0 ? k : across - 1 - k;
				for (int j = 0; j < 3; j++) {
					cells[k * 3 + j] = cell(r, alongX, t + j, v);
				}
			}
			int[] front = cell(r, alongX, t + 1, side == 0 ? len : across - 1 - len);
			if (!tryTake(r, cells, front)) {
				continue;
			}
			int[] c0 = cell(r, alongX, t + 1, side == 0 ? 0 : across - 1);
			Furniture.longTable(d, c0[0], r.y, c0[1], toward(!alongX, side == 0 ? 1 : -1), len);
			for (int k = 0; k < len; k++) {
				int v = side == 0 ? k : across - 1 - k;
				int[] a = cell(r, alongX, t, v);
				int[] b = cell(r, alongX, t + 2, v);
				Furniture.chair(d, a[0], r.y, a[1], toward(alongX, -1));
				Furniture.chair(d, b[0], r.y, b[1], toward(alongX, 1));
			}
			return true;
		}
		return false;
	}

	/** The local cell at (u along the room's long side, v across it). */
	private static int[] cell(Room r, boolean alongX, int u, int v) {
		return alongX ? new int[]{r.x0 + u, r.z0 + v} : new int[]{r.x0 + v, r.z0 + u};
	}

	/** The direction in which x grows ({@code inX}) or z grows, signed. */
	private static Direction toward(boolean inX, int sign) {
		return inX ? (sign > 0 ? Dirs.RIGHT : Dirs.LEFT) : (sign > 0 ? Dirs.BACK : Dirs.FRONT);
	}

	/** A table with chairs in the middle of rooms at least 4 × 4 inside. */
	private static void centreTable(Draft d, Room r) {
		if (r.width() < 4 || r.length() < 4) {
			return;
		}
		int cx = (r.x0 + r.x1) / 2;
		int cz = (r.z0 + r.z1) / 2;
		if (!tryTake(r, new int[][]{{cx, cz}}, null)) {
			return;
		}
		Furniture.table(d, cx, r.y, cz);
		for (Direction dir : Dirs.HORIZONTAL) {
			int sx = cx + Dirs.dx(dir);
			int sz = cz + Dirs.dz(dir);
			if (Noise.unit(d.seed(), sx, r.y, sz) < 0.7 && tryTake(r, new int[][]{{sx, sz}}, null)) {
				Furniture.chair(d, sx, r.y, sz, dir);
			}
		}
	}

	/** Light: lanterns hanging from the ceiling in rooms tall enough, wall torches in low ones. */
	private static void light(Draft d, Room r) {
		int ceiling = r.y + r.height;
		int n = Math.max(1, (r.width() * r.length()) / 20);
		for (int i = 0; i < n; i++) {
			int x = r.x0 + (int) ((i + 0.5) * r.width() / n);
			int z = (r.z0 + r.z1) / 2;
			if (r.width() < r.length()) {
				x = (r.x0 + r.x1) / 2;
				z = r.z0 + (int) ((i + 0.5) * r.length() / n);
			}
			if (r.height >= 3 && d.isSolid(x, ceiling, z) && d.isEmpty(x, ceiling - 1, z)) {
				Furniture.hangingLantern(d, x, ceiling, z, 0);
			} else {
				for (Direction dir : Dirs.HORIZONTAL) {
					int wx = x + Dirs.dx(dir);
					int wz = z + Dirs.dz(dir);
					if (!r.inside(wx, wz) && d.isSolid(wx, r.y + 1, wz) && d.isEmpty(x, r.y + 1, z)) {
						Furniture.wallTorch(d, x, r.y + 1, z, dir.getOpposite());
						break;
					}
				}
			}
		}
	}

	/** A rug over the largest open rectangle in the middle of the floor. */
	private static void rugInOpen(Draft d, Room r) {
		int ax = r.x0 + 1;
		int bx = r.x1 - 1;
		int az = r.z0 + 1;
		int bz = r.z1 - 1;
		if (bx - ax < 1 || bz - az < 1) {
			return;
		}
		for (int z = az; z <= bz; z++) {
			for (int x = ax; x <= bx; x++) {
				if (!r.open(x, z) || !d.isEmpty(x, r.y, z) || !d.isSolid(x, r.y - 1, z)) {
					return;
				}
			}
		}
		Furniture.rug(d, ax, az, bx, bz, r.y);
	}
}
