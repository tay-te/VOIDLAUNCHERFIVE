package dev.voidmc.atlas.api;

import java.util.UUID;

/**
 * A place the player marked (or where they died). Immutable; edits make a copy.
 *
 * @param id        stable id (a UUID string)
 * @param name      shown on the map and in the list
 * @param dimension the dimension's id, like {@code minecraft:overworld}
 * @param colour    an index into {@link #COLOURS}
 * @param created   when it was made, epoch milliseconds
 */
public record Waypoint(String id, String name, int x, int y, int z, String dimension, int colour, Kind kind, long created, boolean visible) {
	public enum Kind {
		USER, DEATH
	}

	/** The waypoint palette: muted, toned for the dark UI. Index 0 is the design's ink-white pin. */
	public static final int[] COLOURS = {0xEDEEEF, 0x8FB8A0, 0x8AA9C9, 0xB7A3D9, 0xC48DA6, 0xC98F7A, 0xC9A66B, 0x9A9DA1};
	public static final String[] COLOUR_NAMES = {"Ink", "Sage", "Sky", "Lilac", "Rose", "Clay", "Ochre", "Stone"};

	public Waypoint {
		name = name == null || name.isBlank() ? "Waypoint" : name.strip();
		if (name.length() > 48) {
			name = name.substring(0, 48);
		}
		colour = Math.floorMod(colour, COLOURS.length);
		kind = kind == null ? Kind.USER : kind;
	}

	public static Waypoint create(String name, int x, int y, int z, String dimension, int colour) {
		return new Waypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, colour, Kind.USER, System.currentTimeMillis(), true);
	}

	public int rgb() {
		return COLOURS[this.colour];
	}

	public Waypoint withName(String name) {
		return new Waypoint(this.id, name, this.x, this.y, this.z, this.dimension, this.colour, this.kind, this.created, this.visible);
	}

	public Waypoint withColour(int colour) {
		return new Waypoint(this.id, this.name, this.x, this.y, this.z, this.dimension, colour, this.kind, this.created, this.visible);
	}

	public Waypoint withVisible(boolean visible) {
		return new Waypoint(this.id, this.name, this.x, this.y, this.z, this.dimension, this.colour, this.kind, this.created, visible);
	}

	public Waypoint withPosition(int x, int y, int z) {
		return new Waypoint(this.id, this.name, x, y, z, this.dimension, this.colour, this.kind, this.created, this.visible);
	}

	public double distance(double px, double py, double pz) {
		double dx = this.x + 0.5 - px;
		double dz = this.z + 0.5 - pz;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
