package dev.voidmc.atlas.api;

import java.util.function.Consumer;

/**
 * Supplies markers to the map. Called on the client thread whenever a map is drawn (the full map, the minimap, the
 * Hub's embedded map), with the area it shows; keep it cheap (return what you already have, don't search the world).
 * A provider that throws is skipped for that frame; after {@value AtlasMarkers#MAX_FAILURES} failures in a row it is
 * switched off and logged once, so a broken provider never breaks the map.
 */
@FunctionalInterface
public interface MarkerProvider {
	void collect(MarkerQuery query, Consumer<Marker> out);
}
