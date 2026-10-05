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
 * @param expires   when it goes by itself, epoch milliseconds; 0 for never (temporary waypoints, death points)
 */
public record Waypoint(String id, String name, int x, int y, int z, String dimension, int colour, Kind kind, long created, boolean visible,
	long expires) {
	public enum Kind {
		/** Made by the player; kept until they delete it. */
		USER,
		/** Where the player died; goes after a while, or once they get back there. */
		DEATH,
		/** A quick mark (from chat, a share, a ping) that goes by itself. */
		TEMPORARY
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
		expires = Math.max(0, expires);
	}

	/** A waypoint that never expires (the record as it was before expiry). */
	public Waypoint(String id, String name, int x, int y, int z, String dimension, int colour, Kind kind, long created, boolean visible) {
		this(id, name, x, y, z, dimension, colour, kind, created, visible, 0);
	}

	/** A temporary waypoint that goes {@code lifetimeMillis} from now. */
	public static Waypoint temporary(String name, int x, int y, int z, String dimension, int colour, long lifetimeMillis) {
		long now = System.currentTimeMillis();
		return new Waypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, colour, Kind.TEMPORARY, now, true, now + Math.max(1, lifetimeMillis));
	}

	/** Whether it has expired by {@code now}. */
	public boolean expired(long now) {
		return this.expires > 0 && now >= this.expires;
	}

	public static Waypoint create(String name, int x, int y, int z, String dimension, int colour) {
		return new Waypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, colour, Kind.USER, System.currentTimeMillis(), true);
	}

	public int rgb() {
		return COLOURS[this.colour];
	}

	public Waypoint withName(String name) {
		return new Waypoint(this.id, name, this.x, this.y, this.z, this.dimension, this.colour, this.kind, this.created, this.visible, this.expires);
	}

	public Waypoint withColour(int colour) {
		return new Waypoint(this.id, this.name, this.x, this.y, this.z, this.dimension, colour, this.kind, this.created, this.visible, this.expires);
	}

	public Waypoint withVisible(boolean visible) {
		return new Waypoint(this.id, this.name, this.x, this.y, this.z, this.dimension, this.colour, this.kind, this.created, visible, this.expires);
	}

	public Waypoint withPosition(int x, int y, int z) {
		return new Waypoint(this.id, this.name, x, y, z, this.dimension, this.colour, this.kind, this.created, this.visible, this.expires);
	}

	public Waypoint withExpires(long expires) {
		return new Waypoint(this.id, this.name, this.x, this.y, this.z, this.dimension, this.colour, this.kind, this.created, this.visible, expires);
	}

	public double distance(double px, double py, double pz) {
		double dx = this.x + 0.5 - px;
		double dz = this.z + 0.5 - pz;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
