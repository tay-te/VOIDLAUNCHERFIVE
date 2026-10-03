package dev.voidmc.hearth.sim.model;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import org.jspecify.annotations.Nullable;

/**
 * Everything a settlement's simulation reaches outside itself. A live world implements it (events, the applier, the
 * planner budget); a history being written on a world generation thread implements it with no events and no world, so
 * the same model runs in both.
 */
public interface SimContext {
	SimRules rules();

	TerrainProbe terrain();

	BlueprintService blueprints();

	/** Null if there is none (growth stops; construction of what is planned goes on). */
	@Nullable SettlementPlanner planner();

	ResidentFactory residents();

	/** The world to fire {@code SettlementEvents} in, or null to fire none (pre-history). */
	@Nullable HearthWorld eventWorld();

	/** Real game time, for the embodied-builder window. */
	long gameTime();

	/**
	 * Whether a planned plot may be built here. Histories keep to their own region (so world generation of a chunk
	 * never needs a neighbour region's plan); live settlements may go anywhere.
	 */
	default boolean allows(SimSettlement settlement, PlotSpec spec) {
		return true;
	}

	/** Asks for one {@code planner.next} call; false when the per-tick budget is spent (the clock tries again later). */
	boolean tryPlannerCall();

	/** Plots were added, removed or changed shape: indexes over plots need refreshing. */
	void plotsChanged(SimSettlement settlement);

	/**
	 * How far a plot may be written into the world moved on (a stage completed; in an embodied settlement, each step).
	 * The applier writes the difference into loaded chunks.
	 */
	void progressed(SimSettlement settlement, SimPlot plot);

	/**
	 * An embodied builder placed steps {@code [from, to)} of a plot itself (cursors, see {@code Cursor}): chunks that had
	 * everything before {@code from} now have everything before {@code to}.
	 */
	void placedByEntity(SimSettlement settlement, SimPlot plot, long from, long to);

	/** The settlement changed and must be saved. */
	void dirty(SimSettlement settlement);

	/**
	 * A crowded settlement wants to send settlers out to found a daughter hamlet. Returns whether a search for a site was
	 * started (or is already under way); histories never found hamlets.
	 */
	default boolean sendSettlers(SimSettlement settlement) {
		return false;
	}
}
