package dev.voidmc.hearth.npc.work;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;

/**
 * Every scaffold block a builder has put up and not yet taken down, saved with the settlement. Builders remove their
 * own as they finish; a builder folded away mid-job has its scaffolding removed on the spot when the chunk is loaded,
 * and anything left by a crash is cleared the next time the settlement is embodied. Scaffolding is never a blueprint
 * step, so nothing else would ever remove it, and a town must never be left wearing it.
 */
public final class ScaffoldLedger {
	public static final Codec<ScaffoldLedger> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG.listOf())
		.xmap(ScaffoldLedger::new, l -> {
			Map<UUID, List<Long>> out = new LinkedHashMap<>();
			l.byBuilder.forEach((k, v) -> out.put(k, List.copyOf(v)));
			return out;
		});

	public static final HearthDataKey<ScaffoldLedger> KEY = HearthDataKey.register(Hearth.id("npc/scaffolds"),
		HearthDataKey.Scope.SETTLEMENT, CODEC, ScaffoldLedger::new);

	/** Positions (packed) per builder, in the order placed. */
	private final Map<UUID, List<Long>> byBuilder = new LinkedHashMap<>();

	public ScaffoldLedger() {
	}

	private ScaffoldLedger(Map<UUID, List<Long>> saved) {
		saved.forEach((k, v) -> this.byBuilder.put(k, new ArrayList<>(v)));
	}

	public void add(UUID builder, BlockPos pos) {
		this.byBuilder.computeIfAbsent(builder, k -> new ArrayList<>()).add(pos.asLong());
	}

	public void remove(UUID builder, BlockPos pos) {
		List<Long> list = this.byBuilder.get(builder);
		if (list != null) {
			list.remove(pos.asLong());
			if (list.isEmpty()) {
				this.byBuilder.remove(builder);
			}
		}
	}

	public List<BlockPos> of(UUID builder) {
		List<Long> list = this.byBuilder.get(builder);
		List<BlockPos> out = new ArrayList<>();
		if (list != null) {
			for (long l : list) {
				out.add(BlockPos.of(l));
			}
		}
		return out;
	}

	public boolean isEmpty() {
		return this.byBuilder.isEmpty();
	}

	public int size() {
		int n = 0;
		for (List<Long> l : this.byBuilder.values()) {
			n += l.size();
		}
		return n;
	}

	/**
	 * Takes down a builder's scaffolding (top first) wherever its chunk is loaded; what is not loaded stays listed for
	 * later. Returns how many blocks were removed.
	 */
	public int clear(ServerLevel level, UUID builder) {
		List<Long> list = this.byBuilder.get(builder);
		if (list == null) {
			return 0;
		}
		int removed = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			BlockPos pos = BlockPos.of(list.get(i));
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
				level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			list.remove(i);
			removed++;
		}
		if (list.isEmpty()) {
			this.byBuilder.remove(builder);
		}
		return removed;
	}

	/** Takes down everything listed whose chunk is loaded except the scaffolding of builders still at work. */
	public int clearOrphans(ServerLevel level, java.util.Set<UUID> working) {
		int removed = 0;
		Iterator<UUID> it = new ArrayList<>(this.byBuilder.keySet()).iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			if (!working.contains(id)) {
				removed += clear(level, id);
			}
		}
		return removed;
	}
}
