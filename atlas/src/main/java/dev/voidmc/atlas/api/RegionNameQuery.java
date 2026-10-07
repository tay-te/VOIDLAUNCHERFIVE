package dev.voidmc.atlas.api;

/**
 * A place Atlas is about to name: a region of one biome, a lake, a river, a peak, a waterfall, a cave mouth, a ravine,
 * or a structure.
 *
 * <p>The kinds asked: {@link Place#REGION} ({@code atlas:region}), {@link Place#LAKE}, {@link Place#RIVER},
 * {@link Place#PEAK}, {@link Place#WATERFALL}, {@link Place#CAVE_MOUTH}, {@link Place#RAVINE}, and
 * {@link Place#STRUCTURE} ({@code atlas:structure}) with {@link #structure} the structure's id. A provider returns
 * null for any kind it does not name (new kinds may be added), and Atlas uses its own name.
 *
 * @param dimension like {@code minecraft:overworld}
 * @param kind      the place's kind ({@link Place#REGION}, {@link Place#LAKE}, {@link Place#PEAK}, ...)
 * @param biome     the biome there, like {@code minecraft:cherry_grove} or {@code expanse:karst_spires}
 * @param x         where its label goes
 * @param z         where its label goes
 * @param area      its size in square blocks as far as it is explored (0 for a point)
 * @param seedHash  a hash of the world's seed (and server), the same every session
 * @param structure for {@link Place#STRUCTURE}, the structure's id ({@code minecraft:village}, {@code atlas:dungeon},
 *                  a mod's {@code expanse:watchtower}); null for every other kind
 */
public record RegionNameQuery(String dimension, String kind, String biome, int x, int z, long area, long seedHash, String structure) {
	/** A query for a natural place or a region (no structure); kept for providers built against the first API. */
	public RegionNameQuery(String dimension, String kind, String biome, int x, int z, long area, long seedHash) {
		this(dimension, kind, biome, x, z, area, seedHash, null);
	}

	/** Whether this asks for a structure's name ({@link #structure} is then its id). */
	public boolean isStructure() {
		return Place.STRUCTURE.equals(this.kind);
	}
}
