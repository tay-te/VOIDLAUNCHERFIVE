package dev.voidmc.gloam.air;

/**
 * Smooth value noise from a hash, with no state and no Minecraft in it: the same seed and coordinates give the same
 * value on every client and on the server, so the air needs no syncing.
 */
public final class Noise {
	private Noise() {
	}

	/** A value in [0, 1) for the lattice point (x, y, z). */
	static float hash(long seed, long x, long y, long z) {
		long h = seed ^ 0x9E3779B97F4A7C15L;
		h ^= x * 0xBF58476D1CE4E5B9L;
		h = Long.rotateLeft(h, 23) * 0x94D049BB133111EBL;
		h ^= y * 0xD6E8FEB86659FD93L;
		h = Long.rotateLeft(h, 29) * 0xBF58476D1CE4E5B9L;
		h ^= z * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 32;
		return (h >>> 40) * 0x1.0p-24F;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	/** Value noise in [0, 1] at (x, y, z), one lattice cell per unit. */
	public static float value(long seed, double x, double y, double z) {
		long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
		double fx = fade(x - ix), fy = fade(y - iy), fz = fade(z - iz);
		double c000 = hash(seed, ix, iy, iz), c100 = hash(seed, ix + 1, iy, iz);
		double c010 = hash(seed, ix, iy + 1, iz), c110 = hash(seed, ix + 1, iy + 1, iz);
		double c001 = hash(seed, ix, iy, iz + 1), c101 = hash(seed, ix + 1, iy, iz + 1);
		double c011 = hash(seed, ix, iy + 1, iz + 1), c111 = hash(seed, ix + 1, iy + 1, iz + 1);
		double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
		double x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
		double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
		return (float) (y0 + (y1 - y0) * fz);
	}

	/** One-dimensional value noise in [0, 1]: a slow wobble over time. */
	public static float value(long seed, double t) {
		return value(seed, t, 0.5, 0.5);
	}

	/**
	 * Fractal noise in [0, 1]: {@code octaves} layers of value noise, each twice as fine and half as strong, so the
	 * large shapes carry small ragged edges. Stays near 0.5 on average and rarely reaches either end.
	 */
	public static float fbm(long seed, double x, double y, double z, int octaves) {
		double sum = 0, amplitude = 1, total = 0;
		for (int o = 0; o < octaves; o++) {
			sum += amplitude * value(seed + o * 0x632BE59BD9B4E019L, x, y, z);
			total += amplitude;
			amplitude *= 0.5;
			x *= 2.03;
			y *= 2.03;
			z *= 2.03;
		}
		return (float) (sum / total);
	}

	/** 0 below {@code from}, 1 above {@code to}, a smooth ramp between. */
	public static float smoothstep(float from, float to, float x) {
		float t = Math.clamp((x - from) / (to - from), 0.0F, 1.0F);
		return t * t * (3 - 2 * t);
	}
}
