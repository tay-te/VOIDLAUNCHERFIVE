package dev.voidmc.hearth.api;

import java.util.Collection;
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
	 * Records that an embodied builder placed the next {@code steps} steps of the plot's current stage in the world.
	 * The sim advances the plot (and its stage when the stage is done) and does not re-apply those steps on chunk load.
	 */
	void advanceConstruction(int plotId, int steps);

	/** Sells or gives a plot to a player (null returns it to the settlement). */
	void setPlotOwner(int plotId, @Nullable UUID player);

	/** A module's own data for this settlement, created from the key's factory on first use and saved with it. */
	<T> T data(HearthDataKey<T> key);

	/** Call after changing anything obtained through {@link #data} so the settlement is saved. */
	void markDirty();
}
