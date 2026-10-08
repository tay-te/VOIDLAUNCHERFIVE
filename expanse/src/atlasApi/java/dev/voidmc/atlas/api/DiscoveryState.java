package dev.voidmc.atlas.api;

/**
 * How much the player knows of a place. Only ever moves forward: unknown, then sighted, then discovered.
 *
 * <ul>
 *   <li>{@link #UNKNOWN}: Atlas knows of it (a structure's blocks under the ground of a chunk the client holds) but the
 *   player has not seen it: not shown anywhere.</li>
 *   <li>{@link #SIGHTED}: seen from afar, in view or in the loaded land, but not visited: shown as a "?".</li>
 *   <li>{@link #DISCOVERED}: the player came within reach of it: shown with its name.</li>
 * </ul>
 */
public enum DiscoveryState {
	UNKNOWN, SIGHTED, DISCOVERED;

	public boolean atLeast(DiscoveryState other) {
		return this.ordinal() >= other.ordinal();
	}

	public static DiscoveryState max(DiscoveryState a, DiscoveryState b) {
		return a.ordinal() >= b.ordinal() ? a : b;
	}
}
