package dev.voidmc.lod.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What one built tile shows from above, cell by cell, for other mods to read ({@code dev.voidmc.lod.api.LodApi}): the
 * top's height and colour, what it is made of, its biome, and whether the chunks the player loaded showed it. Taken
 * from the tile's samples while it is built, only while something listens ({@link TileBuilder#captureTerrain}).
 * Immutable; the arrays are row-major (x fastest), {@code size} x {@code size} cells {@code cell} blocks apart.
 */
public final class TileTerrain {
	private static final AtomicLong REVISIONS = new AtomicLong();

	public final long key;
	/** Grows with every capture: a later revision of the same ground supersedes an earlier one. */
	public final long revision = REVISIONS.incrementAndGet();
	public final int minX;
	public final int minZ;
	public final int cell;
	public final int size;
	/** The top's y (the water's surface where wet), {@link Short#MIN_VALUE} where there is nothing (the void). */
	public final short[] heights;
	/** The top face's colour, 0xRRGGBB, as the LOD draws it. */
	public final int[] colors;
	/** The top block, a material id of the source's registry (0 unknown). */
	public final int[] materials;
	/** The biome, a {@link BiomeIds} id (-1 unknown). */
	public final int[] biomes;
	public final boolean[] seen;

	TileTerrain(long key, int size) {
		this.key = key;
		int level = TileKey.level(key);
		this.minX = TileKey.minX(key);
		this.minZ = TileKey.minZ(key);
		this.cell = TileKey.cell(level);
		this.size = size;
		int n = size * size;
		this.heights = new short[n];
		this.colors = new int[n];
		this.materials = new int[n];
		this.biomes = new int[n];
		this.seen = new boolean[n];
	}

	/** Takes the cells of {@code s}, just sampled for tile {@code key} (cell centres or, for a smooth tile, corners). */
	static TileTerrain of(long key, Samples s) {
		int n = Samples.N;
		TileTerrain t = new TileTerrain(key, n);
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int k = s.at(i, j);
				int c = i + j * n;
				if (s.empty[k]) {
					t.heights[c] = Short.MIN_VALUE;
				} else {
					int ground = (int) Math.ceil(s.ground[k] - 1e-3F);
					int water = s.water[k];
					t.heights[c] = (short) Math.max(-32767, Math.min(32767, water != Integer.MIN_VALUE && water > ground ? water : ground));
				}
				t.colors[c] = s.top[k] & 0xFFFFFF;
				t.materials[c] = s.topMaterial[k];
				t.biomes[c] = s.biome[k];
				t.seen[c] = s.seen[k];
			}
		}
		return t;
	}
}
