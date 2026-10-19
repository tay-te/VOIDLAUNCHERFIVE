package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Wood as the works hold it: cordwood stacked between stakes (billets for the clamp, the kiln's and the glasshouse's
 * fuel), log decks on their skids at the landing (the forest's yield, butt ends to the track), timber racks of seasoning
 * planks and baulks, and stumps where trees stood. Everything stands on the level it is given and on the ground.
 */
public final class Timber {
	private Timber() {
	}

	/**
	 * A cord of billets along {@code run} from (x, y, z): {@code length} long, {@code height} courses high, the billets
	 * lying across the run so their cut ends show on the long face, a stake at each end, the top course ragged.
	 */
	public static void cord(Draft d, int x, int y, int z, Direction run, int length, int height, Mat log) {
		Direction.Axis across = Dirs.right(run).getAxis();
		Mat stake = Works.timber(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz)) {
					continue;
				}
				boolean end = i == 0 || i == length - 1;
				int h = height;
				if (!end && Noise.unit(d.seed() ^ 0xC0BD, px, y, pz) < 0.3) {
					h = Math.max(1, height - 1);
				}
				for (int k = 0; k < h; k++) {
					if (d.isEmpty(px, y + k, pz)) {
						d.set(px, y + k, pz, Works.billet(d, log, across));
					}
				}
			}
			// the stakes holding the ends
			for (int e : new int[]{-1, length}) {
				int px = x + Dirs.dx(run) * e;
				int pz = z + Dirs.dz(run) * e;
				if (Works.in(d, px, pz) && d.isEmpty(px, y, pz) && stake.has(Form.FENCE)) {
					for (int k = 0; k < height; k++) {
						if (d.isEmpty(px, y + k, pz)) {
							d.set(px, y + k, pz, stake.fence());
						}
					}
				}
			}
		}
	}

	/**
	 * A log deck: long logs lying along {@code axis} on two skids, {@code width} across and {@code length} long, piled
	 * pyramid-wise to {@code height} courses, the butts toward the low end of the axis. Returns the courses at its middle.
	 */
	public static int deck(Draft d, int x, int y, int z, Direction.Axis axis, int length, int width, int height, Mat log) {
		boolean alongX = axis == Direction.Axis.X;
		int top = 0;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int b = 0; b < width; b++) {
				int fromEdge = Math.min(b, width - 1 - b);
				int h = Math.min(height, 1 + fromEdge * 2 + (width % 2 == 0 && fromEdge == width / 2 - 1 ? 1 : 0));
				h = Math.min(height, Math.max(1, h));
				for (int a = 0; a < length; a++) {
					int px = alongX ? x + a : x + b;
					int pz = alongX ? z + b : z + a;
					if (!Works.in(d, px, pz)) {
						continue;
					}
					int hh = h;
					// the logs are not all one length: the far ends straggle
					if (a == length - 1 && Noise.unit(d.seed() ^ 0xDEC, px, y, pz) < 0.5) {
						hh = Math.max(1, h - 1);
					}
					for (int k = 0; k < hh; k++) {
						if (d.isEmpty(px, y + k, pz)) {
							d.set(px, y + k, pz, Works.billet(d, log, axis));
						}
					}
					top = Math.max(top, hh);
				}
			}
		}
		return top;
	}

	/** A stump where a tree was felled: a short log in the ground, cut flat (a stripped top now and then). */
	public static void stump(Draft d, int x, int y, int z, Mat log) {
		if (!Works.in(d, x, z) || !d.isEmpty(x, y, z)) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Works.billet(d, log, Direction.Axis.Y));
		}
	}

	/**
	 * A timber rack: posts at both ends and every third cell along {@code run}, with rails at heights 1 and 2 carrying
	 * boards laid across, so seasoning planks show in thin courses with air between. {@code length} cells long.
	 */
	public static void rack(Draft d, int x, int y, int z, Direction run, int length, Mat boards) {
		Mat post = d.mat(Role.FRAME);
		Direction.Axis along = run.getAxis();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz)) {
					continue;
				}
				boolean p = i == 0 || i == length - 1 || i % 3 == 0;
				if (p) {
					for (int k = 0; k < 3; k++) {
						d.set(px, y + k, pz, post.log(Direction.Axis.Y));
					}
					d.set(px, y + 3, pz, post.log(along));
				} else {
					d.set(px, y, pz, boards.slab(SlabType.TOP));
					d.set(px, y + 1, pz, boards.full());
					d.set(px, y + 2, pz, boards.slab(SlabType.BOTTOM));
					d.set(px, y + 3, pz, post.log(along));
				}
			}
		}
	}

	/** Logs of a culture's own tree lying in a loose heap of offcuts and slabwood (chips by the saw). */
	public static void offcuts(Draft d, List<int[]> cells, int y, Mat wood) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				if (!Works.in(d, c[0], c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0x0FFC, c[0], y, c[1]);
				if (u < 0.45 && wood.has(Form.TRAPDOOR)) {
					d.set(c[0], y, c[1], wood.trapdoor(Dirs.HORIZONTAL[(int) (u * 40) % 4], Half.BOTTOM, false));
				} else if (u < 0.8) {
					d.set(c[0], y, c[1], wood.slab(SlabType.BOTTOM));
				}
			}
		}
	}
}
