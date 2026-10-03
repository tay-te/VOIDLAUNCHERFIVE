package dev.voidmc.hearth.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Receives build steps from a {@link Blueprint}: one block to set, in world coordinates. */
@FunctionalInterface
public interface StepSink {
	/**
	 * @param state          the block, with its final shape already resolved (stair corners, fence and wall connections,
	 *                       pane links): appliers place it without neighbour updates
	 * @param blockEntityTag block-entity data to load into the placed block (sign text, lectern book, banner, pot), or null
	 */
	void accept(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityTag);
}
