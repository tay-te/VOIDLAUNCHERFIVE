package dev.voidmc.expanse.world.terrain;

/**
 * Landforms that bring a biome of their own: shapes no climate table can place, because the land makes
 * them (a volcano's cone, a canyon's walls). A column on one reports a weirdness far outside anything
 * the land itself gives (it never goes beyond two thirds either way), and the biome source holds a point
 * for each landform's biome there ({@link dev.voidmc.expanse.world.biome.BiomePlacement}), so the
 * biome comes out of the source like any other: found by locate, honoured by structures, decorated
 * with its own features.
 */
public final class Landform {
	public static final int NONE = 0;
	public static final int VOLCANIC = 1;
	public static final int CANYON = 2;
	public static final int SALT_FLAT = 3;
	public static final int TEPUI = 4;
	public static final int FJORD = 5;

	private Landform() {
	}

	/** The weirdness the columns of landform {@code k} report. */
	public static float weirdness(int k) {
		return 1.5F + 0.5F * k;
	}
}
