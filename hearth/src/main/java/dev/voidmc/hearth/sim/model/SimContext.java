package dev.voidmc.hearth.sim.model;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.Territory;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import java.util.List;
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

	/**
	 * Whether the economy module runs the treasury's daily income (taxes, passing trade, wages, upkeep on
	 * {@code DAY_PASSED}): the clock then leaves out its own per-worker coin, so the town is not paid twice. Histories
	 * fire no events, so they keep the clock's coin (adoption trims what it piled up).
	 */
	default boolean economyHandlesWages() {
		return false;
	}

	// ---------------------------------------------------------------- phase 3: the resource economy (docs/SIM.md §16)

	/** The chains, gathering and site tables, and the land of each biome. */
	default SimData data() {
		return SimData.current();
	}

	/** The open spaces under the ground (Expanse realms), or null. */
	default @Nullable UndergroundProbe underground() {
		return null;
	}

	/**
	 * Surveys the settlement's territory (again): a history does it at once, on its own thread; the live world off the
	 * server thread, merging the result when it is done.
	 */
	default void survey(SimSettlement settlement) {
		Lands.surveyNow(settlement, this);
	}

	/** The other settlements' cores round about, which this one's territory stops short of. */
	default List<Territory.Core> cores(SimSettlement settlement) {
		return List.of();
	}

	/** The largest territory this context allows (a history keeps to its region). */
	default int territoryLimit(SimSettlement settlement, int wanted) {
		return wanted;
	}

	/** Once a sim day: the settlement may send a caravan to a town short of its surplus (the live world only). */
	default void trade(SimSettlement settlement, long day) {
	}

	/**
	 * Whether this settlement's numbers have a world behind them: a live settlement owes the world the trees its foresters
	 * took from natural woods (felled where chunks load); a history writes only its plots.
	 */
	default boolean inWorld() {
		return false;
	}
}
