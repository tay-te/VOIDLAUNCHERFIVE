package dev.voidmc.hearth.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A settlement, as other modules see it: implemented and persisted by the sim module. Server thread only (except where
 * the planner reads it during world generation, which the sim module arranges on a snapshot).
 */
public interface Settlement {
	long id();

	String name();

	Culture culture();

	SettlementTier tier();

	ResourceKey<Level> dimension();

	/** The founding point: the heart of the settlement (its well, bell or fire). */
	BlockPos center();

	/** The extent of everything planned so far, plots and roads. */
	BoundingBox bounds();

	/** The sim day it was founded (negative for settlements older than the world). */
	long foundedDay();

	/**
	 * True while a player is near enough for the settlement to be embodied: residents walk about as entities and builders
	 * place blocks one by one. False while it is simulated abstractly.
	 */
	boolean embodied();

	Collection<? extends PlotView> plots();

	@Nullable PlotView plot(int id);

	Collection<Resident> residents();

	@Nullable Resident resident(UUID id);

	int population();

	/** Beds in completed buildings. */
	int beds();

	Stockpile stockpile();

	/** Coin in the treasury, in bits (the smallest coin; see the economy module). */
	long treasury();

	void deposit(long bits);

	/** Takes coin from the treasury if there is enough; returns whether it did. */
	boolean withdraw(long bits);

	/** The player's standing here: negative is distrust, 0 a stranger, 100 a hero of the town. */
	int reputation(UUID player);

	void addReputation(UUID player, int delta);

	void addResident(Resident resident);

	boolean removeResident(UUID id);

	/**
	 * The NPC module reports which residents currently exist as entities. While a resident is embodied the sim leaves
	 * them out of abstract labour and production (their entity does the work: a builder's placements arrive through
	 * {@link #advanceConstruction}); when they go back to being a record, the sim counts them again.
	 */
	void setResidentEmbodied(UUID id, boolean embodied);

	boolean isResidentEmbodied(UUID id);

	/**
	 * An embodied resident worked {@code ticks} at their trade (the NPC module reports it when the entity folds back into
	 * its record). The sim credits it at its own abstract rates, so a town a player stands in produces as much as one
	 * nobody is near. Builders' work arrives through {@link #advanceConstruction} instead and is not reported here.
	 */
	void creditLabour(UUID resident, JobType job, int ticks);

	/**
	 * Points of interest of this type in the settlement's built plots (from their blueprints), in world coordinates —
	 * the notice board in the plaza, the meeting points, the guard posts. Cached by the sim; cheap.
	 */
	List<Poi> pois(PoiType type);

	/**
	 * Records that an embodied builder placed the next {@code steps} steps of the plot's current stage in the world.
	 * The sim advances the plot (and its stage when the stage is done) and does not re-apply those steps on chunk load.
	 * With {@code steps} 0 it only notes that builders are at work on the plot (putting up scaffolding, placing steps
	 * ahead of the cursor), so abstract labour keeps off it a while.
	 */
	void advanceConstruction(int plotId, int steps);

	/** Sells or gives a plot to a player (null returns it to the settlement). */
	void setPlotOwner(int plotId, @Nullable UUID player);

	/**
	 * The radius of land the settlement works around its centre (grows with tier; docs/LIVING_WORLD.md §7.1). The sim
	 * module overrides this; 0 until it does.
	 */
	default int territoryRadius() {
		return 0;
	}

	/** The resource sites the settlement's survey found in its territory (docs/LIVING_WORLD.md §7.1). */
	default List<ResourceSite> sites() {
		return List.of();
	}

	/** 0 (ruin) to 1 (boom): a slow average of the town's sites, food, work, trade, coin and housing (LIVING_WORLD.md §7.2). */
	default double prosperity() {
		return 0.5;
	}

	/** -1 declining, 0 steady, +1 rising, as the last {@link SettlementEvents#PROSPERITY_CHANGED} said. */
	default int fortunes() {
		return 0;
	}

	/** Why the fortunes last turned ("the iron ore at 412, -96 is worked out"), or "". */
	default String fortunesWhy() {
		return "";
	}

	/** People the territory's food sites and the town's trade can feed ({@link Integer#MAX_VALUE}: not reckoned). */
	default int carryingCapacity() {
		return Integer.MAX_VALUE;
	}

	/** Whether an ABANDONED plot was left standing empty in a decline (folk may move back in; it decays meanwhile). */
	default boolean deserted(int plotId) {
		return false;
	}

	/**
	 * Takes up to {@code units} of today's abstract builder labour (work units, as construction) for other work — repairs —
	 * before the clock spends it on construction; returns how much was granted. Only labour of builders who are records.
	 */
	default int takeBuilderLabour(int units) {
		return units;
	}

	/**
	 * An embodied resident gathered real goods (a felled tree's logs, a catch of fish, shorn wool): the sim adds them to the
	 * stockpile and depletes the site, instead of the labour-derived yield for that work. Report the time with
	 * {@link #creditLabour} as usual; the sim does not credit the same goods twice.
	 */
	default void creditYield(UUID resident, Goods goods, int units) {
		stockpile().add(goods, units);
	}

	/**
	 * A player found a resource of this kind at {@code pos} for the settlement (a prospecting quest): the sim surveys round
	 * it and adds a site if the land bears it out (firing {@link SettlementEvents#SITE_FOUND}), which the planner may then
	 * work. Returns the site, or null if the land does not bear it out.
	 */
	default @Nullable ResourceSite reportSite(ResourceSite.Kind kind, BlockPos pos, String reason) {
		return null;
	}

	/** A module's own data for this settlement, created from the key's factory on first use and saved with it. */
	<T> T data(HearthDataKey<T> key);

	/** Call after changing anything obtained through {@link #data} so the settlement is saved. */
	void markDirty();
}
