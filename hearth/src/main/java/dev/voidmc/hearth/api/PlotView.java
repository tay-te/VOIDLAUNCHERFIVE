package dev.voidmc.hearth.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A plot of a settlement, read-only. Mutations go through {@link Settlement}. */
public interface PlotView {
	int id();

	PlotSpec spec();

	PlotState state();

	/** The stage being built; equal to the blueprint's stage count once complete. */
	int stage();

	/** Steps of {@link #stage()} already placed. */
	int step();

	/** The player who owns this plot (bought it from the settlement), or null if it belongs to the settlement. */
	@Nullable UUID owner();
}
