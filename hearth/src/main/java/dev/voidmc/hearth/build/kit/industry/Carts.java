package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What a caravan travels with: four-wheeled wagons, open and loaded or covered with a canvas tilt over their hoops; the
 * hitching rail where the teams are tied; pens with their troughs and hay.
 */
public final class Carts {
	private Carts() {
	}

	/**
	 * A wagon two wide and four long from (x, z) running along {@code run} (its pole out the front, toward
	 * {@code run.getOpposite()}): a bed of boards on four wheels, covered with a canvas tilt (wool, its colour a faded
	 * one of {@code canvas}) or loaded open with barrels, sacks and hay. Cells: the 2 × 4 bed, a wheel column either side,
	 * the pole cell in front.
	 */
	public static void wagon(Draft d, int x, int z, Direction run, boolean covered, DyeColor canvas) {
		Mat wood = Works.timber(d);
		Mat wheel = d.mat(Role.FRAME);
		Direction across = Dirs.right(run);
		Block wool = Blocks.WOOL.pick(canvas);
		Block woolSlab = Blocks.WOOL_SLAB.pick(canvas);
		Block woolStairs = Blocks.WOOL_STAIRS.pick(canvas);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < 4; i++) {
				for (int k = 0; k < 2; k++) {
					int px = x + Dirs.dx(run) * i + Dirs.dx(across) * k;
					int pz = z + Dirs.dz(run) * i + Dirs.dz(across) * k;
					if (!Works.in(d, px, pz)) {
						continue;
					}
					d.set(px, 0, pz, wood.slab(SlabType.TOP));
					if (covered) {
						boolean end = i == 0 || i == 3;
						if (end) {
							// the tilt's ends rounded down over the bows
							Direction out = i == 0 ? run.getOpposite() : run;
							d.set(px, 1, pz, States.stairs(woolStairs, out.getOpposite(), Half.BOTTOM));
						} else {
							d.set(px, 1, pz, wool.defaultBlockState());
							d.set(px, 2, pz, States.slab(woolSlab, SlabType.BOTTOM));
						}
					} else {
						double u = Noise.unit(d.seed() ^ 0xCA27, px, 1, pz);
						BlockState load = u < 0.35 ? States.facing(Blocks.BARREL, Direction.UP) : u < 0.6 ? Blocks.HAY_BLOCK.defaultBlockState()
							: u < 0.8 ? States.slab(Blocks.WOOL_SLAB.pick(DyeColor.BROWN), SlabType.BOTTOM) : null;
						if (load != null) {
							d.set(px, 1, pz, load);
						}
					}
				}
			}
			// the wheels: at the front and back axles either side
			for (int i : new int[]{0, 3}) {
				for (int side : new int[]{-1, 2}) {
					int px = x + Dirs.dx(run) * i + Dirs.dx(across) * side;
					int pz = z + Dirs.dz(run) * i + Dirs.dz(across) * side;
					if (Works.in(d, px, pz) && d.isEmpty(px, 0, pz) && wheel.has(Form.TRAPDOOR)) {
						Direction face = side < 0 ? across.getOpposite() : across;
						d.set(px, 0, pz, wheel.trapdoor(face, Half.BOTTOM, true));
					}
				}
			}
			// the pole
			int qx = x - Dirs.dx(run);
			int qz = z - Dirs.dz(run);
			if (Works.in(d, qx, qz) && d.isEmpty(qx, 0, qz)) {
				d.set(qx, 0, qz, wood.slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * A hitching rail along {@code run} from (x, z): posts every other cell with a rail across their heads, {@code length}
	 * long. Returns nothing; the archetype registers the posts where the teams stand.
	 */
	public static void rail(Draft d, int x, int z, Direction run, int length) {
		Mat post = d.mat(Role.FRAME);
		Mat fence = Works.timber(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz) || !d.isEmpty(px, 0, pz)) {
					continue;
				}
				boolean end = i == 0 || i == length - 1 || i % 3 == 0;
				if (end) {
					d.set(px, 0, pz, post.log(Direction.Axis.Y));
					d.set(px, 1, pz, post.log(run.getAxis()));
				} else {
					d.set(px, 0, pz, fence.has(Form.FENCE) ? fence.fence() : post.log(Direction.Axis.Y));
					d.set(px, 1, pz, post.log(run.getAxis()));
				}
			}
		}
	}

	/** Canvas colours a caravan's tilts come in: undyed, oatmeal, faded browns. */
	public static DyeColor canvas(Draft d, int k) {
		DyeColor[] c = {DyeColor.WHITE, DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.BROWN};
		return c[(int) (Noise.unit(d.seed() ^ 0xCA77, k, 0, 0) * c.length) % c.length];
	}
}
