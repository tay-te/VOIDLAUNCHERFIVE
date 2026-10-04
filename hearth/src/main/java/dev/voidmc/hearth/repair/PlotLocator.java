package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Answers "which plot, which stage and which blueprint block is at this position" fast enough to sit behind every block
 * a player breaks and every block an explosion takes.
 *
 * <p>Two levels of cache, both built lazily: per settlement a grid of chunk → plots whose footprint touches it (rebuilt
 * after {@code gridTtl} ticks or when plots finish or are given up), and per (plot, chunk, stages done) a section: every
 * cell the plot's completed stages write in that chunk, read with {@link Blueprint#forEachInChunk} one stage at a time
 * so the last write wins, as it does in the world. Sections are kept in an LRU bounded by their total cells.
 *
 * <p>Only plots that stand count: {@link PlotState#BUILT} plots and the completed stages of plots being built.
 * Excavations ({@link ArchetypeCategory#EXTRACTION}) are construction run backwards and are never damaged; abandoned and
 * demolishing plots are nobody's to mend. Where plots overlap on purpose, the later plot wins, as in the sim's applier.
 * Server thread only.
 */
public final class PlotLocator {
	/** Where blueprints and archetype facts come from (the real services in game, doubles in tests). */
	public interface Source {
		@Nullable Blueprint blueprint(Settlement settlement, PlotView plot);

		/** Whether the plot is an excavation (cuts, not a building). */
		boolean extraction(PlotSpec spec);
	}

	/** The real services: the build module's cached blueprints on the world's terrain. */
	public static Source services(HearthWorld world) {
		return new Source() {
			@Override
			public @Nullable Blueprint blueprint(Settlement settlement, PlotView plot) {
				if (!HearthServices.hasBlueprints()) {
					return null;
				}
				try {
					return HearthServices.blueprints().blueprint(plot.spec(), world.terrain());
				} catch (RuntimeException e) {
					return null;
				}
			}

			@Override
			public boolean extraction(PlotSpec spec) {
				return isExtraction(spec);
			}
		};
	}

	static boolean isExtraction(PlotSpec spec) {
		if (spec.archetype().getPath().startsWith("extract/")) {
			return true;
		}
		if (!HearthServices.hasBlueprints()) {
			return false;
		}
		ArchetypeInfo info = HearthServices.blueprints().archetype(spec.archetype());
		return info != null && info.category() == ArchetypeCategory.EXTRACTION;
	}

	/** A blueprint cell found at a position. */
	public record Hit(Settlement settlement, PlotView plot, int stage, BlockState expected) {
	}

	/** One plot's completed cells in one chunk: position → stage << 32 | block state id. */
	static final class Section {
		final Long2LongOpenHashMap cells;

		Section(Long2LongOpenHashMap cells) {
			this.cells = cells;
		}

		int weight() {
			return Math.max(1, this.cells.size());
		}
	}

	private record SectionKey(long settlement, int plot, long chunk, int stagesDone) {
	}

	private static final class Grid {
		final Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
		final long builtAt;

		Grid(long builtAt) {
			this.builtAt = builtAt;
		}
	}

	private final Map<SectionKey, Section> sections = new LinkedHashMap<>(256, 0.75F, true);
	private final Map<Long, Grid> grids = new HashMap<>();
	private int maxCells;
	private int gridTtl;
	private int cells;
	private long hits;
	private long misses;
	private long builds;
	private long buildNanos;

	public PlotLocator(int maxCells, int gridTtl) {
		this.maxCells = maxCells;
		this.gridTtl = gridTtl;
	}

	public void configure(int maxCells, int gridTtl) {
		this.maxCells = maxCells;
		this.gridTtl = gridTtl;
		trim();
	}

	/** The plot cell at a position in a level, or null if no standing plot of any settlement has a block there. */
	public @Nullable Hit locate(HearthWorld world, BlockPos pos, long now) {
		Settlement s = world.settlementAt(pos);
		return s == null ? null : locate(s, services(world), pos, now);
	}

	/** The plot cell at a position in one settlement. */
	public @Nullable Hit locate(Settlement settlement, Source source, BlockPos pos, long now) {
		int cx = SectionPos.blockToSectionCoord(pos.getX());
		int cz = SectionPos.blockToSectionCoord(pos.getZ());
		int[] ids = grid(settlement, now).chunks.get(ChunkKeys.key(cx, cz));
		if (ids == null) {
			return null;
		}
		long key = pos.asLong();
		for (int id : ids) {
			PlotView plot = settlement.plot(id);
			if (plot == null || !standing(plot) || source.extraction(plot.spec())) {
				continue;
			}
			Section section = section(settlement, source, plot, cx, cz);
			if (section == null) {
				continue;
			}
			long v = section.cells.getOrDefault(key, Long.MIN_VALUE);
			if (v != Long.MIN_VALUE) {
				return new Hit(settlement, plot, (int) (v >>> 32), Block.stateById((int) v));
			}
		}
		return null;
	}

	/** Plots whose blocks stand: built, or with stages finished. */
	public static boolean standing(PlotView plot) {
		return plot.state() == PlotState.BUILT || plot.state() == PlotState.BUILDING && plot.stage() > 0;
	}

	/** The completed cells of a plot in one chunk (cached), or null if its blueprint cannot be had. */
	@Nullable Section section(Settlement settlement, Source source, PlotView plot, int chunkX, int chunkZ) {
		SectionKey key = new SectionKey(settlement.id(), plot.id(), ChunkKeys.key(chunkX, chunkZ), plot.stage());
		Section s = this.sections.get(key);
		if (s != null) {
			this.hits++;
			return s;
		}
		this.misses++;
		Blueprint bp = source.blueprint(settlement, plot);
		if (bp == null) {
			return null;
		}
		long t0 = System.nanoTime();
		Long2LongOpenHashMap map = new Long2LongOpenHashMap();
		int done = Math.min(plot.stage(), bp.stageCount());
		for (int st = 0; st < done; st++) {
			long stageBits = (long) st << 32;
			bp.forEachInChunk(chunkX, chunkZ, st, st + 1, (x, y, z, state, tag) -> map.put(BlockPos.asLong(x, y, z), stageBits | Block.getId(state) & 0xFFFF_FFFFL));
		}
		map.trim();
		s = new Section(map);
		this.sections.put(key, s);
		this.cells += s.weight();
		this.builds++;
		this.buildNanos += System.nanoTime() - t0;
		trim();
		return s;
	}

	private void trim() {
		Iterator<Map.Entry<SectionKey, Section>> it = this.sections.entrySet().iterator();
		// the newest section always stays, however large: it is the one being asked for
		while (this.cells > this.maxCells && this.sections.size() > 1 && it.hasNext()) {
			this.cells -= it.next().getValue().weight();
			it.remove();
		}
	}

	private Grid grid(Settlement settlement, long now) {
		Grid g = this.grids.get(settlement.id());
		if (g == null || now - g.builtAt > this.gridTtl || now < g.builtAt) {
			g = buildGrid(settlement, now);
			this.grids.put(settlement.id(), g);
		}
		return g;
	}

	private static Grid buildGrid(Settlement settlement, long now) {
		Grid g = new Grid(now);
		Long2ObjectOpenHashMap<IntArrayList> lists = new Long2ObjectOpenHashMap<>();
		for (PlotView p : settlement.plots()) {
			PlotSpec s = p.spec();
			// footprint plus a margin for eaves, signs and footings that step a block outside it
			int x0 = SectionPos.blockToSectionCoord(s.origin().getX() - 2);
			int x1 = SectionPos.blockToSectionCoord(s.origin().getX() + s.sizeX() + 1);
			int z0 = SectionPos.blockToSectionCoord(s.origin().getZ() - 2);
			int z1 = SectionPos.blockToSectionCoord(s.origin().getZ() + s.sizeZ() + 1);
			for (int cx = x0; cx <= x1; cx++) {
				for (int cz = z0; cz <= z1; cz++) {
					lists.computeIfAbsent(ChunkKeys.key(cx, cz), k -> new IntArrayList()).add(p.id());
				}
			}
		}
		lists.forEach((k, v) -> {
			int[] ids = v.toIntArray();
			java.util.Arrays.sort(ids);
			// later plots first: where plots overlap on purpose, the later one's block is the one that stands
			for (int i = 0, j = ids.length - 1; i < j; i++, j--) {
				int t = ids[i];
				ids[i] = ids[j];
				ids[j] = t;
			}
			g.chunks.put((long) k, ids);
		});
		return g;
	}

	/**
	 * Forget a settlement's grid (a plot finished or was given up): it is rebuilt on the next lookup, cheaply. Sections
	 * stay: they are keyed by stages done, so a plot's change of progress already misses them.
	 */
	public void invalidateGrid(long settlementId) {
		this.grids.remove(settlementId);
	}

	/** Forget a settlement's grid and sections (its plots were rebuilt from scratch, tests). */
	public void invalidate(long settlementId) {
		this.grids.remove(settlementId);
		Iterator<Map.Entry<SectionKey, Section>> it = this.sections.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<SectionKey, Section> e = it.next();
			if (e.getKey().settlement == settlementId) {
				this.cells -= e.getValue().weight();
				it.remove();
			}
		}
	}

	public void clear() {
		this.grids.clear();
		this.sections.clear();
		this.cells = 0;
	}

	/**
	 * Every completed cell of a plot, chunk by chunk over its blueprint's bounds (inspections walk these). Returns false if
	 * the blueprint cannot be had.
	 */
	public boolean forEachCell(Settlement settlement, Source source, PlotView plot, CellVisitor visitor) {
		Blueprint bp = source.blueprint(settlement, plot);
		if (bp == null) {
			return false;
		}
		BoundingBox b = bp.bounds();
		for (int cx = SectionPos.blockToSectionCoord(b.minX()); cx <= SectionPos.blockToSectionCoord(b.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(b.minZ()); cz <= SectionPos.blockToSectionCoord(b.maxZ()); cz++) {
				Section s = section(settlement, source, plot, cx, cz);
				if (s == null) {
					continue;
				}
				for (Long2LongOpenHashMap.Entry e : s.cells.long2LongEntrySet()) {
					long v = e.getLongValue();
					visitor.visit(e.getLongKey(), (int) (v >>> 32), Block.stateById((int) v));
				}
			}
		}
		return true;
	}

	/** The blueprint cell of one plot at a position, or null (the plot does not build there, or not yet). */
	public @Nullable BlockState cellOf(Settlement settlement, Source source, PlotView plot, long pos) {
		Section s = section(settlement, source, plot, SectionPos.blockToSectionCoord(BlockPos.getX(pos)), SectionPos.blockToSectionCoord(BlockPos.getZ(pos)));
		if (s == null) {
			return null;
		}
		long v = s.cells.getOrDefault(pos, Long.MIN_VALUE);
		return v == Long.MIN_VALUE ? null : Block.stateById((int) v);
	}

	@FunctionalInterface
	public interface CellVisitor {
		void visit(long pos, int stage, BlockState expected);
	}

	/**
	 * The index of the step of {@code stage} that last writes {@code pos} (what stands there when the stage is done), or -1.
	 * Walks the stage once; done only when a builder takes the repair.
	 */
	public static int resolveIndex(Blueprint bp, int stage, BlockPos pos) {
		if (stage < 0 || stage >= bp.stageCount()) {
			return -1;
		}
		int[] found = {-1};
		int[] at = {0};
		int n = bp.stepCount(stage);
		for (int i = 0; i < n; i++) {
			at[0] = i;
			bp.step(stage, i, (x, y, z, state, tag) -> {
				if (x == pos.getX() && y == pos.getY() && z == pos.getZ()) {
					found[0] = at[0];
				}
			});
		}
		return found[0];
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "%d sections, %d cells (max %d), %d grids, %d hits / %d misses, %d built in %.1f ms", this.sections.size(),
			this.cells, this.maxCells, this.grids.size(), this.hits, this.misses, this.builds, this.buildNanos / 1e6);
	}

	public int cells() {
		return this.cells;
	}
}
