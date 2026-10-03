package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Chimney stacks: masonry from the hearth up through the roof to clear the ridge line nearby, a corbelled course near the
 * top, and a lit campfire in the pot so it smokes (smoke needs open air above, so the fire is the stack's top block).
 */
public final class Chimneys {
	private Chimneys() {
	}

	/**
	 * A 1 × 1 stack at (x, z) from {@code fromY} up through the roof, ending at least two blocks above the roof surface
	 * at its column and no lower than {@code minTop}. Returns the y of the smoking top.
	 */
	public static int stack(Draft d, int x, int z, int fromY, Roofs.Roof roof, int minTop) {
		Mat stone = d.mat(Role.CHIMNEY);
		int surface = roof.covers(x, z) ? roof.surfaceY(x, z) : fromY;
		int top = Math.max(surface + 2, minTop);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = fromY; y < top; y++) {
				d.set(x, y, z, stone.full());
			}
			// a little corbel under the pot where it stands clear of the roof
			if (top - surface >= 3 && stone.has(dev.voidmc.hearth.build.style.Form.SLAB)) {
				int cy = top - 1;
				for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int ox = x + o[0];
					int oz = z + o[1];
					if (d.isEmpty(ox, cy, oz) && !roof.covers(ox, oz) || d.isEmpty(ox, cy, oz) && roof.surfaceY(ox, oz) < cy - 1) {
						if (ox >= 0 && oz >= -1 && ox < d.frontage() && oz < d.depth()) {
							d.set(ox, cy, oz, stone.slab(SlabType.TOP));
						}
					}
				}
			}
			d.set(x, top, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
		}
		return top;
	}
}
