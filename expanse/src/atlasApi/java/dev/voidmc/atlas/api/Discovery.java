package dev.voidmc.atlas.api;

/**
 * A structure the player has been near (singleplayer only: on a server Atlas cannot know where structures are).
 *
 * @param structure   the structure's id, like {@code minecraft:village_plains} or {@code expanse:karst_temple}
 * @param name        a readable name ("Plains village", "Karst temple")
 * @param category    "LANDMARK", "BUILDING", "VILLAGE", "SIGHT", "DUNGEON"...
 * @param x           the middle of its bounding box
 * @param y           the middle of its bounding box
 * @param z           the middle of its bounding box
 * @param dimension   like {@code minecraft:overworld}
 * @param discovered  when the player first came near, epoch milliseconds
 * @param underground whether it lies below the surface (shown on the underground layer)
 */
public record Discovery(String structure, String name, String category, int x, int y, int z, String dimension, long discovered, boolean underground) {
	/** A stable id: the structure and where it is. */
	public String id() {
		return this.structure + "@" + this.x + "," + this.z;
	}

	/** The mod the structure comes from, upper case ("EXPANSE", "MINECRAFT"). */
	public String source() {
		int colon = this.structure.indexOf(':');
		return (colon > 0 ? this.structure.substring(0, colon) : "minecraft").toUpperCase(java.util.Locale.ROOT);
	}
}
