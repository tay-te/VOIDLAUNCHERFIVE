package dev.voidmc.lod.core;

/**
 * Colour arithmetic and the small amount of noise the meshers use to make far terrain look like the
 * varied place it is, rather than one flat colour per biome. All of it is integer-hash based and
 * stateless, so any worker thread and any tile agree on the value at a given world position.
 */
public final class Colors {
	/** Vanilla's ambient occlusion: the light a block corner keeps with 0, 1, 2 and 3 occluding neighbours. */
	static final float[] AO = {1.0F, 0.8F, 0.62F, 0.48F};

	private Colors() {
	}

	public static int r(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int g(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int b(int c) {
		return c & 0xFF;
	}

	public static int rgb(float r, float g, float b) {
		return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	private static int clamp(float v) {
		int i = (int) (v + 0.5F);
		return i < 0 ? 0 : Math.min(i, 255);
	}

	public static int mix(int a, int b, float t) {
		if (t <= 0) {
			return a;
		}
		if (t >= 1) {
			return b;
		}
		return rgb(r(a) + (r(b) - r(a)) * t, g(a) + (g(b) - g(a)) * t, b(a) + (b(b) - b(a)) * t);
	}

	public static int scale(int c, float f) {
		return rgb(r(c) * f, g(c) * f, b(c) * f);
	}

	/** {@code c} times a tint, both 0xRRGGBB, the way a tinted texture multiplies on the GPU. */
	public static int multiply(int c, int tint) {
		return rgb(r(c) * r(tint) / 255.0F, g(c) * g(tint) / 255.0F, b(c) * b(tint) / 255.0F);
	}

	/** Scales brightness by {@code f} and pushes saturation by {@code s} (1 leaves it). */
	public static int vary(int c, float f, float s) {
		float r = r(c) * f, g = g(c) * f, b = b(c) * f;
		float grey = (r + g + b) / 3;
		return rgb(grey + (r - grey) * s, grey + (g - grey) * s, grey + (b - grey) * s);
	}

	/** Vanilla's lightmap curve: how bright sky light {@code level} (0..15) leaves a surface. */
	public static float brightness(int level) {
		float f = Math.max(0, Math.min(15, level)) / 15.0F;
		return f / (4 - 3 * f);
	}

	/**
	 * Water from above, as vanilla draws it: the water texture (70% grey, 71% opaque) in the biome's tint,
	 * over a bed that darkens a light level per block of depth. Shallows show their sand, deep sea is dark.
	 */
	public static int water(int tint, int bed, float depth, boolean lava) {
		if (lava) {
			return tint;
		}
		int surface = scale(tint, 0.70F);
		int lit = scale(bed, brightness(15 - Math.round(Math.max(0, depth - 1))));
		return mix(lit, surface, 0.706F);
	}

	// ---- hashing and noise -----------------------------------------------------------------------------

	public static long hash(long salt, int x, int z) {
		long h = salt * 0x9E3779B97F4A7C15L ^ x * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h *= 0x94D049BB133111EBL;
		return h ^ h >>> 32;
	}

	/** A uniform value in [0, 1) from 24 bits of {@code h} starting at {@code shift}. */
	public static float unit(long h, int shift) {
		return ((h >>> shift) & 0xFFFFFF) / (float) (1 << 24);
	}

	/** Smooth value noise in [-1, 1] with features {@code scale} blocks across. */
	public static float noise(long salt, double x, double z, double scale) {
		double fx = x / scale, fz = z / scale;
		int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
		float tx = (float) (fx - ix), tz = (float) (fz - iz);
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		float a = unit(hash(salt, ix, iz), 8), b = unit(hash(salt, ix + 1, iz), 8);
		float c = unit(hash(salt, ix, iz + 1), 8), d = unit(hash(salt, ix + 1, iz + 1), 8);
		float top = a + (b - a) * tx, bottom = c + (d - c) * tx;
		return (top + (bottom - top) * tz) * 2 - 1;
	}

	/**
	 * Fractal noise in about [-1, 1] over features from {@code largest} blocks down to twice the cell size:
	 * finer octaves would only alias at this level of detail, so they are left out.
	 */
	public static float fbm(long salt, double x, double z, double largest, int cell, int octaves) {
		float sum = 0, norm = 0, amp = 1;
		double scale = largest;
		for (int o = 0; o < octaves && scale >= 2.0 * cell; o++) {
			sum += amp * noise(salt + o, x, z, scale);
			norm += amp;
			amp *= 0.55F;
			scale *= 0.42;
		}
		return norm == 0 ? 0 : sum / norm;
	}

	public static float smooth(float edge0, float edge1, float x) {
		float t = (x - edge0) / (edge1 - edge0);
		t = t < 0 ? 0 : Math.min(t, 1);
		return t * t * (3 - 2 * t);
	}
}
