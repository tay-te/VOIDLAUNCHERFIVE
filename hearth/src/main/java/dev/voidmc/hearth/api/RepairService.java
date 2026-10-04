package dev.voidmc.hearth.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Damage and repair (docs/LIVING_WORLD.md §2). The blueprint is the truth, so damage is where the world no longer matches
 * a built plot's blueprint: the repair module tracks it from events (player breaks, explosions, fire) and builders'
 * inspections, keeps a repair queue per settlement, restores abstract settlements when their chunks load, and lets
 * embodied builders claim repairs one block at a time. Implemented by the repair module. Server thread only. May be absent.
 */
public interface RepairService {
	/**
	 * The next damaged blueprint block in the settlement nearest {@code near}, reserved for this builder for a while
	 * (so two builders never fight over one block), or null if nothing needs mending.
	 */
	@Nullable RepairTask claim(Settlement settlement, UUID builder, BlockPos near);

	/** The builder restored the block (or gave up: {@code restored} false returns it to the queue). */
	void done(RepairTask task, boolean restored);

	/** Blocks waiting to be mended in a settlement. */
	int damaged(Settlement settlement);

	/**
	 * One block to put back: step {@code index} of stage {@code stage} of the plot's blueprint (place it exactly as the
	 * blueprint says, as builders do for construction).
	 */
	record RepairTask(long settlementId, int plotId, int stage, int index, BlockPos pos, long claimedAt) {
	}
}
