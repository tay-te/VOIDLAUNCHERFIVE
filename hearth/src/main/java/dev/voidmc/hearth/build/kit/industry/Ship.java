package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A ship on the stocks: the keel laid along the slip on its blocks, stem and sternpost reared at the ends, the frames
 * (ribs) standing up from the keel like the bones of a fish, the strakes of planking closing them in from the keel
 * upward (the bow already planked to the sheer, the stern still bare ribs above the lower strakes), shores propping the
 * bilges. Clinker hulls of the north and carvel hulls of the south look alike at this scale: what reads is the shape,
 * pointed at both ends, swelling amidships, rising toward the stem.
 */
public final class Ship {
	private Ship() {
	}

	/** A hull drawn: its bounds and the deck height. */
	public record Hull(int cx, int zBow, int zStern, int halfBeam, int depth, int top) {
	}

	/**
	 * A hull centred on column cx from {@code zBow} (low z, toward the street) to {@code zStern}, {@code halfBeam}
	 * across at its widest (each side of the keel), {@code depth} from keel to sheer, standing with its keel at level y.
	 * Planked from the keel up to the sheer over the fore {@code planked} share of its length, to two strakes over the
	 * rest. Returns the hull.
	 */
	public static Hull hull(Draft d, int cx, int zBow, int zStern, int halfBeam, int depth, int y, double planked, Mat plank, Mat frame) {
		int len = zStern - zBow;
		int top = y + depth;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = zBow; z <= zStern; z++) {
				d.set(cx, y, z, frame.log(Direction.Axis.Z));
			}
		}
		for (int z = zBow + 1; z <= zStern - 1; z++) {
			int i = z - zBow;
			int j = zStern - z;
			// half-breadth at the sheer: the bow converging over three stations, the cog's fuller stern over two
			int hb = Math.min(halfBeam, Math.min(i, j + (j >= 1 ? 1 : 0)));
			if (hb < 1) {
				hb = 1;
			}
			boolean narrowing = hb < halfBeam;
			double t = i / (double) Math.max(1, len);
			int sheer = y + depth + (i <= 2 || j <= 1 ? 1 : 0);
			boolean planking = t <= planked;
			boolean rib = i % 2 == 0;
			for (int side : new int[]{-1, 1}) {
				Direction in = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
				// the bottom: boards from the keel out to the bilge, the turn of the bilge a stair rounding it off
				try (var s = d.stage(StageKind.WALLS)) {
					for (int k = 1; k <= hb - 1; k++) {
						int x = cx + side * k;
						if (k == hb - 1 && hb >= 2 && plank.has(Form.STAIRS)) {
							d.set(x, y, z, plank.stairs(in, Half.TOP));
						} else {
							d.set(x, y, z, plank.full());
						}
					}
				}
				for (int yy = y + 1; yy <= sheer; yy++) {
					int x = cx + side * hb;
					if (!Works.in(d, x, z)) {
						continue;
					}
					boolean strake = planking || yy <= y + 2;
					if (strake) {
						try (var s = d.stage(StageKind.WALLS)) {
							if (yy == y + 1 && plank.has(Form.STAIRS) && !narrowing) {
								d.set(x, yy, z, plank.stairs(in, Half.TOP));
							} else {
								d.set(x, yy, z, plank.full());
							}
						}
					} else if (rib) {
						try (var s = d.stage(StageKind.FRAME)) {
							d.set(x, yy, z, frame.has(Form.FENCE) ? frame.fence() : frame.log(Direction.Axis.Y));
						}
					}
					// the gunwale's cap along the planked part
					if (yy == sheer && strake && plank.has(Form.SLAB) && d.isEmpty(x, yy + 1, z)) {
						try (var s = d.stage(StageKind.WALLS)) {
							d.set(x, yy + 1, z, plank.slab(SlabType.BOTTOM));
						}
					}
				}
				// ribs show inside the planked part too: a frame against the planks every other station
				if (rib && hb >= 2 && depth >= 3) {
					int x = cx + side * (hb - 1);
					try (var s = d.stage(StageKind.FRAME)) {
						for (int yy = y + 1; yy <= y + 2; yy++) {
							if (d.isEmpty(x, yy, z) && frame.has(Form.FENCE) && planking) {
								d.set(x, yy, z, frame.fence());
							}
						}
					}
				}
			}
			// thwarts across the open part's head: a beam every fourth station
			if (!planking && i % 4 == 0) {
				try (var s = d.stage(StageKind.FRAME)) {
					for (int k = -hb + 1; k <= hb - 1; k++) {
						if (d.isEmpty(cx + k, sheer, z)) {
							d.set(cx + k, sheer, z, frame.log(Direction.Axis.X));
						}
					}
				}
			}
		}
		// stem and sternpost: the stem rears and curls forward at its head, the post stands at the stern
		try (var s = d.stage(StageKind.FRAME)) {
			for (int yy = y; yy <= top + 1; yy++) {
				d.set(cx, yy, zBow, frame.log(Direction.Axis.Y));
			}
			if (Works.in(d, cx, zBow - 1) && frame.has(Form.STAIRS)) {
				d.set(cx, top + 1, zBow - 1, frame.stairs(Dirs.BACK, Half.TOP));
			}
			for (int yy = y; yy <= top + 1; yy++) {
				d.set(cx, yy, zStern, frame.log(Direction.Axis.Y));
			}
		}
		return new Hull(cx, zBow, zStern, halfBeam, depth, top + 2);
	}

	/**
	 * Shores under a hull's bilges: posts from the ground at level y up to the first strake on both sides, every
	 * {@code every} cells along it.
	 */
	public static void shores(Draft d, Hull h, int y, int every, Mat post) {
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = h.zBow() + 2; z <= h.zStern() - 2; z += every) {
				for (int side : new int[]{-1, 1}) {
					// the outermost hull cell at the first strakes
					for (int k = h.halfBeam() + 1; k >= 1; k--) {
						int x = h.cx() + side * k;
						if (!Works.in(d, x, z)) {
							continue;
						}
						if (!d.isEmpty(x, y + 2, z) || !d.isEmpty(x, y + 1, z)) {
							int ox = x + side;
							if (Works.in(d, ox, z) && d.isEmpty(ox, y, z) && d.isEmpty(ox, y + 1, z)) {
								d.set(ox, y, z, post.log(Direction.Axis.Y));
								d.set(ox, y + 1, z, post.stairsOrSlab(side > 0 ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
							}
							break;
						}
					}
				}
			}
		}
	}
}
