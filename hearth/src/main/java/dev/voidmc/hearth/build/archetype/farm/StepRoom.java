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
	private StepRoom() {
	}

	/** Notches the banks over every reachable step in the plot's rectangle, standing levels {@code y0..y1}. */
	static void clear(Draft d, int y0, int y1) {
		int w = d.frontage();
		int dp = d.depth();
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
							if (!open(d, x, y + h, z) && cuttable(d, x, y + h, z) && !wet(d, x, y + h, z)) {
								d.set(x, y + h, z, Blocks.AIR.defaultBlockState());
							}
						}
					}
				}
			}
		}
	}

	private static boolean open(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s == null) {
			return y >= d.site().ground(x, z);
		}
		return s.isAir() || !s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			&& !(s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock);
	}

	/** Water stands beside the cell or over it (the bank holds a flooded bed or channel: never notched). */
	private static boolean wet(Draft d, int x, int y, int z) {
		for (int[] o : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
			BlockState s = d.get(x + o[0], y + o[1], z + o[2]);
			if (s != null && !s.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
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
		return b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.DIRT_PATH || b == Blocks.ROOTED_DIRT
			|| b == Blocks.MUD || b == Blocks.PACKED_MUD || b instanceof net.minecraft.world.level.block.SlabBlock
			|| s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE
			|| b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.RED_SAND;
	}
}
