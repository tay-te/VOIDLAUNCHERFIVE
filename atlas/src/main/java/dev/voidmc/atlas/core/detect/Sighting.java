package dev.voidmc.atlas.core.detect;

/**
 * A structure seen in the blocks of a chunk: what it is, where (its anchor block, or the middle of the chunk's share of
 * it), and whether it lies under the ground there.
 */
public record Sighting(StructureKind kind, int x, int y, int z, boolean underground) {
}
