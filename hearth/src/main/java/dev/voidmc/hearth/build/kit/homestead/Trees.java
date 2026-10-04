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

	/** A short trunk and a round crown, blossom through it for apples, cocoa on the trunk for cocoa trees. */
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
		int r = size == 0 ? 1 : 2;
		long seed = d.seed() ^ 0x7E1;
		for (int dy = 0; dy <= (size == 0 ? 2 : 3); dy++) {
			int rr = dy == 0 ? r : dy >= (size == 0 ? 2 : 3) ? 1 : r;
			for (int dx = -rr; dx <= rr; dx++) {
				for (int dz = -rr; dz <= rr; dz++) {
					boolean corner = Math.abs(dx) == rr && Math.abs(dz) == rr;
					double u = Noise.unit(seed, x + dx, base + dy, z + dz);
					if (corner && (rr > 1 || u < 0.6)) {
						continue;
					}
					if (rr == 2 && (Math.abs(dx) == 2 || Math.abs(dz) == 2) && u < 0.22) {
						continue;
					}
					int lx = x + dx;
					int lz = z + dz;
					if (!inPlot(d, lx, lz)) {
						continue;
					}
					if (dx == 0 && dz == 0 && dy < (size == 0 ? 1 : 2)) {
						d.set(lx, base + dy, lz, States.axis(log, Direction.Axis.Y));
						continue;
					}
					Block b = leaves;
					if (kind == Kind.APPLE && u > 0.7) {
						b = u > 0.82 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
					}
					d.setIfAbsent(lx, base + dy, lz, Paint.of(States.leaves(b)));
				}
			}
		}
		// the crown's underside a little ragged: a few leaves hang one lower
		for (Direction dir : Dirs.HORIZONTAL) {
			int lx = x + Dirs.dx(dir) * r;
			int lz = z + Dirs.dz(dir) * r;
			if (inPlot(d, lx, lz) && r == 2 && Noise.unit(seed, lx, base - 1, lz) < 0.4 && d.isAbsent(lx, base - 1, lz) && base - 1 >= Spots.level(d, lx, lz) + 2) {
				d.set(lx, base - 1, lz, States.leaves(leaves));
			}
		}
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

	/** A ladder leaning against a trunk at (x, y..y+1, z), on the side {@code side} of it (left after the harvest). */
	public static void ladder(Draft d, int x, int y, int z, Direction side) {
		int lx = x + Dirs.dx(side);
		int lz = z + Dirs.dz(side);
		if (!inPlot(d, lx, lz) || !d.isAbsent(lx, y, lz) || !d.isAbsent(lx, y + 1, lz)) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(lx, y, lz, States.facing(Blocks.LADDER, side));
			d.set(lx, y + 1, lz, States.facing(Blocks.LADDER, side));
		}
	}

	/** A shade tree (the culture's own), larger than a fruit tree, for a pasture or a yard. */
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
			boolean conifer = BuiltInRegistries.BLOCK.getKey(leaves).getPath().contains("spruce");
			int trunk = conifer ? 2 : 3;
			for (int t = 0; t < trunk; t++) {
				d.set(x, y + t, z, States.axis(log, Direction.Axis.Y));
			}
			int base = y + trunk;
			int layers = conifer ? 6 : 4;
			for (int dy = 0; dy < layers; dy++) {
				int rr = conifer ? Math.max(0, 2 - dy / 2) + (dy % 2 == 0 ? 0 : -0) : dy == layers - 1 ? 1 : 2;
				if (conifer && dy == layers - 1) {
					rr = 0;
				}
				for (int dx = -rr; dx <= rr; dx++) {
					for (int dz = -rr; dz <= rr; dz++) {
						double u = Noise.unit(d.seed() ^ 0x5AD, x + dx, base + dy, z + dz);
						if (Math.abs(dx) == rr && Math.abs(dz) == rr && rr > 0 && (conifer || u < 0.7)) {
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

	/** A mat helper for callers wanting the orchard's leaves. */
	public static Mat leaves(Draft d) {
		return d.mat(Role.TREE_LEAVES);
	}
}
