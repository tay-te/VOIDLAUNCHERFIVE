package dev.voidmc.lod.api;

/** Told of each far tile VOID LOD draws ({@link LodApi#addVisibleTerrainListener}). */
@FunctionalInterface
public interface VisibleTerrainListener {
	/** Called on VOID LOD's own thread, never the render thread, one tile at a time, in the order they were drawn. */
	void onTerrain(VisibleTerrain tile);
}
