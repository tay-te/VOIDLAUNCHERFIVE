package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landscape.Manner.LampKind;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Street lights in each culture's manner (see {@link LampKind}): a timber crook on a stone foot, a double standard, a
 * karst stone lantern, a fire basket on a pillar, a tiki torch, a lantern on a masonry pillar, a bayou lantern on a
 * chain, a lantern under a little roof. All stand on the level they are given and stay inside the plot.
 */
public final class Lamps {
	private Lamps() {
	}

	/** The style's lantern, as a block that can hang. */
	public static Block lantern(Draft d) {
		Block l = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		return l.defaultBlockState().hasProperty(BlockStateProperties.HANGING) ? l : Blocks.LANTERN;
	}

	static boolean in(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** A slender post of the style: a fence of the frame's wood, or the fence role's wall where that is stone. */
	public static Paint post(Draft d) {
		Mat frame = d.mat(Role.FRAME);
		if (frame.has(Form.FENCE)) {
			return frame.fence();
		}
		Mat fence = d.mat(Role.FENCE);
		return fence.has(Form.FENCE) ? fence.fence() : fence.has(Form.WALL) ? fence.wall() : Manner.masonry(d).wall();
	}

	/** A stone foot or shaft: the masonry's wall form (or full block). */
	public static Paint shaft(Draft d) {
		Mat m = Manner.masonry(d);
		return m.has(Form.WALL) ? m.wall() : m.full();
	}

	/**
	 * A lamp standard at (x, z) on standing level y, no taller than {@code room} blocks (and the plot's height).
	 * {@code arm} is the way a crook or bracket reaches (null: lanterns on top). Returns the highest y drawn.
	 */
	public static int standard(Draft d, int x, int y, int z, int room, LampKind kind, @Nullable Direction arm) {
		room = Math.min(room, d.maxHeight() - y);
		if (room < 2) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, y, z, States.lantern(lantern(d), false));
			}
			return y;
		}
		Block lantern = lantern(d);
		BlockState hanging = States.lantern(lantern, true);
		BlockState standing = States.lantern(lantern, false);
		Mat masonry = Manner.masonry(d);
		Mat roof = d.mat(Role.ROOF);
		int top;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			switch (kind) {
				case STONE_LANTERN -> {
					int h = Math.min(room, 4);
					d.set(x, y, z, masonry.full());
					for (int k = 1; k < h - 2; k++) {
						d.set(x, y + k, z, shaft(d));
					}
					d.set(x, y + h - 2, z, standing);
					d.set(x, y + h - 1, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : roof.full());
					top = y + h - 1;
				}
				case BRAZIER -> {
					int h = Math.min(room, 3);
					for (int k = 0; k < h - 1; k++) {
						d.set(x, y + k, z, k == 0 ? masonry.full() : shaft(d));
					}
					d.set(x, y + h - 1, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
					top = y + h - 1;
				}
				case TIKI -> {
					int h = Math.min(room, 4);
					Mat fence = d.mat(Role.FENCE);
					Paint pole = fence.has(Form.FENCE) ? fence.fence() : post(d);
					for (int k = 0; k < h - 1; k++) {
						d.set(x, y + k, z, pole);
					}
					d.set(x, y + h - 1, z, Blocks.TORCH.defaultBlockState());
					top = y + h - 1;
				}
				case PILLAR -> {
					int h = Math.min(room, 4);
					Mat fence = d.mat(Role.FENCE);
					Paint col = fence.has(Form.WALL) && !Manner.wood(fence) ? fence.wall() : shaft(d);
					d.set(x, y, z, masonry.full());
					for (int k = 1; k < h - 1; k++) {
						d.set(x, y + k, z, col);
					}
					d.set(x, y + h - 1, z, standing);
					top = y + h - 1;
				}
				case CAPPED -> {
					int h = Math.min(room, 5);
					d.set(x, y, z, shaft(d));
					for (int k = 1; k < h - 2; k++) {
						d.set(x, y + k, z, post(d));
					}
					d.set(x, y + h - 2, z, standing);
					d.set(x, y + h - 1, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, Half.BOTTOM, false));
					top = y + h - 1;
				}
				case HANGING -> {
					int h = Math.min(room, 5);
					Mat frame = d.mat(Role.FRAME);
					// a stump of a log for a foot, a slender post above
					d.set(x, y, z, frame.log(Direction.Axis.Y).orElse(shaft(d)));
					for (int k = 1; k < h; k++) {
						d.set(x, y + k, z, post(d));
					}
					top = y + h - 1;
					if (!hang(d, x, top, z, arm, 2, hanging)) {
						d.set(x, top + 1, z, standing);
						top++;
					}
				}
				case DOUBLE -> {
					int h = Math.min(room, 5);
					d.set(x, y, z, shaft(d));
					for (int k = 1; k < h; k++) {
						d.set(x, y + k, z, post(d));
					}
					top = y + h - 1;
					Direction a = arm != null ? arm : Dirs.RIGHT;
					boolean one = hang(d, x, top, z, Dirs.right(a), 1, hanging);
					boolean two = hang(d, x, top, z, Dirs.left(a), 1, hanging);
					if (!one && !two && top + 1 < d.maxHeight()) {
						d.set(x, top + 1, z, standing);
						top++;
					}
				}
				default -> {
					int h = Math.min(room, 4);
					d.set(x, y, z, shaft(d));
					for (int k = 1; k < h; k++) {
						d.set(x, y + k, z, post(d));
					}
					top = y + h - 1;
					if (!hang(d, x, top, z, arm, 1, hanging)) {
						if (top + 1 < d.maxHeight()) {
							d.set(x, top + 1, z, standing);
							top++;
						} else {
							d.set(x, top, z, standing);
						}
					}
				}
			}
		}
		return top;
	}

	/** An arm from the post top toward {@code dir} with a lantern hanging under its end; false if it does not fit. */
	private static boolean hang(Draft d, int x, int top, int z, @Nullable Direction dir, int links, BlockState lantern) {
		if (dir == null) {
			return false;
		}
		int ax = x + Dirs.dx(dir);
		int az = z + Dirs.dz(dir);
		if (!in(d, ax, az) || !d.isAbsent(ax, top, az) || !d.isAbsent(ax, top - 1, az) || top - 1 - links < d.site().ground(ax, az)) {
			return false;
		}
		d.set(ax, top, az, post(d));
		int ly = top - 1;
		for (int k = 1; k < links; k++) {
			d.set(ax, ly, az, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			ly--;
		}
		d.set(ax, ly, az, lantern);
		return true;
	}

	/** A lantern on the floor (a lantern set down on a step or a wall top). */
	public static void lanternOn(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, States.lantern(lantern(d), false));
		}
	}
}
