package dev.voidmc.atlas.core.waypoints;

import dev.voidmc.atlas.api.Waypoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A waypoint read from another map mod's files, before the player chooses to bring it in.
 *
 * @param source    which mod it came from
 * @param world     the folder the mod keeps that world's waypoints in, which is how it tells servers and saves apart
 *                  (for example {@code Multiplayer_mc.example.net} for Xaero, or the save's or server's name)
 * @param y         null when the other mod did not know the height (Xaero's {@code ~})
 * @param dimension a dimension id, like {@code minecraft:the_nether}
 * @param colour    the nearest index into {@link Waypoint#COLOURS}
 * @param death     whether the other mod marked it as a death point
 * @param disabled  whether it was switched off (hidden) in the other mod
 */
public record ImportedWaypoint(Source source, String world, String name, int x, Integer y, int z, String dimension, int colour,
		boolean death, boolean disabled) {
	public enum Source {
		XAERO, JOURNEYMAP
	}

	public ImportedWaypoint {
		world = world == null ? "" : world;
		name = name == null || name.isBlank() ? "Waypoint" : name.strip();
		dimension = dimension == null || dimension.isBlank() ? DimensionIds.OVERWORLD : dimension;
		colour = Math.floorMod(colour, Waypoint.COLOURS.length);
	}

	/**
	 * A new Atlas waypoint for this one: a fresh id, its height or {@code fallbackY} when it had none, a death point
	 * if it was one, and hidden if it was switched off.
	 */
	public Waypoint toWaypoint(int fallbackY, long created) {
		return new Waypoint(UUID.randomUUID().toString(), this.name, this.x, this.y == null ? fallbackY : this.y, this.z, this.dimension,
			this.colour, this.death ? Waypoint.Kind.DEATH : Waypoint.Kind.USER, created, !this.disabled);
	}

	/** The waypoints grouped by {@link #world()}, worlds in alphabetical order (ignoring case), each in its given order. */
	public static Map<String, List<ImportedWaypoint>> byWorld(Collection<ImportedWaypoint> waypoints) {
		Map<String, List<ImportedWaypoint>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (ImportedWaypoint w : waypoints) {
			out.computeIfAbsent(w.world(), k -> new ArrayList<>()).add(w);
		}
		return out;
	}

	/** What makes two imported waypoints the same one (a copy kept in a second folder, say). */
	String dedupeKey() {
		return this.source + "|" + this.world + "|" + this.dimension + "|" + this.name + "|" + this.x + "|" + this.y + "|" + this.z + "|" + this.death;
	}
}
