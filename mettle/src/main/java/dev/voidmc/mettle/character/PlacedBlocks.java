package dev.voidmc.mettle.character;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Anti-farm (CHARACTER.md §3.3): which XP-bearing blocks in a chunk were placed by a player. Breaking those gives no
 * XP and no drop perks, so placing and mining the same ore is worthless. Only blocks that give Mining XP are recorded.
 */
public final class PlacedBlocks {
	public static final AttachmentType<Set<Long>> PLACED = AttachmentRegistry.create(Mettle.id("character/placed"), b -> b
		.persistent(Codec.LONG.listOf().xmap(Set::copyOf, List::copyOf)));

	/**
	 * Marks taken this tick. Fabric's {@code PlayerBlockBreakEvents.AFTER} (where trees take the mark) fires before the
	 * block's loot is rolled, so {@link #isPlaced} still answers yes for the drop perks that follow in the same tick.
	 */
	private static final Set<TakenAt> JUST_TAKEN = new HashSet<>();

	private record TakenAt(ResourceKey<Level> dimension, long pos) {
	}

	private PlacedBlocks() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> JUST_TAKEN.clear());
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
		if (!level.isClientSide()) {
			JUST_TAKEN.add(new TakenAt(level.dimension(), pos.asLong()));
		}
		return true;
	}

	/** Whether the block was placed by a player, or was one taken earlier this tick (its drops are being rolled). */
	public static boolean isPlaced(Level level, BlockPos pos) {
		if (!JUST_TAKEN.isEmpty() && JUST_TAKEN.contains(new TakenAt(level.dimension(), pos.asLong()))) {
			return true;
		}
		Set<Long> set = level.getChunkAt(pos).getAttached(PLACED);
		return set != null && set.contains(pos.asLong());
	}
}
