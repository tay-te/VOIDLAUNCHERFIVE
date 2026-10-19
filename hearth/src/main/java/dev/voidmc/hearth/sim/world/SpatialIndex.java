package dev.voidmc.hearth.sim.world;

import dev.voidmc.hearth.sim.apply.ChunkApplier;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Where the live settlements are, for the questions asked every tick: which plots touch this chunk (the applier, chunk
 * loads), which settlements are near this player (embodiment, the arrival banner), and which columns are planned (the
 * world generation guard, asked from worker threads, hence a concurrent map of immutable masks). Rebuilt per settlement
 * when its plots change, which is rare next to how often it is read.
 */
public final class SpatialIndex implements ChunkApplier.PlotIndex {
	/** Cells for settlement lookups: 256 blocks. */
	private static final int CELL = 8;

	private final Long2ObjectOpenHashMap<List<ChunkApplier.PlotRef>> plotsByChunk = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<LongArrayList> chunksBySettlement = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<LongArrayList> settlementsByCell = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<LongArrayList> cellsBySettlement = new Long2ObjectOpenHashMap<>();
	private final Map<Long, long[]> planned = new ConcurrentHashMap<>();
	/** Each settlement's own planned columns by chunk, and which settlements plan in each chunk: {@link #planned} is their union. */
	private final Long2ObjectOpenHashMap<Long2ObjectMap<long[]>> masksBySettlement = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<LongArrayList> maskOwners = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<Map<Integer, PlotMask>> plotMasks = new Long2ObjectOpenHashMap<>();
	private boolean updating;
	private final int reach;
	private final int guardMargin;

	private record PlotMask(dev.voidmc.hearth.api.PlotSpec spec, Long2ObjectMap<long[]> masks) {
	}

	/**
	 * @param reach how far around a settlement's bounds it should be found by {@link #near} (embodiment radius plus
	 *              hysteresis)
	 */
	public SpatialIndex(int reach, int guardMargin) {
		this.reach = reach;
		this.guardMargin = guardMargin;
	}

	@Override
	public List<ChunkApplier.PlotRef> refs(long chunk) {
		List<ChunkApplier.PlotRef> l = this.plotsByChunk.get(chunk);
		return l == null ? List.of() : l;
	}

	public boolean hasPlots(long chunk) {
		return this.plotsByChunk.containsKey(chunk);
	}

	/** Re-indexes everything about one settlement. */
	public void update(SimSettlement s) {
		this.updating = true;
		try {
			remove(s.id());
		} finally {
			this.updating = false;
		}
		LongArrayList chunks = new LongArrayList();
		for (SimPlot p : s.plots()) {
			BoundingBox r = p.reach();
			for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++) {
				for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++) {
					long key = ChunkPos.pack(cx, cz);
					this.plotsByChunk.computeIfAbsent(key, k -> new ArrayList<>(2)).add(new ChunkApplier.PlotRef(s, p.id()));
					chunks.add(key);
				}
			}
		}
		this.chunksBySettlement.put(s.id(), chunks);
		BoundingBox b = s.bounds();
		LongArrayList cells = new LongArrayList();
		for (int cx = (b.minX() - this.reach) >> 4 >> CELL - 4; cx <= (b.maxX() + this.reach) >> 4 >> CELL - 4; cx++) {
			for (int cz = (b.minZ() - this.reach) >> 4 >> CELL - 4; cz <= (b.maxZ() + this.reach) >> 4 >> CELL - 4; cz++) {
				long key = ChunkPos.pack(cx, cz);
				this.settlementsByCell.computeIfAbsent(key, k -> new LongArrayList(1)).add(s.id());
				cells.add(key);
			}
		}
		this.cellsBySettlement.put(s.id(), cells);
		Long2ObjectMap<long[]> masks = PlotColumns.masks();
		// each plot's own columns are worked out once and kept: a city's re-index merges a few thousand masks instead of
		// walking every column of every plot again each time a plot is added
		Map<Integer, PlotMask> cache = this.plotMasks.computeIfAbsent(s.id(), k -> new java.util.HashMap<>());
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (SimPlot p : s.plots()) {
			// built ones too: a live town grows abstractly into land nobody has generated yet, and the trees and
			// boulders world generation would put there must not stand in its streets and houses when it is
			if (p.state() == dev.voidmc.hearth.api.PlotState.ABANDONED) {
				continue;
			}
			seen.add(p.id());
			PlotMask m = cache.get(p.id());
			if (m == null || m.spec != p.spec()) {
				Long2ObjectMap<long[]> own = PlotColumns.masks();
				PlotColumns.mark(p.spec(), this.guardMargin, own);
				m = new PlotMask(p.spec(), own);
				cache.put(p.id(), m);
			}
			m.masks.forEach((k, v) -> {
				long[] into = masks.get((long) k);
				if (into == null) {
					masks.put((long) k, v.clone());
				} else {
					for (int i = 0; i < 4; i++) {
						into[i] |= v[i];
					}
				}
			});
		}
		cache.keySet().retainAll(seen);
		setMasks(s.id(), masks);
	}

	/**
	 * Replaces a settlement's planned columns (null: it is gone) and works out the union again in every chunk that changed,
	 * from all the settlements planning there: two towns whose margins share a chunk both keep their ground guarded.
	 */
	private void setMasks(long id, @Nullable Long2ObjectMap<long[]> masks) {
		Long2ObjectMap<long[]> old = masks == null ? this.masksBySettlement.remove(id) : this.masksBySettlement.put(id, masks);
		it.unimi.dsi.fastutil.longs.LongOpenHashSet touched = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		if (old != null) {
			for (long key : old.keySet()) {
				LongArrayList owners = this.maskOwners.get(key);
				if (owners != null) {
					owners.rem(id);
				}
				touched.add(key);
			}
		}
		if (masks != null) {
			for (long key : masks.keySet()) {
				this.maskOwners.computeIfAbsent(key, k -> new LongArrayList(1)).add(id);
				touched.add(key);
			}
		}
		for (long key : touched) {
			LongArrayList owners = this.maskOwners.get(key);
			if (owners == null || owners.isEmpty()) {
				this.maskOwners.remove(key);
				this.planned.remove(key);
				continue;
			}
			long[] union = new long[4];
			for (long owner : owners) {
				Long2ObjectMap<long[]> theirs = this.masksBySettlement.get(owner);
				long[] v = theirs == null ? null : theirs.get(key);
				if (v != null) {
					for (int i = 0; i < 4; i++) {
						union[i] |= v[i];
					}
				}
			}
			// a fresh array each time: world generation threads read these without locks
			this.planned.put(key, union);
		}
	}

	public void remove(long id) {
		LongArrayList chunks = this.chunksBySettlement.remove(id);
		if (chunks != null) {
			for (long key : chunks) {
				List<ChunkApplier.PlotRef> l = this.plotsByChunk.get(key);
				if (l != null) {
					l.removeIf(r -> r.settlement().id() == id);
					if (l.isEmpty()) {
						this.plotsByChunk.remove(key);
					}
				}
			}
		}
		LongArrayList cells = this.cellsBySettlement.remove(id);
		if (cells != null) {
			for (long key : cells) {
				LongArrayList l = this.settlementsByCell.get(key);
				if (l != null) {
					l.rem(id);
					if (l.isEmpty()) {
						this.settlementsByCell.remove(key);
					}
				}
			}
		}
		if (!this.updating) {
			// gone from the world (an update replaces its masks itself, with no moment of unguarded ground)
			this.plotMasks.remove(id);
			setMasks(id, null);
		}
	}

	/** Settlement ids whose bounds, grown by the index's reach, may contain this block. */
	public LongArrayList near(int x, int z) {
		LongArrayList l = this.settlementsByCell.get(ChunkPos.pack(x >> CELL, z >> CELL));
		return l == null ? new LongArrayList() : l;
	}

	/** Whether a live settlement plans to build on this column (thread-safe). */
	public boolean isPlanned(int x, int z) {
		long[] mask = this.planned.get(ChunkPos.pack(x >> 4, z >> 4));
		if (mask == null) {
			return false;
		}
		int bit = (z & 15) << 4 | x & 15;
		return (mask[bit >> 6] >>> (bit & 63) & 1L) != 0;
	}

	public int indexedChunks() {
		return this.plotsByChunk.size();
	}
}
