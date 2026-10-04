package dev.voidmc.hearth.law.own;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Which plot a block lies in, fast: each settlement's plots bucketed by chunk, rebuilt when its plots change (their
 * number or states) and otherwise at most every half minute. A block is in a plot when it lies inside the footprint,
 * from a few blocks under the floor (cellars, foundations) to the plot's height. Where plots overlap (a house on a
 * terrace, a stall in a square, a rebuild) the most specific wins: private before public, then the smaller footprint.
 */
public final class PlotIndex {
	private static final int BELOW_FLOOR = 6;
	private static final long REBUILD_TICKS = 600;
	private static final Map<Long, PlotIndex> CACHE = new HashMap<>();

	private final Long2ObjectOpenHashMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();
	private final int signature;
	private final long builtAt;

	private PlotIndex(Settlement settlement, int signature, long now) {
		this.signature = signature;
		this.builtAt = now;
		Long2ObjectOpenHashMap<List<Integer>> lists = new Long2ObjectOpenHashMap<>();
		for (PlotView plot : settlement.plots()) {
			PlotSpec s = plot.spec();
			int cx0 = s.origin().getX() >> 4;
			int cz0 = s.origin().getZ() >> 4;
			int cx1 = s.origin().getX() + s.sizeX() - 1 >> 4;
			int cz1 = s.origin().getZ() + s.sizeZ() - 1 >> 4;
			for (int cx = cx0; cx <= cx1; cx++) {
				for (int cz = cz0; cz <= cz1; cz++) {
					lists.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(plot.id());
				}
			}
		}
		lists.forEach((k, v) -> this.byChunk.put((long) k, v.stream().mapToInt(Integer::intValue).toArray()));
	}

	/** The index for a settlement, rebuilt if its plots changed. {@code now} is the game time. Server thread only. */
	public static PlotIndex of(Settlement settlement, long now) {
		int signature = signature(settlement);
		PlotIndex index = CACHE.get(settlement.id());
		if (index == null || index.signature != signature || now - index.builtAt > REBUILD_TICKS || now < index.builtAt) {
			index = new PlotIndex(settlement, signature, now);
			CACHE.put(settlement.id(), index);
		}
		return index;
	}

	/** Forgets every settlement (server stop). */
	public static void clear() {
		CACHE.clear();
	}

	public static void forget(long settlementId) {
		CACHE.remove(settlementId);
	}

	private static int signature(Settlement settlement) {
		int h = settlement.plots().size();
		for (PlotView p : settlement.plots()) {
			h = h * 31 + p.id() * 7 + p.state().ordinal() + (p.owner() == null ? 0 : 1);
		}
		return h;
	}

	/** The most specific plot containing {@code pos}, or null. */
	public @Nullable PlotView plotAt(Settlement settlement, BlockPos pos) {
		int[] ids = this.byChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		if (ids == null) {
			return null;
		}
		PlotView best = null;
		int bestRank = Integer.MAX_VALUE;
		for (int id : ids) {
			PlotView plot = settlement.plot(id);
			if (plot == null || plot.state() == PlotState.PLANNED || !contains(plot.spec(), pos)) {
				continue;
			}
			ArchetypeCategory category = OwnerRules.category(plot.spec().archetype());
			int rank = (OwnerRules.isPublic(category) ? 1 << 24 : 0) + plot.spec().sizeX() * plot.spec().sizeZ();
			if (rank < bestRank) {
				best = plot;
				bestRank = rank;
			}
		}
		return best;
	}

	/** Whether {@code pos} lies in the plot's footprint, between a little under the floor and its height. */
	public static boolean contains(PlotSpec s, BlockPos pos) {
		return pos.getX() >= s.origin().getX() && pos.getX() < s.origin().getX() + s.sizeX()
			&& pos.getZ() >= s.origin().getZ() && pos.getZ() < s.origin().getZ() + s.sizeZ()
			&& pos.getY() >= s.floorY() - BELOW_FLOOR && pos.getY() <= s.floorY() + s.maxHeight();
	}

	/** Whether {@code pos} is inside the plot's walls rather than in its yard: away from the footprint's edge and under the eaves. */
	public static boolean indoors(PlotSpec s, BlockPos pos) {
		return contains(s, pos) && pos.getX() > s.origin().getX() && pos.getX() < s.origin().getX() + s.sizeX() - 1
			&& pos.getZ() > s.origin().getZ() && pos.getZ() < s.origin().getZ() + s.sizeZ() - 1;
	}
}
