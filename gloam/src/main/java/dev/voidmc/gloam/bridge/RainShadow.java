package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Noise;

/**
 * The rain shadow: high ground upwind wrings the rain out of the air before it arrives, so the land in its lee is
 * clearer and drier, while the windward slopes, where the air is lifted, are a little cloudier. Gloam bakes it into the
 * fronts the weather tiles hold ({@link WeatherTiles}) and into the front at the camera ({@code Atmosphere}), so the
 * pack's clouds, the regional rain, the rain particles and the rain's sound all agree. Pure, unit-tested.
 *
 * <p>The land is read at the place and at {@link #UPWIND} distances against the wind (the way it comes from). A ridge
 * standing {@code 30..200} blocks over the place within ten kilometres upwind puts it in full shadow; a place standing
 * {@code 20..160} blocks over the land upwind of it is a windward slope.
 */
public final class RainShadow {
	/** How far upwind the land is read, in blocks. */
	public static final float[] UPWIND = {2000, 5000, 10000};
	/** How much the deepest shadow takes off the front, and how much the steepest windward slope adds. */
	public static final float SHADOW = 0.25F, LIFT = 0.08F;

	private RainShadow() {
	}

	/**
	 * How much the land leans on the front at a place, about -0.25 (deep in a rain shadow) .. 0.08 (a windward slope):
	 * 0 where the land is unknown.
	 *
	 * @param here the ground's height at the place (y)
	 * @param up   the ground's height {@link #UPWIND} blocks upwind of it, nearest first (NaN for unknown)
	 */
	public static float lean(float here, float[] up) {
		if (Float.isNaN(here)) {
			return 0;
		}
		float barrier = 0, upwindSum = 0;
		int upwindN = 0;
		float[] weight = {1.0F, 0.8F, 0.6F};
		for (int k = 0; k < up.length && k < weight.length; k++) {
			if (Float.isNaN(up[k])) {
				continue;
			}
			barrier = Math.max(barrier, weight[k] * (up[k] - here));
			if (k < 2) {
				upwindSum += up[k];
				upwindN++;
			}
		}
		float shadow = Noise.smoothstep(30.0F, 200.0F, barrier);
		float lift = upwindN == 0 ? 0 : Noise.smoothstep(20.0F, 160.0F, here - upwindSum / upwindN);
		return -SHADOW * shadow + LIFT * lift * (1 - shadow);
	}

	/**
	 * The lean at world (x, z) from a raster of the land, for a wind blowing toward ({@code windX}, {@code windZ}) (a unit
	 * vector, as {@code Wind} has it). 0 off the raster.
	 */
	public static float leanAt(LandRaster land, double x, double z, float windX, float windZ) {
		return leanAt(land, x, z, windX, windZ, new float[UPWIND.length]);
	}

	/** {@link #leanAt(LandRaster, double, double, float, float)} with a scratch array of {@link #UPWIND}'s length. */
	public static float leanAt(LandRaster land, double x, double z, float windX, float windZ, float[] up) {
		if (land == null) {
			return 0;
		}
		for (int k = 0; k < UPWIND.length; k++) {
			up[k] = land.altitude(x - windX * UPWIND[k], z - windZ * UPWIND[k]);
		}
		return lean(land.altitude(x, z), up);
	}

	/** A front with the land's lean in it, 0..1. */
	public static float front(float front, float lean) {
		return Math.clamp(front + lean, 0.0F, 1.0F);
	}
}
