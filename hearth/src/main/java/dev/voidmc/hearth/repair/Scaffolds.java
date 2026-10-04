package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.Settlement;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;

/**
 * Takes down scaffolding builders put up for repairs and did not take down themselves (folded away mid-job, a crash).
 * Every scaffold block is listed in the settlement's {@link RepairQueue} as it goes up, so a town is never left wearing
 * one: what is loaded comes down at once, the rest when the settlement is next embodied.
 */
public final class Scaffolds {
	private Scaffolds() {
	}

	/** Takes down one builder's scaffolding where it is loaded; returns how many blocks. */
	public static int clear(ServerLevel level, Settlement s, RepairQueue q, UUID builder) {
		LongArrayList list = q.scaffolds().get(builder);
		if (list == null) {
			return 0;
		}
		int removed = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			BlockPos pos = BlockPos.of(list.getLong(i));
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
				level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			list.removeLong(i);
			removed++;
		}
		if (list.isEmpty()) {
			q.scaffolds().remove(builder);
		}
		if (removed > 0) {
			s.markDirty();
		}
		return removed;
	}

	/** Takes down every builder's scaffolding where it is loaded. */
	public static int clearAll(ServerLevel level, Settlement s, RepairQueue q) {
		int n = 0;
		for (UUID id : new ArrayList<>(q.scaffolds().keySet())) {
			n += clear(level, s, q, id);
		}
		return n;
	}
}
