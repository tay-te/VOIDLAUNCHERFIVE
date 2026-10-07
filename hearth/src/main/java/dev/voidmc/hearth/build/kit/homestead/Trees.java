package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trees a farmer plants: fruit trees for orchards (a short trunk and a round crown, blossom or fruit among the leaves),
 * olive trees with a leaning, gnarled trunk, palms with a crown of fronds, cocoa on jungle trunks, rows of berry bushes.
 * The kind comes from the culture's own garden tree where it bears fruit, else an apple.
 */
public final class Trees {
	private Trees() {
	}

	/** The fruit trees an orchard of this culture plants. */
	public enum Kind {
		/** Oak trunk, oak leaves with blossom (flowering azalea) through the crown. */
		APPLE,
		/** The cherry or wisteria of the culture, all blossom. */
		BLOSSOM,
		/** Leaning gnarled trunk, a loose silvery crown (olive, or azalea). */
		OLIVE,
		/** A tall trunk with a crown of fronds. */
		PALM,
		/** Jungle trunk with cocoa pods, broad crown. */
		COCOA,
		/** The culture's own tree, short and round (acacia, baobab). */
		LOCAL
	}

	/** The orchard tree for the culture: read off its garden tree. */
	public static Kind kind(Draft d) {
		Block leaves = d.mat(Role.TREE_LEAVES).main(Form.FULL);
		String p = leaves == null ? "" : BuiltInRegistries.BLOCK.getKey(leaves).getPath();
		if (p.contains("cherry") || p.contains("wisteria")) {
			return Kind.BLOSSOM;
		}
		if (p.contains("olive") || p.contains("azalea")) {
			return Kind.OLIVE;
		}
		if (p.contains("palm")) {
			return Kind.PALM;
		}
		if (p.contains("jungle")) {
			return d.site().temperature() >= 0.9F && d.mat(Role.GROUND).main(Form.FULL) != Blocks.SAND ? Kind.COCOA : Kind.PALM;
		}
		if (p.contains("acacia") || p.contains("baobab")) {
			return Kind.LOCAL;
		}
		return Kind.APPLE;
	}

	private static Block logOf(Draft d, Kind k) {
		return switch (k) {
			case APPLE -> Blocks.OAK_LOG;
			case COCOA -> Blocks.JUNGLE_LOG;
			default -> {
				Block b = d.mat(Role.TREE_LOG).main(Form.FULL);
				yield b != null ? b : Blocks.OAK_LOG;
			}
		};
	}

	private static Block leavesOf(Draft d, Kind k) {
		return switch (k) {
			case APPLE -> Blocks.OAK_LEAVES;
			case COCOA -> Blocks.JUNGLE_LEAVES;
			default -> {
				Block b = d.mat(Role.TREE_LEAVES).main(Form.FULL);
				yield b != null ? b : Blocks.OAK_LEAVES;
			}
		};
	}

	private static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** Soil under a tree's foot. */
	private static void soil(Draft d, int x, int y, int z) {
		BlockState below = d.get(x, y - 1, z);
		if (below == null || below.isAir() || below.is(Blocks.FARMLAND) || below.is(Blocks.DIRT_PATH)) {
			d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
		}
	}

	/**
	 * A fruit tree standing at (x, y, z) on the ground at standing level y. {@code size} 0 small, 1 grown, 2 old. The
	 * crown never leaves the plot.
	 */
	public static boolean fruit(Draft d, int x, int y, int z, Kind kind, int size) {
		if (top(d, x, y, z, kind, size) >= d.maxHeight()) {
			return false;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			soil(d, x, y, z);
			switch (kind) {
				case PALM -> palm(d, x, y, z, 4 + size + (int) (Noise.unit(d.seed() ^ 0xFA1, x, 0, z) * 2));
				case OLIVE -> olive(d, x, y, z, size);
				default -> round(d, x, y, z, kind, size);
			}
		}
		return true;
	}

	/** The highest y a fruit tree planted at (x, y, z) reaches (its trunk raised clear of the slope round it). */
	public static int top(Draft d, int x, int y, int z, Kind kind, int size) {
		return switch (kind) {
			case PALM -> Math.max(y + 4 + size + 1, headroom(d, x, z, 3) + 2) + 1;
			case OLIVE -> Math.max(y + 2 + (size >= 1 ? 1 : 0), headroom(d, x, z, size == 0 ? 2 : 3)) + 2;
			default -> Math.max(y + 2 + (size >= 2 ? 1 : 0), headroom(d, x, z, size == 0 ? 1 : 2)) + (size == 0 ? 2 : 3) + 1;
		};
	}

	/**
	 * A short trunk and a crown grown the way a fruit tree grows: the trunk forks at the top (the leader stepping a block
	 * toward the light on a grown tree), boughs run out as logs on their sides, and the leaves gather in lobes round the
	 * leader and the boughs' ends, so the crown is lopsided and lumpy, never a ball or a ring. Blossom through it for
	 * apples, cocoa on the trunk for cocoa trees.
	 */
	private static void round(Draft d, int x, int y, int z, Kind kind, int size) {
		Block log = logOf(d, kind);
		Block leaves = leavesOf(d, kind);
		int r0 = size == 0 ? 1 : 2;
		// the crown clears a person's head on every side, the uphill side of a slope too
		int trunk = Math.max(2 + (size >= 2 ? 1 : 0), headroom(d, x, z, r0) - y);
		for (int k = 0; k < trunk; k++) {
			d.set(x, y + k, z, States.axis(log, Direction.Axis.Y));
		}
		int base = y + trunk;
		int height = size == 0 ? 2 : 3;
		long seed = d.seed() ^ 0x7E1 ^ (x * 341873128712L + z * 132897987541L);
		crown(d, x, base, z, log, leaves, size == 0 ? 1 : 2, height, kind == Kind.APPLE, seed, headroom(d, x, z, r0));
		if (kind == Kind.COCOA) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int cx = x + Dirs.dx(dir);
				int cz = z + Dirs.dz(dir);
				if (inPlot(d, cx, cz) && d.isAbsent(cx, y + 1, cz) && Noise.unit(seed ^ 0xC0C, cx, y, cz) < 0.6) {
					// a pod hangs on the trunk: its facing points at the log it grows on
					d.set(cx, y + 1, cz, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, dir.getOpposite()).setValue(CocoaBlock.AGE, 2));
				}
			}
		}
	}

	/**
	 * A broadleaf crown over a trunk top at (x, base - 1, z): a leader up the middle (stepping toward the tree's lean on a
	 * crown of radius 2), boughs as logs on their sides out to lobe centres in different directions and heights, and
	 * leaves where any lobe reaches (a ragged edge from noise, the core always full). Nothing leaves the plot, rises over
	 * {@code base + height}, or hangs under {@code floor} (where heads pass).
	 */
	static void crown(Draft d, int x, int base, int z, Block log, Block leaves, int radius, int height, boolean blossom, long seed, int floor) {
		Direction lean = Dirs.HORIZONTAL[(int) (Noise.unit(seed, 1, 2, 3) * 4) & 3];
		java.util.List<long[]> wood = new java.util.ArrayList<>();
		wood.add(new long[]{x, base - 1, z});
		int lx = x;
		int lz = z;
		if (radius >= 2 && inPlot(d, x + Dirs.dx(lean), z + Dirs.dz(lean)) && Noise.unit(seed, 4, 5, 6) < 0.7) {
			// the leader steps toward the light: a block of bark-all-round at the fork
			lx += Dirs.dx(lean);
			lz += Dirs.dz(lean);
			d.set(lx, base, lz, woodOf(log).defaultBlockState());
			wood.add(new long[]{lx, base, lz});
		}
		for (int k = lx == x ? 0 : 1; k < height - 1; k++) {
			d.set(lx, base + k, lz, States.axis(log, Direction.Axis.Y));
			wood.add(new long[]{lx, base + k, lz});
		}
		// the lobes: the leader's head, then boughs to two or three sides
		java.util.List<double[]> lobes = new java.util.ArrayList<>();
		lobes.add(new double[]{lx, base + height - 1.2, lz, radius + 0.35, height * 0.55 + 0.3});
		int boughs = radius >= 2 ? 2 + (Noise.unit(seed, 7, 8, 9) < 0.5 ? 1 : 0) : 1;
		int first = (int) (Noise.unit(seed, 10, 11, 12) * 4);
		int[] turns = boughs == 3 ? new int[]{0, 1, 3} : new int[]{0, 2};
		for (int b = 0; b < boughs; b++) {
			Direction dir = Dirs.HORIZONTAL[(first + turns[b]) & 3];
			int reach = radius >= 2 ? 1 + (Noise.unit(seed, 13 + b, 14, 15) < 0.55 ? 1 : 0) : 1;
			int by = base + (int) (Noise.unit(seed, 16 + b, 17, 18) * Math.max(1, height - 1));
			int ex = lx;
			int ez = lz;
			for (int k = 1; k <= reach; k++) {
				int nx = lx + Dirs.dx(dir) * k;
				int nz = lz + Dirs.dz(dir) * k;
				if (!inPlot(d, nx, nz) || by < floor) {
					break;
				}
				if (radius >= 2) {
					d.set(nx, by, nz, States.axis(log, Dirs.dx(dir) != 0 ? Direction.Axis.X : Direction.Axis.Z));
					wood.add(new long[]{nx, by, nz});
				}
				ex = nx;
				ez = nz;
			}
			lobes.add(new double[]{ex + Dirs.dx(dir) * 0.4, by + 0.6, ez + Dirs.dz(dir) * 0.4, radius - 0.15 + Noise.unit(seed, 19 + b, 20, 21) * 0.5,
				1.25});
		}
		int span = radius + 1;
		java.util.Set<Long> leaf = new java.util.HashSet<>();
		for (int dy = -1; dy <= height; dy++) {
			int ly = base + dy;
			if (ly < floor || ly > base + height) {
				continue;
			}
			for (int dx = -span; dx <= span; dx++) {
				for (int dz = -span; dz <= span; dz++) {
					int px = x + dx;
					int pz = z + dz;
					if (!inPlot(d, px, pz)) {
						continue;
					}
					double best = 9;
					for (double[] o : lobes) {
						double hx = (px - o[0]) / o[3];
						double hz = (pz - o[2]) / o[3];
						double hy = (ly - o[1]) / o[4];
						best = Math.min(best, hx * hx + hy * hy + hz * hz);
					}
					double u = Noise.unit(seed ^ 0x51E, px, ly, pz);
					// the edge ragged; the core (well inside a lobe) always full, so the crown never shows a hole
					if (best > 1.0 + (u - 0.5) * 0.7 || best > 0.55 && u < 0.12) {
						continue;
					}
					Block b = leaves;
					if (blossom && u > 0.7) {
						b = u > 0.84 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
					}
					if (d.isAbsent(px, ly, pz)) {
						d.set(px, ly, pz, States.leaves(b));
						leaf.add(key(px, ly, pz));
					}
				}
			}
		}
		// leaves the ragged edge cut off from the boughs are taken away (nothing hangs in the air on its own)
		java.util.Set<Long> held = new java.util.HashSet<>();
		java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
		for (long[] w0 : wood) {
			q.add(key((int) w0[0], (int) w0[1], (int) w0[2]));
		}
		while (!q.isEmpty()) {
			long k = q.poll();
			int kx = (int) (k >> 40) - 1024;
			int ky = (int) ((k >> 20) & 0xFFFFF) - 1024;
			int kz = (int) (k & 0xFFFFF) - 1024;
			for (int[] o : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
				long n = key(kx + o[0], ky + o[1], kz + o[2]);
				if (leaf.contains(n) && held.add(n)) {
					q.add(n);
				}
			}
		}
		for (long k : leaf) {
			if (!held.contains(k)) {
				d.remove((int) (k >> 40) - 1024, (int) ((k >> 20) & 0xFFFFF) - 1024, (int) (k & 0xFFFFF) - 1024);
			}
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 1024) << 40) | ((long) (y + 1024) << 20) | (z + 1024);
	}

	/** An olive: a trunk leaning one way and back, a loose, flat crown. */
	private static void olive(Draft d, int x, int y, int z, int size) {
		Block log = logOf(d, Kind.OLIVE);
		Block leaves = leavesOf(d, Kind.OLIVE);
		long seed = d.seed() ^ 0x011;
		Direction lean = Dirs.HORIZONTAL[(int) (Noise.unit(seed, x, 0, z) * 4)];
		d.set(x, y, z, States.axis(log, Direction.Axis.Y));
		int tx = x;
		int tz = z;
		int h = Math.max(2 + (size >= 1 ? 1 : 0), headroom(d, x, z, size == 0 ? 2 : 3) - y);
		for (int k = 1; k < h; k++) {
			if (k == 1 && inPlot(d, x + Dirs.dx(lean), z + Dirs.dz(lean))) {
				// the gnarl: a block of bark-all-round stepping out
				tx += Dirs.dx(lean);
				tz += Dirs.dz(lean);
				Block wood = woodOf(log);
				d.set(tx, y + k, tz, wood.defaultBlockState());
			} else {
				d.set(tx, y + k, tz, States.axis(log, Direction.Axis.Y));
			}
		}
		int base = y + h;
		int r = size == 0 ? 1 : 2;
		for (int dy = 0; dy <= 1; dy++) {
			int rr = dy == 1 ? r - 1 + (size >= 2 ? 1 : 0) : r;
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					double u = Noise.unit(seed, tx + dx, base + dy, tz + dz);
					if (Math.abs(dx) == rr && Math.abs(dz) == rr && u < 0.8 || u < 0.15) {
						continue;
					}
					int lx = tx + dx;
					int lz = tz + dz;
					if (inPlot(d, lx, lz)) {
						d.setIfAbsent(lx, base + dy, lz, Paint.of(States.leaves(leaves)));
					}
				}
			}
		}
		d.set(tx, base, tz, States.axis(log, Direction.Axis.Y));
	}

	/** The lowest a crown of radius r round (x, z) may start: two above the highest ground under it. */
	private static int headroom(Draft d, int x, int z, int r) {
		int m = Integer.MIN_VALUE;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (inPlot(d, x + dx, z + dz)) {
					m = Math.max(m, Spots.level(d, x + dx, z + dz));
				}
			}
		}
		return m + 2;
	}

	private static Block woodOf(Block log) {
		var id = BuiltInRegistries.BLOCK.getKey(log);
		var w = net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_log", "_wood"));
		return BuiltInRegistries.BLOCK.containsKey(w) ? BuiltInRegistries.BLOCK.getValue(w) : log;
	}

	/** A palm: a trunk that leans a little toward its top, a crown of fronds drooping on four sides. */
	private static void palm(Draft d, int x, int y, int z, int height) {
		Block log = logOf(d, Kind.PALM);
		Block leaves = leavesOf(d, Kind.PALM);
		long seed = d.seed() ^ 0xA1A;
		Direction lean = Dirs.HORIZONTAL[(int) (Noise.unit(seed, x, 1, z) * 4)];
		// the drooping fronds clear the heads of folk on every side
		height = Math.max(height, headroom(d, x, z, 3) - y + 2);
		int tx = x;
		int tz = z;
		for (int k = 0; k < height; k++) {
			if (k == height - 2 && inPlot(d, x + Dirs.dx(lean), z + Dirs.dz(lean)) && Noise.unit(seed, x, 2, z) < 0.6) {
				tx += Dirs.dx(lean);
				tz += Dirs.dz(lean);
				d.set(tx, y + k - 1, tz, woodOf(log).defaultBlockState());
			}
			d.set(tx, y + k, tz, States.axis(log, Direction.Axis.Y));
		}
		int top = y + height;
		d.set(tx, top, tz, States.leaves(leaves));
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int k = 1; k <= 3; k++) {
				int lx = tx + Dirs.dx(dir) * k;
				int lz = tz + Dirs.dz(dir) * k;
				int ly = k <= 1 ? top : top - (k - 1);
				if (inPlot(d, lx, lz)) {
					d.setIfAbsent(lx, ly, lz, Paint.of(States.leaves(leaves)));
					if (k == 2) {
						d.setIfAbsent(lx, top, lz, Paint.of(States.leaves(leaves)));
					}
				}
			}
			// the diagonals: short fronds
			Direction r = Dirs.right(dir);
			int lx = tx + Dirs.dx(dir) + Dirs.dx(r);
			int lz = tz + Dirs.dz(dir) + Dirs.dz(r);
			if (inPlot(d, lx, lz)) {
				d.setIfAbsent(lx, top, lz, Paint.of(States.leaves(leaves)));
			}
		}
	}

	/** Berry bushes in a row along {@code run}, {@code n} long, on grass at standing level y. */
	public static void berries(Draft d, int x, int y, int z, Direction run, int n) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < n; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!d.isEmpty(px, y, pz)) {
					continue;
				}
				d.set(px, y - 1, pz, Blocks.GRASS_BLOCK.defaultBlockState());
				int age = 2 + (Noise.unit(d.seed() ^ 0xBE, px, 0, pz) < 0.6 ? 1 : 0);
				d.set(px, y, pz, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, age));
			}
		}
	}

	/**
	 * The picker's short ladder propped against a trunk at (x, y, z), on the side {@code side} of it (left after the
	 * harvest): one rung's height, its foot on the grass. (A taller one would climb into the crown with nowhere to step
	 * off; the pickers reach the fruit from it and from the ground.)
	 */
	public static void ladder(Draft d, int x, int y, int z, Direction side) {
		int lx = x + Dirs.dx(side);
		int lz = z + Dirs.dz(side);
		if (!inPlot(d, lx, lz) || !d.isAbsent(lx, y, lz)) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(lx, y, lz, States.facing(Blocks.LADDER, side));
		}
	}

	/**
	 * A shade tree (the culture's own), larger than a fruit tree, for a pasture or a yard: a broadleaf with a tall clear
	 * trunk and a spreading, lopsided crown of lobes on boughs; a conifer a ragged cone, its tiers uneven.
	 */
	public static void shade(Draft d, int x, int y, int z) {
		Kind k = kind(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			soil(d, x, y, z);
			if (k == Kind.PALM) {
				palm(d, x, y, z, 6);
				return;
			}
			Block log = d.mat(Role.TREE_LOG).main(Form.FULL);
			Block leaves = d.mat(Role.TREE_LEAVES).main(Form.FULL);
			if (log == null || leaves == null) {
				return;
			}
			long seed = d.seed() ^ 0x5AD ^ (x * 341873128712L + z * 132897987541L);
			boolean conifer = BuiltInRegistries.BLOCK.getKey(leaves).getPath().contains("spruce");
			if (!conifer) {
				int trunk = Math.max(3, headroom(d, x, z, 2) - y);
				for (int t = 0; t < trunk; t++) {
					d.set(x, y + t, z, States.axis(log, Direction.Axis.Y));
				}
				crown(d, x, y + trunk, z, log, leaves, 2, 4, false, seed, headroom(d, x, z, 3));
				return;
			}
			int trunk = 2;
			for (int t = 0; t < trunk; t++) {
				d.set(x, y + t, z, States.axis(log, Direction.Axis.Y));
			}
			int base = y + trunk;
			int layers = 6;
			for (int dy = 0; dy < layers; dy++) {
				// tiers stepping in toward the tip, every other one a little drawn in, each skirt uneven
				int rr = dy == layers - 1 ? 0 : Math.max(1, 2 - dy / 2 - (dy % 2));
				for (int dx = -rr - 1; dx <= rr + 1; dx++) {
					for (int dz = -rr - 1; dz <= rr + 1; dz++) {
						double u = Noise.unit(seed, x + dx, base + dy, z + dz);
						int m = Math.abs(dx) + Math.abs(dz);
						boolean in = m <= rr || m == rr + 1 && Math.max(Math.abs(dx), Math.abs(dz)) <= rr && u < 0.45;
						if (!in || m == rr && rr > 1 && u < 0.18) {
							continue;
						}
						if (inPlot(d, x + dx, z + dz)) {
							d.setIfAbsent(x + dx, base + dy, z + dz, Paint.of(States.leaves(leaves)));
						}
					}
				}
				if (dy < layers - 2) {
					d.set(x, base + dy, z, States.axis(log, Direction.Axis.Y));
				}
			}
		}
	}

	/**
	 * A timber tree of given wood, its foot at (x, y, z), at most {@code height} tall: a broadleaf with a clear trunk
	 * and a lobed crown on boughs, or a conifer (a fir or spruce of a woodlot) with a straight stem nearly to the tip and
	 * ragged tiers stepping in. Returns the highest y drawn.
	 */
	public static int timber(Draft d, int x, int y, int z, int height, Block log, Block leaves, boolean conifer) {
		height = Math.min(height, d.maxHeight() - 1 - y);
		long seed = d.seed() ^ 0x71B3 ^ (x * 341873128712L + z * 132897987541L);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			soil(d, x, y, z);
			if (!conifer) {
				int ch = height >= 8 ? 4 : 3;
				int trunk = Math.max(Math.max(2, height - ch - 1), headroom(d, x, z, 2) - y);
				for (int t = 0; t < trunk; t++) {
					d.set(x, y + t, z, States.axis(log, Direction.Axis.Y));
				}
				crown(d, x, y + trunk, z, log, leaves, 2, ch, false, seed, headroom(d, x, z, 3));
				return y + trunk + ch;
			}
			int skirt = Math.max(2, headroom(d, x, z, 2) - y);
			int top = y + Math.max(skirt + 3, height - 1);
			for (int yy = y; yy < top; yy++) {
				d.set(x, yy, z, States.axis(log, Direction.Axis.Y));
			}
			int n = top - (y + skirt);
			for (int k = 0; k <= n; k++) {
				int ly = y + skirt + k;
				// widest low down, stepping in toward the tip; every other tier drawn in a little
				int rr = k == n ? 0 : Math.max(1, (n - k) * 2 / Math.max(1, n) + (k % 2 == 0 ? 0 : -1) + (n >= 6 && k < n / 2 ? 1 : 0));
				for (int dx = -rr; dx <= rr; dx++) {
					for (int dz = -rr; dz <= rr; dz++) {
						int m = Math.abs(dx) + Math.abs(dz);
						double u = Noise.unit(seed, x + dx, ly, z + dz);
						if (m > rr + (u < 0.3 ? 1 : 0) || m == rr && rr > 1 && u < 0.2 || !inPlot(d, x + dx, z + dz)) {
							continue;
						}
						d.setIfAbsent(x + dx, ly, z + dz, Paint.of(States.leaves(leaves)));
					}
				}
			}
			d.setIfAbsent(x, top, z, Paint.of(States.leaves(leaves)));
			return top;
		}
	}

	/** A mat helper for callers wanting the orchard's leaves. */
	public static Mat leaves(Draft d) {
		return d.mat(Role.TREE_LEAVES);
	}
}
