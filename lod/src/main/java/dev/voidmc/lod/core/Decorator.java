package dev.voidmc.lod.core;

/**
 * What a terrain source's world places on its ground, block by block: the trees, as the world's own features place
 * them (same spot, same species, same height and crown), so the tiles right outside the chunks show the trees that
 * load in when the player walks closer, not stand-ins. Only where the source has the world's generator.
 */
public interface Decorator {
	/** The ground the features stand on: column (x, z), as the tile sampled it or asked of the source. */
	interface Ground {
		void column(int x, int z, ColumnSample out);

		/**
		 * Whether column (x, z) is known exactly and cheaply (the tile sampled it, block by block): such columns are
		 * kept for the neighbouring tiles' trees, which would otherwise ask the source again.
		 */
		default boolean exact(int x, int z) {
			return false;
		}

		/** Whether this ground is the land as it is block by block, or rounded to larger cells (the trees are then rough). */
		default boolean blockExact() {
			return true;
		}
	}

	/**
	 * Puts into {@code out} the blocks placed over the blocks [minX, maxX) x [minZ, maxZ), on {@code ground}, and
	 * returns true; or, where they are not worked out yet, starts on them in the background and returns false (then
	 * {@link #onReady} hears of the area once they are).
	 */
	boolean decorate(int minX, int minZ, int maxX, int maxZ, Ground ground, Voxels out);

	/** Told of an area whose blocks became ready after a {@link #decorate} said they were not. */
	interface Ready {
		void ready(int minX, int minZ, int maxX, int maxZ);
	}

	default void onReady(Ready ready) {
	}
}
