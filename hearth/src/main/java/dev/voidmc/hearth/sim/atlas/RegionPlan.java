package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * What the atlas decided for one 1024 × 1024 region, deterministically from the seed: whether a settlement was founded
 * there and, if so, its site, culture, age and the whole history of what it built. Immutable once made; shared between
 * world generation threads.
 *
 * @param site      null for a region nobody settled
 * @param history   the settlement as its history left it (adoption copies it; never mutate)
 * @param plots     every historic plot with completed stages, what world generation writes
 * @param byChunk   chunk ({@code ChunkPos.pack}) to indices into {@link #plots}
 * @param planned   chunk to a 256-bit mask (4 longs, bit {@code z*16+x}) of columns planned for plots and roads,
 *                  including the guard margin: no tree or boulder grows there
 * @param bounds    everything planned, or null
 * @param millis    how long the region took to decide (site survey plus history)
 */
public record RegionPlan(int regionX, int regionZ, @Nullable Site site, @Nullable SimSettlement history, List<HistoricPlot> plots,
	Long2ObjectMap<int[]> byChunk, Long2ObjectMap<long[]> planned, @Nullable BoundingBox bounds, double millis, double historyMillis) {

	/**
	 * @param center  the founding point (its well or fire), on the ground
	 * @param age     sim days the settlement had lived when the world began
	 * @param biome   the biome at the centre, for logs and the atlas map
	 */
	public record Site(long settlementId, String name, Culture culture, BlockPos center, int age, String biome, double score) {
	}

	/** A plot world generation writes: its first {@code completed} stages. */
	public record HistoricPlot(int id, PlotSpec spec, int completed, int stageCount, boolean road) {
	}

	public boolean settled() {
		return this.site != null;
	}

	public boolean isPlanned(int x, int z) {
		long[] mask = this.planned.get(net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4));
		if (mask == null) {
			return false;
		}
		int bit = (z & 15) << 4 | x & 15;
		return (mask[bit >> 6] >>> (bit & 63) & 1L) != 0;
	}
}
