package dev.voidmc.mettle.character;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Anti-farm (CHARACTER.md §3.3): which XP-bearing blocks in a chunk were placed by a player. Breaking those gives no
 * XP and no drop perks, so placing and mining the same ore is worthless. Only blocks that give Mining XP are recorded.
 */
public final class PlacedBlocks {
	public static final AttachmentType<Set<Long>> PLACED = AttachmentRegistry.create(Mettle.id("character/placed"), b -> b
		.persistent(Codec.LONG.listOf().xmap(Set::copyOf, List::copyOf)));

	private PlacedBlocks() {
	}

	public static void init() {
	}

	public static void mark(Level level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Set<Long> set = new HashSet<>(chunk.getAttachedOrElse(PLACED, Set.of()));
		if (set.add(pos.asLong())) {
			chunk.setAttached(PLACED, Set.copyOf(set));
		}
	}

	/** Whether the block was placed by a player; forgets it either way, since it is being broken. */
	public static boolean takePlaced(Level level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Set<Long> set = chunk.getAttached(PLACED);
		if (set == null || !set.contains(pos.asLong())) {
			return false;
		}
		Set<Long> next = new HashSet<>(set);
		next.remove(pos.asLong());
		chunk.setAttached(PLACED, next.isEmpty() ? null : Set.copyOf(next));
		return true;
	}

	public static boolean isPlaced(Level level, BlockPos pos) {
		Set<Long> set = level.getChunkAt(pos).getAttached(PLACED);
		return set != null && set.contains(pos.asLong());
	}
}
