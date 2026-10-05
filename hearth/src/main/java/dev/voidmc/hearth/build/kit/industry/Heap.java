package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Garden;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Heaps of what a works takes in and throws out: ore and charcoal by the furnace, slag and cinders behind it, clay and
 * sand by the kiln, cullet by the glasshouse, salt in white cones by the pans. A heap is a lumpy mound over an ellipse,
 * highest in the middle, its blocks mixed in patches (a heap is tipped load by load), topped with slabs where a course
 * stands half done. It sits on the yard it is given and fills down to the ground where the yard is lower, so nothing
 * hangs.
 */
public final class Heap {
	private Heap() {
	}

	/**
	 * A heap centred on (cx, cz) with radii rx and rz, rising to {@code height} blocks in the middle, standing on level
	 * y. Its body is drawn from {@code mix} in patches; where the surface stands half a block over a whole course
	 * {@code cap} (a slab paint, or null) tops it. Only cells nothing else has taken are used. Returns the cells covered
	 * with their heights {x, z, top}.
	 */
	public static List<int[]> heap(Draft d, double cx, double cz, double rx, double rz, double height, int y, Block[] mix, @Nullable Paint cap, int salt) {
		List<int[]> out = new ArrayList<>();
		int x0 = (int) Math.floor(cx - rx);
		int x1 = (int) Math.ceil(cx + rx);
		int z0 = (int) Math.floor(cz - rz);
		int z1 = (int) Math.ceil(cz + rz);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!Works.in(d, x, z)) {
						continue;
					}
					double t = Math.hypot((x - cx) / Math.max(0.5, rx), (z - cz) / Math.max(0.5, rz));
					if (t > 1.0) {
						continue;
					}
					double lump = (Noise.unit(d.seed() ^ (0x4EA9L * salt), x, y, z) - 0.5) * 0.6;
					double h = height * Math.pow(Math.max(0, 1 - t * t), 0.75) + lump;
					int halves = (int) Math.round(h * 2);
					if (halves < 1) {
						continue;
					}
					if (!d.isEmpty(x, y, z)) {
						continue;
					}
					int full = halves / 2;
					boolean half = halves % 2 == 1;
					if (cap == null && half) {
						full++;
						half = false;
					}
					// fill down to the ground where the yard below is not solid
					footing(d, x, y, z, mix, salt);
					int top = y - 1;
					for (int k = 0; k < full; k++) {
						if (!d.isEmpty(x, y + k, z)) {
							break;
						}
						d.set(x, y + k, z, pick(d, salt, x, y + k, z, mix));
						top = y + k;
					}
					if (half && cap != null && d.isEmpty(x, top + 1, z)) {
						BlockState c = cap.at(x, top + 1, z);
						if (c != null) {
							d.set(x, top + 1, z, c);
						}
					}
					out.add(new int[]{x, z, top});
				}
			}
		}
		return out;
	}

	/** A heap of one block kind with its slab as the cap. */
	public static List<int[]> heap(Draft d, double cx, double cz, double r, double height, int y, Block body, @Nullable Block slab, int salt) {
		Paint cap = slab == null ? null : Paint.of(slab.defaultBlockState());
		return heap(d, cx, cz, r, r, height, y, new Block[]{body}, cap, salt);
	}

	/** The block of a mix for a cell: patches a couple of blocks across, with the odd stray. */
	public static BlockState pick(Draft d, int salt, int x, int y, int z, Block[] mix) {
		if (mix.length == 1) {
			return mix[0].defaultBlockState();
		}
		double v = Noise.value(d.seed() ^ (0x7E11L * salt), x / 1.7, y / 1.7, z / 1.7);
		if (Noise.unit(d.seed() ^ (0x51A7L * salt), x, y, z) < 0.18) {
			v = Noise.unit(d.seed() ^ (0x33L * salt), x, y, z);
		}
		return mix[Math.min(mix.length - 1, (int) (v * mix.length))].defaultBlockState();
	}

	/** Whatever stands under (x, y, z) made solid down to the ground with the heap's own stuff. */
	static void footing(Draft d, int x, int y, int z, Block[] mix, int salt) {
		if (d.isSolid(x, y - 1, z) || Garden.groundLike(d, x, y - 1, z)) {
			return;
		}
		int g = d.site().ground(x, z);
		for (int yy = y - 1; yy >= Math.min(y - 1, g - 1) && yy >= y - 6; yy--) {
			if (d.isSolid(x, yy, z)) {
				break;
			}
			d.set(x, yy, z, pick(d, salt, x, yy, z, mix));
		}
	}
}
