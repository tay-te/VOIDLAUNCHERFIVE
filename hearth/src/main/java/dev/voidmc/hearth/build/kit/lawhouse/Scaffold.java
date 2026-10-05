package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Raised timber work for the law: a scaffold (a deck of planks on posts with knee braces, an edge beam and a rail)
 * or a stone stage under it, the stair up, and the gallows frame itself (two uprights, a cross beam with corbelled ends,
 * the ropes hanging from it as chains, the drop as a trapdoor in the deck, the hangman's lever on a post).
 */
public final class Scaffold {
	private Scaffold() {
	}

	/**
	 * A timber scaffold over {@code x0..x1 × z0..z1} whose deck is at standing level {@code h}: posts at the corners and
	 * every other cell along the edges down to the ground on stone pads, a deck of planks on edge beams, knee braces
	 * (upside-down stairs) under the beams at the posts. Open underneath.
	 */
	public static void timber(Draft d, int x0, int z0, int x1, int z1, int h) {
		Timber t = Timber.frame(d);
		Timber j = Timber.joinery(d);
		Stone pad = Stone.footing(d);
		int deck = h - 1;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edgeX = x == x0 || x == x1;
					boolean edgeZ = z == z0 || z == z1;
					if (!edgeX && !edgeZ) {
						continue;
					}
					// posts at the corners and the middle of the long sides: open underneath, carpentry not a box
					boolean post = edgeX && edgeZ || edgeX && z == (z0 + z1) / 2 && (z1 - z0) >= 4 || edgeZ && x == (x0 + x1) / 2 && (x1 - x0) >= 5;
					if (post) {
						int g = d.site().ground(x, z);
						d.set(x, Math.min(-1, g - 1), z, pad.full());
						for (int y = Math.min(-1, g - 1) + 1; y < deck; y++) {
							d.set(x, y, z, t.post());
						}
					}
				}
			}
			// the edge beams carrying the deck, logs lying along each side
			for (int x = x0; x <= x1; x++) {
				d.set(x, deck, z0, t.beam(Direction.Axis.X));
				d.set(x, deck, z1, t.beam(Direction.Axis.X));
			}
			for (int z = z0 + 1; z < z1; z++) {
				d.set(x0, deck, z, t.beam(Direction.Axis.Z));
				d.set(x1, deck, z, t.beam(Direction.Axis.Z));
			}
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, deck, z, j.planks());
				}
			}
		}
		// knee braces under the long beams beside the corner posts, so the deck reads as carpentry and not a box
		if (deck >= 2) {
			try (var st = d.stage(StageKind.FRAME)) {
				for (int[] c : new int[][]{{x0 + 1, z0, 1}, {x1 - 1, z0, -1}, {x0 + 1, z1, 1}, {x1 - 1, z1, -1}}) {
					if (c[0] > x0 && c[0] < x1 && d.isEmpty(c[0], deck - 1, c[1])) {
						d.set(c[0], deck - 1, c[1], t.stairs(c[2] > 0 ? Dirs.RIGHT : Dirs.LEFT, Half.TOP));
					}
				}
			}
		}
	}

	/**
	 * A stone stage over {@code x0..x1 × z0..z1} with its deck at standing level {@code h}: a body of the footing stone,
	 * the dressing as quoins at its corners and as the coping round its top, the deck paved in planks over the stone
	 * (the drop needs timber).
	 */
	public static void stone(Draft d, int x0, int z0, int x1, int z1, int h) {
		Stone body = Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		Timber j = Timber.joinery(d);
		int deck = h - 1;
		try (var st = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edgeX = x == x0 || x == x1;
					boolean edgeZ = z == z0 || z == z1;
					int g = d.site().ground(x, z);
					for (int y = Math.min(-1, g - 1); y < deck; y++) {
						boolean quoin = edgeX && edgeZ;
						d.set(x, y, z, quoin && y >= 0 ? dress.full() : body.full());
					}
					d.set(x, deck, z, edgeX || edgeZ ? dress.full() : j.planks());
				}
			}
		}
	}

	/**
	 * A straight stair of {@code rise} steps climbing toward {@code dir} from the cell (x, z) at the foot (its first step
	 * at y 0), each step footed to the ground in the footing stone or on a timber stringer, with a rail along
	 * {@code railSide} where that cell is free. Returns {x, z} of the cell beyond the top step.
	 */
	public static int[] stair(Draft d, int x, int z, Direction dir, int rise, Direction railSide, boolean stone) {
		Timber t = Timber.frame(d);
		Timber j = Timber.joinery(d);
		Stone foot = Stone.footing(d);
		Stone dress = Stone.dressing(d);
		for (int k = 0; k < rise; k++) {
			int sx = x + Dirs.dx(dir) * k;
			int sz = z + Dirs.dz(dir) * k;
			int g = d.site().ground(sx, sz);
			try (var st = d.stage(StageKind.FOUNDATION)) {
				for (int y = Math.min(-1, g - 1); y < k; y++) {
					d.set(sx, y, sz, stone ? foot.full() : y == Math.min(-1, g - 1) ? foot.full() : t.post());
				}
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(sx, k, sz, stone && dress.has(Form.STAIRS) ? dress.stairs(dir, Half.BOTTOM) : j.stairs(dir, Half.BOTTOM));
			}
			int rx = sx + Dirs.dx(railSide);
			int rz = sz + Dirs.dz(railSide);
			if (k >= 1 && rx >= 0 && rx < d.frontage() && rz >= 0 && rz < d.depth() && d.isEmpty(rx, k + 1, rz)) {
				try (var st = d.stage(StageKind.EXTERIOR)) {
					for (int y = Math.min(-1, d.site().ground(rx, rz) - 1); y <= k; y++) {
						if (d.isEmpty(rx, y, rz)) {
							d.set(rx, y, rz, y < 0 ? foot.full() : t.fence());
						}
					}
					d.set(rx, k + 1, rz, t.fence());
				}
			}
		}
		return new int[]{x + Dirs.dx(dir) * rise, z + Dirs.dz(dir) * rise};
	}

	/**
	 * The gallows over a deck at standing level {@code h}: uprights at {@code a} and {@code b} (cells along the X axis at
	 * row z) rising {@code tall} blocks, a cross beam a block wider than them each side on corbels, a rope (two links of
	 * chain) from the beam over each cell in {@code ropes}, the drop under each rope as a trapdoor in the deck. Returns
	 * the beam's y.
	 */
	public static int gallows(Draft d, int a, int b, int z, int h, int tall, int[] ropes) {
		Timber t = Timber.frame(d);
		Timber j = Timber.joinery(d);
		int beamY = h + tall;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{a, b}) {
				for (int y = h; y < beamY; y++) {
					d.set(x, y, z, t.post());
				}
			}
			for (int x = a - 1; x <= b + 1; x++) {
				d.set(x, beamY, z, t.beam(Direction.Axis.X));
			}
			// corbels under the beam's ends, outside the uprights
			d.set(a - 1, beamY - 1, z, t.stairs(Dirs.RIGHT, Half.TOP));
			d.set(b + 1, beamY - 1, z, t.stairs(Dirs.LEFT, Half.TOP));
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int x : ropes) {
				d.set(x, beamY - 1, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				if (beamY - 2 > h + 2) {
					d.set(x, beamY - 2, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				}
			}
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int x : ropes) {
				d.set(x, h - 1, z, j.trapdoor(Dirs.BACK, Half.TOP, false));
			}
		}
		return beamY;
	}

	/** The hangman's lever on a short post at standing level y. */
	public static void lever(Draft d, int x, int y, int z, Direction facing) {
		Timber t = Timber.frame(d);
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, t.fence());
			d.set(x, y + 1, z, Blocks.LEVER.defaultBlockState()
				.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
					net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
				.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing));
		}
	}

	/** A rail of fence posts round the edge of a deck at standing level h, open over {@code gaps} (cells {x, z}). */
	public static void rail(Draft d, int x0, int z0, int x1, int z1, int h, java.util.Set<Long> gaps) {
		Timber t = Timber.frame(d);
		Paint fence = t.fence();
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				if (gaps.contains(net.minecraft.core.BlockPos.asLong(c[0], 0, c[1])) || !d.isEmpty(c[0], h, c[1])) {
					continue;
				}
				d.set(c[0], h, c[1], fence);
			}
		}
	}

	/** A key for {@link #rail}'s gaps. */
	public static long gap(int x, int z) {
		return net.minecraft.core.BlockPos.asLong(x, 0, z);
	}
}
