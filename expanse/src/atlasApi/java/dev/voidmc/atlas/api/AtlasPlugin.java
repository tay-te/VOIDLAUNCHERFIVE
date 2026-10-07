package dev.voidmc.atlas.api;

@FunctionalInterface
public interface AtlasPlugin {
	void onAtlasReady(AtlasMarkers markers);

	default void onAtlasPlaces(AtlasPlaces places) {
	}
}
