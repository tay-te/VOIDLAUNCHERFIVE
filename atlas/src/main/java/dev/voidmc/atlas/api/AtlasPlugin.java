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
 * }
 * }</pre>
 */
@FunctionalInterface
public interface AtlasPlugin {
	void onAtlasReady(AtlasMarkers markers);
}
