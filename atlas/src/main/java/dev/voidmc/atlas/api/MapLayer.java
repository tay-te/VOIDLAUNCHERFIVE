package dev.voidmc.atlas.api;

/**
 * A layer of markers the player can switch on and off with a chip in the map's layer row. Atlas has its own
 * ({@link #TERRAIN}, {@link #STRUCTURES}, {@link #WAYPOINTS}, {@link #PLAYERS}); another mod adds its own with
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
	public static final String WAYPOINTS = "atlas:waypoints";
	public static final String PLAYERS = "atlas:players";
}
