package dev.voidmc.atlas.api;

/**
 * The {@code "atlas"} entrypoint: list a class implementing this under {@code "entrypoints": {"atlas": [...]}} in your
 * fabric.mod.json and Atlas calls it once, on the client, when it starts. Nothing of Atlas needs to be on your
 * classpath at runtime unless Atlas is installed: Fabric only loads entrypoints of mods that are present.
 *
 * <pre>{@code
 * public final class HearthAtlas implements AtlasPlugin {
 *     public void onAtlasReady(AtlasMarkers markers) {
 *         markers.registerLayer(new MapLayer("hearth:settlements", "Settlements", true, 15));
 *         markers.registerProvider("hearth:settlements", 0, (query, out) -> {
 *             for (Settlement s : Settlements.known(query.dimension())) {
 *                 out.accept(new Marker("hearth:" + s.id(), "hearth:settlements", s.name(), String.valueOf(s.folk()),
 *                     "HEARTH  ·  VILLAGE", s.x(), s.y(), s.z(), query.dimension(), MarkerGlyph.HOUSE, Marker.Style.SETTLEMENT, 0, null));
 *             }
 *         });
 *     }
 *
 *     public void onAtlasPlaces(AtlasPlaces places) {
 *         places.registerNameProvider("expanse:names", 0, q -> ExpanseNames.of(q.biome(), q.x(), q.z()));
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface AtlasPlugin {
	void onAtlasReady(AtlasMarkers markers);

	/** Called after {@link #onAtlasReady}: landmark and discovery providers, region names, places a server sends. */
	default void onAtlasPlaces(AtlasPlaces places) {
	}

	/** Called after {@link #onAtlasPlaces}: the player's journal (read only). */
	default void onAtlasJournal(AtlasJournal journal) {
	}

	/**
	 * A way to reach the player's friends and party (the VOID launcher's, say), or null. Asked once at start; the first
	 * plugin's transport that is {@link AtlasSocialTransport#available() available} is used. Friend features stay
	 * hidden while there is none.
	 */
	default AtlasSocialTransport socialTransport() {
		return null;
	}
}
