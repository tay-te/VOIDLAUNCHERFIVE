package dev.voidmc.lod.core;

/**
 * What one kind of column in the loaded chunks is made of, worked out once on the client thread from its blocks
 * and biome: the colours of the surface block's top and side, the soil and rock under it, the leaves over it and
 * the water on it, and the material ids for the same faces. Columns refer to one of these by a small index
 * ({@link ChunkColumns}), so a chunk of grass on dirt on stone is one of these and 256 heights.
 *
 * <p>Interned by the {@link ColumnStore}, so equal surfaces are one object and chunks compare by identity. The biome is a
 * {@link BiomeIds} id, -1 where it is not known (land saved before biomes were kept).
 */
public record Surface(int top, int side, int soil, int rock, int leaves, int waterColor, int soilDepth, boolean lava, boolean conifer,
	int topMaterial, int sideMaterial, int soilMaterial, int rockMaterial, int leavesMaterial, int biome) {
	/** A surface of no known biome. */
	public Surface(int top, int side, int soil, int rock, int leaves, int waterColor, int soilDepth, boolean lava, boolean conifer,
		int topMaterial, int sideMaterial, int soilMaterial, int rockMaterial, int leavesMaterial) {
		this(top, side, soil, rock, leaves, waterColor, soilDepth, lava, conifer, topMaterial, sideMaterial, soilMaterial, rockMaterial,
			leavesMaterial, -1);
	}
}
