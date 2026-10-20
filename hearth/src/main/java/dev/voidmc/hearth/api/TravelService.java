package dev.voidmc.hearth.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * People on the roads (docs/LIVING_WORLD_2.md §0.2, §1): every journey is a record whose position is a pure function of
 * time, embodied as a party near players. {@link HearthServices#travel()}; implemented by the npc module's travel (lane
 * A1). Server thread only.
 */
public interface TravelService {
	/**
	 * Sends a journey. The residents named go {@link Settlement#setResidentAway away} until it ends.
	 *
	 * @param to         the settlement it goes to, or null for a {@code destination} in the wild
	 * @param departTick on the sim clock ({@link HearthWorld#clock()})
	 * @param details    kind-specific data (cargo, the message carried, the bailiff's ledger)
	 * @return the journey's id, or 0 if refused (caps, no road)
	 */
	long dispatch(TravelKind kind, Settlement from, @Nullable Settlement to, @Nullable BlockPos destination, List<UUID> residents, long departTick,
		CompoundTag details);

	@Nullable TravellerView traveller(long id);

	/** Journeys within {@code radius} of {@code at} now, nearest first. */
	List<TravellerView> near(ServerLevel level, BlockPos at, int radius);

	/** Where a journey is at a tick on the sim clock, or null if there is no such journey. */
	default @Nullable BlockPos positionAt(long id, long tick) {
		return null;
	}

	/** Stops a journey on the road (bandits, a toll, a player); {@code by} is the player, if one did it. */
	boolean waylay(long id, @Nullable UUID by);

	/** Calls a journey home: its residents come back, it ends. */
	void recall(long id);

	enum TravelKind {
		CARAVAN, CARTER, PEDLAR, PILGRIMS, MESSENGER, PATROL, MARKET_GOERS, SETTLERS, ROAD_CREW, BANDITS, TAX_COLLECTOR, WEDDING_GUESTS, TROUPE,
		DIGNITARY
	}

	/** A journey. {@code departs} and {@code arrives} are on the sim clock; {@code residents} are those away on it. */
	record TravellerView(long id, TravelKind kind, long from, long to, long departs, long arrives, List<UUID> residents, boolean embodied) {
	}
}
