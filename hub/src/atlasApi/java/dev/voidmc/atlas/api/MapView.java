package dev.voidmc.atlas.api;

/** Compile-time copy of Atlas's {@code MapView} layer names (see {@link AtlasApi}). */
public record MapView(String layer, double centerX, double centerZ, double pixelsPerBlock, double rotation, boolean markers, boolean player,
	float cornerRadius) {
	public static final String SURFACE = "surface";
	public static final String UNDERGROUND = "underground";
	public static final String NETHER = "nether";
}
