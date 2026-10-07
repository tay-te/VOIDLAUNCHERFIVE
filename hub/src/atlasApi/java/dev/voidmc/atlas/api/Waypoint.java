package dev.voidmc.atlas.api;

/** Compile-time copy of the accessors of Atlas's {@code Waypoint} record the hub reads (see {@link AtlasApi}). */
public record Waypoint(String id, String name, int x, int y, int z, String dimension) {
}
