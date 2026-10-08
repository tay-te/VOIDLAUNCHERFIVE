package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Head room over the steps of a worked field: where a flight through the banks of terraced beds ends under the next
 * bank's lip or coping (a step with earth or a coping stone over it), the bank is notched over the step, the way a
 * farmer cuts the bank back over the treads he walks every day. Only earth, fill and coping over a step a person can
 * reach (open at its low side) is cut.
 */
final class StepRoom {
	/** What a bank is made of besides the culture's own earth and stone (by name: tags are not bound offline). */
	private static final java.util.Set<net.minecraft.world.level.block.Block> EARTH = java.util.Set.of(Blocks.DIRT, Blocks.COARSE_DIRT,
		Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.PACKED_MUD, Blocks.DIRT_PATH,
		Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
		Blocks.DEEPSLATE, Blocks.CALCITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.TERRACOTTA);

	private StepRoom() {
	}

	/** Notches the banks over every reachable step in the plot's rectangle, standing levels {@code y0..y1}. */
	static void clear(Draft d, int y0, int y1) {
		// (passes until nothing more is cut: a flight's lower step, notched first, opens the way to the one above it)
		for (int pass = 0; pass < 4; pass++) {
			if (!pass(d, y0, y1)) {
				break;
			}
		}
	}

	/** One pass over the plot; whether anything was cut. */
	private static boolean pass(Draft d, int y0, int y1) {
		int w = d.frontage();
		int dp = d.depth();
		boolean cut = false;
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					for (int y = y0; y <= y1; y++) {
						BlockState st = d.get(x, y, z);
						if (st == null || !(st.getBlock() instanceof StairBlock) || st.getValue(StairBlock.HALF) != Half.BOTTOM) {
							continue;
						}
						// someone steps onto it from its low side (open there at the step's level and over it)
						var f = st.getValue(StairBlock.FACING);
						int fx = x - dev.voidmc.hearth.build.draft.Dirs.dx(f);
						int fz = z - dev.voidmc.hearth.build.draft.Dirs.dz(f);
						if (!open(d, fx, y, fz) || !open(d, fx, y + 1, fz)) {
							continue;
						}
						for (int h = 1; h <= 2; h++) {
							if (!open(d, x, y + h, z) && cuttable(d, x, y + h, z) && dam(d, x, y + h, z)) {
								d.set(x, y + h, z, Blocks.AIR.defaultBlockState());
								cut = true;
							}
						}
					}
				}
			}
		}
		return cut;
	}

	private static boolean open(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y >= d.site().ground(x, z);
		}
		// (a path block or a slab is something under the feet, not head room)
		return s.isAir() || s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
	}

	/**
	 * Whether the cell can be notched without letting water out: never under water; where the flooded bed or channel
	 * beside it reaches the notch, the bed's last cell there is banked up with earth first (a farmer narrows the bed by a
	 * cell at the steps rather than flood his path). False where water it cannot bank (over the cell, or not plain water).
	 */
	private static boolean dam(Draft d, int x, int y, int z) {
		BlockState over = d.get(x, y + 1, z);
		if (over != null && !over.getFluidState().isEmpty()) {
			return false;
		}
		java.util.List<int[]> bank = new java.util.ArrayList<>();
		for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			BlockState s = d.get(x + o[0], y, z + o[1]);
			if (s != null && !s.getFluidState().isEmpty()) {
				if (!s.is(Blocks.WATER)) {
					return false;
				}
				bank.add(o);
			}
		}
		for (int[] o : bank) {
			d.set(x + o[0], y, z + o[1], Blocks.COARSE_DIRT.defaultBlockState());
		}
		return true;
	}

	/** Earth, fill, a coping or the natural ground: what a bank is made of (never a shed's wall or a gate). */
	private static boolean cuttable(Draft d, int x, int y, int z) {
		if (y >= d.maxHeight()) {
			return false;
		}
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y < d.site().ground(x, z);
		}
		var b = s.getBlock();
		// the field's own earth, path, fill and bank stone in the culture's materials (a karst path of polished stone, a
		// desert bank of sandstone)
		for (dev.voidmc.hearth.build.style.Role r : new dev.voidmc.hearth.build.style.Role[]{dev.voidmc.hearth.build.style.Role.PATH,
			dev.voidmc.hearth.build.style.Role.GROUND, dev.voidmc.hearth.build.style.Role.FILL, dev.voidmc.hearth.build.style.Role.FOUNDATION}) {
			if (b == d.mat(r).mainOr(dev.voidmc.hearth.build.style.Form.FULL, null)) {
				return true;
			}
		}
		return EARTH.contains(b) || b instanceof net.minecraft.world.level.block.SlabBlock;
	}
}
