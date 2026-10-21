package dev.voidmc.hearth.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The plan module: where a settlement puts things. Registered in {@link HearthServices}.
 *
 * <p>Both methods must be deterministic for a given settlement state and random, and must not touch the world — only
 * the terrain probe — because they run during world generation (a settlement's history is planned when its region is
 * first generated) as well as on the server thread. Keep {@link #next} to a few milliseconds.
 *
 * <p>The planner keeps its own state (road network, districts, layout type) in the settlement's data under its own
 * {@link HearthDataKey}.
 */
public interface SettlementPlanner {
	/**
	 * Lays out a new settlement: the first plots, in build order (a first road, a camp or the first homes, a well or
	 * fire pit). Called once, right after the settlement record is created around {@code ctx.center()}.
	 */
	List<PlotSpec> found(FoundingContext ctx);

	/**
	 * What the settlement should build next, given its needs (housing short, a trade missing, a tier reached), or null
	 * if nothing is needed or nothing fits right now. The sim calls this when builders are free.
	 */
	@Nullable GrowthProject next(Settlement settlement, TerrainProbe terrain, RandomSource random);

	/**
	 * Whether the last {@link #next} for this settlement answered nothing only because it ran out of its time (a live
	 * call's limit), not because nothing was wanted or nothing fit: the sim may ask again soon instead of waiting.
	 */
	default boolean ranOutOfTime(Settlement settlement) {
		return false;
	}

	/** Where roads out of the settlement end: its {@code highway} arm ends and gates (docs/LIVING_WORLD_2.md §1.2). */
	default List<BlockPos> exits(Settlement settlement) {
		return List.of();
	}

	record FoundingContext(Settlement settlement, BlockPos center, TerrainProbe terrain, RandomSource random) {
	}

	/**
	 * @param replaces the id of a plot this project rebuilds (an upgrade: cottage to townhouse), or -1 for new ground
	 * @param reason   a short human-readable why, for logs, debug commands and quest text ("housing: 4 without beds")
	 */
	record GrowthProject(PlotSpec spec, int replaces, String reason) {
	}
}
