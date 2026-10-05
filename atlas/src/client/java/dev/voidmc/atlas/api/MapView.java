package dev.voidmc.atlas.api;

/**
 * What {@link AtlasApi#renderMap} draws: a layer, a centre, a zoom and a turn.
 *
 * @param layer           {@link #SURFACE}, {@link #UNDERGROUND} or {@link #NETHER}
 * @param centerX         the block at the middle (in the shown dimension's coordinates)
 * @param centerZ         the block at the middle
 * @param pixelsPerBlock  screen (physical) pixels per block: whole numbers are pixel-crisp; below 1 the mip levels are
 *                        used (0.5, 0.25, ... down to 1/32)
 * @param rotation        radians, clockwise; 0 is north up
 * @param markers         draw markers (waypoints, discoveries, players, other mods' markers)
 * @param player          draw the player's arrow
 * @param cornerRadius    the map's corner radius, in GUI pixels
 */
public record MapView(String layer, double centerX, double centerZ, double pixelsPerBlock, double rotation, boolean markers, boolean player,
	float cornerRadius) {
	public static final String SURFACE = "surface";
	public static final String UNDERGROUND = "underground";
	public static final String NETHER = "nether";

	public MapView {
		layer = layer == null ? SURFACE : layer;
		pixelsPerBlock = Math.max(1.0 / 32, Math.min(32, pixelsPerBlock));
	}

	/** A north-up surface view centred on a point. */
	public static MapView surface(double centerX, double centerZ, double pixelsPerBlock) {
		return new MapView(SURFACE, centerX, centerZ, pixelsPerBlock, 0, true, true, 12);
	}

	public MapView centredOn(double x, double z) {
		return new MapView(this.layer, x, z, this.pixelsPerBlock, this.rotation, this.markers, this.player, this.cornerRadius);
	}

	public MapView zoomed(double pixelsPerBlock) {
		return new MapView(this.layer, this.centerX, this.centerZ, pixelsPerBlock, this.rotation, this.markers, this.player, this.cornerRadius);
	}

	public MapView onLayer(String layer) {
		return new MapView(layer, this.centerX, this.centerZ, this.pixelsPerBlock, this.rotation, this.markers, this.player, this.cornerRadius);
	}
}
