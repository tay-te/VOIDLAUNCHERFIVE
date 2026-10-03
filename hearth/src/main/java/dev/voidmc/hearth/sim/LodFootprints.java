package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Settlements as VOID LOD sees them at the horizon: for any column, whether a finished building stands there and its
 * silhouette (wall colour under roof colour, eave and ridge heights), read from {@link Silhouette}s rather than blocks.
 * Read-only and thread-safe for LOD's workers; versioned per 1024-block region so LOD rebuilds only tiles whose region
 * changed. Built plots of live settlements come from their records (kept here as they finish), and the finished plots of
 * histories not yet adopted come from the atlas.
 *
 * <pre>
 *   LodFootprints f = LodFootprints.of(level);       // null where Hearth does not run
 *   long v = f.version(x >> 10, z >> 10);            // changed since the tile was built?
 *   int[] out = new int[3];
 *   if (f.sample(x, z, out)) { wall = out[0]; roof = out[1]; eave = out[2] >> 16; ridge = (short) out[2]; }
 * </pre>
 */
public final class LodFootprints {
	/** A finished building. Its silhouette is worked out on first use, by whichever thread draws it (LOD's workers). */
	private static final class Entry {
		final long settlement;
		final int plot;
		final PlotSpec spec;
		private volatile @Nullable Silhouette silhouette;

		Entry(long settlement, int plot, PlotSpec spec) {
			this.settlement = settlement;
			this.plot = plot;
			this.spec = spec;
		}

		@Nullable Silhouette silhouette(TerrainProbe terrain) {
			Silhouette s = this.silhouette;
			if (s == null) {
				try {
					s = HearthServices.blueprints().blueprint(this.spec, terrain).silhouette();
				} catch (RuntimeException e) {
					// an archetype that no longer exists draws nothing
					return null;
				}
				this.silhouette = s;
			}
			return s;
		}
	}

	/** A region's columns: per chunk, 256 × {wall, roof, eave << 16 | ridge & 0xFFFF}. */
	private record Snapshot(long version, Long2ObjectMap<int[]> chunks) {
	}

	private static final class Region {
		final List<Entry> live = new CopyOnWriteArrayList<>();
		volatile long version = 1;
		volatile @Nullable Snapshot snapshot;
	}

	private final Atlas atlas;
	private final TerrainProbe terrain;
	private final Map<Long, Region> regions = new ConcurrentHashMap<>();
	private final Set<Long> adopted = ConcurrentHashMap.newKeySet();

	public LodFootprints(Atlas atlas, TerrainProbe terrain) {
		this.atlas = atlas;
		this.terrain = terrain;
	}

	/** The footprints of a level, or null if Hearth does not run there. */
	public static @Nullable LodFootprints of(ServerLevel level) {
		var w = SimServer.world(level);
		return w == null ? null : w.footprints();
	}

	private Region region(long key) {
		return this.regions.computeIfAbsent(key, k -> new Region());
	}

	// ---------------------------------------------------------------- updates (server thread)

	/** Per live settlement: which of its plots are drawn, and in which region (so updates never scan every region). */
	private final Map<Long, Map<Integer, Long>> drawn = new ConcurrentHashMap<>();

	/** A plot finished: it now shows at the horizon. Cheap: no blueprint is generated here (see {@link Entry}). */
	public void built(SimSettlement s, SimPlot p) {
		if (PlotColumns.isLine(p.spec())) {
			return;
		}
		Map<Integer, Long> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		long key = key(p.spec());
		Long was = mine.put(p.id(), key);
		if (was != null) {
			Region old = this.regions.get(was);
			if (old != null) {
				old.live.removeIf(e -> e.settlement == s.id() && e.plot == p.id());
				bump(old);
			}
		}
		Region r = region(key);
		r.live.add(new Entry(s.id(), p.id(), p.spec()));
		bump(r);
	}

	/** Re-reads a settlement's standing plots (after adoption, removals, or loading): O(plots), no blueprints. */
	public void refresh(SimSettlement s) {
		if (s.originRegion() != SimSettlement.NO_REGION) {
			this.adopted.add(s.originRegion());
			Region r = this.regions.get(s.originRegion());
			if (r != null) {
				bump(r);
			}
		}
		Map<Integer, Long> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		var it = mine.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			SimPlot p = s.plot(en.getKey());
			if (p == null || !p.isStanding() || key(p.spec()) != en.getValue()) {
				it.remove();
				Region r = this.regions.get(en.getValue());
				if (r != null) {
					int id = en.getKey();
					r.live.removeIf(e -> e.settlement == s.id() && e.plot == id);
					bump(r);
				}
			}
		}
		for (SimPlot p : s.plots()) {
			if (p.isStanding() && !mine.containsKey(p.id())) {
				built(s, p);
			}
		}
	}

	private static void bump(Region r) {
		r.version++;
		r.snapshot = null;
	}

	private static long key(PlotSpec spec) {
		int cx = spec.origin().getX() + spec.sizeX() / 2;
		int cz = spec.origin().getZ() + spec.sizeZ() / 2;
		return Atlas.regionKey(Atlas.region(cx), Atlas.region(cz));
	}

	// ---------------------------------------------------------------- queries (any thread)

	/** Changes whenever anything in the region would draw differently. */
	public long version(int regionX, int regionZ) {
		Region r = this.regions.get(Atlas.regionKey(regionX, regionZ));
		return r == null ? 0 : r.version;
	}

	/**
	 * Fills {@code out} with the column's {wall colour, roof colour, eave << 16 | ridge} and returns true if a finished
	 * building stands there.
	 */
	public boolean sample(int x, int z, int[] out) {
		int rx = Atlas.region(x);
		int rz = Atlas.region(z);
		Snapshot snap = snapshot(rx, rz);
		int[] cols = snap.chunks.get(ChunkPos.pack(x >> 4, z >> 4));
		if (cols == null) {
			return false;
		}
		int i = ((z & 15) << 4 | x & 15) * 3;
		if (cols[i] == 0 && cols[i + 1] == 0 && cols[i + 2] == 0) {
			return false;
		}
		out[0] = cols[i];
		out[1] = cols[i + 1];
		out[2] = cols[i + 2];
		return true;
	}

	private Snapshot snapshot(int rx, int rz) {
		long key = Atlas.regionKey(rx, rz);
		Region r = region(key);
		Snapshot snap = r.snapshot;
		long v = r.version;
		if (snap != null && snap.version == v) {
			return snap;
		}
		Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
		for (Entry e : r.live) {
			Silhouette sil = e.silhouette(this.terrain);
			if (sil != null) {
				paint(chunks, e.spec, sil);
			}
		}
		if (!this.adopted.contains(key) && this.atlas.enabled()) {
			RegionPlan plan = this.atlas.plan(rx, rz);
			for (RegionPlan.HistoricPlot hp : plan.plots()) {
				if (hp.completed() >= hp.stageCount() && !hp.road()) {
					try {
						paint(chunks, hp.spec(), HearthServices.blueprints().blueprint(hp.spec(), this.terrain).silhouette());
					} catch (RuntimeException ignored) {
						// an archetype that no longer exists draws nothing
					}
				}
			}
		}
		snap = new Snapshot(v, chunks);
		r.snapshot = snap;
		return snap;
	}

	private static void paint(Long2ObjectOpenHashMap<int[]> chunks, PlotSpec spec, Silhouette s) {
		int packed = s.eaveY() << 16 | s.ridgeY() & 0xFFFF;
		PlotColumns.forEach(spec, 0, (x, z) -> {
			int[] cols = chunks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new int[256 * 3]);
			int i = ((z & 15) << 4 | x & 15) * 3;
			cols[i] = s.wallColor() | 0xFF00_0000;
			cols[i + 1] = s.roofColor() | 0xFF00_0000;
			cols[i + 2] = packed;
		});
	}
}
