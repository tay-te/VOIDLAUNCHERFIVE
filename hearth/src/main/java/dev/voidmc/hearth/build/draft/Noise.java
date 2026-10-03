package dev.voidmc.hearth.build.draft;

/**
 * Deterministic hashing and smooth noise for blueprints. Everything a generator varies goes through these (or a
 * {@link net.minecraft.util.RandomSource} seeded from the spec), never through {@link Math#random()} or the system
 * clock, so the same spec always gives the same building on every thread and in every session.
 */
public final class Noise {
	private Noise() {
	}

	/** The splitmix64 finaliser: a good 64-bit avalanche. */
	public static long mix(long h) {
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	public static long hash(long seed, int x, int y, int z) {
		long h = seed ^ 0x9E3779B97F4A7C15L;
		h = mix(h + x * 0x632BE59BD9B4E019L);
		h = mix(h + y * 0x85157AF5L);
		h = mix(h + z * 0xC2B2AE3D27D4EB4FL);
		return h;
	}

	public static long hash(long seed, long salt) {
		return mix(seed ^ mix(salt + 0x9E3779B97F4A7C15L));
	}

	/** A uniform value in [0, 1) for a lattice point. */
	public static double unit(long seed, int x, int y, int z) {
		return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
	}

	/** Smooth value noise in [0, 1): trilinear with smoothstep, lattice spacing 1. */
	public static double value(long seed, double x, double y, double z) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c000 = unit(seed, x0, y0, z0);
		double c100 = unit(seed, x0 + 1, y0, z0);
		double c010 = unit(seed, x0, y0 + 1, z0);
		double c110 = unit(seed, x0 + 1, y0 + 1, z0);
		double c001 = unit(seed, x0, y0, z0 + 1);
		double c101 = unit(seed, x0 + 1, y0, z0 + 1);
		double c011 = unit(seed, x0, y0 + 1, z0 + 1);
		double c111 = unit(seed, x0 + 1, y0 + 1, z0 + 1);
		double a = lerp(fx, c000, c100);
		double b = lerp(fx, c010, c110);
		double c = lerp(fx, c001, c101);
		double d = lerp(fx, c011, c111);
		return lerp(fz, lerp(fy, a, b), lerp(fy, c, d));
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}
}
