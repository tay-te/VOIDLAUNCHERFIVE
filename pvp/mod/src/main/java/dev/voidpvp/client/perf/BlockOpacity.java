package dev.voidpvp.client.perf;

/**
 * Whether one block cell stops sight, for {@link VoxelRaycast}.
 *
 * <p>An interface so the ray test is plain Java a unit test can drive with a hand-drawn grid,
 * and the game's half — {@code OcclusionCuller}'s world reader — stays the only code that knows
 * what a chunk is. "Opaque" means a full opaque cube and nothing else: glass, leaves, slabs,
 * fences and anything that is not a whole solid block must answer {@code false}, because a cull
 * that hid an entity behind a fence would hide something vanilla shows.</p>
 */
public interface BlockOpacity {

    /** True when the cell at block coordinates {@code (x, y, z)} is a full opaque cube. */
    boolean opaque(int x, int y, int z);
}
