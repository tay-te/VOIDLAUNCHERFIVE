package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The salter's works: shallow evaporation pans divided by kerbs you walk along, some flooded with brine, some drying
 * white, some raked into crust; cones of salt heaped beside them; a graduation tower of brushwood on its timber frame
 * where brine trickles down in the wind (the inland works of Central Europe); the boiling pans over their fires; sacks
 * of salt stacked for the carts.
 */
public final class Salt {
	private Salt() {
	}

	/** One pan of a field, and what state it is in. */
	public record Pan(int x0, int z0, int x1, int z1, int state) {
	}

	/**
	 * A field of pans over [x0..x1] × [z0..z1] (standing level 0): kerbs of {@code kerb} every {@code step} cells (and
	 * round the edge, and down column {@code lane}), flush with the ground, the pans between them sunk a block, floored
	 * with clay, each flooded with brine, drying white or raked into crust by its hash. Returns the pans.
	 */
	public static List<Pan> pans(Draft d, int x0, int z0, int x1, int z1, int step, int lane, Mat kerb) {
		return pans(d, x0, z0, x1, z1, step, step, lane, kerb);
	}

	/**
	 * {@link #pans} with kerbs every {@code stepX} cells across and every {@code stepZ} back, so the pans are long strips
	 * (three across and five back, say) rather than a chequer of little squares.
	 */
	public static List<Pan> pans(Draft d, int x0, int z0, int x1, int z1, int stepX, int stepZ, int lane, Mat kerb) {
		List<Pan> out = new ArrayList<>();
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean k = x == x0 || x == x1 || z == z0 || z == z1 || (x - x0) % stepX == 0 || (z - z0) % stepZ == 0 || x == lane;
					d.set(x, -2, z, Blocks.PACKED_MUD.defaultBlockState());
					if (k) {
						d.set(x, -1, z, kerb.full());
					}
				}
			}
		}
		// the pans: each run of cells between kerbs
		for (int pz = z0 + 1; pz < z1; pz += stepZ) {
			for (int px = x0 + 1; px < x1; px += stepX) {
				int ax = px;
				int bx = Math.min(x1 - 1, px + stepX - 2);
				int az = pz;
				int bz = Math.min(z1 - 1, pz + stepZ - 2);
				// the lane splits a pan
				List<int[]> spans = new ArrayList<>();
				if (lane >= ax && lane <= bx) {
					if (lane - 1 >= ax) {
						spans.add(new int[]{ax, lane - 1});
					}
					if (lane + 1 <= bx) {
						spans.add(new int[]{lane + 1, bx});
					}
				} else {
					spans.add(new int[]{ax, bx});
				}
				for (int[] sp : spans) {
					int state = (int) (Noise.unit(d.seed() ^ 0x5A17, sp[0], 0, az) * 3);
					out.add(new Pan(sp[0], az, sp[1], bz, state));
					fill(d, sp[0], az, sp[1], bz, state);
				}
			}
		}
		return out;
	}

	/** A pan in one state: brine, drying (white from its far end, the brine shrinking toward the near), or crust. */
	private static void fill(Draft d, int x0, int z0, int x1, int z1, int state) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					double u = Noise.unit(d.seed() ^ 0xC2057, x, 0, z);
					boolean crust = state == 2 || state == 1 && z > (z0 + z1) / 2;
					d.set(x, -1, z, crust ? Blocks.CALCITE.defaultBlockState() : Blocks.WATER.defaultBlockState());
					if (crust && state == 2 && u < 0.35) {
						// raked into little ridges of salt
						d.set(x, 0, z, States.of(Blocks.CARPET.pick(DyeColor.WHITE)));
					}
				}
			}
		}
	}

	/**
	 * A cone of salt on whole or half centre (cx, cz): radius r, {@code height} tall, crystalline white, its slopes
	 * smooth (a core of calcite under layers of salt, eighths of a block, rather than stairs that read as steps).
	 * Returns its top.
	 */
	public static int cone(Draft d, double cx, double cz, double r, double height) {
		int x0 = (int) Math.floor(cx - r);
		int z0 = (int) Math.floor(cz - r);
		int x1 = (int) Math.ceil(cx + r);
		int z1 = (int) Math.ceil(cz + r);
		Roofs.Field f = Shape.cone(cx, cz, r, height);
		int top = Integer.MIN_VALUE;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					double h = f.h(x, z);
					if (Double.isNaN(h) || !Works.in(d, x, z) || !d.isEmpty(x, 0, z)) {
						continue;
					}
					int full = (int) Math.floor(h);
					int layers = (int) Math.round((h - full) * 8);
					if (full == 0 && layers == 0) {
						layers = 2;
					}
					for (int y = 0; y < full; y++) {
						d.set(x, y, z, Blocks.CALCITE.defaultBlockState());
					}
					if (layers > 0) {
						d.set(x, full, z, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, Math.min(8, layers)));
					}
					top = Math.max(top, full);
				}
			}
		}
		return top;
	}

	/**
	 * A graduation tower along {@code run} from (x, z): a timber frame {@code length} long and {@code height} tall,
	 * posts every third cell with rails at every course, filled with brushwood (the culture's leaves, packed), a board
	 * walk and a pent roof along its head, a trough of brine along its foot on {@code side}. Returns its top.
	 */
	public static int tower(Draft d, int x, int z, Direction run, int length, int height, Direction side) {
		Mat frame = d.mat(Role.FRAME);
		Mat leaves = d.mat(Role.TREE_LEAVES);
		Mat boards = Works.timber(d);
		int top = height;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz)) {
					continue;
				}
				boolean post = i == 0 || i == length - 1 || i % 3 == 0;
				for (int y = 0; y < height; y++) {
					if (post) {
						d.set(px, y, pz, frame.log(Direction.Axis.Y));
					} else if (y == 0 || y == height - 1 || (y & 1) == 0 && y > 0) {
						d.set(px, y, pz, (y == 0 || y == height - 1) ? frame.log(run.getAxis()) : leaves.leaves());
					} else {
						d.set(px, y, pz, leaves.leaves());
					}
				}
				// the walk and its pent roof
				d.set(px, height, pz, boards.has(Form.SLAB) ? boards.slab(SlabType.BOTTOM) : boards.full());
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int i = -1; i <= length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz) || height + 1 >= d.maxHeight()) {
					continue;
				}
				if (i >= 0 && i < length && i % 3 == 0) {
					d.set(px, height + 1, pz, frame.has(Form.FENCE) ? frame.fence() : boards.fence());
				}
			}
			top = Math.max(top, height + 1);
		}
		// the trough of brine along the foot
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i + Dirs.dx(side);
				int pz = z + Dirs.dz(run) * i + Dirs.dz(side);
				if (Works.in(d, px, pz) && d.isEmpty(px, 0, pz) && i % 2 == 1) {
					d.set(px, 0, pz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				}
			}
		}
		return top;
	}

	/** Sacks of salt: white wool heaped in stairs and slabs along {@code run}. */
	public static void sacks(Draft d, int x, int y, int z, Direction run, int count) {
		Block wool = Blocks.WOOL.pick(DyeColor.WHITE);
		Block stairs = Blocks.WOOL_STAIRS.pick(DyeColor.WHITE);
		Block slab = Blocks.WOOL_SLAB.pick(DyeColor.WHITE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (!Works.in(d, px, pz) || !d.isEmpty(px, y, pz)) {
					continue;
				}
				double u = Noise.unit(d.seed() ^ 0x5AC5, px, y, pz);
				if (u < 0.4) {
					d.set(px, y, pz, wool.defaultBlockState());
					d.set(px, y + 1, pz, States.slab(slab, SlabType.BOTTOM));
				} else if (u < 0.75) {
					d.set(px, y, pz, States.stairs(stairs, i % 2 == 0 ? run : run.getOpposite(), Half.BOTTOM));
				} else {
					d.set(px, y, pz, States.slab(slab, SlabType.BOTTOM));
				}
			}
		}
	}

	/**
	 * The boiling pans: a hearth of {@code body} along {@code run} from (x, z) with a fire under each pan (lit campfires
	 * sunk in the floor) and the pans (cauldrons of brine) on it. Returns the cells before the pans.
	 */
	public static List<int[]> boilers(Draft d, int x, int z, Direction run, int count, Direction front, Fam body) {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			try (var s = d.stage(StageKind.FLOORS)) {
				d.set(px, -1, pz, States.lit(Blocks.CAMPFIRE, true));
			}
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(px, 0, pz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			}
			out.add(new int[]{px + Dirs.dx(front), pz + Dirs.dz(front)});
		}
		return out;
	}
}
