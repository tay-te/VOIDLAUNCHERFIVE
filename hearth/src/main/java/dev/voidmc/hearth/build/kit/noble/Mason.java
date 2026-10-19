package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The mason's small work shared by towers, curtains, gates and halls: parapets cut in the culture's merlons, corbels and
 * machicolations, a battered talus at a wall's foot, arrow loops, footings to the ground, and stone spiral stairs.
 * Everything takes local coordinates and the seat's {@link Dress}.
 */
public final class Mason {
	private Mason() {
	}

	/** Whether (x, z) is inside the plot. */
	public static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** Whether a position may be drawn (in the plot, or the street row high enough for an overhang). */
	public static boolean ok(Draft d, int x, int y, int z) {
		return d.allowed(x, y, z);
	}

	/** The lowest natural ground under the cells. */
	public static int lowest(Draft d, List<int[]> cells) {
		int lo = Integer.MAX_VALUE;
		for (int[] c : cells) {
			lo = Math.min(lo, d.site().ground(c[0], c[1]));
		}
		return lo == Integer.MAX_VALUE ? 0 : lo;
	}

	/** The highest natural ground under the cells. */
	public static int highest(Draft d, List<int[]> cells) {
		int hi = Integer.MIN_VALUE;
		for (int[] c : cells) {
			hi = Math.max(hi, d.site().ground(c[0], c[1]));
		}
		return hi == Integer.MIN_VALUE ? 0 : hi;
	}

	/**
	 * A footing under a cell: the footing stone from two below the ground up to {@code topY} (inclusive), stage
	 * FOUNDATION. Where the ground stands above {@code topY} nothing is laid (the floor is cut into it).
	 */
	public static void footing(Draft d, Dress m, int x, int z, int topY) {
		int g = d.site().ground(x, z);
		int w = d.site().water(x, z);
		int from = Math.min(g, topY) - 2;
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = from; y <= topY; y++) {
				d.set(x, y, z, m.footing.full());
			}
		}
		if (w != dev.voidmc.hearth.build.terrain.Site.NONE && w > g) {
			d.drain(x, z);
		}
	}

	/**
	 * A merlon (or the culture's equivalent) standing at (x, y, z) on a parapet, {@code along} the parapet's run and
	 * {@code out} its field side; {@code index} counts cells along the parapet (the rhythm of merlons and crenels).
	 */
	public static void merlon(Draft d, Dress m, Manner.Merlon kind, int x, int y, int z, int index, Direction along, Direction out, boolean corner) {
		if (!ok(d, x, y, z)) {
			return;
		}
		// every crenel has a sill a half block over the parapet, so its opening stands chest high: nobody steps up into it
		// and over onto the drip course or the field below
		switch (kind) {
			case SQUARE -> {
				if (corner || (index & 1) == 0) {
					d.set(x, y, z, m.wall.full());
					if (ok(d, x, y + 1, z) && d.tier() >= 3 && m.dress.has(Form.SLAB) && corner) {
						d.set(x, y + 1, z, m.dress.slab(SlabType.BOTTOM));
					}
				} else {
					sill(d, m, x, y, z);
				}
			}
			case SWALLOW -> {
				int k = Math.floorMod(index, 3);
				if (corner) {
					d.set(x, y, z, m.wall.full());
				} else if (k != 2) {
					d.set(x, y, z, m.wall.full());
					if (ok(d, x, y + 1, z) && m.wall.has(Form.STAIRS)) {
						d.set(x, y + 1, z, m.wall.stairs(k == 0 ? along.getOpposite() : along, Half.BOTTOM));
					}
				} else {
					sill(d, m, x, y, z);
				}
			}
			case POINTED -> {
				if (corner || (index & 1) == 0) {
					d.set(x, y, z, m.wall.full());
					if (ok(d, x, y + 1, z)) {
						d.set(x, y + 1, z, m.wall.has(Form.WALL) ? m.wall.wall() : m.dress.wallOrFull());
					}
				} else {
					sill(d, m, x, y, z);
				}
			}
			case PLAIN -> {
				if (m.wall.has(Form.SLAB) && !corner) {
					d.set(x, y, z, m.wall.slab(SlabType.BOTTOM));
				} else {
					d.set(x, y, z, m.wall.full());
				}
			}
			case TIMBER -> {
				Paint post = m.frame.log(Direction.Axis.Y);
				if (corner || Math.floorMod(index, 3) == 0) {
					d.set(x, y, z, post);
					if (ok(d, x, y + 1, z) && m.frame.has(Form.FENCE)) {
						d.set(x, y + 1, z, m.frame.fence());
					}
				} else {
					d.set(x, y, z, m.frame.has(Form.FENCE) ? m.frame.fence() : post);
				}
			}
		}
	}

	/**
	 * A crenel's sill: the parapet course under the opening (at y - 1) laid as a wall of the walling (or the dressing),
	 * which stands half a block higher than a block and cannot be stood on, so the crenel's opening is chest high to
	 * the walk; where neither cuts a wall, a bottom slab in the opening on the parapet instead.
	 */
	private static void sill(Draft d, Dress m, int x, int y, int z) {
		if (!d.isEmpty(x, y, z) || !d.isSolid(x, y - 1, z)) {
			return;
		}
		if (m.wall.has(Form.WALL) || m.dress.has(Form.WALL)) {
			d.set(x, y - 1, z, m.wall.has(Form.WALL) ? m.wall.wall() : m.dress.wall());
		} else if (m.wall.has(Form.SLAB) || m.dress.has(Form.SLAB)) {
			d.set(x, y, z, m.wall.has(Form.SLAB) ? m.wall.slab(SlabType.BOTTOM) : m.dress.slab(SlabType.BOTTOM));
		}
	}

	/**
	 * A corbel: an upside-down stair at (x, y, z) whose back is against the wall toward {@code toWall} (a bracket under an
	 * oversailing parapet, a turret, a hoarding).
	 */
	public static void corbel(Draft d, Stone st, int x, int y, int z, Direction toWall) {
		if (ok(d, x, y, z) && d.isEmpty(x, y, z) && !ledge(d, x, y, z, toWall)) {
			d.set(x, y, z, st.stairsOrSlab(toWall, Half.TOP));
		}
	}

	/**
	 * A battered talus at a wall's foot: a sloping stair course at (x, ground, z), outside the wall toward {@code out},
	 * where the ground there lies at or below {@code maxY} (a wall standing on a hillside flares at its foot).
	 */
	public static void talus(Draft d, Dress m, int x, int z, Direction out, int maxY) {
		if (!inPlot(d, x, z)) {
			return;
		}
		int g = d.site().ground(x, z);
		// (not on made ground: a court paved over the cell is walked, and a stair course on it is a step to trip on)
		if (g > maxY || d.site().isWater(x, z) || !d.isAbsent(x, g, z) || !d.isAbsent(x, g - 1, z)) {
			return;
		}
		try (var s = d.stage(StageKind.FOUNDATION)) {
			d.set(x, g - 1, z, m.footing.full());
			d.set(x, g, z, m.footing.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
		}
	}

	/** An arrow loop through a wall cell: air one or two high (the opening a defender shoots from). */
	public static void loop(Draft d, int x, int y, int z, int height) {
		for (int k = 0; k < height; k++) {
			if (ok(d, x, y + k, z)) {
				d.set(x, y + k, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/**
	 * A spiral stair of stone slabs round a newel in the 3 × 3 square centred on (cx, cz), from standing level y0 up
	 * {@code rise}: each ring cell half a block above the last, so a turn climbs four. The headroom over every tread is
	 * cleared; floors the stair passes must leave its square open. Timber cultures climb wooden treads round a post.
	 */
	public static void spiral(Draft d, Dress m, int cx, int cz, int y0, int rise, int startAt, boolean timber) {
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		BlockState air = Blocks.AIR.defaultBlockState();
		Stone treads = m.paving.has(Form.SLAB) ? m.paving : m.dress;
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int y = y0 - 1; y < y0 + rise; y++) {
				d.set(cx, y, cz, timber ? m.frame.log(Direction.Axis.Y) : m.dress.full());
			}
			int steps = rise * 2;
			for (int k = 1; k <= steps; k++) {
				int[] c = ring[(startAt + k) % 8];
				int px = cx + c[0];
				int pz = cz + c[1];
				int y = y0 + (k - 1) / 2;
				boolean top = (k & 1) == 0;
				SlabType t = top ? SlabType.TOP : SlabType.BOTTOM;
				d.set(px, y, pz, timber ? m.furniture.slab(t) : treads.has(Form.SLAB) ? treads.slab(t) : m.furniture.slab(t));
				// headroom: two over the tread, and a third so it can be stepped up from
				for (int h = 1; h <= 3; h++) {
					d.set(px, y + h, pz, air);
				}
			}
		}
	}

	/** The ring cell the spiral of {@link #spiral} arrives on at its top (standing level y0 + rise). */
	public static int[] spiralTop(int cx, int cz, int rise, int startAt) {
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		int[] c = ring[(startAt + rise * 2) % 8];
		return new int[]{cx + c[0], cz + c[1]};
	}

	/**
	 * Whether a cell of the spiral's square needs air at layer {@code y} (the headroom of a tread below): floors and decks
	 * the stair passes through leave these cells open.
	 */
	public static boolean spiralNeedsAir(int cx, int cz, int y0, int rise, int startAt, int x, int y, int z) {
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		int steps = rise * 2;
		for (int k = 1; k <= steps; k++) {
			int[] c = ring[(startAt + k) % 8];
			if (cx + c[0] != x || cz + c[1] != z) {
				continue;
			}
			int ty = y0 + (k - 1) / 2;
			if (y == ty) {
				return true;
			}
			if (y > ty && y <= ty + 3) {
				return true;
			}
		}
		return false;
	}

	/** A lantern on a chain from the block at {@code ceilingY} (exclusive) down to {@code lanternY}, in the culture's iron. */
	public static void chandelier(Draft d, int x, int z, int ceilingY, int lanternY) {
		dev.voidmc.hearth.build.kit.light.Lighting.chandelier(d, x, z, ceilingY, lanternY);
	}

	/** A block of the wall stone or, every so often by noise, the dressing (patched masonry on old walls). */
	public static Paint patched(Dress m, long seed) {
		return (x, y, z) -> Noise.unit(seed, x, y, z) < 0.06 ? m.dress.full().at(x, y, z) : m.wall.full().at(x, y, z);
	}

	/** An upside-down stair of the dressing under a projecting cell (a sill, a cornice), facing back into the wall. */
	public static void sill(Draft d, Dress m, int x, int y, int z, Direction toWall) {
		// never at head height over ground folk walk along the wall on
		boolean overPlaced = d.isSolid(x, y - 2, z) && d.isEmpty(x, y - 1, z);
		boolean overGround = d.isAbsent(x, y - 2, z) && d.isAbsent(x, y - 1, z) && d.site().ground(x, z) == y - 1;
		if (overPlaced || overGround) {
			return;
		}
		if (ok(d, x, y, z) && d.isEmpty(x, y, z) && !ledge(d, x, y, z, toWall) && !lowOver(d, x, y, z)) {
			d.set(x, y, z, m.dress.stairsOrSlab(toWall, Half.TOP));
		}
	}

	/**
	 * Whether a course block (a sill, a corbel, a string course: an upside-down stair at y against the wall toward
	 * {@code toWall}) would join a floor folk stand on beside it, flush or a step down from it (a walk, a deck, a tower's
	 * landing): then it is a ledge to walk out on along the wall's face, and is left out.
	 */
	/**
	 * Whether a block at y would hang within three rows over a floor or the ground beneath it: too low to clear the head
	 * of someone standing or stepping up there.
	 */
	public static boolean lowOver(Draft d, int x, int y, int z) {
		int g = d.site().ground(x, z);
		for (int yy = y - 1; yy >= y - 4; yy--) {
			boolean solid = d.isAbsent(x, yy, z) ? yy < g : d.isSolid(x, yy, z);
			if (solid) {
				return y - (yy + 1) <= 3;
			}
		}
		return false;
	}

	/** Whether a block is a course block: an upside-down stair or a top slab, hung on a wall's face. */
	public static boolean course(@org.jspecify.annotations.Nullable BlockState st) {
		return st != null && (st.getBlock() instanceof StairBlock && st.getValue(StairBlock.HALF) == Half.TOP
			|| st.getBlock() instanceof SlabBlock && st.getValue(SlabBlock.TYPE) == SlabType.TOP);
	}

	public static boolean ledge(Draft d, int x, int y, int z, Direction toWall) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dir == toWall) {
				continue;
			}
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			for (int top = y; top <= y + 1; top++) {
				BlockState st = d.get(nx, top, nz);
				if (st == null || !d.isSolid(nx, top, nz) || !d.isEmpty(nx, top + 1, nz) || !d.isEmpty(nx, top + 2, nz)) {
					continue;
				}
				// another course block beside it is the course running on, not a floor
				if (!course(st)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A vertical log of a mat, or its full block where it has no logs. */
	public static Paint post(Mat m) {
		return m.has(Form.LOG) ? m.log(Direction.Axis.Y) : m.full();
	}

	/** A horizontal log along an axis, or the full block. */
	public static Paint beam(Mat m, Direction.Axis axis) {
		return m.has(Form.LOG) ? m.log(axis) : m.full();
	}

	/** The wall banner block of a dye, or null. */
	public static Block wallBanner(Dress m, int salt) {
		var entries = m.cloth.palette().entries();
		return entries.get(Math.floorMod(salt, entries.size())).material().block(Form.WALL_BANNER);
	}

	/**
	 * Closes a steep rasterised roof: under every column whose surface stands more than a block over its lowest
	 * neighbour, full blocks of the roofing down to that neighbour's surface (a cone's or a spire's point would otherwise
	 * float over the ring below it).
	 */
	public static void solidify(Draft d, dev.voidmc.hearth.build.kit.Roofs.Roof r, Mat roof) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					int top = r.surfaceY(x, z);
					int low = top;
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (r.covers(nx, nz)) {
							low = Math.min(low, r.surfaceY(nx, nz));
						}
					}
					for (int y = low + 1; y < top; y++) {
						if (ok(d, x, y, z) && d.isAbsent(x, y, z)) {
							d.set(x, y, z, roof.full());
						}
					}
				}
			}
		}
	}

	/** Direction from a local step, defaulting to front. */
	public static Direction dir(int dx, int dz) {
		Direction r = Dirs.of(Integer.signum(dx), Integer.signum(dz));
		return r == null ? Dirs.FRONT : r;
	}

	/** A hanging lantern state of the culture (always the iron lantern on the surface). */
	public static BlockState lantern(boolean hanging) {
		return States.lantern(Blocks.LANTERN, hanging);
	}

	/**
	 * The drafted blocks that would float: not joined (by a face or an edge, through other drafted blocks) to the natural
	 * ground under the plot, as the blueprint check sees them. Returns their keys ({@link Draft#key}).
	 */
	public static it.unimi.dsi.fastutil.longs.LongArrayList loose(Draft d) {
		var cells = d.cells();
		it.unimi.dsi.fastutil.longs.LongOpenHashSet solid = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		for (var e : cells.long2ObjectEntrySet()) {
			if (!e.getValue().state().isAir()) {
				solid.add(e.getLongKey());
			}
		}
		it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue q = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
		int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (long k : solid) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			for (int[] o : six) {
				int nx = x + o[0];
				int ny = y + o[1];
				int nz = z + o[2];
				if (d.isAbsent(nx, ny, nz) && ny < d.site().ground(nx, nz)) {
					seen.add(k);
					q.enqueue(k);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						int nz = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
						if (nz == 0 || nz == 3) {
							continue;
						}
						long n = Draft.key(x + dx, y + dy, z + dz);
						if (solid.contains(n) && seen.add(n)) {
							q.enqueue(n);
						}
					}
				}
			}
		}
		it.unimi.dsi.fastutil.longs.LongArrayList out = new it.unimi.dsi.fastutil.longs.LongArrayList();
		for (long k : solid) {
			if (!seen.contains(k)) {
				out.add(k);
			}
		}
		return out;
	}

	/**
	 * Takes away what a later pass left hanging: every group of at most {@code most} drafted blocks no longer joined to the
	 * ground (a crenel's sill whose parapet a tower's room was cleared out of, a lamp whose post a doorway took).
	 */
	public static void dropLoose(Draft d, int most) {
		var l = loose(d);
		if (l.isEmpty() || l.size() > most) {
			return;
		}
		for (long k : l) {
			d.set(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k), Blocks.AIR.defaultBlockState());
		}
	}

	/**
	 * The last stroke of a seat or a fort: {@link #dropLoose} the few blocks a later pass left hanging, then (with
	 * -Dhearth.dbgloose) print whatever still floats, for finding the pass that leaves it.
	 */
	public static void settle(Draft d, String what) {
		dropLoose(d, 6);
		if (System.getProperty("hearth.dbgloose") == null) {
			return;
		}
		var l = loose(d);
		if (l.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder("DBGLOOSE " + what + " " + d.style().culture() + " " + d.frontage() + "x" + d.depth() + " " + l.size() + ":");
		for (int i = 0; i < Math.min(12, l.size()); i++) {
			long k = l.getLong(i);
			sb.append(' ').append(Draft.keyX(k)).append('/').append(Draft.keyY(k)).append('/').append(Draft.keyZ(k)).append('=')
				.append(d.get(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k)).getBlock().getDescriptionId());
		}
		System.out.println(sb);
	}
}
