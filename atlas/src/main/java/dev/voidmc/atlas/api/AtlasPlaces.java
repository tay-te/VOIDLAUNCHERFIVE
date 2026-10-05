package dev.voidmc.atlas.api;

import java.util.List;
import java.util.function.Consumer;

/**
 * Atlas's places: the landmarks, structures and named regions it has found, and the hooks for other mods and servers to
 * add their own. Reached through {@link AtlasPlugin#onAtlasPlaces} (the {@code "atlas"} entrypoint) or
 * {@code AtlasApi.get().places()} on the client. Atlas runs the same with none of these hooks in use.
 */
public interface AtlasPlaces {
	/** Registers (or replaces, by id) a source of places, asked in {@code priority} order (lower first). */
	void registerPlaceProvider(String id, int priority, PlaceProvider provider);

	void unregisterPlaceProvider(String id);

	/** Registers (or replaces, by id) a namer of regions and natural places, asked in {@code priority} order. */
	void registerNameProvider(String id, int priority, RegionNameProvider provider);

	void unregisterNameProvider(String id);

	/**
	 * Adds or updates a place pushed from elsewhere (a VOID server telling its players about its landmarks: give it
	 * {@link Source#voidServer()}). Kept with the world's map like Atlas's own; a later report with the same id replaces
	 * it, keeping how far the player has come with it.
	 */
	void report(Place place);

	/** Forgets a reported place. */
	void forget(String placeId);

	/** Every place the player knows of (sighted or discovered) in a dimension. */
	List<Place> places(String dimension);

	/** Called on the client thread whenever a place is sighted or discovered (for a toast, a journal entry). */
	void addListener(Consumer<Place> listener);
}
