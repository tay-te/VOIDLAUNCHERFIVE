package dev.voidmc.atlas.api;

public record RegionNameQuery(String dimension, String kind, String biome, int x, int z, long area, long seedHash) {
}
