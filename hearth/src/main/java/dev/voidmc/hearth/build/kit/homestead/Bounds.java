package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Field boundaries that follow the ground: dry-stone walls laid in two courses with a capping of wall stones, hedgerows
 * with a tree standing in them now and then, post-and-rail fences, earth bunds. Each stands on the surface of its own
 * column as the draft has it (after levelling), steps with the ground so it never gapes, leaves gaps where a path or
 * steps cross, and takes gates with posts where the caller asks.
 */
public final class Bounds {
	private Bounds() {
	}

	/** How a boundary is made. */
	public enum Kind {
		/** Two courses of fieldstone capped with wall stones. */
		DRYSTONE,
		/** A hedge of the culture's leaves, a tree in it here and there. */
		HEDGE,
		/** Posts and rails (the style's fence). */
		RAILS,
		/** A low bank of earth. */
		BUND,
		NONE
	}

	/** The culture's way of bounding land, from its yard habit. */
	public static Kind kind(Draft d) {
		Habits.FenceKind f = d.habits().yard().fence();
		return switch (f) {
			case WALL -> DRYSTONE_OK(d) ? Kind.DRYSTONE : Kind.RAILS;
			case HEDGE -> Kind.HEDGE;
			case FENCE -> Kind.RAILS;
			case NONE -> d.mat(Role.FENCE).has(Form.FENCE) ? Kind.RAILS : Kind.BUND;
		};
	}

	private static boolean DRYSTONE_OK(Draft d) {
		return d.mat(Role.FOUNDATION).has(Form.WALL) || d.mat(Role.FENCE).has(Form.WALL);
	}

	/** The cells of a rectangle's ring, clockwise from the front-left corner. */
	public static List<int[]> ring(int x0, int z0, int x1, int z1) {
		return dev.voidmc.hearth.build.draft.Draft.ring(x0, z0, x1, z1);
	}

	/**
	 * A boundary along cells (in order). Cells listed in {@code gates} (as "x,z") get a gate facing {@code gateFacing}
	 * across the line, with posts either side; cells whose surface is not ground (steps, paving, a building) are left open.
	 */
	public static void line(Draft d, List<int[]> cells, Kind kind, Set<String> gates, Direction gateFacing) {
		java.util.Map<String, Direction> m = new java.util.HashMap<>();
		for (String k : gates) {
			m.put(k, gateFacing);
		}
		line(d, cells, kind, m);
	}

	/** {@link #line} with a facing per gate cell. */
	public static void line(Draft d, List<int[]> cells, Kind kind, java.util.Map<String, Direction> gates) {
		if (kind == Kind.NONE) {
			return;
		}
		int n = cells.size();
		int[] g = new int[n];
		for (int i = 0; i < n; i++) {
			g[i] = Groundwork.surface(d, cells.get(i)[0], cells.get(i)[1]);
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat fenceWall = d.mat(Role.FENCE);
		Mat fence = d.mat(Role.FENCE);
		Mat hedge = d.mat(Role.HEDGE);
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < n; i++) {
				int x = cells.get(i)[0];
				int z = cells.get(i)[1];
				int y = g[i];
				if (!Garden.groundLike(d, x, y - 1, z) || !d.isEmpty(x, y, z)) {
					continue;
				}
				// the top course reaches the neighbours' so a stepped line stays closed
				int top = y;
				for (int k : new int[]{i - 1, i + 1}) {
					if (k >= 0 && k < n && adjacent(cells.get(i), cells.get(k))) {
						top = Math.max(top, g[k]);
					}
				}
				Direction gf = gates.get(x + "," + z);
				if (gf != null) {
					gate(d, x, y, z, gf, kind, fence, frame);
					continue;
				}
				switch (kind) {
					case DRYSTONE -> {
						for (int yy = y; yy < Math.max(y + 1, top + 1); yy++) {
							d.set(x, yy, z, stone.full());
						}
						int capY = Math.max(y + 1, top + 1);
						double u = Noise.unit(d.seed() ^ 0xD5, x, 0, z);
						if (u < 0.08 && stone.has(Form.SLAB)) {
							// a through-stone laid flat across the top
							d.set(x, capY, z, stone.slab(SlabType.BOTTOM));
						} else if (stone.has(Form.WALL)) {
							d.set(x, capY, z, stone.wall());
						} else if (fenceWall.has(Form.WALL)) {
							d.set(x, capY, z, fenceWall.wall());
						}
					}
					case HEDGE -> {
						int h = Noise.unit(d.seed() ^ 0x4ED, x, 0, z) < 0.65 ? 2 : 1;
						for (int yy = y; yy < Math.max(y + h, top + 1); yy++) {
							d.set(x, yy, z, hedge.leaves());
						}
					}
					case RAILS -> {
						for (int yy = y; yy <= top; yy++) {
							d.set(x, yy, z, fence.has(Form.FENCE) ? fence.fence() : stone.wall());
						}
					}
					case BUND -> {
						d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
						d.set(x, y, z, Blocks.GRASS_BLOCK.defaultBlockState());
						if (Noise.unit(d.seed() ^ 0xB4, x, 0, z) < 0.3) {
							d.set(x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
						}
					}
					default -> {
					}
				}
			}
		}
	}

	private static boolean adjacent(int[] a, int[] b) {
		return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == 1;
	}

	/** A gate across the line with a post either side (stone piers in a wall, posts in a hedge or fence). */
	private static void gate(Draft d, int x, int y, int z, Direction facing, Kind kind, Mat fence, Mat frame) {
		d.set(x, y, z, fence.has(Form.FENCE_GATE) ? fence.gate(facing, false) : Paint.of(Blocks.AIR.defaultBlockState()));
		d.set(x, y + 1, z, Blocks.AIR.defaultBlockState());
		Direction across = Dirs.right(facing);
		for (int side : new int[]{-1, 1}) {
			int px = x + Dirs.dx(across) * side;
			int pz = z + Dirs.dz(across) * side;
			if (px < 0 || pz < 0 || px >= d.frontage() || pz >= d.depth()) {
				continue;
			}
			int py = Groundwork.surface(d, px, pz);
			if (!Garden.groundLike(d, px, py - 1, pz) || !d.isEmpty(px, py, pz) && !(kind == Kind.HEDGE || kind == Kind.DRYSTONE)) {
				continue;
			}
			if (kind == Kind.DRYSTONE) {
				Mat stone = d.mat(Role.FOUNDATION);
				d.set(px, py, pz, stone.full());
				d.set(px, py + 1, pz, stone.full());
				if (stone.has(Form.SLAB)) {
					d.set(px, py + 2, pz, stone.slab(SlabType.BOTTOM));
				}
			} else {
				d.set(px, py, pz, frame.log(Direction.Axis.Y));
				d.set(px, py + 1, pz, frame.log(Direction.Axis.Y));
			}
		}
	}

	/**
	 * A boundary round a rectangle with gates at the given cells (a gate on the front or back run opens front to back,
	 * one on a side run left to right); the ring is walked clockwise from the front-left corner.
	 */
	public static void around(Draft d, int x0, int z0, int x1, int z1, Kind kind, List<int[]> gateCells) {
		java.util.Map<String, Direction> gates = new java.util.HashMap<>();
		for (int[] c : gateCells) {
			boolean side = (c[0] == x0 || c[0] == x1) && c[1] != z0 && c[1] != z1;
			gates.put(c[0] + "," + c[1], side ? Dirs.LEFT : Dirs.FRONT);
		}
		line(d, ring(x0, z0, x1, z1), kind, gates);
	}

	/** A hedgerow tree standing in a hedge or at a field corner: a short trunk and a broad crown of the local tree. */
	public static void hedgeTree(Draft d, int x, int y, int z) {
		Garden.tree(d, x, y, z, 3 + (int) (Noise.unit(d.seed() ^ 0x7EE, x, 0, z) * 2));
	}

	/** Whether a block is a boundary piece (for keeping paths clear). */
	public static boolean isBoundary(BlockState s) {
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock);
	}
}
