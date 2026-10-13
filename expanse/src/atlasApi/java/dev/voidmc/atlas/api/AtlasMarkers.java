package dev.voidmc.atlas.api;

import java.util.List;

/**
 * Where other mods hook their markers into the map. Usually reached through the {@code "atlas"} entrypoint
 * ({@link AtlasPlugin}), or {@code AtlasApi.get().markers()} on the client.
 */
public interface AtlasMarkers {
	/** Consecutive failures after which a provider is switched off. */
	int MAX_FAILURES = 3;

	/**
	 * Registers (or replaces, by id) a provider. Providers are asked in order of {@code priority} (lower first), then
	 * id; markers from later providers are drawn above earlier ones.
	 *
	 * @param id       stable id, like {@code hearth:settlements}
	 * @param priority draw order
	 */
	void registerProvider(String id, int priority, MarkerProvider provider);

	void unregisterProvider(String id);

	/** Adds (or replaces, by id) a layer chip. */
	void registerLayer(MapLayer layer);

	/** Every layer, in chip order. */
	List<MapLayer> layers();

	/** Every marker every provider has for a query, in draw order. Never throws for a provider's sake. */
	List<Marker> collect(MarkerQuery query);

	/**
	 * Tracks a marker by id (a quest's target): the compass, the minimap's rim, one beam and the map's hairline follow
	 * it, as they do a tracked waypoint, until the player tracks something else or the provider stops showing it. False
	 * if no provider shows that marker now (or tracking is not available here). The tracked id is remembered across
	 * sessions: a marker tracked before is followed again as soon as its provider shows it, and tracking it again then
	 * simply re-arms it (it is never switched off by this call).
	 */
	default boolean track(String markerId) {
		return false;
	}

	/** Stops tracking whatever is tracked, if it is a marker this mod tracked. */
	default void untrack(String markerId) {
	}
}
