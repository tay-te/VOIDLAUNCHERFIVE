package dev.voidmc.hearth.npc.work.field;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Setting a dressing's block (a timber stack, a laundry post, a shutter, a camp's fire) without the server re-planning
 * every walker's path at once.
 *
 * <p>Why: {@code ServerLevel.sendBlockUpdated} (what a set with {@link Block#UPDATE_CLIENTS} calls) asks every navigating
 * mob of the level whether its path passes near the block, and recomputes those paths there and then; a path is only
 * recomputed once in 20 ticks, so the first block set in a pass near walking folk paid for them all (7-25 ms at a logging
 * camp's timber stack, ~1 ms a laundry yard; docs/requests/lwA4.md). A dressing is small and off the folk's ways: the
 * block goes to the clients by the chunk's own change list, the path-type cache forgets it, and anyone who walks into it
 * finds their way round as they do round any obstacle.
 */
public final class QuietBlocks {
	private QuietBlocks() {
	}

	/** {@code level.setBlock(pos, state, flags)}, sent to the clients without re-planning paths. */
	public static boolean set(ServerLevel level, BlockPos pos, BlockState state, int flags) {
		boolean changed = level.setBlock(pos, state, flags & ~Block.UPDATE_CLIENTS);
		if (changed && (flags & Block.UPDATE_CLIENTS) != 0) {
			level.getChunkSource().blockChanged(pos);
			level.getPathTypeCache().invalidate(pos);
		}
		return changed;
	}
}
