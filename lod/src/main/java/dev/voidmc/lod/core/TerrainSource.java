package dev.voidmc.lod.core;

/**
 * Where LOD terrain comes from: anything that can describe a column of the world without building the
 * chunk it lies in. Called from worker threads, concurrently, so implementations keep per-thread state.
 *
 * <p>{@code cell} is the size of the LOD cell being sampled, in blocks. A source that knows the land as a
 * sum of detail at different scales should leave out the detail smaller than a cell: it is sub-pixel at
 * that distance, and dropping it is both cheaper and what keeps far terrain from shimmering.
 */
public interface TerrainSource {
	void sample(int x, int z, int cell, ColumnSample out);

	/** Bumped whenever what the source returns could change (a new world, a reloaded pack). */
	default long version() {
		return 0;
	}

	/**
	 * Whether the source knows anything about the block area (max exclusive): a tile over land it knows nothing of
	 * is not built or drawn. Only a source that can be ignorant (the chunks a client has seen, on a server) says no.
	 */
	default boolean covers(int minX, int minZ, int maxX, int maxZ) {
		return true;
	}
}
