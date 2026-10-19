package dev.voidmc.atlas.api;

/**
 * A layer of markers the player can switch on and off with a chip in the map's layer row. Atlas has its own
 * ({@link #TERRAIN}, {@link #NAMES}, {@link #LANDMARKS}, {@link #PLACES}, {@link #WAYPOINTS}, {@link #PLAYERS}, {@link #FLUIDS}); another mod adds its own with
 * {@link AtlasMarkers#registerLayer} (a Hearth "Settlements" layer).
 *
 * @param id       stable id, like {@code hearth:settlements}
 * @param label    the chip's label
 * @param visible  whether it starts switched on
 * @param order    where its chip goes in the row (Atlas's own are 0, 10, 20, 30)
 */
public record MapLayer(String id, String label, boolean visible, int order) {
	public static final String TERRAIN = "atlas:terrain";
	public static final String STRUCTURES = "atlas:structures";
	/** The names of regions, lakes and rivers, written on the land. */
	public static final String NAMES = "atlas:names";
	/** Major places: villages, temples, monuments, peaks, lakes, waterfalls... always labelled. */
	public static final String LANDMARKS = "atlas:landmarks";
	/** Minor places to explore: ruined portals, wrecks, cave mouths, geodes, dungeons... labelled from 1:1. */
	public static final String PLACES = "atlas:places";
	/** Underground water and lava (a terrain switch, shown on the underground view only). */
	public static final String FLUIDS = "atlas:fluids";
	public static final String WAYPOINTS = "atlas:waypoints";
	public static final String PLAYERS = "atlas:players";
}
