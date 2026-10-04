package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a block left in an empty cell is in the way (docs/LIVING_WORLD.md §2: litter is cleared only where it blocks a
 * door or a path): the cells beside a door or gate, the two cells over each entrance's outside block, and the walking
 * space of a street (the two cells above its surface).
 */
public final class Litter {
	private Litter() {
	}

	/** Whether an air cell of {@code plot} at {@code pos} is part of a way in or a way through. */
	public static boolean blocksWay(PlotLocator locator, Settlement settlement, PlotLocator.Source source, PlotView plot, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState n = locator.cellOf(settlement, source, plot, pos.relative(d).asLong());
			if (n != null && (n.getBlock() instanceof DoorBlock || n.getBlock() instanceof FenceGateBlock)) {
				return true;
			}
		}
		if (street(plot)) {
			BlockState below = locator.cellOf(settlement, source, plot, pos.below().asLong());
			if (below != null && !below.isAir()) {
				return true;
			}
			BlockState below2 = locator.cellOf(settlement, source, plot, pos.below(2).asLong());
			if (below != null && below2 != null && !below2.isAir()) {
				return true;
			}
		}
		Blueprint bp = source.blueprint(settlement, plot);
		if (bp != null) {
			for (BlockPos e : bp.entrances()) {
				if (e.equals(pos) || e.equals(pos.below())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Roads, stairs, bridges and walkways: plots folk walk along. */
	static boolean street(PlotView plot) {
		String path = plot.spec().archetype().getPath();
		return path.startsWith("infra/road") || path.startsWith("infra/stairs") || path.startsWith("infra/bridge") || path.startsWith("infra/walkway")
			|| path.startsWith("infra/dock") || path.startsWith("landscape/plaza");
	}
}
