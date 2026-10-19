package dev.voidmc.atlas.api;

/**
 * The area a map is about to draw: markers outside it are not needed (but are harmless). Coordinates are blocks.
 *
 * @param dimension the dimension shown, like {@code minecraft:overworld}
 * @param minX west edge
 * @param minZ north edge
 * @param maxX east edge
 * @param maxZ south edge
 * @param playerX the player's position (for distances), or the view's centre when there is no player
 * @param playerZ
 * @param purpose what is asking: the full map, the minimap, or the list (which wants everything, unbounded)
 */
public record MarkerQuery(String dimension, double minX, double minZ, double maxX, double maxZ, double playerX, double playerZ, Purpose purpose) {
	public enum Purpose {
		MAP, MINIMAP, LIST
	}

	public boolean contains(double x, double z) {
		return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
	}
}
