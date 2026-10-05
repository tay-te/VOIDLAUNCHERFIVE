package dev.voidmc.atlas.api;

/**
 * A place Atlas is about to name: a region of one biome, a lake, a river, a peak, a waterfall, a cave mouth or a
 * ravine.
 *
 * @param dimension like {@code minecraft:overworld}
 * @param kind      the place's kind ({@link Place#REGION}, {@link Place#LAKE}, {@link Place#PEAK}, ...)
 * @param biome     the biome there, like {@code minecraft:cherry_grove} or {@code expanse:karst_spires}
 * @param x         where its label goes
 * @param z         where its label goes
 * @param area      its size in square blocks as far as it is explored (0 for a point)
 * @param seedHash  a hash of the world's seed (and server), the same every session
 */
public record RegionNameQuery(String dimension, String kind, String biome, int x, int z, long area, long seedHash) {
}
