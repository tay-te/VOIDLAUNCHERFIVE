package dev.voidmc.atlas.core.detect;

/**
 * One chunk's blocks as the structure scan wants them: section by section, counted from each section's palette (cheap:
 * no block is visited), and block by block only in sections whose palette holds an anchor ({@link Sig#ANCHORS}).
 */
public interface ChunkBlocks {
	int chunkX();

	int chunkZ();

	/** The lowest block height of the chunk. */
	int minY();

	/** The number of 16-block sections. */
	int sections();

	/** Adds how many blocks of each signal section {@code s} holds into {@code counts}; false if the section is empty. */
	boolean count(int s, int[] counts);

	/** Whether section {@code s} may hold an anchor signal (its palette has one). */
	boolean mayHaveAnchor(int s);

	/** The code of a block: chunk-local x and z (0..15), world y. */
	int code(int lx, int y, int lz);

	/** The height of the highest solid block that is not leaves, at a chunk-local column (the ground under the trees). */
	int surface(int lx, int lz);
}
