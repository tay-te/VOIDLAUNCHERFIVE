package dev.voidmc.atlas.api;

import java.util.List;

/**
 * Compile-time copy of the part of VOID Atlas's client API the hub calls (the real interface is
 * {@code atlas/src/client/java/dev/voidmc/atlas/api/AtlasApi.java}). Used only when no Atlas jar is
 * staged in {@code libs/}; never packaged (the hub jar excludes {@code dev/voidmc/atlas/**}). Every
 * signature here must match the real one exactly: methods are linked by name and descriptor.
 */
public interface AtlasApi {
	static AtlasApi get() {
		throw new IllegalStateException("VOID Atlas is not initialised");
	}

	static boolean available() {
		return false;
	}

	String version();

	List<Waypoint> waypoints();

	Waypoint addWaypoint(String name, int x, int y, int z, String dimension);

	MapWidget createMapWidget();

	void openMap();
}
