package dev.voidmc.atlas.api;

import java.util.function.Consumer;

/**
 * Supplies places (landmarks, discoveries) for a dimension: called on the client thread about once a second, so hand
 * over what you already know. A place given with {@link DiscoveryState#UNKNOWN} has its state kept by Atlas (sighted
 * once within view, discovered once within reach, and remembered for the world); one given with a state shows as
 * given. A place whose source is Atlas's default is credited to the provider. A provider that throws is skipped;
 * after three failures in a row it is switched off.
 */
@FunctionalInterface
public interface PlaceProvider {
	void collect(String dimension, Consumer<Place> out);
}
