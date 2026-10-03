package dev.voidmc.hearth.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A plot of a settlement, read-only. Mutations go through {@link Settlement}. */
public interface PlotView {
	int NONE = -1;

	int id();

	PlotSpec spec();

	PlotState state();

	/** The stage being built; equal to the blueprint's stage count once complete. */
	int stage();

	/** Steps of {@link #stage()} already placed. */
	int step();

	/**
	 * True while the current stage cannot start because the stockpile is short of its goods. Builders leave such a plot
	 * alone; a {@link PlotState#BUILDING} plot that is not waiting has had its current stage's goods taken.
	 */
	boolean waitingForGoods();

	/** The plot this one rebuilds (an upgrade: a cottage becoming a townhouse), or {@link #NONE}. */
	int replaces();

	/** The player who owns this plot (bought it from the settlement), or null if it belongs to the settlement. */
	@Nullable UUID owner();
}
