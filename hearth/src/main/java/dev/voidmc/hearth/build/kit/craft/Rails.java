package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.trade.Hall;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rail along every edge a worker walks over a drop: the staging round a hull, a charging gallery, a hayloft's well,
 * a landing on a trestle. Wherever a cell one can stand on at a level has beside it a fall of four blocks or more, a
 * fence of the culture's wood goes on the edge: on a joist end laid out from the deck (so the rail stands on the deck's
 * edge, never in the air), or, where the drop is the plot's own edge, on the cell itself. Ladders, stairs, hoist doors
 * and anything else standing in the edge cell close it already; cells listed as gaps (a hoist's landing, a gangway) are
 * left open.
 */
public final class Rails {
	private Rails() {
	}

	/**
	 * Rails the edges of everything one can stand on at level y over the box [x0..x1] x [z0..z1]. Returns how many rails
	 * were set.
	 */
	public static int guard(Draft d, int x0, int z0, int x1, int z1, int y, List<int[]> gaps) {
		return guard(d, x0, z0, x1, z1, y, gaps, true);
	}

	/** {@link #guard}; {@code street}: also toward the street (the row before the plot). */
	private static int guard(Draft d, int x0, int z0, int x1, int z1, int y, List<int[]> gaps, boolean street) {
		Paint rail = Hall.woodFence(d).fence();
		int n = 0;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!standable(d, x, y, z) || gap(gaps, x, z)) {
						continue;
					}
					for (Direction dir : Dirs.HORIZONTAL) {
						int nx = x + Dirs.dx(dir);
						int nz = z + Dirs.dz(dir);
						if (!street && nz < 0 || gap(gaps, nx, nz) || !d.isEmpty(nx, y, nz) || !d.isEmpty(nx, y + 1, nz) || drop(d, nx, y, nz) < 4) {
							continue;
						}
						if (!d.allowed(nx, y, nz) || !d.allowed(nx, y - 1, nz)) {
							// the plot ends here: the rail goes on the edge cell itself
							d.set(x, y, z, rail);
							n++;
							break;
						}
						BlockState deck = d.get(x, y - 1, z);
						if (deck != null && deck.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) {
							d.set(nx, y - 1, nz, deck);
						} else {
							d.set(nx, y - 1, nz, Hall.woodFence(d).full());
						}
						d.set(nx, y, nz, rail);
						n++;
					}
				}
			}
		}
		return n;
	}

	/**
	 * Rails the yard's edges: everything one can stand on at the ground's levels (from three below the floor to two over
	 * it) beside a fall of four or more, inside the plot or over its edge where the land drops away below a levelled yard
	 * (never toward the street). Run when everything else is drawn. Returns how many rails were set.
	 */
	public static int yard(Draft d) {
		int n = 0;
		for (int y = -3; y <= 2; y++) {
			n += guard(d, 0, 0, d.frontage() - 1, d.depth() - 1, y, List.of(), false);
		}
		n += reached(d);
		// a little higher (a mound's or a bank's top) only at the plot's sides and back, where the land falls away
		for (int y = 3; y <= 4; y++) {
			for (int[] r : new int[][]{{0, 0, 0, d.depth() - 1}, {d.frontage() - 1, 0, d.frontage() - 1, d.depth() - 1}, {0, d.depth() - 1, d.frontage() - 1,
				d.depth() - 1}}) {
				n += guard(d, r[0], r[1], r[2], r[3], y, List.of(), false);
			}
		}
		return n;
	}

	/**
	 * Rails the stairwell of a U-stair ({@link Hall#switchback}'s result {@code well}, from storey k up to k + 1) on the
	 * floor above: round its opening, so nobody steps off the landing's floor into it; the stair's head stays open.
	 */
	public static int well(Draft d, Hall h, int k, int[] well) {
		if (well == null || k + 1 >= h.storeys()) {
			return 0;
		}
		return opening(d, h.floorY(k + 1), well[4] - 1, well[5] - 1, well[6] + 1, well[7] + 1);
	}

	/**
	 * Rails the head of a straight flight ({@link Hall#flight}'s result {@code flight}, from storey k up to k + 1) on the
	 * floor above, round the opening the flight climbs through. Call when the floor is furnished.
	 */
	public static int flight(Draft d, Hall h, int k, int[] flight) {
		if (flight == null || k + 1 >= h.storeys()) {
			return 0;
		}
		return opening(d, h.floorY(k + 1), Math.min(flight[4], flight[6]) - 1, Math.min(flight[5], flight[7]) - 1, Math.max(flight[4], flight[6]) + 1,
			Math.max(flight[5], flight[7]) + 1);
	}

	/** Rails round an opening in a floor at level y within the box, for whoever stands on the floor or on a chest there. */
	private static int opening(Draft d, int y, int x0, int z0, int x1, int z1) {
		return guard(d, x0, z0, x1, z1, y, List.of()) + guard(d, x0, z0, x1, z1, y + 1, List.of());
	}

	/**
	 * Rails every edge someone can actually get to: walks the finished draft from its entrances the way a person does
	 * (a step up of a block or a stair, a drop of three at most, up and down ladders, through doors) and rails each cell
	 * walked to that has a fall of four or more beside it, at whatever height: a gallery, a landing, a roof climbed onto
	 * from a stack of barrels. Ladder cells are left as they are. Returns how many rails were set.
	 */
	public static int reached(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int y0 = -6;
		int y1 = d.maxHeight() + 1;
		int sy = y1 - y0 + 1;
		int sx = w + 2;
		int sz = dp + 2;
		boolean[] seen = new boolean[sx * sy * sz];
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		for (int[] e : d.entrances()) {
			for (int dy = 1; dy >= -2; dy--) {
				int y = e[1] + dy;
				if (walk(d, e[0], y, e[2])) {
					mark(seen, q, e[0], y, e[2], y0, sx, sy, sz);
					break;
				}
			}
		}
		java.util.List<int[]> edges = new java.util.ArrayList<>();
		while (!q.isEmpty()) {
			int[] c = q.poll();
			int x = c[0];
			int y = c[1];
			int z = c[2];
			boolean climb = ladder(d.get(x, y, z));
			boolean head = pass(d, x, y + 2, z);
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (nx < -1 || nz < -1 || nx > w || nz > dp) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (dy == 1 && !head) {
						continue;
					}
					if (walk(d, nx, ny, nz)) {
						mark(seen, q, nx, ny, nz, y0, sx, sy, sz);
						break;
					}
					BlockState there = d.get(nx, ny, nz);
					if (dy == 0 && step(there) && head && walk(d, nx, ny + 1, nz)) {
						mark(seen, q, nx, ny + 1, nz, y0, sx, sy, sz);
						break;
					}
					if (!pass(d, nx, ny, nz) || dy < 0 && !pass(d, nx, ny + 1, nz)) {
						if (dy <= 0) {
							break;
						}
					}
				}
			}
			if (climb) {
				for (int dy : new int[]{1, -1}) {
					if (walk(d, x, y + dy, z) || ladder(d.get(x, y + dy, z))) {
						mark(seen, q, x, y + dy, z, y0, sx, sy, sz);
					}
				}
			}
			if (!climb && x >= 0 && z >= 0 && x < w && z < dp) {
				edges.add(c);
			}
		}
		int n = 0;
		Paint rail = Hall.woodFence(d).fence();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : edges) {
				int x = c[0];
				int y = c[1];
				int z = c[2];
				if (!d.isEmpty(x, y, z)) {
					continue;
				}
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (!pass(d, nx, y, nz) || !pass(d, nx, y + 1, nz) || ladder(d.get(nx, y, nz)) || drop(d, nx, y, nz) < 4) {
						continue;
					}
					if (nz < 0 && y < 3) {
						// the street's own edge at its level is the street's business
						continue;
					}
					if (nz < 0 || !d.allowed(nx, y, nz) || !d.allowed(nx, y - 1, nz)) {
						d.set(x, y, z, rail);
						n++;
						break;
					}
					BlockState deck = d.get(x, y - 1, z);
					d.set(nx, y - 1, nz, deck != null && deck.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
						? Paint.of(deck) : Hall.woodFence(d).full());
					d.set(nx, y, nz, rail);
					n++;
				}
			}
		}
		return n;
	}

	private static void mark(boolean[] seen, java.util.ArrayDeque<int[]> q, int x, int y, int z, int y0, int sx, int sy, int sz) {
		int ix = x + 1;
		int iz = z + 1;
		int iy = y - y0;
		if (ix < 0 || iz < 0 || iy < 0 || ix >= sx || iz >= sz || iy >= sy) {
			return;
		}
		int i = (iy * sz + iz) * sx + ix;
		if (!seen[i]) {
			seen[i] = true;
			q.add(new int[]{x, y, z});
		}
	}

	/** Feet or head pass through (x, y, z): nothing drawn and above the ground, or something a person passes through. */
	private static boolean pass(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y >= d.site().ground(x, z);
		}
		return dev.voidmc.hearth.build.kit.light.Spaces.passable(s);
	}

	/** Someone stands at (x, y, z): feet and head pass, and something holds them (a floor, the ground, a ladder). */
	private static boolean walk(Draft d, int x, int y, int z) {
		if (!pass(d, x, y, z) || !pass(d, x, y + 1, z)) {
			return false;
		}
		BlockState feet = d.get(x, y, z);
		if (ladder(feet)) {
			return true;
		}
		BlockState under = d.get(x, y - 1, z);
		if (under == null) {
			return y - 1 < d.site().ground(x, z);
		}
		return dev.voidmc.hearth.build.kit.light.Spaces.standable(under) || ladder(under);
	}

	private static boolean ladder(BlockState s) {
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock);
	}

	private static boolean step(BlockState s) {
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
			&& s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
			|| s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
			&& s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
			|| s.getBlock() instanceof net.minecraft.world.level.block.BedBlock);
	}

	/** Someone can stand at (x, y, z): something under it, and the cell and the one over it empty. */
	public static boolean standable(Draft d, int x, int y, int z) {
		BlockState under = d.get(x, y - 1, z);
		if (under == null) {
			// nothing drawn under it: the natural ground, where it stands at this level
			return d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && d.site().ground(x, z) == y;
		}
		return !under.isAir() && !(under.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)
			&& !(under.getBlock() instanceof net.minecraft.world.level.block.WallBlock) && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z)
			&& !under.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
	}

	/** How far one stepping off into (x, y, z) falls: empty cells under it down to something solid or the ground. */
	public static int drop(Draft d, int x, int y, int z) {
		// (the site knows the ground a few blocks round the plot too)
		int g = d.site().ground(x, z);
		int n = 0;
		for (int yy = y - 1; yy >= y - 12; yy--) {
			BlockState s = d.get(x, yy, z);
			if (s == null ? yy < g : !s.isAir()) {
				break;
			}
			n++;
		}
		return n;
	}

	private static boolean gap(List<int[]> gaps, int x, int z) {
		for (int[] g : gaps) {
			if (g[0] == x && g[1] == z) {
				return true;
			}
		}
		return false;
	}
}
