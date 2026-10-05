package dev.voidmc.atlas.core.waypoints;

import dev.voidmc.atlas.api.Waypoint;

/**
 * A waypoint as it travels between players in a {@link WaypointShare} code. It carries only what is worth sending:
 * no id (the receiver makes their own), no creation time and no hidden flag.
 *
 * @param name      shown on the map; blank becomes "Waypoint", and it is cut to {@value #MAX_NAME} characters
 * @param y         null when only x and z are known
 * @param dimension a dimension id; blank becomes the overworld
 * @param colour    an index into {@link Waypoint#COLOURS}; out-of-range values wrap
 * @param note      optional free text, cut to {@value #MAX_NOTE} characters; blank becomes null
 * @param expiresAt optional epoch milliseconds after which the sender meant it to be ignored; null for never
 */
public record SharedWaypoint(String name, int x, Integer y, int z, String dimension, int colour, String note, Long expiresAt) {
	public static final int MAX_NAME = 48;
	public static final int MAX_NOTE = 256;

	public SharedWaypoint {
		name = name == null || name.isBlank() ? "Waypoint" : clamp(name.strip(), MAX_NAME);
		dimension = dimension == null || dimension.isBlank() ? DimensionIds.OVERWORLD : dimension.strip();
		colour = Math.floorMod(colour, Waypoint.COLOURS.length);
		note = note == null || note.isBlank() ? null : clamp(note.strip(), MAX_NOTE);
	}

	/** A waypoint with no note and no expiry. */
	public static SharedWaypoint of(String name, int x, Integer y, int z, String dimension, int colour) {
		return new SharedWaypoint(name, x, y, z, dimension, colour, null, null);
	}

	/** Whether the sender's expiry time has passed at {@code now} (epoch milliseconds). */
	public boolean expired(long now) {
		return this.expiresAt != null && now >= this.expiresAt;
	}

	/** A shareable copy of one of the player's own waypoints. */
	public static SharedWaypoint from(Waypoint w) {
		return new SharedWaypoint(w.name(), w.x(), w.y(), w.z(), w.dimension(), w.colour(), null, null);
	}

	/** A new waypoint of the player's own, standing at {@code fallbackY} when no height was shared. */
	public Waypoint toWaypoint(int fallbackY) {
		return Waypoint.create(this.name, this.x, this.y == null ? fallbackY : this.y, this.z, this.dimension, this.colour);
	}

	/** Cuts a string to at most {@code max} characters without splitting a surrogate pair. */
	static String clamp(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		int end = max;
		if (Character.isHighSurrogate(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(0, end).strip();
	}
}
